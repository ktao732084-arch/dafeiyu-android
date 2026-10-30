package com.xiaoxiao.pet;

import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import android.app.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class SharedExperienceUiTest {
 MainActivity a;File dir;GomokuStore games;LocalDate day;long at;Gomoku game;AgentRuntime.Model originalDiary;
 @Before public void init()throws Exception{
  AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
  Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().putBoolean("agent.diary",false).commit();TestKeyStore.install();AgentRuntime.memory=null;PetService.instance=null;
  a=UiTest.ready();dir=Files.createTempDirectory("shared-runtime").toFile();AgentRuntime.memory=new AgentStore(dir);games=new GomokuStore(dir);day=LocalDate.now().minusDays(1);at=day.atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();game=game(at);
  originalDiary=AgentRuntime.diaryModel;ModelSettings.save("https://example.invalid/v1","controlled","test-only-key",false);
 }
 @After public void cleanup()throws Exception{AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.diaryModel=originalDiary;AgentRuntime.cancel();a.finish();Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
 Gomoku game(long when)throws Exception{Gomoku g=new Gomoku(UUID.randomUUID().toString(),when,ZoneId.systemDefault().getId());for(int p:new int[]{0,15,1,16,2,17,3,18,4})g.play(p);g.finishedAt=when+1000;ChatArchive.write(new File(games.results,g.id+".json"),g.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return g;}
 String answer(){return "{\"diary\":\"一起下棋的这天值得记下来。\",\"moment\":{\"source_id\":\"game:"+game.id+"\",\"reflection\":\"现在回想这盘棋，还是想再试一次。\"}}";}
 void awaitDiary()throws Exception{AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();}
 @Test public void openingAppWritesGameOnlyDayOnceAndLeavesPersonaAndMoodAlone()throws Exception{
  String persona=PersonaManager.prompt("main");JSONObject before=AgentRuntime.store().snapshot();AtomicInteger calls=new AtomicInteger();
  AgentRuntime.diaryModel=(m,t,p)->{calls.incrementAndGet();assertTrue(m.toString().contains("game:"+game.id));assertTrue(new JSONObject(m.getJSONObject(1).getString("content")).getJSONArray("conversation").length()==0);return answer();};
  Data.prefs().edit().putBoolean("agent.diary",true).commit();a.onResume();awaitDiary();
  JSONObject s=AgentRuntime.store().snapshot();assertEquals(1,Diary.entries(s).length());assertEquals(0,s.getJSONArray("memories").length());assertEquals(before.optString("emotion"),s.optString("emotion"));assertEquals(persona,PersonaManager.prompt("main"));
  AgentRuntime.writeDiaryIfDue();awaitDiary();assertEquals(1,calls.get());assertEquals(LocalDate.now().toString(),Data.prefs().getString("diary.attemptedOn",""));
 }
 @Test public void clearingDuringGenerationRejectsTheLatePageAndItsEvidence()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AgentRuntime.diaryModel=(m,t,p)->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return answer();};Data.prefs().edit().putBoolean("agent.diary",true).commit();
  AgentRuntime.writeDiaryIfDue();assertTrue(entered.await(5,TimeUnit.SECONDS));try{AgentRuntime.store().clearDiary();}finally{release.countDown();}awaitDiary();
  assertEquals(0,Diary.entries(AgentRuntime.store().snapshot()).length());assertFalse(AgentRuntime.store().snapshot().toString().contains(game.id));
  AgentStore reopened=new AgentStore(dir);assertTrue(reopened.snapshot().optLong("diaryClearedAt")>at);assertEquals(0,Diary.entries(reopened.snapshot()).length());
 }
 @Test public void switchesAndNoUsefulSourcesDoNotSpendDiaryCalls()throws Exception{
  AtomicInteger calls=new AtomicInteger();AgentRuntime.diaryModel=(m,t,p)->{calls.incrementAndGet();return answer();};
  AgentRuntime.writeDiaryIfDue();awaitDiary();assertEquals(0,calls.get());
  Data.prefs().edit().putBoolean("agent.diary",true).putBoolean("agent.memory",false).commit();AgentRuntime.writeDiaryIfDue();awaitDiary();assertEquals(0,calls.get());
  Data.prefs().edit().putBoolean("agent.memory",true).commit();AgentRuntime.store().clearDiary();AgentRuntime.writeDiaryIfDue();awaitDiary();assertEquals(0,calls.get());
 }
 @Test public void forgettingContextSourcePurgesItsCueButKeepsTheSelectedMoment()throws Exception{
  AgentStore db=AgentRuntime.store();List<JSONObject> turns=new ArrayList<>();String[] words={"我在写论文，但卡住了","这次只完成两百字","给这个小行动起名叫星火吧"};
  for(int i=0;i<words.length;i++){JSONObject t=new JSONObject().put("id","cue"+i).put("at",at+i*60000).put("user",words[i]).put("reply","好");turns.add(t);}
  JSONObject plan=NativeMemoryEngine.evaluate(db.snapshot(),new JSONArray().put(MemoryLedger.record("essay",words[0],"cue0",false,at)),"",at,ZoneId.systemDefault(),null,new AgentTransport.Token()).state;
  assertTrue(db.commit(db.epoch(),words[0],"好","calm",null,true,false,null,new MemoryBridge.Result(plan,null,false,"",new JSONArray()),"cue0",at,null));
  SharedExperience.Material m=SharedExperience.collect(day,ZoneId.systemDefault(),turns,db.snapshot(),new JSONArray());SharedExperience.request(day,m,"大肥鱼",new JSONArray(),"");
  JSONObject page=SharedExperience.entry("{\"moment\":{\"source_id\":\"chat:cue2\",\"reflection\":\"这样也挺好。\"}}",day,m,at+86400000);
  assertTrue(db.addDiary(db.epoch(),page));assertTrue(db.snapshot().toString().contains("论文"));db.forget("essay");
  JSONObject saved=new AgentStore(dir).snapshot();assertEquals(1,Diary.entries(saved).length());assertFalse(saved.toString().contains("论文"));
  assertFalse(new File(dir,"companion-v1.json.bak").exists());
  JSONObject stale=new JSONObject(page.toString()).put("date",day.minusDays(1).toString());
  assertFalse(db.addDiary(db.epoch(),stale)); // Even with the current epoch, a deleted cue source cannot return.
 }
 @Test public void notebookPagesSearchesShowsEvidenceAndDeletesOnlyThePage()throws Exception{
  AgentStore db=AgentRuntime.store();SharedExperience.Material material=SharedExperience.collect(day,ZoneId.systemDefault(),Collections.emptyList(),db.snapshot(),games.resultsOn(day,ZoneId.systemDefault()));
  JSONObject page=SharedExperience.entry(answer(),day,material,System.currentTimeMillis());assertTrue(db.addDiary(db.epoch(),page));
  for(int i=1;i<=24;i++)assertTrue(db.addDiary(db.epoch(),Diary.entry(day.minusDays(i),"旧日记记录海边的聊天。",Collections.emptyList(),at-i)));
  MemoryPanel.diary(a);AlertDialog diary=(AlertDialog)UiTest.dialog();View root=diary.getWindow().getDecorView();assertNotNull(UiTest.find(root,"再显示 5 篇（共 25 篇）"));
  assertNull(UiTest.find(root,day.minusDays(24).toString()));UiTest.click(root,"再显示 5 篇（共 25 篇）");assertNotNull(UiTest.find(root,day.minusDays(24).toString()));
  EditText search=(EditText)UiTest.description(root,"搜索经历或日期");search.setText("五子棋");ShadowLooper.idleMainLooper();assertNotNull(UiTest.find(root,"发生的事：五子棋对局：你赢，她输。"));assertFalse(UiTest.texts(root).contains("旧日记记录海边"));
  UiTest.click(root,"查看依据");AlertDialog evidence=(AlertDialog)UiTest.dialog();assertTrue(UiTest.texts(evidence.getWindow().getDecorView()).contains("回想整理于"));evidence.getButton(-1).performClick();
  UiTest.click(root,"删除这篇");AlertDialog confirm=(AlertDialog)UiTest.dialog();assertEquals(25,Diary.entries(db.snapshot()).length());confirm.getButton(-1).performClick();ShadowLooper.idleMainLooper();UiTest.storage();
  assertTrue(diary.isShowing());assertNotNull(UiTest.find(root,"没有找到相关经历。"));assertEquals(24,Diary.entries(db.snapshot()).length());assertTrue(games.hasResult(game.id,game.finishedAt,game.outcome()));
  assertTrue(Diary.suppressed(db.snapshot(),day));assertFalse(db.addDiary(db.epoch(),page));
 }
}
