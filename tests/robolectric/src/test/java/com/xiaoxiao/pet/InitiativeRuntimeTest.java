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
    @Test public void proactiveTurnKeepsTheCarriedMoodsCause()throws Exception{
        due();AgentStore db=AgentRuntime.store();db.commit(db.epoch(),"我考过了","哼，我早说你可以。","happy",null,true,false,null,null,"passed",System.currentTimeMillis()-60000,null,false,null,null,2,"替TA考过高兴",Boolean.TRUE);
        AgentRuntime.model=(m,t,p)->"{\"reply\":\"晚上要不要庆祝一下？\",\"emotion\":\"happy\",\"mood_level\":2,\"mood_cause\":\"\",\"mood_changed\":false,\"next_check_minutes\":30}";
        AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertNotNull(spoken);JSONObject s=AgentRuntime.store().snapshot();assertEquals("替TA考过高兴",s.getString("moodCause"));assertEquals("passed",s.getString("moodSource"));
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
    @Test public void busySurvivesRestartAndTouchThenFreeUsesModelChoice()throws Exception{
        AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();return "{\"reply\":\"好，等你回来\",\"next_check_minutes\":60}";};
        UiTest.Counter first=new UiTest.Counter();AgentRuntime.send("我现在有点忙，等忙完了再说",false,first);UiTest.awaitIdle();assertEquals(1,first.done);
        JSONObject busy=AgentRuntime.store().snapshot();assertTrue(busy.getBoolean("proactiveWaiting"));assertFalse(busy.has("proactiveNextAt"));
        AgentRuntime.touch("用户点了你");AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);assertFalse(AgentRuntime.store().initiativeDue(System.currentTimeMillis()+86400000,0));AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertEquals(1,calls.get());
        AgentRuntime.model=(m,t,p)->{assertTrue(m.toString().contains("waiting_for_user"));return "{\"reply\":\"忙完啦，聊个轻松的\",\"next_check_minutes\":3}";};
        UiTest.Counter second=new UiTest.Counter();AgentRuntime.send("我忙完了，现在可以聊天了",false,second);UiTest.awaitIdle();assertEquals(1,second.done);JSONObject free=AgentRuntime.store().snapshot();assertFalse(free.getBoolean("proactiveWaiting"));assertEquals(3,free.getJSONObject("initiativePlan").getInt("minutes"));
    }
    @Test public void missingPlanRepairsOnceWithoutReplacingTheVisibleReply()throws Exception{
        AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->calls.incrementAndGet()==1?"欢迎回来，我们聊聊吧。":"{\"reply\":\"不应替换原文\",\"next_check_minutes\":4}";
        UiTest.Counter c=new UiTest.Counter();AgentRuntime.send("我忙完了",false,c);UiTest.awaitIdle();assertEquals(2,calls.get());assertEquals(1,c.done);JSONObject s=AgentRuntime.store().snapshot();assertEquals("model_repair",s.getJSONObject("initiativePlan").getString("source"));assertEquals("欢迎回来，我们聊聊吧。",s.getJSONArray("turns").getJSONObject(0).getString("reply"));
    }
    @Test public void failedRepairKeepsChatButDoesNotInventASchedule()throws Exception{
        AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();return "普通文本，缺少时间";};UiTest.Counter c=new UiTest.Counter();AgentRuntime.send("我忙完了",false,c);UiTest.awaitIdle();assertEquals(2,calls.get());assertEquals(1,c.done);assertTrue(AgentRuntime.store().snapshot().getBoolean("proactiveAwaitingPlan"));assertFalse(AgentRuntime.store().snapshot().has("proactiveNextAt"));
    }
    @Test public void silenceAnnouncementsNeverBecomeAnotherNotification()throws Exception{
        due();int before=AgentRuntime.history().length();AgentRuntime.model=(m,t,p)->"{\"reply\":\"你还没接吃什么那茬，我也不催。\",\"next_check_minutes\":47,\"topic\":\"午饭\"}";
        AgentRuntime.heartbeat(service);UiTest.awaitIdle();assertNull(spoken);assertEquals(before,AgentRuntime.history().length());assertEquals("quiet",AgentRuntime.store().snapshot().getString("proactiveOutcome"));assertEquals(47,AgentRuntime.store().snapshot().getJSONObject("initiativePlan").getInt("minutes"));
    }
    @Test public void cancelledRepairCannotRestoreClearedAvailability()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),exit=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();AgentRuntime.model=(m,t,p)->{if(calls.incrementAndGet()==1)return "回来啦";entered.countDown();exit.await(5,TimeUnit.SECONDS);return "{\"reply\":\"回来啦\",\"next_check_minutes\":2}";};
        UiTest.Counter c=new UiTest.Counter();AgentRuntime.send("我忙完了",false,c);assertTrue(entered.await(5,TimeUnit.SECONDS));AgentRuntime.cancel();AgentRuntime.store().clear(false);exit.countDown();UiTest.awaitIdle();assertEquals(0,AgentRuntime.history().length());assertFalse(AgentRuntime.store().snapshot().has("initiativePlan"));
    }
}
