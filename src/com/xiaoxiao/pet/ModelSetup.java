package com.xiaoxiao.pet;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** One-key onboarding; custom providers keep their existing configuration. */
final class ModelSetup {
    interface Probe {String run(ModelSettings.Config config,AgentTransport.Token token)throws Exception;}
    static Probe probe=ModelSetup::request;
    static String request(ModelSettings.Config config,AgentTransport.Token token)throws Exception {
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content","请只回复：连接成功。"));
        return AgentTransport.request(config,messages,token,raw->{},false,128);
    }
    static void show(Activity activity){new Screen(activity).show();}
    static final class Screen {
        final Activity a;final Handler main=new Handler(Looper.getMainLooper());
        final LinearLayout body,advanced,guide;final RadioGroup providers;final RadioButton deepseek,custom;
        final EditText url,model,key;final Switch header;final TextView status,balance;final Button test,clear,refreshBalance;
        AlertDialog dialog;AgentTransport.Token active;Runnable deadline;
        Screen(Activity activity){
            a=activity;body=column();body.setPadding(dp(16),dp(8),dp(16),dp(12));
            providers=new RadioGroup(a);providers.setOrientation(1);
            deepseek=option("DeepSeek（推荐）");custom=option("其他模型 / 自定义");body.addView(providers);
            guide=column();body.addView(guide);
            label(guide,"DeepSeek V4.1 Flash · 日常聊天\n只要一枚 API Key，地址和模型都替你选好了。",14);
            label(guide,"① 打开官方平台，登录后创建 API Key\n② 复制完整的 Key，回到这里粘贴\n③ 点「连接并保存」，她就能开口啦",13);
            button(guide,"去 DeepSeek 官网获取 Key",()->open(ModelPresets.KEY_URL));
            label(guide,"API 按用量收费，需要账户有可用余额；网页聊天免费不代表 API 免费。",12);
            button(guide,"查看余额 / 充值",()->open(ModelPresets.BALANCE_URL));
            advanced=column();body.addView(advanced);
            label(advanced,"填写兼容 Chat Completions 的 HTTPS 接口。现有配置会保留。",13);
            url=Chat.field(a,advanced,"API 地址（https://…/v1）",Data.prefs().getString("api.url",""),false);url.setContentDescription("API 地址");
            model=Chat.field(a,advanced,"模型名称",Data.prefs().getString("api.model",""),false);model.setContentDescription("模型名称");
            header=new Switch(a);header.setText("使用 api-key 请求头");header.setTextColor(Apple.INK);header.setChecked(Data.prefs().getBoolean("api.header",false));advanced.addView(header);
            key=Chat.field(a,body,Data.prefs().getString("api.secret","").isEmpty()?"在这里粘贴 API Key":"已保存 Key；留空继续使用","",true);key.setContentDescription("API Key");
            key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            label(body,"Key 加密存在这台手机里。聊天内容和相关记忆会发给你选的模型服务商。",12);
            test=button(body,"测试连接",()->connect(false));
            status=label(body,"连接测试会发送一条简短消息，使用你的 API 额度。",12);status.setContentDescription("模型连接状态");status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            balance=label(body,DeepSeekBalance.cached(),13);balance.setContentDescription("账户余额");balance.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            refreshBalance=button(body,"刷新余额",this::queryBalance);
            clear=button(body,"清除已保存的 Key",this::clearKey);clear.setVisibility(Data.prefs().getString("api.secret","").isEmpty()?View.GONE:View.VISIBLE);
            boolean recommended=Data.prefs().getString("api.url","").isEmpty()||ModelPresets.isRecommended(url.getText().toString(),model.getText().toString(),header.isChecked());
            providers.check(recommended?deepseek.getId():custom.getId());
            providers.setOnCheckedChangeListener((group,id)->{cancel();key.setText("");balance.setText("余额尚未查询");status.setText("切换服务商后请粘贴对应的 API Key。");mode();});
            android.text.TextWatcher changed=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){balance.setText("配置已更改，请刷新余额");}public void afterTextChanged(android.text.Editable s){}};
            key.addTextChangedListener(changed);url.addTextChangedListener(changed);header.setOnCheckedChangeListener((b,value)->balance.setText("配置已更改，请刷新余额"));
        }
        void show(){
            ScrollView scroll=new SpringScrollView(a);scroll.addView(body);
            dialog=new Apple.Builder(a).setTitle("模型连接").setView(scroll).setNegativeButton("取消",null).setPositiveButton("连接并保存",null).show();
            dialog.setOnDismissListener(d->cancel());
            dialog.getButton(-1).setOnClickListener(v->{if(deepseek.isChecked())connect(true);else saveCustom();});mode();
        }
        void mode(){boolean simple=deepseek.isChecked();guide.setVisibility(simple?View.VISIBLE:View.GONE);advanced.setVisibility(simple?View.GONE:View.VISIBLE);test.setVisibility(simple?View.GONE:View.VISIBLE);if(dialog!=null)dialog.getButton(-1).setText(simple?"连接并保存":"保存");}
        ModelSettings.Config config()throws Exception {
            String base=deepseek.isChecked()?ModelPresets.DEEPSEEK_URL:url.getText().toString().trim();
            return new ModelSettings.Config(base,deepseek.isChecked()?ModelPresets.DEEPSEEK_MODEL:model.getText().toString().trim(),ModelSettings.selectedSecret(base,key.getText().toString()),!deepseek.isChecked()&&header.isChecked(),Data.prefs().getBoolean("agent.stream",true));
        }
        void saveCustom(){try{save(config());}catch(Exception e){error(e);}}
        void clearKey(){cancel();try{ModelSettings.clear();key.setText("");key.setHint("在这里粘贴 API Key");balance.setText("余额尚未查询");status.setText("已清除 Key，重新粘贴后即可连接。");clear.setVisibility(View.GONE);refresh();}catch(Exception e){error(e);}}
        void save(ModelSettings.Config config)throws Exception {
            ModelSettings.save(config.address.base,config.model,config.secret,config.keyHeader);
            dialog.dismiss();refresh();Toast.makeText(a,"模型设置已保存，可以开始聊天啦",Toast.LENGTH_SHORT).show();
        }
        void refresh(){if(a instanceof MainActivity&&!a.isFinishing()&&!a.isDestroyed())((MainActivity)a).render();}
        void connect(boolean saveAfter){
            if(active!=null)return;final ModelSettings.Config config;
            try{config=config();}catch(Exception e){error(e);return;}
            final AgentTransport.Token token=new AgentTransport.Token();active=token;enabled(false);status.setText("正在连接，请稍候…");
            deadline=()->{if(active==token){cancel();status.setText("连接超时，请检查网络后重试。原配置未更改。");}};main.postDelayed(deadline,45000);
            Chat.executor.execute(()->{
                Exception failed=null;DeepSeekBalance.Result funds=null;String balanceText="当前服务商暂不支持余额查询";
                try{String answer=probe.run(config,token);token.check();if(answer==null||answer.trim().isEmpty())throw new java.io.IOException("模型没有返回文本");}catch(Exception e){failed=e;}
                if(token.cancelled)return;
                if(DeepSeekBalance.supported(config))try{funds=DeepSeekBalance.client.read(config,token);balanceText=funds.summary;}catch(Exception e){balanceText="余额查询失败，余额未知；可稍后刷新。";}
                final Exception failure=failed;final DeepSeekBalance.Result result=funds;final String details=balanceText;
                main.post(()->{if(!current(token))return;finish();balance.setText(details);
                    if(failure!=null){error(failure);return;}
                    try{
                        if(saveAfter){save(config);if(result!=null)DeepSeekBalance.cache(config,result);
                            new Apple.Builder(a).setTitle("连接成功").setMessage("模型已实际回复，设置已保存。\n\n"+details)
                                .setNegativeButton("完成",null).setPositiveButton("开始聊天",(d,w)->Chat.show(a,"main","main")).show();
                        }else{if(result!=null)DeepSeekBalance.cache(config,result);status.setText("连接成功，可以保存使用。");}
                    }catch(Exception e){error(e);}
                });
            });
        }
        void queryBalance(){
            if(active!=null)return;final ModelSettings.Config config;
            try{config=config();if(!DeepSeekBalance.supported(config)){balance.setText("当前服务商暂不支持余额查询，请到该服务商官网查看。");return;}}catch(Exception e){error(e);return;}
            AgentTransport.Token token=new AgentTransport.Token();active=token;enabled(false);balance.setText("正在查询官方余额…");
            deadline=()->{if(active==token){cancel();balance.setText("余额查询超时，余额未知；请稍后刷新。");}};main.postDelayed(deadline,20000);
            Chat.executor.execute(()->{try{DeepSeekBalance.Result result=DeepSeekBalance.client.read(config,token);token.check();
                main.post(()->{if(!current(token))return;finish();balance.setText(result.summary);DeepSeekBalance.cache(config,result);});
            }catch(Exception e){main.post(()->{if(!current(token))return;finish();balance.setText("余额查询失败，余额未知；请检查网络和 Key 后刷新。");});}});
        }
        boolean current(AgentTransport.Token token){return active==token&&!token.cancelled&&dialog.isShowing()&&!a.isFinishing()&&!a.isDestroyed();}
        void finish(){if(deadline!=null)main.removeCallbacks(deadline);active=null;enabled(true);}
        void cancel(){if(active!=null)active.cancel();finish();}
        void enabled(boolean value){key.setEnabled(value);url.setEnabled(value);model.setEnabled(value);header.setEnabled(value);deepseek.setEnabled(value);custom.setEnabled(value);test.setEnabled(value);clear.setEnabled(value);refreshBalance.setEnabled(value);if(dialog!=null)dialog.getButton(-1).setEnabled(value);}
        void error(Exception e){String message=e.getMessage();String entered=key.getText().toString().trim();if(message==null)message="请检查网络和 API Key 后重试。";if(!entered.isEmpty())message=message.replace(entered,"***");status.setText(message);}
        void open(String address){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}catch(ActivityNotFoundException e){status.setText("未找到浏览器，请用浏览器打开："+address);}}
        int dp(int value){return Apple.dp(a,value);}
        LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(1);return l;}
        TextView label(LinearLayout parent,String text,int size){TextView v=Apple.label(a,text,size,false);v.setTextColor(Apple.SECONDARY);v.setPadding(0,dp(6),0,dp(6));parent.addView(v);return v;}
        Button button(LinearLayout parent,String text,Runnable action){Button b=new Button(a);b.setText(text);Apple.button(b,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(6);p.bottomMargin=dp(2);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
        RadioButton option(String name){RadioButton b=new RadioButton(a);b.setId(View.generateViewId());b.setText(name);Apple.choice(b);RadioGroup.LayoutParams p=new RadioGroup.LayoutParams(-1,-2);p.topMargin=dp(10);providers.addView(b,p);return b;}
    }
}
