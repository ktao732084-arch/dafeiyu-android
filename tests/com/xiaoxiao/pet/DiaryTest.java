package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.time.*;import java.util.*;

/** Companion diary: when an entry is due, what the model sees and may return, and that deletions reach the diary. */
public final class DiaryTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.of(2026,10,29),yesterday=today.minusDays(1);
  File root=new File(args[0]);root.mkdirs();AgentStore db=new AgentStore(new File(root,"diary"));
  long y=yesterday.atTime(20,0).atZone(zone).toInstant().toEpochMilli();
  String[] said={"我明天要去面试一家外企","有点紧张","我准备了自我介绍","你觉得穿什么好"};
  for(int i=0;i<said.length;i++)db.commit(db.epoch(),said[i],"我陪你一起想。","calm",null,false,false,null,null,"y"+i,y+i*60_000L,null);
  db.commit(db.epoch(),"早上好","早！","happy",null,false,false,null,null,"t0",today.atTime(8,0).atZone(zone).toInstant().toEpochMilli(),null);
  check(yesterday.equals(Diary.due(db.snapshot(),today,zone)),"yesterday had enough turns and no entry");
  check(Diary.due(db.snapshot(),today.plusDays(5),zone)==null,"days older than the lookback are not backfilled");
  List<JSONObject> turns=db.dayTurns(yesterday,zone);check(turns.size()==4&&turns.get(0).getString("user").equals(said[0]),"the whole day, oldest first, nothing from today");
  JSONArray req=Diary.request(yesterday,turns,"小鲸");String system=req.getJSONObject(0).getString("content"),user=req.getJSONObject(1).getString("content");
  check(system.contains("小鲸")&&system.contains("2026-10-28")&&system.contains("不推测")&&user.contains("外企")&&user.contains("我陪你一起想"),"request carries the day's words and the rules");
  check("昨天陪他准备面试，他有点紧张。".equals(Diary.parse("{\"diary\":\"昨天陪他准备面试，他有点紧张。\"}"))&&"今天聊了面试的事。".equals(Diary.parse("```json\n{\"diary\":\"今天聊了面试的事。\"}\n```"))&&"他今天很开心呀".equals(Diary.parse("他今天很开心呀")),"JSON, fenced or plain answers");
  check(Diary.parse("")==null&&Diary.parse("好")==null&&Diary.parse("他说密码是abc123456")==null&&Diary.parse("长".repeat(200))==null,"empty, too short, too long or secret answers are dropped");
  long epoch=db.epoch();JSONObject entry=Diary.entry(yesterday,"陪他准备了明天的外企面试，他有点紧张。",turns,y+3600_000L);
  check(db.addDiary(epoch,entry)&&!db.addDiary(epoch,entry),"one entry per day");
  check(Diary.due(db.snapshot(),today,zone)==null,"not due again once written");
  check(Diary.context(db.snapshot(),"",today).toString().contains("外企面试")&&Diary.context(db.snapshot(),"",today.plusDays(30)).length()==0,"recent entries reach the prompt, old ones only when relevant");
  check(Diary.context(db.snapshot(),"面试结果出来了",today.plusDays(30)).length()==1,"an old entry about this message comes back");
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.remember=true;in.diary=true;in.now=today.atTime(9,0).atZone(zone).toInstant().toEpochMilli();
  String prompt=PromptBuilder.build(in,"面试结果出来了",false,db.snapshot(),null).toString();check(prompt.contains("你的日记")&&prompt.contains("外企面试")&&prompt.contains("可能不准"),"the relevant diary reaches the prompt, marked as her own summary");
  check(!PromptBuilder.build(in,"晚饭吃面还是炒饭",false,db.snapshot(),null).toString().contains("你的日记"),"normal unrelated question does not receive a recent diary fallback");
  in.diary=false;check(!PromptBuilder.build(in,"面试结果出来了",false,db.snapshot(),null).toString().contains("你的日记"),"diary off: not used");
  db.commit(db.epoch(),"晚上吃什么","火锅！","happy",null,false,false,null,new MemoryBridge.Result(MemoryLedger.copy(AgentStore.empty(db.epoch())),null,false,"",new JSONArray()),"t1",today.atTime(19,0).atZone(zone).toInstant().toEpochMilli(),null);
  check(Diary.entries(db.snapshot()).length()==1,"a chat turn built on an older snapshot keeps the diary");
  long stale=db.epoch();db.remember("我喜欢蓝色");check(!db.addDiary(stale,Diary.entry(today,"写了一半的日记，记录被改过。",turns,y)),"an entry written while memories changed is discarded");
  // Forgetting a memory whose source is one of the day's turns removes that day's entry.
  JSONObject plan=NativeMemoryEngine.evaluate(db.snapshot(),new JSONArray().put(MemoryLedger.record("job","我明天要去面试一家外企","y0",false,y)),"",y,zone,null,new AgentTransport.Token()).state;
  db.commit(db.epoch(),"好","好","calm",null,true,false,null,new MemoryBridge.Result(plan,null,false,"",new JSONArray()),"t2",today.atTime(20,0).atZone(zone).toInstant().toEpochMilli(),null);
  String job=null;JSONArray mem=db.snapshot().getJSONArray("memories");for(int i=0;i<mem.length();i++)if(mem.getJSONObject(i).getString("text").contains("外企"))job=mem.getJSONObject(i).getString("id");
  check(job!=null&&Diary.entries(db.snapshot()).length()==1,"diary still there before forgetting");db.forget(job);
  check(Diary.entries(db.snapshot()).length()==0,"forgetting the source memory removes the diary entry built from it");
  check(db.addDiary(db.epoch(),Diary.entry(today.minusDays(2),"前天聊了周末去爬山。",Collections.singletonList(new JSONObject().put("id","t1").put("user","x")),y))&&Diary.entries(db.snapshot()).length()==1,"another day");
  db.clear(false);check(Diary.entries(db.snapshot()).length()==0,"clearing chats clears the diary");
  db.clearDiary();check(Diary.entries(new AgentStore(new File(root,"diary")).snapshot()).length()==0,"and stays cleared after reopening");
  sharedChecks(new File(root,"shared"),today,zone);
  System.out.println("PASS: "+checks+" diary checks");
 }
 static Gomoku fixture(GomokuStore store,long at)throws Exception{
  Gomoku g=new Gomoku(UUID.randomUUID().toString(),at,ZoneId.systemDefault().getId());
  for(int p:new int[]{0,15,1,16,2,17,3,18,4})g.play(p);g.finishedAt=at+1000;
  ChatArchive.write(new File(store.results,g.id+".json"),g.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return g;
 }
 static void sharedChecks(File root,LocalDate today,ZoneId zone)throws Exception{
  AgentStore db=new AgentStore(root);GomokuStore games=new GomokuStore(root);LocalDate day=today.minusDays(1);long at=day.atTime(12,0).atZone(zone).toInstant().toEpochMilli();
  Gomoku first=fixture(games,at);for(int i=1;i<12;i++)fixture(games,at+i*2000);
  check(games.context().getJSONArray("recent_results").length()==8&&db.dayGames(day,zone).length()==12,"day's durable games are not limited by last-eight chat context");
  SharedExperience.Material m=SharedExperience.collect(day,zone,Collections.emptyList(),db.snapshot(),db.dayGames(day,zone));
  check(m.eligible()&&m.turns.isEmpty(),"finished game alone can produce a page");
  JSONArray req=SharedExperience.request(day,m,"小鲸",new JSONArray(),"她的固定性格");check(req.toString().contains("game:"+first.id)&&req.toString().contains("现在回想"),"model receives source ids and retrospective instruction");
  String raw=new JSONObject().put("diary","这句不应冒充事实。").put("moment",new JSONObject().put("source_id","game:"+first.id).put("reflection","现在想起那盘棋，还是挺想再玩一局。").put("fact","模型伪造的胜负")).toString();
  JSONObject page=SharedExperience.entry(raw,day,m,at+86400000L);check(page!=null&&SharedExperience.fact(page).contains("用户赢")&&!page.toString().contains("模型伪造"),"fact is copied from source, never model-written");
  check(page.getJSONArray("sources").length()==0&&page.getJSONObject("moment").getLong("at")==first.finishedAt&&page.getLong("at")!=first.finishedAt,"event time and writing time are separate, game is not a fake chat source");
  check(db.addDiary(db.epoch(),page),"grounded game page saved");JSONObject before=db.snapshot();
  String context=Diary.context(before,"上次下棋",today.plusDays(30)).toString();check(context.contains("用户赢")&&context.contains("recollection")&&context.contains("不是当时的原话或当前心情"),"old shared game recalled with fact and reflection distinguished");
  check(before.toString().equals(db.snapshot().toString()),"recall does not change mood or user facts");
  check(Diary.browse(before,"五子棋").size()==1&&Diary.browse(before,day.toString()).size()==1,"search matches event text and date");
  JSONObject roundTrip=new JSONObject(before.toString());check(Diary.entries(roundTrip).getJSONObject(0).getJSONObject("moment").getString("gameId").equals(first.id),"export roundtrip keeps evidence snapshot");
  check(SharedExperience.entry("{\"moment\":{\"source_id\":\"game:invented\",\"reflection\":\"我很高兴\"},\"diary\":\"不允许回退这段文字\"}",day,m,at)==null,"invented source rejects whole new-format page");
  check(SharedExperience.entry("{\"moment\":\"bad schema\",\"diary\":\"不允许用这段文字绕过校验\"}",day,m,at)==null,"malformed moment cannot bypass source validation");
  check(SharedExperience.entry("{\"moment\":{\"source_id\":\"game:"+first.id+"\",\"reflection\":\"密码12345678901\"}}",day,m,at)==null,"sensitive reflection rejected");
  check(SharedExperience.entry("{\"diary\":\"没有来源的游戏日记\"}",day,m,at)==null,"game-only generation requires a grounded moment");
  long pending=db.epoch();db.forgetDiary(day.toString());check(!db.addDiary(pending,page),"delete during generation rejects late result");
  AgentStore reopened=new AgentStore(root);check(Diary.suppressed(reopened.snapshot(),day)&&!reopened.addDiary(reopened.epoch(),page),"explicitly forgotten date stays suppressed after restart");
  LocalDate older=day.minusDays(1);Gomoku old=fixture(games,at-86400000L);SharedExperience.Material oldM=SharedExperience.collect(older,zone,Collections.emptyList(),reopened.snapshot(),db.dayGames(older,zone));
  JSONObject oldPage=SharedExperience.entry(raw.replace(first.id,old.id),older,oldM,at);check(reopened.addDiary(reopened.epoch(),oldPage),"another day is independent");
  pending=reopened.epoch();reopened.clearDiary();check(reopened.epoch()>pending&&!reopened.addDiary(pending,oldPage),"clear invalidates in-flight diary");
  check(!SharedExperience.collect(older,zone,Collections.emptyList(),reopened.snapshot(),games.resultsOn(older,zone)).eligible(),"pre-clear game evidence cannot regenerate a page");
  JSONObject fresh=AgentStore.empty(0);List<JSONObject> turns=new ArrayList<>();
  for(int i=0;i<4;i++)turns.add(new JSONObject().put("id","chat"+i).put("at",at+i).put("user","我想去海边散步"+i).put("reply","保存但未展示的秘密回复").put("displayed",0));
  SharedExperience.Material chat=SharedExperience.collect(day,zone,turns,fresh,new JSONArray());JSONArray chatReq=SharedExperience.request(day,chat,"小鲸",new JSONArray(),"");
  check(!chatReq.toString().contains("秘密回复"),"unshown reply cannot become an experienced memory");
  JSONObject chatPage=SharedExperience.entry("{\"moment\":{\"source_id\":\"chat:chat2\",\"reflection\":\"回想起来，我也期待那片海。\"}}",day,chat,at+1000);
  check(chatPage.getJSONArray("sources").length()==1&&chatPage.getJSONArray("sources").getString(0).equals("chat2"),"selected moment links only its actual chat source");
  Diary.add(fresh,chatPage);Diary.dropSources(fresh,Collections.singleton("chat1"));check(Diary.entries(fresh).length()==1,"unrelated source does not remove selected moment");
  Diary.dropSources(fresh,Collections.singleton("chat2"));check(Diary.entries(fresh).length()==0,"forgetting selected source removes reflection and source snapshot");
  JSONObject oldSource=SharedExperience.entry(raw,day,m,at);JSONObject ranked=AgentStore.empty(0);Diary.add(ranked,oldSource);
  for(int i=0;i<3;i++)Diary.add(ranked,Diary.entry(today.plusDays(10+i),"今天聊了火锅和饮料。",Collections.emptyList(),at+i));
  JSONArray hits=Diary.context(ranked,"上次下棋",today.plusDays(14));check(hits.length()<=2&&hits.toString().contains(first.id)==false&&hits.toString().contains("用户赢"),"relevant older event beats unrelated recent pages, no internal source ids in prompt");
  JSONObject empty=AgentStore.empty(0);Initiative.interaction(empty,"用户轻轻点了你",at,"tap","main");check(!SharedExperience.collect(day,zone,Collections.emptyList(),empty,new JSONArray()).eligible(),"tap-only day does not turn into a journal factory");
  long utc=LocalDate.of(2026,10,28).atTime(23,30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
  check(SharedExperience.onDay(utc,LocalDate.of(2026,10,29),ZoneId.of("Asia/Shanghai"),0)&&!SharedExperience.onDay(utc,LocalDate.of(2026,10,29),ZoneId.of("UTC"),0),"day selection respects timezone");
  List<JSONObject> topic=new ArrayList<>();String[] words={"昨天烤了饼干","我今天打开论文，但只写出了两行","只有两百字，我给它起名叫星火","星火行动完成了，先收工"};
  for(int i=0;i<words.length;i++)topic.add(new JSONObject().put("id","topic"+i).put("at",at+i*60000).put("user",words[i]).put("reply","好的"));
  SharedExperience.Material topicM=SharedExperience.collect(day,zone,topic,AgentStore.empty(0),new JSONArray());SharedExperience.request(day,topicM,"小鲸",new JSONArray(),"");
  JSONObject topicPage=SharedExperience.entry("{\"moment\":{\"source_id\":\"chat:topic3\",\"reflection\":\"这样的小步子也挺让人高兴。\"}}",day,topicM,at+86400000);
  check(SharedExperience.contextQuotes(topicPage).length()==2&&!topicPage.toString().contains("饼干"),"only two nearby preceding user excerpts retained");
  JSONObject topicState=AgentStore.empty(0);Diary.add(topicState,topicPage);
  check(Diary.context(topicState,"今天又被空白文档卡住了，好想逃",today.plusDays(30)).length()==1,"writing paraphrase recalls older episode via source-linked context");
  check(Diary.context(topicState,"晚饭煮面还是炒饭",today.plusDays(30)).length()==0,"topic cues do not recall unrelated older episode");
  check(SharedExperience.evidence(topicPage).contains("论文")&&Diary.context(topicState,"写论文",today.plusDays(30)).toString().contains("context_quotes"),"retrieved topic is inspectable user evidence, not invented keywords");
  Diary.dropSources(topicState,Collections.singleton("topic1"));
  check(Diary.entries(topicState).length()==1&&!topicState.toString().contains("论文")&&SharedExperience.contextQuotes(topicPage).length()==1,"forgetting contextual source removes its quote but keeps main episode");
  check(Diary.context(topicState,"空白文档卡住",today.plusDays(30)).length()==0,"deleted topic cue no longer contributes to recall");
  topic.get(1).put("at",at-3600000);topic.get(2).put("at",at-1800000);topicM=SharedExperience.collect(day,zone,topic,AgentStore.empty(0),new JSONArray());
  JSONObject distant=SharedExperience.entry("{\"moment\":{\"source_id\":\"chat:topic3\",\"reflection\":\"小步子也不错。\"}}",day,topicM,at+86400000);
  check(SharedExperience.contextQuotes(distant).length()==0,"old conversation before the fifteen-minute window is not attached");
  check(!SharedExperience.recallCheck("小灯塔你还记得吗？").isEmpty()&&SharedExperience.recallCheck("晚饭吃面吧").isEmpty(),"uncertainty cue is conditional on a memory question");
 }
}
