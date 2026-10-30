package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;

/** Real Android chat input and persistence, controlled model; never touches the production app's data. */
public final class PersonaUpdateInstrumentation extends Instrumentation {
 MainActivity activity;ChatPage page;int checks;
 interface Checked{void run()throws Exception;}
 void main(Checked action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
 void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void send(String text){page.input.setText(text);page.send();}
 void settle()throws Exception{AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);main(()->{});}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.memory",false).putBoolean("agent.diary",false).putBoolean("agent.proactive",false).commit();UpdateRuntime.channels=c->"";AgentRuntime.memory=new AgentStore(new File(getTargetContext().getFilesDir(),"agent"));GrowthStore.quiet();PersonaManager.initialize("main","gentle");ModelSettings.save("https://example.invalid/v1","controlled","test-only-key",false);});
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(()->{page=new ChatPage(activity,"main","main");page.open();});
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AgentRuntime.model=(m,t,p)->{entered.countDown();release.await(8,TimeUnit.SECONDS);throw new IOException("controlled failure");};
   main(()->send("我今天有点累"));check(entered.await(5,TimeUnit.SECONDS),"first request entered");try{main(()->{check(page.input.isEnabled(),"typing stays enabled");page.input.setText("还有一句正在打");});}finally{release.countDown();}settle();main(()->check(page.input.getText().toString().equals("我今天有点累\n还有一句正在打"),"failure preserves unsent typing"));
   CountDownLatch entered2=new CountDownLatch(1),release2=new CountDownLatch(1);List<String> asks=new CopyOnWriteArrayList<>();AgentRuntime.model=(m,t,p)->{asks.add(m.getJSONObject(m.length()-1).getString("content"));if(asks.size()==1){entered2.countDown();release2.await(8,TimeUnit.SECONDS);t.check();}return "几句话我都听到了。";};
   main(()->send("第一句"));check(entered2.await(5,TimeUnit.SECONDS),"joined request entered");try{main(()->{send("第二句");send("第三句");});}finally{release2.countDown();}settle();JSONArray history=AgentRuntime.store().historyPage(null,20);check(history.length()==1&&history.getJSONObject(0).getString("user").equals("第一句\n第二句\n第三句"),"three messages become one saved turn");check(asks.size()==2,"cancelled intermediate request never calls model");main(()->check(!page.sending&&page.status.getText().length()==0,"interjection finishes without stop error"));
   AgentRuntime.model=(m,t,p)->"这句已经回复好了。";
   main(()->{send("已经保存的第一句");AgentRuntime.worker.submit(()->{}).get(5,TimeUnit.SECONDS);send("刚补发的第二句");page.input.setText("第三句还在输入");});settle();main(()->check(page.input.getText().toString().equals("刚补发的第二句\n第三句还在输入"),"saved-answer boundary preserves all drafting"));
   history=AgentRuntime.store().historyPage(null,20);check(history.length()==2&&history.getJSONObject(1).optInt("displayed")>0,"committed answer still displayed once");
   main(()->{page.input.setText(String.join("",Collections.nCopies(5000,"一")));page.restoreDraft(String.join("",Collections.nCopies(5000,"二")));check(page.input.length()==10001,"long restored draft is not truncated");page.input.setText("刚补发的第二句\n第三句还在输入");});
   waitForIdleSync();SystemClock.sleep(500);main(()->check(page.dialog.getWindow().getDecorView().hasWindowFocus(),"chat owns foreground"));try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"persona-interjection.png"))){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"screenshot saved");}
   List<JSONObject> stylesInput=Collections.singletonList(new JSONObject().put("id","synthetic-sensitive").put("user","这是测试，我的密码是橘子汽水"));String raw="{\"phrases\":[{\"phrase\":\"橘子汽水\",\"when\":\"聊天\"}]}";check(Phrases.parse(raw,stylesInput,"2026-10-30").isEmpty(),"sensitive-source phrase rejected on Android");
   List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(Data.read(getTargetContext().getAssets().open("personas.json"))));Growth.Story story=GrowthStore.story();Growth.Material material=new Growth.Material(new JSONArray(),"你",15,250,2);material.viaGame=true;material.met=7;Set<String> voices=new HashSet<>();for(PersonaManager.Persona style:styles){String lines=Growth.scene(story,3,false,material,style).lines().toString();check(!lines.contains("{viagame}")&&!lines.contains("{met}"),"chapter placeholders resolved");voices.add(lines);}check(voices.size()==5,"all five chapter voices are distinct");
   result.putString("stream","PASS: "+checks+" Android persona-update checks\n");
  }catch(Throwable t){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));}
  finally{try{main(()->{AgentRuntime.cancel();if(page!=null)page.dialog.dismiss();if(activity!=null)activity.finish();});}catch(Exception ignored){}}
  finish(code,result);
 }
}
