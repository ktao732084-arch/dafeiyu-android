package com.xiaoxiao.pet;
import android.app.*;import android.os.*;import org.json.*;import java.util.concurrent.*;

/** Uses a controlled model, but the real Android Handler, storage, prompt builder and runtime. */
public class AgentInstrumentation extends Instrumentation {
 int checks;void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 static final class Reply implements AgentRuntime.Listener {
  final CountDownLatch ready=new CountDownLatch(1);String text,error,emotion,partial;
  public void partial(String v){partial=v;}public void done(String v,String e){text=v;emotion=e;ready.countDown();}public void error(String v){error=v;ready.countDown();}
  void await()throws Exception{if(!ready.await(15,TimeUnit.SECONDS))throw new AssertionError("callback timeout");}
 }
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;android.content.Context originalContext=null;
  try{
   runOnMainSync(()->Data.init(getTargetContext()));
   originalContext=Data.app;
   final String namespace="runtime-tests-"+java.util.UUID.randomUUID();
   final java.io.File isolatedFiles=new java.io.File(getTargetContext().getCacheDir(),namespace);isolatedFiles.mkdirs();
   Data.app=new android.content.ContextWrapper(getTargetContext()){
    public android.content.Context getApplicationContext(){return this;}
    public java.io.File getFilesDir(){return isolatedFiles;}
    public android.content.SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences(namespace,mode);}
   };
   PersonaManager.initialize("main","classic");
   AgentRuntime.memory=new AgentStore(new java.io.File(isolatedFiles,"agent"));
   String originalUrl=Data.prefs().getString("api.url",""),originalModel=Data.prefs().getString("api.model",""),originalSecret=Data.prefs().getString("api.secret","");boolean originalMemory=AgentRuntime.rememberEnabled();
   try{
    Data.prefs().edit().putString("api.url","https://example.invalid/v1").putString("api.model","test-model").putBoolean("agent.memory",true).commit();Chat.secret("test-only-key");
    check("test-only-key".equals(Chat.secret()),"Android Keystore round trip");check(!Data.prefs().getString("api.secret","").contains("test-only-key"),"key is encrypted");
    AgentRuntime.model=(messages,token,progress)->{check(messages.getJSONObject(0).getString("content").contains("鲸鱼娘"),"persona is loaded");progress.accept("{\"reply\":\"蓝色，我也喜欢。");return "{\"reply\":\"蓝色，我也喜欢。\",\"emotion\":\"happy\",\"memories\":[\"我喜欢蓝色\",\"用户喜欢红色\"]}";};
    Reply first=new Reply();check(AgentRuntime.send("我喜欢蓝色",false,first),"start turn");first.await();check(first.error==null&&first.text.contains("蓝色"),"model reply displayed");check("happy".equals(first.emotion),"emotion parsed");check(first.partial!=null&&!first.partial.contains("reply"),"stream shows only reply");check(AgentRuntime.store().snapshot().getJSONArray("memories").length()==1,"grounded memory only");
    AgentRuntime.model=(messages,token,progress)->{check(messages.toString().contains("我喜欢蓝色"),"recall injected");return "{\"reply\":\"记得呀，蓝色。\",\"emotion\":\"bad-action\",\"memories\":[]}";};
    Reply second=new Reply();AgentRuntime.send("我喜欢什么颜色？",false,second);second.await();check("calm".equals(second.emotion),"unsupported action sanitized");
    AgentRuntime.store().clear(false);Data.prefs().edit().putBoolean("agent.memory",false).commit();AgentRuntime.model=(messages,token,progress)->{check(!messages.toString().contains("我喜欢蓝色"),"memory off excludes stored memories");return "正常回复";};Reply third=new Reply();AgentRuntime.send("你好",false,third);third.await();check("正常回复".equals(third.text),"plain-text compatibility");
    Data.prefs().edit().putBoolean("agent.memory",true).commit();int before=AgentRuntime.history().length();
    CountDownLatch entered=new CountDownLatch(1),exit=new CountDownLatch(1);AgentRuntime.model=(messages,token,progress)->{entered.countDown();exit.await(10,TimeUnit.SECONDS);return "迟到的回复";};Reply cancel=new Reply();AgentRuntime.send("取消测试",false,cancel);check(entered.await(5,TimeUnit.SECONDS),"model entered");AgentRuntime.cancel();cancel.await();exit.countDown();Thread.sleep(150);check(AgentRuntime.history().length()==before,"cancel cannot commit");
    AgentRuntime.model=(messages,token,progress)->{throw new java.io.IOException("test transport failure");};Reply fail=new Reply();AgentRuntime.send("失败测试",false,fail);fail.await();check(fail.error!=null&&AgentRuntime.history().length()==before,"failed turn not committed");
    Data.prefs().edit().remove("api.secret").commit();Reply local=new Reply();AgentRuntime.send("记住：我喜欢小熊",false,local);local.await();check(local.error==null&&AgentRuntime.store().memoryContext("小熊").contains("小熊"),"explicit memory works offline");
    check("点击回应-开心跃动".equals(AgentRuntime.action("happy")),"emotion animation mapping");
    result.putString("stream","PASS: "+checks+" Android runtime checks\n");
   }finally{AgentRuntime.cancel();AgentRuntime.model=AgentTransport::request;AgentRuntime.memory=null;Data.prefs().edit().putString("api.url",originalUrl).putString("api.model",originalModel).putString("api.secret",originalSecret).putBoolean("agent.memory",originalMemory).commit();}
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e.toString());result.putString("stack",android.util.Log.getStackTraceString(e));}
  try{Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}catch(Exception ignored){}
  if(originalContext!=null)Data.app=originalContext;AgentRuntime.memory=null;
  finish(code,result);
 }
}
