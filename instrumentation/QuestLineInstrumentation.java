package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.content.pm.ActivityInfo;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import org.json.*;

/** Synthetic fresh companion on actual Android: goal card, wrapping, and a real local-memory goal. */
public final class QuestLineInstrumentation extends Instrumentation {
 MainActivity activity;Dialog dialog;int checks;
 interface Checked{void run()throws Exception;}
 void main(Checked action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
 void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public void onCreate(Bundle state){super.onCreate(state);start();}
 String text(View v){StringBuilder s=new StringBuilder(v instanceof TextView?((TextView)v).getText():"");if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append('\n').append(text(((ViewGroup)v).getChildAt(i)));return s.toString();}
 TextView find(View v,String needle){if(v instanceof TextView&&((TextView)v).getText().toString().contains(needle))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=find(((ViewGroup)v).getChildAt(i),needle);if(found!=null)return found;}return null;}
 @SuppressWarnings("unchecked") Dialog story()throws Exception{java.lang.reflect.Field field=DialogLifecycle.class.getDeclaredField("windows");field.setAccessible(true);Map<Activity,Set<Dialog>> windows=(Map<Activity,Set<Dialog>>)field.get(null);for(Dialog d:windows.get(activity))if(d.isShowing())return d;throw new AssertionError("story dialog missing");}
 void complete(TextView v,String label){check(v!=null&&v.getLayout()!=null,label+" laid out");check(v.getLayout().getHeight()<=v.getHeight()-v.getPaddingTop()-v.getPaddingBottom(),label+" fits vertically");for(int i=0;i<v.getLayout().getLineCount();i++)check(v.getLayout().getEllipsisCount(i)==0,label+" not ellipsized");}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).commit();UpdateRuntime.channels=c->"";AgentRuntime.memory=new AgentStore(new File(getTargetContext().getFilesDir(),"agent"));GrowthStore.quiet();PersonaManager.initialize("main","classic");});
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}){
    main(()->{if(dialog!=null)dialog.dismiss();activity.setRequestedOrientation(orientation);});SystemClock.sleep(800);waitForIdleSync();
    main(()->{complete(find(activity.content,"下一步"),"home goal summary");GrowthPanel.show(activity);dialog=story();});waitForIdleSync();SystemClock.sleep(400);
    main(()->{String shown=text(dialog.getWindow().getDecorView());check(shown.contains("这一章要一起做的事"),"quest card present");check(shown.contains("让她记住 1 件你的事  0/1")&&shown.contains("一起聊满 3 天"),"both real goal alternatives shown");check(shown.contains("泡泡瓶装到 60"),"bond threshold shown");complete(find(dialog.getWindow().getDecorView(),"做到其中一项"),"goal card");check(dialog.getWindow().getDecorView().hasWindowFocus(),"story owns foreground");});
    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?"quest-portrait.png":"quest-landscape.png"))){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"quest screenshot saved");}
   }
   AgentRuntime.store().remember("我喜欢海边散步");GrowthStore.get().sync();JSONObject state=GrowthStore.get().snapshot();check(Growth.progress(state,"memories")==1,"saved local fact completes memory goal");check("memories".equals(Growth.done(state,GrowthStore.story(),1)),"goal latched from actual ledger");check(Growth.chapter(state)==1,"goal alone cannot bypass bond threshold");
   main(()->{dialog.dismiss();activity.render();check(text(activity.content).contains("这一章的事已经完成"),"home reflects completed goal");});
   result.putString("stream","PASS: "+checks+" Android quest-line checks\n");
  }catch(Throwable t){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));}
  finally{try{main(()->{if(dialog!=null)dialog.dismiss();if(activity!=null)activity.finish();AgentRuntime.cancel();});}catch(Exception ignored){}}
  finish(code,result);
 }
}
