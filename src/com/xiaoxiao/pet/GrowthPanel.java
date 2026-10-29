package com.xiaoxiao.pet;

import android.app.*;import android.content.res.ColorStateList;import android.widget.*;import org.json.*;import java.util.*;

/** 「我们的故事」: chapter progress, the bubble jar, chapter cards, the two collections and the bond record. */
final class GrowthPanel {
 static String fmt(long at,String pattern){return new java.text.SimpleDateFormat(pattern,Locale.CHINA).format(new Date(at));}
 /** One line for the home screen, e.g. 第2章「熟悉」 · 羁绊 120/250. */
 static String summary(JSONObject s){Growth.Story story=GrowthStore.story();int ch=Growth.chapter(s);String next=ch<Growth.MAX_CHAPTER?"/"+story.chapter(ch+1).bond:"";return story.chapter(ch).title()+" · 羁绊 "+Growth.bond(s)+next;}
 static String today(JSONObject s){
  JSONObject d=s.optJSONObject("daily");if(d==null||!d.optString("date").equals(Growth.date(System.currentTimeMillis(),java.time.ZoneId.systemDefault())))return "今天还没见面";
  List<String> parts=new ArrayList<>();parts.add(d.optInt("met")>0?"已见面":"还没见面");if(d.optInt("turns")>0)parts.add("聊了 "+d.optInt("turns")+" 轮");if(d.optInt("feed")>0)parts.add("喂过 "+d.optInt("feed")+" 次");if(d.optInt("touch")>0)parts.add("摸摸 "+d.optInt("touch")+" 次");
  return "今天："+String.join(" · ",parts);
 }

