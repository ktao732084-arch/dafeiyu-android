package com.xiaoxiao.pet;
import java.time.*;import java.util.*;

/** Time, festival and touch rules for animation choice; seeded randomness keeps runs repeatable. */
public final class ActionPlanTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static Set<String> all(){Set<String> s=new HashSet<>();for(List<String> l:Arrays.asList(ActionPlan.SLEEP,ActionPlan.TAPS,ActionPlan.EVERYDAY,ActionPlan.WORK,ActionPlan.PLAY))s.addAll(l);
  s.addAll(Arrays.asList(ActionPlan.IDLE,ActionPlan.DRAG,ActionPlan.WOKEN,ActionPlan.STARTLED,ActionPlan.ANNOYED,"晨间刷牙","吃早餐","吃午餐","吃白饭","吃晚餐","涮火锅","是啊，吃什么","大口吃零食","工作状态-雀跃庆祝","深度思考碎碎念","碎碎念-对屏碎碎念",
   "中秋赏月吃月饼","讨糖南瓜灯","萌化小幽灵","吃粽子","吃饺子","放烟花","写福字","收红包","吃年糕","摇扇纳凉","吃西瓜","玩水枪","吃冰淇淋融化","堆雪人","吃糖葫芦","放风筝","吃大闸蟹","被落叶淹没"));
  for(List<String> l:ActionPlan.EMOTIONS.values())s.addAll(l);return s;}
 static ActionPlan.Moment at(int y,int mo,int d,int h,int mi,int lm,int ld){return new ActionPlan.Moment(LocalDateTime.of(y,mo,d,h,mi),lm,ld,false,false);}
 public static void main(String[] args){
  Random r=new Random(7);
  ActionPlan varied=new ActionPlan(ActionPlan.TAP_ACTIONS);Set<String> tapped=new HashSet<>();Deque<String> tapWindow=new ArrayDeque<>();
  for(int i=0;i<200;i++){String clip=varied.tap(i*6000L,ActionPlan.IDLE,r);check(!tapWindow.contains(clip),"no repeated tap clip in recent eight");tapped.add(clip);tapWindow.addLast(clip);if(tapWindow.size()>8)tapWindow.removeFirst();}
  check(tapped.size()>=40,"broad tap repertoire: "+tapped.size());
  varied.available(Arrays.asList(ActionPlan.IDLE,"鲸鱼吐泡泡特效"));
  check("鲸鱼吐泡泡特效".equals(varied.tap(2000000,ActionPlan.IDLE,r)),"chapter-limited repertoire respected");
  check(ActionPlan.IDLE.equals(new ActionPlan(Collections.singleton(ActionPlan.IDLE)).tap(0,ActionPlan.IDLE,r)),"missing tap clips fall back to idle, never empty");
  for(String clip:ActionPlan.TAP_ACTIONS)check(!ActionPlan.eating(clip)&&!ActionPlan.FESTIVALS.contains(clip)&&!ActionPlan.SLEEP.contains(clip)&&!clip.startsWith("余额")&&!ActionPlan.DRAG.equals(clip),"tap cannot fabricate a meal, balance or hold: "+clip);
  System.out.println("TAP REPERTOIRE: "+ActionPlan.TAP_ACTIONS.size()+" eligible clips");
  // 2026-10-28 is a Wednesday, lunar 9/18.
  ActionPlan p=new ActionPlan(all());
  for(int i=0;i<200;i++)check(ActionPlan.SLEEP.contains(p.idle(at(2026,10,28,2,i%60,9,18),r)),"small hours are sleepy only");
  int lunch=0;p=new ActionPlan(all());for(int i=0;i<200;i++){String x=p.idle(at(2026,10,28,12,0,9,18),r);if("吃午餐".equals(x)||"吃白饭".equals(x))lunch++;}check(lunch==1,"lunch once per lunch window, got "+lunch);
  int rice=0,meals=0;for(int i=0;i<400;i++){ActionPlan q=new ActionPlan(all());for(int j=0;j<20;j++){String x=q.idle(at(2026,10,28,12,0,9,18),r);if("吃白饭".equals(x))rice++;if(ActionPlan.MEALS.contains(x))meals++;}}check(meals==400&&rice>40&&rice<160,"plain rice is the occasional joke lunch: "+rice+"/"+meals);
  int breakfast=0;for(int s=0;s<100;s++)if("吃早餐".equals(new ActionPlan(all()).idle(at(2026,10,28,7,30,9,18),r)))breakfast++;check(breakfast>5,"breakfast shows up in the morning");
  for(int s=0;s<300;s++){String a=new ActionPlan(all()).idle(at(2026,10,28,20,0,9,18),r);check(!"晨间刷牙".equals(a)&&!a.startsWith("吃早")&&!a.equals("吃午餐"),"no toothbrush or breakfast in the evening: "+a);}
  for(int s=0;s<300;s++){String a=new ActionPlan(all()).idle(at(2027,1,12,15,0,12,5),r);check(!"摇扇纳凉".equals(a)&&!"玩水枪".equals(a)&&!"吃西瓜".equals(a),"no summer clips in January: "+a);}
  check(ActionPlan.slot(at(2026,10,28,10,0,9,18))==ActionPlan.WORK,"weekday morning is work");
  check(ActionPlan.slot(at(2026,10,31,10,0,9,21))==ActionPlan.PLAY,"Saturday morning is play");
  check(ActionPlan.slot(at(2026,10,28,12,15,9,18)).contains("吃午餐")&&ActionPlan.slot(at(2026,10,28,18,0,9,18)).contains("吃晚餐"),"meal slots");
  check(ActionPlan.slot(at(2027,1,12,18,0,12,5)).contains("涮火锅")&&!ActionPlan.slot(at(2026,7,12,18,0,5,28)).contains("涮火锅"),"hot pot dinner in winter only");
  int moon=0;for(int s=0;s<100;s++)if("中秋赏月吃月饼".equals(new ActionPlan(all()).idle(at(2026,9,25,15,0,8,15),r)))moon++;check(moon>=10,"Mid-Autumn mooncake is likely on the day, got "+moon);
  check(ActionPlan.festival(new ActionPlan.Moment(LocalDateTime.of(2026,9,25,15,0),8,15,true,false)).isEmpty(),"leap month is not the festival");
  check(ActionPlan.festival(at(2026,10,31,15,0,9,21)).contains("讨糖南瓜灯"),"Halloween");
  check(ActionPlan.festival(new ActionPlan.Moment(LocalDateTime.of(2026,2,16,15,0),12,29,false,true)).containsAll(Arrays.asList("吃饺子","放烟花")),"New Year's Eve on a 29-day month");
  check(ActionPlan.festival(at(2026,6,19,15,0,5,5)).contains("吃粽子")&&ActionPlan.festival(at(2026,4,5,15,0,2,18)).contains("吃青团"),"Dragon Boat and Qingming");
  check(ActionPlan.festival(at(2026,10,28,15,0,9,18)).isEmpty(),"ordinary day has no festival");
  for(int s=0;s<100;s++)check(!new ActionPlan(all()).idle(at(2026,9,25,23,0,8,15),r).equals("中秋赏月吃月饼"),"festival clips are daytime only");
  p=new ActionPlan(all());LinkedList<String> last=new LinkedList<>();LocalDateTime t=LocalDateTime.of(2026,10,31,9,0);
  for(int i=0;i<600;i++){String a=p.idle(new ActionPlan.Moment(t,9,21,false,false),r);check(!last.contains(a),"no repeat within "+ActionPlan.RECENT+" clips: "+a);last.add(a);if(last.size()>ActionPlan.RECENT)last.removeFirst();t=t.plusMinutes(1);}
  Set<String> few=new HashSet<>(Arrays.asList(ActionPlan.IDLE,"悠闲哼歌","照镜子"));p=new ActionPlan(few);
  for(int i=0;i<50;i++)check(few.contains(p.idle(at(2026,10,28,12,0,9,18),r)),"only clips in the catalog");
  for(int i=0;i<200;i++){long g=ActionPlan.gapMillis(at(2026,10,28,14,0,9,18),r),n=ActionPlan.gapMillis(at(2026,10,28,3,0,9,18),r);check(g>=25_000&&g<55_000&&n>=180_000&&n<360_000,"gaps: day half a minute, night minutes");}
  p=new ActionPlan(all());check(ActionPlan.WOKEN.equals(p.tap(0,"原地小憩沉眠",r)),"tap wakes a dozing pet");
  p=new ActionPlan(all());String prev=null;for(int i=0;i<3;i++){String a=p.tap(i*10_000L,ActionPlan.IDLE,r);check(ActionPlan.TAP_ACTIONS.contains(a)&&!a.equals(prev),"varied tap reactions");prev=a;}
  p=new ActionPlan(all());String a=null;for(int i=0;i<4;i++)a=p.tap(i*500L,ActionPlan.IDLE,r);check(ActionPlan.ANNOYED.equals(a),"poking four times annoys her");
  check(ActionPlan.TAP_ACTIONS.contains(p.tap(2500,ActionPlan.IDLE,r)),"annoyance resets after showing");
  p=new ActionPlan(all());prev=null;for(int i=0;i<40;i++){String e=p.emotion("happy","点击回应-开心跃动",r);check(ActionPlan.EMOTIONS.get("happy").contains(e)&&!e.equals(prev),"emotion varies within its family");prev=e;}
  check("点击回应-开心跃动".equals(new ActionPlan(few).emotion("happy","点击回应-开心跃动",r)),"canonical clip when no variants exist");
  for(int month=1;month<=12;month++)for(int lm=1;lm<=12;lm++)for(int ld=1;ld<=30;ld+=1){ActionPlan.Moment m=new ActionPlan.Moment(LocalDateTime.of(2026,month,month==2?28:30,12,0),lm,ld,false,ld>=29);
   for(String f:ActionPlan.festival(m))check(ActionPlan.FESTIVALS.contains(f),"festival clip has a cooldown group: "+f);for(String f:ActionPlan.season(m))check(ActionPlan.SEASONS.contains(f),"season clip has a cooldown group: "+f);}
  Map<String,Integer> day=new HashMap<>();p=new ActionPlan(all());t=LocalDateTime.of(2026,10,31,0,0);
  while(t.getDayOfMonth()==31){ActionPlan.Moment m=new ActionPlan.Moment(t,9,21,false,false);String x=p.idle(m,r);String k=ActionPlan.kind(x);if(k!=null&&k.startsWith("@"))day.merge(k,1,Integer::sum);day.merge(x,1,Integer::sum);
   if(ActionPlan.MEALS.contains(x)||ActionPlan.SNACKS.contains(x)||ActionPlan.FESTIVALS.contains(x)||ActionPlan.SEASONS.contains(x))check(t.getHour()>=7&&(t.getHour()<22||t.getHour()==22&&t.getMinute()<30),"special clips by day only: "+x+" at "+t);
   t=t.plusSeconds(10).plusNanos(ActionPlan.gapMillis(m,r)*1_000_000);}
  check(day.getOrDefault("@meal",0)==3,"three meals on Halloween Saturday: "+day.get("@meal"));
  check(day.getOrDefault("@snack",0)==1&&day.getOrDefault("晨间刷牙",0)==1,"one snack time and one toothbrush");
  int fest=day.getOrDefault("@festival",0);check(fest>=4&&fest<=10,"festival clips several times a day, not constantly: "+fest);
  check(day.getOrDefault("@season",0)<=8,"seasonal clips stay occasional: "+day.get("@season"));
  // Sleep: from midnight she yawns once, then dozes on a loop; a tap wakes her for ten minutes.
  p=new ActionPlan(all());ActionPlan.Moment night=at(2026,10,29,2,0,9,19);
  check(ActionPlan.YAWN.equals(p.idle(night,r)),"falls asleep with a yawn");
  for(int i=0;i<5;i++)check(ActionPlan.DOZE.equals(p.idle(night,r)),"then dozes on a loop");
  long n0=ActionPlan.epoch(night);check(ActionPlan.WOKEN.equals(p.tap(n0,ActionPlan.DOZE,r)),"tap wakes the sleeper");
  check(!p.asleep(at(2026,10,29,2,5,9,19))&&p.asleep(at(2026,10,29,2,11,9,19)),"awake ten minutes, then asleep again");
  check(!p.asleep(at(2026,10,29,9,0,9,19)),"never asleep by day");
  check(!new ActionPlan(all()).wake(n0,ActionPlan.IDLE)&&new ActionPlan(all()).wake(n0,ActionPlan.DOZE),"only a dozing pet is woken");
  // Interaction-aware idle: attention right after you, sulking after pokes, wandering when ignored.
  ActionPlan.Moment noon=at(2026,11,4,14,0,9,26);long t14=ActionPlan.epoch(noon); // an ordinary Wednesday, no festivallong t14=ActionPlan.epoch(noon);
  int look=0;for(int i=0;i<300;i++){ActionPlan q=new ActionPlan(all());q.interacted(t14-10_000);if(ActionPlan.ATTENTION.contains(q.idle(noon,r)))look++;}check(look>100,"looks your way right after you interact: "+look);
  int sulk=0;for(int i=0;i<300;i++){ActionPlan q=new ActionPlan(all());for(int k=0;k<4;k++)q.tap(t14-30_000+k*500,ActionPlan.IDLE,r);if(ActionPlan.SULKING.contains(q.idle(noon,r)))sulk++;}check(sulk>110,"grumbles after being poked: "+sulk);
  int lonely=0,lonelyEarly=0;for(int i=0;i<300;i++){ActionPlan q=new ActionPlan(all());q.interacted(t14-4*3600_000L);if(ActionPlan.LONELY.contains(q.idle(noon,r)))lonely++;ActionPlan e=new ActionPlan(all());e.interacted(t14-20*60_000L);if(ActionPlan.LONELY.contains(e.idle(noon,r)))lonelyEarly++;}
  check(lonely>lonelyEarly+40,"wanders about after hours alone: "+lonely+" vs "+lonelyEarly);
  // Being interrupted mid-activity, and what the model hears.
  check(ActionPlan.CAUGHT.equals(new ActionPlan(all()).tap(0,"吃午餐",r))&&ActionPlan.STARTLED.equals(new ActionPlan(all()).tap(0,"写代码",r)),"caught mid-snack, startled at work");
  check(ActionPlan.tapNote(ActionPlan.DOZE,ActionPlan.WOKEN).contains("戳醒")&&ActionPlan.tapNote("吃午餐",ActionPlan.CAUGHT).contains("吃着东西")&&ActionPlan.tapNote(ActionPlan.IDLE,ActionPlan.ANNOYED).contains("连续戳")&&ActionPlan.tapNote(ActionPlan.IDLE,"点击回应-开心跃动").contains("碰了碰"),"tap notes for the model");
  // Lunar table spot checks against Hong Kong Observatory dates, including the two New Years android.icu gets wrong.
  check(Arrays.equals(new int[]{1,1,0},ActionPlan.lunar(LocalDate.of(2027,2,6)))&&Arrays.equals(new int[]{1,1,0},ActionPlan.lunar(LocalDate.of(2030,2,3))),"Lunar New Year 2027 and 2030");
  check(ActionPlan.Moment.of(LocalDateTime.of(2027,2,5,20,0)).lunarNewYearEve&&ActionPlan.Moment.of(LocalDateTime.of(2030,2,2,20,0)).lunarNewYearEve&&!ActionPlan.Moment.of(LocalDateTime.of(2027,2,4,20,0)).lunarNewYearEve,"New Year's Eve on 29- and 30-day months");
  check(ActionPlan.festival(ActionPlan.Moment.of(LocalDateTime.of(2026,9,25,15,0))).contains("中秋赏月吃月饼")&&ActionPlan.festival(ActionPlan.Moment.of(LocalDateTime.of(2026,6,19,15,0))).contains("吃粽子"),"Mid-Autumn and Dragon Boat 2026 from the table");
  check(Arrays.equals(new int[]{6,1,1},ActionPlan.lunar(LocalDate.of(2025,7,25)))&&Arrays.equals(new int[]{11,1,1},ActionPlan.lunar(LocalDate.of(2033,12,22))),"leap months 2025 and 2033");
  check(ActionPlan.lunar(LocalDate.of(2024,2,9))==null&&ActionPlan.lunar(LocalDate.of(2058,1,1))==null&&ActionPlan.festival(ActionPlan.Moment.of(LocalDateTime.of(2070,9,25,15,0))).isEmpty(),"outside the table: no lunar festivals, no crash");
  System.out.println("PASS: "+checks+" action plan checks");
 }
}
