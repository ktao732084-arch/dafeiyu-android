package com.xiaoxiao.pet;

import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import org.json.*;

/** One physical lift, independent of a long press menu or a burst of taps. Main thread only. */
final class LiftInteraction {
    static final long HOLD_MS=5000;
    final PetService.Pet pet;
    boolean active,attempted,suppressed;
    long serial,startedAt,startedWall,visualStartedAt=-1;
    Runnable check;
    String region="head";
    float grabX=.5f,grabY=.4f;
    WaistLiftView waistView;
    final SuspensionMotion motion=new SuspensionMotion();
    boolean settling;
    float exitOffsetX,exitOffsetY,exitAlpha;
    android.animation.ValueAnimator settleAnimator;
    LiftInteraction(PetService.Pet pet){this.pet=pet;}
    static boolean waistHit(float normalizedY,float top,float bottom){return bottom>top&&(normalizedY-top)/(bottom-top)>=.62f;}
    void capture(float rawX,float rawY){
        grabX=(rawX-pet.x)/pet.width;grabY=(rawY-pet.y)/(pet.width*.75f);
        region=waistHit(grabY,pet.normT,pet.normB)?"waist":"head";
    }
    boolean waist(){return active&&waistView!=null;}
    boolean renderingWaist(){return waistView!=null;}
    void visualFrame(){if(visualStartedAt<0)visualStartedAt=SystemClock.uptimeMillis();}
    float entry(){if(!UiMotion.enabled())return 1;if(visualStartedAt<0)return 0;float t=Math.min(1,(SystemClock.uptimeMillis()-visualStartedAt)/220f);return t*t*(3-2*t);}
    float pinX(){return active?grabX*pet.width:exitOffsetX;}
    float pinY(){return active?grabY*pet.width*.75f:exitOffsetY;}
    void resetForTouch(){if(active)end();finishVisual();}
    float poseWidth(){return pet.width*.75f*(pet.normB-pet.normT)/WaistLiftView.ASPECT;}
    void bounds(){
        float w=poseWidth()/pet.width,h=poseWidth()*WaistLiftView.ASPECT/(pet.width*.75f),margin=.12f;
        float ax=pinX()/pet.width,ay=pinY()/(pet.width*.75f);
        pet.cropL=ax-w*WaistLiftView.PIN_U-margin;pet.cropR=ax+w*(1-WaistLiftView.PIN_U)+margin;
        pet.cropT=ay-h*WaistLiftView.PIN_V-margin;pet.cropB=ay+h*(1-WaistLiftView.PIN_V)+margin;
        if(settling||entry()<1){pet.cropL=Math.min(pet.cropL,pet.normL-.04f);pet.cropR=Math.max(pet.cropR,pet.normR+.04f);pet.cropT=Math.min(pet.cropT,pet.normT-.04f);pet.cropB=Math.max(pet.cropB,pet.normB+.04f);}
    }
    String holding(){return "waist".equals(region)?"用户从后腰的衣服处把你拎了起来，身体向前垂着":"用户从头部把你提了起来";}
    void begin(){
        // Interrupt an old response without losing completed short drags in the same burst.
        cancelReply();pet.touchReplies.generation++;AgentRuntime.interruptTouch(pet);
        if(pet.touchReplyCheck!=null){pet.owner().handler.removeCallbacks(pet.touchReplyCheck);pet.touchReplyCheck=null;}
        if(pet.speech!=null)pet.speech.cancel();pet.endScene();pet.clearBubbleVisual();
        AgentRuntime.touch(holding()+"，手指还没有松开","lift",pet.id,System.currentTimeMillis());
        active=true;attempted=false;suppressed=false;serial++;startedAt=SystemClock.uptimeMillis();startedWall=System.currentTimeMillis();visualStartedAt=-1;
        motion.reset(startedAt,pet.x+grabX*pet.width,pet.y+grabY*pet.width*.75f);
        if("waist".equals(region)&&pet.frame!=null&&pet.video!=null&&"main".equals(pet.role)){
            try{waistView=new WaistLiftView(pet.owner(),this);waistView.setAlpha(.01f);pet.frame.addView(waistView,new android.widget.FrameLayout.LayoutParams(-1,-1));pet.video.setAlpha(1);pet.video.suspend(true);}
            catch(java.io.IOException missing){region="head";Data.log(pet.role,"后腰姿态不可用，沿用悬空动画");}
        }
        if(pet.frame!=null)pet.frame.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if(pet.video!=null)pet.video.animate().cancel();
        if(pet.plan.has(ActionPlan.DRAG)){pet.play(ActionPlan.DRAG);if(pet.video!=null)pet.applyBounds(ActionPlan.DRAG);}
        check=this::consider;pet.owner().handler.postDelayed(check,HOLD_MS);
    }
    long duration(){return Math.max(0,SystemClock.uptimeMillis()-startedAt);}
    void consider(){
        if(!active||suppressed||attempted||!pet.dragging||!pet.moved)return;
        PetService service=pet.owner();
        if(!TouchReply.allowed(service,pet)||!AgentRuntime.configured()||!PersonaManager.isInitialized(pet.role)){cancelReply();return;}
        long now=SystemClock.uptimeMillis();
        if(duration()<HOLD_MS||AgentRuntime.busy()||!free()||now-pet.touchReplies.lastAttempt<TouchReply.REQUEST_GAP_MS){service.handler.postDelayed(check,500);return;}
        try{
            long held=duration(),epoch=AgentRuntime.store().epoch();
            String note=holding()+"，已经持续"+(held/1000)+"秒，目前还没有松手";
            JSONObject event=new JSONObject().put("pet",pet.id).put("kind","lift_hold").put("note",note).put("at",System.currentTimeMillis()).put("startedAt",startedWall).put("durationMs",held);
            JSONObject evidence=new JSONObject().put("count",1).put("phase","holding").put("grabRegion",region).put("durationMs",held).put("events",new JSONArray().put(event.put("grabRegion",region)));
            TouchReply.Request request=new TouchReply.Request(pet.touchReplies.generation,epoch,now,evidence,serial);
            AgentRuntime.touch(note,"lift_hold",pet.id,System.currentTimeMillis());
            attempted=AgentRuntime.respondToTouch(service,pet,request);
            if(attempted){pet.touchReplies.lastAttempt=now;}
            else if(active&&!suppressed)service.handler.postDelayed(check,500);
        }catch(Exception unavailable){cancelReply();Data.log(pet.role,"提起回应暂不可用");}
    }
    boolean free(){return active&&pet.dragging&&pet.moved&&!pet.longPressed&&pet.scene==null&&pet.menuDialog==null&&pet.bubble==null&&(pet.speech==null||!pet.speech.active);}
    boolean current(TouchReply.Request request){return active&&!suppressed&&request.liftSerial==serial;}
    void cancelReply(){suppressed=true;if(check!=null){pet.owner().handler.removeCallbacks(check);check=null;}}
    void end(){
        if(!active){cancelReply();return;}
        boolean fromWaist=waist();
        if(fromWaist){
            float anchorX=pet.x+pinX(),anchorY=pet.y+pinY();
            android.graphics.PointF foot=waistView.footWorld();exitAlpha=entry();
            // On a very brief lift, blend the landing point too; do not teleport to a barely visible pose.
            float footX=pet.x+pet.width*.5f,footY=pet.y+pet.normB*pet.width*.75f;
            pet.x+=(foot.x-footX)*exitAlpha;pet.y+=(foot.y-footY)*exitAlpha;
            exitOffsetX=anchorX-pet.x;exitOffsetY=anchorY-pet.y;
        }
        active=false;serial++;cancelReply();
        if(attempted)TouchReply.cancel(pet);
        else {pet.touchReplies.generation++;AgentRuntime.interruptTouch(pet);}
        if(fromWaist&&pet.video!=null){
            settling=true;pet.video.animate().cancel();pet.video.setRotation(0);pet.video.setScaleX(1);pet.video.setScaleY(1);
            if(!UiMotion.enabled()||!pet.owner().animating()||pet.frame==null){finishVisual();return;}
            boolean landing=!Data.prefs().getBoolean("physics",true)||SystemClock.uptimeMillis()-pet.lastMove>100||Math.hypot(pet.vx,pet.vy)<Math.max(900,400*pet.owner().getResources().getDisplayMetrics().density);
            pet.video.setPivotX(pet.width*.5f);pet.video.setPivotY(pet.width*.75f*pet.normB);
            settleAnimator=android.animation.ValueAnimator.ofFloat(0,1);settleAnimator.setDuration(240);
            settleAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            settleAnimator.addUpdateListener(anim->{if(settleAnimator!=anim||pet.video==null||waistView==null)return;float t=(Float)anim.getAnimatedValue();waistView.setAlpha(exitAlpha*(1-t));pet.video.setAlpha(1-exitAlpha*(1-t));float compression=landing?(float)Math.sin(Math.PI*t)*.025f:0;pet.video.setScaleY(1-compression);pet.video.setScaleX(1+compression*.45f);pet.update();});
            settleAnimator.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator anim){if(settleAnimator==anim){settleAnimator=null;finishVisual();}}});
            pet.update();pet.state.x=pet.x;pet.state.y=pet.y;settleAnimator.start();
        }else if(pet.video!=null){
            if(UiMotion.enabled())pet.video.animate().rotation(0).scaleX(1).scaleY(1).setDuration(180).start();
            else {pet.video.setRotation(0);pet.video.setScaleX(1);pet.video.setScaleY(1);}
        }
    }
    void finishVisual(){
        if(settleAnimator!=null){android.animation.ValueAnimator previous=settleAnimator;settleAnimator=null;previous.cancel();}
        boolean had=waistView!=null;settling=false;
        if(had){if(pet.frame!=null)pet.frame.removeView(waistView);waistView=null;pet.geometryWidth=0;}
        if(had&&pet.video!=null){pet.video.setAlpha(1);pet.video.setRotation(0);pet.video.setScaleX(1);pet.video.setScaleY(1);pet.video.suspend(!pet.owner().animating());if(pet.visible&&pet.owner().animating())pet.video.play(pet.current);}
        if(had&&pet.frame!=null){pet.applyBounds(pet.current);pet.state.x=pet.x;pet.state.y=pet.y;}
    }
    float amount(){return active&&UiMotion.enabled()?Math.min(1,duration()/180f):0;}
    float offset(){return 0;}
    void draw(){
        if((!active&&!settling)||pet.video==null)return;
        motion.step(SystemClock.uptimeMillis(),pet.x+pinX(),pet.y+pinY(),pet.owner().getResources().getDisplayMetrics().density);
        if(renderingWaist()){
            if(active){float e=entry();waistView.setAlpha(Math.max(.01f,e));pet.video.setAlpha(1-e);}
            waistView.invalidate();return;
        }
        float a=amount();
        pet.video.setPivotX(grabX*pet.width);pet.video.setPivotY(grabY*pet.width*.75f);
        pet.video.setRotation(a*motion.angle*.45f);
        pet.video.setScaleX(1+.025f*a);pet.video.setScaleY(1+.025f*a);
    }
}
