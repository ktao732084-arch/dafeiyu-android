package com.xiaoxiao.pet;
import android.app.*;import android.widget.*;import org.json.*;

final class MemoryPanel {
    static Switch toggle(MainActivity a,LinearLayout l,String label,boolean value){Switch s=new Switch(a);s.setText(label);s.setTextColor(Apple.INK);s.setChecked(value);l.addView(s);return s;}
    static void settings(MainActivity a){
        LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        l.addView(a.text("记忆引擎已内置在手机里。时间解析、去重、更正、归档与查找都在本机运行，不需要电脑或另外部署服务。",14));
        GroupedList.toggle(l,"用当前模型辅助理解记忆",Data.prefs().getBoolean("memory.smart",true),(v,on)->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("memory.smart",on).apply();});
        l.addView(a.text("默认开启。直接使用「模型连接」里已有的模型和 Key，理解换种说法、偏好变化等；可能增加模型请求。没有网络或模型不可用时使用本地规则，已有记忆仍保存在手机。",12));
        l.addView(a.text("上次整理："+MemoryBridge.lastStatus,12));
        a.button(l,"共同经历与记忆变化",v->history(a));
        a.button(l,"开发联调（可选）",v->serviceSettings(a));
        a.button(l,"使用手机内置引擎",v->{try{MemoryBridge.save(false,"","",false,false);MemoryBridge.lastStatus="手机内置记忆 · 默认运行";Toast.makeText(a,"已使用手机内置记忆",Toast.LENGTH_SHORT).show();}catch(Exception e){AgentPanel.fail(a,e);}});
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("手机记忆").setView(scroll).setPositiveButton("完成",null).show();
    }
    static void serviceSettings(MainActivity a){
        LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        l.addView(a.text("连接后，她可以整理记忆的变化、理解时间并按意思回忆。记忆仍保存在手机。服务收到本轮查询和手账快照；如果服务配置了模型，也会向该模型发送相关内容。",13));
        Switch enabled=toggle(a,l,"连接 Python 联调引擎","python-debug".equals(Data.prefs().getString("memory.mode","native"))&&Data.prefs().getBoolean("memory.service.enabled",false));
        EditText url=Chat.field(a,l,"记忆服务地址",Data.prefs().getString("memory.service.url","http://127.0.0.1:8787"),false);
        EditText key=Chat.field(a,l,"访问令牌（已保存时留空）","",true);
        Switch semantic=toggle(a,l,"按意思回忆",Data.prefs().getBoolean("memory.service.semantic",true));
        Switch judge=toggle(a,l,"智能判断记忆变化",Data.prefs().getBoolean("memory.service.judge",true));
        l.addView(a.text("语义模型与写入判断模型在记忆服务端配置。智能判断会产生额外模型请求；未配置时使用规则。关闭连接后仍可使用手机本地记忆。",12));
        TextView status=a.text("上次处理："+MemoryBridge.lastStatus,12);l.addView(status);
        Button probe=a.button(l,"保存并测试连接",v->{});
        probe.setOnClickListener(v->{try{
            MemoryBridge.save(enabled.isChecked(),url.getText().toString(),key.getText().toString(),semantic.isChecked(),judge.isChecked());key.setText("");
            MemoryBridge.Config config=MemoryBridge.read();if(!config.enabled){status.setText("手机本地记忆");return;}
            status.setText("正在连接…");probe.setEnabled(false);
            AgentRuntime.worker.execute(()->{String message;try{JSONObject result=MemoryBridge.client.call(config,"/health",null,new AgentTransport.Token());if(result.optInt("protocol")!=1)throw new Exception("服务协议不兼容");message="连接成功 · 语义模型："+result.optString("embeddings","none")+" · 智能判断："+(result.optBoolean("llm_judge")?"已配置":"尚未配置");}catch(Exception e){message="连接失败，请检查服务地址、令牌和网络。手机本地记忆可继续使用。";}final String outcome=message;a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed()){status.setText(outcome);probe.setEnabled(true);}});});
        }catch(Exception e){AgentPanel.fail(a,e);}});
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);AlertDialog dialog=new Apple.Builder(a).setTitle("记忆引擎").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).show();
        dialog.getButton(-1).setOnClickListener(v->{try{MemoryBridge.save(enabled.isChecked(),url.getText().toString(),key.getText().toString(),semantic.isChecked(),judge.isChecked());dialog.dismiss();}catch(Exception e){AgentPanel.fail(a,e);}});
    }
    static void history(MainActivity a){try{
        JSONObject s=AgentRuntime.store().snapshot();JSONArray archive=s.getJSONArray("archive"),audit=s.getJSONArray("audit");LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(18));
        l.addView(a.text("这些是已经变化或过期的事情，日常聊天不会当作当前事实。点开可查看来源或忘记相关版本。",13));
        for(int i=archive.length()-1;i>=0;i--){JSONObject r=archive.getJSONObject(i),e=r.optJSONObject("engine");String reason=e==null?"历史":"expired".equals(e.optString("archived_reason"))?"已过期":"已更新";a.button(l,"["+reason+"] "+r.optString("text"),v->historicalEntry(a,r));}
        l.addView(a.text("最近的整理记录",15));for(int i=audit.length()-1;i>=Math.max(0,audit.length()-30);i--){JSONObject e=audit.getJSONObject(i);String op=e.optString("op");String label="ADD".equals(op)?"写下记忆":"MERGE".equals(op)?"合并重复":"ARCHIVE".equals(op)?"收入历史":"INVALIDATE".equals(op)||"DOWNGRADE".equals(op)?"更新旧说法":"重复表达";l.addView(a.text(new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(e.optLong("at")))+" · "+label,12));}
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("共同经历与记忆变化").setView(scroll).setPositiveButton("关闭",null).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}
    static void historicalEntry(MainActivity a,JSONObject r){
        String at=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(r.optLong("at")));
        new Apple.Builder(a).setTitle("历史记忆").setMessage(r.optString("text")+"\n\n记录于 "+at+"\n这条记录已过期或被更新，不作为当前事实。").setNegativeButton("关闭",null).setPositiveButton("忘记相关版本",(d,w)->new Apple.Builder(a).setMessage("忘记这件事的当前与历史版本，以及直接来源对话？").setNegativeButton("取消",null).setPositiveButton("忘记",(dd,ww)->{try{AgentRuntime.cancel();AgentRuntime.store().forget(r.optString("id"));history(a);}catch(Exception e){AgentPanel.fail(a,e);}}).show()).show();
    }
}
