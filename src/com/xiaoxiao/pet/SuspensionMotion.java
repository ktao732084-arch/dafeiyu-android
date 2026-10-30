package com.xiaoxiao.pet;

/** Damped hanging motion driven by movement of the grip, not a perpetual canned sway. */
final class SuspensionMotion {
    static final float LIMIT=14f;
    float angle,angularVelocity,velocityX,velocityY,lastX,lastY;
    long lastAt;
    boolean ready;
    void reset(long now,float x,float y){ready=true;lastAt=now;lastX=x;lastY=y;angle=angularVelocity=velocityX=velocityY=0;}
    void step(long now,float x,float y,float density){
        if(!ready){reset(now,x,y);return;}
        float dt=(now-lastAt)/1000f;if(dt<=0)return;
        if(dt>.25f){lastAt=now;lastX=x;lastY=y;velocityX=velocityY=angularVelocity=0;return;}
        float blend=1-(float)Math.exp(-dt/.07f);
        float vx=velocityX+((x-lastX)/dt-velocityX)*blend,vy=velocityY+((y-lastY)/dt-velocityY)*blend;
        float gravity=900*Math.max(.5f,density),ax=(vx-velocityX)/dt,ay=(vy-velocityY)/dt;
        float target=(float)Math.toDegrees(Math.atan2(ax,Math.max(gravity*.3f,gravity+ay)));
        target=Math.max(-LIMIT,Math.min(LIMIT,target));
        int steps=Math.max(1,(int)Math.ceil(dt/.008f));float h=dt/steps;
        for(int i=0;i<steps;i++){
            angularVelocity+=(70*(target-angle)-14*angularVelocity)*h;
            angle+=angularVelocity*h;
            if(Math.abs(angle)>LIMIT){angle=Math.max(-LIMIT,Math.min(LIMIT,angle));angularVelocity*=.2f;}
        }
        velocityX=vx;velocityY=vy;lastX=x;lastY=y;lastAt=now;
    }
}
