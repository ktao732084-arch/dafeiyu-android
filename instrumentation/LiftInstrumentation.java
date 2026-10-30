package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Real pointer down/move/hold/up, real video loop, synthetic model and separate app UID. */
public final class LiftInstrumentation extends Instrumentation {
 PetService service;PetService.Pet pet;Activity activity;int checks;AtomicInteger calls=new AtomicInteger();
 interface Checked{void run()throws Exception;}
 void main(Checked action){final Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void motion(int action,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);pet.touch(e);e.recycle();}
 void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(400);try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){check(getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out),"screenshot "+name);}}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",true).putBoolean("agent.memory",false).putBoolean("agent.diary",false).putBoolean("physics",false).commit();UpdateRuntime.channels=c->"";PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled-lift","test-only-key",false);AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"lift-"+UUID.randomUUID()));GrowthStore g=GrowthStore.get();while(g.pending()>0){Growth.Scene scene=g.nextScene();if(scene==null)break;g.shown(scene);}g.flush();});
   final String persona=PersonaManager.prompt("main");
   AgentRuntime.model=(messages,token,progress)->{calls.incrementAndGet();check(messages.getJSONObject(0).getString("content").contains(persona),"frozen persona reaches held request");check(messages.toString().contains("durationMs")&&messages.toString().contains("holding"),"real ongoing hold evidence");return "{\"reply\":\"先把本鲸放下来，我又不是钥匙扣。\",\"emotion\":\"playful\"}";};
   activity=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{FrameLayout bg=new FrameLayout(activity);bg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff142b43,0xff708da5}));activity.setContentView(bg);});
   getTargetContext().startForegroundService(new Intent(getTargetContext(),PetService.class).setAction("show").putExtra("role","main"));
   for(int i=0;i<100;i++){main(()->{service=PetService.instance;if(service!=null)pet=service.pets.get("main");});if(pet!=null&&pet.frame!=null)break;SystemClock.sleep(50);}
   main(()->{check(pet!=null&&pet.frame!=null,"overlay attached");pet.rotation=false;pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.55f;pet.update();motion(MotionEvent.ACTION_DOWN,450,850);motion(MotionEvent.ACTION_MOVE,470,730);check(pet.lift.active&&ActionPlan.DRAG.equals(pet.current),"drag enters suspended animation");});
   SystemClock.sleep(2200);check(calls.get()==0,"brief lift waits before model");shot("pet-lift");
   boolean[] spoken={false};for(int i=0;i<160;i++){main(()->spoken[0]=pet.bubble!=null);if(spoken[0])break;SystemClock.sleep(50);}
   main(()->{check(spoken[0]&&pet.dragging&&pet.lift.active,"complaint appears before release");check(ActionPlan.DRAG.equals(pet.current),"speech preserves hanging animation");});check(calls.get()==1,"one model call");shot("pet-lift-reply");
   JSONObject state=AgentRuntime.store().snapshot(),turn=state.getJSONArray("turns").getJSONObject(0),evidence=turn.getJSONObject("interaction");check(evidence.getInt("count")==1&&evidence.getLong("durationMs")>=5000,"one sustained event with measured duration");check(state.optInt("userTurns")==0&&turn.optInt("displayed")>0,"physical reply saved without fabricated user text");check(persona.equals(PersonaManager.prompt("main")),"persona unchanged");
   while(pet.lift.duration()<11500)SystemClock.sleep(100);
   CountDownLatch inspected=new CountDownLatch(1);boolean[] loop={false};pet.video.queueEvent(()->{loop[0]=pet.video.player!=null&&pet.video.player.isLooping()&&ActionPlan.DRAG.equals(pet.video.pending);inspected.countDown();});check(inspected.await(3,TimeUnit.SECONDS)&&loop[0],"real decoder still loops hanging clip after its ten-second duration");check(calls.get()==1,"long hold does not spam model");
   main(()->{motion(MotionEvent.ACTION_UP,470,730);check(!pet.lift.active&&!pet.dragging,"release ends hold");check(Data.idle(pet.role).equals(pet.current),"release restores idle");});SystemClock.sleep(600);
   main(()->{check(Math.abs(pet.video.getRotation())<.01f&&Math.abs(pet.video.getScaleX()-1)<.01f,"release clears lift transforms");Data.prefs().edit().putBoolean("agent.touchReply",false).commit();motion(MotionEvent.ACTION_DOWN,470,730);motion(MotionEvent.ACTION_MOVE,490,700);});SystemClock.sleep(5400);check(calls.get()==1,"disabled setting also blocks held complaint");
   main(()->{motion(MotionEvent.ACTION_CANCEL,490,700);check(!pet.lift.active,"cancel releases held state");});
   result.putString("stream","PASS: "+checks+" Android lift checks\n");
  }catch(Throwable error){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));}
  finally{try{main(()->{AgentRuntime.cancel();if(pet!=null){pet.lift.end();pet.clearBubble();}getTargetContext().stopService(new Intent(getTargetContext(),PetService.class));if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
