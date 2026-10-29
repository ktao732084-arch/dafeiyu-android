package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;

/** Synthetic conversations on the real Android UI; no production files or model requests. */
public final class ChatPresentationInstrumentation extends Instrumentation {
    Activity activity;ChatPage chat;int checks;
    interface Checked{void run()throws Exception;}
    void main(Checked task)throws Exception{final Throwable[] failure={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new Exception(failure[0]);}
    void check(boolean yes,String what){checks++;if(!yes)throw new AssertionError(what);}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    int buttons(View v){int n=v instanceof Button&&((Button)v).getText().toString().contains("开始对局")?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=buttons(((ViewGroup)v).getChildAt(i));return n;}
    void layoutCheck(View v){if(v instanceof TextView&&!(v instanceof Button)){TextView t=(TextView)v;check(t.getLayout()!=null,"text laid out");check(t.getLayout().getHeight()<=t.getHeight()-t.getPaddingTop()-t.getPaddingBottom(),"text not clipped vertically");}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)layoutCheck(((ViewGroup)v).getChildAt(i));}
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).commit();UpdateRuntime.channels=c->"";PersonaManager.initialize("main","classic");AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"chat-"+java.util.UUID.randomUUID()));ModelSettings.save("https://example.invalid/v1","controlled","test-only-key",false);});
            activity=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->{chat=new ChatPage(activity,"main","main");chat.open();});waitForIdleSync();SystemClock.sleep(300);
            main(()->{chat.rows.removeAllViews();String reply="刚刚那盘是你赢了，最后一手下得真漂亮。现在你赢两局，我赢一局，还没有平局——五子棋这回算你厉害，下次可没这么容易。";chat.message("刚才那盘谁赢了？",true,null,false);chat.segments(new JSONObject().put("reply",reply).put("displayed",ReplySegments.legacySplit(reply).size()));check(buttons(chat.rows)==0,"score report has no game entry");check(chat.rows.getChildCount()>=4,"old history is redisplayed in short bubbles");});
            waitForIdleSync();SystemClock.sleep(500);main(()->layoutCheck(chat.rows));screenshot("chat-short-bubbles.png");
            main(()->{chat.rows.removeAllViews();chat.message("想一起玩一会儿吗？",true,null,false);chat.segments(new JSONObject().put("reply","有空的话，陪我下盘五子棋吧。你先落子，我准备好了。"));check(buttons(chat.rows)==1,"explicit invitation offers exactly one entry");});
            waitForIdleSync();SystemClock.sleep(500);main(()->layoutCheck(chat.rows));screenshot("chat-game-invitation.png");
            main(()->{chat.rows.removeAllViews();chat.segments(new JSONObject().put("user","我想玩五子棋").put("reply","好呀，来吧。"));check(buttons(chat.rows)==1,"user request offers entry");chat.rows.removeAllViews();chat.segments(new JSONObject().put("reply","先聊聊刚才的棋局。陪我再下盘五子棋吧。").put("segmentVersion",3).put("displayed",1));check(buttons(chat.rows)==0,"unshown invitation does not offer entry");});
            main(()->{chat.dialog.dismiss();activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});waitForIdleSync();SystemClock.sleep(800);
            main(()->{check(activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"landscape active");chat=new ChatPage(activity,"main","main");chat.open();});waitForIdleSync();SystemClock.sleep(300);
            main(()->{chat.rows.removeAllViews();chat.message("这盘五子棋是谁赢了？",true,null,false);chat.segments(new JSONObject().put("reply","这盘是你赢了，最后那一手很漂亮。你已经赢了两盘，我这边还只有一盘……五子棋的账我可都记着呢。"));check(buttons(chat.rows)==0,"landscape score report has no entry");check(chat.rows.getChildCount()>=4,"landscape keeps short bubbles");});waitForIdleSync();SystemClock.sleep(500);main(()->layoutCheck(chat.rows));screenshot("chat-landscape.png");
            result.putString("stream","PASS: "+checks+" Android chat presentation checks\n");
        }catch(Throwable t){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));}
        finally{try{main(()->{if(chat!=null)chat.dialog.dismiss();if(activity!=null)activity.finish();AgentRuntime.cancel();});}catch(Exception ignored){}}
        finish(code,result);
    }
    void screenshot(String name)throws Exception{main(()->check(chat.dialog.getWindow().getDecorView().hasWindowFocus(),"test chat owns the foreground before screenshot"));try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot saved");}}
}
