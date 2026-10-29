package com.xiaoxiao.pet;
import android.app.*;import android.view.View;import android.widget.*;import org.json.*;

final class MemoryPanel {
    static void interactions(MainActivity a){try{
        JSONObject state=AgentRuntime.store().snapshot();JSONArray events=state.optJSONArray("interactions");StringBuilder text=new StringBuilder("这里保留近期200次点击、拖动等互动，并附发生时间；完整对话仍在聊天记录。清空聊天时会一起清除。\n\n");
        if(events==null||events.length()==0)text.append("还没有记录到桌宠互动。");
        else for(int i=events.length()-1;i>=0;i--){JSONObject e=events.getJSONObject(i);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.ROOT);f.setTimeZone(java.util.TimeZone.getTimeZone(e.optString("timeZone",java.util.TimeZone.getDefault().getID())));text.append(f.format(new java.util.Date(e.optLong("at")))).append("  ").append(e.optString("note")).append('\n');}
        String outcome=state.optString("proactiveOutcome");if(!outcome.isEmpty())text.append("\n上次考虑：").append("quiet".equals(outcome)?"选择先安静陪着你":"speak".equals(outcome)?"决定和你说句话":"error".equals(outcome)?"模型连接未完成，稍后再试":"窗口暂不可见，没有展示").append('\n');
        text.append("\n主动安排：").append(Initiative.status(state)).append("\n");
        if(state.has("proactiveNextAt"))text.append("\n下次考虑开口：").append(new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.ROOT).format(new java.util.Date(state.optLong("proactiveNextAt")))).append("（开启主动陪伴并满足可见和勿扰设置后才会考虑，不保证届时发消息）");
        new Apple.Builder(a).setTitle("最近互动").setMessage(text).setPositiveButton("知道了",null).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}
    static Switch toggle(MainActivity a,LinearLayout l,String label,boolean value){Switch s=new Switch(a);s.setText(label);s.setTextColor(Apple.INK);s.setChecked(value);l.addView(s);return s;}
    static void settings(MainActivity a){
        LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        TextView intro=GroupedList.note(l,"记忆引擎就装在手机里。理解时间、去重、更正、归档和查找都在本机完成，不需要电脑，也不用另外部署服务。");
        GroupedList.toggle(GroupedList.group(l),"用当前模型辅助理解记忆",Data.prefs().getBoolean("memory.smart",true),(v,on)->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("memory.smart",on).apply();});
        GroupedList.note(l,"默认开启。直接用「模型连接」里的模型和 Key，帮她听懂换种说法、喜好变化这些事，可能多用一点模型请求。没网或模型不可用时用本机规则，记忆照样存在手机里。");
        GroupedList.note(l,"上次整理："+MemoryBridge.lastStatus);
        LinearLayout rows=GroupedList.group(l);GroupedList.row(rows,"共同经历与记忆变化",v->history(a));
        // Python comparison is for developers: hidden unless already in use, and revealed by long-pressing the first paragraph.
        LinearLayout dev=GroupedList.group(l);dev.setVisibility("python-debug".equals(Data.prefs().getString("memory.mode","native"))?View.VISIBLE:View.GONE);
        intro.setOnLongClickListener(v->{dev.setVisibility(View.VISIBLE);return true;});
        GroupedList.row(dev,"开发联调（可选）",v->serviceSettings(a));
        GroupedList.row(dev,"使用手机内置引擎",v->{try{MemoryBridge.save(false,"","",false,false);MemoryBridge.lastStatus="手机内置记忆 · 默认运行";Toast.makeText(a,"已使用手机内置记忆",Toast.LENGTH_SHORT).show();}catch(Exception e){AgentPanel.fail(a,e);}});
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
        GroupedList.note(l,"这些是已经变了或过期的事，平时聊天不会当成现在的情况。点开能看来源，也能忘掉相关版本。");
        if(archive.length()>0){LinearLayout past=GroupedList.group(l);for(int i=archive.length()-1;i>=0;i--){JSONObject r=archive.getJSONObject(i),e=r.optJSONObject("engine");String reason=e==null?"历史":"expired".equals(e.optString("archived_reason"))?"已过期":"已更新";GroupedList.entry(past,"["+reason+"] "+r.optString("text"),v->historicalEntry(a,r));}}
        GroupedList.section(l,"最近的整理记录");for(int i=audit.length()-1;i>=Math.max(0,audit.length()-30);i--){JSONObject e=audit.getJSONObject(i);String op=e.optString("op");String label="ADD".equals(op)?"写下记忆":"MERGE".equals(op)?"合并重复":"ARCHIVE".equals(op)?"收入历史":"INVALIDATE".equals(op)||"DOWNGRADE".equals(op)?"更新旧说法":"重复表达";GroupedList.note(l,new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(e.optLong("at")))+" · "+label);}
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("共同经历与记忆变化").setView(scroll).setPositiveButton("关闭",null).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}
    /** What she has said about herself, kept so she stays consistent; read-only, clearable as a whole. */
    static void self(MainActivity a){try{
        JSONArray rows=AgentRuntime.store().snapshot().optJSONArray("self");LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(18));
        GroupedList.note(l,"这是她在聊天里亲口说过的关于自己的话（喜好、习惯），之后会尽量说到做到。这只是她的角色设定，不是你的记忆。");
        if(rows==null||rows.length()==0){TextView empty=a.text("还没有哦。聊到她喜欢什么的时候，会慢慢记下来。",15);empty.setTextColor(Apple.SECONDARY);empty.setPadding(a.dp(6),a.dp(16),a.dp(6),a.dp(8));l.addView(empty);}
        else{LinearLayout said=GroupedList.group(l);for(int i=rows.length()-1;i>=0;i--){GroupedList.divider(said);TextView line=a.text(rows.getJSONObject(i).optString("text"),15);line.setLineSpacing(a.dp(3),1);line.setPadding(a.dp(16),a.dp(13),a.dp(16),a.dp(13));said.addView(line);}}
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);
        new Apple.Builder(a).setTitle("她说过的自己").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("全部清空",(d,w)->confirm(a,"清空她说过的自己？","她之前说过的喜好和习惯会全部忘掉，之后可能说出和以前不一样的话。你的记忆和聊天记录不受影响。",()->{AgentRuntime.cancel();AgentRuntime.store().clearSelf();})).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}
    /** Her diary, newest first; her own summaries of past days, clearable as a whole. */
    static void diary(MainActivity a){try{
        JSONArray rows=Diary.entries(AgentRuntime.store().snapshot());LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(18));
        GroupedList.note(l,"她每天最多写一篇，概括前一天你们聊过的事。这是她自己的概括，可能不准；聊天时以你的原话和记忆为准。忘记记忆或清空聊天时，相关的日记会一起删掉。");
        if(rows.length()==0){TextView empty=a.text(AgentRuntime.diaryEnabled()?"还没有日记哦。聊过一天之后，下次聊天时她会写下前一天的。":"陪伴日记关着呢，可以在陪伴设置里打开。",15);empty.setTextColor(Apple.SECONDARY);empty.setPadding(a.dp(6),a.dp(16),a.dp(6),a.dp(8));l.addView(empty);}
        else{LinearLayout pages=GroupedList.group(l);for(int i=rows.length()-1;i>=0;i--){JSONObject r=rows.getJSONObject(i);GroupedList.divider(pages);LinearLayout page=a.column();page.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(13));TextView date=a.text(r.optString("date"),12);date.setTextColor(Apple.SECONDARY);page.addView(date);TextView text=a.text(r.optString("text"),15);text.setLineSpacing(a.dp(3),1);text.setPadding(0,a.dp(6),0,0);page.addView(text);pages.addView(page);}}
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);
        new Apple.Builder(a).setTitle("她的日记").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("全部清空",(d,w)->confirm(a,"清空她的日记？","已经写下的日记会全部删除，聊天记录和记忆还在。之后她只会给最近几天补写日记。",()->AgentRuntime.store().clearDiary())).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}
    /** Her understanding of how to be with you, each with the words it came from; deletable one by one. */
    static void understanding(MainActivity a){try{
        JSONArray rows=Understanding.entries(AgentRuntime.store().snapshot());LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(18));final AlertDialog[] dialog=new AlertDialog[1];
        GroupedList.note(l,"这是她从你们的聊天里慢慢摸索出来的相处方式，只用来拿捏说话的分寸和时机，不会当成关于你的事实。每一条都附着你当时的原话；说得不对就点开删掉，之后的聊天也能改变它。忘记相关记忆或清空聊天时会一起删掉。");
        if(rows.length()==0){TextView empty=a.text(AgentRuntime.diaryEnabled()?"还没有哦。她写陪伴日记的时候，会顺便想想怎样和你相处更舒服。":"陪伴日记关着呢。打开后，她会在写日记时慢慢形成对你的了解。",15);empty.setTextColor(Apple.SECONDARY);empty.setPadding(a.dp(6),a.dp(16),a.dp(6),a.dp(8));l.addView(empty);}
        else{LinearLayout list=GroupedList.group(l);for(int i=rows.length()-1;i>=0;i--){JSONObject u=rows.getJSONObject(i);JSONArray ev=u.optJSONArray("evidence");JSONObject last=ev==null||ev.length()==0?null:ev.optJSONObject(ev.length()-1);
            String basis=last==null?"":"\n依据："+"「"+last.optString("quote")+"」 · "+last.optString("date")+(u.optInt("confirmed",1)>1?" · 印证 "+u.optInt("confirmed",1)+" 天":"");
            Button row=GroupedList.entry(list,u.optString("text")+basis,v->new Apple.Builder(a).setMessage("删掉这条了解？她之后不会再按它来拿捏分寸。").setNegativeButton("留着",null).setPositiveButton("删掉",(d,w)->Chat.store(a,()->AgentRuntime.store().forgetUnderstanding(u.optString("id")),()->{dialog[0].dismiss();understanding(a);},e->AgentPanel.fail(a,e))).show());row.setMaxLines(6);}}
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);
        dialog[0]=new Apple.Builder(a).setTitle("她对你的了解").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("全部清空",(d,w)->confirm(a,"清空她对你的了解？","这些相处理解清空后无法恢复，之后可从新的日记重新形成。",()->AgentRuntime.store().clearUnderstanding())).show();
    }catch(Exception e){AgentPanel.fail(a,e);}}

    /** Clearing a whole list cannot be undone, so it always asks once more. */
    static void confirm(MainActivity a,String title,String message,Chat.Change action){new Apple.Builder(a).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认清空",(d,w)->Chat.store(a,action,()->Toast.makeText(a,"已清空",Toast.LENGTH_SHORT).show(),e->AgentPanel.fail(a,e))).show();}
    static void historicalEntry(MainActivity a,JSONObject r){
        String at=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(r.optLong("at")));
        new Apple.Builder(a).setTitle("历史记忆").setMessage(r.optString("text")+"\n\n记录于 "+at+"\n这条记录已过期或被更新，不作为当前事实。").setNegativeButton("关闭",null).setPositiveButton("忘记相关版本",(d,w)->new Apple.Builder(a).setMessage("忘记这件事的当前与历史版本，以及直接来源对话？").setNegativeButton("取消",null).setPositiveButton("忘记",(dd,ww)->{AgentRuntime.cancel();Chat.store(a,()->AgentRuntime.store().forget(r.optString("id")),()->history(a),e->AgentPanel.fail(a,e));}).show()).show();
    }
}
