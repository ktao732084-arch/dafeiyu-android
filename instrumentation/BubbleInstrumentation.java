package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.UUID;

/** Product overlays on Android, isolated UID and synthetic speech; no model credentials. */
public final class BubbleInstrumentation extends Instrumentation {
 PetService service;PetService.Pet pet;Activity activity;int checks;
 interface Checked {void run()throws Exception;}
 void main(Checked action){final Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 void settle(){waitForIdleSync();SystemClock.sleep(550);}
 void shot(String name)throws Exception {settle();File file=new File(getTargetContext().getExternalFilesDir(null),name+".png");try(FileOutputStream out=new FileOutputStream(file)){check(getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out),"screenshot "+name);}}
 void fit(){
  check(pet.blp.y>=service.safeTop,"bubble above system bar");check(pet.blp.y+pet.bubbleHeight<=service.safeBottom,"bubble inside bottom edge");
  for(int i=0;i<pet.bubbleStack.getChildCount();i++){TextView card=(TextView)pet.bubbleStack.getChildAt(i);check(card.getLayout()!=null&&card.getLayout().getHeight()<=card.getHeight()-card.getCompoundPaddingTop()-card.getCompoundPaddingBottom(),"real font fits card "+i);check(!card.hasOnClickListeners(),"no history link on speech card");}
 }
 void touch(int action){long t=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(t,t,action,450,700,0);pet.touch(event);event.recycle();}
 TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",false).putBoolean("agent.memory",false).putBoolean("agent.diary",false).commit();UpdateRuntime.channels=c->"";PersonaManager.initialize("main","classic");AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"bubbles-"+UUID.randomUUID()));GrowthStore g=GrowthStore.get();while(g.pending()>0){Growth.Scene scene=g.nextScene();if(scene==null)break;g.shown(scene);}g.flush();});
   activity=startActivitySync(new Intent(getTargetContext(),TouchReplyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   main(()->{FrameLayout backdrop=new FrameLayout(activity);backdrop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff10283e,0xff365870,0xffa2b4c7}));activity.setContentView(backdrop);});
   getTargetContext().startForegroundService(new Intent(getTargetContext(),PetService.class).setAction("show").putExtra("role","main"));
   for(int i=0;i<100;i++){main(()->{service=PetService.instance;if(service!=null)pet=service.pets.get("main");});if(pet!=null&&pet.frame!=null)break;SystemClock.sleep(50);}
   main(()->{check(pet!=null&&pet.frame!=null,"product overlay attached");pet.rotation=false;pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.60f;pet.update();pet.clearBubble();pet.appendBubble("我又不是抛接玩具。",null);});settle();main(this::fit);shot("bubble-one");
   main(()->pet.appendBubble("稳住、稳稳稳……",null));settle();main(()->pet.appendBubble("慢一点，我还没站稳呢。",null));settle();
   main(()->{check(pet.bubbleStack.getChildCount()==3,"three separate sentence cards");fit();});shot("bubble-three");
   final Dialog[] original={null};final TextView[] oldest={null};final int[] receipts={0};
   main(()->{original[0]=pet.bubbleDialog;oldest[0]=(TextView)pet.bubbleStack.getChildAt(0);pet.appendBubble("好啦，现在站稳了。",()->receipts[0]++);check(pet.bubbleStack.getChildCount()==3,"never four cards during retirement");check(receipts[0]==0,"new receipt waits for insertion");});
   SystemClock.sleep(120);main(()->check(oldest[0].getAlpha()<.72f,"oldest fades before removal"));settle();
   main(()->{check(pet.bubbleDialog==original[0],"same overlay reused");check(pet.bubbleStack.getChildCount()==3,"three cards after fourth");check(pet.bubbleStack.indexOfChild(oldest[0])<0,"oldest removed");check(receipts[0]==1,"receipt once");check(pet.bubble.getText().toString().equals("好啦，现在站稳了。"),"newest is fourth");fit();});shot("bubble-four");
   main(()->{pet.appendBubble("这句不应在清理后出现。",()->receipts[0]++);pet.clearBubble();});settle();
   main(()->{check(pet.bubbleStack==null&&pet.bubbleDialog==null,"clear removes stack");check(receipts[0]==1,"cancelled eviction has no receipt");pet.showBubble("屏幕顶端也能看清这一句。",false);pet.y=-pet.state.top;pet.x=-pet.state.left;pet.update();});settle();
   main(()->{fit();check(!pet.bubbleStack.above,"top edge places stack below");});shot("bubble-edge");
   main(()->{pet.clearBubble();Data.prefs().edit().putString("appearance.mode","night").commit();Apple.load();pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.60f;pet.update();pet.showBubble("放大字体之后，这句也要完整读得清楚。",false);pet.bubble.setTextSize(24);pet.positionBubbles();});settle();main(this::fit);shot("bubble-large-night");
   main(()->{pet.clearBubble();Data.prefs().edit().putString("appearance.mode","day").commit();Apple.load();pet.reply("好呀。明天见！");});settle();
   main(()->check(pet.bubbleStack.getChildCount()==1&&pet.bubble.getText().toString().equals("好呀。"),"local replies also start one sentence at a time"));SystemClock.sleep(4200);settle();
   main(()->{check(pet.bubbleStack.getChildCount()==2&&pet.bubble.getText().toString().equals("明天见！"),"local second sentence arrives separately");pet.clearBubble();});
   main(()->{pet.clearBubble();Data.prefs().edit().putBoolean("agent.aside",true).commit();AgentStore db=AgentRuntime.store();db.commit(db.epoch(),"你在吗","我在呢。","calm",null,false,false,null,null,"bubble-merge",System.currentTimeMillis(),null,true,null,null,0,"",Boolean.FALSE,null,"这句是小声嘀咕");pet.speak("我在呢。","bubble-merge");});settle();
   main(()->{check(pet.bubble.getText().toString().contains("小声嘀咕"),"latest-main aside survives the new sentence stack");check(!pet.bubble.getText().toString().contains("聊天记录"),"normal speech has no history footer");check(!pet.bubble.hasOnClickListeners(),"normal speech has no history jump");fit();});
   main(()->{pet.clearBubble();pet.x=service.sw()/2f-pet.width/2f;pet.y=service.sh()*.60f;pet.update();touch(MotionEvent.ACTION_DOWN);});SystemClock.sleep(500);main(()->touch(MotionEvent.ACTION_UP));settle();
   main(()->{check(pet.menuDialog!=null&&pet.menuDialog.isShowing(),"real long press opens menu");WindowManager.LayoutParams p=pet.menuDialog.getWindow().getAttributes();check(p.x>=0&&p.y>=service.safeTop&&p.y+p.height<=service.safeBottom,"menu fits screen");Dialog first=pet.menuDialog;pet.menu();check(pet.menuDialog==first,"no duplicate menus");});shot("pet-menu");
   main(()->{MotionEvent outside=MotionEvent.obtain(0,0,MotionEvent.ACTION_OUTSIDE,0,0,0);pet.menuDialog.getWindow().getDecorView().dispatchTouchEvent(outside);outside.recycle();check(!pet.menuDialog.isShowing(),"outside touch closes menu");});settle();
   main(()->{check(pet.menuDialog==null,"outside dismissal releases reference");pet.menu();TextView chat=find(pet.menuDialog.getWindow().getDecorView(),"聊天");check(chat!=null,"chat tile exists");chat.performClick();});settle();
   main(()->{check(Chat.isPetChatOpen(pet.id),"chat tile opens real chat");Chat.closePet(pet.id);});settle();
   main(()->{pet.menu();TextView close=find(pet.menuDialog.getWindow().getDecorView(),"×");check(close!=null,"explicit close control exists");close.performClick();});settle();
   main(()->{check(pet.menuDialog==null,"close control releases menu");pet.menu();pet.hide();check(pet.menuDialog==null,"hide closes menu");});
   result.putString("stream","PASS: "+checks+" Android bubble and menu checks\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e);result.putString("stack",android.util.Log.getStackTraceString(e));}
  finally{try{main(()->{AgentRuntime.cancel();if(pet!=null){PetMenu.close(pet);pet.clearBubble();}getTargetContext().stopService(new Intent(getTargetContext(),PetService.class));if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
