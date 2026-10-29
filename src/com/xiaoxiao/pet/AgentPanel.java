package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import java.util.*;import android.view.*;import android.widget.*;import org.json.*;

final class AgentPanel {
 static void fail(MainActivity a,Exception e){Toast.makeText(a,e.getMessage()==null?"保存失败，请重试":e.getMessage(),Toast.LENGTH_LONG).show();}
 static void settings(MainActivity a){
  LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(16));GroupedList.note(l,"按你舒服的节奏来。她主动开口时也会用到你配置的模型额度。");
  GroupedList.toggle(GroupedList.group(l),"记住重要的事",AgentRuntime.rememberEnabled(),(v,on)->{AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.memory",on).apply();});
  GroupedList.note(l,"她会从你的原话里记下喜好和约定。关掉后不再自动记下或翻找旧事；你问某段时间聊过什么时，仍会查本机聊天记录。");
  final TextView[] hint=new TextView[1];
  GroupedList.toggle(GroupedList.group(l),"偶尔主动和我说话",Data.prefs().getBoolean("agent.proactive",false),(v,on)->{if(!on)AgentRuntime.cancel();Data.prefs().edit().putBoolean("agent.proactive",on).apply();proactiveHint(hint[0]);});
  hint[0]=GroupedList.note(l,"");hint[0].setTextColor(Apple.BLUE);proactiveHint(hint[0]);
  // The cost stays in plain sight; the full rules sit one tap away.
  TextView how=GroupedList.note(l,"她自己决定要不要开口、说什么、隔多久再想想，没有每日次数。每次考虑都会用模型额度，即使最后没说话。了解更多 ›");
  how.setOnClickListener(v->new Apple.Builder(a).setTitle("主动陪伴怎么运作").setMessage("她会结合你们的聊天、点击拖动、记忆和自己的性格，决定要不要开口、说什么、多久再考虑。\n\n你说在忙、等你回来时，她会真正暂停主动消息；你说忙完了再重新安排。模型遗漏时间时最多补问一次，仍失败则暂停并在最近互动说明，不暗中默认15分钟。\n\n没有固定每日次数；两次考虑至少隔2分钟，避免刷屏。\n\n只有亮屏解锁、她展开在屏幕上、你们没正在聊天时才会考虑；系统勿扰和安静陪伴优先。\n\n每次考虑都会使用模型额度，即使她决定先不说话。下一次考虑时间不是保证发消息的时间。").setPositiveButton("知道了",null).show());
  GroupedList.toggle(GroupedList.group(l),"夜间安静（23:00–08:00）",Data.prefs().getBoolean("agent.quietNight",false),(v,on)->{Data.prefs().edit().putBoolean("agent.quietNight",on).apply();if(on)AgentRuntime.interruptProactive();proactiveHint(hint[0]);});
  GroupedList.note(l,"不开时，她会参考时间和自己的性格决定。");
  GroupedList.toggle(GroupedList.group(l),"写陪伴日记",AgentRuntime.diaryEnabled(),(v,on)->Data.prefs().edit().putBoolean("agent.diary",on).apply());
  GroupedList.note(l,"每天最多一次模型请求，把前一天的聊天写成一两句日记。是她自己的概括，可能不准；可在记忆手账里查看和清空。");
  GroupedList.section(l,"安静一会儿");LinearLayout quiet=GroupedList.group(l);
  // One row that shows whether quiet time is on and until when, instead of two rows that both always look available.
  Button focus=GroupedList.row(quiet,focusLabel(),GroupedList.MOON,GroupedList.VIOLET,null);
  focus.setOnClickListener(v->{if(focusUntil()>0){Data.prefs().edit().putLong("agent.focusUntil",0).apply();Toast.makeText(a,"她又可以找你说话啦",0).show();}else{Data.prefs().edit().putLong("agent.focusUntil",System.currentTimeMillis()+3600000).apply();AgentRuntime.cancel();Toast.makeText(a,"接下来一小时，她只静静陪着你",0).show();}focus.setText(focusLabel());proactiveHint(hint[0]);});
  GroupedList.section(l,"更多");LinearLayout more=GroupedList.group(l);
  GroupedList.row(more,"记忆引擎",GroupedList.BOOK,GroupedList.ORANGE,v->MemoryPanel.settings(a));
  GroupedList.row(more,"切换明暗外观 · 现在是"+Apple.modeLabel(),GroupedList.MOON,GroupedList.GREY,v->{Data.prefs().edit().putString("appearance.mode",Apple.dark?"day":"night").apply();Apple.load();a.render();});
  GroupedList.row(more,"手机悬浮权限",GroupedList.PHONE,GroupedList.SKY,v->PermissionsPanel.show(a));
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("陪伴设置").setView(scroll).setPositiveButton("完成",null).show();
 }
 /** The end of the current quiet hour, or 0 when it is not on. */
 static long focusUntil(){long until=Data.prefs().getLong("agent.focusUntil",0);return until>System.currentTimeMillis()?until:0;}
 static String clock(long at){return new java.text.SimpleDateFormat("HH:mm",java.util.Locale.CHINA).format(new java.util.Date(at));}
 static String focusLabel(){long until=focusUntil();return until>0?"结束安静陪伴（到 "+clock(until)+"）":"安静陪伴 1 小时";}
 /**
  * Proactive chat silently waits for several conditions; turning it on and hearing nothing looks broken.
  * Says which one is missing right now, or when she will next consider talking. Display only: no limits added.
  */
 static void proactiveHint(TextView hint){
  if(hint==null)return;String text="";
  if(Data.prefs().getBoolean("agent.proactive",false)){
   PetService service=PetService.instance;PetService.Pet pet=service==null?null:service.pets.get("main");long until=focusUntil();
   if(!AgentRuntime.configured())text="现在还不会开口：模型还没接通，先去首页的「模型连接」。";
   else if(pet==null||!pet.visible)text="现在还不会开口：她要先住到屏幕上（首页「让她住到屏幕上」）。";
   else if(pet.docked)text="现在还不会开口：她收在屏幕边上，把她拉出来后才会考虑。";
   else if(until>0)text="安静陪伴中，"+clock(until)+" 以后才会考虑开口。";
   else if(AgentRuntime.nightQuiet())text="夜间安静中，08:00 以后才会考虑开口。";
   else{text="已开启。回到桌面后，她会自己决定什么时候开口。";
    try{org.json.JSONObject state=AgentRuntime.store().snapshot();String status=Initiative.status(state);if(!status.isEmpty())text+="\n"+status+"。";if(state.has("proactiveNextAt")){long next=state.optLong("proactiveNextAt");if(next>System.currentTimeMillis())text+="下次考虑约在 "+clock(next)+"。";}}catch(Exception ignored){}}
  }
  hint.setText(text);hint.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
 }
 static final int PAGE=60;
 /** What the notebook is showing: a search text, which kind of memory, and how many rows. Kept across edits. */
 static final class Notebook {String query="";int kind=MemoryLedger.ALL,shown=PAGE;}
 static void memories(MainActivity a){memories(a,new Notebook());}
 /** Newest memories first, a page at a time (up to 500 rows would make one dialog sluggish), searchable and filterable in place. */
 static void memories(MainActivity a,Notebook view){try{
  JSONObject snapshot=AgentRuntime.store().snapshot();JSONArray entries=snapshot.getJSONArray("memories");LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(18));
  GroupedList.note(l,"她记得的，都是你亲口说过的事。点开可以修改、固定或忘记；忘记时，那一轮原话也会一起删掉。");
  if(snapshot.optBoolean("recovered")){TextView warn=GroupedList.note(l,"上次数据出了点状况，已经从本机备份恢复。建议导出一份手账。");warn.setTextColor(Apple.DANGER);}
  GroupedList.note(l,snapshot.optString("memoryStatus",MemoryBridge.lastStatus));
  final AlertDialog[] dialog=new AlertDialog[1];
  LinearLayout story=GroupedList.group(l);GroupedList.row(story,"共同经历与记忆变化",GroupedList.CLOCK,GroupedList.TEAL,v->MemoryPanel.history(a));GroupedList.row(story,"最近互动",GroupedList.CHAT,GroupedList.SKY,v->MemoryPanel.interactions(a));GroupedList.row(story,"她说过的自己",GroupedList.HEART,GroupedList.PINK,v->MemoryPanel.self(a));GroupedList.row(story,"她的日记",GroupedList.BOOK,GroupedList.VIOLET,v->MemoryPanel.diary(a));
  TextView count=GroupedList.section(l,"她记得的事 · "+entries.length()+" 条");
  LinearLayout holder=a.column();final JSONArray[] list={entries};Runnable[] fill=new Runnable[1];
  // The editor opens on top of the notebook; after a save or forget the list refreshes in place.
  Runnable reload=()->{try{JSONArray now=AgentRuntime.store().snapshot().getJSONArray("memories");
    if(fill[0]==null||now.length()==0){dialog[0].dismiss();memories(a,view);return;}
    list[0]=now;count.setText("她记得的事 · "+now.length()+" 条");fill[0].run();}catch(Exception e){fail(a,e);}};
  if(entries.length()>0){
   EditText search=Chat.field(a,l,"搜索记忆",view.query,false);search.setContentDescription("搜索记忆");((LinearLayout.LayoutParams)search.getLayoutParams()).topMargin=a.dp(10);
   LinearLayout kinds=new LinearLayout(a);LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,a.dp(44));row.topMargin=a.dp(8);l.addView(kinds,row);
   Button[] chips=new Button[3];String[] names={"全部","我写的","自动记下"};
   for(int k=0;k<3;k++){final int kind=k;Button chip=new Button(a);chip.setText(names[k]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(k>0)p.leftMargin=a.dp(6);kinds.addView(chip,p);chips[k]=chip;
    chip.setOnClickListener(v->{view.kind=kind;view.shown=PAGE;fill[0].run();});}
   fill[0]=()->{for(int k=0;k<3;k++){Apple.button(chips[k],k==view.kind);chips[k].setTextSize(13);chips[k].setPadding(a.dp(4),0,a.dp(4),0);chips[k].setSingleLine(true);chips[k].setContentDescription(names[k]+(k==view.kind?"，已选中":""));}fillMemories(a,holder,list[0],view,reload);};
   search.addTextChangedListener(new ChatPage.Watcher(()->{view.query=search.getText().toString();view.shown=PAGE;fill[0].run();}));
   fill[0].run();
  }else{
   if(entries.length()==0){TextView empty=a.text(AgentRuntime.rememberEnabled()?"这里还空空的。\n聊天时她会从你的原话里记下喜好和约定，\n也可以直接说「记住：我喜欢下雨天」。":"这里还空空的。\n「记住重要的事」关着，可以在陪伴设置里打开。",15);empty.setTextColor(Apple.SECONDARY);empty.setGravity(android.view.Gravity.CENTER);empty.setLineSpacing(a.dp(4),1);empty.setPadding(0,a.dp(22),0,a.dp(22));empty.setBackground(Apple.shape(a,Apple.FILL,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=a.dp(10);holder.addView(empty,p);}
  }
  l.addView(holder);
  LinearLayout actions=GroupedList.group(l);
  GroupedList.row(actions,"写下一条记忆",GroupedList.PLUS,GroupedList.SKY,v->edit(a,null,null,reload));
  GroupedList.row(actions,"导出手账与对话",GroupedList.SHARE,GroupedList.MINT,v->a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"大肥鱼-手账.json"),42));
  LinearLayout danger=GroupedList.group(l);GroupedList.row(danger,"清空聊天记录，保留记忆",GroupedList.CHAT,GroupedList.GREY,v->confirmClear(a,false,dialog[0]));Button reset=GroupedList.row(danger,"忘记全部对话和记忆",GroupedList.TRASH,GroupedList.RED,v->confirmClear(a,true,dialog[0]));reset.setTextColor(Apple.DANGER);
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);dialog[0]=new Apple.Builder(a).setTitle("记忆手账").setView(scroll).setPositiveButton("合上手账",null).show();
 }catch(Exception e){fail(a,e);}}
 /** The rows matching the notebook's search and kind, with a "more" row that grows the list in place. */
 static void fillMemories(MainActivity a,LinearLayout holder,JSONArray entries,Notebook view,Runnable reload){
  holder.removeAllViews();List<JSONObject> rows=MemoryLedger.browse(entries,view.query,view.kind);
  if(rows.isEmpty()){TextView none=GroupedList.note(holder,view.query.trim().isEmpty()?"这一类还没有记忆。":"没有找到包含「"+view.query.trim()+"」的记忆。");none.setPadding(a.dp(6),a.dp(14),a.dp(6),a.dp(6));return;}
  LinearLayout list=GroupedList.group(holder);
  for(int i=0;i<Math.min(view.shown,rows.size());i++){JSONObject m=rows.get(i);String id=m.optString("id");GroupedList.entry(list,(m.optBoolean("pinned")?"★ ":"")+m.optString("text"),v->edit(a,m,id,reload));}
  if(rows.size()>view.shown){Button more=GroupedList.row(list,"再显示 "+Math.min(PAGE,rows.size()-view.shown)+" 条（共 "+rows.size()+" 条）",v->{view.shown+=PAGE;fillMemories(a,holder,entries,view,reload);});more.setTextColor(Apple.BLUE);}
 }
 static void confirmClear(MainActivity a,boolean all,Dialog parent){new Apple.Builder(a).setTitle(all?"真的要全部忘掉吗？":"清空聊天记录？").setMessage(all?"会停下当前回复，清除这台手机上的对话、记忆和情绪记录。模型连接和她的性格会保留，已导出的文件不受影响。":"会停下当前回复并清除这台手机上的对话，手账里的记忆还在。").setNegativeButton("取消",null).setPositiveButton("确认清除",(d,w)->{Chat.interruptPresentation("main");AgentRuntime.cancel();Chat.store(a,()->AgentRuntime.store().clear(all),()->{parent.dismiss();memories(a);},e->fail(a,e));}).show();}
 static void edit(MainActivity a,JSONObject memory,String id,Runnable saved){
  LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(12));EditText text=Chat.field(a,l,"一件想让她一直记得的事",memory==null?"":memory.optString("text"),false);text.setSingleLine(false);text.setMinLines(3);text.setMaxLines(8);text.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});Switch pinned=new Switch(a);pinned.setText("固定这条记忆（每次聊天都带上）");pinned.setTextColor(Apple.INK);pinned.setTextSize(14);pinned.setMinHeight(a.dp(52));pinned.setChecked(memory==null||memory.optBoolean("pinned"));l.addView(pinned);
  if(memory!=null){String source=memory.optString("source");TextView details=a.text("记录于 "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(memory.optLong("at")))+"\n来源："+(source.startsWith("你")?source:"你的对话原话"),12);details.setTextColor(Apple.SECONDARY);l.addView(details);}
  AlertDialog dialog=new Apple.Builder(a).setTitle(id==null?"写下记忆":"这条记忆").setView(l).setNegativeButton("取消",null).setPositiveButton("保存",null).setNeutralButton(id==null?"":"忘记",null).show();
  // Saving rewrites the state file: done in the background, with the button held so a second tap cannot save twice.
  Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
  save.setOnClickListener(v->{AgentRuntime.cancel();String value=text.getText().toString().trim();boolean pin=pinned.isChecked();save.setEnabled(false);
   Chat.store(a,()->{if(id==null)AgentRuntime.store().remember(value,pin);else AgentRuntime.store().editMemory(id,value,pin);},()->{dialog.dismiss();saved.run();},e->{save.setEnabled(true);fail(a,e);});});
  if(id==null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new Apple.Builder(a).setMessage("忘记这条记忆及其直接来源对话？").setNegativeButton("取消",null).setPositiveButton("忘记",(d,w)->{AgentRuntime.cancel();Chat.store(a,()->AgentRuntime.store().forget(id),()->{dialog.dismiss();saved.run();},e->fail(a,e));}).show());
 }
}
