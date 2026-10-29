package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import java.nio.file.*;import java.time.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.json.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class HistoryRecallIntegrationTest {
    long lastWeek;
    @Before public void init()throws Exception{
        AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);Chat.petChats.clear();Chat.presentations.clear();Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();PetService.instance=null;TestKeyStore.install();
        AgentRuntime.memory=new AgentStore(Files.createTempDirectory("historical-agent").toFile());PersonaManager.ensureInitialized("main");PersonaManager.initialize("main","classic");
        HistoryRecall.Plan plan=HistoryRecall.plan("上周聊过什么",System.currentTimeMillis(),ZoneId.systemDefault());lastWeek=plan.from+3600000;
        MemoryBridge.client=(c,p,b,t)->{throw new AssertionError("history lookup must stay on phone");};NativeMemoryIntelligence.transport=(c,m,t)->{throw new AssertionError("no auxiliary recall model needed");};
        AgentRuntime.model=(m,t,p)->{throw new AssertionError("unexpected model request");};
    }
    @After public void cleanup()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();AgentRuntime.model=AgentTransport::request;MemoryBridge.client=MemoryBridge::http;NativeMemoryIntelligence.transport=NativeMemoryIntelligence::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void seed(boolean archive,boolean shown)throws Exception{
        AgentStore db=AgentRuntime.store();db.commit(db.epoch(),"我昨天看了星际穿越，很喜欢","UNSHOWN_REPLY_7391","calm",null,false,false,null,null,"old-movie",lastWeek,null,!shown);
        if(archive)for(int i=0;i<165;i++)db.commit(db.epoch(),"今天的其他话题"+i,"今天回复","calm",null,false,false,null,null,"recent-"+i,System.currentTimeMillis()+i,null,false);
    }
    UiTest.Counter send(String user)throws Exception{UiTest.Counter listener=new UiTest.Counter();assertTrue(AgentRuntime.send(user,false,listener));UiTest.awaitIdle();return listener;}
    JSONObject evidence(JSONArray messages)throws Exception{String text=messages.getJSONObject(messages.length()-1).getString("content");int begin=text.indexOf("{\"historical_chat_search\"");assertTrue("evidence must be in the model request",begin>=0);int end=text.indexOf("\n</背景>",begin);return new JSONObject(text.substring(begin,end).trim()).getJSONObject("historical_chat_search");}
    @Test public void archivedLastWeekReachesModelAndUsesOneGenerationCall()throws Exception{
        seed(true,false);ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);AtomicInteger calls=new AtomicInteger();
        Data.prefs().edit().putString("memory.mode","python-debug").putBoolean("memory.service.enabled",true).putString("memory.service.url","invalid-debug-address").commit();
        AgentRuntime.model=(messages,token,progress)->{calls.incrementAndGet();JSONObject data=evidence(messages);assertTrue(data.toString().contains("星际穿越"));assertFalse(data.toString().contains("今天的其他话题"));assertFalse(messages.toString().contains("UNSHOWN_REPLY_7391"));assertEquals("old-movie",data.getJSONArray("records").getJSONObject(0).getString("id"));return "上周你聊到了星际穿越。";};
        UiTest.Counter listener=send("我们上周聊过什么？");assertEquals(1,listener.done);assertEquals(0,listener.errors);assertEquals(1,calls.get());
    }
    @Test public void emptyRangeAnswersHonestlyWithoutCallingModel()throws Exception{ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);UiTest.Counter listener=send("上周聊过什么");assertEquals(1,listener.done);String answer=AgentRuntime.history().getJSONObject(1).getString("content");assertTrue(answer.contains("没有找到"));assertTrue(answer.contains("不能据此判断我们没聊过"));}
    @Test public void vagueRangeAsksForDateWithoutInventingMemory()throws Exception{UiTest.Counter listener=send("我们之前聊过什么");assertEquals(1,listener.done);assertTrue(AgentRuntime.history().toString().contains("哪段时间"));}
    @Test public void oldQuotesAreNotWrittenBackAsCurrentFacts()throws Exception{
        seed(false,true);ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);
        AgentRuntime.model=(m,t,p)->"{\"reply\":\"上周你聊到了电影。我最爱吃小鱼干\",\"memories\":[\"我喜欢电影\"],\"self\":[\"我最爱吃小鱼干\"]}";
        assertEquals(1,send("上周聊过什么").done);JSONObject s=AgentRuntime.store().snapshot();assertEquals(0,s.getJSONArray("memories").length());assertTrue(s.optJSONArray("self")==null||s.getJSONArray("self").length()==0);
    }
    @Test public void explicitHistoryRequestStillWorksWithAutomaticMemoryDisabled()throws Exception{seed(false,true);Data.prefs().edit().putBoolean("agent.memory",false).commit();ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);AgentRuntime.model=(m,t,p)->{assertTrue(evidence(m).toString().contains("星际穿越"));return "上周你提过星际穿越。";};assertEquals(1,send("上周提过什么电影").done);}
    @Test public void clearedHistoryCannotBeRecalled()throws Exception{seed(true,true);AgentRuntime.store().clear(false);UiTest.Counter listener=send("上周聊过什么");assertEquals(1,listener.done);assertFalse(AgentRuntime.history().toString().contains("星际穿越"));}
    @Test public void deletionWhileAnswerIsPendingPreventsLateCommit()throws Exception{
        seed(false,true);ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "迟到的历史总结";};
        UiTest.Counter listener=new UiTest.Counter();AgentRuntime.send("上周聊过什么",false,listener);try{assertTrue(entered.await(3,TimeUnit.SECONDS));AgentRuntime.store().clear(true);}finally{release.countDown();}UiTest.awaitIdle();assertEquals(0,listener.done);assertEquals(1,listener.errors);assertEquals(0,AgentRuntime.history().length());
    }
    @Test public void offlineLookupShowsOriginalExcerptsWithoutPretendingToSummarize()throws Exception{seed(false,false);UiTest.Counter listener=send("上周聊过什么");assertEquals(1,listener.done);String answer=AgentRuntime.history().getJSONObject(3).getString("content");assertTrue(answer.contains("原文摘录"));assertTrue(answer.contains("星际穿越"));assertFalse(answer.contains("UNSHOWN_REPLY_7391"));}
}
