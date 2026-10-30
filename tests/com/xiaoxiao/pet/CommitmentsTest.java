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
  System.out.println("PASS: "+checks+" bilateral commitment checks");
 }
}
