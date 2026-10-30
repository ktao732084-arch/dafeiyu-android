package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.webkit.*;
import java.util.*;import java.util.concurrent.*;import org.json.*;

/** Real WebView/HTTPS acceptance in a separate UID, using synthetic feedback only. */
public final class FeedbackInstrumentation extends Instrumentation {
 MainActivity activity;Dialog dialog;WebView web;int checks;
 void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}return null;}
 void locate(){try{java.lang.reflect.Field f=DialogLifecycle.class.getDeclaredField("windows");f.setAccessible(true);Map<?,?> all=(Map<?,?>)f.get(null);Set<?> windows=(Set<?>)all.get(activity);if(windows!=null)for(Object item:windows){Dialog d=(Dialog)item;WebView w=find(d.getWindow().getDecorView());if(w!=null&&d.isShowing()){dialog=d;web=w;return;}}}catch(Exception e){throw new RuntimeException(e);}}
 String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};runOnMainSync(()->web.evaluateJavascript(code,value->{result[0]=value;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("JavaScript timed out");return result[0];}
 void waitJs(String expr)throws Exception{for(int i=0;i<80;i++){if("true".equals(js(expr)))return;SystemClock.sleep(400);}throw new AssertionError("Page condition failed: "+expr+"; "+js("document.body.innerText"));}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   runOnMainSync(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.proactive",false).putBoolean("agent.touchReply",false).commit();UpdateRuntime.channels=c->"";try{PersonaManager.initialize("main","classic");}catch(Exception e){throw new RuntimeException(e);}});
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   runOnMainSync(()->{FeedbackPanel.show(activity);locate();check(web!=null,"real product feedback WebView attached");});
   waitJs("document.getElementById('send')!==null&&!document.getElementById('send').disabled");check(true,"native install headers establish HTTPS session");
   check(js("location.href").contains("https://81.70.79.133/dafeiyu-feedback/"),"configured self-hosted origin");
   check("true".equals(js("!document.cookie.includes('dafeiyu_feedback')")),"session credentials are HttpOnly");
   js("document.getElementById('text').value='Android WebView 部署验收：只用于测试，不是用户反馈。';document.getElementById('text').dispatchEvent(new Event('input'));document.getElementById('send').click();");
   waitJs("document.querySelectorAll('#reports article').length===1");check(true,"native WebView submits and loads saved report");
   check("true".equals(js("document.querySelector('#reports').innerText.includes('Android WebView 部署验收')")),"Unicode report rendered");
   String receipt=js("document.querySelector('#reports .receipt').innerText");result.putString("receipt",receipt);
   String id=Data.prefs().getString("feedback.openid","");
   runOnMainSync(()->{dialog.dismiss();FeedbackPanel.show(activity);locate();});
   waitJs("document.querySelectorAll('#reports article').length===1");check(id.equals(Data.prefs().getString("feedback.openid","")),"same identity after reopening");check(true,"feedback remains after reopening");
   String state=js("document.querySelector('#reports').innerText");check(state.contains("Android WebView 部署验收"),"reopened page retains original text");
   java.io.File shot=new java.io.File(getTargetContext().getExternalFilesDir(null),"feedback-android.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(shot)){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot saved");}
   result.putString("stream","PASS: "+checks+" native WebView feedback checks; "+receipt+"\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));}
  finally{try{runOnMainSync(()->{if(dialog!=null)dialog.dismiss();if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
