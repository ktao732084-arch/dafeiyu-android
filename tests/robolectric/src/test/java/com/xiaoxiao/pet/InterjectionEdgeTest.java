package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import org.json.*;import java.util.*;import java.util.concurrent.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class InterjectionEdgeTest {
 UiTest fixture=new UiTest();MainActivity activity;ChatPage page;
 @Before public void setup()throws Exception{fixture.init();activity=UiTest.ready();Data.prefs().edit().putBoolean("agent.memory",false).putBoolean("agent.diary",false).commit();ModelSettings.save("https://example.invalid/v1","controlled","test-only-key",false);page=new ChatPage(activity,"main","main");page.open();}
 @After public void close()throws Exception{page.dialog.dismiss();AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);activity.pet.release();fixture.cleanup();}
 void send(String text){page.input.setText(text);page.send();}
 @Test public void failureKeepsTheUnsentFollowupToo()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);throw new java.io.IOException("controlled failure");};
  send("我今天有点累");try{assertTrue(entered.await(3,TimeUnit.SECONDS));page.input.setText("还有一句正在打");}finally{release.countDown();}UiTest.awaitIdle();
  assertEquals("我今天有点累\n还有一句正在打",page.input.getText().toString());assertFalse(page.sending);assertEquals(0,AgentRuntime.store().historyPage(null,20).length());
 }
 @Test public void alreadySavedAnswerKeepsQueuedAndCurrentlyTypedMessages()throws Exception{
  AgentRuntime.model=(m,t,p)->"第一条我收到了。";send("第一条消息");AgentRuntime.worker.submit(()->{}).get(8,TimeUnit.SECONDS);
  assertEquals(1,AgentRuntime.store().historyPage(null,20).length());send("第二条补充");page.input.setText("第三条还没发");ShadowLooper.idleMainLooper();
  assertEquals("第二条补充\n第三条还没发",page.input.getText().toString());JSONArray turns=AgentRuntime.store().historyPage(null,20);assertEquals(1,turns.length());assertEquals("第一条消息",turns.getJSONObject(0).getString("user"));assertTrue(turns.getJSONObject(0).optInt("displayed")>0);
 }
 @Test public void threeRapidMessagesCommitOneJoinedTurn()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);List<String> requests=new CopyOnWriteArrayList<>();AgentRuntime.model=(m,t,p)->{requests.add(m.getJSONObject(m.length()-1).getString("content"));if(requests.size()==1){entered.countDown();release.await(5,TimeUnit.SECONDS);t.check();}return "这几句都听到了。";};
  send("第一句");try{assertTrue(entered.await(3,TimeUnit.SECONDS));send("第二句");send("第三句");}finally{release.countDown();}UiTest.awaitIdle();
  JSONArray turns=AgentRuntime.store().historyPage(null,20);assertEquals(1,turns.length());assertEquals("第一句\n第二句\n第三句",turns.getJSONObject(0).getString("user"));assertEquals(2,requests.size());assertFalse(UiTest.texts(page.rows).contains("已停止回复"));assertFalse(page.sending);
 }
 @Test public void ordinaryCompletionDoesNotEraseTyping()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "好，我在听。";};
  send("先说这一句");try{assertTrue(entered.await(3,TimeUnit.SECONDS));page.input.setText("正在打下一句");}finally{release.countDown();}UiTest.awaitIdle();assertEquals("正在打下一句",page.input.getText().toString());
 }
 @Test public void restoringMultipleMessagesDoesNotHitTheSingleMessageLimit()throws Exception{
  String first=String.join("",Collections.nCopies(5000,"甲")),second=String.join("",Collections.nCopies(5000,"乙"));CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);throw new java.io.IOException("controlled failure");};
  send(first);try{assertTrue(entered.await(3,TimeUnit.SECONDS));page.input.setText(second);}finally{release.countDown();}UiTest.awaitIdle();assertEquals(first+"\n"+second,page.input.getText().toString());
 }
}
