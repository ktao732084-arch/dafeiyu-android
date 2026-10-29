package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import org.json.*;import android.app.*;import android.view.*;import android.widget.*;import java.nio.file.*;import java.util.concurrent.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class ChatPolishTest {
    MainActivity activity;ChatPage page;org.robolectric.android.controller.ActivityController<MainActivity> controller;
    @Before public void init()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);Chat.petChats.clear();Chat.presentations.clear();Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.memory=new AgentStore(Files.createTempDirectory("chat-polish").toFile());PetService.instance=null;TestKeyStore.install();controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();ShadowLooper.idleMainLooper();UiTest.click(UiTest.dialog(),"确认性格，开始陪伴");Data.prefs().edit().putBoolean("agent.diary",false).commit();}
    @After public void cleanup()throws Exception{if(page!=null)page.dialog.dismiss();if(controller!=null)controller.pause().stop().destroy();ShadowLooper.idleMainLooper();AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.model=AgentTransport::request;NativeMemoryIntelligence.transport=NativeMemoryIntelligence::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void configure()throws Exception{ModelSettings.save("https://example.invalid/v1","controlled","test-key",false);NativeMemoryIntelligence.transport=(c,m,t)->{throw new java.io.IOException("controlled memory fallback");};}
    UiTest.Counter send(String s)throws Exception{UiTest.Counter listener=new UiTest.Counter();assertTrue(AgentRuntime.send(s,false,listener));UiTest.awaitIdle();return listener;}
    EditText nickname(Dialog d){for(EditText e:UiTest.all(d.getWindow().getDecorView(),EditText.class))if("她对你的称呼".contentEquals(e.getContentDescription()))return e;throw new AssertionError("nickname input missing");}
    @Test public void successfulChatUpdatesSingleSettingAndManualChangeWins()throws Exception{
        configure();AgentRuntime.model=(m,t,p)->{assertTrue(m.getJSONObject(0).getString("content").contains("\"name\":\"kk\""));return "[tool] 好的kk，我记住了。";};
        assertEquals(1,send("我叫k你以后就叫我kk吧").done);assertEquals("kk",Nickname.current());assertFalse(AgentRuntime.history().toString().contains("[tool]"));
        ProfilePage.show(activity,"main");Dialog profile=UiTest.dialog();assertEquals("kk",nickname(profile).getText().toString());nickname(profile).setText("小K");UiTest.click(profile,"保存");
        AgentPanel.settings(activity);Dialog settings=UiTest.dialog();assertTrue("no duplicate nickname field",UiTest.all(settings.getWindow().getDecorView(),EditText.class).isEmpty());UiTest.click(settings,"完成");assertTrue(AgentRuntime.context("你好",false).toString().contains("小K"));
    }
    @Test public void failureAndCancellationCannotOverwriteManualNickname()throws Exception{
        configure();Nickname.manual("旧名");AgentRuntime.model=(m,t,p)->{throw new java.io.IOException("controlled failure");};assertEquals(1,send("以后叫我新名").errors);assertEquals("旧名",Nickname.current());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(5,TimeUnit.SECONDS);return "迟到的回复";};UiTest.Counter result=new UiTest.Counter();AgentRuntime.send("以后叫我新名",false,result);
        try{assertTrue(entered.await(3,TimeUnit.SECONDS));Nickname.manual("手动名");}finally{release.countDown();}UiTest.awaitIdle();assertEquals("手动名",Nickname.current());assertEquals(0,result.done);assertEquals(0,AgentRuntime.history().length());
    }
    @Test public void existingExplicitMemoryMigratesOnceAndManualClearSticks()throws Exception{
        AgentRuntime.store().remember("我叫kk，以后就叫我kk吧");assertEquals("kk",Nickname.current());assertEquals("memory-migration",Data.prefs().getString("agent.nickname.origin",""));Nickname.manual("");assertEquals("",Nickname.current());assertTrue(AgentRuntime.store().memoryContext("kk").contains("kk"));
    }
    @Test public void slowMigrationCannotOverwriteConcurrentManualEdit()throws Exception{
        AgentStore db=AgentRuntime.store();db.remember("我叫kk，以后就叫我kk吧");FutureTask<String> read=new FutureTask<>(Nickname::current);Thread reader=new Thread(read,"nickname-migration-test");
        synchronized(db){reader.start();long deadline=System.currentTimeMillis()+3000;while(reader.getState()!=Thread.State.BLOCKED&&System.currentTimeMillis()<deadline)Thread.sleep(5);assertEquals(Thread.State.BLOCKED,reader.getState());Nickname.manual("手动称呼");}
        assertEquals("手动称呼",read.get(5,TimeUnit.SECONDS));assertEquals("手动称呼",Nickname.current());
    }
    @Test public void oldHistoryAndModelGuessCannotRenameUser()throws Exception{
        configure();Nickname.manual("固定称呼");long at=System.currentTimeMillis()-60000;AgentRuntime.store().commit(0,"我叫旧名","旧回复","calm",null,false,false,null,null,"old",at,null,false);
        AgentRuntime.model=(m,t,p)->"{\"reply\":\"过去你提过旧名\",\"memories\":[\"我叫旧名\"]}";assertEquals(1,send("今天我们聊过什么").done);assertEquals("固定称呼",Nickname.current());
    }
    @Test public void namingStillWorksWhenAutomaticMemoryIsOff()throws Exception{
        configure();Data.prefs().edit().putBoolean("agent.memory",false).commit();AgentRuntime.model=(m,t,p)->"好呀kk";assertEquals(1,send("以后叫我kk").done);assertEquals("kk",Nickname.current());assertEquals(0,AgentRuntime.store().snapshot().getJSONArray("memories").length());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void pendingFooterDoesNotClipTheFirstSegment()throws Exception{
        String reply="我在2026-10-19 至 2026-10-25没有找到可用的聊天记录。可能当时还没保存或后来清理过，不能据此判断我们没聊过。";
        AgentRuntime.store().commit(0,"上周我们聊过什么",reply,"calm",null,false,false,null,null,"pending",System.currentTimeMillis(),null,true);AgentRuntime.store().shown("pending",1);page=new ChatPage(activity,"main","main");page.open();
        page.rows.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(4000,View.MeasureSpec.AT_MOST));page.rows.layout(0,0,600,page.rows.getMeasuredHeight());
        TextView bubble=UiTest.find(page.rows,ReplySegments.split(reply).get(0).trim());assertNotNull(bubble);assertNotNull(bubble.getLayout());assertTrue("test must wrap into multiple lines",bubble.getLayout().getLineCount()>1);assertTrue("all wrapped lines fit inside bubble",bubble.getHeight()-bubble.getCompoundPaddingTop()-bubble.getCompoundPaddingBottom()>=bubble.getLayout().getHeight());
    }
    @Test public void dismissingChatRefreshesHomeAndLegacyMarkersStayOutOfDisplay()throws Exception{
        AgentRuntime.store().commit(0,"旧问题","旧预览","calm",null,false,false);activity.render();assertTrue(UiTest.texts(activity.content).contains("旧预览"));
        AgentRuntime.store().commit(0,"新问题","[tool] 最新回复","calm",null,false,false,null,null,"new",System.currentTimeMillis()+1000,null,false);page=new ChatPage(activity,"main","main");page.open();assertFalse(UiTest.texts(page.rows).contains("[tool]"));page.dialog.dismiss();ShadowLooper.idleMainLooper();assertTrue("home shows newly saved reply",UiTest.texts(activity.content).contains("最新回复"));assertFalse(UiTest.texts(activity.content).contains("旧预览"));assertTrue(AgentRuntime.store().snapshot().toString().contains("[tool]"));
    }
}
