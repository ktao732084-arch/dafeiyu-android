package com.xiaoxiao.pet;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Which animation plays when. Idle actions follow the local clock, weekday, season and Chinese
 * festivals; emotion and touch reactions vary instead of repeating one clip. Pure Java: the
 * caller supplies the time, the lunar date and the clips that exist in the catalog.
 */
final class ActionPlan {
    /** Local time plus the Chinese lunar date. */
    static final class Moment {
        final LocalDateTime time; final int lunarMonth, lunarDay; final boolean leapMonth, lunarNewYearEve;
        Moment(LocalDateTime time,int lunarMonth,int lunarDay,boolean leapMonth,boolean lunarNewYearEve) {
            this.time=time; this.lunarMonth=lunarMonth; this.lunarDay=lunarDay; this.leapMonth=leapMonth; this.lunarNewYearEve=lunarNewYearEve;
        }
        static Moment of(LocalDateTime time) {
            int[] today=ActionPlan.lunar(time.toLocalDate()),tomorrow=ActionPlan.lunar(time.toLocalDate().plusDays(1));
            boolean eve=tomorrow!=null&&tomorrow[0]==1&&tomorrow[1]==1&&tomorrow[2]==0;
            return today==null?new Moment(time,0,0,false,false):new Moment(time,today[0],today[1],today[2]==1,eve);
        }
        int minute(){ return time.getHour()*60+time.getMinute(); }
        boolean between(int fromHour,int fromMinute,int toHour,int toMinute){ int m=minute(); return m>=fromHour*60+fromMinute && m<toHour*60+toMinute; }
        boolean lunar(int month,int fromDay,int toDay){ return !leapMonth && lunarMonth==month && lunarDay>=fromDay && lunarDay<=toDay; }
        boolean solar(int month,int fromDay,int toDay){ return time.getMonthValue()==month && time.getDayOfMonth()>=fromDay && time.getDayOfMonth()<=toDay; }
        boolean weekend(){ DayOfWeek d=time.getDayOfWeek(); return d==DayOfWeek.SATURDAY||d==DayOfWeek.SUNDAY; }
        boolean night(){ return between(0,0,6,0); }
        boolean late(){ return between(22,30,24,0); }
    }

    // Lunar years 2024-2056: first day (yyyymmdd), leap month (00 = none), then each month's length in order,
    // 1 = 30 days and 0 = 29, a leap month right after its namesake. Generated from Hong Kong Observatory data
    // (lunardate package). android.icu's ChineseCalendar puts Lunar New Year 2027 and 2030 a day off.
    static final String[] LUNAR={
        "2024021000010010110110","20250129061010010101110","2026021700101001001110","2027020600110100100110",
        "20280126051110100100110","2029021300110101010011","2030020300010110101010","20310123030110101101010",
        "2032021100100101101101","20330131110100101011101","2034021900010010101101","2035020800101001001101",
        "20360128061101001001011","2037021500110100100101","2038020400110101010010","20390124051101101010100",
        "2040021200101101011010","2041020100010101101101","20420122020100101011011","2043021000010010011011",
        "20440130071010010010111","2045021700101001001011","2046020600101010100101","20470126051011010100101",
        "2048021400011011010010","2049020200101011011010","20500123030101010110110","2051021100100100110111",
        "20520201080100100101111","2053021900010010010111","2054020800011001001011","20550128060110101001010",
        "2056021500111010100101"
    };
    /** {month, day, leap ? 1 : 0}, or null outside the table, where festivals simply do not show. */
    static int[] lunar(LocalDate date) {
        for(int i=LUNAR.length-1;i>=0;i--) {
            String year=LUNAR[i];LocalDate start=LocalDate.of(Integer.parseInt(year.substring(0,4)),Integer.parseInt(year.substring(4,6)),Integer.parseInt(year.substring(6,8)));
            if(date.isBefore(start))continue;
            long offset=ChronoUnit.DAYS.between(start,date);int leap=Integer.parseInt(year.substring(8,10)),index=10;
            for(int month=1;month<=12;month++)for(int pass=0;pass<(month==leap?2:1);pass++) {
                int length=year.charAt(index++)=='1'?30:29;
                if(offset<length)return new int[]{month,(int)offset+1,pass};
                offset-=length;
            }
            return null;
        }
        return null;
    }

