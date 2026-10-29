package com.xiaoxiao.pet;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import android.content.Intent;
import android.widget.FrameLayout;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class CompanionBehaviorTest {
    PetService service;
    @Before public void init()throws Exception {
        AgentRuntime.cancel();AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);
        Data.app=null;Data.roles.clear();Data.init(RuntimeEnvironment.getApplication());Data.prefs().edit().clear().commit();
        AgentRuntime.memory=new AgentStore(Files.createTempDirectory("behavior-memory").toFile());
        service=Robolectric.buildService(PetService.class).get();PetService.instance=service;MainActivity.active=false;
    }
    @After public void cleanup()throws Exception {
        service.handler.removeCallbacksAndMessages(null);
        android.view.Choreographer.getInstance().removeFrameCallback(service.motionFrame);
        PetService.instance=null;MainActivity.active=false;AgentRuntime.cancel();
        AgentRuntime.worker.submit(()->{}).get(15,TimeUnit.SECONDS);
        Data.logWriter.submit(()->{}).get(5,TimeUnit.SECONDS);
    }
    @Test public void chatWakesVisibleSleepingPetAndReachesPrompt()throws Exception {
        PetService.Pet pet=service.new Pet("main","main") { @Override void play(String action){current=action;} };
        pet.visible=true;pet.frame=new FrameLayout(service);pet.current=ActionPlan.DOZE;service.pets.put("main",pet);
        service.heard("main");assertEquals(ActionPlan.WOKEN,pet.current);
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.now=System.currentTimeMillis();
        in.lastEvent=AgentRuntime.lastEvent;in.lastEventAt=AgentRuntime.lastEventAt;
        JSONArray request=PromptBuilder.build(in,"醒醒",false,AgentRuntime.store().snapshot(),null);
        assertTrue(request.toString().contains("消息叫醒"));
        pet.visible=false;pet.current=ActionPlan.DOZE;service.heard("main");assertEquals(ActionPlan.DOZE,pet.current);
    }
    @Test public void screenAndKeyguardGateAnimationWithoutPretendingToBeInteraction()throws Exception {
        long touch=AgentRuntime.lastTouch;
        service.screen.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));assertFalse(service.animating());
        service.screenOn=true;service.keyguard=true;assertFalse(service.animating());
        service.screen.onReceive(service,new Intent(Intent.ACTION_USER_PRESENT));assertTrue(service.animating());
        MainActivity.active=true;assertFalse(service.animating());
        assertEquals(touch,AgentRuntime.lastTouch);
        service.started=true;assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(null,0,1));
    }
    @Test public void scheduledChoicesExistInBundledCharacterCatalog()throws Exception {
        Collection<String> catalog=Data.actions("main");assertTrue(catalog.contains(ActionPlan.DOZE));assertTrue(catalog.contains(ActionPlan.WOKEN));
        for(int seed=0;seed<8;seed++){
            ActionPlan plan=new ActionPlan(catalog);Random random=new Random(seed);
            LocalDateTime start=LocalDateTime.of(2026,1,1,0,0);
            for(int hour=0;hour<365*24;hour+=3){String action=plan.idle(ActionPlan.Moment.of(start.plusHours(hour)),random);assertTrue("missing animation: "+action,catalog.contains(action));}
        }
    }
    @Test public void clearSelfInvalidatesInFlightTurnAndPurgesBackup()throws Exception {
        File dir=Files.createTempDirectory("self-clear").toFile();AgentStore db=new AgentStore(dir);
        assertTrue(db.commit(db.epoch(),"你喜欢什么","我最爱吃小鱼干","happy",new JSONArray(),true,false,null,null,"self-turn",0,new JSONArray().put("我最爱吃小鱼干")));
        assertEquals(1,db.snapshot().getJSONArray("self").length());long oldEpoch=db.epoch();
        db.clearSelf();assertFalse(db.commit(oldEpoch,"你喜欢什么","我最爱吃小鱼干","happy",null,true,false));
        assertFalse(new File(dir,"companion-v1.json.bak").exists());assertEquals(0,new AgentStore(dir).snapshot().getJSONArray("self").length());
    }
    @Test public void selfStatementsNeverReplaceFrozenPersonaAndCanBeDisabled()throws Exception {
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="初始化固定人格";in.now=System.currentTimeMillis();in.remember=true;
        JSONObject state=AgentRuntime.store().snapshot().put("self",new JSONArray().put(new JSONObject().put("text","我最爱吃小鱼干").put("at",in.now)));
        JSONArray request=PromptBuilder.build(in,"你好",false,state,null);
        String system=request.getJSONObject(0).getString("content");assertTrue(system.startsWith(in.persona));assertTrue(system.contains("和初始人格冲突时以初始人格为准"));
        assertTrue(request.toString().contains("我最爱吃小鱼干"));in.remember=false;
        assertFalse(PromptBuilder.build(in,"你好",false,state,null).toString().contains("我最爱吃小鱼干"));
    }
    @Test public void transportKeepsNativeNonStreamingOverrideAndReasoningBudget()throws Exception {
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicReference<JSONObject> captured=new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/v1/chat/completions",exchange->{try{
            captured.set(new JSONObject(AgentTransport.boundedRead(exchange.getRequestBody(),65536)));
            byte[] body="{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
        }catch(Exception e){throw new RuntimeException(e);}finally{exchange.close();}});server.start();
        try{for(String model:new String[]{"deepseek-chat","deepseek-reasoner"}){
            ModelSettings.Config config=loopbackTransportConfig(server,model);
            assertEquals("ok",AgentTransport.request(config,new JSONArray(),new AgentTransport.Token(),raw->{},false,2048));
            assertFalse(captured.get().getBoolean("stream"));assertEquals(model.endsWith("reasoner")?8192:2048,captured.get().getInt("max_tokens"));
        }}finally{server.stop(0);}
    }
    @Test public void filteredStreamIsRejectedEvenWhenDoneMarkerFollows()throws Exception {
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            byte[] body=("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"content_filter\"}]}\n\ndata: [DONE]\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try{
            ModelSettings.Config config=loopbackTransportConfig(server,"mock");
            try{AgentTransport.request(config,new JSONArray(),new AgentTransport.Token(),raw->{},true,1200);fail("filtered response must not be committed");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("不完整"));}
        }finally{server.stop(0);}
    }
    // Route only the test's already-validated config to its local fixture. Production continues to require HTTPS.
    static ModelSettings.Config loopbackTransportConfig(com.sun.net.httpserver.HttpServer server,String model)throws Exception {
        ModelSettings.Config config=new ModelSettings.Config("https://example.invalid/v1",model,"test-only-key",false,true);
        java.lang.reflect.Field endpoint=ApiEndpoint.class.getDeclaredField("endpoint");endpoint.setAccessible(true);
        endpoint.set(config.address,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1/chat/completions");return config;
    }
}
