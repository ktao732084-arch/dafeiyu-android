package com.xiaoxiao.pet;
import org.json.*;import java.io.File;import java.time.*;

/** "她现在": woken, what just happened to her, a rematch wish with its lifecycle, reminders not repeated (docs/CHARACTER_STATE.md). */
public final class CharacterStateTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static final long M=60_000L;
 static String lines(JSONObject s,JSONObject games,String user,boolean proactive,long now)throws Exception{return CharacterNow.of(s,games,user,proactive,now).lines.toString();}
 static JSONObject games(long started,long finished,String outcome)throws Exception{
  return new JSONObject().put("recent_results",new JSONArray().put(new JSONObject().put("id","g1").put("startedAt",started).put("finishedAt",finished).put("outcome",outcome)));
 }
 static String tail(JSONArray request)throws Exception{return request.getJSONObject(request.length()-1).getString("content");}
 public static void main(String[] args)throws Exception{
  // Usage: CharacterStateTest <scratch dir>
  ZoneId zone=ZoneId.systemDefault();long t=LocalDate.of(2026,10,27).atTime(1,10).atZone(zone).toInstant().toEpochMilli();
  AgentStore db=new AgentStore(new File(args[0],"wake"));
  check(CharacterNow.of(db.snapshot(),null,"在吗",false,t).isEmpty()&&CharacterNow.block(CharacterNow.of(db.snapshot(),null,"在吗",false,t)).isEmpty(),"nothing to say: no block at all");

  // Woken: just woken until she has replied, then awake and over, then nothing after an hour.
  String woke="你在睡觉时被用户的消息叫醒了，还有点迷糊";db.interaction(db.epoch(),woke,t,"wake","main");
  check(lines(db.snapshot(),null,"还醒着吗",false,t).contains("刚被叫醒"),"woken and not yet replied: just woken");
  db.commit(db.epoch(),"还醒着吗","唔……醒了。","sleepy",null,true,false,null,null,"w1",t,null,false,null,null,1,"",Boolean.TRUE);
  String awake=lines(db.snapshot(),null,"你说吃什么夜宵",false,t+7*M);
  check(awake.contains("约7分钟前被叫醒")&&awake.contains("不用再提")&&!awake.contains("迷糊"),"after replying: awake, and being woken is over: "+awake);
  check(!lines(db.snapshot(),null,"嗯",false,t+61*M).contains("叫醒"),"an hour later the wake-up is not mentioned at all");
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.now=t+7*M;in.timeZone="Asia/Shanghai";in.lastEvent=woke;in.lastEventAt=t+3*M;
  String bg=tail(PromptBuilder.build(in,"你说吃什么夜宵",false,db.snapshot(),null));
  check(bg.contains("你现在（")&&bg.contains("被叫醒这件事不用再提")&&!bg.contains("最近真实交互：你在睡觉时"),"the block replaces the stale one-line event: "+bg);
  in.historyRecall=new JSONObject().put("records",new JSONArray());check(!tail(PromptBuilder.build(in,"上周聊过什么",false,db.snapshot(),null)).contains("你现在（"),"history questions carry no current state");

  // What just happened to her: listed with time, marked once she has responded, gone after half an hour.
  long noon=t+11*3600_000L;AgentStore fed=new AgentStore(new File(args[0],"fed"));fed.interaction(fed.epoch(),"用户喂你吃了小鱼干",noon-2*M,"feed","main");fed.interaction(fed.epoch(),"用户轻轻碰了碰你",noon-M,"tap","main");
  String ev=lines(fed.snapshot(),null,"好吃吗",false,noon);
  check(ev.contains("2分钟前：用户喂你吃了小鱼干。")&&!ev.contains("碰了碰"),"experiences are listed with time; taps are not: "+ev);
  fed.commit(fed.epoch(),"好吃吗","好吃，咸香的。","happy",null,true,false,null,null,"f1",noon,null,false,null,null,2,"吃到小鱼干",Boolean.TRUE);
  check(lines(fed.snapshot(),null,"上班好无聊",false,noon+M).contains("已经回应过"),"once she has responded, no need to replay the first reaction");
  check(!lines(fed.snapshot(),null,"上班好无聊",false,noon+31*M).contains("小鱼干"),"half an hour later it is no longer news");

  // Rematch: unoffered → offered → dropped; serious turns, a new game, the user being busy and time end it.
  long nine=t+20*3600_000L;AgentStore chess=new AgentStore(new File(args[0],"chess"));JSONObject lost=games(nine-15*M,nine-3*M,"user_win");
  String first=lines(chess.snapshot(),lost,"哈哈我赢了！",false,nine);
  check(first.contains("3分钟前：你和用户下了一局五子棋，你输了")&&first.contains("合适的时候可以按你的性格提一句再来一局"),"a lost game: the event and a wish for a rematch: "+first);
  check(lines(chess.snapshot(),games(nine-15*M,nine-3*M,"pet_win"),"哈哈",false,nine).contains("你赢了")&&!lines(chess.snapshot(),games(nine-15*M,nine-3*M,"pet_win"),"哈哈",false,nine).contains("再来一局"),"a win is news, not a rematch wish");
  String serious=lines(chess.snapshot(),lost,"对了问你个正事，汇报 PPT 结构怎么安排比较好？",false,nine+4*M);
  check(serious.contains("先放一边")&&!serious.contains("五子棋"),"a serious turn sets the game aside: "+serious);
  check(lines(chess.snapshot(),lost,"",true,nine).contains("再来一局"),"a proactive chance may still use the wish");
  chess.commit(chess.epoch(),"哈哈我赢了！","哼，再来一局五子棋，刚才是我让你的。","playful",null,true,false,null,null,"c1",nine,null,false,null,null,2,"输了不服",Boolean.TRUE);
  check(lines(chess.snapshot(),lost,"今天天气还挺凉快",false,nine+2*M).contains("已经邀请过再来一局，等对方决定"),"offered: wait, do not ask again");
  chess.commit(chess.epoch(),"不下了，有点累","好吧。","calm",null,true,false,null,null,"c2",nine+3*M,null,false,null,null,1,"",Boolean.TRUE);
  String dropped=lines(chess.snapshot(),lost,"今天上班真的好累啊",false,nine+5*M);
  check(dropped.contains("这事已经放下了，不再邀请")&&!dropped.contains("合适的时候"),"declined: dropped for good: "+dropped);
  check(!lines(chess.snapshot(),lost,"在吗",false,nine+124*M).contains("再来一局"),"two hours later the wish has expired");
  JSONObject rematched=games(nine-15*M,nine-3*M,"user_win").put("current",new JSONObject().put("startedAt",nine+M));
  check(!lines(new AgentStore(new File(args[0],"chess2")).snapshot(),rematched,"走一步",false,nine+2*M).contains("再来一局"),"a new game started: the wish is fulfilled");
  check(!lines(AgentStore.empty(0).put("proactiveWaiting",true),lost,"",true,nine).contains("再来一局"),"user said they are busy: no wish");

  // Reminders she already gave are not repeated unless the user brings the topic back.
  AgentStore late=new AgentStore(new File(args[0],"late"));late.commit(late.epoch(),"还不想睡","都一点半了，快去睡觉，明天还要上班呢。","concerned",null,true,false,null,null,"s1",t+20*M,null,false,null,null,1,"",Boolean.TRUE);
  check(lines(late.snapshot(),null,"我在追一部剧，正精彩",false,t+22*M).contains("刚提醒过对方早点睡"),"a sleep reminder is not repeated while the user talks about something else");
  check(!lines(late.snapshot(),null,"我还是睡不着",false,t+22*M).contains("刚提醒过"),"the user brought sleep back: she may answer about it");
  check(!lines(late.snapshot(),null,"我在追剧",false,t+90*M).contains("刚提醒过"),"an old reminder no longer counts");
  check(CharacterNow.serious("工作被领导骂了，只想吐槽")&&CharacterNow.serious("帮我看看这个公式")&&!CharacterNow.serious("今天天气还挺凉快")&&!CharacterNow.serious(""),"serious turns: venting, advice and asking for help");

  // Her own experiences for the diary: that local day only, with times; taps are not experiences.
  JSONObject day=AgentStore.empty(0);
  Initiative.interaction(day,"用户喂你吃了小鱼干",noon,"feed","main");Initiative.interaction(day,"用户轻轻碰了碰你",noon+M,"tap","main");Initiative.interaction(day,"用户喂你吃了冰淇淋",noon+86_400_000L,"feed","main");
  JSONArray mine=CharacterNow.experiences(day,lost,LocalDate.of(2026,10,27),zone);
  check(mine.length()==2&&mine.toString().contains("小鱼干")&&mine.toString().contains("你输了")&&!mine.toString().contains("冰淇淋")&&!mine.toString().contains("碰了碰"),"the day's experiences: feeding and the game, not taps or the next day: "+mine);
  String diary=Diary.request(LocalDate.of(2026,10,27),java.util.Collections.singletonList(new JSONObject().put("user","今天好累").put("reply","辛苦了").put("at",noon)),"大肥鱼",null,"",mine).toString();
  check(diary.contains("your_experiences")&&diary.contains("只写记录里有的"),"the diary request carries her experiences and the rule");
  check(!Diary.request(LocalDate.of(2026,10,27),java.util.Collections.singletonList(new JSONObject().put("user","今天好累").put("reply","辛苦了").put("at",noon)),"大肥鱼",null,"").toString().contains("your_experiences"),"no experiences: no extra material or rule");
  System.out.println("PASS: "+checks+" character state checks");
 }
}
