package com.xiaoxiao.pet;

import android.app.*;
import android.os.Bundle;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Opt-in synthetic data only, on a newly created emulator. Never seed an existing user's app. */
public class UpgradeAcceptance extends Instrumentation {
    Bundle args;
    public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
    void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            require("fresh-emulator-only".equals(args.getString("scope")),"Explicit isolated test scope required");
            runOnMainSync(()->Data.init(getTargetContext()));
            File proof=new File(getTargetContext().getFilesDir(),"upgrade-acceptance.json");
            AgentStore store=AgentRuntime.store();
            if("seed".equals(args.getString("mode"))){
                require(!proof.exists()&&!PersonaManager.isInitialized("main")&&store.history().length()==0&&store.snapshot().getJSONArray("memories").length()==0&&!ModelSettings.complete(),"Refuse to seed any existing app data");
                PersonaManager.initialize("main","gentle");Data.prefs().edit().putString("agent.nickname","升级验收小船长").commit();Chat.secret("synthetic-upgrade-key-not-a-service-key");
                store.remember("我喜欢蓝色");store.commit(store.epoch(),"升级验收：我喜欢蓝色","记住啦，这是覆盖安装测试。","calm",new JSONArray(),false,false);store.awaitWrites();
                JSONObject expected=new JSONObject().put("persona",PersonaManager.prompt("main")).put("history",store.history().toString()).put("memories",store.snapshot().getJSONArray("memories").toString());
                Files.write(proof.toPath(),expected.toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream","PASS: seeded synthetic persona, nickname, memory, history and encrypted test key in old release\n");
            }else{
                JSONObject expected=new JSONObject(new String(Files.readAllBytes(proof.toPath()),StandardCharsets.UTF_8));
                require(expected.getString("persona").equals(PersonaManager.prompt("main")),"Frozen persona changed");
                require(expected.getString("history").equals(store.history().toString()),"History changed");
                require(expected.getString("memories").equals(store.snapshot().getJSONArray("memories").toString()),"Memories changed");
                require("升级验收小船长".equals(Data.prefs().getString("agent.nickname","")),"Nickname lost");
                require("synthetic-upgrade-key-not-a-service-key".equals(Chat.secret()),"Keystore key lost");
                require(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode==15,"New version not installed");
                result.putString("stream","PASS: actual 0.4.9.1 -> 0.4.9.2 overlay preserves frozen persona, nickname, exact memories/history and Android Keystore secret\n");
            }
            code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");result.putString("stack",android.util.Log.getStackTraceString(e));}
        finish(code,result);
    }
}
