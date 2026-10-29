package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import org.json.*;import android.app.*;import android.widget.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class ConversationTest {
    MainActivity activity;ChatPage page;org.robolectric.android.controller.ActivityController<MainActivity> controller;
    static final String REPLY="今天的风很舒服，我就在这里安静陪你待一会儿，听你慢慢说今天发生了什么。等你忙完了，我们再一起聊点轻松的事情，或者想想周末有什么小期待。还有一件刚刚想到的特别重要的小事，我会等你有空的时候再慢慢告诉你。";
    @Before public void init()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);Chat.petChats.clear();Chat.presentations.clear();Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.memory=new AgentStore(Files.createTempDirectory("conversation-ui").toFile());PetService.instance=null;TestKeyStore.install();controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();ShadowLooper.idleMainLooper();UiTest.click(UiTest.dialog(),"确认性格，开始陪伴");Data.prefs().edit().putBoolean("agent.memory",false).commit();}
    @After public void cleanup()throws Exception{if(page!=null)page.dialog.dismiss();if(controller!=null)controller.pause().stop().destroy();ShadowLooper.idleMainLooper();Chat.presentations.clear();Chat.petChats.clear();AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);AgentRuntime.model=AgentTransport::request;Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void open(){page=new ChatPage(activity,"main","main");page.open();}
    void seed(int count)throws Exception{long time=System.currentTimeMillis()-count*60000;for(int i=0;i<count;i++)AgentRuntime.store().commit(AgentRuntime.store().epoch(),"历史问题"+i,"历史回复"+i,"calm",null,false,false,null,null,"seed"+i,time+i*60000,null,false);}
    JSONObject last()throws Exception{JSONArray rows=AgentRuntime.store().snapshot().getJSONArray("turns");return rows.getJSONObject(rows.length()-1);}
    @Test public void historyPageLoadsEarlierTurnsWithDateTimeAndCopy()throws Exception{
        seed(25);open();assertEquals(20,page.records.length());assertFalse(UiTest.texts(page.rows).contains("历史问题0\n"));page.loadOlder();assertEquals(25,page.records.length());assertTrue(UiTest.texts(page.rows).contains("历史问题0"));assertNotNull(UiTest.find(page.rows,ChatPage.when(page.records.getJSONObject(0),page.records.getJSONObject(0).getLong("at"))));
        TextView message=UiTest.find(page.rows,"历史回复0");assertNotNull(message);message.performLongClick();PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();assertEquals("her reply offers copy only",1,menu.getMenu().size());Shadows.shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(menu.getMenu().getItem(0));android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);assertEquals("历史回复0",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
    @Test public void shortHistoryHidesOlderButton()throws Exception{seed(5);open();assertNotEquals(android.view.View.VISIBLE,page.older.getVisibility());}
    @Test public void pagingMarksTheEarliestPage()throws Exception{
        seed(25);open();assertEquals(android.view.View.VISIBLE,page.older.getVisibility());page.loadOlder();assertFalse(page.older.isEnabled());assertEquals("已经翻到最早的聊天啦",page.older.getText().toString());
    }
    @Test public void longPressOnYourOwnWordsRemembersThem()throws Exception{
        seed(3);open();TextView mine=UiTest.find(page.rows,"历史问题1");mine.performLongClick();PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();assertEquals(2,menu.getMenu().size());
        Shadows.shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(menu.getMenu().getItem(1));UiTest.storage();JSONArray memories=AgentRuntime.store().snapshot().getJSONArray("memories");
        assertEquals(1,memories.length());assertEquals("历史问题1",memories.getJSONObject(0).getString("text"));assertFalse(memories.getJSONObject(0).getBoolean("pinned"));assertTrue(MemoryLedger.manual(memories.getJSONObject(0)));
    }
    @Test public void searchFindsArchivedWordsAndOpensThatDayAtTheTurn()throws Exception{
        long day=86_400_000L,start=System.currentTimeMillis()-200*day;
        for(int i=0;i<200;i++)AgentRuntime.store().commit(AgentRuntime.store().epoch(),i==10?"楼下新开的火锅店叫老码头":"日常"+i,"嗯嗯"+i,"calm",null,false,false,null,null,"d"+i,start+i*day,null,false);
        open();JSONArray hits=AgentRuntime.store().searchChat("老码头",40);assertEquals(1,hits.length());page.jump(hits.getJSONObject(0),"老码头");
        assertTrue(UiTest.texts(page.rows).contains("楼下新开的火锅店叫老码头"));assertTrue(UiTest.texts(page.rows).contains("— 搜索到的这一句 —"));assertEquals(android.view.View.VISIBLE,page.filter.getVisibility());
        assertFalse("other days stay out",UiTest.texts(page.rows).contains("日常11"));page.latest();assertTrue(UiTest.texts(page.rows).contains("日常199"));assertFalse(UiTest.texts(page.rows).contains("— 搜索到的这一句 —"));
    }
    @Test public void failedReplyOffersResendWithTheDraft()throws Exception{
        ModelSettings.save("https://example.invalid/v1","mock","test-key",false);AgentRuntime.model=(m,t,p)->{throw new java.io.IOException("controlled failure");};open();
        page.input.setText("在吗");page.send();UiTest.awaitIdle();assertEquals("在吗",page.input.getText().toString());assertEquals(android.view.View.VISIBLE,page.retry.getVisibility());
        AgentRuntime.model=(m,t,p)->"在呢。";page.retry.performClick();assertNotEquals(android.view.View.VISIBLE,page.retry.getVisibility());UiTest.awaitIdle();assertEquals("在吗",last().getString("user"));
        page.input.setText("x");page.input.setText("");assertNotEquals(android.view.View.VISIBLE,page.retry.getVisibility());
    }
    @Test public void oneModelCallCreatesProgressiveSegmentsAndDisplayedTimestamps()throws Exception{
        ModelSettings.save("https://example.invalid/v1","mock","test-key",false);java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();AgentRuntime.model=(m,t,p)->{calls.incrementAndGet();return new JSONObject().put("reply",REPLY).toString();};open();page.input.setText("聊聊今天");page.send();UiTest.awaitIdle();
        assertEquals(1,calls.get());assertEquals(1,last().getInt("displayed"));assertTrue(last().has("repliedAt"));assertTrue(last().has("segmentDisplayedAt"));assertFalse(UiTest.texts(page.rows).contains("特别重要的小事"));
        ShadowLooper.idleMainLooper(30,TimeUnit.SECONDS);assertEquals(3,last().getInt("displayed"));assertTrue(UiTest.texts(page.rows).contains("特别重要的小事"));
    }
    @Test public void newMessageCancelsOldSegmentsWithoutLosingCompleteRecord()throws Exception{
        ModelSettings.save("https://example.invalid/v1","mock","test-key",false);AgentRuntime.model=(m,t,p)->REPLY;open();page.input.setText("说说看");page.send();UiTest.awaitIdle();String old=last().getString("id");
        AgentRuntime.model=(m,t,p)->"好，我们换个话题。";page.input.setText("换个话题");page.send();UiTest.awaitIdle();ShadowLooper.idleMainLooper(30,TimeUnit.SECONDS);
        JSONArray records=AgentRuntime.store().historyPage(null,20);JSONObject first=records.getJSONObject(0);assertEquals(old,first.getString("id"));assertEquals(REPLY,first.getString("reply"));assertEquals(1,first.getInt("displayed"));assertTrue(ReplySegments.label(first).contains("未展示"));
    }
    @Test public void explicitFullReplyViewMarksUnshownPartsAndKeepsTimestamp()throws Exception{
        long sent=System.currentTimeMillis()-60000;AgentRuntime.store().commit(0,"请详细说",REPLY+REPLY+REPLY,"calm",null,false,false,null,null,"long",sent,null,true);open();JSONObject row=page.records.getJSONObject(0);page.expand(row);
        assertTrue(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains(REPLY));assertEquals(sent,last().getLong("receivedAt"));assertEquals(1,last().getInt("displayed"));assertTrue(last().getLong("displayedAt")>=sent);
    }
    @Test public void closingPageStopsScheduledSegments()throws Exception{
        ModelSettings.save("https://example.invalid/v1","mock","test-key",false);AgentRuntime.model=(m,t,p)->REPLY;open();page.input.setText("你好");page.send();UiTest.awaitIdle();page.loadOlder();assertFalse(page.speech.active);page.dialog.dismiss();ShadowLooper.idleMainLooper(30,TimeUnit.SECONDS);assertEquals(1,last().getInt("displayed"));
    }
    @Test public void dateRangeIsStableAcrossDisplayTimezoneChanges()throws Exception{
        TimeZone original=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));long at=java.time.Instant.parse("2026-10-28T17:20:30Z").toEpochMilli();AgentRuntime.store().commit(0,"跨时区消息","回复","calm",null,false,false,null,null,"zoned",at,null,true);JSONObject record=last();TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));assertEquals("2026-10-29 01:20:30",ChatPage.time(record,"yyyy-MM-dd HH:mm:ss",at));assertEquals(1,AgentRuntime.store().historyRange(at,at+1,null,20).length());}finally{TimeZone.setDefault(original);}
    }
    @Test public void bubbleHistoryIntentOpensExplicitConversationEntry(){Chat.openHistory(activity);android.content.Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(MainActivity.class.getName(),intent.getComponent().getClassName());assertTrue(intent.getBooleanExtra("openChat",false));}
    @Test public void closingChatAlsoClearsItsPetBubble(){PetService service=Robolectric.buildService(PetService.class).get();java.util.concurrent.atomic.AtomicInteger cleared=new java.util.concurrent.atomic.AtomicInteger();PetService.Pet pet=service.new Pet("main","main"){@Override void clearBubble(){cleared.incrementAndGet();}};service.pets.put("main",pet);PetService.instance=service;open();page.dialog.dismiss();ShadowLooper.idleMainLooper();assertEquals(1,cleared.get());PetService.instance=null;}
    @Test public void undeliveredSelfStatementsDoNotBecomeSpokenMemories()throws Exception{
        AgentRuntime.store().commit(0,"喜欢什么","我最爱吃小鱼干","happy",null,true,false,null,null,"self",System.currentTimeMillis(),new JSONArray().put("我最爱吃小鱼干"),true);assertEquals(0,AgentStore.selfContext(AgentRuntime.store().snapshot(),"喜欢").length());AgentRuntime.store().shown("self",1);assertEquals(1,AgentStore.selfContext(AgentRuntime.store().snapshot(),"喜欢").length());
    }
}
