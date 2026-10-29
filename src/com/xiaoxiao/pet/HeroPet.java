package com.xiaoxiao.pet;
import android.app.Activity;import android.graphics.BitmapFactory;import android.os.*;import android.view.*;import android.widget.*;

/**
 * Her portrait on the home page, alive: the desktop pet's alpha clips, picked by ActionPlan from the clock, season and
 * festivals, with a reaction when tapped. The still portrait (a frame of her idle clip) stays underneath until the first
 * video frame is drawn, and is all there is when system animations are off. One player per activity, carried across
 * render() and paused with the activity; while the app is in front the desktop pet is hidden and paused, so only one plays.
 */
final class HeroPet {
 static final String ROLE="main";
 final Activity activity;final FrameLayout stage;final ImageView poster;final AlphaVideo video;final ActionPlan plan;final Handler handler=new Handler(Looper.getMainLooper());String current=Data.idle(ROLE);boolean running;
 final Runnable next=new Runnable(){public void run(){if(!running||video==null)return;if(!current.equals(Data.idle(ROLE))){later();return;}String pick=plan.idle(PetService.moment(),Data.random);if(pick.equals(Data.idle(ROLE)))later();else video.play(pick);}};

 HeroPet(Activity activity){
  this.activity=activity;stage=new FrameLayout(activity);poster=new ImageView(activity);poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
  try(java.io.InputStream in=activity.getAssets().open(Data.role(ROLE).optString("portrait"))){poster.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){}
  stage.setContentDescription("蓝色鲸鱼娘大肥鱼");stage.addView(poster);
  plan=new ActionPlan(GrowthStore.unlockedOrAll(Data.actions(ROLE)));
  if(!UiMotion.enabled()){video=null;return;}
  video=new AlphaVideo(activity,ROLE);video.actionListener=name->current=name;video.completed=this::later;
  video.shown=()->poster.animate().alpha(0).setDuration(180).start();
  stage.addView(video);stage.setOnClickListener(v->tap());
 }
 /** Puts her in the hero card (render() rebuilds the page); the portrait covers the gap until the clip is drawn again. */
 void attach(LinearLayout hero,int height){
  plan.available(GrowthStore.unlockedOrAll(Data.actions(ROLE)));
  if(stage.getParent()!=null)((ViewGroup)stage.getParent()).removeView(stage);
  int room=activity.getResources().getDisplayMetrics().widthPixels-Apple.dp(activity,76),w=Math.min(height*4/3,room),h=w*3/4;
  for(View v:new View[]{poster,video})if(v!=null)v.setLayoutParams(new FrameLayout.LayoutParams(w,h,Gravity.CENTER));
  poster.animate().cancel();poster.setAlpha(1f);hero.addView(stage,new LinearLayout.LayoutParams(-1,height));
 }
 void tap(){if(video==null)return;handler.removeCallbacks(next);video.play(plan.tap(System.currentTimeMillis(),current,Data.random));}
 void later(){handler.removeCallbacks(next);if(running)handler.postDelayed(next,ActionPlan.gapMillis(PetService.moment(),Data.random));}
 void resume(){running=true;if(video!=null)video.suspend(false);later();}
 void pause(){running=false;handler.removeCallbacks(next);if(video!=null)video.suspend(true);}
 void release(){pause();if(video!=null)video.release();}
}
