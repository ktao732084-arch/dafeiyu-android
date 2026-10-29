package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.os.*;import android.graphics.Color;import android.graphics.PixelFormat;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;

public class PetService extends Service {
 static PetService instance;
 int screenWidth,screenHeight,safeTop,safeBottom;
 void screenSize(){
  if(Build.VERSION.SDK_INT>=30){android.view.WindowMetrics metrics=wm.getMaximumWindowMetrics();android.graphics.Rect rect=metrics.getBounds();screenWidth=rect.width();screenHeight=rect.height();android.graphics.Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safeTop=insets.top;safeBottom=screenHeight-insets.bottom;}
  else{android.graphics.Point real=new android.graphics.Point();wm.getDefaultDisplay().getRealSize(real);screenWidth=real.x;screenHeight=real.y;safeTop=0;safeBottom=screenHeight;}
 }

 public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);Apple.load();int oldW=screenWidth,oldH=screenHeight;screenSize();if(oldW!=screenWidth||oldH!=screenHeight){for(Pet p:pets.values())p.update();requestMotion();}}

 static final int WINDOW_FLAGS=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
 final ArrayList<Pet> framePets=new ArrayList<>();final ArrayList<MotionEngine.Body> bodies=new ArrayList<>();boolean frameScheduled,savePending;long frameTime,lastSlowFrame;
 boolean inMotion(){for(Pet p:pets.values())if(p.visible&&!p.docked&&!p.docking&&(p.dragging||p.moving))return true;return false;}
 void requestMotion(){if(instance==this&&screenOn&&!MainActivity.active&&!frameScheduled){frameScheduled=true;Choreographer.getInstance().postFrameCallback(motionFrame);}}
 final Choreographer.FrameCallback motionFrame=new Choreographer.FrameCallback(){public void doFrame(long now){frameScheduled=false;if(instance!=PetService.this||!screenOn||MainActivity.active){frameTime=0;return;}if(frameTime!=0&&now-frameTime>50000000L&&SystemClock.uptimeMillis()-lastSlowFrame>5000){lastSlowFrame=SystemClock.uptimeMillis();Data.log("app","运动慢帧 gapMs="+((now-frameTime)/1000000)+" visible="+framePets.size());}float dt=frameTime==0?1f/60:Math.min(.05f,(now-frameTime)/1000000000f);frameTime=now;framePets.clear();bodies.clear();for(Pet p:pets.values())if(p.visible&&!p.docked&&!p.docking&&p.frame!=null){p.geometry();MotionEngine.Body b=p.state;b.held=p.dragging;b.awake=p.moving;b.gravity=p.thrown;b.vx=p.vx;b.vy=p.vy;if(p.dragging){b.targetX=p.x;b.targetY=p.y;}else{b.x=p.x;b.y=p.y;}framePets.add(p);bodies.add(b);}boolean next=MotionEngine.step(bodies,dt,sw(),sh(),Data.prefs().getBoolean("collision",true));for(Pet p:framePets){MotionEngine.Body b=p.state;if(p.moving&&!b.awake){savePending=true;if(ActionPlan.DRAG.equals(p.current))p.play(Data.idle(p.role));}p.x=b.x;p.y=b.y;p.vx=b.vx;p.vy=b.vy;p.moving=b.awake;p.thrown=b.gravity;p.update();if(b.contact&&SystemClock.uptimeMillis()-p.lastHit>1000){p.lastHit=SystemClock.uptimeMillis();Data.log(p.role,"碰撞 "+p.id);}}if(next)requestMotion();else{frameTime=0;savePending=true;if(!inMotion())for(Pet p:pets.values())p.flushBounds();}}};

 final LinkedHashMap<String,Pet> pets=new LinkedHashMap<>();final Handler handler=new Handler(Looper.getMainLooper());WindowManager wm;boolean screenOn=true,keyguard,started;long lastTick;long lastAgentTick,lastGrowthTick;
 // Overlays sit below the lock screen and behind the app's own pages, so only decode video while it can be seen.
 boolean locked(){KeyguardManager k=getSystemService(KeyguardManager.class);return k!=null&&k.isKeyguardLocked();}
 boolean animating(){return screenOn&&!keyguard&&!MainActivity.active;}
 void syncVideo(){boolean pause=!animating();for(Pet p:pets.values())if(p.video!=null)p.video.suspend(pause);}
 // Screen off stops the tick entirely; a user's own chat request keeps running so a paid reply is not thrown away.
 final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_USER_PRESENT.equals(i.getAction())){keyguard=false;syncVideo();requestMotion();Data.log("app","解锁恢复");return;}screenOn=!Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOn)keyguard=locked();if(!screenOn||keyguard)for(Pet p:pets.values())p.clearBubble();syncVideo();handler.removeCallbacks(tick);if(screenOn){handler.post(tick);requestMotion();AgentRuntime.note("手机刚刚亮屏");}else{frameTime=0;AgentRuntime.interruptProactive();}Data.log("app",screenOn?"亮屏恢复":"息屏暂停");}};
 public void onCreate(){super.onCreate();Data.init(this);Apple.load();instance=this;wm=(WindowManager)getSystemService(WINDOW_SERVICE);screenSize();PowerManager power=getSystemService(PowerManager.class);screenOn=power==null||power.isInteractive();keyguard=locked();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("pets","桌宠运行",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PetService.class).setAction("hideAll"),PendingIntent.FLAG_IMMUTABLE);try{startForeground(1,new Notification.Builder(this,"pets").setSmallIcon(statusIcon()).setContentTitle("大肥鱼").setContentText("独立 AI 伙伴 · 点击回到她的小窝").setContentIntent(open).addAction(new Notification.Action.Builder(null,"全部隐藏",stop).build()).setOngoing(true).build());}catch(RuntimeException e){Data.log("app","前台服务启动被系统拒绝");stopSelf();return;}started=true;IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);JSONArray stored=Data.instances();for(int i=0;i<stored.length();i++){JSONObject o=stored.optJSONObject(i);if(o!=null&&Data.roles.containsKey(o.optString("role"))&&!pets.containsKey(o.optString("id"))){Pet p=new Pet(o);pets.put(p.id,p);}}GrowthStore.changed=()->handler.post(this::refreshGrowth);handler.post(tick);Data.log("app","悬浮服务启动");}
 public IBinder onBind(Intent i){return null;}
 /** The launcher's whale (fish_mark) as a status bar silhouette: the system only uses the alpha, so a generic star said nothing. */
 android.graphics.drawable.Icon statusIcon(){
  int size=Apple.dp(this,24);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);
  c.scale(size/72f,size/72f);c.translate(-20,-16);android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);
  android.graphics.Path body=new android.graphics.Path();body.moveTo(24,59);body.cubicTo(24,44,34,35,48,35);body.cubicTo(65,35,74,47,75,60);body.cubicTo(82,58,85,53,85,47);body.cubicTo(92,53,90,65,82,70);body.cubicTo(72,79,59,80,46,77);body.cubicTo(31,74,24,67,24,59);body.close();
  body.moveTo(48,31);body.cubicTo(41,25,44,21,48,26);body.cubicTo(52,18,58,21,53,28);body.lineTo(50,32);body.close();c.drawPath(body,p);
  p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));c.drawCircle(38,54,3.5f,p);
  return android.graphics.drawable.Icon.createWithBitmap(b);
 }
 /** With nobody on screen the service, its notification and its once-a-second tick have nothing to do. */
 void stopIfEmpty(){for(Pet p:pets.values())if(p.visible)return;save();stopSelf();}
 // START_STICKY: when the system kills the process for memory, it restarts the service with a null intent
 // and the pets come back from saved instances, unless overlay permission was revoked or nothing was showing.
 public int onStartCommand(Intent i,int flags,int start){if(!started)return START_NOT_STICKY;if(!android.provider.Settings.canDrawOverlays(this)){Data.log("app","悬浮权限已关闭，停止服务");stopSelf();return START_NOT_STICKY;}boolean shown=false;for(Pet p:pets.values())shown|=p.visible;if(i==null&&!shown){stopSelf();return START_NOT_STICKY;}if(i!=null){String a=i.getAction(),role=i.getStringExtra("role");if("show".equals(a)&&Data.roles.containsKey(role))show(role);if("showAll".equals(a))for(String r:Data.roles.keySet())show(r);if("hideAll".equals(a)){for(Pet p:pets.values())p.hide();save();stopSelf();}if("ensure".equals(a)&&Data.roles.containsKey(role)&&!pets.containsKey(role))pets.put(role,new Pet(role,role));}for(Pet p:pets.values())if(p.visible)p.attach();control(MainActivity.active);return START_STICKY;}
 public void onDestroy(){GrowthStore.changed=null;AgentRuntime.interruptProactive();if(started)save();instance=null;Choreographer.getInstance().removeFrameCallback(motionFrame);handler.removeCallbacksAndMessages(null);if(started)unregisterReceiver(screen);for(Pet p:pets.values())p.detach();Data.log("app","悬浮服务停止");super.onDestroy();}
 void control(boolean active){if(active){frameTime=0;for(Pet p:pets.values())p.clearBubble();}else requestMotion();syncVideo();for(Pet p:pets.values())if(p.frame!=null){p.lp.alpha=active?0:1;p.lp.flags=WINDOW_FLAGS|(p.docked?WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:0)|(active?WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:0);wm.updateViewLayout(p.frame,p.lp);if(p.bubbleDialog!=null){if(active)p.bubbleDialog.hide();else p.bubbleDialog.show();}}}
 int sw(){return screenWidth;}int sh(){return screenHeight;}
 Pet show(String role){Pet p=pets.get(role);if(p==null){p=new Pet(role,role);pets.put(role,p);}p.visible=true;if(p.docked){p.docked=false;p.detach();}p.attach();save();control(MainActivity.active);p.userInteraction("show","用户让你出现在桌面上",false);final Pet shown=p;if(p.away())handler.postDelayed(()->finishTrip(shown),450);return p;}
 void cloneRole(String role){int total=0;for(Pet p:pets.values())if(p.role.equals(role)&&!p.id.equals(role))total++;if(total>=8){Toast.makeText(this,"每个角色最多8个分身",0).show();return;}int n=1;while(pets.containsKey(role+"#"+n))n++;Pet p=new Pet(role+"#"+n,role);pets.put(p.id,p);p.visible=true;p.attach();save();control(MainActivity.active);Data.log(role,"添加分身 "+p.id);}
 void remove(String id){Pet p=pets.get(id);if(p==null)return;p.hide();if(!p.id.equals(p.role))pets.remove(id);save();stopIfEmpty();}
 void save(){JSONArray a=new JSONArray();for(Pet p:pets.values())a.put(p.json());Data.instances(a);}
 /** A chat message reaches her too: a dozing pet wakes up, and the model hears about it. Main thread. */
 void heard(String id){Pet p=pets.get(id);if(p==null||!p.visible||p.frame==null)return;if(p.plan.wake(System.currentTimeMillis(),p.current)){p.play(ActionPlan.WOKEN);AgentRuntime.touch("你在睡觉时被用户的消息叫醒了，还有点迷糊");}}
 void talking(String id){Pet p=pets.get(id);if(p!=null)p.play(Data.talk(p.role));}
 void reply(String id,String text){Pet p=pets.get(id);if(p!=null&&p.visible)p.reply(text);}
 // Once a second while the screen is on (idle actions are 20 s apart, the agent heartbeat 30 s); never while it is off.
 final Runnable tick=new Runnable(){public void run(){if(instance!=PetService.this||!screenOn)return;long now=SystemClock.uptimeMillis();if(now-lastAgentTick>30000){lastAgentTick=now;AgentRuntime.heartbeat(PetService.this);UpdateRuntime.tick(PetService.this);}if(now-lastGrowthTick>5000){lastGrowthTick=now;growthTick();}if(animating()){for(Pet p:pets.values())if(p.visible&&p.frame!=null&&p.rotation&&!p.busy&&p.scene==null&&!AgentRuntime.busy()&&!inMotion()&&now>p.nextAction&&p.video!=null){p.play(p.plan.idle(moment(),Data.random));p.nextAction=now+60000;}}if(!inMotion())for(Pet p:pets.values())p.flushBounds();if(savePending&&!inMotion()){savePending=false;save();}handler.postDelayed(this,1000);}};
 static ActionPlan.Moment moment(){return ActionPlan.Moment.of(java.time.LocalDateTime.now());}
 class Pet {
  final MotionEngine.Body state=new MotionEngine.Body();int geometryWidth;float normL=.33f,normT=.22f,normR=.67f,normB=.79f;
  void geometry(){if(geometryWidth==width)return;geometryWidth=width;state.left=normL*width;state.right=normR*width;state.top=normT*width*.75f;state.bottom=normB*width*.75f;state.mass=width*width;}
  float cropL=0,cropT=0,cropR=1,cropB=1;
  String deferredBounds;
  void flushBounds(){if(deferredBounds!=null){String action=deferredBounds;deferredBounds=null;bounds(action);}}
  void bounds(String action){if(inMotion()){deferredBounds=action;return;}applyBounds(action);}
  void applyBounds(String action){deferredBounds=null;JSONObject all=Data.role(role).optJSONObject("bounds");JSONArray a=all==null?null:all.optJSONArray(action);if(a==null)return;cropL=(float)a.optDouble(0);cropT=(float)a.optDouble(1);cropR=(float)a.optDouble(2,1);cropB=(float)a.optDouble(3,1);update();}

  final String id,role;final ActionPlan plan;String current="";int width,previewWidth=-1;float previewX,previewY;float x,y,vx,vy;boolean docked,dockRight,dockExpanded,docking;android.animation.ValueAnimator dockTransition;TextView dockTab;Runnable edgeDock,dockCollapse;android.animation.ValueAnimator dockAnimator;float dockY;boolean visible,rotation=true,busy,dragging,moving,thrown;long nextAction,lastHit;FrameLayout frame;AlphaVideo video;WindowManager.LayoutParams lp;TextView bubble;Dialog bubbleDialog;int bubbleHeight;WindowManager.LayoutParams blp;Runnable bubbleEnd,hold,thinkingTick;boolean moved,longPressed;float downX,downY,ox,oy,lastX,lastY;long lastMove;
  Pet(String id,String role){this.id=id;this.role=role;plan=new ActionPlan(GrowthStore.unlockedOrAll(Data.actions(role)));current=Data.idle(role);width=Math.min((int)(220*getResources().getDisplayMetrics().density),sw()*2/3);int n=pets.size();x=sw()*(1+2*(n%2))/4f-width/2f;y=150+(n/2)*(width*.58f);nextAction=SystemClock.uptimeMillis()+15000;JSONObject all=Data.role(role).optJSONObject("bounds");JSONArray bounds=all==null?null:all.optJSONArray(Data.idle(role));if(bounds!=null){normL=(float)bounds.optDouble(0);normT=(float)bounds.optDouble(1);normR=(float)bounds.optDouble(2,1);normB=(float)bounds.optDouble(3,1);}geometry();state.x=x;state.y=y;}
  Pet(JSONObject o){this(o.optString("id"),o.optString("role"));width=Math.max(160,Math.min(sw(),o.optInt("width",width)));x=(float)o.optDouble("x",x);y=(float)o.optDouble("y",y);visible=o.optBoolean("visible");docked=o.optBoolean("docked");dockRight=o.optBoolean("dockRight");dockY=(float)o.optDouble("dockY",.5);rotation=o.optBoolean("rotation",true);geometry();state.x=x;state.y=y;}
  JSONObject json(){JSONObject o=new JSONObject();try{o.put("id",id).put("role",role).put("width",previewWidth<0?width:previewWidth).put("x",previewWidth<0?x:previewX).put("y",previewWidth<0?y:previewY).put("visible",visible).put("docked",docked).put("dockRight",dockRight).put("dockY",dockY).put("rotation",rotation);}catch(Exception e){throw new IllegalStateException(e);}return o;}
  void attach(){if(frame!=null||!visible||!android.provider.Settings.canDrawOverlays(PetService.this))return;if(docked){attachDock();return;}lp=new WindowManager.LayoutParams(width,width*3/4,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WINDOW_FLAGS,PixelFormat.TRANSLUCENT);if(Build.VERSION.SDK_INT>=30)lp.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=34)lp.setCanPlayMoveAnimation(false);lp.setTitle("大肥鱼/"+id);lp.gravity=Gravity.TOP|Gravity.LEFT;frame=new FrameLayout(PetService.this);video=new AlphaVideo(PetService.this,role);video.actionListener=name->bounds(name);video.completed=()->{busy=false;current=Data.idle(role);nextAction=SystemClock.uptimeMillis()+ActionPlan.gapMillis(moment(),Data.random);JSONObject all=Data.role(role).optJSONObject("bounds");JSONArray bounds=all==null?null:all.optJSONArray(Data.idle(role));if(bounds!=null){normL=(float)bounds.optDouble(0);normT=(float)bounds.optDouble(1);normR=(float)bounds.optDouble(2,1);normB=(float)bounds.optDouble(3,1);}geometry();state.x=x;state.y=y;if(dragging&&moved&&plan.has(ActionPlan.DRAG))play(ActionPlan.DRAG);else if(rotation&&!dragging&&!moving&&plan.asleep(moment()))play(plan.idle(moment(),Data.random));};current=Data.idle(role);frame.addView(video,new FrameLayout.LayoutParams(width,width*3/4));frame.setOnTouchListener((v,e)->touch(e));wm.addView(frame,lp);bounds(Data.idle(role));state.x=x;state.y=y;requestMotion();Data.log(role,"显示 "+id);}
  void update(){if(frame==null||docking)return;if(docked){positionDock();return;}if(video!=null){android.view.ViewGroup.LayoutParams videoSize=video.getLayoutParams();if(videoSize.width!=width||videoSize.height!=width*3/4){videoSize.width=width;videoSize.height=width*3/4;video.setLayoutParams(videoSize);}video.setTranslationX(-Math.round(width*cropL));video.setTranslationY(-Math.round(width*.75f*cropT));}geometry();x=Math.max(-state.left,Math.min(sw()-state.right,x));y=Math.max(-state.top,Math.min(sh()-state.bottom,y));int px=Math.round(x)+Math.round(width*cropL),py=Math.round(y)+Math.round(width*.75f*cropT),w=Math.max(1,Math.round(width*cropR)-Math.round(width*cropL)),h=Math.max(1,Math.round(width*.75f*cropB)-Math.round(width*.75f*cropT));if(lp.x!=px||lp.y!=py||lp.width!=w||lp.height!=h){lp.setTitle("大肥鱼/"+id);lp.x=px;lp.y=py;lp.width=w;lp.height=h;wm.updateViewLayout(frame,lp);}if(bubble!=null){int edge=Apple.dp(PetService.this,12),gap=Apple.dp(PetService.this,8);int bx=Math.max(edge,Math.min(sw()-blp.width-edge,(int)x+width/2-blp.width/2));int by=Math.round(y+width*.75f*cropT)-bubbleHeight-gap;if(by<safeTop+edge)by=Math.round(y+width*.75f*cropB)+gap;by=Math.max(safeTop+edge,Math.min(safeBottom-bubbleHeight-edge,by));if(blp.x!=bx||blp.y!=by){blp.x=bx;blp.y=by;bubbleDialog.getWindow().setAttributes(blp);}}}
  boolean touch(MotionEvent e){if(docking)return true;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=lastX=e.getRawX();downY=lastY=e.getRawY();ox=x;oy=y;lastMove=SystemClock.uptimeMillis();moved=false;longPressed=false;dragging=true;moving=false;thrown=false;vx=vy=0;state.x=x;state.y=y;requestMotion();hold=()->{longPressed=true;userInteraction("menu","用户长按你打开了菜单",false);menu();};handler.postDelayed(hold,400);return true;case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-downX,dy=e.getRawY()-downY;if(!moved&&Math.abs(dx)+Math.abs(dy)>14){moved=true;handler.removeCallbacks(hold);if(plan.wake(System.currentTimeMillis(),current))AgentRuntime.touch("你在睡觉时被用户拎了起来，还有点迷糊");if(video!=null&&plan.has(ActionPlan.DRAG)){play(ActionPlan.DRAG);applyBounds(ActionPlan.DRAG);}}if(moved){long now=SystemClock.uptimeMillis();float dt=Math.max(.008f,(now-lastMove)/1000f);vx=.65f*vx+.35f*Math.max(-4000,Math.min(4000,(e.getRawX()-lastX)/dt));vy=.65f*vy+.35f*Math.max(-4000,Math.min(4000,(e.getRawY()-lastY)/dt));lastX=e.getRawX();lastY=e.getRawY();lastMove=now;x=Math.max(-state.left,Math.min(sw()-state.right,ox+dx));y=Math.max(-state.top,Math.min(sh()-state.bottom,oy+dy));requestMotion();trackEdge(e);}return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(hold);if(edgeDock!=null){handler.removeCallbacks(edgeDock);edgeDock=null;}dragging=false;if(moved&&e.getActionMasked()==MotionEvent.ACTION_UP){float edge=Apple.dp(PetService.this,24),travel=Apple.dp(PetService.this,48);float outward=e.getRawX()-downX;boolean left=outward < -travel&&e.getRawX()<edge&&x+state.left<=edge;boolean right=outward>travel&&e.getRawX()>sw()-edge&&x+state.right>=sw()-edge;if(left||right){userDockAtEdge(right,(y+width*.375f)/sh(),ox,oy);return true;}}if(moved){if(SystemClock.uptimeMillis()-lastMove>100)vx=vy=0;thrown=e.getActionMasked()==MotionEvent.ACTION_UP&&Data.prefs().getBoolean("physics",true)&&Math.hypot(vx,vy)>Math.max(900,400*getResources().getDisplayMetrics().density);moving=thrown;if(!thrown){vx=vy=0;if(ActionPlan.DRAG.equals(current))play(Data.idle(role));}state.x=x;state.y=y;requestMotion();save();userInteraction(e.getActionMasked()==MotionEvent.ACTION_CANCEL?"cancel":thrown?"throw":"drag",e.getActionMasked()==MotionEvent.ACTION_CANCEL?"用户的拖动被中断":thrown?"用户拖动后把你甩了起来":"用户把你挪到新位置",e.getActionMasked()==MotionEvent.ACTION_UP);Data.log(role,(thrown?"投掷 ":"放置 ")+id+" x="+Math.round(x)+" y="+Math.round(y));}else if(!longPressed&&e.getActionMasked()==MotionEvent.ACTION_UP){if(scene!=null){userInteraction("scene","用户轻点你继续看章节剧情",false);nextLine();return true;}String before=current,reaction=plan.tap(System.currentTimeMillis(),before,Data.random);userInteraction("tap",ActionPlan.tapNote(before,reaction),true);play(reaction);grow(reaction);}return true;}return true;}
  void play(String action){if(video==null)return;current=action;GrowthStore growth=GrowthStore.quiet();if(growth!=null)growth.seen(action);busy=!Data.idle(role).equals(action);bounds(action);video.play(action);Data.log(role,"动作 "+id+" "+action);}
  void previewSize(int value){width=Math.max(160,Math.min(sw(),value));update();requestMotion();}
  void resize(int value){previewSize(value);save();userInteraction("resize","用户调整了你的大小",false);Data.log(role,"大小 "+id+" "+width);}
  void trackEdge(MotionEvent e){float edge=Apple.dp(PetService.this,24),travel=Apple.dp(PetService.this,48),delta=e.getRawX()-downX;boolean left=delta < -travel&&e.getRawX()<edge&&x+state.left<=edge;boolean right=delta>travel&&e.getRawX()>sw()-edge&&x+state.right>=sw()-edge;if(!left&&!right){if(edgeDock!=null){handler.removeCallbacks(edgeDock);edgeDock=null;}return;}if(edgeDock==null){final boolean side=right;edgeDock=()->{edgeDock=null;if(dragging&&!docked)userDockAtEdge(side,(y+width*.375f)/sh(),ox,oy);};handler.postDelayed(edgeDock,180);}}
  void dockAtEdge(boolean right,float centerY,float restoreX,float restoreY){
   if(docked||docking||!visible)return;dockRight=right;dockY=Math.max(0,Math.min(1,centerY));dragging=moving=thrown=false;vx=vy=0;
   if(edgeDock!=null){handler.removeCallbacks(edgeDock);edgeDock=null;}if(hold!=null)handler.removeCallbacks(hold);clearBubble();Chat.closePet(id);SizePanel.close(id);
   x=restoreX;y=restoreY;state.x=x;state.y=y;
   Runnable finish=()->{docking=false;detach();docked=true;attach();save();control(MainActivity.active);if(frame!=null)UiMotion.enter(frame);Data.log(role,"拖动收起到屏幕边缘 "+id);
    // Dragging to the edge is easy to do by accident; the first time, say how she comes back.
    if(!away()&&!Data.prefs().getBoolean("hint.dock",false)){Data.prefs().edit().putBoolean("hint.dock",true).apply();Toast.makeText(PetService.this,Data.name(role)+"躲到屏幕边上啦：点一下露出来，再点一下她就回来",Toast.LENGTH_LONG).show();}};
   if(!UiMotion.enabled()||frame==null){finish.run();return;}
   docking=true;if(video!=null)video.suspend(true);final FrameLayout surface=frame;final int from=lp.x,to=right?sw()-lp.width+Apple.dp(PetService.this,8):-Apple.dp(PetService.this,8);surface.setPivotX(right?surface.getWidth():0);surface.setPivotY(surface.getHeight()/2f);
   dockTransition=android.animation.ValueAnimator.ofFloat(0,1);dockTransition.setDuration(460);dockTransition.setInterpolator(new android.view.animation.PathInterpolator(.4f,0f,.2f,1f));
   dockTransition.addUpdateListener(a->{if(!docking||frame!=surface)return;float t=(Float)a.getAnimatedValue();lp.x=Math.round(from+(to-from)*t);lp.alpha=MainActivity.active?0:1-t;surface.setScaleX(1-.78f*t);surface.setScaleY(1-.78f*t);wm.updateViewLayout(surface,lp);});
   dockTransition.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){if(dockTransition!=a||!docking)return;dockTransition=null;finish.run();}});dockTransition.start();
  }
  void restoreDock(){if(!docked)return;detach();docked=false;attach();save();control(MainActivity.active);if(frame!=null)UiMotion.enter(frame);Data.log(role,"恢复边缘角色 "+id);if(away())handler.postDelayed(()->finishTrip(this),450);}
  void positionDock(){int h=Apple.dp(PetService.this,64);lp.x=dockRight?sw()-lp.width:0;lp.y=Math.max(safeTop,Math.min(safeBottom-h,Math.round(dockY*sh()-h/2f)));wm.updateViewLayout(frame,lp);}
  void expandDock(boolean expand){if(!docked||frame==null||dockTab==null)return;dockExpanded=expand;if(dockCollapse!=null)handler.removeCallbacks(dockCollapse);if(dockAnimator!=null)dockAnimator.cancel();dockTab.setText(dockLabel(expand));dockTab.setContentDescription(Data.name(role)+(away()?"出门了，"+(expand?"再次点击叫她回来":"点击展开"):expand?"，再次点击恢复":"，已收起，点击展开"));int target=Apple.dp(PetService.this,expand?148:28);if(!UiMotion.enabled()){lp.width=target;positionDock();}else{dockAnimator=android.animation.ValueAnimator.ofInt(lp.width,target);dockAnimator.setDuration(360);dockAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));dockAnimator.addUpdateListener(a->{if(docked&&frame!=null){lp.width=Math.max(Apple.dp(PetService.this,24),(Integer)a.getAnimatedValue());positionDock();}});dockAnimator.start();}if(expand){dockCollapse=()->expandDock(false);handler.postDelayed(dockCollapse,4000);}}
  void attachDock(){dockExpanded=false;int w=Apple.dp(PetService.this,28),h=Apple.dp(PetService.this,64);lp=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WINDOW_FLAGS|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);if(Build.VERSION.SDK_INT>=30)lp.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=34)lp.setCanPlayMoveAnimation(false);lp.gravity=Gravity.TOP|Gravity.LEFT;lp.setTitle("大肥鱼-边缘/"+id);frame=new FrameLayout(PetService.this){public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){if(dockExpanded)expandDock(false);return false;}return super.dispatchTouchEvent(event);}};dockTab=Apple.label(PetService.this,dockLabel(false),12,true);dockTab.setGravity(Gravity.CENTER);dockTab.setTextColor(Apple.INK);dockTab.setBackground(Apple.shape(PetService.this,Apple.PANEL,12));dockTab.setContentDescription(Data.name(role)+"，已收起，点击展开");frame.addView(dockTab,new FrameLayout.LayoutParams(-1,-1));dockTab.setOnClickListener(v->{if(dockExpanded){userInteraction("restore","用户把你从屏幕边缘叫了回来",false);restoreDock();}else{userInteraction("menu","用户点开了你的屏幕边缘标签",false);expandDock(true);}});wm.addView(frame,lp);positionDock();}
  void hide(){if(visible)userInteraction("hide","用户暂时把你收了起来",false);visible=false;detach();Data.log(role,"隐藏 "+id);}
  void detach(){docking=false;if(dockTransition!=null){android.animation.ValueAnimator transition=dockTransition;dockTransition=null;transition.cancel();}if(frame!=null)frame.animate().cancel();if(edgeDock!=null){handler.removeCallbacks(edgeDock);edgeDock=null;}if(dockCollapse!=null)handler.removeCallbacks(dockCollapse);if(dockAnimator!=null){dockAnimator.cancel();dockAnimator=null;}dockExpanded=false;dockTab=null;if(hold!=null)handler.removeCallbacks(hold);if(video!=null){video.release();video=null;}if(frame!=null){wm.removeView(frame);frame=null;}clearBubble();Chat.closePet(id);SizePanel.close(id);busy=false;}
  final TouchReply.Batch touchReplies=new TouchReply.Batch();Runnable touchReplyCheck;
  PetService owner(){return PetService.this;}
  void userInteraction(String kind,String note,boolean reply){TouchReply.event(PetService.this,this,kind,note,reply);}
  void userDockAtEdge(boolean right,float center,float restoreX,float restoreY){if(docked||docking)return;userInteraction("dock","用户把你拖到屏幕边缘收了起来",false);dockAtEdge(right,center,restoreX,restoreY);}
  SpeechQueue speech;String speechTurn;
  void clearBubble(){TouchReply.cancel(this);if(speech!=null)speech.cancel();endScene();clearBubbleVisual();}
  void clearBubbleVisual(){if(thinkingTick!=null){handler.removeCallbacks(thinkingTick);thinkingTick=null;}if(bubbleEnd!=null)handler.removeCallbacks(bubbleEnd);if(bubble!=null){if(bubbleDialog!=null){bubbleDialog.dismiss();bubbleDialog=null;}bubble=null;bubbleHeight=0;}}
  void thinking(){reply("正在回复…");if(!UiMotion.enabled())return;thinkingTick=new Runnable(){int count;public void run(){if(bubble==null)return;count=count%3+1;bubble.setText("正在回复"+(count==1?"·":count==2?"··":"···"));handler.postDelayed(this,450);}};handler.post(thinkingTick);}
  void streamReply(String text){if(thinkingTick!=null){handler.removeCallbacks(thinkingTick);thinkingTick=null;}if(bubble==null){reply(text);return;}bubble.setText(AgentPolicy.clip(Chat.displayText(text),250));}
  void speak(String text,String turnId){
   clearBubble();speechTurn=turnId;
   if(speech==null)speech=new SpeechQueue(new SpeechQueue.Clock(){public void later(Runnable task,long delay){handler.postDelayed(task,delay);}public void remove(Runnable task){handler.removeCallbacks(task);}},new SpeechQueue.Sink(){
    public boolean visible(){return Pet.this.visible&&!docked&&frame!=null&&animating();}
    public void show(String text,int shown,boolean preview){showBubble(!preview&&speech!=null&&speech.parts!=null&&shown>=speech.parts.size()?text+"\n\n点此查看聊天记录":text,false);try{AgentRuntime.store().shown(speechTurn,shown);Chat.historyChanged(Pet.this.id);}catch(Exception e){Data.log(role,"气泡展示状态暂未保存");}}
    public void stop(int shown){clearBubbleVisual();}
   });speech.start(text);
  }
  void reply(String text){clearBubble();showBubble(text,true);}
  void showBubble(String text,boolean autoHide){
   clearBubbleVisual();String visibleText=Chat.displayText(text);bubble=Apple.label(PetService.this,"",15,false);bubble.setLineSpacing(0f,1f);bubble.setText(visibleText);bubble.setOnClickListener(v->Chat.openHistory(PetService.this));
   bubble.setPadding(Apple.dp(PetService.this,12),Apple.dp(PetService.this,8),Apple.dp(PetService.this,12),Apple.dp(PetService.this,8));
   int bubbleWidth=Math.min(sw()-Apple.dp(PetService.this,24),Apple.dp(PetService.this,260));bubble.measure(View.MeasureSpec.makeMeasureSpec(bubbleWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));bubbleHeight=bubble.getMeasuredHeight();
   bubbleDialog=CompactPanel.create(PetService.this);bubbleDialog.setContentView(bubble);Window bw=bubbleDialog.getWindow();bw.setWindowAnimations(0);bw.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);bw.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);bw.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);bubbleDialog.show();bw.getDecorView().setPadding(0,0,0,0);FrostedPanel.configure(bubbleDialog,16,false);blp=bw.getAttributes();blp.width=bubbleWidth;blp.height=-2;
   if(Build.VERSION.SDK_INT>=30)blp.setFitInsetsTypes(0);if(Build.VERSION.SDK_INT>=34)blp.setCanPlayMoveAnimation(false);blp.setTitle("大肥鱼-气泡/"+id);blp.gravity=Gravity.TOP|Gravity.LEFT;
   bubble.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=bubbleHeight){bubbleHeight=b-t;update();}});
   bubbleDialog.getWindow().setAttributes(blp);update();if(UiMotion.enabled()){bubble.setAlpha(.4f);bubble.animate().alpha(1).setDuration(160).start();}if(MainActivity.active)bubbleDialog.hide();bubbleEnd=()->clearBubbleVisual();if(autoHide&&!Chat.isPetChatOpen(id))handler.postDelayed(bubbleEnd,10000);
  }

  void menu(){dragging=false;if(frame==null)return;PetMenu.show(PetService.this,this);}

  // ---- growth: chapter scenes, postcards and the trip tab (GrowthStore holds the state) ----
  Growth.Scene scene;int sceneStep;boolean sceneSaved;Runnable sceneNext;
  boolean away(){if(!id.equals("main"))return false;GrowthStore g=GrowthStore.quiet();return g!=null&&g.trip()!=null;}
  String dockLabel(boolean expand){
   String name=Data.name(role);
   if(away()){JSONObject t=GrowthStore.quiet().trip();String back=t==null?"":new java.text.SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(t.optLong("end")));return expand?"约"+back+"回来\n再点叫她回来":"出门";}
   return expand?name+"\n再次点击恢复":name.substring(0,Math.min(2,name.length()));
  }
  /** Daily first meeting (with a greeting) and the tap reward; poking her into a sulk earns nothing. */
  void grow(String reaction){
   GrowthStore g=GrowthStore.quiet();if(g==null||!id.equals("main"))return;
   try{Growth.Award met=g.met();if(!ActionPlan.ANNOYED.equals(reaction))g.touched();
    if(met.first&&PersonaManager.isInitialized("main")&&scene==null)showBubble(PersonaManager.voice("main","greeting_"+Growth.slot(java.time.LocalTime.now().getHour()),Growth.greeting(GrowthStore.story(),java.time.LocalTime.now().getHour(),Data.random,you())).replace("{you}",you())+(met.fish>0?"（小鱼干 +"+met.fish+"）":""),true);}
   catch(Exception e){Data.log(role,"成长记录暂未保存");}
  }
  /** Plays a chapter scene line by line; a tap or a tap on the bubble moves on. Marked as seen at its last line. */
  void playScene(Growth.Scene s){endScene();clearBubble();scene=s;sceneStep=-1;sceneSaved=false;Data.log(role,"剧情 "+s.title);nextLine();}
  void nextLine(){
   if(scene==null)return;if(sceneNext!=null)handler.removeCallbacks(sceneNext);sceneStep++;
   if(sceneStep>=scene.steps.size()){endScene();clearBubbleVisual();return;}
   String[] step=scene.steps.get(sceneStep);if(!step[0].isEmpty()&&!Data.file(role,step[0]).isEmpty())play(step[0]);
   showBubble(step[1],false);if(bubble!=null)bubble.setOnClickListener(v->nextLine());
   if(sceneStep==scene.steps.size()-1&&!sceneSaved){sceneSaved=true;final Growth.Scene done=scene;GrowthStore g=GrowthStore.quiet();
    if(g!=null)try{g.shown(done);AgentRuntime.touch("你和用户一起经历了"+done.title);}catch(Exception e){Data.log(role,"剧情记录暂未保存");}}
   sceneNext=this::nextLine;handler.postDelayed(sceneNext,Math.max(3500,Math.min(10000,2200+step[1].length()*180)));
  }
  void endScene(){if(sceneNext!=null){handler.removeCallbacks(sceneNext);sceneNext=null;}scene=null;}
  void showPostcard(JSONObject card,String lead){
   showBubble(lead+"\n\n"+Growth.postcardText(card),false);if(bubble!=null)bubble.setOnClickListener(v->clearBubble());
   bubbleEnd=()->clearBubble();handler.postDelayed(bubbleEnd,15000);
   GrowthStore g=GrowthStore.quiet();if(g!=null)try{g.read(card.optString("id"));AgentRuntime.touch("在应用内的虚拟旅行中，你"+("away".equals(card.optString("kind"))?"在用户不在时":"")+"去了「"+card.optString("place")+"」，带回了一张明信片和"+card.optString("item"));}catch(Exception e){Data.log(role,"明信片记录暂未保存");}
  }
 }

 static String you(){try{String n=Nickname.current();return n==null||n.trim().isEmpty()?"你":n.trim();}catch(Exception e){return "你";}}
 /** Unlocks after a chapter change. */
 void refreshGrowth(){for(Pet p:pets.values())p.plan.available(GrowthStore.unlockedOrAll(Data.actions(p.role)));}
 /** Every five seconds with the screen on: trips that are due, then a waiting chapter scene or postcard. */
 void growthTick(){
  GrowthStore g=GrowthStore.quiet();Pet p=pets.get("main");if(g==null)return;
  try{
   if(p!=null){p.plan.hungry=g.hungry();p.plan.fedAt=g.lastFed()>0?g.lastFed():-1;}
   if(g.tripDue()){if(p!=null&&p.docked&&p.visible&&!p.docking)p.restoreDock();else if(p==null||!p.docking)finishTrip(p);return;}
   if(p==null||!quietMoment(p))return;
   Growth.Scene scene=g.nextScene();if(scene!=null){p.playScene(scene);return;}
   JSONObject card=g.unread();if(card!=null&&g.trip()==null)p.showPostcard(card,tripLine("away".equals(card.optString("kind"))?"away":"back"));
  }catch(Exception e){Data.log("main","成长剧情暂不可用");}
 }
 /** Nothing else is going on: visible, awake, no chat, no speech, no drag, Do Not Disturb off, daytime. */
 boolean quietMoment(Pet p){
  if(!p.visible||p.frame==null||p.docked||p.docking||p.dragging||p.moving||p.thrown||p.busy||!animating()||inMotion()||p.bubble!=null||p.scene!=null||p.speech!=null&&p.speech.active)return false;
  if(AgentRuntime.busy()||Chat.isPetChatOpen(p.id)||moment().night()||!PersonaManager.isInitialized("main"))return false;
  NotificationManager nm=getSystemService(NotificationManager.class);return nm==null||nm.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL;
 }
 /** Feeding from the menu: pays in fish, plays the eating clip, and she hears about it. */
 String tripLine(String event){return PersonaManager.voice("main","trip_"+event,Growth.pick(GrowthStore.story().trip.optJSONArray(event),Data.random,you())).replace("{you}",you());}
 void feed(Pet p,Growth.Food food){
  GrowthStore g=GrowthStore.quiet();if(g==null)return;
  try{Growth.Award a=new Growth.Award();String error=g.feed(food,a);if(error!=null){Toast.makeText(this,error,Toast.LENGTH_SHORT).show();return;}g.met();
   if(!Data.file(p.role,food.clip).isEmpty())p.play(food.clip);p.plan.hungry=false;p.plan.fedAt=System.currentTimeMillis();p.userInteraction("feed","用户喂你吃了"+food.name,true);if(p.visible&&p.bubble==null&&p.scene==null&&(p.speech==null||!p.speech.active)&&!AgentRuntime.busy()&&!Chat.isPetChatOpen(p.id))p.showBubble(PersonaManager.voice("main","feed","谢谢这份{food}。").replace("{food}",food.name),true);
  }catch(Exception e){Toast.makeText(this,"喂食暂未保存，请重试",Toast.LENGTH_SHORT).show();}
 }
 /** Sets off on a trip: a departure line, then she tucks herself into the nearer screen edge until she is back. */
 void goOut(Pet p,boolean snack){
  GrowthStore g=GrowthStore.quiet();if(g==null)return;
  try{String error=g.startTrip(snack);if(error!=null){Toast.makeText(this,"locked".equals(error)?GrowthStore.story().trip.optString("locked"):error,Toast.LENGTH_SHORT).show();return;}
   JSONObject t=g.trip();String back=new java.text.SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(t.optLong("end")));
   AgentRuntime.touch("用户送你参加应用内的虚拟旅行，约"+back+"回来");if(!Data.file(p.role,"原地左转奔跑").isEmpty())p.play("原地左转奔跑");
   p.showBubble(tripLine("depart").replace("{time}",back),false);
   handler.postDelayed(()->{if(p.visible&&!p.docked&&p.frame!=null&&p.away()){p.clearBubble();p.dockAtEdge(p.x+p.width/2f>sw()/2f,(p.y+p.width*.375f)/sh(),p.x,p.y);}},2800);
  }catch(Exception e){Toast.makeText(this,"出门暂未成功，请重试",Toast.LENGTH_SHORT).show();}
 }
 /** Ends the trip (on time, or early when you call her back) and shows what she brought. */
 void finishTrip(Pet p){
  GrowthStore g=GrowthStore.quiet();if(g==null)return;
  try{Growth.Award a=new Growth.Award();String result=g.endTrip(a);if(result==null)return;Data.log("main","旅行结束 "+result);
   // Hidden, still tucked away, or a chat is starting: the postcard waits, unread, for the next quiet moment.
   if(p==null||!p.visible||p.frame==null||p.docked||AgentRuntime.busy()||Chat.isPetChatOpen(p.id))return;
   if(!Data.file(p.role,"原地漂浮踏步").isEmpty())p.play("原地漂浮踏步");
   String lead=tripLine(result);
   if(a.postcard!=null)p.showPostcard(a.postcard,lead);else p.showBubble(lead,true);
  }catch(Exception e){Data.log("main","旅行记录暂未保存");}
 }
 /** A chat message calls her back from a trip (chat comes first). Main thread. */
 void callBack(){Pet p=pets.get("main");if(p==null||!p.away())return;if(p.docked&&p.visible&&p.frame!=null)p.restoreDock();else finishTrip(p);}
}
