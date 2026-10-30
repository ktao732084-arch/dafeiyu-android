package com.xiaoxiao.pet;
import org.json.*;import java.io.File;

/**
 * The 活人感 additions (docs/CHARACTER.md): her murmur beside the reply (小声嘀咕) is cleaned, saved with the turn,
 * asked for only when switched on, and carried in her history only for fully shown turns. Her day (HerDay) comes
 * only from clips that really played and real feeding, resets each day, and hunger is said only when the body grumbles.
 * Usage: AliveTest <state directory>
 */
public final class AliveTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}

 public static void main(String[] args)throws Exception{
  aside(args[0]);
  day(args[0]);
  System.out.println("PASS: "+checks+" alive checks");
 }

 static void aside(String dir)throws Exception{
  // Cleaning: one short line of hers, never a copy of the reply, a label, a secret or a wrapped markdown italic.
  check(AgentPolicy.aside("又在赶 ddl 啊…","好，我们先把第三节理顺。").equals("又在赶 ddl 啊…"),"a short murmur is kept");
  check(AgentPolicy.aside("*这个点了还在忙啊…*","先睡吧。").equals("这个点了还在忙啊…"),"markdown italics are removed");
  check(AgentPolicy.aside("（还好啦，都是我该干的）","嗯。").equals("还好啦，都是我该干的"),"wrapping brackets are removed");
  check(AgentPolicy.aside("","好。").isEmpty()&&AgentPolicy.aside(null,"好。").isEmpty()&&AgentPolicy.aside(3,"好。").isEmpty(),"no murmur, no line");
  check(AgentPolicy.aside("我其实一直在想这件事到底该怎么办才好呢，真的很想帮上忙，可是又怕说多了打扰你","好。").isEmpty(),"too long is dropped");
  check(AgentPolicy.aside("先睡吧","嗯，先睡吧。明天再说。").isEmpty(),"repeating the reply is dropped");
  check(AgentPolicy.aside("[tool] 记一下","好。").isEmpty(),"a channel label is dropped");
  check(AgentPolicy.aside("……","好。").isEmpty(),"punctuation alone is dropped");

  // Parsing: the field travels with the reply; a broken envelope keeps the reply and drops the murmur.
  AgentReply r=AgentReply.parse("{\"reply\":\"好，我们先把第三节理顺。\",\"aside\":\"又在赶 ddl 啊…\",\"emotion\":\"calm\"}");
  check(r.reply.equals("好，我们先把第三节理顺。")&&r.aside.equals("又在赶 ddl 啊…"),"aside is parsed");
  check(AgentReply.parse("{\"reply\":\"好。\",\"aside\":\"好。\"}").aside.isEmpty(),"aside equal to the reply is dropped");
  check(AgentReply.parse("{\"reply\":\"好。\"}").aside.isEmpty()&&AgentReply.parse("就这样吧").aside.isEmpty(),"no aside field, no murmur");

  // Saving: the murmur is stored with its turn and can be looked up for the pet bubble.
  AgentStore db=new AgentStore(new File(dir,"aside"));long t=1_800_000_000_000L;
  check(db.commit(db.epoch(),"帮我看看报告","好，我们先把第三节理顺。","calm",null,true,false,null,null,"a1",t,null,false,null,null,0,"",Boolean.FALSE,null,"又在赶 ddl 啊…"),"commit with aside");
  check(db.commit(db.epoch(),"好的","嗯。","calm",null,true,false,null,null,"a2",t+60_000L,null,false,null,null,0,"",Boolean.FALSE),"commit without aside");
  check(db.aside("a1").equals("又在赶 ddl 啊…")&&db.aside("a2").isEmpty()&&db.aside("missing").isEmpty()&&db.aside(null).isEmpty(),"aside lookup by turn");
  JSONArray turns=db.snapshot().getJSONArray("turns");check(turns.getJSONObject(0).optString("aside").equals("又在赶 ddl 啊…")&&!turns.getJSONObject(1).has("aside"),"only a real murmur is saved");

  // Prompt: asked for as a habit of hers only when switched on; past murmurs ride along with their replies.
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.now=t+120_000L;in.aside=true;
  JSONArray on=PromptBuilder.build(in,"嗯",false,db.snapshot(),null);String system=on.getJSONObject(0).getString("content");
  check(system.contains("你有小声嘀咕的习惯")&&!system.contains("可以偶尔"),"the murmur is a habit, not a permission");
  check(new JSONObject(on.getJSONObject(2).getString("content")).optString("aside").equals("又在赶 ddl 啊…")&&!new JSONObject(on.getJSONObject(4).getString("content")).has("aside"),"her past murmur is in her history turn");
  check(on.getJSONObject(on.length()-1).getString("content").contains("包含reply、aside、"),"the turn protocol names aside");
  in.aside=false;JSONArray off=PromptBuilder.build(in,"嗯",false,db.snapshot(),null);
  check(!off.toString().contains("小声嘀咕")&&!off.toString().contains("aside"),"switched off: no murmur anywhere in the request");
 }

 static long at(java.time.ZoneId zone,int day,int hour,int minute){return java.time.LocalDateTime.of(2026,10,day,hour,minute).atZone(zone).toInstant().toEpochMilli();}
 static void day(String dir)throws Exception{
  java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");long M=60_000L;
  JSONObject log=null;
  log=HerDay.record(log,"晨间刷牙",at(zone,30,7,40),zone);
  log=HerDay.record(log,"小提琴演奏",at(zone,30,10,0),zone);
  log=HerDay.record(log,"小提琴演奏",at(zone,30,10,20),zone);
  log=HerDay.record(log,"碎碎念-发呆碎碎念",at(zone,30,10,25),zone);
  log=HerDay.record(log,"原地小憩沉眠",at(zone,30,13,10),zone);
  log=HerDay.record(log,"可爱宅舞",at(zone,30,20,0),zone);log=HerDay.record(log,"轻快摇摆舞",at(zone,30,20,10),zone);
  JSONArray items=log.getJSONArray("items");
  check(items.length()==4,"one entry per stretch: a second violin within half an hour and two dances merge, idle clips are not doings ("+items+")");
  check(HerDay.record(log,"小提琴演奏",at(zone,31,9,0),zone).getJSONArray("items").length()==1,"a new day starts a new log");
  JSONObject none=new JSONObject().put("interactions",new JSONArray());
  String b=HerDay.block(log,none,false,at(zone,30,21,0),zone);
  check(b.contains("早上刷牙；上午拉小提琴；中午打盹；晚上跳舞")&&b.contains("没做过的不编"),"her day by part of the day: "+b);
  check(!HerDay.block(log,none,false,at(zone,30,12,0),zone).contains("跳舞"),"what has not happened yet is not in her day");
  check(HerDay.block(log,none,false,at(zone,31,9,0),zone).isEmpty(),"yesterday's log says nothing today");
  check(HerDay.block(null,none,false,at(zone,30,9,0),zone).isEmpty(),"nothing recorded, nothing said");
  // Food: real feed records; hunger only at the times the body grumbles too.
  JSONObject fed=new JSONObject().put("interactions",new JSONArray().put(new JSONObject().put("kind","feed").put("note","用户喂你吃了小鱼干").put("at",at(zone,30,10,5))).put(new JSONObject().put("kind","feed").put("note","用户喂你吃了冰淇淋").put("at",at(zone,29,20,0))).put(new JSONObject().put("kind","tap").put("note","用户轻轻碰了碰你").put("at",at(zone,30,10,6))));
  String f=HerDay.block(null,fed,false,at(zone,30,11,10),zone);
  check(f.contains("今天吃到的：10:05 用户喂你吃了小鱼干")&&!f.contains("冰淇淋")&&!f.contains("碰了碰"),"today's meals only: "+f);
  check(HerDay.block(null,none,true,at(zone,30,11,10),zone).contains("肚子有点空")&&HerDay.block(null,none,true,at(zone,30,17,20),zone).contains("不讨食"),"unfed at a meal time: she is hungry and says it without begging");
  check(HerDay.block(null,none,true,at(zone,30,14,0),zone).isEmpty()&&HerDay.block(null,none,false,at(zone,30,11,10),zone).isEmpty(),"no grumbling outside meal times or once fed");
  ActionPlan.Moment m=ActionPlan.Moment.of(java.time.LocalDateTime.of(2026,10,30,15,30));check(m.peckish()&&!ActionPlan.Moment.of(java.time.LocalDateTime.of(2026,10,30,14,0)).peckish(),"the same meal times as the screen");
  // In the request: ordinary and proactive turns, not serious ones or history questions.
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.now=at(zone,30,21,0);in.day=log;
  AgentStore db=new AgentStore(new File(dir,"day"));
  check(PromptBuilder.build(in,"你今天干嘛了",false,db.snapshot(),null).toString().contains("你的今天（"),"her day is in an ordinary turn");
  check(PromptBuilder.build(in,"",true,db.snapshot(),null).toString().contains("你的今天（"),"and in a proactive turn, as something to share");
  check(!PromptBuilder.build(in,"帮我看看这份报告怎么写",false,db.snapshot(),null).toString().contains("你的今天（"),"not when the user is on something serious");
  in.historyRecall=new JSONObject().put("records",new JSONArray());check(!PromptBuilder.build(in,"昨天聊了什么",false,db.snapshot(),null).toString().contains("你的今天（"),"not for history questions");
 }
}
