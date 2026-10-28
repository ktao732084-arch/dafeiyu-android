package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import org.json.*;

final class AgentPanel {
 static void fail(MainActivity a,Exception e){Toast.makeText(a,e.getMessage()==null?"保存失败，请重试":e.getMessage(),Toast.LENGTH_LONG).show();}
 static void settings(MainActivity a){
  LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(16));TextView note=a.text("让她以你舒服的节奏陪伴。主动聊天会使用你配置的模型额度。",14);note.setTextColor(Apple.SECONDARY);l.addView(note);
  EditText nickname=Chat.field(a,l,"她怎么称呼你（可留空）",Data.prefs().getString("agent.nickname",""),false);nickname.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
  GroupedList.toggle(l,"记住重要的事",AgentRuntime.rememberEnabled(),(v,on)->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.memory",on).apply();});
  TextView memory=a.text("开启后可从你的原话记住偏好与约定。关闭后不新增、不检索长期记忆，已有记忆仍可在手账删除。最近对话仍用于接续聊天。",12);memory.setTextColor(Apple.SECONDARY);l.addView(memory);
  GroupedList.toggle(l,"偶尔主动和我说话",Data.prefs().getBoolean("agent.proactive",false),(v,on)->{if(!on)AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.proactive",on).apply();});
  TextView proactive=a.text("只在亮屏、桌宠可见且没有聊天时触发；聊过之后至少等30分钟，两次请求至少间隔90分钟，每天最多3次。23:00–08:00和系统勿扰时保持安静。不会在后台刷网页。",12);proactive.setTextColor(Apple.SECONDARY);l.addView(proactive);
  GroupedList.toggle(l,"流式接收（不支持时关闭）",Data.prefs().getBoolean("agent.stream",true),(v,on)->Data.prefs().edit().putBoolean("agent.stream",on).apply());
  a.button(l,"记忆引擎",v->MemoryPanel.settings(a));
  a.button(l,"安静陪伴 1 小时",v->{Data.prefs().edit().putLong("agent.focusUntil",System.currentTimeMillis()+3600000).apply();AgentRuntime.cancel();Toast.makeText(a,"这一小时她不会主动开口",0).show();});a.button(l,"结束安静陪伴",v->{Data.prefs().edit().putLong("agent.focusUntil",0).apply();Toast.makeText(a,"恢复原有陪伴设置",0).show();});
  a.button(l,"切换明暗外观",v->{Data.prefs().edit().putString("appearance.mode",Apple.dark?"day":"night").apply();Apple.load();a.render();});a.button(l,"手机悬浮权限",v->PermissionsPanel.show(a));
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("陪伴设置").setView(scroll).setPositiveButton("完成",(d,w)->Data.prefs().edit().putString("agent.nickname",nickname.getText().toString().trim()).apply()).show();
 }
 static final int PAGE=60;
 static void memories(MainActivity a){memories(a,PAGE);}
 /** Newest memories first, a page at a time: up to 500 rows would make one dialog sluggish. */
 static void memories(MainActivity a,int shown){try{
  JSONObject snapshot=AgentRuntime.store().snapshot();JSONArray entries=snapshot.getJSONArray("memories");LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));TextView info=a.text("她记得的，是你亲口说过的事。\n点开可修改、固定或忘记。删除也会移除直接来源的那轮对话；其他对话如有重复提及，可清空聊天记录。",13);info.setTextColor(Apple.SECONDARY);l.addView(info);
  if(snapshot.optBoolean("recovered"))l.addView(a.text("上次数据异常，已从本地备份恢复。建议导出手账。",13));
  l.addView(a.text(snapshot.optString("memoryStatus",MemoryBridge.lastStatus),12));a.button(l,"共同经历与记忆变化",v->MemoryPanel.history(a));a.button(l,"她说过的自己",v->MemoryPanel.self(a));
  if(entries.length()==0){TextView empty=a.text("还没有写下共同记忆。\n试着说：记住：我喜欢下雨天。",17);empty.setPadding(0,a.dp(24),0,a.dp(24));l.addView(empty);}
  final AlertDialog[] dialog=new AlertDialog[1];for(int i=entries.length()-1;i>=Math.max(0,entries.length()-shown);i--){JSONObject m=entries.getJSONObject(i);String id=m.getString("id");Button row=a.button(l,(m.optBoolean("pinned")?"★ ":"")+m.getString("text"),v->{dialog[0].dismiss();edit(a,m,id);});row.setSingleLine(false);row.setMaxLines(3);row.getLayoutParams().height=a.dp(84);}
  if(entries.length()>shown)a.button(l,"再显示 "+Math.min(PAGE,entries.length()-shown)+" 条（共 "+entries.length()+" 条）",v->{dialog[0].dismiss();memories(a,shown+PAGE);});
  a.button(l,"写下一条记忆",v->{dialog[0].dismiss();edit(a,null,null);});a.button(l,"导出手账与对话",v->a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"大肥鱼-手账.json"),42));a.button(l,"清空聊天记录，保留记忆",v->confirmClear(a,false,dialog[0]));Button reset=a.button(l,"忘记全部对话和记忆",v->confirmClear(a,true,dialog[0]));reset.setTextColor(Apple.DANGER);
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);dialog[0]=new Apple.Builder(a).setTitle("记忆手账 · "+entries.length()+" 条").setView(scroll).setPositiveButton("关闭",null).show();
 }catch(Exception e){fail(a,e);}}
 static void confirmClear(MainActivity a,boolean all,Dialog parent){new Apple.Builder(a).setTitle(all?"忘记全部？":"清空聊天记录？").setMessage(all?"将停止当前回复，清除本机对话、记忆和情绪记录。模型连接和人格保留。已导出的文件不受影响。":"将停止当前回复并清除本机对话，手账中的记忆保留。").setNegativeButton("取消",null).setPositiveButton("确认清除",(d,w)->{try{Chat.interruptPresentation("main");AgentRuntime.cancel();AgentRuntime.store().clear(all);parent.dismiss();memories(a);}catch(Exception e){fail(a,e);}}).show();}
 static void edit(MainActivity a,JSONObject memory,String id){
  LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(12));EditText text=Chat.field(a,l,"一件想让她记得的事",memory==null?"":memory.optString("text"),false);text.setSingleLine(false);text.setMinLines(3);text.setMaxLines(8);text.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});Switch pinned=new Switch(a);pinned.setText("固定这条记忆");pinned.setTextColor(Apple.INK);pinned.setChecked(memory==null||memory.optBoolean("pinned"));l.addView(pinned);
  if(memory!=null){String source=memory.optString("source");TextView details=a.text("记录于 "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(memory.optLong("at")))+"\n来源："+(source.startsWith("你")?source:"你的对话原话"),12);details.setTextColor(Apple.SECONDARY);l.addView(details);}
  AlertDialog dialog=new Apple.Builder(a).setTitle(id==null?"写下记忆":"这条记忆").setView(l).setNegativeButton("取消",(d,w)->memories(a)).setPositiveButton("保存",null).setNeutralButton(id==null?"":"忘记",null).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{AgentRuntime.cancel();if(id==null)AgentRuntime.store().remember(text.getText().toString().trim(),pinned.isChecked());else AgentRuntime.store().editMemory(id,text.getText().toString().trim(),pinned.isChecked());dialog.dismiss();memories(a);}catch(Exception e){fail(a,e);}});
  if(id==null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new Apple.Builder(a).setMessage("忘记这条记忆及其直接来源对话？").setNegativeButton("取消",null).setPositiveButton("忘记",(d,w)->{try{AgentRuntime.cancel();AgentRuntime.store().forget(id);dialog.dismiss();memories(a);}catch(Exception e){fail(a,e);}}).show());
 }
}
