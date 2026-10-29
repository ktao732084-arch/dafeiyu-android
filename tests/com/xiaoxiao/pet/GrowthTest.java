package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

/**
 * The growth main line: daily caps (chatting more never earns more), chapter gates on bond and days, no decay,
 * unlocks, feeding, trips, bubbles popping when memories are deleted, scenes quoting only real memories,
 * upgrading a 0.4.x companion, the prompt stage and lore, and the store surviving a damaged file.
 * Usage: GrowthTest <repository root> <scratch directory>
 */
public final class GrowthTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 static long at(int month,int day,int hour,int minute){return LocalDateTime.of(2026,month,day,hour,minute).atZone(ZONE).toInstant().toEpochMilli();}

 public static void main(String[] args)throws Exception{
  File root=new File(args[0]),scratch=new File(args[1]);scratch.mkdirs();
  Growth.Story s=new Growth.Story(new JSONObject(new String(Files.readAllBytes(new File(root,"assets/story.json").toPath()),"UTF-8")));
  JSONObject catalog=new JSONArray(new String(Files.readAllBytes(new File(root,"assets/catalog.json").toPath()),"UTF-8")).getJSONObject(0);
  Set<String> clips=new LinkedHashSet<>();for(Iterator<String> k=catalog.getJSONObject("files").keys();k.hasNext();)clips.add(k.next());
  story(s,clips);
  daily(s);
  chapters(s);
  unlocks(s,clips);
  feeding(s);
  bubbles(s);
  scenes(s);
  trips(s);
  migration(s,clips);
  prompt(s);
  plan(s,clips);
  store(s,scratch);
  System.out.println("PASS: "+checks+" growth checks");
 }

 /** story.json agrees with the catalog: every clip has a chapter, every name it uses exists, places cover every month. */
 static void story(Growth.Story s,Set<String> clips){
  for(String c:clips)check(s.unlock.containsKey(c)||ActionPlan.FESTIVALS.contains(c)||ActionPlan.SEASONS.contains(c),"clip without a chapter: "+c);
  for(String c:s.unlock.keySet())check(clips.contains(c),"unlock list names a missing clip: "+c);
  for(String c:ActionPlan.FESTIVALS)check(!s.unlock.containsKey(c),"festival clips are never chapter-locked: "+c);
  for(Growth.Food f:s.foods){check(clips.contains(f.clip),"food clip exists: "+f.clip);check(Growth.chapterOf(s,f.clip)<=f.chapter,"food not offered before its clip unlocks: "+f.name);}
  check(s.foods.get(0).price==0,"plain rice is free");
  for(Growth.Chapter c:s.chapters){for(String[] step:c.scene)check(clips.contains(step[0])&&Growth.chapterOf(s,step[0])<=c.id,"scene clip exists and is unlocked by then: "+step[0]);
   for(String[] step:c.neutral)check(clips.contains(step[0]),"neutral scene clip exists: "+step[0]);}
  check(s.places.length()>=40,"at least forty trip places");
  for(int month=1;month<=12;month++){int n=0;for(int i=0;i<s.places.length();i++){JSONArray m=s.places.getJSONObject(i).optJSONArray("months");boolean in=m==null;for(int j=0;m!=null&&j<m.length();j++)if(m.getInt(j)==month)in=true;if(in)n++;}check(n>=30,"enough places in month "+month);}
  for(String slot:new String[]{"morning","noon","afternoon","evening","night"})check(s.greetings.getJSONArray(slot).length()>0,"greetings for "+slot);
  for(String key:new String[]{"depart","back","early","tooEarly","away"})check(s.trip.getJSONArray(key).length()>0,"trip lines: "+key);
 }

 /** Caps: one chat reward however many turns; five taps; three feeds; one meeting; a clock turned back earns nothing. */
 static void daily(Growth.Story s)throws Exception{
  JSONObject g=Growth.fresh(at(10,1,8,0));Random r=new Random(1);
  Growth.Award first=Growth.met(g,s,at(10,1,8,0),ZONE,r),again=Growth.met(g,s,at(10,1,9,0),ZONE,r);
  check(first.first&&first.bond==5&&first.fish==5&&!again.first&&!again.any(),"first meeting of the day only");
  int bond=Growth.bond(g);for(int i=0;i<100;i++)Growth.chatted(g,at(10,1,10,0)+i*1000,ZONE);
  check(Growth.bond(g)==bond+10&&Growth.fish(g)==8,"a hundred turns earn the same as three");
  JSONObject h=Growth.fresh(0);Growth.chatted(h,at(10,1,10,0),ZONE);Growth.chatted(h,at(10,1,10,1),ZONE);check(Growth.bond(h)==0,"two turns are not yet a chat day");
  Growth.chatted(h,at(10,1,10,2),ZONE);check(Growth.bond(h)==10,"the third turn earns the chat reward");
  bond=Growth.bond(g);for(int i=0;i<20;i++)Growth.touched(g,at(10,1,11,0)+i,ZONE);check(Growth.bond(g)==bond+5,"five taps a day");
  Growth.Award back=Growth.met(g,s,at(9,30,8,0),ZONE,r);check(!back.any(),"turning the clock back to yesterday earns nothing");
  Growth.Award next=Growth.met(g,s,at(10,2,8,0),ZONE,r);check(next.first&&next.bond==5,"the next day starts fresh");
  check(Growth.today(g,"turns",at(10,2,8,0),ZONE)==0,"turn counter resets with the day");
  // The ledger stays bounded and still adds up.
  JSONObject l=Growth.fresh(0);for(int d=0;d<700;d++){long t=at(1,1,9,0)+d*Growth.DAY;Growth.met(l,s,t,ZONE,r);}
  JSONArray rows=l.getJSONArray("ledger");int sum=0;for(int i=0;i<rows.length();i++)sum+=rows.getJSONObject(i).optInt("bond");
  check(rows.length()<=Growth.LEDGER&&sum==Growth.bond(l)&&"earlier".equals(rows.getJSONObject(0).optString("type")),"older ledger rows fold into a summary that keeps the total");
 }

 /** Both gates must open; several chapters can pass at once; nothing decays while away. */
 static void chapters(Growth.Story s)throws Exception{
  long since=at(10,1,8,0);JSONObject g=Growth.fresh(since);g.put("bond",1000);
  check(!Growth.advance(g,s,since,at(10,2,8,0))&&Growth.chapter(g)==1,"bond alone does not skip the day gate (day 2)");
  check(Growth.advance(g,s,since,at(10,3,8,0))&&Growth.chapter(g)==2,"day 3 opens chapter 2");
  check(Growth.advance(g,s,since,at(10,31,8,0))&&Growth.chapter(g)==4,"day 31 with enough bond passes chapters 3 and 4");
  JSONArray p=g.getJSONArray("pending");check(p.length()==4&&p.getInt(0)==1&&p.getInt(3)==4,"each reached chapter waits for its scene, in order");
  check(!Growth.advance(g,s,since,at(12,31,8,0))&&Growth.chapter(g)==4,"day 92 without 1200 bond stays in chapter 4");
  JSONObject idle=Growth.fresh(since);idle.put("bond",70);Growth.advance(idle,s,since,at(10,3,8,0));Random r=new Random(2);
  Growth.Award away=Growth.met(idle,s,at(12,25,8,0),ZONE,r);
  check(Growth.bond(idle)==75&&Growth.chapter(idle)==2,"months away: no decay, only today's meeting is added");
  idle.put("lastMet",at(10,3,8,0));Growth.Award back=Growth.met(idle,s,at(12,26,8,0),ZONE,r);
  check(back.postcard!=null&&"away".equals(back.postcard.optString("kind"))&&idle.getJSONArray("postcards").length()==1,"a long absence leaves exactly one postcard");
  JSONObject early=Growth.fresh(since);early.put("lastMet",since);check(Growth.met(early,s,at(10,20,8,0),ZONE,r).postcard==null,"no away postcard before she can travel");
  check(Growth.day(since,since)==1&&Growth.day(0,since)==0,"the first meeting is day 1");
 }

 static void unlocks(Growth.Story s,Set<String> clips)throws Exception{
  JSONObject g=Growth.fresh(0);Set<String> open=Growth.unlocked(g,s,clips);
  check(open.contains("待机呼吸休闲")&&open.contains("放烟花")&&open.contains("堆雪人")&&open.contains("鲸鱼吐泡泡特效"),"routine, festival, season and chapter 1 clips are open at chapter 1");
  check(!open.contains("小提琴演奏")&&!open.contains("蓝鲸现世")&&!open.contains("下五子棋")&&!open.contains("吃西瓜"),"later chapters stay locked");
  for(String c:ActionPlan.SLEEP)check(open.contains(c),"sleep clips always open: "+c);
  for(List<String> list:ActionPlan.EMOTIONS.values())check(list.stream().anyMatch(open::contains),"every emotion keeps a clip at chapter 1");
  g.put("chapter",4);check(Growth.unlocked(g,s,clips).contains("小提琴演奏")&&!Growth.unlocked(g,s,clips).contains("蓝鲸现世"),"chapter 4 performs, the whale waits for chapter 5");
  g.put("chapter",5);check(Growth.unlocked(g,s,clips).size()==clips.size(),"chapter 5 opens everything");
  JSONObject legacy=Growth.fresh(0).put("legacy",true);check(Growth.unlocked(legacy,s,clips).size()==clips.size(),"a 0.4.x companion keeps every clip, 蓝鲸现世 included");
  check(Growth.seen(g,"放烟花",5)&&!Growth.seen(g,"放烟花",6)&&g.getJSONObject("seen").getLong("放烟花")==5,"first sighting recorded once");
 }

 static void feeding(Growth.Story s)throws Exception{
  JSONObject g=Growth.fresh(0);long t=at(10,1,12,0);
  check(Growth.feed(g,s.food("白饭"),t,ZONE,null)==null&&Growth.fish(g)==0&&Growth.bond(g)==2,"plain rice is free and still counts");
  check(Growth.feed(g,s.food("零食"),t,ZONE,null).contains("不够"),"not enough fish");
  check(Growth.feed(g,s.food("西瓜"),t,ZONE,null).contains("第2章"),"a later food is locked");
  g.put("fish",20);for(int i=0;i<4;i++)Growth.feed(g,s.food("零食"),t+i,ZONE,null);
  check(Growth.fish(g)==12&&Growth.bond(g)==6&&g.getInt("feeds")==5,"fish paid every time, bond only for the first three feeds");
  check(!Growth.hungry(g,t,ZONE)&&Growth.hungry(g,t+Growth.DAY,ZONE),"fed today, hungry tomorrow");
  check(Growth.justFed(g,t+10*Growth.MINUTE)&&!Growth.justFed(g,t+40*Growth.MINUTE),"content for half an hour");
  check(Growth.available(new JSONObject().put("legacy",true).put("chapter",1),s.food("一口 Token")),"legacy companions can be fed everything they could eat before");
 }

 static JSONObject memory(String id,String text,long at,boolean pinned)throws Exception{return MemoryLedger.record(id,text,"turn-"+id,pinned,at);}

 /** New memories today earn bond (three a day); deleted ones pop and take their quotes out of chapter cards. */
 static void bubbles(Growth.Story s)throws Exception{
  JSONObject g=Growth.fresh(0);long t=at(10,5,20,0);
  JSONArray mem=new JSONArray().put(memory("old","我喜欢海边散步",at(9,1,10,0),false));
  check(!Growth.sync(g,mem,new JSONArray(),new JSONArray(),t,ZONE).any(),"a memory from an earlier day earns nothing");
  for(int i=0;i<5;i++)mem.put(memory("m"+i,"今天记下的第"+i+"件事",t-i,false));
  Growth.Award a=Growth.sync(g,mem,new JSONArray(),new JSONArray(),t,ZONE);check(a.bond==9,"three new memories a day earn bond");
  JSONArray diary=new JSONArray().put(new JSONObject().put("date","2026-10-04").put("text","写了日记"));
  Growth.sync(g,mem,new JSONArray(),diary,t,ZONE);check(Growth.bubbles(g)==7,"memories and diary pages are bubbles");
  Growth.Material m=new Growth.Material(mem,"kk",5,7,0);Growth.Scene sc=Growth.scene(s,2,false,m);Growth.shown(g,sc,t,false);
  check(g.getJSONArray("cards").getJSONObject(0).getJSONArray("lines").toString().contains("海边散步"),"the card quotes the real memory");
  JSONArray archived=new JSONArray().put(mem.getJSONObject(0));JSONArray rest=new JSONArray();for(int i=1;i<mem.length();i++)rest.put(mem.get(i));
  Growth.sync(g,rest,archived,diary,t,ZONE);check(g.getJSONArray("cards").getJSONObject(0).getJSONArray("lines").toString().contains("海边散步")&&g.getInt("popped")==1,"an archived (superseded) memory pops its bubble but the quote stays");
  int bond=Growth.bond(g),chapter=Growth.chapter(g);
  Growth.sync(g,new JSONArray(),new JSONArray(),new JSONArray(),t,ZONE);
  String card=g.getJSONArray("cards").getJSONObject(0).toString();
  check(!card.contains("海边散步")&&g.getJSONArray("cards").getJSONObject(0).getJSONArray("sources").length()==0,"clearing everything strips the quote from the card");
  check(g.getInt("popped")==7&&Growth.bond(g)==bond&&Growth.chapter(g)==chapter,"bubbles pop, bond and chapter stay");
 }

 /** Scenes use the user's own words when they exist and a fallback when not; custom personas get the neutral script. */
 static void scenes(Growth.Story s)throws Exception{
  JSONArray mem=new JSONArray().put(memory("a","我在准备考研，目标是海洋大学",at(9,1,10,0),false)).put(memory("b","我养了一只叫团子的猫",at(9,5,10,0),true)).put(memory("c","我最喜欢的季节是秋天",at(9,9,10,0),false));
  Growth.Material m=new Growth.Material(mem,"kk",12,30,40),none=new Growth.Material(new JSONArray(),"",12,30,40);
  Growth.Scene two=Growth.scene(s,2,false,m);String text=two.lines().toString();
  check(text.contains("考研")&&text.contains("kk")&&two.sources.length()==1&&"a".equals(two.sources.getString(0)),"chapter 2 quotes the first memory and calls you by name");
  check(!two.fallback.toString().contains("考研")&&two.fallback.size()==two.steps.size(),"a quote-free fallback exists for every line");
  check(Growth.scene(s,3,false,m).lines().toString().contains("团子"),"chapter 3 prefers a pinned memory");
  String letter=Growth.scene(s,4,false,m).lines().toString();check(letter.contains("考研")&&letter.contains("团子")&&letter.contains("秋天"),"the letter quotes up to three memories");
  Growth.Scene empty=Growth.scene(s,3,false,none);check(empty.sources.length()==0&&empty.lines().toString().contains("12 天")&&empty.lines().toString().contains("你"),"no memories: the fallback line with real counts");
  String five=Growth.scene(s,5,false,m).lines().toString();check(five.contains("12 天")&&five.contains("30 个泡泡")&&five.contains("40 次")&&five.contains("蓝鲸"),"chapter 5 counts come from local records");
  check(Growth.scene(s,5,false,m).steps.stream().anyMatch(x->x[0].equals("蓝鲸现世")),"chapter 5 plays 蓝鲸现世");
  String neutral=Growth.scene(s,1,true,m).lines().toString();check(!neutral.contains("深海")&&!neutral.contains("海里"),"custom personas get no whale lore");
  for(int c=1;c<=5;c++){Growth.Scene x=Growth.scene(s,c,false,m);check(x.steps.get(x.steps.size()-1)[1].contains("解锁"),"every scene ends by naming what it unlocked");
   for(String[] step:x.steps)check(!step[1].contains("{"),"no placeholder left: "+step[1]);}
  StringBuilder sixty=new StringBuilder();for(int i=0;i<60;i++)sixty.append("我");String longQuote=Growth.quote(sixty.toString());check(longQuote.codePointCount(0,longQuote.length())==40&&longQuote.endsWith("…"),"long quotes are clipped");
  JSONObject g=Growth.fresh(0);int fish=Growth.fish(g);Growth.Award first=Growth.shown(g,Growth.scene(s,1,false,m),5,false),again=Growth.shown(g,Growth.scene(s,1,false,m),6,false);
  check(first.bond==20&&first.fish==20&&!again.any()&&g.getJSONArray("cards").length()==1&&Growth.pending(g)==0&&Growth.fish(g)==fish+20,"a scene rewards once and leaves one card");
 }

 static void trips(Growth.Story s)throws Exception{
  Random r=new Random(3);JSONObject g=Growth.fresh(0);long t=at(10,10,9,0);
  check("locked".equals(Growth.startTrip(g,false,t,r)),"no trips in chapter 1");
  g.put("chapter",2);check(Growth.startTrip(g,true,t,r).contains("不够"),"a snack bento needs fish");
  check(Growth.startTrip(g,false,t,r)==null&&Growth.startTrip(g,false,t,r)!=null,"one trip at a time");
  JSONObject trip=Growth.trip(g);long end=trip.getLong("end");check(end-t>=Growth.HOUR&&end-t<=4*Growth.HOUR,"one to four hours");
  Growth.Award a=new Growth.Award();check("tooEarly".equals(Growth.endTrip(g,s,t+5*Growth.MINUTE,ZONE,r,a))&&a.postcard==null,"called back at once: no postcard, no penalty");
  g.put("fish",10);Growth.startTrip(g,true,t,r);check(Growth.fish(g)==7,"a snack bento costs three");
  a=new Growth.Award();check("tooEarly".equals(Growth.endTrip(g,s,t+Growth.MINUTE,ZONE,r,a))&&Growth.fish(g)==10,"the bento is refunded");
  Growth.startTrip(g,false,t,r);long due=Growth.trip(g).getLong("end");check(!Growth.tripDue(g,due-1)&&Growth.tripDue(g,due),"due at its end time");
  a=new Growth.Award();check("back".equals(Growth.endTrip(g,s,due,ZONE,r,a))&&a.postcard!=null&&a.fish>=1&&Growth.trip(g)==null,"back on time with a postcard and fish");
  Growth.startTrip(g,false,t,r);a=new Growth.Award();check("early".equals(Growth.endTrip(g,s,t+30*Growth.MINUTE,ZONE,r,a))&&a.postcard!=null,"called back early still brings a postcard");
  JSONObject card=Growth.unread(g);int bond=Growth.bond(g);Growth.read(g,card.getString("id"),t);Growth.read(g,card.getString("id"),t);
  check(Growth.bond(g)==bond+5,"a postcard is worth five bond once");
  Set<String> winter=new HashSet<>();JSONObject w=Growth.fresh(0).put("chapter",2);for(int i=0;i<200;i++){Growth.startTrip(w,false,at(1,10,9,0),r);Growth.Award x=new Growth.Award();Growth.endTrip(w,s,at(1,10,15,0),ZONE,r,x);winter.add(x.postcard.getString("place"));}
  check(!winter.contains("樱花海岸")&&!winter.contains("夏日海水浴场")&&winter.contains("雪中港口"),"seasonal places only in season");
  check(w.getJSONArray("postcards").length()==Growth.POSTCARDS,"postcards are bounded");
 }

 /** A 0.4.x companion: estimate capped by days, recap cards for passed chapters, every clip kept. */
 static void migration(Growth.Story s,Set<String> clips)throws Exception{
  long since=at(6,1,8,0),now=at(10,29,9,0);JSONArray mem=new JSONArray();for(int i=0;i<30;i++)mem.put(memory("m"+i,"第"+i+"件事",since+i*Growth.DAY,false));
  JSONArray diary=new JSONArray().put(new JSONObject().put("date","2026-10-01"));Growth.Material m=new Growth.Material(mem,"kk",Growth.day(since,now),0,0);
  JSONObject g=Growth.migrate(s,now,since,60,mem,diary,200,m,false,"0.4.x");
  check(Growth.bond(g)==60*15+30*3+5+100&&Growth.chapter(g)==4,"151 days, 60 chat days: chapter 4 (bond "+Growth.bond(g)+")");
  check(g.getBoolean("legacy")&&Growth.unlocked(g,s,clips).contains("蓝鲸现世"),"legacy keeps every clip");
  JSONArray cards=g.getJSONArray("cards");check(cards.length()==3&&cards.getJSONObject(0).getBoolean("recap")&&Growth.pending(g)==4,"chapters 1–3 as recap cards, chapter 4 plays once");
  check(Growth.bond(g)<s.chapter(5).bond,"recaps add no reward");
  check(g.getJSONObject("known").getJSONArray("memories").length()==30&&!Growth.sync(g,mem,new JSONArray(),diary,now,ZONE).any(),"existing memories are known, not re-awarded");
  JSONObject young=Growth.migrate(s,now,at(10,28,9,0),2,mem,diary,200,m,false,"0.4.x");
  check(Growth.bond(young)==90&&Growth.chapter(young)==1,"two days of history cap the estimate at 90");
  JSONObject custom=Growth.migrate(s,now,since,60,mem,diary,200,m,true,"0.4.x");check(!custom.getJSONArray("cards").toString().contains("深海"),"custom persona recaps use the neutral script");
 }

 static void prompt(Growth.Story s)throws Exception{
  long base=at(10,1,8,0);
  check(AgentPolicy.relationship(base,base+2*Growth.DAY,30,400,1).contains("刚认识")&&AgentPolicy.relationship(base,base,0,0,3).contains("已经熟悉")&&AgentPolicy.relationship(base,base,0,0,4).contains("老朋友"),"the stage follows the chapter");
  check(AgentPolicy.relationship(base,base+2*Growth.DAY,2,10).contains("刚认识"),"without a chapter the old rule stands");
  String two=Growth.lore(s,2);check(two.contains("吐泡泡")&&two.contains("敢出门")&&!two.contains("泡泡瓶")&&!two.contains("蓝鲸"),"lore up to the current chapter only");
  check(Growth.lore(s,5).contains("蓝鲸"),"chapter 5 knows");
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.now=base+40*Growth.DAY;in.initializedAt=base;in.chapter=2;in.lore=two;
  JSONArray messages=PromptBuilder.build(in,"在吗",false,AgentStore.empty(0),null);String system=messages.getJSONObject(0).getString("content"),user=messages.getJSONObject(messages.length()-1).getString("content");
  check(system.contains("敢出门")&&!user.contains("敢出门"),"lore sits in the cacheable system part");
  check(user.contains("已经熟悉"),"the chapter sets the stage in the background");
  PromptBuilder.Inputs plain=new PromptBuilder.Inputs();plain.persona="你是大肥鱼。";plain.now=in.now;check(!PromptBuilder.build(plain,"在吗",false,AgentStore.empty(0),null).getJSONObject(0).getString("content").contains("来历"),"no lore when none is given (custom persona)");
 }

 /** Idle picks never use a locked clip; hunger and fullness shift the picks without breaking anything. */
 static void plan(Growth.Story s,Set<String> clips)throws Exception{
  JSONObject g=Growth.fresh(0);Set<String> open=Growth.unlocked(g,s,clips);ActionPlan p=new ActionPlan(clips);p.available(open);Random r=new Random(4);
  LocalDateTime t=LocalDateTime.of(2026,10,3,7,0);int hungry=0;p.hungry=true;
  for(int i=0;i<3000;i++){t=t.plusMinutes(7);String pick=p.idle(ActionPlan.Moment.of(t),r);check(open.contains(pick),"locked clip played: "+pick);if(ActionPlan.HUNGRY.contains(pick))hungry++;}
  check(hungry>0,"hungry grumbling happens");
  for(int i=0;i<200;i++){String e=p.emotion("playful","鲸鱼吐泡泡特效",r);check(open.contains(e),"locked emotion clip: "+e);}
  p.available(clips);p.hungry=false;p.fedAt=ActionPlan.epoch(ActionPlan.Moment.of(LocalDateTime.of(2026,10,4,12,0)));
  int full=0;for(int i=0;i<400;i++){String pick=p.idle(ActionPlan.Moment.of(LocalDateTime.of(2026,10,4,12,10).plusSeconds(i)),r);if(ActionPlan.FULL.contains(pick))full++;}
  check(full>0,"just fed, she is content");
 }

 static GrowthStore.Host host(long since,boolean initialized,JSONObject agent){return new GrowthStore.Host(){
  public long since(){return since;}public boolean initialized(){return initialized;}public boolean custom(){return false;}public String nickname(){return "kk";}public JSONObject agent(){return agent;}};}

 /** New install vs upgrade, saving and reloading, a damaged file recovered from its backup, scenes and stats through the store. */
 static void store(Growth.Story s,File scratch)throws Exception{
  long since=System.currentTimeMillis()-40*Growth.DAY;
  JSONObject agent=AgentStore.empty(0).put("chatDays",20).put("archive",new JSONArray());agent.getJSONArray("memories").put(memory("x","我喜欢下雨天",since,false));
  File fresh=new File(scratch,"fresh-"+System.nanoTime()+".json");GrowthStore a=new GrowthStore(fresh,s,host(0,false,agent),new Random(5));
  check(a.chapter()==1&&!a.snapshot().getBoolean("legacy")&&a.pending()==1,"a new install starts at chapter 1 with the first scene waiting");
  File old=new File(scratch,"old-"+System.nanoTime()+".json");GrowthStore b=new GrowthStore(old,s,host(since,true,agent),new Random(5));
  check(b.snapshot().getBoolean("legacy")&&b.chapter()>=2&&"0.4.x".equals(b.snapshot().getString("migratedFrom")),"an initialized companion is migrated once");
  b.met();b.touched();int bond=Growth.bond(b.snapshot());b.flush();
  GrowthStore reopened=new GrowthStore(old,s,host(since,true,agent),new Random(5));check(Growth.bond(reopened.snapshot())==bond&&reopened.snapshot().has("migratedFrom"),"saved and reloaded without migrating again");
  Growth.Scene scene=reopened.nextScene();check(scene!=null&&scene.lines().toString().contains("下雨天"),"the store builds the waiting scene from real memories");
  reopened.shown(scene);reopened.flush();check(reopened.pending()==0,"scene marked as shown");
  reopened.met();reopened.flush();
  Files.write(old.toPath(),"{not json".getBytes("UTF-8"));GrowthStore recovered=new GrowthStore(old,s,host(since,true,agent),new Random(5));
  check(recovered.snapshot().getJSONArray("cards").length()>0&&recovered.snapshot().has("migratedFrom"),"a damaged file is recovered from its backup");
  AtomicCounter counter=new AtomicCounter();GrowthStore.changed=counter::hit;
  JSONObject young=AgentStore.empty(0).put("archive",new JSONArray());File growing=new File(scratch,"grow-"+System.nanoTime()+".json");
  GrowthStore c=new GrowthStore(growing,s,host(System.currentTimeMillis()-5*Growth.DAY,false,young),new Random(6));
  for(int i=0;i<3;i++)c.chatted();c.met();c.touched();c.touched();for(int i=0;i<30;i++)c.touched();
  check(c.chapter()==1&&Growth.bond(c.snapshot())==20,"one day: chat 10, meeting 5, five taps");
  c.snapshot();c.feed(s.food("白饭"),null);c.feed(s.food("白饭"),null);c.feed(s.food("白饭"),null);c.feed(s.food("白饭"),null);
  check(Growth.bond(c.snapshot())==26&&!c.hungry(),"feeding through the store");
  JSONObject g=c.snapshot();check(g.getJSONArray("ledger").length()>0,"ledger kept");
  GrowthStore.changed=null;c.flush();
  check(new File(growing+".tmp").exists()==false,"no temp file left behind");
 }
 static final class AtomicCounter{int n;void hit(){n++;}}
}
