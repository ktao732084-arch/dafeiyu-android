package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import org.json.*;

/** The pet menu's growth panels: feeding and "more" (size, our story, hide). Same look as ActionSheet. */
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

 /** Foods unlocked so far. Feeding is free; the first three feeds a day bring you closer. */
 static void feed(Context c,PetService.Pet pet){
  GrowthStore g=GrowthStore.quiet();if(g==null){Toast.makeText(c,"成长记录暂不可用",Toast.LENGTH_SHORT).show();return;}
  JSONObject s=g.snapshot();JSONObject d=s.optJSONObject("daily");int fed=d!=null&&d.optString("date").equals(Growth.date(System.currentTimeMillis(),java.time.ZoneId.systemDefault()))?d.optInt("feed"):0;
  Sheet sheet=new Sheet(c,"喂食","每天前3次喂食会让你们更亲近。新的食物随章节解锁。"+(fed>0?"\n今天已经喂过 "+fed+" 次。":""));
  for(Growth.Food f:GrowthStore.story().foods){boolean open=Growth.available(s,f);
   sheet.row(f.name+(open?"":" · 第"+f.chapter+"章解锁"),open,v->{sheet.dialog.dismiss();if(PetService.instance!=null)PetService.instance.feed(pet,f);});}
  sheet.show(pet,"大肥鱼-喂食");
 }

 /** Size, our story (opens the app) and hide, moved here from the four-item menu. */
 static void more(Context c,PetService.Pet pet){
  Sheet sheet=new Sheet(c,"更多","「我们的故事」会打开 App，看章节、泡泡瓶和图鉴。");
  sheet.row("大小",true,v->{sheet.dialog.dismiss();SizePanel.show(c,pet);});
  sheet.row("我们的故事",true,v->{sheet.dialog.dismiss();c.startActivity(new Intent(c,MainActivity.class).putExtra("openStory",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
  sheet.row("隐藏",true,v->{sheet.dialog.dismiss();if(PetService.instance!=null){pet.hide();PetService.instance.save();Toast.makeText(c,Data.name(pet.role)+"先回小窝了。想她的时候，在首页点「让她住到屏幕上」",Toast.LENGTH_LONG).show();PetService.instance.stopIfEmpty();}});
  sheet.show(pet,"大肥鱼-更多");
 }
}
