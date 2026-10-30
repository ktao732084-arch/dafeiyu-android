package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.util.*;

/** Rule capture (NativeMemoryEngine.autoFacts): recall and false-memory rates must not regress. Usage: <tests/memory-capture> <memory-service> */
public final class MemoryCaptureTest {
 static int[] rates(Path file)throws Exception{int tp=0,pos=0,fp=0,neg=0;for(String l:Files.readAllLines(file)){if(l.isEmpty())continue;boolean want=l.charAt(0)=='+',got=!NativeMemoryEngine.autoFacts(l.substring(1)).isEmpty();if(want){pos++;if(got)tp++;}else{neg++;if(got)fp++;}}return new int[]{tp,pos,fp,neg};}
 public static void main(String[] args)throws Exception{
  int[] t=rates(Paths.get(args[0],"tuning.txt")),b=rates(Paths.get(args[0],"blind.txt"));
  int caught=0,total=0,privacy=0;File[] files=new File(args[1],"upstream/eval/cases").listFiles((d,n)->n.endsWith(".json"));
  for(File f:files){String category=f.getName().replace(".json","");if(category.equals("noise"))continue;JSONArray cases=new JSONArray(new String(Files.readAllBytes(f.toPath()),"UTF-8"));
   for(int i=0;i<cases.length();i++){JSONArray sessions=cases.getJSONObject(i).getJSONArray("sessions");for(int j=0;j<sessions.length();j++){JSONArray say=sessions.getJSONObject(j).optJSONArray("say");if(say!=null)for(int k=0;k<say.length();k++){boolean got=!NativeMemoryEngine.autoFacts(say.getString(k)).isEmpty();if(category.equals("privacy")){if(got)privacy++;}else{total++;if(got)caught++;}}}}}
  System.out.println("PASS: rule capture tuning "+t[0]+"/"+t[1]+" facts, "+t[2]+"/"+t[3]+" false; blind "+b[0]+"/"+b[1]+", "+b[2]+"/"+b[3]+" false; shared scenarios "+caught+"/"+total+", privacy "+privacy);
  if(t[0]<46||t[2]>1||b[0]<22||b[2]>2||caught<total-2||privacy>0)throw new AssertionError("rule capture regressed");
  // Integration: the model's quotes come first; rules fill in what it missed, at most three, no duplicates.
  JSONArray c=MemoryLedger.candidates("我叫林晓。我对花生过敏，很严重。我养了一只橘猫叫团子。今天好累",new JSONArray().put("我对花生过敏"),null,"t",1L);
  if(c.length()!=3||!c.getJSONObject(0).getString("text").equals("我对花生过敏")||c.toString().contains("好累"))throw new AssertionError("candidates: "+c);
  if(MemoryLedger.candidates("哈哈哈你真好玩",new JSONArray(),null,"t",1L).length()!=0)throw new AssertionError("chatter captured");
  for(String q:new String[]{"我喜欢什么颜色？","我喜欢什么颜色","你知道我住哪里吗","我是不是很笨呢"})if(!NativeMemoryEngine.autoFacts(q).isEmpty())throw new AssertionError("question captured: "+q);
  if(NativeMemoryEngine.autoFacts("你猜我喜欢什么？我喜欢蓝色。").size()!=1)throw new AssertionError("a fact after a question in the same message is still kept");
  for(String request:new String[]{"帮我改一条给朋友的消息：我十分钟后到。","帮我把‘我喜欢上海。以后我就住上海。’改成更客气的说法。","刚才那句话重新写：请对方确认最终采用哪一版，确认后再修改。","你先别给我列计划，我就想吐槽两句。","你不用再给我安排任务了。"}){
   if(!NativeMemoryEngine.autoFacts(request).isEmpty()||MemoryLedger.candidates(request,new JSONArray().put(request),null,"request",1).length()!=0)throw new AssertionError("one-turn request became a fact: "+request);
  }
  String task="帮我把‘我喜欢上海。以后我就住上海。’改成更客气的说法。";
  if(MemoryLedger.candidates(task,new JSONArray().put("我喜欢上海").put("以后我就住上海"),null,"task",1).length()!=0)throw new AssertionError("quoted writing material escaped its request");
  for(String prefix:new String[]{"再","先","也","顺便","另外，","那就"})if(MemoryLedger.candidates(prefix+"帮我改一句给朋友的话：我十分钟后到。",new JSONArray().put("我十分钟后到"),null,"request-prefix",1).length()!=0)throw new AssertionError("request prefix escaped capture filter: "+prefix);
  for(String question:new String[]{"我一直不吃辣吗？","我一直不吃辣吗","我一直不吃辣？"})if(MemoryLedger.candidates(question,new JSONArray().put("我一直不吃辣"),null,"question",1).length()!=0)throw new AssertionError("a quoted part of a question became a fact");
  if(MemoryLedger.candidates("我一直不吃辣吗？我喜欢蓝色。",new JSONArray().put("我一直不吃辣").put("我喜欢蓝色"),null,"question-and-fact",1).length()!=1)throw new AssertionError("separate assertion after question was lost");
  String mixed="帮我润色这句：我喜欢上海。我喜欢蓝色。";
  JSONArray facts=MemoryLedger.candidates(mixed,new JSONArray().put("我喜欢上海").put("我喜欢蓝色"),null,"mixed",1);
  if(facts.length()!=1||!facts.getJSONObject(0).getString("text").equals("我喜欢蓝色"))throw new AssertionError("a separate real assertion must survive: "+facts);
  String correction="先纠正一下，我不是怕辣，只是昨天吃多了今天想清淡点。";
  if(MemoryLedger.candidates(correction,new JSONArray().put(correction),null,"correct",1).length()==0)throw new AssertionError("user correction was discarded");
  String preference="我不喜欢别人替我安排一整套行程。";
  if(MemoryLedger.candidates(preference,new JSONArray().put(preference),null,"preference",1).length()==0)throw new AssertionError("stable communication preference was discarded");
  if(MemoryLedger.candidates("记住：你先别给我列计划",new JSONArray(),"你先别给我列计划","manual",1).length()!=1)throw new AssertionError("explicit request to remember must still win");
  if(MemoryLedger.candidates("帮我记住我会写代码",new JSONArray().put("我会写代码"),null,"remember",1).length()==0)throw new AssertionError("asking to remember an ability is not a writing task");
  JSONObject state=MemoryLedger.copy(AgentStore.empty(0));long now=System.currentTimeMillis();
  JSONObject amended=MemoryLedger.record("amended",correction,"correct",false,now);
  state.getJSONArray("memories").put(amended);
  state.getJSONArray("archive").put(MemoryLedger.record("old","最好不要辣，我昨天已经被辣到了","old-turn",false,now-1000).put("engine",new JSONObject().put("superseded_by","amended").put("invalid_at",java.time.Instant.ofEpochMilli(now).toString())));
  for(int i=0;i<20;i++)state.getJSONArray("memories").put(MemoryLedger.record("other"+i,"我收藏第"+i+"种邮票","t"+i,false,now));
  String context=MemoryLedger.context(state,"午饭不辣是因为我一直怕辣吗",new JSONArray().put("other0"));
  if(!context.contains(correction)||!context.contains("\"user_correction\":true")||context.contains("最好不要辣"))throw new AssertionError("retrieval missed the current correction: "+context);
  if(MemoryLedger.context(state,"邮票",new JSONArray().put("other0")).contains(correction))throw new AssertionError("irrelevant correction leaked into unrelated context");
  if(MemoryLedger.context(state,"我今天想聊电影",new JSONArray().put("other0")).contains(correction))throw new AssertionError("generic pronouns or time words matched an unrelated correction");
  amended.put("engine",new JSONObject().put("invalid_at",java.time.Instant.ofEpochMilli(now).toString()));
  if(MemoryLedger.context(state,"怕辣",new JSONArray()).contains(correction))throw new AssertionError("invalidated correction revived");
  state.getJSONArray("memories").remove(0);
  if(MemoryLedger.context(state,"怕辣",new JSONArray()).contains(correction))throw new AssertionError("deleted correction revived");
  JSONObject legacy=MemoryLedger.record("legacy","帮我改一条给朋友的消息：我十分钟后到","legacy-turn",false,now);
  state.getJSONArray("memories").put(legacy);
  if(MemoryLedger.context(state,"朋友消息",new JSONArray().put("legacy")).contains("十分钟"))throw new AssertionError("legacy task text returned as a user fact");
  legacy.put("pinned",true);
  if(!MemoryLedger.context(state,"朋友消息",new JSONArray().put("legacy")).contains("十分钟"))throw new AssertionError("explicitly pinned note was hidden");
 }
}
