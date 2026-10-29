package com.xiaoxiao.pet;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.provider.Settings;import android.view.*;import android.widget.*;

public class MainActivity extends Activity {
 static boolean active;boolean updateResumed;LinearLayout content;HeroPet pet;final Handler handler=new Handler(Looper.getMainLooper());
 public void onCreate(Bundle b){Data.init(this);Apple.load();setTheme(Apple.dark?com.dafeiyu.companion.R.style.AppThemeDark:com.dafeiyu.companion.R.style.AppTheme);super.onCreate(b);try{boolean met=PersonaManager.ensureInitialized("main");GrowthStore.quiet();if(!met)handler.post(()->PersonaPanel.show(this,"main"));}catch(Exception e){AgentPanel.fail(this,e);}render();consumeChatIntent();}
 public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);consumeChatIntent();}
 void consumeChatIntent(){if(getIntent()==null)return;if(getIntent().getBooleanExtra("openChat",false)){getIntent().removeExtra("openChat");handler.post(()->openChat());}if(getIntent().getBooleanExtra("openModel",false)){getIntent().removeExtra("openModel");handler.post(()->Chat.settings(this));}if(getIntent().getBooleanExtra("openStory",false)){getIntent().removeExtra("openStory");handler.post(()->GrowthPanel.show(this));}if(getIntent().getBooleanExtra("openUpdate",false)){getIntent().removeExtra("openUpdate");handler.post(()->UpdatePanel.show(this));}if(getIntent().getBooleanExtra("openGomoku",false)){getIntent().removeExtra("openGomoku");handler.post(()->GomokuPage.show(this));}}
 public void onResume(){super.onResume();active=true;updateResumed=true;AgentRuntime.interruptProactive();Apple.load();meet();render();pet.resume();PermissionsPanel.refresh();if(PetService.instance!=null)PetService.instance.control(true);handler.postDelayed(()->{if(updateResumed&&!isFinishing()&&!isDestroyed())UpdateRuntime.enter(this);},1500);}
 public void onPause(){updateResumed=false;if(pet!=null)pet.pause();UpdateRuntime.leave(this);Chat.interruptPresentation("main");active=false;if(PetService.instance!=null)PetService.instance.control(false);super.onPause();}
 public void onDestroy(){DialogLifecycle.close(this);if(pet!=null)pet.release();handler.removeCallbacksAndMessages(null);super.onDestroy();}
 public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);Apple.load();render();}
 int dp(int v){return Apple.dp(this,v);}TextView text(String s,int size){return Apple.label(this,s,size,false);}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 Button button(LinearLayout parent,String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);Apple.button(b,false);b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(8);parent.addView(b,p);return b;}
 LinearLayout panel(){LinearLayout box=column();box.setPadding(dp(18),dp(18),dp(18),dp(18));box.setBackground(Apple.shape(this,Apple.FILL,24));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);content.addView(box,p);return box;}
 String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
 void render(){
  Apple.load();getWindow().setStatusBarColor(Apple.BACK);getWindow().setNavigationBarColor(Apple.BACK);getWindow().getDecorView().setSystemUiVisibility(Apple.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  ScrollView scroll=new SpringScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Apple.BACK);content=column();content.setPadding(dp(20),dp(20),dp(20),dp(32));scroll.addView(content);setContentView(scroll);
  // Title, with her current mood as a small chip.
  LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.BOTTOM);LinearLayout titles=column();TextView eyebrow=text("一只鲸鱼，住进日常",13);eyebrow.setTextColor(Apple.BLUE);titles.addView(eyebrow);TextView title=Apple.label(this,"大肥鱼",30,true);title.setPadding(0,dp(6),0,0);titles.addView(title);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  TextView mood=text("● "+AgentRuntime.moodLabel(),12);mood.setTextColor(Apple.BLUE);mood.setBackground(Apple.shape(this,Apple.TINT,14));mood.setPadding(dp(10),dp(6),dp(10),dp(6));head.addView(mood);content.addView(head);
  // Hero: her (animated) portrait on a soft sky gradient, one primary action and two quiet ones.
  LinearLayout hero=panel();GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,Apple.dark?new int[]{0xff1a2c44,0xff1c1c20}:new int[]{0xffe6f0ff,0xfff7faff});bg.setCornerRadius(dp(24));hero.setBackground(bg);hero.setPadding(dp(18),dp(8),dp(18),dp(18));
  if(pet==null)pet=new HeroPet(this);pet.attach(hero,dp(190));
  TextView name=Apple.label(this,Data.name("main"),20,true);name.setGravity(Gravity.CENTER);hero.addView(name);
  recent(hero);
  Button chat=button(hero,"和她聊聊",v->openChat());Apple.button(chat,true);chat.setTextSize(16);chat.getLayoutParams().height=dp(50);((LinearLayout.LayoutParams)chat.getLayoutParams()).topMargin=dp(12);
  boolean visible=PetService.instance!=null&&PetService.instance.pets.get("main")!=null&&PetService.instance.pets.get("main").visible;
  Button desk=button(hero,visible?"请她回来歇会":"让她住到屏幕上",v->{send(visible?"hideAll":"show","main");handler.postDelayed(this::render,300);});LinearLayout.LayoutParams deskSize=(LinearLayout.LayoutParams)desk.getLayoutParams();deskSize.height=dp(46);deskSize.topMargin=dp(10);desk.setBackground(Apple.shape(this,Apple.PANEL,14));
  boolean steps=firstSteps(visible);
  if(!steps&&!Settings.canDrawOverlays(this)){View hint=PermissionsPanel.hint(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);content.addView(hint,p);}
  // Two groups, each entry once: what is about the two of you, and how the app behaves.
  GroupedList.section(content,"我们");LinearLayout us=GroupedList.group(content);
  GrowthStore growth=PersonaManager.isInitialized("main")?GrowthStore.quiet():null;if(growth!=null)GroupedList.row(us,"我们的故事",GroupedList.WAVE,GroupedList.TEAL,v->GrowthPanel.show(this));
  GroupedList.row(us,"一起下五子棋",GroupedList.PLAY,GroupedList.TEAL,v->GomokuPage.show(this));
  GroupedList.row(us,"记忆手账",GroupedList.BOOK,GroupedList.ORANGE,v->AgentPanel.memories(this));
  GroupedList.row(us,PersonaManager.isInitialized("main")?"她的性格 · "+PersonaManager.label("main"):"初次相遇 · 确定性格",GroupedList.HEART,GroupedList.PINK,v->PersonaPanel.show(this,"main"));
  GroupedList.row(us,"名字与称呼",GroupedList.TAG,GroupedList.VIOLET,v->ProfilePage.show(this,"main"));
  if(growth!=null){org.json.JSONObject g=growth.snapshot();GroupedList.note(content,GrowthPanel.summary(g)+"\n"+GrowthPanel.today(g));}
  GroupedList.section(content,"设置");LinearLayout settings=GroupedList.group(content);
  GroupedList.row(settings,"模型连接",GroupedList.BOLT,GroupedList.SKY,v->Chat.settings(this));
  GroupedList.row(settings,"陪伴设置",GroupedList.SLIDERS,GroupedList.MINT,v->AgentPanel.settings(this));
  GroupedList.row(settings,"桌宠设置",GroupedList.PHONE,GroupedList.TEAL,v->manage());
  GroupedList.row(settings,"外观 · "+Apple.modeLabel(),GroupedList.MOON,GroupedList.GREY,v->{Data.prefs().edit().putString("appearance.mode",Apple.dark?"day":"night").apply();Apple.load();render();});
  GroupedList.note(content,AgentRuntime.configured()?"模型已接通，聊天内容会发往你设置的模型服务商。":"模型还没接通。推荐 DeepSeek：去官网拿一枚 API Key，粘贴进来就能聊。");
  GroupedList.section(content,"关于");LinearLayout more=GroupedList.group(content);GroupedList.row(more,"关于与开源来源",GroupedList.INFO,GroupedList.GREY,v->about());
  GroupedList.row(more,"检查更新",GroupedList.SHARE,GroupedList.SKY,v->UpdatePanel.show(this));
  TextView footer=text("大肥鱼 "+version()+" · 非官方社区二创\n记忆只存在这台手机里",12);footer.setTextColor(Apple.SECONDARY);footer.setGravity(Gravity.CENTER);footer.setLineSpacing(dp(3),1);footer.setPadding(0,dp(28),0,0);content.addView(footer);
 }
 /** Opening the app counts as meeting her today. */
 void meet(){GrowthStore g=PersonaManager.isInitialized("main")?GrowthStore.quiet():null;if(g!=null)try{g.met();}catch(Exception ignored){}}
 /**
  * After the first meeting, the two things still needed — a model so she can talk, and her place on the screen —
  * as a short checklist, until both have been done once or it is dismissed. Returns whether it is shown.
  */
 boolean firstSteps(boolean onScreen){
  android.content.SharedPreferences prefs=Data.prefs();boolean model=AgentRuntime.configured();
  if(model&&onScreen&&!prefs.getBoolean("onboarding.done",false))prefs.edit().putBoolean("onboarding.done",true).apply();
  if(!PersonaManager.isInitialized("main")||prefs.getBoolean("onboarding.done",false))return false;
  GroupedList.section(content,"开始之前还差 "+((model?0:1)+(onScreen?0:1))+" 步");LinearLayout steps=GroupedList.group(content);
  Button connect=GroupedList.row(steps,model?"✓ 已接通模型":"接通模型，让她能开口",GroupedList.BOLT,GroupedList.SKY,v->Chat.settings(this));
  Button place=GroupedList.row(steps,onScreen?"✓ 她已经在桌面上":"把她放到桌面上",GroupedList.PHONE,GroupedList.MINT,v->{send("show","main");handler.postDelayed(this::render,300);});
  if(model)connect.setTextColor(Apple.SECONDARY);if(onScreen)place.setTextColor(Apple.SECONDARY);
  TextView later=GroupedList.note(content,"不再提示");later.setTextColor(Apple.BLUE);later.setOnClickListener(v->{prefs.edit().putBoolean("onboarding.done",true).apply();render();});
  return true;
 }
 /**
  * Under her name: her latest line and when, so the hero doubles as the conversation preview (one way into chat
  * instead of a button plus a separate "recent chat" row); a greeting before you have talked.
  */
 void recent(LinearLayout hero){
  String preview=null,at="";
  try{org.json.JSONArray last=AgentRuntime.store().conversationRange(Long.MIN_VALUE,Long.MAX_VALUE,null,1);if(last.length()>0){org.json.JSONObject t=last.getJSONObject(0);String reply=ReplySegments.visible(t);preview=Chat.displayText(reply.isEmpty()?"你："+t.optString("user"):reply).replace('\n',' ');at=ChatPage.when(t,t.optLong("repliedAt",t.optLong("at")));}}catch(Exception ignored){}
  TextView line=text(preview!=null?preview:PersonaManager.isInitialized("main")?"我在呢，有话就慢慢说。":"先和她打个招呼，决定她的性格吧。",14);line.setTextColor(Apple.SECONDARY);line.setGravity(Gravity.CENTER);line.setPadding(dp(8),dp(8),dp(8),0);
  if(preview!=null){line.setMaxLines(2);line.setEllipsize(android.text.TextUtils.TruncateAt.END);line.setOnClickListener(v->openChat());line.setContentDescription("最近的聊天："+preview+"，点按继续");}
  hero.addView(line);
  if(!at.isEmpty()){TextView when=text(at,12);when.setTextColor(Apple.SECONDARY);when.setAlpha(.7f);when.setGravity(Gravity.CENTER);when.setPadding(0,dp(4),0,0);hero.addView(when);}
 }
 void openChat(){try{if(!PersonaManager.ensureInitialized("main")){PersonaPanel.show(this,"main");return;}Chat.show(this,"main","main");}catch(Exception e){AgentPanel.fail(this,e);}}
 void send(String action,String role){if(!Settings.canDrawOverlays(this)){PermissionsPanel.show(this);return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);startForegroundService(new Intent(this,PetService.class).setAction(action).putExtra("role",role));}
 void size(PetService.Pet p){SizePanel.show(this,p);}
 void manage(){if(!Settings.canDrawOverlays(this)){PermissionsPanel.show(this);return;}send("ensure","main");handler.postDelayed(()->{if(isFinishing()||PetService.instance==null)return;PetService.Pet p=PetService.instance.pets.get("main");if(p==null)return;LinearLayout l=column();l.setPadding(dp(16),0,dp(16),dp(16));LinearLayout actions=GroupedList.group(l);GroupedList.row(actions,"播放一个动作",GroupedList.PLAY,GroupedList.TEAL,v->{if(!p.visible)PetService.instance.show("main");new ActionSheet(this,p,false,null).show();});GroupedList.row(actions,"调整大小",GroupedList.SLIDERS,GroupedList.SKY,v->size(p));LinearLayout switches=GroupedList.group(l);GroupedList.toggle(switches,"自己随意做小动作",p.rotation,(v,on)->{p.rotation=on;if(PetService.instance!=null)PetService.instance.save();});GroupedList.toggle(switches,"可以拖动、会弹跳",Data.prefs().getBoolean("physics",true),(v,on)->Data.prefs().edit().putBoolean("physics",on).apply());GroupedList.row(GroupedList.group(l),"悬浮窗、通知与后台权限",GroupedList.PHONE,GroupedList.SKY,v->PermissionsPanel.show(this));GroupedList.note(l,"长按桌面上的她，也能聊天、喂食、看动作、送她出门。");new Apple.Builder(this).setTitle("桌宠设置").setView(l).setPositiveButton("好的",null).show();},250);}
 void about(){new Apple.Builder(this).setTitle("大肥鱼 · 独立陪伴版").setMessage("版本 "+version()+"\n\n基于 yshuang722-spec/xiaoxiao-zhuochong 安卓版改造。桌宠上游：PC2005-cloud/dsh-pet；透明视频路线参考 OTFiles/dsh-pet-inAndroid。人格改编自 ERUIHNIYHBKBNF/dafeiyu-persona。\n\n新增独立对话运行时、可追溯记忆、流式回复、情绪动作与可选主动陪伴。无需 DSH 或电脑常开。\n\n代码按 MIT 条款；动画和角色素材另受上游非商业与署名条款约束。此体验包供非商业体验。\n\n模型提供 AI 回复，动画表达为角色演绎。应用不读取其他 App、屏幕或通知。完整来源和许可随源码包提供。").setPositiveButton("知道了",null).show();}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&result==RESULT_OK&&data!=null)try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){org.json.JSONObject export=AgentRuntime.store().snapshot();GrowthStore growth=GrowthStore.quiet();if(growth!=null){growth.sync();export.put("growth",growth.snapshot());}out.write(export.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));Toast.makeText(this,"手账已导出（不含 API Key）",0).show();}catch(Exception e){Toast.makeText(this,"导出失败",0).show();}}
}
