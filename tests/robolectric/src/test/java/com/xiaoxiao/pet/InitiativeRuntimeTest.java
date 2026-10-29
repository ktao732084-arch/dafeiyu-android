package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import org.json.*;import android.app.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class InitiativeRuntimeTest {
    PetService service;PetService.Pet pet;String spoken;
    @Before public void init()throws Exception{
        AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().putBoolean("agent.proactive",true).putBoolean("agent.diary",false).commit();
        AgentRuntime.memory=new AgentStore(Files.createTempDirectory("initiative-runtime").toFile());AgentRuntime.lastTouch=0;Chat.petChats.clear();Chat.presentations.clear();MainActivity.active=false;TestKeyStore.install();PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);
        NativeMemoryIntelligence.transport=(c,m,t)->{throw new java.io.IOException("controlled local memory");};
        service=Robolectric.buildService(PetService.class).get();PetService.instance=service;service.screenOn=true;
        pet=service.new Pet("main","main"){@Override void play(String action){current=action;} @Override void speak(String text,String turn){spoken=text;try{AgentRuntime.store().shown(turn,1);}catch(Exception e){throw new RuntimeException(e);}}};pet.visible=true;pet.docked=false;service.pets.put("main",pet);
        NotificationManager manager=Data.app.getSystemService(NotificationManager.class);Shadows.shadowOf(manager).setNotificationPolicyAccessGranted(true);manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    }
    @After public void cleanup()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();service.handler.removeCallbacksAndMessages(null);PetService.instance=null;MainActivity.active=false;AgentRuntime.model=AgentTransport::request;NativeMemoryIntelligence.transport=NativeMemoryIntelligence::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void due()throws Exception{AgentStore db=AgentRuntime.store();long now=System.currentTimeMillis();db.commit(db.epoch(),"我刚考完试","辛苦啦","calm",null,false,false,null,null,"exam",now-5*60000,null,false);db.interaction("用户点了你",now-4*60000);for(int i=0;i<5;i++)db.reserveAttempt("2026-10-29",now-3*60000);db.initiativeDecision(db.epoch(),"quiet",2,now-3*60000);}
    @Test public void quietUsesRealContextAndSchedulesWithoutCreatingMessage()throws Exception{
        due();int before=AgentRuntime.history().length();AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();assertTrue(m.toString().contains("用户点了你"));assertTrue(m.toString().contains("我刚考完试"));assertTrue(m.getJSONObject(0).getString("content").contains("鲸鱼娘"));return "{\"reply\":\"[安静陪伴]\",\"next_check_minutes\":17}";};
        assertTrue(AgentRuntime.canSpeakNow());long start=System.currentTimeMillis();AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertEquals(1,calls.get());assertEquals(before,AgentRuntime.history().length());assertNull(spoken);JSONObject s=AgentRuntime.store().snapshot();assertEquals("quiet",s.getString("proactiveOutcome"));assertTrue(s.getLong("proactiveNextAt")>=start+17*60000);AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertEquals(1,calls.get());
    }
    @Test public void speaksBeyondOldDailyCapAndStoresNextChoice()throws Exception{
        due();AgentRuntime.model=(m,t,p)->"{\"reply\":\"考试结束啦，想怎么放松？\",\"next_check_minutes\":3}";AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertNotNull(spoken);JSONObject s=AgentRuntime.store().snapshot();JSONArray turns=s.getJSONArray("turns");JSONObject last=turns.getJSONObject(turns.length()-1);assertTrue(last.getBoolean("proactive"));assertEquals(1,last.getInt("displayed"));assertEquals("speak",s.getString("proactiveOutcome"));assertTrue(s.getLong("proactiveNextAt")>System.currentTimeMillis()+170000);
    }
    @Test public void ordinaryReplyAlsoChoosesTimingWithoutExtraCall()throws Exception{
        AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();return "{\"reply\":\"好，过一会儿再聊。\",\"next_check_minutes\":4}";};UiTest.Counter result=new UiTest.Counter();long at=System.currentTimeMillis();assertTrue(AgentRuntime.send("一会儿再聊",false,result));UiTest.awaitIdle();assertEquals(1,result.done);assertEquals(1,calls.get());assertTrue(AgentRuntime.store().snapshot().getLong("proactiveNextAt")>=at+4*60000);
    }
    @Test public void errorsBackOffAndDoNotWriteChat()throws Exception{
        due();int before=AgentRuntime.history().length();AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();throw new java.io.IOException("controlled failure");};AgentRuntime.heartbeat(service);UiTest.awaitIdle();JSONObject s=AgentRuntime.store().snapshot();assertEquals("error",s.getString("proactiveOutcome"));assertTrue(s.getLong("proactiveNextAt")>System.currentTimeMillis()+9*60000);assertEquals(before,AgentRuntime.history().length());AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertEquals(1,calls.get());
    }
    @Test public void hiddenWhileWaitingDoesNotCommitSpeech()throws Exception{
        due();int before=AgentRuntime.history().length();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "{\"reply\":\"接着聊聊考试吧\",\"next_check_minutes\":5}";};AgentRuntime.heartbeat(service);try{assertTrue(entered.await(3,TimeUnit.SECONDS));pet.visible=false;}finally{release.countDown();}UiTest.awaitIdle();assertNull(spoken);assertEquals(before,AgentRuntime.history().length());assertEquals("not-visible",AgentRuntime.store().snapshot().getString("proactiveOutcome"));
    }
    @Test public void clearedHistoryRejectsPendingQuietDecision()throws Exception{
        due();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "{\"reply\":\"[安静陪伴]\",\"next_check_minutes\":2}";};AgentRuntime.heartbeat(service);try{assertTrue(entered.await(3,TimeUnit.SECONDS));AgentRuntime.store().clear(false);}finally{release.countDown();}UiTest.awaitIdle();JSONObject s=AgentRuntime.store().snapshot();assertEquals(0,s.getJSONArray("interactions").length());assertFalse(s.has("proactiveNextAt"));assertEquals(0,AgentRuntime.history().length());
    }
    @Test public void touchIsPersistentAndFocusOrDisabledSwitchBlocksChecks()throws Exception{
        AgentRuntime.touch("用户拖动后把你甩了起来");AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);assertTrue(AgentRuntime.store().snapshot().getJSONArray("interactions").toString().contains("甩了起来"));
        Data.prefs().edit().putLong("agent.focusUntil",System.currentTimeMillis()+60000).commit();assertFalse(AgentRuntime.canSpeakNow());Data.prefs().edit().putLong("agent.focusUntil",0).putBoolean("agent.proactive",false).commit();assertFalse(AgentRuntime.canSpeakNow());
    }
    @Test public void nightHoursAreAnOptionalUserChoice(){assertFalse(AgentRuntime.nightQuiet(23));Data.prefs().edit().putBoolean("agent.quietNight",true).commit();assertTrue(AgentRuntime.nightQuiet(23));assertTrue(AgentRuntime.nightQuiet(7));assertFalse(AgentRuntime.nightQuiet(8));}
}
