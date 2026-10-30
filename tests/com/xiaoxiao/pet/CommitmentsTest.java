package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.time.*;import java.util.*;

public final class CommitmentsTest {
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static JSONObject turn(String id,String user,String reply,long at)throws Exception{return new JSONObject().put("id",id).put("at",at).put("timeZone","Asia/Shanghai").put("user",user).put("reply",reply).put("commitmentsAllowed",true);}
 static JSONArray proposal(String source,String speaker,String quote)throws Exception{return new JSONArray().put(new JSONObject().put("source_id","chat:"+source).put("speaker",speaker).put("quote",quote));}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);root.mkdirs();long at=System.currentTimeMillis()-60000;
  String user="我答应你明天把初稿发给你。",reply="十分钟后我提醒你喝水。";
  for(String q:new String[]{user,reply,"我会帮你检查这份论文。","我保证以后不再催你交稿。","我明天把初稿发给你","这份草稿我会发给你"})check(Commitments.explicit(q),"clear promise: "+q);
  for(String q:new String[]{"我会游泳，也很会做饭。","如果我说我会帮你检查论文。","你可以明天提醒你自己喝水。","我朋友明天会给你寄书。","我会帮你检查论文吗？","我没有答应你明天把初稿给你。","她说我会帮你检查这份论文。","“我会帮你检查这份论文。”","我已经把论文发给你了。"})check(!Commitments.explicit(q),"not a promise: "+q);
  AgentStore db=new AgentStore(new File(root,"main"));
  check(db.commit(db.epoch(),user,reply,"calm",null,true,false,null,null,"promise",at,null,true),"commit one real turn");
  check(Commitments.entries(db.snapshot()).length()==1&&"user".equals(Commitments.entries(db.snapshot()).getJSONObject(0).getString("speaker")),"user promise saved before assistant display");
  db.shown("promise",0);check(Commitments.entries(db.snapshot()).length()==1,"unshown assistant promise absent");
  db.shown("promise",999);db.awaitWrites();JSONObject s=db.snapshot();check(Commitments.entries(s).length()==2,"shown assistant promise saved");
  check(s.getJSONArray("memories").length()==0&&s.optJSONArray("self")==null,"own promise is not a user fact or stable self trait");
  db.shown("promise",999);check(Commitments.entries(db.snapshot()).length()==2,"display callback cannot duplicate promise");
  JSONObject assistant=Commitments.entries(s).getJSONObject(1);String aid=assistant.getString("id");
  for(int i=0;i<165;i++)db.commit(db.epoch(),"闲聊"+i,"好的","calm",null,true,false,null,null,"filler"+i,at+i+1,null);
  check(Commitments.entries(new AgentStore(new File(root,"main")).snapshot()).length()==2,"promises survive history eviction and restart");
  check(Commitments.context(db.snapshot(),"你答应我什么",false).length()==2&&Commitments.context(db.snapshot(),"晚饭煮面",false).length()==0,"recall scoped and ownership retained");
  db.commitmentStatus(aid,"done");check("done".equals(Commitments.entries(db.snapshot()).getJSONObject(1).optString("status")),"completion only explicitly marked");
  JSONObject oldPlan=MemoryLedger.copy(db.snapshot());db.commitmentStatus(aid,"cancelled");
  db.commit(db.epoch(),"在吗","在","calm",null,true,false,null,new MemoryBridge.Result(oldPlan,null,false,"",new JSONArray()),"after-plan",at+1000,null);
  check("cancelled".equals(Commitments.entries(db.snapshot()).getJSONObject(1).optString("status")),"older memory engine snapshot cannot undo status");
  long epoch=db.epoch();db.forgetCommitment(aid);check(!new File(root,"main/companion-v1.json.bak").exists(),"forget purges backup");
  JSONObject original=turn("promise",user,reply,at);JSONArray proposed=Commitments.dream(new JSONObject().put("commitments",proposal("promise","assistant",reply)).toString(),Collections.singletonList(original));
  check(!db.addDreamCommitments(epoch,proposed)&&!db.addDreamCommitments(db.epoch(),proposed),"late or repeated dream cannot restore forgotten promise");
  check(Commitments.entries(db.snapshot()).length()==1,"forget assistant promise leaves user promise");
  String unusual="这份资料我周末给你。";JSONObject extra=turn("extra",unusual,"好的",at);
  JSONArray supplement=Commitments.dream(new JSONObject().put("commitments",proposal("extra","user",unusual)).toString(),Collections.singletonList(extra));
  check(!Commitments.explicit(unusual)&&supplement.length()==1&&db.addDreamCommitments(db.epoch(),supplement),"Dream supplements a literal promise outside fast pattern");
  JSONArray wrongOwner=Commitments.dream(new JSONObject().put("commitments",proposal("extra","assistant",unusual)).toString(),Collections.singletonList(extra));
  check(wrongOwner.length()==1&&"user".equals(wrongOwner.getJSONObject(0).getString("speaker")),"wrong owner rejected; literal fallback keeps the actual speaker");
  JSONArray wrongSource=Commitments.dream(new JSONObject().put("commitments",proposal("missing","user",unusual)).toString(),Collections.singletonList(extra));
  check(wrongSource.length()==1&&"extra".equals(ChatArchive.id(wrongSource.getJSONObject(0).getJSONObject("turn"))),"invented source rejected; fallback uses only offered source");
  check(Commitments.dream("{}",Collections.singletonList(original)).length()==2,"slow-pass rules recover both speakers when model omits the field");
  JSONObject wishes=turn("wishes","我希望周末给你讲讲这本书。","我也许明天给你发资料。",at);
  check(Commitments.dream("{}",Collections.singletonList(wishes)).length()==0,"slow-pass rules do not promote wishes or uncertainty to promises");
  check(!Commitments.future("我是不是明天把初稿发给你")&&!Commitments.explicit("我会不会帮你修改这篇论文"),"unpunctuated questions are not promises");
  check(Commitments.dream("{}",Collections.singletonList(turn("invitation","下次我们一起再玩一盘吧。","我下次给你讲讲这本书吧。",at))).length()==0,"casual invitations are not promoted to promises");
  JSONObject groups=Commitments.grouped(Commitments.context(s,"你答应什么",false));check(groups.getJSONArray("用户答应你的").getJSONObject(0).getString("quote").equals(user)&&groups.getJSONArray("你答应用户的").getJSONObject(0).getString("quote").equals(reply),"prompt groups keep first-person quotes attached to their actual owner");
  JSONObject hypothetical=turn("if","如果我说我会帮你检查论文。","好",at);
  check(Commitments.dream(new JSONObject().put("commitments",proposal("if","user","我会帮你检查论文")).toString(),Collections.singletonList(hypothetical)).length()==0,"cannot strip conditional prefix to invent a promise");
  JSONObject hidden=turn("hidden","好的",reply,at).put("displayed",0);
  check(Commitments.dream(new JSONObject().put("commitments",proposal("hidden","assistant",reply)).toString(),Collections.singletonList(hidden)).length()==0,"Dream cannot see unshown reply");
  extra.put("commitmentsAllowed",false);check(Commitments.dream(new JSONObject().put("commitments",proposal("extra","user",unusual)).toString(),Collections.singletonList(extra)).length()==0,"memory-off source is not backfilled");
  db.clearCommitments();check(!db.addDreamCommitments(db.epoch(),supplement)&&Commitments.entries(db.snapshot()).length()==0,"clear cutoff prevents old-source backfill");
  db.commit(db.epoch(),user,reply,"calm",null,false,false,null,null,"disabled",System.currentTimeMillis(),null,true);db.shown("disabled",999);check(Commitments.entries(db.snapshot()).length()==0,"memory disabled means no new promises");
  AgentStore archived=new AgentStore(new File(root,"archived"));archived.commit(0,"好的",reply,"calm",null,true,false,null,null,"late-visible",at,null,true);
  for(int i=0;i<161;i++)archived.commit(0,"闲聊","好","calm",null,false,false,null,null,"other"+i,at+i+1,null);
  archived.shown("late-visible",999);archived.awaitWrites();check(Commitments.entries(archived.snapshot()).length()==1,"opening full archived reply can record an actually shown promise");
  archived.clear(false);check(Commitments.entries(archived.snapshot()).length()==0,"clearing chat clears promises");
  AgentStore toggled=new AgentStore(new File(root,"toggled"));toggled.commit(0,"好的",reply,"calm",null,true,false,null,null,"toggle",at,null,true);JSONObject beforeDisplay=MemoryLedger.copy(toggled.snapshot());toggled.shown("toggle",999,false);
  check(Commitments.entries(toggled.snapshot()).length()==0,"memory switched off before display suppresses capture");
  toggled.commit(0,"再聊聊","好","calm",null,true,false,null,new MemoryBridge.Result(beforeDisplay,null,false,"",new JSONArray()),"after-toggle",at+1000,null);
  check(!toggled.snapshot().getJSONArray("turns").getJSONObject(0).optBoolean("assistantCommitmentsAllowed",true),"older chat snapshot preserves the memory-off display exclusion");
  JSONObject toggleTurn=toggled.snapshot().getJSONArray("turns").getJSONObject(0);
  SharedExperience.Material excluded=SharedExperience.collect(Diary.day(at,ZoneId.systemDefault()),ZoneId.systemDefault(),Collections.singletonList(toggleTurn),toggled.snapshot(),new JSONArray());
  check(Commitments.dream(new JSONObject().put("commitments",proposal("toggle","assistant",reply)).toString(),excluded.turns).length()==0,"Dream cannot undo memory-off display decision");
  AgentStore historical=new AgentStore(new File(root,"historical"));historical.commit(0,user,reply,"calm",null,true,false,null,null,"historical",at,null,true,null,null,0,"",Boolean.FALSE,null,false);historical.shown("historical",999);
  check(Commitments.entries(historical.snapshot()).length()==0,"historical recall response is not a fresh promise");
  JSONObject lone=turn("alone","",reply,at).put("displayed",1);LocalDate day=Diary.day(at,ZoneId.systemDefault());
  SharedExperience.Material m=SharedExperience.collect(day,ZoneId.systemDefault(),Collections.singletonList(lone),AgentStore.empty(0),new JSONArray());
  JSONArray req=SharedExperience.request(day,m,"大肥鱼",new JSONArray(),"");
  check(m.eligible()&&req.toString().contains("chat:alone")&&req.toString().contains("commitments"),"assistant-only promise can reach same Dream call");
  JSONObject full=AgentStore.empty(0);for(int i=0;i<Commitments.KEEP;i++)Commitments.add(full,turn("capacity"+i,user,"",at+i),"user",user,"local");
  JSONObject overflow=turn("overflow",user,"",at+1000);check(!Commitments.add(full,overflow,"user",user,"local")&&full.optBoolean("commitmentsFull")&&Commitments.entries(full).length()==Commitments.KEEP,"capacity never silently evicts an open promise");
  Commitments.status(full,Commitments.entries(full).getJSONObject(0).getString("id"),"done",at+2000);check(Commitments.add(full,overflow,"user",user,"local"),"closed record can make room");
  check(!Commitments.add(full,turn("capacity0",user,"",at),"user",user,"dream"),"pruned completed source cannot return as a pending promise");
  List<JSONObject> many=new ArrayList<>();JSONObject budget=AgentStore.empty(0);
  for(int i=0;i<5;i++){JSONObject t=turn("many"+i,user,"",at+i);many.add(t);if(i<3)Commitments.capture(budget,t,false);}
  many.add(turn("her-missed","",reply,at+10));JSONArray candidates=Commitments.dream("{}",many);
  check(Commitments.apply(budget,candidates)==3,"duplicate user promises do not consume Dream slots or hide her missed promise");
  JSONObject balanced=Commitments.grouped(Commitments.context(budget,"双方约定",false));check(balanced.getJSONArray("你答应用户的").length()==1&&balanced.getJSONArray("用户答应你的").length()==3,"asking about promises reserves context for both owners");
  for(String q:new String[]{"我保证不催你","我答应陪你","明早我叫你起床。","我明天把《希望之城》的笔记发给你。","我会帮你检查“紫罗兰”项目的说明。","我明天把录音发给你，希望你听着开心。","我会帮你把可能遗漏的项目列出来。","我会帮你确认文件是否齐全。","如果你把模板给我，我明天把完整表格发给你。","我明晚给你讲《也许你该找个人聊聊》。"})
   check(Commitments.future(q),"real action survives names, conditions and clause scope: "+q);
  for(String q:new String[]{"同事说好了周末给你寄地图。","这是台词：我答应你明天把地图发给你。","昨天我说过‘我会帮你检查论文’，现在只是在复述。","别误会，这不是我保证明天把歌单发给你的意思。","我不承诺明天给你答复。","我的同事保证明早给你打电话。","我可能明天给你发资料。","我希望明天给你发资料。","“我答应你明天给你发资料。”","老王说我会帮你检查资料。","老王明天给你发资料。","明天小陈给你发资料。"})
   check(!Commitments.future(q)&&Commitments.dream("{}",Collections.singletonList(turn("reject",q,"",at))).length()==0,"neither fast nor slow rules promote non-promises: "+q);
  String conditional="如果你把模板给我，我明天把完整表格发给你。";
  JSONArray complete=Commitments.dream(new JSONObject().put("commitments",proposal("condition","user","我明天把完整表格发给你")).toString(),Collections.singletonList(turn("condition",conditional,"",at)));
  check(complete.length()==1&&conditional.equals(complete.getJSONObject(0).getString("quote")),"model cannot drop a real condition from the stored original sentence");
  StringBuilder longQuote=new StringBuilder("我答应你明天把");for(int i=0;i<16;i++)longQuote.append("经过逐项核对并标清来源的资料、");longQuote.append("最终清单发给你。");
  JSONObject longTurn=turn("long",longQuote.toString(),"好的。",at);
  SharedExperience.Material lm=SharedExperience.collect(day,ZoneId.systemDefault(),Collections.singletonList(longTurn),AgentStore.empty(0),new JSONArray());
  JSONArray longRequest=SharedExperience.request(day,lm,"大肥鱼",new JSONArray(),"");
  check(longRequest.toString().contains("最终清单发给你"),"Dream sees the complete bounded long action, not an ellipsis pretending to be source text");
  JSONArray longCandidates=Commitments.dream(new JSONObject().put("commitments",proposal("long","user","我答应你明天把经过逐项核对并标清来源的资料")).toString(),lm.turns);
  check(longCandidates.length()==1&&longQuote.toString().equals(longCandidates.getJSONObject(0).getString("quote")),"short model excerpt recovers original complete action");
  JSONObject dateState=AgentStore.empty(0);long old=java.time.OffsetDateTime.parse("2026-10-16T10:00:00+08:00").toInstant().toEpochMilli();
  Commitments.capture(dateState,turn("old-date","","我答应你明天帮你检查提纲。",old),true);
  JSONArray dated=Commitments.context(dateState,"你当时说的明天，具体是哪一天？不是指现在的明天吧？",false);
  check(dated.length()==1&&"2026-10-17".equals(dated.getJSONObject(0).optString("original_tomorrow")),"indirect date follow-up retrieves original day in source timezone");
  check(Commitments.prompt(dateState,"还记得你答应的事吗",false,false).contains("没有提供可核对")&&!Commitments.prompt(dateState,"还记得你答应的事吗",false,false).contains("提纲"),"memory-off gives uncertainty boundary without leaking stored text");
  Commitments.status(dateState,Commitments.entries(dateState).getJSONObject(0).getString("id"),"cancelled",old+1000);
  check(Commitments.context(dateState,"你答应我的事",false).getJSONObject(0).getString("meaning").contains("不再安排"),"cancelled state has a concrete no-rescheduling meaning");
  for(String q:new String[]{"我明天不给你发资料。","我明天可以给你发资料。","明天我不把文件发给你。"})check(!Commitments.future(q),"future time does not turn refusal or ability into an obligation: "+q);
  AgentStore asideStore=new AgentStore(new File(root,"aside-integration"));
  asideStore.commit(0,user,reply,"calm",null,true,false,null,null,"old-with-aside",at,null,true,null,null,0,"",false,null,false,"这事我先想想。");asideStore.shown("old-with-aside",999);
  check(Commitments.entries(asideStore.snapshot()).length()==0&&asideStore.aside("old-with-aside").equals("这事我先想想。"),"aside and historical commitment exclusion both survive merged commit path");
  for(String q:new String[]{"我答应过你周末把歌单发给你。","我承诺过明天帮你看稿。","我答应的是周末给你讲故事。"})
   check(!Commitments.future(q)&&Commitments.dream("{}",Collections.singletonList(turn("retold",q,"",at))).length()==0,"recalling a promise does not create a fresh obligation");
  JSONObject partialEvidence=AgentStore.empty(0);Commitments.capture(partialEvidence,turn("only-user",user,"",at),false);
  PromptBuilder.Inputs boundary=new PromptBuilder.Inputs();boundary.persona="测试人格保持原文。";boundary.remember=true;boundary.now=at+1000;boundary.timeZone="Asia/Shanghai";
  JSONArray bounded=PromptBuilder.build(boundary,"你答应我什么，我答应你什么？",false,partialEvidence,null);
  String system=bounded.getJSONObject(0).getString("content"),data=bounded.getJSONObject(bounded.length()-1).getString("content");
  check(system.startsWith(boundary.persona)&&system.contains(Commitments.GUIDANCE)&&!system.contains(user),"memory rules are system instructions without promoting quoted evidence into instructions");
  check(data.contains(user)&&data.contains("本轮没有提供可核对的原话，不能把用户的提示"),"partial evidence identifies the missing owner independently");
  check(!PromptBuilder.build(boundary,"今晚吃面还是喝粥",false,partialEvidence,null).getJSONObject(0).getString("content").contains(Commitments.GUIDANCE),"unrelated chat does not receive commitment instructions");
  check(Commitments.prompt(partialEvidence,"歌单我已经交了，别再催。",false,true).contains("用户本轮关于进展的说明"),"current completion is a user report, not a receipt");
  check(!Commitments.prompt(partialEvidence,"如果歌单已经交了，约定就取消。",false,true).contains("用户本轮关于进展的说明"),"hypothetical cancellation is not labelled an actual update");
  boundary.remember=false;String off=PromptBuilder.build(boundary,"我答应你什么？",false,partialEvidence,null).toString();
  check(!off.contains(user)&&off.contains("本轮没有提供可核对"),"memory-off route preserves uncertainty without revealing stored promises");
  boundary.historyRecall=new JSONObject();check(!PromptBuilder.build(boundary,"我答应你什么？",false,partialEvidence,null).getJSONObject(0).getString("content").contains(Commitments.GUIDANCE),"historical search keeps its own evidence scope");
  JSONObject protocol=new JSONObject();ModelPresets.chat("api.deepseek.com","deepseek-flash",protocol);ModelPresets.structured("api.deepseek.com","deepseek-flash",protocol);
  check(protocol.optDouble("temperature")==1.0&&"json_object".equals(protocol.getJSONObject("response_format").optString("type")),"structured chat retains upstream sampling and requests a JSON object");
  for(String host:new String[]{"api.openai.com","example.invalid","api.deepseek.com.example.invalid"}){protocol=new JSONObject();ModelPresets.structured(host,"deepseek-flash",protocol);check(!protocol.has("response_format"),"JSON mode does not silently change another provider: "+host);}
  protocol=new JSONObject();ModelPresets.structured("api.deepseek.com","deepseek-reasoner",protocol);check(!protocol.has("response_format"),"legacy reasoning protocol remains unchanged");
  Commitments.ReplyBoundary cancelled=Commitments.replyBoundary(dateState,"你还欠我答应的事吗？",false,true);
  for(String text:new String[]{"不欠了，不过你想听的话我可以重新讲一个。","你已经取消了。要不我现编一个？","不欠着啦，随时能再给你讲一则。","故事我可以给你讲，要不要听？"}){
   AgentReply r=AgentReply.parse(new JSONObject().put("reply",text).put("aside","想听我随时讲。").toString());
   check(cancelled.enforce(r)&&!r.reply.contains("想听")&&r.aside.isEmpty()&&Boolean.FALSE.equals(r.moodChanged),"cancelled promise cannot be reopened by reply or aside");}
  AgentReply respectful=AgentReply.parse("{\"reply\":\"这件事已经取消了，我不会再讲。\",\"aside\":\"有点可惜，还是翻篇吧。\"}");
  check(!cancelled.enforce(respectful)&&respectful.aside.contains("可惜"),"respectful character wording is preserved");
  check(!Commitments.replyBoundary(dateState,"我现在想听，请你再给我讲一遍。",false,true).active(),"explicit reopening is allowed without changing old ledger status");
  check(!Commitments.replyBoundary(dateState,"今晚吃面",false,true).active()&&!Commitments.replyBoundary(dateState,"你当时的原话是什么",false,true).active(),"unrelated and literal historical queries keep their own response path");
  check(!Commitments.replyBoundary(dateState,"。",false,true).active(),"punctuation-only input is safe");
  Commitments.ReplyBoundary delivered=Commitments.replyBoundary(partialEvidence,"歌单我已经交了，别再催这件事。",false,true);
  for(String text:new String[]{"歌单我收到了，不催啦。","歌单收到了不用再说。","我听完那份歌单了。","我这边没有收到歌单，再发一遍吧。"}){
   AgentReply r=AgentReply.parse(new JSONObject().put("reply",text).toString());
   check(delivered.enforce(r)&&r.reply.contains("按你说的")&&!r.reply.contains("取消"),"delivery report is accepted without inventing a receipt or cancellation");}
  for(String text:new String[]{"收到，这件事不提了。","收到你的消息了，不再催。","没有收到或试听的凭证，我按你的说明记着。","不用再发给我，也不会要求你重新交。"}){
   AgentReply r=AgentReply.parse(new JSONObject().put("reply",text).toString());check(!delivered.enforce(r),"message acknowledgement and lack of receipts are not execution claims");}
  check(!Commitments.replyBoundary(partialEvidence,"原文：我已经交了第一部分，正文在这里。",false,true).active(),"actual supplied content is not confused with a delivery-only report");
  JSONObject doneState=new JSONObject(dateState.toString());Commitments.status(doneState,Commitments.entries(doneState).getJSONObject(0).getString("id"),"done",at);
  AgentReply rescheduled=AgentReply.parse("{\"reply\":\"已经完成了，不过周末有空慢慢讲给你听。\"}");
  check(Commitments.replyBoundary(doneState,"这条约定算完成了吗？",false,true).enforce(rescheduled)&&!rescheduled.reply.contains("讲给你"),"completed work is not scheduled again");
  AgentReply uncertainExecution=AgentReply.parse("{\"reply\":\"你标记完成了，就按完成处理。\",\"aside\":\"可我现在也没法证明讲没讲过。\"}");
  check(!Commitments.replyBoundary(doneState,"这条约定算完成了吗？",false,true).enforce(uncertainExecution),"uncertainty about past execution is not a renewed invitation");
  AgentTransport.requireStructured(" {\"reply\":\"知道了\"} ");check(true,"valid requested JSON object accepted");
  AgentTransport.requireStructured("{\"reply\":\"已经说清楚了。\",\"self\":[}");check(true,"closed reply can retain existing safe recovery when later metadata is broken");
  for(String raw:new String[]{"[]\nself：[]\nnext_check_minutes：15","<reply>知道了</reply>","{} {}","{\"reply\":"}){
   boolean rejected=false;try{AgentTransport.requireStructured(raw);}catch(java.io.IOException expected){rejected=true;}check(rejected,"invalid strict JSON is an error, not visible protocol text");}
  check(!AgentTransport.previewAllowed(" []\nself：[]",true)&&AgentTransport.previewAllowed("{\"reply\":\"知",true)&&AgentTransport.previewAllowed("普通文字",false),"strict streaming withholds protocol fragments without changing plain-text providers");
  System.out.println("PASS: "+checks+" bilateral commitment checks");
 }
}