 static void show(MainActivity a){try{
  GrowthStore g=GrowthStore.get();g.sync();JSONObject s=g.snapshot();Growth.Story story=GrowthStore.story();
  int ch=Growth.chapter(s),day=Growth.day(PersonaManager.initializedAt("main"),System.currentTimeMillis());Growth.Chapter c=story.chapter(ch);boolean custom=PersonaManager.CUSTOM.equals(PersonaManager.mode("main"));
  LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(4),a.dp(18),a.dp(18));
  TextView title=Apple.label(a,c.title(),20,true);title.setPadding(a.dp(6),a.dp(6),0,0);l.addView(title);
  if(!custom)GroupedList.note(l,c.lore);
  String progress;int pct;
  if(ch<Growth.MAX_CHAPTER){Growth.Chapter next=story.chapter(ch+1);pct=Math.min(100,Growth.bond(s)*100/Math.max(1,next.bond));
   progress="羁绊 "+Growth.bond(s)+" / "+next.bond+" · 相识第 "+Math.max(1,day)+" 天"+(day<next.day?"（下一章需第 "+next.day+" 天）":"")+"\n下一章："+next.title()+"，解锁"+next.unlocks;}
  else{pct=100;progress="羁绊 "+Growth.bond(s)+" · 相识第 "+Math.max(1,day)+" 天\n主线已经走完。泡泡瓶变成了「我们的海」，可以一直攒下去。";}
  ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(pct);bar.setProgressTintList(ColorStateList.valueOf(Apple.BLUE));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,a.dp(10));bp.topMargin=a.dp(10);l.addView(bar,bp);
  GroupedList.note(l,progress);GroupedList.note(l,today(s)+"\n一共喂过 "+s.optInt("feeds")+" 次");
  JSONObject known=s.optJSONObject("known");int memories=known==null?0:known.optJSONArray("memories").length(),diary=known==null?0:known.optJSONArray("diary").length();
  JSONArray cards=s.optJSONArray("cards");
  GroupedList.section(l,(ch>=Growth.MAX_CHAPTER?"我们的海":"泡泡瓶")+" · "+Growth.bubbles(s)+" 个泡泡");
  LinearLayout jar=GroupedList.group(l);
  GroupedList.row(jar,"记忆泡泡 · "+memories,GroupedList.BOOK,GroupedList.ORANGE,v->AgentPanel.memories(a));
  GroupedList.row(jar,"日记泡泡 · "+diary,GroupedList.BOOK,GroupedList.VIOLET,v->MemoryPanel.diary(a));
  GroupedList.row(jar,"章节回忆 · "+cards.length(),GroupedList.HEART,GroupedList.PINK,v->cards(a));
  if(s.optInt("popped")>0)GroupedList.note(l,"有 "+s.optInt("popped")+" 个泡泡破了：对应的记忆或日记已经删除，内容不会留在这里。羁绊和章节不受影响。");
  GroupedList.section(l,"收集");LinearLayout collect=GroupedList.group(l);
  Set<String> all=Data.actions("main"),open=g.unlocked(all);
  GroupedList.row(collect,"动作图鉴 · 已解锁 "+open.size()+"/"+all.size(),GroupedList.PLAY,GroupedList.TEAL,v->collection(a));
  int seasonal=0;for(String x:seasonal())if(s.optJSONObject("seen").has(x))seasonal++;
  GroupedList.row(collect,"时令图鉴 · 见过 "+seasonal+"/"+seasonal().size(),GroupedList.CLOCK,GroupedList.MINT,v->seasonal(a));
  GroupedList.row(collect,"成长记录",GroupedList.SLIDERS,GroupedList.GREY,v->ledger(a));
  GroupedList.note(l,"怎么变亲近：每天第一次见面、聊满3轮、喂食（每天3次）、摸摸（每天5次）、新记下的事（每天3条）、每一章的剧情。聊得再多，一天也只算一次，不会因为多聊多花钱。离开不会扣分，羁绊值只增不减。模型的回复不会改变这些数。");
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("我们的故事").setView(scroll).setPositiveButton("合上",null).show();
 }catch(Exception e){AgentPanel.fail(a,e);}}

 static List<String> seasonal(){List<String> out=new ArrayList<>(new TreeSet<>(ActionPlan.FESTIVALS));out.addAll(new TreeSet<>(ActionPlan.SEASONS));return out;}

 static void cards(MainActivity a){try{
  JSONArray cards=GrowthStore.get().snapshot().optJSONArray("cards");LinearLayout l=a.column();l.setPadding(a.dp(18),0,a.dp(18),a.dp(18));
  GroupedList.note(l,"每一章她都会和你经历一件事。引用的原话会随记忆删除而去掉。");
  if(cards.length()==0)GroupedList.note(l,"还没有。等她准备好，会在桌面上和你一起经历第一章。");
  else{LinearLayout list=GroupedList.group(l);for(int i=cards.length()-1;i>=0;i--){JSONObject c=cards.getJSONObject(i);StringBuilder text=new StringBuilder();JSONArray lines=c.optJSONArray("lines");for(int j=0;lines!=null&&j<lines.length();j++)text.append(lines.optString(j)).append("\n\n");
   GroupedList.entry(list,c.optString("title")+(c.optBoolean("recap")?" · 补记":"")+" · "+fmt(c.optLong("at"),"yyyy-MM-dd"),v->new Apple.Builder(a).setTitle(c.optString("title")).setMessage(text.toString().trim()).setPositiveButton("好的",null).show());}}
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);new Apple.Builder(a).setTitle("章节回忆").setView(scroll).setPositiveButton("关闭",null).show();
 }catch(Exception e){AgentPanel.fail(a,e);}}

 static void collection(MainActivity a){try{
  GrowthStore g=GrowthStore.get();JSONObject s=g.snapshot();Growth.Story story=GrowthStore.story();JSONObject seen=s.optJSONObject("seen");JSONObject groups=Data.role("main").optJSONObject("groups");StringBuilder text=new StringBuilder();
  if(s.optBoolean("legacy"))text.append("你们在 0.5.0 之前就认识了，原有动作全部保留。\n\n");
  for(Map.Entry<String,String> e:Data.menuGroups("main").entrySet()){JSONArray list=groups.optJSONArray(e.getValue());if(list==null)continue;LinkedHashSet<String> all=new LinkedHashSet<>();for(int i=0;i<list.length();i++)all.add(list.optString(i));
   Set<String> open=g.unlocked(all);TreeMap<Integer,List<String>> locked=new TreeMap<>();List<String> have=new ArrayList<>();
   for(String x:all)if(open.contains(x))have.add(x+(seen.has(x)?"":"（还没见过）"));else locked.computeIfAbsent(Growth.chapterOf(story,x),k->new ArrayList<>()).add(x);
   text.append(e.getKey()).append("  ").append(open.size()).append("/").append(all.size()).append("\n");if(!have.isEmpty())text.append("已解锁：").append(String.join("、",have)).append("\n");
   for(Map.Entry<Integer,List<String>> lock:locked.entrySet())text.append(story.chapter(lock.getKey()).title()).append("：").append(String.join("、",lock.getValue())).append("\n");text.append("\n");}
  text.append("节日和季节动作不按章节锁，按日期出现，见「时令图鉴」。");
  new Apple.Builder(a).setTitle("动作图鉴").setMessage(text.toString().trim()).setPositiveButton("好的",null).show();
 }catch(Exception e){AgentPanel.fail(a,e);}}

 static void seasonal(MainActivity a){try{
  JSONObject seen=GrowthStore.get().snapshot().optJSONObject("seen");StringBuilder text=new StringBuilder("节日动作只在对应的日子出现，季节动作跟着月份走。第一次见到时记在这里，一年才能集齐。\n\n");
  for(String x:seasonal())text.append(seen.has(x)?"✓ "+x+" · "+fmt(seen.optLong(x),"yyyy-MM-dd"):"· "+x+" · 还没遇到").append("\n");
  new Apple.Builder(a).setTitle("时令图鉴").setMessage(text.toString().trim()).setPositiveButton("好的",null).show();
 }catch(Exception e){AgentPanel.fail(a,e);}}

 static final Map<String,String> LABELS=new HashMap<>();
 static{String[] pairs={"met","今天第一次见面","chat","今天聊满3轮","feed","喂食","touch","摸摸","memory","新记下一件事","chapter","章节剧情","postcard","看明信片（旧版）","migrated","升级时按已有记录估算","earlier","更早的记录合计"};for(int i=0;i<pairs.length;i+=2)LABELS.put(pairs[i],pairs[i+1]);}
 static void ledger(MainActivity a){try{
  JSONArray rows=GrowthStore.get().snapshot().optJSONArray("ledger");StringBuilder text=new StringBuilder("每一笔都来自这台手机上的真实互动，最多显示最近80笔。\n\n");int shown=0;
  for(int i=rows.length()-1;i>=0&&shown<80;i--){JSONObject r=rows.getJSONObject(i);if(r.optInt("bond")==0)continue;shown++;String label=LABELS.containsKey(r.optString("type"))?LABELS.get(r.optString("type")):r.optString("type");
   text.append(fmt(r.optLong("at"),"MM-dd HH:mm")).append("  ").append(label).append(r.has("ref")&&!"migrated".equals(r.optString("type"))?"（"+r.optString("ref")+"）":"").append("  羁绊 +").append(r.optInt("bond")).append("\n");}
  new Apple.Builder(a).setTitle("成长记录").setMessage(text.toString().trim()).setPositiveButton("好的",null).show();
 }catch(Exception e){AgentPanel.fail(a,e);}}
}