    static final String IDLE="待机呼吸休闲", DRAG="被鼠标拖拽悬空反馈", WOKEN="打瞌睡被惊醒", STARTLED="被吓一跳", ANNOYED="点击回应-傲娇生气";
    static final List<String> SLEEP=Arrays.asList("原地小憩沉眠","哈欠连天","打瞌睡被惊醒");
    static final String DOZE="原地小憩沉眠",YAWN="哈欠连天",CAUGHT="偷吃零食被抓住";
    // How she reacts to you between clips: looks your way right after you interact, grumbles a while after
    // being poked too much, and wanders about when nobody has paid attention to her for hours.
    static final List<String> ATTENTION=Arrays.asList("东张西望","碎碎念-对屏碎碎念","点击回应-元气挥手","小幅度原地360度旋转展示");
    static final List<String> SULKING=Arrays.asList("碎碎念-对屏碎碎念","用鲸鱼尾巴拍打地面");
    static final List<String> LONELY=Arrays.asList("碎碎念-发呆碎碎念","工作状态-原地踱步张望","东张西望","碎碎念-擦桌碎碎念");
    static final List<String> TAPS=Arrays.asList("点击回应-开心跃动","点击回应-害羞惊讶","点击回应-挠痒咯咯笑","点击回应-元气挥手");
    // Always fine whatever the hour or season.
    static final List<String> EVERYDAY=Arrays.asList("悠闲哼歌","超大伸懒腰","原地敲击桌面互动","原地重力下蹲压缩","女仆屈膝礼仪","被吓一跳",
        "小幅度原地360度旋转展示","偷吃零食被抓住","用鲸鱼尾巴拍打地面","照镜子","整体换装试色","轻快记录","东张西望","碎碎念-擦桌碎碎念","碎碎念-发呆碎碎念");
    static final List<String> WORK=Arrays.asList("写代码","轻快记录","原地敲击桌面互动","工作状态-忙碌点按","工作状态-思考冒泡","工作状态-清点归档","工作状态-原地踱步张望");
    static final List<String> PLAY=Arrays.asList("原地专心玩魔方","原地蹲下玩玩具汽车","鲸鱼吐泡泡特效","原地跳跃抓碎头顶物品","玩游戏气急败坏","小提琴演奏",
        "蓝鲸现世","优雅女仆舞","轻快摇摆舞","可爱宅舞","吹气球","动物环绕","拆礼物","变鸽子","扑克魔术","抽陀螺","吹笛子","撸猫","凭空生花","骑木马","三球抛接","踢毽子","下五子棋","荡秋千");
    static final Map<String,List<String>> EMOTIONS=new HashMap<>();
    static {
        EMOTIONS.put("happy",Arrays.asList("点击回应-开心跃动","点击回应-元气挥手","轻快摇摆舞","工作状态-雀跃庆祝"));
        EMOTIONS.put("shy",Arrays.asList("点击回应-害羞惊讶","女仆屈膝礼仪"));
        EMOTIONS.put("curious",Arrays.asList("东张西望","工作状态-思考冒泡","深度思考碎碎念"));
        EMOTIONS.put("concerned",Arrays.asList("碎碎念-对屏碎碎念","工作状态-思考冒泡"));
        EMOTIONS.put("playful",Arrays.asList("鲸鱼吐泡泡特效","点击回应-挠痒咯咯笑","变鸽子","凭空生花","扑克魔术"));
        EMOTIONS.put("sleepy",Arrays.asList("原地小憩沉眠","哈欠连天"));
        EMOTIONS.put("calm",Arrays.asList("碎碎念-发呆碎碎念","悠闲哼歌","碎碎念-擦桌碎碎念"));
    }
    // Special clips have their own cooldown, and their kind shares one too: lunch happens once per lunch,
    // and a festival day shows one festival clip about every hour and a half rather than at every pick.
    static final Set<String> MEALS=new HashSet<>(Arrays.asList("吃早餐","吃午餐","吃白饭","吃晚餐","涮火锅","吃大闸蟹"));
    static final Set<String> SNACKS=new HashSet<>(Arrays.asList("大口吃零食","吃西瓜","吃冰淇淋融化","吃糖葫芦"));
    static final Set<String> FESTIVALS=new HashSet<>(Arrays.asList("吃饺子","放烟花","写福字","收红包","吃年糕","吃腊八粥","舞狮头","吃汤圆","放孔明灯",
        "吃粽子","穿针乞巧","放河灯","中秋赏月吃月饼","吃重阳糕","插茱萸赏菊","吃青团","讨糖南瓜灯","萌化小幽灵","装点圣诞树"));
    static final Set<String> SEASONS=new HashSet<>(Arrays.asList("放风筝","蝴蝶蜜蜂环绕头顶开花","摇扇纳凉","玩水枪","被落叶淹没","堆雪人"));
    static final Set<String> ONCE=new HashSet<>(Arrays.asList("晨间刷牙","是啊，吃什么"));
    static final long MINUTE=60_000L;
    static final long ATTENTIVE_FOR=90_000L,SULKS_FOR=2*MINUTE,LONELY_AFTER=180*MINUTE,AWAKE_AFTER_WAKING=10*MINUTE;
    static final int RECENT=8;

