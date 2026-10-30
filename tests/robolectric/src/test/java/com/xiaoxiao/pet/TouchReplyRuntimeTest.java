package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import org.json.*;import android.app.*;import android.view.*;import android.widget.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class TouchReplyRuntimeTest {
 public static class TestService extends PetService {@Override void requestMotion(){}}
 TestService service;PetService.Pet pet;String spoken;AtomicInteger calls;
 @Before public void init()throws Exception{
  AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().putBoolean("agent.memory",false).putBoolean("agent.diary",false).commit();TestKeyStore.install();PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);AgentRuntime.memory=new AgentStore(Files.createTempDirectory("touch-runtime").toFile());
  MainActivity.active=false;Chat.petChats.clear();Chat.presentations.clear();service=Robolectric.buildService(TestService.class).get();service.screenOn=true;service.screenWidth=1080;service.screenHeight=1920;service.safeBottom=1920;PetService.instance=service;
  pet=service.new Pet("main","main"){
   @Override void play(String action){current=action;}
   @Override void grow(String reaction){}
   @Override void dockAtEdge(boolean right,float center,float x,float y){docked=true;dragging=false;TouchReply.cancel(this);}
   @Override void speak(String text,String id){spoken=text;TouchReply.cancel(this);try{AgentRuntime.store().shown(id,1);}catch(Exception e){throw new RuntimeException(e);}}
  };pet.visible=true;pet.frame=new FrameLayout(Data.app);service.pets.put("main",pet);
  NotificationManager nm=Data.app.getSystemService(NotificationManager.class);Shadows.shadowOf(nm).setNotificationPolicyAccessGranted(true);nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);calls=new AtomicInteger();
  AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();assertTrue(m.toString().contains("interaction_event"));assertTrue(m.toString().contains("鲸鱼娘"));return "{\"reply\":\"我在呢，慢点戳。\",\"emotion\":\"playful\"}";};
 }
 @After public void cleanup()throws Exception{TouchReply.cancel(pet);AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);service.handler.removeCallbacksAndMessages(null);PetService.instance=null;MainActivity.active=false;AgentRuntime.model=AgentTransport::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
 void gestures()throws Exception{for(int i=0;i<3;i++)pet.userInteraction("tap","用户轻轻点了你",true);AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);}
 void settle()throws Exception{ShadowLooper.idleMainLooper(2,TimeUnit.SECONDS);UiTest.awaitIdle();}
 void motion(int action,float x,float y){android.view.MotionEvent event=android.view.MotionEvent.obtain(0,android.os.SystemClock.uptimeMillis(),action,x,y,0);pet.touch(event);event.recycle();}
 @Test public void gestureBurstSpeaksOnceEvenWithIdleChatDisabled()throws Exception{
  gestures();assertEquals(0,calls.get());settle();assertEquals(1,calls.get());assertEquals("我在呢，慢点戳。",spoken);JSONObject state=AgentRuntime.store().snapshot(),turn=state.getJSONArray("turns").getJSONObject(0);assertTrue(turn.has("interaction"));assertEquals(1,turn.getInt("displayed"));assertEquals(0,state.optInt("userTurns"));
  ShadowLooper.idleMainLooper(1,TimeUnit.MINUTES);assertEquals(1,calls.get());
 }
 @Test public void singleTapIsRecordedButDoesNotRequestAModel()throws Exception{pet.userInteraction("tap","用户轻轻点了你",true);AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);settle();assertEquals(0,calls.get());assertEquals(1,AgentRuntime.store().snapshot().getJSONArray("interactions").length());}
 @Test public void disabledAndBusyStatesDoNotReplyOrClearWaiting()throws Exception{
  Data.prefs().edit().putBoolean("agent.touchReply",false).commit();gestures();settle();assertEquals(0,calls.get());
  Data.prefs().edit().putBoolean("agent.touchReply",true).commit();AgentStore db=AgentRuntime.store();InitiativePlan plan=InitiativePlan.parse(new JSONObject().put("availability","busy").put("availability_evidence","我在忙，等忙完再聊").put("next_check_minutes",40));plan.ground("我在忙，等忙完再聊",false);db.commit(db.epoch(),"我在忙，等忙完再聊","好","calm",null,false,false,null,null,"busy",System.currentTimeMillis(),null,false,null,plan);
  gestures();settle();assertEquals(0,calls.get());assertTrue(db.snapshot().getBoolean("proactiveWaiting"));
 }
 @Test public void playingOrExistingBubbleWaitsAndNewTouchesAreCombined()throws Exception{
  pet.busy=true;gestures();settle();assertEquals(0,calls.get());pet.busy=false;pet.bubble=new TextView(Data.app);settle();assertEquals(0,calls.get());pet.bubble=null;settle();assertEquals(1,calls.get());
  gestures();settle();assertEquals(1,calls.get());
 }
 @Test public void hiddenWindowAndClearRejectLateReplies()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),done=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();done.await(5,TimeUnit.SECONDS);return "迟到回应";};gestures();ShadowLooper.idleMainLooper(2,TimeUnit.SECONDS);assertTrue(entered.await(3,TimeUnit.SECONDS));pet.visible=false;AgentRuntime.store().clear(false);done.countDown();UiTest.awaitIdle();assertNull(spoken);assertEquals(0,AgentRuntime.history().length());
 }
 @Test public void userChatPreemptsPendingTouchRequest()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger n=new AtomicInteger();AgentRuntime.model=(m,t,p)->{if(n.incrementAndGet()==1){entered.countDown();done.await(5,TimeUnit.SECONDS);return "迟到触摸回复";}return "好，继续聊。";};gestures();ShadowLooper.idleMainLooper(2,TimeUnit.SECONDS);assertTrue(entered.await(3,TimeUnit.SECONDS));UiTest.Counter response=new UiTest.Counter();assertTrue(AgentRuntime.send("我回来啦",false,response));done.countDown();UiTest.awaitIdle();assertEquals(1,response.done);assertNull(spoken);JSONArray turns=AgentRuntime.store().snapshot().getJSONArray("turns");assertEquals(1,turns.length());assertEquals("我回来啦",turns.getJSONObject(0).getString("user"));
 }
 @Test public void actualTapDragAndEdgeGesturesHaveTypedRecords()throws Exception{
  Data.prefs().edit().putBoolean("agent.touchReply",false).putBoolean("physics",false).commit();motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_UP,200,200);
  motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,230);motion(MotionEvent.ACTION_UP,300,230);
  motion(MotionEvent.ACTION_DOWN,900,200);motion(MotionEvent.ACTION_MOVE,1,200);motion(MotionEvent.ACTION_UP,1,200);
  AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);JSONArray events=AgentRuntime.store().snapshot().getJSONArray("interactions");assertEquals(5,events.length());assertEquals("tap",events.getJSONObject(0).getString("kind"));assertEquals("lift",events.getJSONObject(1).getString("kind"));assertEquals("drag",events.getJSONObject(2).getString("kind"));assertEquals("lift",events.getJSONObject(3).getString("kind"));assertEquals("dock",events.getJSONObject(4).getString("kind"));assertEquals(0,calls.get());
 }
 @Test public void cancelledDragIsRecordedWithoutPretendingItCompleted()throws Exception{motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,200);motion(MotionEvent.ACTION_CANCEL,300,200);AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);assertEquals("cancel",AgentRuntime.store().snapshot().getJSONArray("interactions").getJSONObject(1).getString("kind"));assertEquals(0,pet.touchReplies.count);}
 @Test public void quietModelAndOverlongReplyStaySilent()throws Exception{AgentRuntime.model=(m,t,p)->"[安静陪伴]";gestures();settle();assertNull(spoken);assertEquals(0,AgentRuntime.history().length());}
 @Test public void holdingSpeaksInFrozenPersonaBeforeReleaseAndOnlyOnce()throws Exception{
  final String persona=PersonaManager.prompt("main");
  AgentRuntime.model=(messages,t,p)->{calls.incrementAndGet();String prompt=messages.toString();assertTrue(prompt.contains("durationMs"));assertTrue(prompt.contains("仍在提着"));assertTrue(messages.getJSONObject(0).getString("content").contains(persona));return "先把本鲸放下来，我又不是钥匙扣。";};
  motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,250);pet.busy=true;
  ShadowLooper.idleMainLooper(4900,TimeUnit.MILLISECONDS);assertEquals(0,calls.get());
  ShadowLooper.idleMainLooper(200,TimeUnit.MILLISECONDS);UiTest.awaitIdle();assertEquals(1,calls.get());assertTrue(pet.dragging&&pet.lift.active);assertEquals("先把本鲸放下来，我又不是钥匙扣。",spoken);
  JSONObject state=AgentRuntime.store().snapshot(),evidence=state.getJSONArray("turns").getJSONObject(0).getJSONObject("interaction");assertEquals("holding",evidence.getString("phase"));assertTrue(evidence.getLong("durationMs")>=5000);assertEquals(1,evidence.getInt("count"));assertEquals(0,state.optInt("userTurns"));assertEquals(persona,PersonaManager.prompt("main"));
  ShadowLooper.idleMainLooper(30,TimeUnit.SECONDS);assertEquals(1,calls.get());
 }
 @Test public void briefLiftDoesNotCallTheModel()throws Exception{
  motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,250);ShadowLooper.idleMainLooper(2,TimeUnit.SECONDS);motion(MotionEvent.ACTION_UP,300,250);settle();assertEquals(0,calls.get());assertFalse(pet.lift.active);
 }
 @Test public void threeShortDragsStillBuildTheOriginalInteractionBurst()throws Exception{
  Data.prefs().edit().putBoolean("physics",false).commit();
  for(int i=0;i<3;i++){motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,240,210);motion(MotionEvent.ACTION_UP,240,210);}
  assertEquals(3,pet.touchReplies.count);settle();assertEquals(1,calls.get());
  assertEquals(3,AgentRuntime.store().snapshot().getJSONArray("turns").getJSONObject(0).getJSONObject("interaction").getInt("count"));
 }
 @Test public void releaseCancelsPendingHoldingReply()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),done=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();done.await(5,TimeUnit.SECONDS);return "仍然提着我的迟到回复";};
  motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,250);ShadowLooper.idleMainLooper(5100,TimeUnit.MILLISECONDS);assertTrue(entered.await(3,TimeUnit.SECONDS));motion(MotionEvent.ACTION_UP,300,250);done.countDown();UiTest.awaitIdle();assertNull(spoken);assertEquals(0,AgentRuntime.history().length());
 }
 @Test public void disabledSettingAlsoSilencesHolding()throws Exception{
  Data.prefs().edit().putBoolean("agent.touchReply",false).commit();motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,250);ShadowLooper.idleMainLooper(10,TimeUnit.SECONDS);UiTest.awaitIdle();assertEquals(0,calls.get());assertTrue(pet.lift.active);
 }
 @Test public void typingPreemptsTheHoldingReply()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger n=new AtomicInteger();AgentRuntime.model=(m,t,p)->{if(n.incrementAndGet()==1){entered.countDown();done.await(5,TimeUnit.SECONDS);return "迟到提起回复";}return "好，继续聊。";};
  motion(MotionEvent.ACTION_DOWN,200,200);motion(MotionEvent.ACTION_MOVE,300,250);ShadowLooper.idleMainLooper(5100,TimeUnit.MILLISECONDS);assertTrue(entered.await(3,TimeUnit.SECONDS));UiTest.Counter response=new UiTest.Counter();assertTrue(AgentRuntime.send("继续聊正事",false,response));done.countDown();UiTest.awaitIdle();assertNull(spoken);assertEquals(1,response.done);assertEquals(1,AgentRuntime.store().snapshot().getJSONArray("turns").length());
 }

 @Test public void grabRegionIsChosenOnDownAndStaysFixedDuringDrag()throws Exception{
  float px=pet.x+pet.width*.5f,head=pet.y+(pet.normT+.25f*(pet.normB-pet.normT))*pet.width*.75f;
  motion(MotionEvent.ACTION_DOWN,px,head);motion(MotionEvent.ACTION_MOVE,px+50,head+100);assertEquals("head",pet.lift.region);motion(MotionEvent.ACTION_CANCEL,px+50,head+100);
  float waist=pet.y+(pet.normT+.8f*(pet.normB-pet.normT))*pet.width*.75f;px=pet.x+pet.width*.5f;
  motion(MotionEvent.ACTION_DOWN,px,waist);motion(MotionEvent.ACTION_MOVE,px+50,waist-100);assertEquals("waist",pet.lift.region);
  motion(MotionEvent.ACTION_MOVE,px+60,waist-200);assertEquals("waist",pet.lift.region);
 }
 @Test public void waistHoldPromptCarriesTheActualGrabRegion()throws Exception{
  AgentRuntime.model=(messages,t,p)->{calls.incrementAndGet();String body=messages.toString();assertTrue(body.contains("grabRegion"));assertTrue(body.contains("waist"));assertTrue(body.contains("后腰"));return "别拎我的围裙后面啦。";};
  float px=pet.x+pet.width*.5f,py=pet.y+(pet.normT+.8f*(pet.normB-pet.normT))*pet.width*.75f;
  motion(MotionEvent.ACTION_DOWN,px,py);motion(MotionEvent.ACTION_MOVE,px+30,py-40);ShadowLooper.idleMainLooper(5100,TimeUnit.MILLISECONDS);UiTest.awaitIdle();
  assertEquals(1,calls.get());JSONObject interaction=AgentRuntime.store().snapshot().getJSONArray("turns").getJSONObject(0).getJSONObject("interaction");assertEquals("waist",interaction.getString("grabRegion"));assertEquals("waist",interaction.getJSONArray("events").getJSONObject(0).getString("grabRegion"));
 }

}
