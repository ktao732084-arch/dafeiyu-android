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
  Data.prefs().edit().putBoolean("agent.memory",false).commit();JSONArray disabledRequest=AgentRuntime.context("你答应提醒我的事",false);String disabled=disabledRequest.getJSONObject(disabledRequest.length()-1).getString("content");
  // Recent conversation/reminder context is independent of the long-term ledger switch.
  // Verify the actual commitment evidence block, including the new empty-result explanation.
  String marker="本轮可核对的约定资料：";assertTrue(disabled.contains(marker));
  JSONObject evidence=new JSONObject(AgentPolicy.envelope(disabled.substring(disabled.indexOf(marker)+marker.length()))),groups=evidence.getJSONObject("双方约定");
  assertEquals(0,groups.getJSONArray("用户答应你的").length());assertEquals(0,groups.getJSONArray("你答应用户的").length());
  assertFalse(evidence.toString().contains("十分钟后我提醒你喝水"));assertFalse(evidence.toString().contains("我答应你明天把初稿发给你"));assertTrue(evidence.toString().contains("本轮没有提供可核对"));
 }
 @Test public void sameDreamCallCanSupplementWithoutProducingADiary()throws Exception{
  LocalDate day=LocalDate.now().minusDays(1);long at=day.atTime(19,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();String quote="这份资料我周末给你。";
  db.commit(db.epoch(),quote,"好","calm",null,true,false,null,null,"dream-source",at,null);assertEquals(0,Commitments.entries(db.snapshot()).length());
  AgentRuntime.diaryModel=(m,t,p)->{assertTrue(m.toString().contains("commitments"));return "{\"commitments\":[{\"source_id\":\"chat:dream-source\",\"speaker\":\"user\",\"quote\":\""+quote+"\"}]}";};
  Data.prefs().edit().putBoolean("agent.diary",true).commit();AgentRuntime.writeDiaryIfDue();AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);
  assertEquals(1,Commitments.entries(db.snapshot()).length());assertEquals(0,Diary.entries(db.snapshot()).length());
 }
 @Test public void cancellationBoundaryRunsBeforePreviewAndPersistence()throws Exception{
  long at=System.currentTimeMillis()-60000;
  db.commit(db.epoch(),"好","我答应你周末讲青松寓言。","calm",null,true,false,null,null,"old-story",at,null,true);db.shown("old-story",999,true);
  db.commitmentStatus(Commitments.entries(db.snapshot()).getJSONObject(0).getString("id"),"cancelled");
  AgentRuntime.model=(m,t,p)->{p.accept("{\"reply\":\"你想听我可以重新讲一个");return "{\"reply\":\"不欠了，不过你想听我可以重新讲一个。\",\"aside\":\"想听我随时讲。\"}";};
  UiTest.Counter result=new UiTest.Counter();assertTrue(AgentRuntime.send("你现在还欠我那个青松寓言吗？",false,result));
  AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
  assertEquals(0,result.errors);assertEquals(1,result.done);assertEquals(0,result.partial);
  JSONArray turns=db.snapshot().getJSONArray("turns");JSONObject saved=turns.getJSONObject(turns.length()-1);
  assertTrue(saved.getString("reply").contains("不再安排"));assertFalse(saved.getString("reply").contains("想听"));assertEquals("",saved.optString("aside"));
  assertEquals("cancelled",Commitments.entries(db.snapshot()).getJSONObject(0).getString("status"));
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
