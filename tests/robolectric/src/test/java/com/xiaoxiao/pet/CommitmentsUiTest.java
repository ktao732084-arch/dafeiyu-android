package com.xiaoxiao.pet;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import android.app.*;import android.view.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.concurrent.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class CommitmentsUiTest {
 MainActivity a;AgentStore db;AgentRuntime.Model original,diary;
 @Before public void setup()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
  Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().putBoolean("agent.diary",false).putBoolean("agent.proactive",false).commit();TestKeyStore.install();PetService.instance=null;
  AgentRuntime.memory=null;a=UiTest.ready();AgentRuntime.memory=db=new AgentStore(Files.createTempDirectory("commitment-ui").toFile());ModelSettings.save("https://example.invalid/v1","controlled","test-only-key",false);original=AgentRuntime.model;diary=AgentRuntime.diaryModel;}
 @After public void close()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.model=original;AgentRuntime.diaryModel=diary;a.finish();}
 @Test public void actualRuntimeKeepsBothOwnersEvenWhenModelReturnsNoMemoryFields()throws Exception{
  AgentRuntime.model=(m,t,p)->"{\"reply\":\"十分钟后我提醒你喝水。\"}";
  assertTrue(AgentRuntime.send("我答应你明天把初稿发给你。",false,new UiTest.Counter()));AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
  assertEquals(1,Commitments.entries(db.snapshot()).length());JSONObject turn=db.snapshot().getJSONArray("turns").getJSONObject(0);db.shown(turn.getString("id"),999,true);db.awaitWrites();
  JSONArray promises=Commitments.entries(db.snapshot());assertEquals(2,promises.length());assertEquals("user",promises.getJSONObject(0).getString("speaker"));assertEquals("assistant",promises.getJSONObject(1).getString("speaker"));
  assertTrue(AgentRuntime.context("你答应提醒我的事",false).toString().contains("十分钟后我提醒你喝水"));
  assertTrue(AgentRuntime.context("你答应提醒我的事",false).toString().contains("你答应用户的"));
  Data.prefs().edit().putBoolean("agent.memory",false).commit();assertFalse(AgentRuntime.context("你答应提醒我的事",false).toString().contains("你答应用户的"));
 }
 @Test public void sameDreamCallCanSupplementWithoutProducingADiary()throws Exception{
  LocalDate day=LocalDate.now().minusDays(1);long at=day.atTime(19,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();String quote="这份资料我周末给你。";
  db.commit(db.epoch(),quote,"好","calm",null,true,false,null,null,"dream-source",at,null);assertEquals(0,Commitments.entries(db.snapshot()).length());
  AgentRuntime.diaryModel=(m,t,p)->{assertTrue(m.toString().contains("commitments"));return "{\"commitments\":[{\"source_id\":\"chat:dream-source\",\"speaker\":\"user\",\"quote\":\""+quote+"\"}]}";};
  Data.prefs().edit().putBoolean("agent.diary",true).commit();AgentRuntime.writeDiaryIfDue();AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
  assertEquals(1,Commitments.entries(db.snapshot()).length());assertEquals(0,Diary.entries(db.snapshot()).length());
 }
 @Test public void notebookMarksStatusAndDeletesWithoutChangingTheOriginalChat()throws Exception{
  String quote="我答应你明天把初稿发给你。";db.commit(db.epoch(),quote,"十分钟后我提醒你喝水。","calm",null,true,false);
  MemoryPanel.commitments(a);AlertDialog page=(AlertDialog)UiTest.dialog();View root=page.getWindow().getDecorView();UiTest.click(root,"你答应的 · 待确认\n"+quote);
  AlertDialog detail=(AlertDialog)UiTest.dialog();assertTrue(UiTest.texts(detail.getWindow().getDecorView()).contains("说于"));detail.getButton(-1).performClick();ShadowLooper.idleMainLooper();
  AlertDialog menu=(AlertDialog)UiTest.dialog();menu.getListView().performItemClick(null,0,0);ShadowLooper.idleMainLooper();UiTest.storage();
  assertTrue(page.isShowing());assertNotNull(UiTest.find(root,"你答应的 · 已标记完成\n"+quote));
  UiTest.click(root,"你答应的 · 已标记完成\n"+quote);((AlertDialog)UiTest.dialog()).getButton(-1).performClick();ShadowLooper.idleMainLooper();
  ((AlertDialog)UiTest.dialog()).getListView().performItemClick(null,3,3);ShadowLooper.idleMainLooper();AlertDialog confirm=(AlertDialog)UiTest.dialog();
  assertEquals(2,Commitments.entries(db.snapshot()).length());confirm.getButton(-1).performClick();ShadowLooper.idleMainLooper();UiTest.storage();
  assertEquals(1,Commitments.entries(db.snapshot()).length());assertEquals(1,db.snapshot().getJSONArray("turns").length());
 }
 @Test public void dreamPreservesUpstreamPhraseLearningAlongsidePromises()throws Exception{
  LocalDate day=LocalDate.now().minusDays(1);long at=day.atTime(19,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();String quote="这份资料我周末给你。";
  db.commit(db.epoch(),quote+"先卷一点点。","好","calm",null,true,false,null,null,"both",at,null);
  AgentRuntime.diaryModel=(m,t,p)->{assertTrue(m.toString().contains("phrases")&&m.toString().contains("commitments"));return "{\"diary\":\"记下了周末给资料这件事。\",\"phrases\":[{\"phrase\":\"先卷一点点\",\"when\":\"鼓励自己时\"}],\"commitments\":[{\"source_id\":\"chat:both\",\"speaker\":\"user\",\"quote\":\""+quote+"\"}]}";};
  Data.prefs().edit().putBoolean("agent.diary",true).commit();AgentRuntime.writeDiaryIfDue();AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
  assertEquals(1,Diary.entries(db.snapshot()).length());assertEquals(1,Phrases.entries(db.snapshot()).length());assertEquals(1,Commitments.entries(db.snapshot()).length());
 }
}
