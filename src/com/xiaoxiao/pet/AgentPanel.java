package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import org.json.*;

final class AgentPanel {
 static void fail(MainActivity a,Exception e){Toast.makeText(a,e.getMessage()==null?"保存失败，请重试":e.getMessage(),Toast.LENGTH_LONG).show();}
 static void settings(MainActivity a){
  LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(16));GroupedList.note(l,"按你舒服的节奏来。她主动开口时也会用到你配置的模型额度。");
  GroupedList.toggle(GroupedList.group(l),"记住重要的事",AgentRuntime.rememberEnabled(),(v,on)->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.memory",on).apply();});
  GroupedList.note(l,"开着时，她会从你的原话里记下喜好和约定。关掉后不再自动记下或主动翻找旧事，已有记忆仍可在手账里删。你明确问某段时间聊过什么时，仍会按请求查本机聊天记录。最近的对话照样用来接话。");
  GroupedList.toggle(GroupedList.group(l),"偶尔主动和我说话",Data.prefs().getBoolean("agent.proactive",false),(v,on)->{if(!on)AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.proactive",on).apply();});
  GroupedList.note(l,"她会结合你们的聊天、点击拖动、记忆和自己的性格，决定要不要开口、说什么、多久再考虑。没有固定每日次数；两次考虑至少隔2分钟，避免刷屏。只有亮屏解锁、她展开在屏幕上、你们没正在聊天时才会考虑；系统勿扰和安静陪伴优先。每次考虑都会使用模型额度，即使她决定先不说话。");
  GroupedList.toggle(GroupedList.group(l),"夜间安静（23:00–08:00）",Data.prefs().getBoolean("agent.quietNight",false),(v,on)->{Data.prefs().edit().putBoolean("agent.quietNight",on).apply();if(on)AgentRuntime.interruptProactive();});
  GroupedList.note(l,"夜间是否固定安静由你选择；不开启时，她仍会参考当前时间和人格自行决定。下一次考虑时间不是保证发消息的时间。");
  GroupedList.toggle(GroupedList.group(l),"写陪伴日记",AgentRuntime.diaryEnabled(),(v,on)->Data.prefs().edit().putBoolean("agent.diary",on).apply());
  GroupedList.note(l,"每天最多一次模型请求，把前一天的聊天写成她的一两句日记，好记得「我们上周聊过什么」。这是她自己的概括，可能不准；在记忆手账里可以查看和清空。");
  GroupedList.toggle(GroupedList.group(l),"流式接收（不支持时关闭）",Data.prefs().getBoolean("agent.stream",true),(v,on)->Data.prefs().edit().putBoolean("agent.stream",on).apply());
  GroupedList.section(l,"安静一会儿");LinearLayout quiet=GroupedList.group(l);
  GroupedList.row(quiet,"安静陪伴 1 小时",GroupedList.MOON,GroupedList.VIOLET,v->{Data.prefs().edit().putLong("agent.focusUntil",System.currentTimeMillis()+3600000).apply();AgentRuntime.cancel();Toast.makeText(a,"接下来一小时，她只静静陪着你",0).show();});
  GroupedList.row(quiet,"结束安静陪伴",GroupedList.CHAT,GroupedList.MINT,v->{Data.prefs().edit().putLong("agent.focusUntil",0).apply();Toast.makeText(a,"她又可以找你说话啦",0).show();});
  GroupedList.section(l,"更多");LinearLayout more=GroupedList.group(l);
  GroupedList.row(more,"记忆引擎",GroupedList.BOOK,GroupedList.ORANGE,v->MemoryPanel.settings(a));
  GroupedList.row(more,"切换明暗外观 · 现在是"+Apple.modeLabel(),GroupedList.MOON,GroupedList.GREY,v->{Data.prefs().edit().putString("appearance.mode",Apple.dark?"day":"night").apply();Apple.load();a.render();});
  GroupedList.row(more,"手机悬浮权限",GroupedList.PHONE,GroupedList.SKY,v->PermissionsPanel.show(a));
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("陪伴设置").setView(scroll).setPositiveButton("完成",null).show();
 }
 static final int PAGE=60;
 static void memories(MainActivity a){memories(a,PAGE);}
 /** Newest memories first, a page at a time: up to 500 rows would make one dialog sluggish. */
 static void memories(MainActivity a,int shown){try{
  JSONObject snapshot=AgentRuntime.store().snapshot();JSONArray entries=snapshot.getJSONArray("memories");LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(18));
  GroupedList.note(l,"她记得的，都是你亲口说过的事。点开可以修改、固定或忘记；忘记时，那一轮原话也会一起删掉。");
  if(snapshot.optBoolean("recovered")){TextView warn=GroupedList.note(l,"上次数据出了点状况，已经从本机备份恢复。建议导出一份手账。");warn.setTextColor(Apple.DANGER);}
  GroupedList.note(l,snapshot.optString("memoryStatus",MemoryBridge.lastStatus));
  final AlertDialog[] dialog=new AlertDialog[1];
  LinearLayout story=GroupedList.group(l);GroupedList.row(story,"共同经历与记忆变化",GroupedList.CLOCK,GroupedList.TEAL,v->MemoryPanel.history(a));GroupedList.row(story,"最近互动",GroupedList.CHAT,GroupedList.SKY,v->MemoryPanel.interactions(a));GroupedList.row(story,"她说过的自己",GroupedList.HEART,GroupedList.PINK,v->MemoryPanel.self(a));GroupedList.row(story,"她的日记",GroupedList.BOOK,GroupedList.VIOLET,v->MemoryPanel.diary(a));GroupedList.row(story,"她对你的了解",GroupedList.TAG,GroupedList.ORANGE,v->MemoryPanel.understanding(a));
  GroupedList.section(l,"她记得的事 · "+entries.length()+" 条");
  if(entries.length()==0){TextView empty=a.text("这里还空空的。\n聊天时说一句「记住：我喜欢下雨天」试试。",15);empty.setTextColor(Apple.SECONDARY);empty.setGravity(android.view.Gravity.CENTER);empty.setLineSpacing(a.dp(4),1);empty.setPadding(0,a.dp(22),0,a.dp(22));empty.setBackground(Apple.shape(a,Apple.FILL,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=a.dp(10);l.addView(empty,p);}
  else{LinearLayout list=GroupedList.group(l);for(int i=entries.length()-1;i>=Math.max(0,entries.length()-shown);i--){JSONObject m=entries.getJSONObject(i);String id=m.getString("id");GroupedList.entry(list,(m.optBoolean("pinned")?"★ ":"")+m.getString("text"),v->{dialog[0].dismiss();edit(a,m,id);});}
   if(entries.length()>shown){Button more=GroupedList.row(list,"再显示 "+Math.min(PAGE,entries.length()-shown)+" 条（共 "+entries.length()+" 条）",v->{dialog[0].dismiss();memories(a,shown+PAGE);});more.setTextColor(Apple.BLUE);}}
  LinearLayout actions=GroupedList.group(l);
  GroupedList.row(actions,"写下一条记忆",GroupedList.PLUS,GroupedList.SKY,v->{dialog[0].dismiss();edit(a,null,null);});
  GroupedList.row(actions,"导出手账与对话",GroupedList.SHARE,GroupedList.MINT,v->a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"大肥鱼-手账.json"),42));
  LinearLayout danger=GroupedList.group(l);GroupedList.row(danger,"清空聊天记录，保留记忆",GroupedList.CHAT,GroupedList.GREY,v->confirmClear(a,false,dialog[0]));Button reset=GroupedList.row(danger,"忘记全部对话和记忆",GroupedList.TRASH,GroupedList.RED,v->confirmClear(a,true,dialog[0]));reset.setTextColor(Apple.DANGER);
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);dialog[0]=new Apple.Builder(a).setTitle("记忆手账").setView(scroll).setPositiveButton("合上手账",null).show();
 }catch(Exception e){fail(a,e);}}
 static void confirmClear(MainActivity a,boolean all,Dialog parent){new Apple.Builder(a).setTitle(all?"真的要全部忘掉吗？":"清空聊天记录？").setMessage(all?"会停下当前回复，清除这台手机上的对话、记忆和情绪记录。模型连接和她的性格会保留，已导出的文件不受影响。":"会停下当前回复并清除这台手机上的对话，手账里的记忆还在。").setNegativeButton("取消",null).setPositiveButton("确认清除",(d,w)->{try{Chat.interruptPresentation("main");AgentRuntime.cancel();AgentRuntime.store().clear(all);parent.dismiss();memories(a);}catch(Exception e){fail(a,e);}}).show();}
 static void edit(MainActivity a,JSONObject memory,String id){
  LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(12));EditText text=Chat.field(a,l,"一件想让她一直记得的事",memory==null?"":memory.optString("text"),false);text.setSingleLine(false);text.setMinLines(3);text.setMaxLines(8);text.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});Switch pinned=new Switch(a);pinned.setText("固定这条记忆（每次聊天都带上）");pinned.setTextColor(Apple.INK);pinned.setTextSize(14);pinned.setMinHeight(a.dp(52));pinned.setChecked(memory==null||memory.optBoolean("pinned"));l.addView(pinned);
  if(memory!=null){String source=memory.optString("source");TextView details=a.text("记录于 "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(memory.optLong("at")))+"\n来源："+(source.startsWith("你")?source:"你的对话原话"),12);details.setTextColor(Apple.SECONDARY);l.addView(details);}
  AlertDialog dialog=new Apple.Builder(a).setTitle(id==null?"写下记忆":"这条记忆").setView(l).setNegativeButton("取消",(d,w)->memories(a)).setPositiveButton("保存",null).setNeutralButton(id==null?"":"忘记",null).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{AgentRuntime.cancel();if(id==null)AgentRuntime.store().remember(text.getText().toString().trim(),pinned.isChecked());else AgentRuntime.store().editMemory(id,text.getText().toString().trim(),pinned.isChecked());dialog.dismiss();memories(a);}catch(Exception e){fail(a,e);}});
  if(id==null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new Apple.Builder(a).setMessage("忘记这条记忆及其直接来源对话？").setNegativeButton("取消",null).setPositiveButton("忘记",(d,w)->{try{AgentRuntime.cancel();AgentRuntime.store().forget(id);dialog.dismiss();memories(a);}catch(Exception e){fail(a,e);}}).show());
 }
}
