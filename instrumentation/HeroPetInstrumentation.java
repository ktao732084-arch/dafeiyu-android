package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.concurrent.*;

/** Real MediaPlayer/EGL and Activity lifecycle, in the isolated test UID. No real model calls. */
public final class HeroPetInstrumentation extends Instrumentation {
 MainActivity activity;Activity cover;int checks;
 interface Checked{void run()throws Exception;}
 void main(Checked action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
 void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public void onCreate(Bundle state){super.onCreate(state);start();}
 long[] video()throws Exception{long[] state={0,0,0};CountDownLatch done=new CountDownLatch(1);activity.pet.video.queueEvent(()->{try{AlphaVideo v=activity.pet.video;state[0]=v.haveFrame?1:0;if(v.player!=null){state[1]=v.player.isPlaying()?1:0;state[2]=v.player.getCurrentPosition();}}finally{done.countDown();}});check(done.await(5,TimeUnit.SECONDS),"render thread responds");return state;}
 void ready()throws Exception{boolean[] ready={false};for(int i=0;i<100;i++){main(()->ready[0]=activity.pet!=null&&activity.pet.video!=null&&activity.pet.poster.getAlpha()<.05f);if(ready[0])break;SystemClock.sleep(100);}check(ready[0],"first video frame replaces poster");check(video()[0]==1&&video()[1]==1,"video rendered and playing");}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).commit();UpdateRuntime.channels=c->"";GrowthStore.quiet();PersonaManager.initialize("main","classic");AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"hero-"+java.util.UUID.randomUUID()));});
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready();AlphaVideo original=activity.pet.video;
   long before=video()[2];SystemClock.sleep(350);check(video()[2]!=before,"idle video advances");
   main(()->{activity.render();activity.render();check(activity.pet.video==original,"same video view after repeated render");});ready();
   main(()->activity.pet.stage.performClick());SystemClock.sleep(250);check(ActionPlan.TAPS.contains(activity.pet.video.pending),"tap selects real reaction clip");
   cover=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(200);
   main(()->check(!activity.pet.running&&activity.pet.video.suspended,"background pauses home animation"));check(video()[1]==0,"MediaPlayer actually paused");
   main(()->cover.finish());SystemClock.sleep(350);ready();check(activity.pet.video==original,"resume reuses video view");
   waitForIdleSync();SystemClock.sleep(300);main(()->check(activity.getWindow().getDecorView().hasWindowFocus(),"home is foreground"));
   try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"hero-home.png"))){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"home screenshot saved");}
   main(()->activity.finish());for(int i=0;i<60&&original.renderThread.isAlive();i++)SystemClock.sleep(100);check(original.closed&&!original.renderThread.isAlive(),"destroy releases render thread");
   result.putString("stream","PASS: "+checks+" Android animated-home checks\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}
  finally{try{main(()->{if(cover!=null)cover.finish();if(activity!=null)activity.finish();AgentRuntime.cancel();});}catch(Exception ignored){}}
  finish(code,result);
 }
}
