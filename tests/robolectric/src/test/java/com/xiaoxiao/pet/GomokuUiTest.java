package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import android.app.*;import android.view.*;import java.util.concurrent.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class GomokuUiTest {
    MainActivity a;
    @Before public void init()throws Exception{AgentRuntime.cancel();Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.memory=null;PetService.instance=null;TestKeyStore.install();GomokuPage.current.clear();GomokuPage.store().clear();a=UiTest.ready();}
    @After public void close()throws Exception{GomokuPage p=GomokuPage.current.get();if(p!=null)p.dialog.dismiss();GomokuPage.WORK.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void idle()throws Exception{ShadowLooper.idleMainLooper();GomokuPage.WORK.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();}
    @Test public void homeOpensPlayableBoardAndResumeRestoresMoves()throws Exception{
        UiTest.click(a.content,"一起下五子棋");idle();GomokuPage p=GomokuPage.current.get();assertNotNull(p);assertTrue(p.dialog.isShowing());assertEquals(225,p.board.getChildCount());
        p.play(112);idle();assertEquals(1,p.game.moves.size());p.dialog.dismiss();GomokuPage.show(a);idle();p=GomokuPage.current.get();assertEquals(1,p.game.moves.size());
        ShadowLooper.idleMainLooper(1,TimeUnit.SECONDS);idle();assertEquals(2,p.game.moves.size());assertEquals(1,p.game.turn());
    }
    @Test public void invitationButtonRoutesToSameRealBoard()throws Exception{
        ChatPage chat=new ChatPage(a,"main","main");chat.open();chat.message("陪我下盘五子棋吧",false,null,true);UiTest.click(chat.rows,"开始对局 · 五子棋");idle();assertFalse(chat.dialog.isShowing());assertTrue(GomokuPage.current.get().dialog.isShowing());
    }
    @Test public void endIsConfirmedAndHistoryCountsItOnce()throws Exception{
        GomokuPage.show(a);idle();GomokuPage p=GomokuPage.current.get();p.play(112);idle();p.finish();assertFalse(p.game.over());AlertDialog confirm=(AlertDialog)UiTest.dialog();confirm.getButton(-1).performClick();idle();assertTrue(p.game.abandoned);assertEquals(1,GomokuPage.store().context().getInt("abandoned"));assertTrue(p.again.getText().toString().contains("再来"));
        p.restart();idle();assertFalse(p.game.over());assertEquals(0,p.game.moves.size());assertEquals(1,GomokuPage.store().context().getInt("abandoned"));
    }
    @Test public void visibleBoardFitsPortraitAndLandscape(){GomokuPage p=new GomokuPage(a);for(int[] size:new int[][]{{360,500},{1500,500}}){p.board.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));p.board.layout(0,0,p.board.getMeasuredWidth(),p.board.getMeasuredHeight());assertEquals(p.board.getMeasuredWidth(),p.board.getMeasuredHeight());assertTrue(p.board.cells[224].getRight()<=p.board.getWidth());assertTrue(p.board.cells[224].getBottom()<=p.board.getHeight());}}
}
