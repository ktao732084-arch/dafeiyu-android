package com.xiaoxiao.pet;

import android.app.*;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class ModelSetupTest {
    MainActivity activity;AlertDialog dialog;
    @Before public void init()throws Exception {
        AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);
        Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();
        TestKeyStore.install();AgentRuntime.memory=null;PetService.instance=null;
        ModelSetup.probe=(c,t)->{throw new AssertionError("No live model may be called by this test");};activity=UiTest.ready();
    }
    @After public void cleanup()throws Exception {if(dialog!=null)dialog.dismiss();ShadowLooper.idleMainLooper();ModelSetup.probe=ModelSetup::request;AgentRuntime.cancel();Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    void open(){Chat.settings(activity);dialog=(AlertDialog)UiTest.dialog();}
    EditText field(String name){return (EditText)UiTest.description(dialog.getWindow().getDecorView(),name);}
    String status(){return ((TextView)UiTest.description(dialog.getWindow().getDecorView(),"模型连接状态")).getText().toString();}
    void drainUntil(java.util.function.BooleanSupplier done)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!done.getAsBoolean()&&System.nanoTime()<end){ShadowLooper.idleMainLooper();Thread.sleep(10);}ShadowLooper.idleMainLooper();assertTrue("async setup did not finish",done.getAsBoolean());}
    @Test public void firstRunOnlyShowsKeyAndOfficialLinks(){
        open();assertTrue(((RadioButton)UiTest.find(dialog.getWindow().getDecorView(),"DeepSeek（推荐）")).isChecked());
        assertFalse(field("API 地址").isShown());assertFalse(field("模型名称").isShown());assertTrue(field("API Key").isShown());
        assertEquals(1,UiTest.all(dialog.getWindow().getDecorView(),EditText.class).stream().filter(v->v.isShown()).count());
        assertTrue(UiTest.texts(dialog.getWindow().getDecorView()).contains("DeepSeek V4.1 Flash"));
        UiTest.click(dialog,"去 DeepSeek 官网获取 Key");Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals(ModelPresets.KEY_URL,intent.getDataString());
        assertEquals("连接并保存",dialog.getButton(-1).getText().toString());assertFalse(ModelSettings.complete());
    }
    @Test public void pastedKeyTestsThenSavesPresetAtomically()throws Exception {
        open();AtomicInteger calls=new AtomicInteger();ModelSetup.probe=(c,t)->{calls.incrementAndGet();assertEquals("https://api.deepseek.com/chat/completions",c.address.endpoint);assertEquals("deepseek-flash",c.model);assertEquals("test-only-key",c.secret);assertFalse(c.keyHeader);assertFalse(ModelSettings.complete());return "连接成功。";};
        field("API Key").setText("  test-only-key  ");dialog.getButton(-1).performClick();drainUntil(()->!dialog.isShowing());
        assertEquals(1,calls.get());assertTrue(ModelSettings.complete());assertEquals("test-only-key",ModelSettings.read().secret);assertFalse(Data.prefs().getString("api.secret","").contains("test-only-key"));
        open();assertEquals("",field("API Key").getText().toString());assertFalse(field("API 地址").isShown());
    }
    @Test public void failedConnectionKeepsPreviousConfigAndMasksEnteredKey()throws Exception {
        ModelSettings.save("https://existing.example/v1","existing-model","previous-key",false);Map<String,?> before=new HashMap<>(Data.prefs().getAll());open();UiTest.click(dialog,"DeepSeek（推荐）");
        ModelSetup.probe=(c,t)->{throw new IOException("API Key test-only-key 无效");};field("API Key").setText("test-only-key");dialog.getButton(-1).performClick();drainUntil(()->status().contains("无效"));
        assertTrue(dialog.isShowing());assertEquals(before,Data.prefs().getAll());assertFalse(status().contains("test-only-key"));assertTrue(dialog.getButton(-1).isEnabled());
    }
    @Test public void providerSwitchNeverReusesOtherProvidersSavedOrTypedKey()throws Exception {
        ModelSettings.save("https://existing.example/v1","existing-model","previous-key",false);open();field("API Key").setText("typed-other-provider-key");UiTest.click(dialog,"DeepSeek（推荐）");assertEquals("",field("API Key").getText().toString());
        dialog.getButton(-1).performClick();assertTrue(status().contains("更换接口服务商"));assertEquals("existing-model",ModelSettings.read().model);
    }
    @Test public void legacyCustomConfigurationIsNotReplacedByPreset()throws Exception {
        ModelSettings.save("https://api.deepseek.com/v1","deepseek-chat","legacy-key",true);open();assertTrue(field("API 地址").isShown());assertEquals("deepseek-chat",field("模型名称").getText().toString());
        dialog.getButton(-1).performClick();assertFalse(dialog.isShowing());assertEquals("deepseek-chat",ModelSettings.read().model);assertTrue(ModelSettings.read().keyHeader);assertEquals("legacy-key",ModelSettings.read().secret);
    }
    @Test public void closingDuringProbePreventsLateSave()throws Exception {
        open();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        ModelSetup.probe=(c,t)->{entered.countDown();release.await(5,TimeUnit.SECONDS);returned.countDown();return "连接成功";};field("API Key").setText("test-only-key");dialog.getButton(-1).performClick();
        try{assertTrue(entered.await(3,TimeUnit.SECONDS));assertFalse(dialog.getButton(-1).isEnabled());dialog.dismiss();}finally{release.countDown();}
        assertTrue(returned.await(3,TimeUnit.SECONDS));Chat.executor.submit(()->{}).get(3,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();assertFalse(ModelSettings.complete());
    }
    @Test public void timeoutAllowsRetryButCannotSaveLateResult()throws Exception {
        open();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<AgentTransport.Token> token=new AtomicReference<>();
        ModelSetup.probe=(c,t)->{token.set(t);entered.countDown();release.await(5,TimeUnit.SECONDS);return "连接成功";};field("API Key").setText("test-only-key");dialog.getButton(-1).performClick();
        try{assertTrue(entered.await(3,TimeUnit.SECONDS));ShadowLooper.idleMainLooper(46,TimeUnit.SECONDS);assertTrue(status().contains("超时"));assertTrue(token.get().cancelled);assertTrue(dialog.getButton(-1).isEnabled());assertFalse(ModelSettings.complete());}finally{release.countDown();}
    }
    @Test public void flashDisablesThinkingWithoutChangingOtherProviders()throws Exception {
        JSONObject p=new JSONObject();ModelPresets.apply("api.deepseek.com","deepseek-flash",p);assertEquals("disabled",p.getJSONObject("thinking").getString("type"));
        for(String[] item:new String[][]{{"api.deepseek.com","deepseek-reasoner"},{"other.example","deepseek-flash"}}){JSONObject untouched=new JSONObject();ModelPresets.apply(item[0],item[1],untouched);assertFalse(untouched.has("thinking"));}
        assertTrue(ModelPresets.httpError(401).contains("Key"));assertTrue(ModelPresets.httpError(402).contains("余额不足"));
        assertTrue(ModelPresets.isRecommended(ModelPresets.DEEPSEEK_URL,ModelPresets.DEEPSEEK_MODEL,false));assertFalse(ModelPresets.isRecommended("https://api.deepseek.com.evil.example",ModelPresets.DEEPSEEK_MODEL,false));
    }
}
