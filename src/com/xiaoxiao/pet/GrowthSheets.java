package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import org.json.*;

/** The pet menu's growth panels: feeding, going out, and "more" (size, our story, hide). Same look as ActionSheet. */
final class GrowthSheets {
 static final class Sheet {
  final Context c;final Dialog dialog;final LinearLayout rows;final TextView hint;
  Sheet(Context c,String heading,String note){this.c=c;dialog=CompactPanel.create(c);LinearLayout content=new LinearLayout(c);content.setOrientation(1);content.setPadding(dp(12),dp(8),dp(12),dp(8));
   LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=Apple.label(c,heading,17,true);title.setPadding(dp(8),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
   Button close=new Button(c);close.setText("关闭");Apple.button(close,false);top.addView(close,new LinearLayout.LayoutParams(dp(64),dp(44)));close.setOnClickListener(v->dialog.dismiss());content.addView(top);
   ScrollView scroll=new SpringScrollView(c);scroll.setFillViewport(true);rows=new LinearLayout(c);rows.setOrientation(1);scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   hint=Apple.label(c,note,12,false);hint.setTextColor(Apple.SECONDARY);hint.setPadding(0,dp(10),0,0);content.addView(hint);dialog.setContentView(content);}
  int dp(int x){return Apple.dp(c,x);}
  Button row(String text,boolean enabled,View.OnClickListener click){Button b=new Button(c);b.setText(text);Apple.button(b,false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(16);b.setAllCaps(false);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(-1,dp(48));a.topMargin=dp(8);rows.addView(b,a);b.setEnabled(enabled);if(!enabled)b.setAlpha(.45f);b.setOnClickListener(click);return b;}
  void show(PetService.Pet pet,String title){CompactPanel.show(dialog,c,pet,title);}
 }

 static String jar(int fish){return fish<=0?"余额-分文不剩":fish<5?"余额-袋空如洗":fish<15?"余额-数金皱眉":fish<40?"余额-钱袋如常":fish<100?"余额-金袋叮当":"余额-钱袋满溢";}

 /** Foods unlocked so far; plain rice is free, so feeding never runs dry. Tapping the jar shows how full it is. */
 static void feed(Context c,PetService.Pet pet){
  GrowthStore g=GrowthStore.quiet();if(g==null){Toast.makeText(c,"成长记录暂不可用",Toast.LENGTH_SHORT).show();return;}
  JSONObject s=g.snapshot();int fish=Growth.fish(s),fed=s.optJSONObject("daily")!=null&&s.optJSONObject("daily").optString("date").equals(Growth.date(System.currentTimeMillis(),java.time.ZoneId.systemDefault()))?s.optJSONObject("daily").optInt("feed"):0;
  Sheet sheet=new Sheet(c,"喂食 · 小鱼干 "+fish,"每天前3次喂食会让你们更亲近；白饭永远免费。小鱼干靠每天见面、聊天和她旅行带回来，不能用钱买。"+(fed>0?"\n今天已经喂过 "+fed+" 次。":""));
  sheet.row("看看小鱼干罐",true,v->{if(!Data.file(pet.role,jar(fish)).isEmpty())pet.play(jar(fish));});
  for(Growth.Food f:GrowthStore.story().foods){boolean open=Growth.available(s,f);
   sheet.row(f.name+" · "+(f.price==0?"免费":f.price+" 小鱼干")+(open?"":" · 第"+f.chapter+"章解锁"),open&&fish>=f.price,v->{sheet.dialog.dismiss();if(PetService.instance!=null)PetService.instance.feed(pet,f);});}
  sheet.show(pet,"大肥鱼-喂食");
 }

 /** Going out (chapter 2): a free plain-rice bento, or a snack bento that costs three fish and brings two more back. */
 static void trip(Context c,PetService.Pet pet){
  GrowthStore g=GrowthStore.quiet();if(g==null){Toast.makeText(c,"成长记录暂不可用",Toast.LENGTH_SHORT).show();return;}
  JSONObject s=g.snapshot();
  if(!Growth.canTravel(s)){Toast.makeText(c,GrowthStore.story().trip.optString("locked"),Toast.LENGTH_LONG).show();return;}
  Sheet sheet=new Sheet(c,"出门 · 带个便当","她会出门1–4小时，缩在屏幕边上。点一下边上的小标签，或者和她聊天，她就会提前回来，不会有惩罚。回来时带一张明信片。");
  sheet.row("白饭便当 · 免费",true,v->{sheet.dialog.dismiss();if(PetService.instance!=null)PetService.instance.goOut(pet,false);});
  sheet.row("零食便当 · "+Growth.SNACK_BENTO+" 小鱼干（多带回2条）",Growth.fish(s)>=Growth.SNACK_BENTO,v->{sheet.dialog.dismiss();if(PetService.instance!=null)PetService.instance.goOut(pet,true);});
  sheet.show(pet,"大肥鱼-出门");
 }

 /** Size, our story (opens the app) and hide, moved here from the four-item menu. */
 static void more(Context c,PetService.Pet pet){
  Sheet sheet=new Sheet(c,"更多","「我们的故事」会打开 App，看章节、泡泡瓶和图鉴。");
  sheet.row("大小",true,v->{sheet.dialog.dismiss();SizePanel.show(c,pet);});
  sheet.row("我们的故事",true,v->{sheet.dialog.dismiss();c.startActivity(new Intent(c,MainActivity.class).putExtra("openStory",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
  sheet.row("隐藏",true,v->{sheet.dialog.dismiss();if(PetService.instance!=null){pet.hide();PetService.instance.save();}});
  sheet.show(pet,"大肥鱼-更多");
 }
}
