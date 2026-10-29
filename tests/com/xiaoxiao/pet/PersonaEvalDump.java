package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;

/**
 * Writes the exact chat requests the app would send for each style and probe, for tools/persona-eval.py.
 * Probes are deliberately not the bundled examples, so the eval measures her voice rather than recall.
 * Usage: PersonaEvalDump <personas.json> <out.json>
 */
public final class PersonaEvalDump {
 static final String[] PROBES={
  "我今天升职了！","下雨了，不想出门。","你觉得我该不该辞职？","陪我玩个游戏吧。","你是不是只会说好听的？","我养的猫生病了，有点担心。",
  "推荐一部电影。","你吃饭了吗？","我考试没过。","我跟朋友吵架了。","你最喜欢什么季节？","我刚跑完五公里。",
 };
 public static void main(String[] args)throws Exception{
  List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)));
  // A companion met three weeks ago who has chatted on a handful of days, with no memories, at one fixed moment.
  long now=1_793_000_000_000L;JSONObject state=AgentStore.empty(0);MemoryLedger.migrate(state);state.put("chatDays",6).put("userTurns",40).put("lastUser",now-3*3600_000L);
  JSONArray out=new JSONArray(),meta=new JSONArray();
  for(PersonaManager.Persona p:styles){
   meta.put(new JSONObject().put("id",p.id).put("name",p.name).put("description",p.description));
   for(String probe:PROBES){
    PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona=PersonaManager.presetPrompt(p);in.guidance=PersonaManager.guidance(p);in.signature=p.signature;
    in.timeZone="Asia/Shanghai";in.now=now;in.initializedAt=now-21*86_400_000L;in.remember=true;in.diary=true;
    out.put(new JSONObject().put("style",p.id).put("probe",probe).put("messages",PromptBuilder.build(in,probe,false,state,null)));
   }
  }
  try(Writer w=new OutputStreamWriter(new FileOutputStream(args[1]),StandardCharsets.UTF_8)){w.write(new JSONObject().put("styles",meta).put("cases",out).toString());}
  System.out.println("wrote "+out.length()+" requests for "+styles.size()+" styles to "+args[1]);
 }
}
