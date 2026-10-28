package com.xiaoxiao.pet;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Inset grey groups on white, single dividers, optional colour badges, and native accessible buttons. */
final class GroupedList {
 static final int CHAT=0,CLOCK=1,BOLT=2,BOOK=3,HEART=4,TAG=5,SLIDERS=6,PLAY=7,INFO=8,PHONE=9,MOON=10,SHARE=11,PLUS=12,TRASH=13,WAVE=14;
 // Badge colours stay saturated in both appearances, like the primary button.
 static final int SKY=0xff1670ef,MINT=0xff17b26a,ORANGE=0xfff79009,PINK=0xffee46bc,VIOLET=0xff7a5af8,TEAL=0xff0ba5ec,GREY=0xff8a9099,RED=0xfff04438;
 static LinearLayout group(LinearLayout parent){Context c=parent.getContext();LinearLayout l=new LinearLayout(c);l.setOrientation(1);l.setBackground(Apple.shape(c,Apple.FILL,16));l.setClipToOutline(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=Apple.dp(c,10);parent.addView(l,p);return l;}
 static TextView section(LinearLayout parent,String title){Context c=parent.getContext();TextView t=Apple.label(c,title,13,true);t.setTextColor(Apple.SECONDARY);t.setPadding(Apple.dp(c,4),Apple.dp(c,22),0,0);parent.addView(t);return t;}
 static TextView note(LinearLayout parent,String text){Context c=parent.getContext();TextView t=Apple.label(c,text,12,false);t.setTextColor(Apple.SECONDARY);t.setLineSpacing(Apple.dp(c,3),1);t.setPadding(Apple.dp(c,6),Apple.dp(c,8),Apple.dp(c,6),Apple.dp(c,2));parent.addView(t);return t;}
 static void divider(LinearLayout group){if(group.getChildCount()==0)return;Context c=group.getContext();View line=Apple.hairline(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,1);p.leftMargin=Apple.dp(c,Boolean.TRUE.equals(group.getTag())?60:16);group.addView(line,p);}
 static Button row(LinearLayout group,String text,View.OnClickListener action){Context c=group.getContext();divider(group);Button b=new Button(c);b.setText(text);Apple.button(b,false);b.setTextColor(Apple.INK);b.setTypeface(Typeface.create("sans-serif",0));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18656580),Apple.shape(c,Apple.FILL,0),null));b.setPadding(Apple.dp(c,16),0,Apple.dp(c,14),0);b.setCompoundDrawablesWithIntrinsicBounds(null,null,new Chevron(c),null);b.setOnClickListener(action);group.addView(b,new LinearLayout.LayoutParams(-1,Apple.dp(c,54)));return b;}
 /** A settings row led by a small coloured badge, as in the phone's own settings. */
 static Button row(LinearLayout group,String text,int icon,int color,View.OnClickListener action){group.setTag(true);Button b=row(group,text,action);Context c=group.getContext();b.setCompoundDrawablePadding(Apple.dp(c,14));b.setCompoundDrawablesWithIntrinsicBounds(new Badge(c,icon,color),null,new Chevron(c),null);return b;}
 /** Multi-line row for user-written content such as memories. */
 static Button entry(LinearLayout group,String text,View.OnClickListener action){Button b=row(group,text,action);Context c=group.getContext();b.setSingleLine(false);b.setMaxLines(3);b.setLineSpacing(Apple.dp(c,2),1);b.setPadding(Apple.dp(c,16),Apple.dp(c,13),Apple.dp(c,14),Apple.dp(c,13));b.getLayoutParams().height=-2;b.setMinHeight(Apple.dp(c,54));return b;}
 static Button menuOption(LinearLayout group,String text,View.OnClickListener action){Context c=group.getContext();Button b=new Button(c);b.setText(text);Apple.button(b,false);b.setTextColor(Apple.INK);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18656580),Apple.shape(c,Apple.PANEL,12),null));b.setClipToOutline(true);b.setPadding(Apple.dp(c,12),0,Apple.dp(c,12),0);b.setOnClickListener(action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Apple.dp(c,48));p.topMargin=Apple.dp(c,4);p.bottomMargin=Apple.dp(c,4);group.addView(b,p);return b;}
 static Switch toggle(LinearLayout group,String text,boolean checked,android.widget.CompoundButton.OnCheckedChangeListener action){divider(group);Context c=group.getContext();Switch s=new Switch(c);s.setText(text);s.setTextSize(15);s.setTextColor(Apple.INK);s.setPadding(Apple.dp(c,16),0,Apple.dp(c,12),0);s.setChecked(checked);s.setOnCheckedChangeListener(action);group.addView(s,new LinearLayout.LayoutParams(-1,Apple.dp(c,54)));return s;}
 static final class Chevron extends Drawable {
  final int size;final Paint paint=new Paint(3);Chevron(Context c){size=Apple.dp(c,16);setBounds(0,0,size,size);}
  public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
  public void draw(Canvas c){paint.setColor(Apple.SECONDARY);paint.setAlpha(150);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*.1f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Rect b=getBounds();Path p=new Path();p.moveTo(b.left+size*.4f,b.top+size*.25f);p.lineTo(b.left+size*.65f,b.top+size*.5f);p.lineTo(b.left+size*.4f,b.top+size*.75f);c.drawPath(p,paint);}
  public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 /** White line glyph on a rounded colour square, drawn on a 24-unit grid. */
 static final class Badge extends Drawable {
  final int size,kind,color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  Badge(Context c,int kind,int color){size=Apple.dp(c,30);this.kind=kind;this.color=color;setBounds(0,0,size,size);}
  public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
  public void draw(Canvas c){
   Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(size/24f,size/24f);
   p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(0,0,24,24,7,7,p);
   p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path g=new Path();
   switch(kind){
    case CHAT:c.drawRoundRect(5,6,19,16,3.5f,3.5f,p);g.moveTo(8,16);g.lineTo(8,19);g.lineTo(11.5f,16);break;
    case CLOCK:c.drawCircle(12,12,7,p);g.moveTo(12,8);g.lineTo(12,12);g.lineTo(15,14);break;
    case BOLT:g.moveTo(13,4.5f);g.lineTo(7.5f,13);g.lineTo(11.5f,13);g.lineTo(10.5f,19.5f);g.lineTo(16.5f,11);g.lineTo(12.5f,11);g.close();break;
    case BOOK:c.drawRoundRect(6.5f,5,17.5f,19,2,2,p);g.moveTo(9.5f,9);g.lineTo(14.5f,9);g.moveTo(9.5f,12);g.lineTo(13,12);break;
    case HEART:g.moveTo(12,18.5f);g.cubicTo(4,13,5.5f,6.5f,9,6.5f);g.cubicTo(10.6f,6.5f,11.5f,7.5f,12,8.6f);g.cubicTo(12.5f,7.5f,13.4f,6.5f,15,6.5f);g.cubicTo(18.5f,6.5f,20,13,12,18.5f);g.close();break;
    case TAG:g.moveTo(5.5f,6.5f);g.lineTo(5.5f,12);g.lineTo(12.5f,19);g.lineTo(19,12.5f);g.lineTo(12,5.5f);g.lineTo(6.5f,5.5f);g.close();c.drawCircle(9,9,.9f,p);break;
    case SLIDERS:g.moveTo(5,8);g.lineTo(19,8);g.moveTo(5,16);g.lineTo(19,16);p.setStyle(Paint.Style.FILL);c.drawCircle(9,8,2.2f,p);c.drawCircle(15,16,2.2f,p);p.setStyle(Paint.Style.STROKE);break;
    case PLAY:g.moveTo(9,6.5f);g.lineTo(17.5f,12);g.lineTo(9,17.5f);g.close();break;
    case INFO:c.drawCircle(12,12,7,p);g.moveTo(12,11);g.lineTo(12,15.5f);p.setStyle(Paint.Style.FILL);c.drawCircle(12,8.3f,1,p);p.setStyle(Paint.Style.STROKE);break;
    case PHONE:c.drawRoundRect(7.5f,4.5f,16.5f,19.5f,2.5f,2.5f,p);g.moveTo(11,16.5f);g.lineTo(13,16.5f);break;
    case MOON:g.moveTo(16.5f,14.5f);g.cubicTo(12,15.5f,8.5f,12,9.5f,7);g.cubicTo(6.5f,8,5,10.5f,5.5f,13.5f);g.cubicTo(6.3f,17.5f,11,19.8f,14.5f,17.8f);g.cubicTo(15.4f,17,16,16,16.5f,14.5f);g.close();break;
    case SHARE:g.moveTo(12,5);g.lineTo(12,14);g.moveTo(8.5f,8.5f);g.lineTo(12,5);g.lineTo(15.5f,8.5f);g.moveTo(7,12.5f);g.lineTo(7,18.5f);g.lineTo(17,18.5f);g.lineTo(17,12.5f);break;
    case PLUS:g.moveTo(12,6.5f);g.lineTo(12,17.5f);g.moveTo(6.5f,12);g.lineTo(17.5f,12);break;
    case TRASH:g.moveTo(6,8);g.lineTo(18,8);g.moveTo(10,8);g.lineTo(10,6);g.lineTo(14,6);g.lineTo(14,8);g.moveTo(7.5f,8);g.lineTo(8.5f,18.5f);g.lineTo(15.5f,18.5f);g.lineTo(16.5f,8);break;
    default:g.moveTo(4.5f,13);g.cubicTo(7,9,9,9,12,13);g.cubicTo(15,17,17,17,19.5f,13);break;
   }
   c.drawPath(g,p);c.restore();
  }
  public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