    private volatile Set<String> available;
    // Growth: no meal yet today makes the meal-time grumbling likelier; just fed, she is content for half an hour.
    volatile boolean hungry; volatile long fedAt=-1;
    static final List<String> HUNGRY=Arrays.asList("是啊，吃什么","偷吃零食被抓住"), FULL=Arrays.asList("悠闲哼歌","超大伸懒腰");
    private final ArrayDeque<String> recent=new ArrayDeque<>();
    private final Map<String,Long> played=new HashMap<>();
    private final ArrayDeque<Long> taps=new ArrayDeque<>();
    private String lastTap="",lastEmotion="";
    // Wall-clock milliseconds, the same clock as Moment.
    private volatile long lastInteraction=-1,annoyedAt=-1,awakeUntil=0;

    ActionPlan(Collection<String> available){ this.available=new HashSet<>(available); }
    /** Clips unlocked by the growth chapter; locked ones leave the idle pools and the emotion choices. */
    void available(Collection<String> available){ this.available=new HashSet<>(available); }

    /** Festival clips for this day; daytime only, applied by idle(). */
    static List<String> festival(Moment m) {
        List<String> out=new ArrayList<>();
        if(m.lunarNewYearEve) out.addAll(Arrays.asList("吃饺子","放烟花","写福字","收红包"));
        else if(m.lunar(12,23,30)) out.addAll(Arrays.asList("写福字","吃年糕"));
        if(m.lunar(12,7,8)) out.add("吃腊八粥");
        if(m.lunar(1,1,7)) out.addAll(Arrays.asList("收红包","放烟花","舞狮头","吃饺子","吃年糕"));
        if(m.lunar(1,13,15)) out.addAll(Arrays.asList("吃汤圆","放孔明灯","舞狮头"));
        if(m.lunar(5,3,5)) out.add("吃粽子");
        if(m.lunar(7,7,7)) out.add("穿针乞巧");
        if(m.lunar(7,14,15)) out.add("放河灯");
        if(m.lunar(8,13,16)) out.add("中秋赏月吃月饼");
        if(m.lunar(9,8,9)) out.addAll(Arrays.asList("吃重阳糕","插茱萸赏菊"));
        if(m.solar(4,3,6)) out.add("吃青团");
        if(m.solar(12,21,23)) out.addAll(Arrays.asList("吃饺子","吃汤圆"));
        if(m.solar(10,29,31)) out.addAll(Arrays.asList("讨糖南瓜灯","萌化小幽灵"));
        if(m.solar(12,20,25)) out.add("装点圣诞树");
        if(m.solar(12,31,31)||m.solar(1,1,1)) out.add("放烟花");
        return out;
    }
    /** Non-food seasonal clips; seasonal food belongs to meal and snack times in slot(). */
    static List<String> season(Moment m) {
        switch(m.time.getMonthValue()) {
            case 3: case 4: case 5: return Arrays.asList("放风筝","蝴蝶蜜蜂环绕头顶开花");
            case 6: case 7: case 8: return Arrays.asList("摇扇纳凉","玩水枪");
            case 9: return Collections.emptyList();
            case 10: case 11: return Arrays.asList("被落叶淹没");
            default: return Arrays.asList("堆雪人");
        }
    }
    /** What this part of the day is about; empty when nothing in particular. */
    static List<String> slot(Moment m) {
        int month=m.time.getMonthValue();boolean winter=month==12||month<=2,summer=month>=6&&month<=8,autumn=month>=9&&month<=11;
        if(m.between(6,0,9,0)) return m.between(7,0,9,0)?Arrays.asList("晨间刷牙","超大伸懒腰","哈欠连天","吃早餐"):Arrays.asList("晨间刷牙","超大伸懒腰","哈欠连天");
        if(m.between(11,0,11,30)||m.between(17,0,17,30)) return Arrays.asList("是啊，吃什么","偷吃零食被抓住");
        if(m.between(11,30,13,0)) return Arrays.asList("吃午餐","吃午餐","吃午餐","吃白饭"); // plain rice is the joke lunch, one in four
        if(m.between(13,0,14,0)) return Arrays.asList("哈欠连天","打瞌睡被惊醒","原地小憩沉眠");
        if(m.between(15,0,16,30)) return summer?Arrays.asList("大口吃零食","吃西瓜","吃冰淇淋融化","偷吃零食被抓住"):winter?Arrays.asList("大口吃零食","吃糖葫芦","偷吃零食被抓住"):Arrays.asList("大口吃零食","偷吃零食被抓住");
        if(m.between(17,30,19,30)) return winter?Arrays.asList("吃晚餐","涮火锅"):autumn?Arrays.asList("吃晚餐","吃大闸蟹"):Arrays.asList("吃晚餐");
        if(!m.weekend()&&(m.between(9,0,11,0)||m.between(14,0,17,0))) return WORK;
        if(m.between(19,30,22,30)||m.weekend()&&m.between(9,0,22,30)) return PLAY;
        if(m.late()) return Arrays.asList("哈欠连天","原地小憩沉眠","打瞌睡被惊醒","悠闲哼歌");
        return Collections.emptyList();
    }
    /** Shared cooldown group of a clip, or null for ordinary clips that only follow the no-repeat rule. */
    static String kind(String action) {
        if(FESTIVALS.contains(action)) return "@festival";
        if(SEASONS.contains(action)) return "@season";
        if(MEALS.contains(action)) return "@meal";
        if(SNACKS.contains(action)) return "@snack";
        return ONCE.contains(action)?action:null;
    }
    static long cooldown(String key) {
        switch(key) {
            case "@festival": return 90*MINUTE;
            case "@season": return 120*MINUTE;
            case "@meal": case "@snack": return 150*MINUTE;
            default: return (FESTIVALS.contains(key)?180:SEASONS.contains(key)?240:180)*MINUTE;
        }
    }

