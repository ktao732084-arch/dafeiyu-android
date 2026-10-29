package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.os.*;import android.view.MotionEvent;import org.json.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.io.*;

/** Real MotionEvents, video callbacks, overlay and local history; never uses a real model credential. */
public final class TouchReplyInstrumentation extends Instrumentation {
 Activity activity;PetService service;PetService.Pet pet;int checks;final AtomicInteger calls=new AtomicInteger();
 interface Checked{void run()throws Exception;}
 void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
 void main(Checked action){runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void tap(){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,450,700,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,450,700,0);pet.touch(down);pet.touch(up);down.recycle();up.recycle();}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",true).putBoolean("agent.memory",false).putBoolean("agent.diary",false).commit();UpdateRuntime.channels=c->"";PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled-touch","test-only-key",false);AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"touch-"+java.util.UUID.randomUUID()));
    // A synthetic companion whose introductory scene is already seen, to isolate the interaction path.
    GrowthStore g=GrowthStore.get();while(g.pending()>0){Growth.Scene scene=g.nextScene();if(scene==null)break;g.shown(scene);}g.flush();});
   AgentRuntime.model=(messages,token,progress)->{calls.incrementAndGet();check(messages.toString().contains("interaction_event"),"physical event evidence reaches model");return "{\"reply\":\"我在呢，慢点戳。再戳我，我可要装凶了。\",\"emotion\":\"playful\"}";};
   activity=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   getTargetContext().startForegroundService(new Intent(getTargetContext(),PetService.class).setAction("show").putExtra("role","main"));
   for(int i=0;i<100;i++){main(()->{service=PetService.instance;if(service!=null)pet=service.pets.get("main");});if(pet!=null&&pet.frame!=null)break;SystemClock.sleep(50);}
   main(()->{check(pet!=null&&pet.frame!=null,"product overlay attached");pet.rotation=false;pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.55f;pet.update();tap();tap();tap();});
   AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);long plan=AgentRuntime.store().snapshot().optLong("proactiveNextAt");
   boolean[] shown={false};for(int i=0;i<700;i++){main(()->shown[0]=pet.bubble!=null&&pet.bubble.getText().toString().contains("慢点戳"));if(shown[0])break;SystemClock.sleep(50);}
   check(shown[0],"visible reactive bubble after three actual taps");check(calls.get()==1,"one model call for the burst");
   JSONObject s=AgentRuntime.store().snapshot();JSONArray turns=s.getJSONArray("turns");check(turns.length()==1,"one stored response");JSONObject turn=turns.getJSONObject(0);check(turn.getJSONObject("interaction").getInt("count")==3,"history labels the three gestures");check(turn.optInt("displayed")>0,"display receipt saved");check(s.optInt("userTurns")==0&&s.optLong("lastUser")==0,"physical response is not a typed message");check(s.optLong("proactiveNextAt")==plan,"reply leaves idle schedule unchanged");
   // Text is assigned before the overlay reaches SurfaceFlinger; wait for actual layout and drawing.
   waitForIdleSync();SystemClock.sleep(600);main(()->check(pet.bubble!=null&&pet.bubble.isShown()&&pet.bubble.getWidth()>0&&pet.bubble.getHeight()>0,"bubble laid out and visible"));
   java.io.File screenshot=new java.io.File(getTargetContext().getExternalFilesDir(null),"touch-reply.png");try(FileOutputStream out=new FileOutputStream(screenshot)){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot saved");}
   check(AgentRuntime.store().conversationRange(Long.MIN_VALUE,Long.MAX_VALUE,null,20).length()==0,"reaction is absent from chat query");
   check(AgentRuntime.context("刚才怎么了",false).toString().contains("慢点戳"),"shown reaction is still remembered");
   // Inspect the last segment too: it used to attach an invitation to open chat.
   boolean[] last={false};for(int i=0;i<200;i++){main(()->last[0]=pet.bubble!=null&&pet.bubble.getText().toString().contains("装凶"));if(last[0])break;SystemClock.sleep(50);}
   check(last[0],"final reaction segment displayed");
   main(()->{check(!pet.bubble.getText().toString().contains("聊天记录"),"reaction has no chat footer");pet.bubble.performClick();check(pet.bubble==null,"tap dismisses interaction bubble");check(ChatPage.current.get()==null,"tap does not open chat");
    ChatPage chat=new ChatPage(activity,"main","main");chat.open();check(chat.records.length()==0,"actual chat page hides stored reaction");chat.dialog.dismiss();Data.prefs().edit().putBoolean("agent.touchReply",false).commit();tap();tap();tap();});SystemClock.sleep(2400);check(calls.get()==1,"disabled setting does not request another reply");
   result.putString("stream","PASS: "+checks+" Android touch-response checks\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e);result.putString("stack",android.util.Log.getStackTraceString(e));}
  finally{try{main(()->{AgentRuntime.cancel();if(pet!=null)pet.clearBubble();getTargetContext().stopService(new Intent(getTargetContext(),PetService.class));if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
