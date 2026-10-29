package com.xiaoxiao.pet;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class PersonaContinuityTest {
 @Before public void init()throws Exception{Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();AgentRuntime.cancel();AgentRuntime.memory=new AgentStore(Files.createTempDirectory("persona-continuity").toFile());TestKeyStore.install();}
 @After public void cleanup(){AgentRuntime.cancel();AgentRuntime.model=AgentTransport::request;}
 @Test public void diaryAndChapterUseActiveVoiceWithoutChangingFrozenPersona()throws Exception{
  PersonaManager.initialize("main","gentle");String frozen=PersonaManager.prompt("main");
  String voice=PersonaManager.diaryGuidance("main");assertTrue(voice.startsWith(frozen));assertTrue(voice.contains("句子柔"));
  assertTrue(PersonaManager.voice("main","feed","").contains("{food}"));
  JSONArray memories=new JSONArray().put(MemoryLedger.record("quote-1","我喜欢蓝色","source-1",true,System.currentTimeMillis()));
  Growth.Scene scene=Growth.scene(GrowthStore.story(),4,false,new Growth.Material(memories,"你",20,7,3),PersonaManager.activeStyle("main"));
  assertFalse(scene.lines().toString().contains("不准笑"));assertTrue(scene.lines().toString().contains("我喜欢蓝色"));assertEquals("quote-1",scene.sources.getString(0));
  assertFalse(scene.fallback.toString().contains("我喜欢蓝色"));assertEquals(frozen,PersonaManager.prompt("main"));
 }
 @Test public void runtimeDoesNotApplyWorkVentingToAdviceOrOtherTopics()throws Exception{
  PersonaManager.initialize("main","steady");JSONObject state=AgentRuntime.store().snapshot();
  state.put("understanding",new JSONArray().put(new JSONObject().put("text","工作倾诉时似乎希望先听完再回应").put("scene","venting").put("topic","work").put("confirmed",3)));
  assertTrue(tail(AgentRuntime.context("工作被领导骂了，只想吐槽",false,state,null,null)).contains("工作倾诉时似乎希望先听完再回应"));
  assertFalse(tail(AgentRuntime.context("工作这次我需要建议",false,state,null,null)).contains("工作倾诉时似乎希望先听完再回应"));
  assertFalse(tail(AgentRuntime.context("朋友让我很难过",false,state,null,null)).contains("工作倾诉时似乎希望先听完再回应"));
 }
 @Test public void replyKeepFlagReachesStoreAndPreservesMoodSource()throws Exception{
  UiTest.ready();ModelSettings.save("https://example.invalid/v1","controlled-model","test-only-key",false);Data.prefs().edit().putBoolean("agent.diary",false).commit();
  AgentRuntime.model=(m,t,p)->"{\"reply\":\"谢谢，我很开心。\",\"emotion\":\"happy\",\"mood_level\":3,\"mood_cause\":\"受到鼓励\",\"mood_changed\":true,\"next_check_minutes\":20}";
  AgentRuntime.send("你做得真好",false,new UiTest.Counter());UiTest.awaitIdle();JSONObject before=AgentRuntime.store().snapshot();
  AgentRuntime.model=(m,t,p)->"{\"reply\":\"嗯，继续听你说。\",\"emotion\":\"sleepy\",\"mood_level\":3,\"mood_cause\":\"不应覆盖的改写\",\"mood_changed\":false,\"next_check_minutes\":20}";
  java.util.concurrent.atomic.AtomicReference<String> shown=new java.util.concurrent.atomic.AtomicReference<>();AgentRuntime.send("嗯",false,new AgentRuntime.Listener(){public void partial(String t){}public void done(String t,String e){shown.set(e);}public void error(String t){shown.set("error:"+t);}});UiTest.awaitIdle();JSONObject after=AgentRuntime.store().snapshot();assertEquals("happy",shown.get());
  assertEquals("happy",after.getString("emotion"));assertEquals(before.getLong("emotionAt"),after.getLong("emotionAt"));assertEquals(before.getString("moodSource"),after.getString("moodSource"));
 }
 static String tail(JSONArray request)throws Exception{return request.getJSONObject(request.length()-1).getString("content");}
}
