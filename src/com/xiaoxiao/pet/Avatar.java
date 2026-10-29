package com.xiaoxiao.pet;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** Round chat avatars: her face cropped from the bundled portrait, or the first letter of her name without one. */
final class Avatar extends Drawable {
 private static final Map<String,Bitmap> portraits=new HashMap<>();
 // Face area of the bundled 320×240 portrait, as fractions of its width and height.
 private static final float LEFT=114/320f,TOP=52/240f,SIDE=88/320f;
 private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Bitmap bitmap;private final String letter;private final int size;
 private Avatar(Context c,Bitmap bitmap,String letter,int dp){this.bitmap=bitmap;this.letter=letter;size=Apple.dp(c,dp);setBounds(0,0,size,size);text.setTextAlign(Paint.Align.CENTER);text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
 static Avatar of(Context c,String role,int dp){return new Avatar(c,portrait(c,role),Data.name(role).isEmpty()?"鱼":Data.name(role).substring(0,1),dp);}
 private static synchronized Bitmap portrait(Context c,String role){
  String path=Data.role(role).optString("portrait");if(path.isEmpty())return null;
  if(!portraits.containsKey(path)){Bitmap b=null;try(java.io.InputStream in=c.getAssets().open(path)){b=BitmapFactory.decodeStream(in);}catch(Exception ignored){}portraits.put(path,b);}
  return portraits.get(path);
 }
 public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
 public void draw(Canvas c){
  Rect b=getBounds();float r=Math.min(b.width(),b.height())/2f,cx=b.exactCenterX(),cy=b.exactCenterY();
  fill.setColor(Apple.TINT);c.drawCircle(cx,cy,r,fill);
  if(bitmap!=null){
   float side=bitmap.getWidth()*SIDE,scale=2*r/side;Matrix m=new Matrix();m.setTranslate(-bitmap.getWidth()*LEFT,-bitmap.getHeight()*TOP);m.postScale(scale,scale);m.postTranslate(cx-r,cy-r);
   BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);shader.setLocalMatrix(m);image.setShader(shader);c.drawCircle(cx,cy,r,image);
  }else{text.setColor(Apple.BLUE);text.setTextSize(r*.9f);c.drawText(letter,cx,cy-(text.descent()+text.ascent())/2,text);}
 }
 public void setAlpha(int a){fill.setAlpha(a);image.setAlpha(a);text.setAlpha(a);}
 public void setColorFilter(ColorFilter f){image.setColorFilter(f);}
 public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
