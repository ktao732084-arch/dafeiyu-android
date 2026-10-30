package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.webkit.*;
import java.util.*;import java.util.concurrent.*;import org.json.*;

/** Real WebView/HTTPS acceptance in a separate UID, using synthetic feedback only. */
public final class FeedbackInstrumentation extends Instrumentation {
 MainActivity activity;Dialog dialog;WebView web;int checks;android.net.Uri fixture;
 void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}return null;}
 void locate(){try{java.lang.reflect.Field f=DialogLifecycle.class.getDeclaredField("windows");f.setAccessible(true);Map<?,?> all=(Map<?,?>)f.get(null);Set<?> windows=(Set<?>)all.get(activity);if(windows!=null)for(Object item:windows){Dialog d=(Dialog)item;WebView w=find(d.getWindow().getDecorView());if(w!=null&&d.isShowing()){dialog=d;web=w;return;}}}catch(Exception e){throw new RuntimeException(e);}}
 String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};runOnMainSync(()->web.evaluateJavascript(code,value->{result[0]=value;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("JavaScript timed out");return result[0];}
 void waitJs(String expr)throws Exception{for(int i=0;i<80;i++){if("true".equals(js(expr)))return;SystemClock.sleep(400);}throw new AssertionError("Page condition failed: "+expr+"; "+js("document.body.innerText"));}
 android.view.accessibility.AccessibilityNodeInfo choose(android.view.accessibility.AccessibilityNodeInfo n){if(n==null)return null;String text=String.valueOf(n.getText());if(n.isVisibleToUser()&&(text.equals("选择文件")||text.equals("Choose File")||text.equals("Choose file")))return n;for(int i=0;i<n.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo result=choose(n.getChild(i));if(result!=null)return result;}return null;}
 void attachFixture()throws Exception{
  ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"dafeiyu-feedback-check.png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
  fixture=getTargetContext().getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);check(fixture!=null,"test image created by isolated app");
  try(java.io.OutputStream out=getTargetContext().getContentResolver().openOutputStream(fixture)){android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(64,32,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.rgb(60,140,230));b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);b.recycle();}
  js("document.getElementById('image').scrollIntoView({block:'center'});");SystemClock.sleep(500);
  android.view.accessibility.AccessibilityNodeInfo button=choose(getUiAutomation().getRootInActiveWindow());boolean clicked=button!=null&&button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
  if(!clicked){
   JSONObject rect=new JSONObject((String)new JSONTokener(js("JSON.stringify((()=>{const r=document.getElementById('image').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,viewport:innerWidth};})())")).nextValue());
   float[] point=new float[2];runOnMainSync(()->{int[] at=new int[2];web.getLocationOnScreen(at);float scale=web.getWidth()/(float)rect.optDouble("viewport");point[0]=at[0]+(float)rect.optDouble("x")*scale;point[1]=at[1]+(float)rect.optDouble("y")*scale;check(point[1]>=at[1]&&point[1]<at[1]+web.getHeight(),"file input is within visible WebView");});
   long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(down,true);down.recycle();SystemClock.sleep(80);MotionEvent up=MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,point[0],point[1],0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);clicked=getUiAutomation().injectInputEvent(up,true);up.recycle();
  }
  check(clicked,"real file chooser control clicked");
  boolean[] pending={false};for(int i=0;i<30;i++){runOnMainSync(()->pending[0]=FeedbackPanel.picking!=null);if(pending[0])break;SystemClock.sleep(100);}check(pending[0],"system image picker callback pending");
  // Supply an owned content URI through the real Activity result route; do not browse personal photos.
  runOnMainSync(()->activity.onActivityResult(FeedbackPanel.PICK,Activity.RESULT_OK,new Intent().setData(fixture)));
  getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
  waitJs("!document.getElementById('preview').hidden&&!document.getElementById('send').disabled");check(true,"selected content URI readable by WebView FileReader");
 }
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   runOnMainSync(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",false).commit();UpdateRuntime.channels=c->"";try{PersonaManager.initialize("main","classic");}catch(Exception e){throw new RuntimeException(e);}});
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   runOnMainSync(()->{FeedbackPanel.show(activity);locate();check(web!=null,"real product feedback WebView attached");});
   waitJs("document.getElementById('send')!==null&&!document.getElementById('send').disabled");check(true,"native install headers establish HTTPS session");
   check(js("location.href").contains("https://81.70.79.133/dafeiyu-feedback/"),"configured self-hosted origin");
   check("true".equals(js("!document.cookie.includes('dafeiyu_feedback')")),"session credentials are HttpOnly");
   attachFixture();
   js("document.getElementById('text').value='Android WebView 部署验收：只用于测试，不是用户反馈。';document.getElementById('text').dispatchEvent(new Event('input'));document.getElementById('send').click();");
   waitJs("document.querySelectorAll('#reports article').length===1");check(true,"native WebView submits and loads saved report");
   waitJs("document.querySelector('#reports img')!==null&&document.querySelector('#reports img').naturalWidth>0");check(true,"native selected image uploaded and served privately");
   check("true".equals(js("document.querySelector('#reports').innerText.includes('Android WebView 部署验收')")),"Unicode report rendered");
   String receipt=js("document.querySelector('#reports .receipt').innerText");result.putString("receipt",receipt);
   String id=Data.prefs().getString("feedback.openid","");
   runOnMainSync(()->{dialog.dismiss();FeedbackPanel.show(activity);locate();});
   waitJs("document.querySelectorAll('#reports article').length===1");check(id.equals(Data.prefs().getString("feedback.openid","")),"same identity after reopening");check(true,"feedback remains after reopening");
   String state=js("document.querySelector('#reports').innerText");check(state.contains("Android WebView 部署验收"),"reopened page retains original text");
   java.io.File shot=new java.io.File(getTargetContext().getExternalFilesDir(null),"feedback-android.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(shot)){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot saved");}
   result.putString("stream","PASS: "+checks+" native WebView feedback checks; "+receipt+"\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));}
  finally{try{if(fixture!=null)getTargetContext().getContentResolver().delete(fixture,null,null);runOnMainSync(()->{if(dialog!=null)dialog.dismiss();if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
