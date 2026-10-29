package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import android.app.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Notebook search, confirmed clearing, the proactive status line and the hidden developer rows. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class UsabilityUiTest {
    MainActivity a;
    @Before public void init()throws Exception{AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();TestKeyStore.install();AgentRuntime.memory=null;PetService.instance=null;a=UiTest.ready();}
    @After public void cleanup()throws Exception{AgentRuntime.cancel();ShadowLooper.idleMainLooper();Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    View root(){return UiTest.dialog().getWindow().getDecorView();}
    @Test public void notebookSearchFiltersInPlaceAndPagesTheMatches()throws Exception{
        // This is a paging fixture, not a semantic-dedup test: near-identical numbered text is otherwise merged.
        JSONObject fixture=AgentStore.empty(0);long at=System.currentTimeMillis();MemoryLedger.addLocal(fixture,MemoryLedger.record("blue","我喜欢蓝色","你手动保存",true,at));
        for(int i=0;i<70;i++)MemoryLedger.addLocal(fixture,MemoryLedger.record("row"+i,"第"+i+"件小事","你手动保存",false,at+i+1));
        java.nio.file.Path dir=java.nio.file.Files.createTempDirectory("notebook-paging");java.nio.file.Files.write(dir.resolve("companion-v1.json"),fixture.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));AgentRuntime.memory=new AgentStore(dir.toFile());
        AgentPanel.memories(a);View root=root();assertNotNull(UiTest.find(root,"再显示 11 条（共 71 条）"));
        ((EditText)UiTest.description(root,"搜索记忆")).setText("蓝色");ShadowLooper.idleMainLooper();
        assertNotNull(UiTest.find(root,"★ 我喜欢蓝色"));assertNull(UiTest.find(root,"第3件小事"));
        ((EditText)UiTest.description(root,"搜索记忆")).setText("");UiTest.click(root,"再显示 11 条（共 71 条）");assertNotNull(UiTest.find(root,"★ 我喜欢蓝色"));assertNotNull(UiTest.find(root,"第0件小事"));
        UiTest.click(root,"自动记下");assertNotNull(UiTest.find(root,"这一类还没有记忆。"));
    }
    @Test public void clearingTheDiaryAsksFirst()throws Exception{
        java.time.LocalDate day=java.time.LocalDate.now().minusDays(1);assertTrue(AgentRuntime.store().addDiary(AgentRuntime.store().epoch(),Diary.entry(day,"今天陪他聊了晚饭。",new java.util.ArrayList<>(),System.currentTimeMillis())));
        MemoryPanel.diary(a);AlertDialog diary=(AlertDialog)UiTest.dialog();diary.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();
        assertEquals("one tap does not clear",1,Diary.entries(AgentRuntime.store().snapshot()).length());
        AlertDialog confirm=(AlertDialog)UiTest.dialog();assertNotSame(diary,confirm);confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();UiTest.storage();
        assertEquals(0,Diary.entries(AgentRuntime.store().snapshot()).length());
    }
    @Test public void proactiveSettingSaysWhatIsMissing()throws Exception{
        AgentPanel.settings(a);View root=root();assertFalse(UiTest.texts(root).contains("现在还不会开口"));
        UiTest.click(root,"偶尔主动和我说话");assertTrue(UiTest.texts(root).contains("模型还没接通"));
        ModelSettings.save("https://example.invalid/v1","mock","test-only-key",false);TextView quiet=UiTest.find(root,"安静陪伴 1 小时");quiet.performClick();ShadowLooper.idleMainLooper();
        assertTrue(quiet.getText().toString().startsWith("结束安静陪伴（到 "));assertTrue("pet not on screen comes first",UiTest.texts(root).contains("住到屏幕上"));
        quiet.performClick();ShadowLooper.idleMainLooper();assertEquals("安静陪伴 1 小时",quiet.getText().toString());assertEquals(0,Data.prefs().getLong("agent.focusUntil",-1));
    }
    @Test public void emptyNotebookStillOpensAndEditsRefreshItInPlace()throws Exception{
        AgentPanel.memories(a);AlertDialog notebook=(AlertDialog)UiTest.dialog();assertTrue(notebook.isShowing());assertTrue(UiTest.texts(root()).contains("这里还空空的"));
        AgentRuntime.store().remember("我喜欢蓝色",false);AgentRuntime.store().remember("我不吃香菜",false);AgentPanel.memories(a);notebook=(AlertDialog)UiTest.dialog();
        UiTest.click(notebook,"我喜欢蓝色");AlertDialog editor=(AlertDialog)UiTest.dialog();assertTrue("the notebook stays open under the editor",notebook.isShowing());
        UiTest.all(editor.getWindow().getDecorView(),EditText.class).get(0).setText("我最喜欢深蓝色");editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();UiTest.storage();
        assertFalse(editor.isShowing());assertTrue(notebook.isShowing());assertNotNull(UiTest.find(notebook.getWindow().getDecorView(),"我最喜欢深蓝色"));
    }
    @Test public void firstStepsChecklistUntilDoneOrDismissed(){
        a.render();assertNotNull(UiTest.find(a.content,"接通模型，让她能开口"));assertNotNull(UiTest.find(a.content,"开始之前还差 2 步"));
        UiTest.click(a.content,"不再提示");assertNull(UiTest.find(a.content,"接通模型，让她能开口"));assertTrue(Data.prefs().getBoolean("onboarding.done",false));
    }
    @Test public void developerMemoryRowsAppearOnlyOnLongPress(){
        MemoryPanel.settings(a);View root=root();assertFalse(UiTest.find(root,"开发联调（可选）").isShown());
        UiTest.find(root,"记忆引擎就装在手机里。理解时间、去重、更正、归档和查找都在本机完成，不需要电脑，也不用另外部署服务。").performLongClick();ShadowLooper.idleMainLooper();
        assertTrue(UiTest.find(root,"开发联调（可选）").isShown());
    }
}
