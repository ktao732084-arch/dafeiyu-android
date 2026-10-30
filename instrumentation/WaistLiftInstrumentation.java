package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.atomic.*;

/** Real rear-waist pose, pointer anchor, release placement and region-aware speech on Android. */
public final class WaistLiftInstrumentation extends Instrumentation {
 PetService service;PetService.Pet pet;Activity activity;int checks;AtomicInteger calls=new AtomicInteger();int blendFrames;float pointerX,pointerY,footX,footY;ParcelFileDescriptor recording;long recordStarted;
 interface Checked{void run()throws Exception;}
 void main(Checked action){final Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void motion(int action,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);pet.touch(e);e.recycle();}
 void settle(){waitForIdleSync();SystemClock.sleep(500);}
 void shot(String name)throws Exception{settle();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){check(getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out),"screenshot "+name);}}
 void grab(boolean waist){
  pointerX=pet.x+pet.width*.5f;pointerY=pet.y+(pet.normT+(waist?.8f:.25f)*(pet.normB-pet.normT))*pet.width*.75f;
  motion(MotionEvent.ACTION_DOWN,pointerX,pointerY);pointerX+=15;pointerY-=8;motion(MotionEvent.ACTION_MOVE,pointerX,pointerY);
 }
 void frameFits(){check(pet.lp.x>=-1&&pet.lp.y>=-1&&pet.lp.x+pet.lp.width<=service.sw()+1&&pet.lp.y+pet.lp.height<=service.sh()+1,"pose window fits edge without cropping body");}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",true).putBoolean("agent.memory",false).putBoolean("agent.diary",false).putBoolean("physics",false).commit();UpdateRuntime.channels=c->"";PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled-waist","test-only-key",false);AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"waist-"+UUID.randomUUID()));GrowthStore g=GrowthStore.get();while(g.pending()>0){Growth.Scene scene=g.nextScene();if(scene==null)break;g.shown(scene);}g.flush();});
   final String persona=PersonaManager.prompt("main");
   AgentRuntime.model=(messages,token,progress)->{calls.incrementAndGet();check(messages.getJSONObject(0).getString("content").contains(persona),"frozen persona retained");check(messages.toString().contains("waist")&&messages.toString().contains("后腰"),"model receives real waist region");return "{\"reply\":\"别拎后腰啦，围裙要皱了。\",\"emotion\":\"playful\"}";};
   activity=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{FrameLayout bg=new FrameLayout(activity);bg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff142b43,0xff708da5}));activity.setContentView(bg);});
   getTargetContext().startForegroundService(new Intent(getTargetContext(),PetService.class).setAction("show").putExtra("role","main"));
   for(int i=0;i<100;i++){main(()->{service=PetService.instance;if(service!=null)pet=service.pets.get("main");});if(pet!=null&&pet.frame!=null)break;SystemClock.sleep(50);}
   main(()->{check(pet!=null&&pet.frame!=null,"overlay attached");pet.rotation=false;pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.43f;pet.update();});settle();
   main(()->check(activity.hasWindowFocus(),"preview activity owns foreground before recording"));
   File movie=new File(getTargetContext().getExternalFilesDir(null),"waist-motion.mp4");recording=getUiAutomation().executeShellCommand("screenrecord --size 720x1280 --time-limit 12 --bit-rate 3000000 "+movie.getAbsolutePath());recordStarted=SystemClock.uptimeMillis();SystemClock.sleep(500);
   main(()->{grab(true);check("waist".equals(pet.lift.region)&&pet.lift.waist(),"waist down selects distinct pose");check(pet.video.suspended&&pet.lift.waistView.getAlpha()<1,"entry starts a blend from paused default video");check(pet.lift.visualStartedAt<0,"entry clock waits for actual first paint, not asset decoding");});settle();
   main(()->{check(pet.lift.waistView.isShown()&&pet.lift.waistView.getWidth()>0,"waist sprite laid out");check(pet.video.getAlpha()==0&&pet.lift.waistView.getAlpha()==1,"entry blend finishes on waist pose");check(pet.lift.visualStartedAt>=pet.lift.startedAt,"real first paint started the entry transition");check(Math.abs(pet.x+pet.lift.grabX*pet.width-pointerX)<2&&Math.abs(pet.y+pet.lift.grabY*pet.width*.75f-pointerY)<2,"back knot tracks original finger contact");});
   final float originX=pointerX,originY=pointerY;final float[] peak={0};
   for(int i=1;i<=12;i++){final int step=i;main(()->{pointerX=originX+(float)Math.sin(step*Math.PI/6)*60;pointerY=originY+(float)Math.sin(step*Math.PI/12)*18;motion(MotionEvent.ACTION_MOVE,pointerX,pointerY);peak[0]=Math.max(peak[0],Math.abs(pet.lift.motion.angle));});SystemClock.sleep(40);}
   check(peak[0]>.1f,"actual moving grip drives inertial swing");shot("waist-held");
   boolean[] spoken={false};for(int i=0;i<180;i++){main(()->spoken[0]=pet.bubble!=null);if(spoken[0])break;SystemClock.sleep(50);}
   main(()->{check(spoken[0]&&pet.lift.waist()&&pet.dragging,"waist complaint preserves suspended pose");check(pet.bubble.getText().toString().contains("后腰"),"waist-specific controlled reply");});shot("waist-reply");
   JSONObject evidence=AgentRuntime.store().snapshot().getJSONArray("turns").getJSONObject(0).getJSONObject("interaction");check("waist".equals(evidence.getString("grabRegion")),"history has actual grab region");check(calls.get()==1,"one held request");
   main(()->{PointF foot=pet.lift.waistView.footWorld();footX=foot.x;footY=foot.y;motion(MotionEvent.ACTION_UP,pointerX,pointerY);check(!pet.lift.active&&pet.lift.settling&&pet.lift.waistView!=null,"release blends back instead of deleting pose in one frame");pet.lift.settleAnimator.addUpdateListener(a->{float t=(Float)a.getAnimatedValue();if(t>0&&t<1)blendFrames++;});check(Math.abs(pet.x+pet.width*.5f-footX)<3&&Math.abs(pet.y+pet.normB*pet.width*.75f-footY)<3,"landing follows rotated feet on both axes");});settle();
   main(()->{check(blendFrames>0,"release paints intermediate animation frames");check(pet.lift.waistView==null&&!pet.lift.settling&&pet.video.getAlpha()==1&&!pet.video.suspended,"default video fully restored after release blend");});shot("waist-released");

   main(()->{pet.clearBubble();Data.prefs().edit().putBoolean("agent.touchReply",false).commit();grab(false);check("head".equals(pet.lift.region)&&!pet.lift.waist(),"head still uses original animation");check(pet.video.getAlpha()==1&&ActionPlan.DRAG.equals(pet.current),"head video visible");});shot("head-held");
   while(SystemClock.uptimeMillis()-recordStarted<12500)SystemClock.sleep(100);if(recording!=null){recording.close();recording=null;}main(()->check(activity.hasWindowFocus(),"preview keeps foreground through recording"));
   main(()->{motion(MotionEvent.ACTION_CANCEL,pointerX,pointerY);grab(true);check(pet.lift.waist(),"second waist lift recreates pose");pet.x=service.sw();pet.y=service.sh();pet.update();frameFits();pet.x=-service.sw();pet.y=-service.sh();pet.update();frameFits();pet.hide();check(pet.lift.waistView==null&&!pet.lift.active&&pet.frame==null,"hide removes pose and ends hold");});
   result.putString("stream","PASS: "+checks+" Android waist/head checks\n");
  }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));}
  finally{if(recording!=null)try{recording.close();}catch(Exception ignored){}try{main(()->{AgentRuntime.cancel();if(pet!=null){pet.lift.end();pet.clearBubble();}getTargetContext().stopService(new Intent(getTargetContext(),PetService.class));if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
