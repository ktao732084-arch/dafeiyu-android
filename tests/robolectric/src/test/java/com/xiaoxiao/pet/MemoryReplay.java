package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.Assert.*;

/** Unchanged upstream expectations, replayed through the actual Android HTTP client. */
final class MemoryReplay {
    static boolean matches(String text,JSONArray keywords){String lower=text.toLowerCase(Locale.ROOT);for(int i=0;i<keywords.length();i++)if(!lower.contains(keywords.optString(i).toLowerCase(Locale.ROOT)))return false;return true;}
    static void check(JSONArray out,JSONObject c,int day,String metric,boolean passed)throws Exception {out.put(new JSONObject().put("case",c.getString("id")).put("day",day).put("metric",metric).put("passed",passed));}
    static MemoryBridge.Result call(MemoryBridge.Config config,JSONObject state,JSONArray incoming,String query,long now)throws Exception {
        if(config==null)return NativeMemoryEngine.evaluate(state,incoming,query,now,ZoneOffset.UTC,null,new AgentTransport.Token());
        MemoryBridge.Result result=MemoryBridge.evaluate(config,state,incoming,query,now,new AgentTransport.Token());assertTrue("real service call must succeed: "+result.status,result.remote);return result;
    }
    static void run(MemoryBridge.Config config)throws Exception {
        File root=new File("../../memory-service").getCanonicalFile();File[] files=new File(root,"upstream/eval/cases").listFiles((d,n)->n.endsWith(".json"));assertNotNull(files);Arrays.sort(files,Comparator.comparing(File::getName));JSONArray results=new JSONArray();int cases=0;
        for(File file:files){JSONArray scenarios=new JSONArray(new String(Files.readAllBytes(file.toPath()),"UTF-8"));for(int ci=0;ci<scenarios.length();ci++){
            JSONObject c=scenarios.getJSONObject(ci),state=MemoryLedger.copy(AgentStore.empty(0));JSONArray sessions=c.getJSONArray("sessions");int sequence=0;cases++;
            for(int si=0;si<sessions.length();si++){
                JSONObject session=sessions.getJSONObject(si);long now=LocalDate.parse(session.getString("date")).atTime(10,0).toInstant(ZoneOffset.UTC).toEpochMilli();JSONArray say=session.optJSONArray("say");
                if(say!=null)for(int i=0;i<say.length();i++){sequence++;JSONArray incoming=new JSONArray().put(MemoryLedger.record("r"+sequence,say.getString(i),"turn"+sequence,false,now));state=call(config,state,incoming,"",now).state;}
                long checkedAt=now+48600000;state=call(config,state,new JSONArray(),"",checkedAt).state;JSONArray active=state.getJSONArray("memories");JSONObject expect=session.optJSONObject("expect");
                if(expect!=null)for(String metric:new String[]{"remember","forget","unique"}){JSONArray checks=expect.optJSONArray(metric);if(checks==null)continue;for(int i=0;i<checks.length();i++){int found=0;for(int j=0;j<active.length();j++){JSONObject r=active.getJSONObject(j);if(matches(r.getJSONObject("engine").optString("content",r.optString("text")),checks.getJSONArray(i)))found++;}check(results,c,si+1,metric,metric.equals("remember")?found>0:metric.equals("forget")?found==0:found<=1);}}
                JSONArray queries=session.optJSONArray("queries");if(queries!=null)for(int qi=0;qi<queries.length();qi++){
                    JSONObject q=queries.getJSONObject(qi);MemoryBridge.Result response=call(config,state,new JSONArray(),q.getString("q"),checkedAt);state=response.state;Map<String,String> contents=new HashMap<>();active=state.getJSONArray("memories");for(int i=0;i<active.length();i++){JSONObject r=active.getJSONObject(i);contents.put(r.getString("id"),r.getJSONObject("engine").optString("content",r.optString("text")));}
                    List<String> ranked=new ArrayList<>();for(int i=0;i<Math.min(3,response.hits.length());i++)ranked.add(contents.get(response.hits.getString(i)));JSONArray hit=q.optJSONArray("hit"),top=q.optJSONArray("top1"),stale=q.optJSONArray("stale");
                    if(hit!=null){boolean found=false;for(String text:ranked)if(matches(text,hit))found=true;check(results,c,si+1,"hit",found);}
                    if(top!=null)check(results,c,si+1,"top1",!ranked.isEmpty()&&matches(ranked.get(0),top));
                    if(stale!=null){boolean found=false;for(String text:ranked)if(matches(text,stale)&&(hit==null||!matches(text,hit)))found=true;check(results,c,si+1,"stale",!found);}
                }
            }
        }}
        Files.write(new File(config==null?"target/memory-replay-native.json":"target/memory-replay-android.json").toPath(),results.toString(2).getBytes("UTF-8"));JSONArray baseline=new JSONArray(new String(Files.readAllBytes(new File(root,"protocol-baseline.json").toPath()),"UTF-8"));assertEquals(84,cases);assertEquals(baseline.length(),results.length());int passed=0;List<String> regressions=new ArrayList<>();
        for(int i=0;i<results.length();i++){JSONObject actual=results.getJSONObject(i),expected=baseline.getJSONObject(i);for(String key:new String[]{"case","day","metric"})assertEquals(actual.toString()+" / "+key,expected.get(key),actual.get(key));if(config!=null)assertEquals(actual.toString(),expected.getBoolean("passed"),actual.getBoolean("passed"));else if(expected.getBoolean("passed")&&!actual.getBoolean("passed"))regressions.add(actual.toString());if(actual.getBoolean("passed"))passed++;}
        System.out.println("Shared memory scenarios: "+cases+"; product expectations: "+passed+"/"+results.length()+"; mode="+(config==null?"native/offline":"Python HTTP"));assertTrue("Regressions versus reviewed rules: "+regressions,regressions.isEmpty());
    }
}
