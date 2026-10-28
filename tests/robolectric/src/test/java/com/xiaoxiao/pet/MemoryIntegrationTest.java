package com.xiaoxiao.pet;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import android.app.*;
import android.widget.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,manifest=Config.NONE) @LooperMode(LooperMode.Mode.PAUSED)
public class MemoryIntegrationTest {
    static Process service;static String base;static final String SECRET="android-test-token-01234567890123456789";
    @BeforeClass public static void startService()throws Exception {
        String script=System.getProperty("memory.service.script",new File("../../memory-service/upstream/src/companion_api.py").getCanonicalPath());
        ProcessBuilder b=new ProcessBuilder(System.getProperty("memory.python","python"),"-X","utf8",script,"--port","0");
        b.environment().keySet().removeIf(k->k.startsWith("OPENAI_")||k.startsWith("MEMORY_"));b.environment().put("MEMORY_SERVICE_TOKEN",SECRET);b.redirectErrorStream(true);service=b.start();
        ExecutorService reader=Executors.newSingleThreadExecutor();try {String line=reader.submit(()->new BufferedReader(new InputStreamReader(service.getInputStream(),"UTF-8")).readLine()).get(15,TimeUnit.SECONDS);assertNotNull(line);assertTrue(line,line.startsWith("Memory service ready at "));base="http://"+line.substring("Memory service ready at ".length(),line.indexOf(';'));}finally{reader.shutdownNow();}
    }
    @AfterClass public static void stopService()throws Exception{if(service!=null){service.destroy();if(!service.waitFor(5,TimeUnit.SECONDS))service.destroyForcibly();}}
    @Before public void init()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.memory=new AgentStore(Files.createTempDirectory("fish-memory-test").toFile());AgentRuntime.model=AgentTransport::request;PetService.instance=null;MemoryBridge.client=MemoryBridge::http;MemoryBridge.lastStatus="手机本地记忆";TestKeyStore.install();}
    @After public void reset()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();MemoryBridge.client=MemoryBridge::http;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    MemoryBridge.Config config()throws Exception{return new MemoryBridge.Config(true,new MemoryBridge.Address(base),SECRET,false,false);}
    JSONObject state()throws Exception{return MemoryLedger.copy(AgentStore.empty(0));}
    JSONObject row(String id,String text,long now)throws Exception{return MemoryLedger.record(id,text,"turn-"+id,false,now);}
    MemoryBridge.Result call(JSONObject state,JSONArray incoming,String query,long now)throws Exception{return MemoryBridge.evaluate(config(),state,incoming,query,now,new AgentTransport.Token());}
    @Test public void actualHttpCaptureCorrectionAndRestart()throws Exception {
        long now=System.currentTimeMillis();MemoryBridge.Result first=call(state(),new JSONArray().put(row("old","记住，我对花生过敏，很严重",now)),"花生",now);assertTrue(first.status,first.remote);
        MemoryBridge.Result next=call(first.state,new JSONArray().put(row("new","更正：我对花生不过敏了",now+1)),"花生",now+1);assertTrue(next.status,next.remote);assertEquals(1,next.state.getJSONArray("memories").length());assertEquals("old",next.state.getJSONArray("archive").getJSONObject(0).getString("id"));assertFalse(next.hits.toString().contains("old"));
        File dir=Files.createTempDirectory("fish-restart").toFile();AgentStore db=new AgentStore(dir);assertTrue(db.commit(0,"更正：我对花生不过敏了","记住了","calm",null,true,false,null,next,"turn-new",now));assertEquals("new",new AgentStore(dir).snapshot().getJSONArray("memories").getJSONObject(0).getString("id"));
    }
    @Test public void correctedSourceExcludedFromBothRecentAndLongTermContext()throws Exception {
        UiTest.ready();
        long now=System.currentTimeMillis();MemoryBridge.Result first=call(state(),new JSONArray().put(row("old","记住，我对花生过敏，很严重",now)),"花生",now);
        first.state.getJSONArray("turns").put(new JSONObject().put("id","turn-old").put("at",now).put("user","记住，我对花生过敏，很严重").put("reply","我记住了"));
        MemoryBridge.Result next=call(first.state,new JSONArray().put(row("new","更正：我对花生不过敏了",now+1)),"花生",now+1);
        String prompt=AgentRuntime.context("花生",false,next.state,next.hits).toString();assertFalse(prompt.contains("很严重"));assertTrue(prompt.contains("不过敏"));
    }
    @Test public void oldSchemaAndDeleteClearArchiveIndexAndBackup()throws Exception {
        File dir=Files.createTempDirectory("fish-migrate").toFile();JSONObject old=AgentStore.empty(0);old.getJSONArray("memories").put(row("old","我喜欢蓝色",System.currentTimeMillis()));Files.write(new File(dir,"companion-v1.json").toPath(),old.toString().getBytes("UTF-8"));AgentStore db=new AgentStore(dir);assertEquals(2,db.snapshot().getInt("memoryVersion"));
        JSONObject s=db.snapshot();JSONObject archived=row("past","我以前喜欢红色",System.currentTimeMillis());archived.put("engine",new JSONObject().put("superseded_by","old").put("invalid_at","2026-01-01T00:00:00Z"));s.getJSONArray("archive").put(archived);s.put("vector_cache",new JSONObject().put("model","example").put("vectors",new JSONObject()));
        MemoryBridge.Result plan=new MemoryBridge.Result(s,null,true,"test",new JSONArray());assertTrue(db.commit(0,"我喜欢蓝色","好","happy",null,true,false,null,plan,"turn-old",System.currentTimeMillis()));db.forget("old");JSONObject remaining=db.snapshot();assertEquals(0,remaining.getJSONArray("memories").length());assertEquals(0,remaining.getJSONArray("archive").length());assertEquals(0,remaining.getJSONObject("vector_cache").length());assertFalse(new File(dir,"companion-v1.json.bak").exists());assertFalse(new AgentStore(dir).snapshot().toString().contains("红色"));
    }
    @Test public void failedServiceKeepsLocalQuoteAndDoesNotInventSuccess()throws Exception {
        MemoryBridge.client=(c,p,b,t)->{throw new IOException("test outage");};long now=System.currentTimeMillis();MemoryBridge.Result r=call(state(),new JSONArray().put(row("a","我喜欢蓝色",now)),"蓝色",now);assertFalse(r.remote);assertEquals(1,r.state.getJSONArray("memories").length());assertTrue(r.status.contains("手机内置"));
    }
    @Test public void fabricatedResponseFailsClosedToLocal()throws Exception {
        long now=System.currentTimeMillis();MemoryBridge.client=(c,p,b,t)->{JSONObject fake=state();fake.getJSONArray("memories").put(row("a","用户从未说过的事",now));return new JSONObject().put("protocol",1).put("state",fake).put("hits",new JSONArray().put("a"));};MemoryBridge.Result r=call(state(),new JSONArray().put(row("a","我喜欢蓝色",now)),"蓝色",now);assertFalse(r.remote);assertEquals("我喜欢蓝色",r.state.getJSONArray("memories").getJSONObject(0).getString("text"));
    }
    @Test public void cancellationDoesNotFallBackAndCommit()throws Exception {
        AgentTransport.Token token=new AgentTransport.Token();MemoryBridge.client=(c,p,b,t)->{t.cancel();throw new IOException("stopped");};try{MemoryBridge.evaluate(config(),state(),new JSONArray().put(row("a","我喜欢蓝色",System.currentTimeMillis())),"蓝色",System.currentTimeMillis(),token);fail("must propagate cancellation");}catch(IOException expected){assertTrue(token.cancelled);}
    }
    @Test public void inFlightResultCannotResurrectUserDeletion()throws Exception {
        MainActivity a=UiTest.ready();MemoryBridge.save(true,base,SECRET,false,false);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        MemoryBridge.client=(c,p,b,t)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return MemoryBridge.http(c,p,b,t);};UiTest.Counter done=new UiTest.Counter();AgentRuntime.send("记住：我喜欢蓝色",false,done);assertTrue(entered.await(3,TimeUnit.SECONDS));AgentRuntime.store().clear(true);release.countDown();UiTest.awaitIdle();assertEquals(0,AgentRuntime.store().snapshot().getJSONArray("memories").length());assertEquals(0,done.done);assertEquals(1,done.errors);
    }
    @Test public void settingsUiEncryptsTokenAndBindsOrigin()throws Exception {
        MainActivity a=UiTest.ready();MemoryPanel.serviceSettings(a);AlertDialog dialog=(AlertDialog)UiTest.dialog();UiTest.click(dialog,"连接 Python 联调引擎");List<EditText> fields=UiTest.all(dialog.getWindow().getDecorView(),EditText.class);fields.get(0).setText(base);fields.get(1).setText(SECRET);dialog.getButton(-1).performClick();assertFalse(dialog.isShowing());assertTrue(MemoryBridge.read().enabled);assertEquals(SECRET,MemoryBridge.read().secret);assertFalse(Data.prefs().getString("memory.service.secret","").contains(SECRET));try{MemoryBridge.save(true,"https://other.invalid","",false,false);fail();}catch(IOException expected){}assertEquals(base,MemoryBridge.read().address.base);
    }
    @Test public void disabledMemoryMakesNoServiceRequests()throws Exception {
        UiTest.ready();MemoryBridge.save(true,base,SECRET,false,false);Data.prefs().edit().putBoolean("agent.memory",false).commit();ModelSettings.save("https://example.invalid/v1","mock","test-only-key",false);java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();MemoryBridge.client=(c,p,b,t)->{requests.incrementAndGet();throw new IOException("memory disabled");};AgentRuntime.model=(m,t,p)->"{\"reply\":\"你好\",\"memories\":[\"我喜欢蓝色\"]}";UiTest.Counter result=new UiTest.Counter();AgentRuntime.send("我喜欢蓝色",false,result);UiTest.awaitIdle();assertEquals(1,result.done);assertEquals(0,requests.get());assertEquals(0,AgentRuntime.store().snapshot().getJSONArray("memories").length());
    }
    @Test public void onlyLoopbackAllowsHttp()throws Exception {
        assertEquals(base,new MemoryBridge.Address(base).base);for(String url:new String[]{"http://192.168.1.2:8787","http://example.com","https://user:pass@example.com","https://example.com?token=abc"})try{new MemoryBridge.Address(url);fail(url);}catch(Exception expected){}
    }
    @Test public void unchanged84ScenarioProtocolReplayMatchesPython()throws Exception {MemoryReplay.run(config());}
    @Test public void historicalEntryIsReadOnlyAndCannotRestoreAnOldFact()throws Exception {
        MainActivity a=UiTest.ready();JSONObject old=row("old","我以前喜欢红色",System.currentTimeMillis());String before=AgentRuntime.store().snapshot().toString();MemoryPanel.historicalEntry(a,old);AlertDialog dialog=(AlertDialog)UiTest.dialog();assertEquals(0,UiTest.all(dialog.getWindow().getDecorView(),EditText.class).size());assertEquals("忘记相关版本",dialog.getButton(-1).getText().toString());dialog.getButton(-2).performClick();assertEquals(before,AgentRuntime.store().snapshot().toString());
    }
}
