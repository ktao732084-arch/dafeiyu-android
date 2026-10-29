package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

/**
 * The 84 shared memory scenarios through NativeMemoryEngine on the plain JVM (rules only, no model),
 * like the Robolectric MemoryReplay in native mode, so CI checks it on every push. Every product
 * expectation that passes in memory-service/protocol-baseline.json must still pass here.
 * Usage: NativeReplayTest <path to memory-service>
 */
public final class NativeReplayTest {
 static boolean matches(String text,JSONArray keywords){String lower=text.toLowerCase(Locale.ROOT);for(int i=0;i<keywords.length();i++)if(!lower.contains(keywords.optString(i).toLowerCase(Locale.ROOT)))return false;return true;}
 static String content(JSONObject r){JSONObject e=r.optJSONObject("engine");return e==null?r.optString("text"):e.optString("content",r.optString("text"));}
 static MemoryBridge.Result call(JSONObject state,JSONArray incoming,String query,long now)throws Exception{return NativeMemoryEngine.evaluate(state,incoming,query,now,ZoneOffset.UTC,null,new AgentTransport.Token());}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);File[] files=new File(root,"upstream/eval/cases").listFiles((d,n)->n.endsWith(".json"));Arrays.sort(files,Comparator.comparing(File::getName));
  List<Object[]> results=new ArrayList<>();int cases=0;
  for(File file:files){JSONArray scenarios=new JSONArray(new String(Files.readAllBytes(file.toPath()),"UTF-8"));for(int ci=0;ci<scenarios.length();ci++){
   JSONObject c=scenarios.getJSONObject(ci),state=MemoryLedger.copy(AgentStore.empty(0));JSONArray sessions=c.getJSONArray("sessions");int sequence=0;cases++;
   for(int si=0;si<sessions.length();si++){
    JSONObject session=sessions.getJSONObject(si);long now=LocalDate.parse(session.getString("date")).atTime(10,0).toInstant(ZoneOffset.UTC).toEpochMilli();JSONArray say=session.optJSONArray("say");
    if(say!=null)for(int i=0;i<say.length();i++){sequence++;state=call(state,new JSONArray().put(MemoryLedger.record("r"+sequence,say.getString(i),"turn"+sequence,false,now)),"",now).state;}
    long checkedAt=now+48600000;state=call(state,new JSONArray(),"",checkedAt).state;JSONArray active=state.getJSONArray("memories");JSONObject expect=session.optJSONObject("expect");
    if(expect!=null)for(String metric:new String[]{"remember","forget","unique"}){JSONArray checks=expect.optJSONArray(metric);if(checks==null)continue;for(int i=0;i<checks.length();i++){int found=0;for(int j=0;j<active.length();j++)if(matches(content(active.getJSONObject(j)),checks.getJSONArray(i)))found++;
     results.add(new Object[]{c.getString("id"),si+1,metric,metric.equals("remember")?found>0:metric.equals("forget")?found==0:found<=1});}}
    JSONArray queries=session.optJSONArray("queries");if(queries!=null)for(int qi=0;qi<queries.length();qi++){
     JSONObject q=queries.getJSONObject(qi);MemoryBridge.Result response=call(state,new JSONArray(),q.getString("q"),checkedAt);state=response.state;Map<String,String> contents=new HashMap<>();active=state.getJSONArray("memories");for(int i=0;i<active.length();i++)contents.put(active.getJSONObject(i).getString("id"),content(active.getJSONObject(i)));
     List<String> ranked=new ArrayList<>();for(int i=0;i<Math.min(3,response.hits.length());i++)ranked.add(contents.get(response.hits.getString(i)));JSONArray hit=q.optJSONArray("hit"),top=q.optJSONArray("top1"),stale=q.optJSONArray("stale");
     if(hit!=null){boolean found=false;for(String text:ranked)if(matches(text,hit))found=true;results.add(new Object[]{c.getString("id"),si+1,"hit",found});}
     if(top!=null)results.add(new Object[]{c.getString("id"),si+1,"top1",!ranked.isEmpty()&&matches(ranked.get(0),top)});
     if(stale!=null){boolean found=false;for(String text:ranked)if(matches(text,stale)&&(hit==null||!matches(text,hit)))found=true;results.add(new Object[]{c.getString("id"),si+1,"stale",!found});}
    }}}}
  JSONArray baseline=new JSONArray(new String(Files.readAllBytes(new File(root,"protocol-baseline.json").toPath()),"UTF-8"));
  if(cases!=84||baseline.length()!=results.size())throw new AssertionError("scenario shape changed: "+cases+" cases, "+results.size()+" checks");
  int passed=0,basePassed=0;List<String> regressions=new ArrayList<>(),gains=new ArrayList<>();
  for(int i=0;i<results.size();i++){Object[] a=results.get(i);JSONObject b=baseline.getJSONObject(i);
   if(!b.getString("case").equals(a[0])||b.getInt("day")!=(Integer)a[1]||!b.getString("metric").equals(a[2]))throw new AssertionError("order differs at "+i);
   boolean ok=(Boolean)a[3];if(ok)passed++;if(b.getBoolean("passed"))basePassed++;
   if(b.getBoolean("passed")&&!ok)regressions.add(a[0]+"/day"+a[1]+"/"+a[2]);if(!b.getBoolean("passed")&&ok)gains.add(a[0]+"/day"+a[1]+"/"+a[2]);}
  System.out.println("PASS: native memory "+passed+"/"+results.size()+" product expectations on 84 shared scenarios (Python rules "+basePassed+"/"+results.size()+")"+(gains.isEmpty()?"":"; better than Python on "+gains));
  if(!regressions.isEmpty())throw new AssertionError("Regressions versus the Python rules: "+regressions);
 }
}
