package com.xiaoxiao.pet;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import android.app.*;import android.widget.*;import org.json.*;import java.time.*;import java.util.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class PhraseUiTest {
 UiTest fixture=new UiTest();MainActivity activity;List<JSONObject> learned,turns;LocalDate day;long at;
 @Before public void init()throws Exception{fixture.init();activity=UiTest.ready();day=LocalDate.now().minusDays(1);at=day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();AgentStore db=AgentRuntime.store();db.commit(db.epoch(),"这也太有画面了","嗯，我听着。","calm",null,false,false,null,null,"phrase-origin",at,null,false);turns=db.dayTurns(day,ZoneId.systemDefault());learned=Phrases.parse("{\"phrases\":[{\"phrase\":\"有画面了\",\"when\":\"轻松聊天时\"}]}",turns,day.toString());assertTrue(db.addDiary(db.epoch(),Diary.entry(day,"今天聊了几句。",turns,at),null,learned));}
 @After public void cleanup()throws Exception{DialogLifecycle.close(activity);activity.pet.release();fixture.cleanup();}
 @Test public void phraseWithoutUnderstandingCanBeDeletedFromItsExistingPage()throws Exception{
  MemoryPanel.understanding(activity);Dialog list=UiTest.dialog();assertTrue(UiTest.texts(list.getWindow().getDecorView()).contains("她学会的你的说法"));
  Button row=null;for(Button button:UiTest.all(list.getWindow().getDecorView(),Button.class))if(button.getText().toString().contains("有画面了"))row=button;assertNotNull(row);row.performClick();ShadowLooper.idleMainLooper();((AlertDialog)UiTest.dialog()).getButton(-1).performClick();UiTest.storage();assertEquals(0,Phrases.entries(AgentRuntime.store().snapshot()).length());assertFalse(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains("有画面了"));
 }
 @Test public void bothSwitchesGateLearnedPhrasesAndDeletionRejectsLateDiary()throws Exception{
  Data.prefs().edit().putBoolean("agent.memory",true).putBoolean("agent.diary",true).commit();assertTrue(AgentRuntime.context("聊聊",false).toString().contains("对方常用的说法"));
  Data.prefs().edit().putBoolean("agent.diary",false).commit();assertFalse(AgentRuntime.context("聊聊",false).toString().contains("对方常用的说法"));Data.prefs().edit().putBoolean("agent.diary",true).putBoolean("agent.memory",false).commit();assertFalse(AgentRuntime.context("聊聊",false).toString().contains("对方常用的说法"));
  AgentStore db=AgentRuntime.store();long epoch=db.epoch();db.forgetPhrase(Phrases.entries(db.snapshot()).getJSONObject(0).getString("id"));assertFalse(db.addDiary(epoch,Diary.entry(day.minusDays(1),"迟到的日记",turns,at),null,learned));assertEquals(0,Phrases.entries(db.snapshot()).length());
 }
}
