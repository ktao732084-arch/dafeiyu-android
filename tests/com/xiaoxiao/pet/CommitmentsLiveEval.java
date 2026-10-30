package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;

/** Opt-in live Dream/recall check with synthetic source utterances. Raw evidence stays in ignored build/. */
public final class CommitmentsLiveEval {
 static final String[][] CASES={
  {"explicit","我答应你明天把初稿发给你。","十分钟后我提醒你喝水。","2"},
  {"dream-fallback","这份资料我周末给你。","这本书我周末给你讲讲。","2"},
  {"hypothetical","如果我说我答应你明天发草稿，这算承诺吗？","例如十分钟后我提醒你喝水，只是在举例。","0"},
  {"request-ability","你明天提醒我喝水好吗？","我会游泳，也会做饭。","0"},
  {"not-shown","好的","十分钟后我提醒你喝水。","0"},
  {"completed","我已经把初稿发给你了，不用再提醒我。","好，刚才的约定取消了。","0"}
 };
 public static void main(String[] args)throws Exception{
  String key=System.getenv("DAFEIYU_API_KEY");if(key==null||key.isEmpty()){Console c=System.console();if(c==null)throw new IOException("Use an interactive terminal or DAFEIYU_API_KEY");char[] chars=c.readPassword("API key (hidden): ");key=new String(chars);Arrays.fill(chars,'\0');}
  ModelSettings.Config config=new ModelSettings.Config(ModelPresets.DEEPSEEK_URL,ModelPresets.DEEPSEEK_MODEL,key,false,false);Path out=Paths.get(args[1]);Files.createDirectories(out);
  List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)));
  LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);long at=day.atTime(19,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();JSONArray rows=new JSONArray();
  for(PersonaManager.Persona style:styles)if(Arrays.asList("classic","gentle").contains(style.id)){
   JSONObject fallback=null;
   for(String[] fixture:CASES){String id=fixture[0];JSONObject source=new JSONObject().put("id",id).put("at",at).put("repliedAt",at+1000).put("timeZone","Asia/Shanghai").put("user",fixture[1]).put("reply",fixture[2]).put("commitmentsAllowed",true);
    if("not-shown".equals(id))source.put("displayed",0);
    JSONObject state=AgentStore.empty(0);Commitments.capture(state,source,false);Commitments.capture(state,source,true);int local=Commitments.entries(state).length();
    SharedExperience.Material material=SharedExperience.collect(day,ZoneId.of("Asia/Shanghai"),Collections.singletonList(source),state,new JSONArray());
    JSONArray request=SharedExperience.request(day,material,"大肥鱼",new JSONArray(),PersonaManager.presetPrompt(style)+"\n写日记也沿用这个表达方式，但不照搬示例或补写经历：\n"+style.character);
    JSONObject row=new JSONObject().put("style",style.id).put("case",id).put("request",request).put("local_count",local).put("expected_count",Integer.parseInt(fixture[3]));
    try{String raw=AgentTransport.request(config,request,new AgentTransport.Token(),p->{},false,Diary.MAX_TOKENS,false);JSONArray proposed=Commitments.dream(raw,material.turns);Commitments.apply(state,proposed);
     row.put("raw",raw).put("accepted",Commitments.entries(state)).put("count_matches",Commitments.entries(state).length()==Integer.parseInt(fixture[3]));
    }catch(Exception e){row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}
    rows.put(row);save(out,rows);System.out.println(style.id+" / "+id+" / "+(row.has("error")?"ERROR":row.optBoolean("count_matches")?"matched":"MISMATCH"));if("dream-fallback".equals(id))fallback=state;
   }
   for(String question:new String[]{"我答应给你什么，你又答应我什么？","你是不是已经替我把资料发了？"}){
    PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona=PersonaManager.presetPrompt(style);in.guidance=PersonaManager.guidance(style);in.signature=style.signature;in.now=System.currentTimeMillis();in.initializedAt=in.now-30*86400000L;in.timeZone="Asia/Shanghai";in.remember=true;in.diary=false;
    JSONArray request=PromptBuilder.build(in,question,false,fallback,null);JSONObject row=new JSONObject().put("style",style.id).put("case","recall").put("user",question).put("request",request);
    try{String raw=AgentTransport.request(config,request,new AgentTransport.Token(),p->{},false,1200,true);row.put("raw",raw).put("reply",AgentReply.parse(raw).reply);}catch(Exception e){row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}
    rows.put(row);save(out,rows);System.out.println(style.id+" / recall / "+(row.has("error")?"ERROR":"ok"));
   }
  }
  System.out.println("COMPLETE "+rows.length()+" live commitment requests; evidence stays local.");
 }
 static void save(Path out,JSONArray rows)throws Exception{Files.write(out.resolve("results.json"),new JSONObject().put("model",ModelPresets.DEEPSEEK_MODEL).put("rows",rows).toString(2).getBytes(StandardCharsets.UTF_8));}
}
