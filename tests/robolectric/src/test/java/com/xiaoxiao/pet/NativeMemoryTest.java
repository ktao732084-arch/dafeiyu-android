package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import java.io.*;import java.nio.file.*;import java.time.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.json.*;import android.app.*;import android.widget.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class NativeMemoryTest {
    @Before public void init()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.memory=new AgentStore(Files.createTempDirectory("native-memory").toFile());PetService.instance=null;TestKeyStore.install();MemoryBridge.client=(c,p,b,t)->{throw new AssertionError("native mode must never call a Python service");};NativeMemoryIntelligence.transport=(c,m,t)->{throw new IOException("controlled model unavailable");};}
    @After public void cleanup()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();MemoryBridge.client=MemoryBridge::http;NativeMemoryIntelligence.transport=NativeMemoryIntelligence::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    JSONObject state()throws Exception{return MemoryLedger.copy(AgentStore.empty(0));}
    JSONObject row(String id,String text,long now)throws Exception{return MemoryLedger.record(id,text,"turn-"+id,false,now);}
    MemoryBridge.Result run(JSONObject state,JSONArray incoming,String query,long now,NativeMemoryEngine.Intelligence smart)throws Exception{return NativeMemoryEngine.evaluate(state,incoming,query,now,ZoneOffset.UTC,smart,new AgentTransport.Token());}
    void send(String text)throws Exception{UiTest.Counter c=new UiTest.Counter();assertTrue(AgentRuntime.send(text,false,c));UiTest.awaitIdle();assertEquals(1,c.done);assertEquals(0,c.errors);}
    @Test public void nativeIsDefaultForNewAndUpgradedInstall()throws Exception {
        assertFalse(MemoryBridge.read().enabled);Data.prefs().edit().putBoolean("memory.service.enabled",true).putString("memory.service.url","http://127.0.0.1:8787").commit();assertFalse("legacy service settings must not activate on upgrade",MemoryBridge.read().enabled);
        MainActivity a=UiTest.ready();MemoryPanel.settings(a);Dialog d=UiTest.dialog();assertTrue(UiTest.all(d.getWindow().getDecorView(),EditText.class).isEmpty());assertTrue(((Switch)UiTest.find(d.getWindow().getDecorView(),"用当前模型辅助理解记忆")).isChecked());
    }
    @Test public void unchanged84ScenariosRunWithoutPythonOrNetwork()throws Exception{MemoryReplay.run(null);}
    @Test public void offlineConversationCorrectsAndArchivesOnPhone()throws Exception {
        UiTest.ready();String persona=PersonaManager.prompt("main");send("记住：我对花生过敏，很严重");send("记住：更正，我对花生不过敏了");JSONObject s=AgentRuntime.store().snapshot();assertEquals(1,s.getJSONArray("memories").length());assertEquals(1,s.getJSONArray("archive").length());assertTrue(s.getJSONArray("memories").getJSONObject(0).getString("text").contains("不过敏"));assertEquals(persona,PersonaManager.prompt("main"));assertTrue(s.getString("memoryStatus").contains("手机内置"));
    }
    @Test public void smartWritingReusesExistingPhoneModelByDefault()throws Exception {
        UiTest.ready();ModelSettings.save("https://example.invalid/v1","existing-chat-model","test-existing-key",false);AgentRuntime.store().remember("我住在北京海淀区");AtomicInteger count=new AtomicInteger();
        NativeMemoryIntelligence.transport=(c,m,t)->{count.incrementAndGet();assertEquals("existing-chat-model",c.model);assertEquals("test-existing-key",c.secret);assertEquals("https://example.invalid/v1/chat/completions",c.address.endpoint);JSONObject p=new JSONObject(m.getJSONObject(1).getString("content")).getJSONArray("plans").getJSONObject(0);return new JSONObject().put("decisions",new JSONArray().put(new JSONObject().put("new_id",p.getString("new_id")).put("decision","SUPERSEDE").put("targets",new JSONArray().put(p.getJSONArray("candidates").getJSONObject(0).getString("id"))))).toString();};
        send("记住：我现在住在上海浦东了");assertEquals(1,count.get());JSONObject s=AgentRuntime.store().snapshot();assertEquals(1,s.getJSONArray("memories").length());assertEquals(1,s.getJSONArray("archive").length());assertTrue(s.getJSONArray("memories").getJSONObject(0).getString("text").contains("上海"));
    }
    @Test public void semanticRecallSelectsExistingIdsUsingSameModel()throws Exception {
        ModelSettings.save("https://example.invalid/v1","existing-chat-model","test-existing-key",false);AgentRuntime.store().remember("我在协和医学院读书");String id=AgentRuntime.store().snapshot().getJSONArray("memories").getJSONObject(0).getString("id");AtomicInteger count=new AtomicInteger();
        // More memories than the prompt carries anyway, so model selection has something to choose from.
        for(String fact:new String[]{"我养了一只橘猫","我每天跑步五公里","我最爱吃火锅","我的生日在三月","我住在杭州西湖边","我会弹吉他","我在学日语","我喜欢下雨天","我妈妈是老师","我每周去游泳","我收藏邮票","我讨厌香菜","我开电动车","我喜欢科幻小说","我早上喝咖啡"})AgentRuntime.store().remember(fact);
        assertTrue(AgentRuntime.store().snapshot().getJSONArray("memories").length()>MemoryLedger.CORE+10);
        NativeMemoryIntelligence.transport=(c,m,t)->{count.incrementAndGet();JSONObject data=new JSONObject(m.getJSONObject(1).getString("content"));assertEquals("我的学校在哪里",data.getString("query"));return new JSONObject().put("ids",new JSONArray().put(id)).toString();};
        MemoryBridge.Result r=MemoryBridge.evaluate(MemoryBridge.read(),AgentRuntime.store().snapshot(),new JSONArray(),"我的学校在哪里",System.currentTimeMillis(),new AgentTransport.Token());assertFalse(r.remote);assertEquals(id,r.hits.getString(0));assertEquals(1,count.get());
    }
    @Test public void malformedOrInventedModelTargetsNeverInvalidate()throws Exception {
        long now=System.currentTimeMillis();JSONObject s=run(state(),new JSONArray().put(row("old","我住在北京海淀区",now)),"",now,null).state;
        MemoryBridge.Result r=run(s,new JSONArray().put(row("new","我现在住在上海浦东了",now)),"",now,(op,data,t)->new JSONObject().put("decisions",new JSONArray().put(new JSONObject().put("new_id","new").put("decision","SUPERSEDE").put("targets",new JSONArray().put("invented")))));
        assertEquals(2,r.state.getJSONArray("memories").length());assertEquals(0,r.state.getJSONArray("archive").length());assertTrue(r.status.contains("本地规则"));
    }
    @Test public void batchDecisionsAreAppliedAtomically()throws Exception {
        long now=System.currentTimeMillis();JSONObject s=run(state(),new JSONArray().put(row("old","我住在北京海淀区",now)),"",now,null).state;
        MemoryBridge.Result r=run(s,new JSONArray().put(row("one","我现在住在上海浦东了",now)).put(row("two","我养了一只猫",now)),"",now,(op,data,t)->new JSONObject().put("decisions",new JSONArray().put(new JSONObject().put("new_id","one").put("decision","SUPERSEDE").put("targets",new JSONArray().put("old"))).put(new JSONObject().put("new_id","two").put("decision","SUPERSEDE").put("targets",new JSONArray().put("invented")))));
        assertNotNull(NativeMemoryEngine.find(r.state,"old"));assertTrue(MemoryLedger.current(NativeMemoryEngine.find(r.state,"old"),now));assertEquals(0,r.state.getJSONArray("archive").length());
    }
    @Test public void datesUseObservationZoneAndExpireLocally()throws Exception {
        long observed=Instant.parse("2026-10-28T17:00:00Z").toEpochMilli();JSONObject r=row("a","我明天有重要考试",observed);JSONObject s=NativeMemoryEngine.evaluate(state(),new JSONArray().put(r),"考试",observed,ZoneId.of("Asia/Shanghai"),null,new AgentTransport.Token()).state;
        assertEquals("我明天有重要考试",s.getJSONArray("memories").getJSONObject(0).getString("text"));assertEquals("2026-10-30",s.getJSONArray("memories").getJSONObject(0).getJSONObject("engine").getString("event_date"));
        MemoryBridge.Result expired=run(s,new JSONArray(),"考试",observed+4*86400000L,null);assertEquals(0,expired.state.getJSONArray("memories").length());assertEquals(1,expired.state.getJSONArray("archive").length());
        JSONObject recurring=row("b","我每周三去健身",observed);NativeMemoryEngine.initialize(recurring,ZoneOffset.UTC);assertFalse(recurring.getJSONObject("engine").has("expires_at"));
    }
    @Test public void lateModelCannotRestoreDeletedPhoneMemory()throws Exception {
        UiTest.ready();ModelSettings.save("https://example.invalid/v1","mock","test-existing-key",false);AgentRuntime.store().remember("我住在北京海淀区");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        NativeMemoryIntelligence.transport=(c,m,t)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "{\"decisions\":[]}";};UiTest.Counter listener=new UiTest.Counter();AgentRuntime.send("记住：我现在住在上海浦东了",false,listener);try{assertTrue(entered.await(3,TimeUnit.SECONDS));AgentRuntime.store().clear(true);}finally{release.countDown();}UiTest.awaitIdle();assertEquals(0,listener.done);assertEquals(1,listener.errors);assertEquals(0,AgentRuntime.store().snapshot().getJSONArray("memories").length());
    }
    @Test public void localModelCancellationDoesNotBecomeRuleFallback()throws Exception {
        long now=System.currentTimeMillis();JSONObject s=run(state(),new JSONArray().put(row("a","我喜欢蓝色",now)),"",now,null).state;AgentTransport.Token token=new AgentTransport.Token();try{NativeMemoryEngine.evaluate(s,new JSONArray(),"喜欢",now,ZoneOffset.UTC,(op,data,t)->{t.cancel();throw new IOException("cancelled");},token);fail();}catch(IOException expected){assertTrue(token.cancelled);}
    }
    @Test public void phoneMemorySurvivesRestartAndForgetPurgesHistory()throws Exception {
        File dir=Files.createTempDirectory("native-restart").toFile();AgentStore db=new AgentStore(dir);db.remember("我对花生过敏");db.remember("更正，我对花生不过敏了");db=new AgentStore(dir);assertEquals(1,db.snapshot().getJSONArray("archive").length());db.forget(db.snapshot().getJSONArray("memories").getJSONObject(0).getString("id"));db=new AgentStore(dir);assertEquals(0,db.snapshot().getJSONArray("memories").length());assertEquals(0,db.snapshot().getJSONArray("archive").length());assertFalse(new File(dir,"companion-v1.json.bak").exists());
    }
}