    static long epoch(Moment m){ return m.time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    /** From midnight to six she sleeps for real: the doze clip loops until a tap, a lift or a message wakes her. */
    boolean asleep(Moment m){ return m.night()&&epoch(m)>=awakeUntil; }
    static boolean dozing(String current){ return SLEEP.contains(current)&&!WOKEN.equals(current); }
    void interacted(long now){ lastInteraction=now; }
    /** Wakes her if she is dozing; she then stays up for ten minutes before nodding off again. */
    boolean wake(long now,String current) {
        interacted(now);
        if(!dozing(current))return false;
        awakeUntil=now+AWAKE_AFTER_WAKING;return true;
    }

    /** The next idle clip. Late night is sleepy and quiet; daytime mixes the hour, festivals, season, everyday clips and how you have treated her. */
    String idle(Moment m,Random random) {
        long now=epoch(m);if(lastInteraction<0)lastInteraction=now-ATTENTIVE_FOR; // the lonely clock starts; nobody has just touched her
        String pick;
        if(asleep(m)) {
            // A yawn first, then dozing on a loop.
            String last=recent.peekLast();pick=(last==null||!SLEEP.contains(last))&&has(YAWN)?YAWN:has(DOZE)?DOZE:null;
        }
        else if(m.night()) {
            // Only three sleepy clips exist, so night skips the no-repeat rule: mostly dozing, now and then a yawn.
            List<String> options=new ArrayList<>();for(String a:SLEEP)if(has(a))options.add(a);
            pick=options.isEmpty()?null:options.contains(SLEEP.get(0))&&random.nextInt(10)<6?SLEEP.get(0):options.get(random.nextInt(options.size()));
        }
        else {
            List<List<String>> pools=new ArrayList<>();List<Integer> weights=new ArrayList<>();
            if(m.between(8,0,22,30)) add(pools,weights,festival(m),3,now);
            add(pools,weights,slot(m),m.late()?6:4,now);
            if(m.between(8,0,22,30)) add(pools,weights,season(m),1,now);
            if(hungry&&(m.between(11,0,11,30)||m.between(15,0,16,30)||m.between(17,0,17,30))) add(pools,weights,HUNGRY,3,now);
            if(fedAt>0&&now>=fedAt&&now-fedAt<30*MINUTE) add(pools,weights,FULL,4,now);
            add(pools,weights,m.weekend()||m.between(19,30,22,30)?merge(EVERYDAY,PLAY):EVERYDAY,3,now);
            if(annoyedAt>=0&&now-annoyedAt<SULKS_FOR) add(pools,weights,SULKING,8,now);
            else if(now-lastInteraction<ATTENTIVE_FOR) add(pools,weights,ATTENTION,6,now);
            if(now-lastInteraction>=LONELY_AFTER&&m.between(8,0,22,30)) add(pools,weights,LONELY,4,now);
            pick=null;int total=0;for(int w:weights)total+=w;
            if(total>0){int r=random.nextInt(total);for(int i=0;i<pools.size();i++){r-=weights.get(i);if(r<0){pick=choose(pools.get(i),now,random);break;}}}
            if(pick==null) pick=choose(EVERYDAY,now,random);
        }
        return remember(pick==null?IDLE:pick,now);
    }
    /** Pause after a clip: about half a minute by day, several minutes at night so she is not busy while you sleep. */
    static long gapMillis(Moment m,Random random) {
        if(m.night()) return 180_000+random.nextInt(180_000);
        if(m.late()) return 60_000+random.nextInt(60_000);
        return 25_000+random.nextInt(30_000);
    }
    /**
     * A tap (now in wall-clock ms): wakes her if she was dozing, annoys her when poked four times in five
     * seconds, catches her mid-snack or startles her at work, otherwise a varied happy reaction.
     */
    String tap(long now,String current,Random random) {
        while(!taps.isEmpty()&&now-taps.peekFirst()>5000)taps.removeFirst();
        taps.addLast(now);
        String pick;
        if(wake(now,current)) pick=has(WOKEN)?WOKEN:STARTLED;
        else if(taps.size()>=4&&has(ANNOYED)) {pick=ANNOYED;taps.clear();annoyedAt=now;}
        else if(eating(current)&&has(CAUGHT)) pick=CAUGHT;
        else if(WORK.contains(current)&&has(STARTLED)) pick=STARTLED;
        else {List<String> options=new ArrayList<>();for(String a:TAPS)if(has(a)&&!a.equals(lastTap))options.add(a);pick=options.isEmpty()?lastTap:options.get(random.nextInt(options.size()));}
        lastTap=pick;return pick;
    }
    static boolean eating(String action){ return action.startsWith("吃")||MEALS.contains(action)||SNACKS.contains(action)||action.equals("涮火锅"); }
    /** What a clip shows her doing, in words the model can use ("拉小提琴"); null for reactions, idling and UI feedback. */
    static final Map<String,String> DOING=new HashMap<>();
    static{String[] pairs={"悠闲哼歌","哼歌","超大伸懒腰","伸懒腰","原地敲击桌面互动","敲着桌子玩","哈欠连天","打哈欠","原地小憩沉眠","打盹","偷吃零食被抓住","偷吃零食","用鲸鱼尾巴拍打地面","用尾巴拍着地面玩","照镜子","照镜子","整体换装试色","试新衣服的颜色","轻快记录","记东西","写代码","写代码","摇扇纳凉","摇扇子乘凉","晨间刷牙","刷牙","原地专心玩魔方","玩魔方","原地蹲下玩玩具汽车","玩玩具汽车","鲸鱼吐泡泡特效","吐泡泡","原地跳跃抓碎头顶物品","跳起来抓头顶的东西","玩游戏气急败坏","玩游戏，玩得气急败坏","玩水枪","玩水枪","小提琴演奏","拉小提琴","优雅女仆舞","跳舞","轻快摇摆舞","跳舞","可爱宅舞","跳舞","吹气球","吹气球","动物环绕","和一群小动物待在一起","放风筝","放风筝","拆礼物","拆礼物","变鸽子","变鸽子魔术","扑克魔术","变扑克魔术","抽陀螺","抽陀螺","吹笛子","吹笛子","蝴蝶蜜蜂环绕头顶开花","头顶开花，引来蝴蝶蜜蜂","撸猫","撸猫","凭空生花","变出花来","骑木马","骑木马","三球抛接","抛球杂耍","踢毽子","踢毽子","下五子棋","一个人摆弄五子棋（只是小动作，不是和用户的对局）","荡秋千","荡秋千","吃白饭","吃白饭","大口吃零食","吃零食","吃Token","吃 Token","吃早餐","吃早餐","吃午餐","吃午餐","吃晚餐","吃晚餐","吃冰淇淋融化","吃冰淇淋","吃大闸蟹","吃大闸蟹","吃糖葫芦","吃糖葫芦","吃长寿面","吃长寿面","吃西瓜","吃西瓜","涮火锅","涮火锅","被落叶淹没","在落叶堆里玩","中秋赏月吃月饼","赏月吃月饼","堆雪人","堆雪人","放烟花","放烟花","吃粽子","吃粽子","吃年糕","吃年糕","吃青团","吃青团","吃腊八粥","喝腊八粥","吃重阳糕","吃重阳糕","收红包","收红包","写福字","写福字","穿针乞巧","穿针乞巧","舞狮头","舞狮","讨糖南瓜灯","提着南瓜灯讨糖","插茱萸赏菊","插茱萸赏菊","放河灯","放河灯","萌化小幽灵","扮小幽灵","装点圣诞树","装点圣诞树","放孔明灯","放孔明灯","吃汤圆","吃汤圆","吃饺子","吃饺子"};for(int i=0;i<pairs.length;i+=2)DOING.put(pairs[i],pairs[i+1]);}
    static String doing(String clip){return clip==null?null:DOING.get(clip);}
    /**
     * The clip for something she says she is doing ("我去拉会儿小提琴"), so her body follows her words. Only first-person
     * statements count, within one clause and not negated; "你在吃西瓜吗" or "我不想吃冰淇淋" never start a clip.
     */
    private static final String[][] SAID={{"(拉|练)[^，。！？!?\\n]{0,3}小提琴","小提琴演奏"},{"吹[^，。！？!?\\n]{0,3}笛子","吹笛子"},{"(玩|拧|转)[^，。！？!?\\n]{0,3}魔方","原地专心玩魔方"},{"吃[^，。！？!?\\n]{0,3}冰淇淋","吃冰淇淋融化"},{"吃[^，。！？!?\\n]{0,3}西瓜","吃西瓜"},{"吃[^，。！？!?\\n]{0,3}糖葫芦","吃糖葫芦"},{"吃[^，。！？!?\\n]{0,3}零食","大口吃零食"},{"放[^，。！？!?\\n]{0,3}风筝","放风筝"},{"踢[^，。！？!?\\n]{0,3}毽子","踢毽子"},{"荡[^，。！？!?\\n]{0,3}秋千","荡秋千"},{"吹[^，。！？!?\\n]{0,3}气球","吹气球"},{"抽[^，。！？!?\\n]{0,3}陀螺","抽陀螺"},{"吐[^，。！？!?\\n]{0,3}泡泡","鲸鱼吐泡泡特效"},{"哼[^，。！？!?\\n]{0,3}歌","悠闲哼歌"},{"伸[^，。！？!?\\n]{0,2}懒腰","超大伸懒腰"},{"写[^，。！？!?\\n]{0,3}代码","写代码"},{"跳[^，。！？!?\\n]{0,3}舞","轻快摇摆舞"},{"照[^，。！？!?\\n]{0,2}镜子","照镜子"},{"摇[^，。！？!?\\n]{0,2}扇子","摇扇纳凉"}};
    private static final java.util.regex.Pattern[] SAID_PATTERNS=new java.util.regex.Pattern[SAID.length];
    static{for(int i=0;i<SAID.length;i++)SAID_PATTERNS[i]=java.util.regex.Pattern.compile("(?:我|咱)(?:先|去|来|正|正在|在|要|想|继续|接着|刚|刚刚|刚才)?[^，。！？!?你不没别\\n]{0,4}"+SAID[i][0]);}
    static String said(String reply){
        if(reply==null)return null;
        for(int i=0;i<SAID.length;i++)if(SAID_PATTERNS[i].matcher(reply).find())return SAID[i][1];
        return null;
    }
    /** What the model is told about a tap, so her next words can follow from it. */
    static String tapNote(String before,String reaction) {
        if(dozing(before)) return "你在打盹时被用户戳醒了，还有点迷糊";
        if(ANNOYED.equals(reaction)) return "用户连续戳了你好几下";
        if(eating(before)) return "你正吃着东西，被用户戳了一下";
        if(WORK.contains(before)) return "你正忙着，被用户戳了一下";
        return "用户轻轻碰了碰你";
    }
    /** Emotion after a reply: the canonical clip half the time, otherwise a related one, never the same twice running. */
    String emotion(String emotion,String canonical,Random random) {
        List<String> options=new ArrayList<>();for(String a:EMOTIONS.getOrDefault(emotion,Collections.emptyList()))if(has(a)&&!a.equals(lastEmotion))options.add(a);
        String pick=options.isEmpty()?canonical:options.contains(canonical)&&random.nextBoolean()?canonical:options.get(random.nextInt(options.size()));
        lastEmotion=pick;return pick;
    }

    boolean has(String action){ return available.contains(action); }
    private void add(List<List<String>> pools,List<Integer> weights,List<String> pool,int weight,long now) {
        for(String a:pool)if(usable(a,now)){pools.add(pool);weights.add(weight);return;}
    }
    private boolean usable(String action,long now) {
        if(!has(action)||recent.contains(action))return false;
        String kind=kind(action);return kind==null||rested(action,now)&&rested(kind,now);
    }
    private boolean rested(String key,long now){ Long last=played.get(key); return last==null||now-last>=cooldown(key); }
    private String choose(List<String> pool,long now,Random random) {
        List<String> options=new ArrayList<>();for(String a:pool)if(usable(a,now))options.add(a);
        return options.isEmpty()?null:options.get(random.nextInt(options.size()));
    }
    private String remember(String action,long now) {
        recent.addLast(action);while(recent.size()>RECENT)recent.removeFirst();
        String kind=kind(action);if(kind!=null){played.put(action,now);played.put(kind,now);}
        return action;
    }
    private static List<String> merge(List<String> a,List<String> b){ List<String> out=new ArrayList<>(a);out.addAll(b);return out; }
}
