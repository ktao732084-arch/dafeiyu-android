package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.os.*;import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;import java.io.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;

/** Actual Android runtime, notebook rendering and deletion with synthetic history and a controlled diary model. */
public final class ExperienceInstrumentation extends Instrumentation {
 int checks;MainActivity activity;final AtomicInteger calls=new AtomicInteger();
 void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
 interface Checked{void run()throws Exception;}
 void main(Checked action){runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 AccessibilityNodeInfo find(String text){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return null;
  java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text);
  for(AccessibilityNodeInfo n:nodes)if(n.getText()!=null&&n.getText().toString().equals(text))return n;
  for(AccessibilityNodeInfo n:nodes)if(n.getText()!=null&&n.getText().toString().contains(text))return n;return null;}
 void click(String text){AccessibilityNodeInfo n=find(text);check(n!=null,"visible control: "+text);while(n!=null&&!n.isClickable())n=n.getParent();check(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click: "+text);waitForIdleSync();}
 public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;AgentRuntime.Model original=AgentRuntime.diaryModel;
  try{
   main(()->{Data.init(getTargetContext());Data.prefs().edit().clear().putBoolean("agent.diary",false).putBoolean("agent.proactive",false).commit();
    PersonaManager.initialize("main","classic");ModelSettings.save("https://example.invalid/v1","controlled-diary","test-only-key",false);UpdateRuntime.channels=c->"";
    AgentRuntime.memory=new AgentStore(new File(getTargetContext().getCacheDir(),"shared-"+UUID.randomUUID()));});
   AgentStore db=AgentRuntime.store();ZoneId zone=ZoneId.systemDefault();LocalDate day=LocalDate.now(zone).minusDays(1);long at=day.atTime(19,0).atZone(zone).toInstant().toEpochMilli();
   // Synthetic historical game, not a changed device clock or a fabricated real-user chat.
   File dir=new File(getTargetContext().getCacheDir(),"shared-games-"+UUID.randomUUID());dir.mkdirs();AgentRuntime.memory=db=new AgentStore(dir);GomokuStore games=new GomokuStore(dir);
   Gomoku game=new Gomoku(UUID.randomUUID().toString(),at,zone.getId());for(int p:new int[]{0,15,1,16,2,17,3,18,4})game.play(p);game.finishedAt=at+1000;
   ChatArchive.write(new File(games.results,game.id+".json"),game.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   String frozen=PersonaManager.prompt("main");
   AgentRuntime.diaryModel=(m,t,p)->{calls.incrementAndGet();check(m.toString().contains("game:"+game.id),"actual request carries archived game source");return "{\"moment\":{\"source_id\":\"game:"+game.id+"\",\"reflection\":\"回想这盘棋，输了也还想再试一次。\"}}";};
   main(()->Data.prefs().edit().putBoolean("agent.diary",true).commit());
   activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),ExperienceNotebookActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   AgentRuntime.diaryWorker.submit(()->{}).get(20,TimeUnit.SECONDS);waitForIdleSync();
   check(calls.get()==1&&Diary.entries(db.snapshot()).length()==1,"app entry writes game-only day once");
   AgentRuntime.writeDiaryIfDue();AgentRuntime.diaryWorker.submit(()->{}).get(10,TimeUnit.SECONDS);check(calls.get()==1,"daily model-call budget survives another check");
   check(frozen.equals(PersonaManager.prompt("main"))&&db.snapshot().getJSONArray("memories").length()==0,"persona and user facts unchanged");
   JSONObject page=Diary.entries(db.snapshot()).getJSONObject(0);long epoch=db.epoch();
   main(()->MemoryPanel.diary(activity));waitForIdleSync();SystemClock.sleep(650);
   File shot=new File(getTargetContext().getExternalFilesDir(null),"shared-experiences.png");try(FileOutputStream out=new FileOutputStream(shot)){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"notebook screenshot saved");}
   check(find("发生的事：五子棋对局：你赢，她输。")!=null&&find("她的回想")!=null,"fact and reflection both rendered on Android");
   click("查看依据");check(find("回想整理于")!=null,"source and writing times visible");click("知道了");
   click("删除这篇");check(Diary.entries(db.snapshot()).length()==1,"delete asks before changing stored page");click("删除");
   Chat.storage.submit(()->{}).get(10,TimeUnit.SECONDS);waitForIdleSync();
   check(Diary.entries(db.snapshot()).length()==0&&Diary.suppressed(db.snapshot(),day),"single-page deletion leaves suppression marker");
   check(!db.addDiary(epoch,page)&&games.hasResult(game.id,game.finishedAt,game.outcome()),"late page rejected, real game retained");
   check(Diary.suppressed(new AgentStore(dir).snapshot(),day),"deleted page remains suppressed after store reopen");
   click("关闭");
   check(db.commit(db.epoch(),"我答应你明天把初稿发给你。","十分钟后我提醒你喝水。","calm",null,true,false,null,null,"bilateral",System.currentTimeMillis(),null,true),"promise turn saved");
   check(Commitments.entries(db.snapshot()).length()==1,"only user promise before display");db.shown("bilateral",999,true);db.awaitWrites();check(Commitments.entries(db.snapshot()).length()==2,"visible assistant promise captured on Android");
   main(()->MemoryPanel.commitments(activity));waitForIdleSync();SystemClock.sleep(450);
   File promiseShot=new File(getTargetContext().getExternalFilesDir(null),"bilateral-commitments.png");try(FileOutputStream out=new FileOutputStream(promiseShot)){check(getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"promise notebook screenshot saved");}
   check(find("你答应的")!=null&&find("她答应的")!=null,"two owners rendered separately");click("她答应的");click("管理");click("标记完成");Chat.storage.submit(()->{}).get(10,TimeUnit.SECONDS);waitForIdleSync();
   check("done".equals(Commitments.entries(db.snapshot()).getJSONObject(1).optString("status")),"manual completion stored without executing a task");
   result.putString("stream","PASS: "+checks+" Android shared-experience checks\n");
  }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("stream","FAIL: "+e);result.putString("stack",android.util.Log.getStackTraceString(e));}
  finally{AgentRuntime.diaryModel=original;try{main(()->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.diary",false).commit();if(activity!=null)activity.finish();});}catch(Throwable ignored){}}
  finish(code,result);
 }
}
