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
        DeepSeekBalance.client=(c,t)->funds(true,"18.50");
        ModelSetup.probe=(c,t)->{throw new AssertionError("No live model may be called by this test");};activity=UiTest.ready();
    }
    @After public void cleanup()throws Exception {if(dialog!=null)dialog.dismiss();ShadowLooper.idleMainLooper();ModelSetup.probe=ModelSetup::request;DeepSeekBalance.client=DeepSeekBalance::request;AgentRuntime.cancel();Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);}
    static DeepSeekBalance.Result funds(boolean available,String total)throws Exception{return DeepSeekBalance.parse(new JSONObject().put("is_available",available).put("balance_infos",new JSONArray().put(new JSONObject().put("currency","CNY").put("total_balance",total).put("topped_up_balance",total).put("granted_balance","0.00"))));}
    String balance(){return ((TextView)UiTest.description(dialog.getWindow().getDecorView(),"账户余额")).getText().toString();}
    void open(){Chat.settings(activity);dialog=(AlertDialog)UiTest.dialog();}
    EditText field(String name){return (EditText)UiTest.description(dialog.getWindow().getDecorView(),name);}
    String status(){return ((TextView)UiTest.description(dialog.getWindow().getDecorView(),"模型连接状态")).getText().toString();}
    void drainUntil(java.util.function.BooleanSupplier done)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!done.getAsBoolean()&&System.nanoTime()<end){ShadowLooper.idleMainLooper();Thread.sleep(10);}ShadowLooper.idleMainLooper();assertTrue("async setup did not finish",done.getAsBoolean());}
    @Test public void keyPasteAndCustomStreamingSwitch()throws Exception{
        open();UiTest.click(dialog,"其他模型 / 自定义");Switch stream=(Switch)UiTest.find(dialog.getWindow().getDecorView(),"流式接收（接口不支持时关闭）");assertTrue(stream.isShown());assertTrue(stream.isChecked());
        android.content.ClipboardManager board=activity.getSystemService(android.content.ClipboardManager.class);
        board.setPrimaryClip(android.content.ClipData.newPlainText("key","  sk-test-only-1234567890  "));UiTest.click(dialog,"粘贴剪贴板里的 Key");
        assertEquals("sk-test-only-1234567890",field("API Key").getText().toString());assertFalse("the status never shows the whole key",status().contains("test-only-1234567890"));
        board.setPrimaryClip(android.content.ClipData.newPlainText("key","这不是 一枚 key"));UiTest.click(dialog,"粘贴剪贴板里的 Key");assertTrue(status().contains("不像"));assertEquals("sk-test-only-1234567890",field("API Key").getText().toString());
        field("API 地址").setText("https://example.invalid/v1");field("模型名称").setText("mock");stream.setChecked(false);dialog.getButton(-1).performClick();ShadowLooper.idleMainLooper();
        assertTrue(ModelSettings.complete());assertFalse(Data.prefs().getBoolean("agent.stream",true));
    }
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
        assertTrue(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains("18.50"));assertTrue(DeepSeekBalance.cached().contains("18.50"));
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
    @Test public void balanceFailureDoesNotDiscardWorkingModelConnection()throws Exception {
        open();ModelSetup.probe=(c,t)->"连接成功";DeepSeekBalance.client=(c,t)->{throw new IOException("offline");};
        field("API Key").setText("test-only-key");dialog.getButton(-1).performClick();drainUntil(()->!dialog.isShowing());assertTrue(ModelSettings.complete());
        assertTrue(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains("余额未知"));assertEquals("余额尚未查询",DeepSeekBalance.cached());
    }
    @Test public void refreshBalanceUsesSavedKeyWithoutCallingChatAndClearPurgesCache()throws Exception {
        ModelSettings.save(ModelPresets.DEEPSEEK_URL,ModelPresets.DEEPSEEK_MODEL,"saved-test-key",false);open();AtomicInteger calls=new AtomicInteger();
        DeepSeekBalance.client=(c,t)->{assertEquals("saved-test-key",c.secret);calls.incrementAndGet();return funds(true,"32.10");};UiTest.click(dialog,"刷新余额");drainUntil(()->balance().contains("32.10"));
        assertEquals(1,calls.get());assertTrue(DeepSeekBalance.cached().contains("上次查询"));assertTrue(DeepSeekBalance.cached().contains("32.10"));
        UiTest.click(dialog,"清除已保存的 Key");assertEquals("余额尚未查询",balance());assertFalse(Data.prefs().contains("api.balance.summary"));
    }
    @Test public void foreignProviderNeverCallsDeepSeekBalanceEndpoint()throws Exception {
        ModelSettings.save("https://other.example/v1","other-model","foreign-key",false);open();DeepSeekBalance.client=(c,t)->{throw new AssertionError("foreign key leaked");};
        UiTest.click(dialog,"刷新余额");assertTrue(balance().contains("暂不支持"));assertFalse(DeepSeekBalance.supported(ModelSettings.read()));
        assertFalse(DeepSeekBalance.supported(new ModelSettings.Config("https://api.deepseek.com:8443","deepseek-flash","test-only-key",false,true)));
    }
    @Test public void insufficientFundsShowsActualZeroAndDoesNotSaveUnusableModel()throws Exception {
        open();ModelSetup.probe=(c,t)->{throw new IOException(ModelPresets.httpError(402));};DeepSeekBalance.client=(c,t)->funds(false,"0.00");field("API Key").setText("test-only-key");dialog.getButton(-1).performClick();
        drainUntil(()->status().contains("余额不足"));assertTrue(balance().contains("¥0.00"));assertTrue(balance().contains("请充值"));assertFalse(ModelSettings.complete());
    }
    @Test public void cancelledBalanceCannotPopulateCache()throws Exception {
        ModelSettings.save(ModelPresets.DEEPSEEK_URL,ModelPresets.DEEPSEEK_MODEL,"saved-test-key",false);open();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        DeepSeekBalance.client=(c,t)->{entered.countDown();release.await(5,TimeUnit.SECONDS);returned.countDown();return funds(true,"99.00");};UiTest.click(dialog,"刷新余额");
        try{assertTrue(entered.await(3,TimeUnit.SECONDS));dialog.dismiss();}finally{release.countDown();}
        assertTrue(returned.await(3,TimeUnit.SECONDS));Chat.executor.submit(()->{}).get(3,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();assertFalse(Data.prefs().contains("api.balance.summary"));
    }
    @Test public void balanceParserKeepsCurrenciesSeparateAndRejectsMalformedAmounts()throws Exception {
        JSONObject usd=new JSONObject().put("currency","USD").put("total_balance","2.5000").put("topped_up_balance","2.00").put("granted_balance","0.5000");
        JSONObject cny=new JSONObject().put("currency","CNY").put("total_balance","10.25").put("topped_up_balance","10.00").put("granted_balance","0.25");
        JSONObject payload=new JSONObject().put("is_available",true).put("balance_infos",new JSONArray().put(cny).put(usd));String summary=DeepSeekBalance.parse(payload).summary;
        assertTrue(summary.contains("人民币 ¥10.25"));assertTrue(summary.contains("美元 $2.5000"));
        usd.put("total_balance","unknown");try{DeepSeekBalance.parse(payload);fail("malformed amount must not become zero");}catch(IOException expected){}
        try{DeepSeekBalance.parse(new JSONObject().put("is_available",true).put("balance_infos",new JSONArray()));fail("missing balance must not become zero");}catch(IOException expected){}
    }
}
