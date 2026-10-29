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
  String prompt=PromptBuilder.build(in,"在吗",false,db.snapshot(),null).toString();check(prompt.contains("你的日记")&&prompt.contains("外企面试")&&prompt.contains("可能不准"),"the diary reaches the prompt, marked as her own summary");
  in.diary=false;check(!PromptBuilder.build(in,"在吗",false,db.snapshot(),null).toString().contains("你的日记"),"diary off: not used");
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
  System.out.println("PASS: "+checks+" diary checks");
 }
}
