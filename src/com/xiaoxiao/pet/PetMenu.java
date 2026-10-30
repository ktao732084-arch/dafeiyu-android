package com.xiaoxiao.pet;

import android.app.Dialog;import android.content.Context;import android.graphics.*;import android.graphics.drawable.Drawable;import android.os.Build;import android.view.*;import android.widget.*;

/** Four nearby commands: chat, feed, actions and more. The detailed action catalog is a separate scrollable surface. */
final class PetMenu {
 static void close(PetService.Pet pet){if(pet.menuDialog!=null){pet.menuDialog.dismiss();pet.menuDialog=null;}}
 static void show(Context c,PetService.Pet pet){
  if(pet.frame==null||PetService.instance==null)return;
  if(pet.menuDialog!=null&&pet.menuDialog.isShowing())return;
  pet.frame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
  Dialog d=CompactPanel.create(c);pet.menuDialog=d;
  d.setOnDismissListener(dialog->{if(pet.menuDialog==d)pet.menuDialog=null;});
  LinearLayout content=new LinearLayout(c);content.setOrientation(1);int pad=Apple.dp(c,10);content.setPadding(pad,pad,pad,pad);
  LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=Apple.label(c,Data.name(pet.role),14,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(pad,0,0,0);
  header.addView(title,new LinearLayout.LayoutParams(0,Apple.dp(c,44),1));title.setGravity(Gravity.CENTER_VERTICAL);
  Button close=new Button(c);close.setText("×");close.setTextSize(24);close.setContentDescription("关闭快捷菜单");close.setStateListAnimator(null);close.setElevation(0);close.setMinWidth(0);close.setMinimumWidth(0);close.setPadding(0,0,0,0);close.setBackground(Apple.shape(c,Apple.FILL,22));close.setTextColor(Apple.SECONDARY);UiMotion.press(close);close.setOnClickListener(v->d.dismiss());header.addView(close,new LinearLayout.LayoutParams(Apple.dp(c,44),Apple.dp(c,44)));content.addView(header);
  String[] names={"聊天","喂食","动作","更多"};
  for(int row=0;row<2;row++){
   LinearLayout line=new LinearLayout(c);
   for(int col=0;col<2;col++){
    final int index=row*2+col;
    Button button=new Button(c);button.setText(names[index]);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(index==0?Apple.BLUE:Apple.INK);button.setGravity(Gravity.CENTER);button.setStateListAnimator(null);button.setElevation(0);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(pad,pad,pad,pad);button.setMinHeight(Apple.dp(c,76));
    button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226d9edf),Apple.shape(c,index==0?Apple.TINT:Apple.FILL,16),null));
    button.setCompoundDrawablePadding(Apple.dp(c,6));button.setCompoundDrawablesWithIntrinsicBounds(null,new MenuIcon(c,index),null,null);UiMotion.press(button);
    button.setOnClickListener(v->{d.dismiss();if(index==0)Chat.show(c,pet.role,pet.id);if(index==1)GrowthSheets.feed(c,pet);if(index==2)new ActionSheet(c,pet,false,null).show();if(index==3)GrowthSheets.more(c,pet);});
    LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.topMargin=Apple.dp(c,8);if(col==0)cell.rightMargin=Apple.dp(c,8);line.addView(button,cell);
   }
   content.addView(line);
  }
  ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);
  d.setContentView(scroll);d.setCanceledOnTouchOutside(true);Window w=d.getWindow();w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
  int sw=PetService.instance.sw(),sh=PetService.instance.sh();Rect safe=new Rect(0,0,sw,sh);
  if(Build.VERSION.SDK_INT>=30){android.view.WindowMetrics m=c.getSystemService(WindowManager.class).getMaximumWindowMetrics();android.graphics.Insets in=m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safe.set(in.left,in.top,sw-in.right,sh-in.bottom);}
  int gap=Apple.dp(c,10),width=Math.min(Apple.dp(c,244),safe.width()-2*gap);content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int height=Math.min(content.getMeasuredHeight(),safe.height()-2*gap);
  int left=pet.lp.x,top=pet.lp.y,right=left+pet.lp.width,bottom=top+pet.lp.height,x,y;
  if(safe.right-right>=width+2*gap){x=right+gap;y=(top+bottom-height)/2;}
  else if(left-safe.left>=width+2*gap){x=left-gap-width;y=(top+bottom-height)/2;}
  else{x=(left+right-width)/2;y=top-safe.top>safe.bottom-bottom?top-gap-height:bottom+gap;}
  x=Math.max(safe.left+gap,Math.min(safe.right-width-gap,x));y=Math.max(safe.top+gap,Math.min(safe.bottom-height-gap,y));
  d.show();Apple.dialog(d);w.setWindowAnimations(0);w.setTitle("大肥鱼-快捷菜单/"+pet.id);w.setLayout(width,height);WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.TOP|Gravity.LEFT;p.x=x;p.y=y;p.flags&=~WindowManager.LayoutParams.FLAG_DIM_BEHIND;p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);w.setAttributes(p);FrostedPanel.configure(d,22,false);
  w.getDecorView().setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){d.dismiss();return true;}return false;});
  UiMotion.enter(content);
 }
 static final class MenuIcon extends Drawable {
  final int size,kind;final Paint p=new Paint(3);MenuIcon(Context c,int k){size=Apple.dp(c,22);kind=k;setBounds(0,0,size,size);}
  public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
  public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(size/24f,size/24f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Apple.INK);Path path=new Path();
   if(kind==0){c.drawRoundRect(3,4,21,17,4,4,p);path.moveTo(6,17);path.lineTo(6,21);path.lineTo(11,17);}
   if(kind==1){path.moveTo(3,11);path.lineTo(21,11);path.moveTo(4,11);path.cubicTo(4,17,8,20,12,20);path.cubicTo(16,20,20,17,20,11);path.moveTo(9,7);path.quadTo(10,5,9,3);path.moveTo(14,7);path.quadTo(15,5,14,3);}
   if(kind==2){path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();}
   if(kind==3){p.setStyle(Paint.Style.FILL);c.drawCircle(5,12,1.8f,p);c.drawCircle(12,12,1.8f,p);c.drawCircle(19,12,1.8f,p);}c.drawPath(path,p);c.restore();}
  public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
