package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;

/**
 * The growth main line (docs/GROWTH_DESIGN.md): one number, bond, earned from real, local interactions;
 * chapters gated by both bond and the days since the first meeting; chapter scenes, unlocked clips and free
 * feeding. Pure Java over one JSON state, so every rule runs in the JVM tests; GrowthStore keeps the file.
 * Nothing here takes model output: the model can neither award bond nor advance a chapter.
 */
final class Growth {
    static final int SCHEMA=1, MAX_CHAPTER=5, LEDGER=500, CARDS=20;
    static final long MINUTE=60_000L, HOUR=60*MINUTE, DAY=24*HOUR, FED_FOR=30*MINUTE;
    static final int EVENT_BOND=20, CHAT_TURNS=3;

    /** Per-day rewards: {bond, times a day}. Chat counts once a day however long the conversation. */
    static final Map<String,int[]> DAILY=new LinkedHashMap<>();
    static {
        DAILY.put("met",new int[]{5,1});
        DAILY.put("chat",new int[]{10,1});
        DAILY.put("feed",new int[]{2,3});
        DAILY.put("touch",new int[]{1,5});
        DAILY.put("memory",new int[]{3,3});
    }

    static final class Chapter {
        final int id,bond,day;final String name,lore,unlocks;final List<String[]> scene,neutral;
        Chapter(JSONObject o)throws Exception{id=o.getInt("id");bond=o.getInt("bond");day=o.getInt("day");name=o.getString("name");lore=o.getString("lore");unlocks=o.getString("unlocks");scene=steps(o.getJSONArray("scene"));neutral=steps(o.getJSONArray("neutral"));}
        String title(){return "第"+id+"章「"+name+"」";}
    }
    static final class Food {
        final String name,clip;final int chapter;
        Food(JSONObject o)throws Exception{name=o.getString("name");clip=o.getString("clip");chapter=o.getInt("chapter");}
    }
    /** assets/story.json: chapter scripts, which clips each chapter unlocks, foods and greetings. */
    static final class Story {
        final List<Chapter> chapters=new ArrayList<>();final Map<String,Integer> unlock=new HashMap<>();final List<Food> foods=new ArrayList<>();
        final JSONObject greetings;
        Story(JSONObject o)throws Exception{
            if(o.getInt("schemaVersion")!=1)throw new IllegalStateException("剧情资源版本不支持");
            JSONArray c=o.getJSONArray("chapters");for(int i=0;i<c.length();i++)chapters.add(new Chapter(c.getJSONObject(i)));
            if(chapters.size()!=MAX_CHAPTER)throw new IllegalStateException("剧情资源应有5章");
            for(int i=0;i<MAX_CHAPTER;i++){Chapter ch=chapters.get(i);if(ch.id!=i+1||i>0&&(ch.bond<=chapters.get(i-1).bond||ch.day<chapters.get(i-1).day))throw new IllegalStateException("章节门槛应逐章递增");}
            JSONObject u=o.getJSONObject("unlocks");
            for(Iterator<String> k=u.keys();k.hasNext();){String key=k.next();int chapter=key.equals("always")?0:Integer.parseInt(key);JSONArray a=u.getJSONArray(key);for(int i=0;i<a.length();i++)if(unlock.put(a.getString(i),chapter)!=null)throw new IllegalStateException("动作重复归类："+a.getString(i));}
            JSONArray f=o.getJSONArray("foods");for(int i=0;i<f.length();i++)foods.add(new Food(f.getJSONObject(i)));
            greetings=o.getJSONObject("greetings");
        }
        Chapter chapter(int id){return chapters.get(Math.max(1,Math.min(MAX_CHAPTER,id))-1);}
        Food food(String name){for(Food f:foods)if(f.name.equals(name))return f;return null;}
    }
    private static List<String[]> steps(JSONArray a)throws Exception{List<String[]> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONArray s=a.getJSONArray(i);out.add(new String[]{s.getString(0),s.getString(1),s.optString(2,null)});}return out;}

    // ---- state ----

    static JSONObject fresh(long now)throws Exception {
        return base(now).put("pending",new JSONArray().put(1));
    }
    private static JSONObject base(long now)throws Exception {
        return new JSONObject().put("schemaVersion",SCHEMA).put("createdAt",now).put("bond",0).put("chapter",1).put("legacy",false)
            .put("chapterAt",new JSONObject().put("1",now)).put("pending",new JSONArray()).put("daily",new JSONObject()).put("ledger",new JSONArray())
            .put("cards",new JSONArray()).put("seen",new JSONObject()).put("known",new JSONObject().put("memories",new JSONArray()).put("diary",new JSONArray()))
            .put("popped",0).put("feeds",0).put("lastFed",0).put("lastMet",0);
    }
    /**
     * Growth once also had dried fish (a currency for food and bentos) and trips with postcards; both were
     * removed to keep one main line. An older file keeps its bond, chapter, cards and collections; the
     * removed parts are dropped. Returns whether anything changed.
     */
    static boolean simplify(JSONObject g){
        boolean changed=false;for(String key:new String[]{"fish","trip","postcards"})if(g.has(key)){g.remove(key);changed=true;}
        return changed;
    }
    static int chapter(JSONObject g){return Math.max(1,Math.min(MAX_CHAPTER,g.optInt("chapter",1)));}
    static int bond(JSONObject g){return g.optInt("bond");}
    /** 相识第几天, counted like AgentPolicy.relationship(): the first meeting is day 1. */
    static int day(long since,long now){return since<=0||now<since?0:(int)((now-since)/DAY)+1;}
    static String date(long now,ZoneId zone){return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();}

    /** Today's counters. A clock turned back keeps the later day's counters, so nothing is awarded twice. */
    static JSONObject daily(JSONObject g,long now,ZoneId zone)throws Exception {
        String today=date(now,zone);JSONObject d=g.optJSONObject("daily");
        if(d==null||d.optString("date").isEmpty()||today.compareTo(d.optString("date"))>0){d=new JSONObject().put("date",today);g.put("daily",d);}
        return d;
    }
    static int today(JSONObject g,String kind,long now,ZoneId zone)throws Exception{return daily(g,now,zone).optInt(kind);}

    static final class Award {
        int bond;boolean first,chapterUp;
        boolean any(){return bond>0;}
    }
    private static void ledger(JSONObject g,long now,String type,int bond,String ref)throws Exception {
        JSONArray a=g.getJSONArray("ledger");JSONObject row=new JSONObject().put("at",now).put("type",type).put("bond",bond);if(ref!=null)row.put("ref",ref);a.put(row);
        // Older rows fold into one summary, so the totals stay explainable without an unbounded file.
        while(a.length()>LEDGER){JSONObject first=a.getJSONObject(0),second=a.getJSONObject(1);boolean summary="earlier".equals(first.optString("type"));
            JSONObject merged=new JSONObject().put("type","earlier").put("at",second.optLong("at")).put("bond",first.optInt("bond")+second.optInt("bond")).put("count",(summary?first.optInt("count"):1)+1);
            a.remove(0);a.put(0,merged);}
    }
    private static void add(JSONObject g,long now,String type,int bond,String ref,Award out)throws Exception {
        if(bond==0)return;g.put("bond",bond(g)+bond);ledger(g,now,type,bond,ref);
        if(out!=null)out.bond+=bond;
    }
    /** A capped daily reward; returns whether it was still available today. */
    private static boolean daily(JSONObject g,String kind,long now,ZoneId zone,String ref,Award out)throws Exception {
        int[] rule=DAILY.get(kind);JSONObject d=daily(g,now,zone);int used=d.optInt(kind);if(used>=rule[1])return false;
        d.put(kind,used+1);add(g,now,kind,rule[0],ref,out);return true;
    }

    /** The first time you meet each day: five bond. Days apart cost nothing. */
    static Award met(JSONObject g,long now,ZoneId zone)throws Exception {
        Award out=new Award();long last=g.optLong("lastMet");
        out.first=daily(g,"met",now,zone,null,out);
        if(now>last)g.put("lastMet",now);
        return out;
    }
    /** One committed user turn (not proactive, not a history question). The third today earns the chat reward. */
    static Award chatted(JSONObject g,long now,ZoneId zone)throws Exception {
        Award out=new Award();JSONObject d=daily(g,now,zone);int turns=d.optInt("turns")+1;d.put("turns",turns);
        if(turns>=CHAT_TURNS)daily(g,"chat",now,zone,null,out);return out;
    }
    /** A tap she enjoyed; poking her until she is annoyed does not count. */
    static Award touched(JSONObject g,long now,ZoneId zone)throws Exception {
        Award out=new Award();daily(g,"touch",now,zone,null,out);return out;
    }
    static boolean available(JSONObject g,Food f){return g.optBoolean("legacy")||chapter(g)>=f.chapter;}
    /** Feeding is free; foods unlock with the chapters. Returns null on success or why not. */
    static String feed(JSONObject g,Food f,long now,ZoneId zone,Award out)throws Exception {
        if(f==null)return "没有这种食物";
        if(!available(g,f))return f.name+"要到第"+f.chapter+"章才能喂";
        daily(g,"feed",now,zone,f.name,out);g.put("feeds",g.optInt("feeds")+1).put("lastFed",now);return null;
    }
    static boolean hungry(JSONObject g,long now,ZoneId zone)throws Exception{return today(g,"feed",now,zone)==0;}
    static boolean justFed(JSONObject g,long now){long at=g.optLong("lastFed");return at>0&&now>=at&&now-at<FED_FOR;}

    /**
     * Keeps the bubble jar in step with the memory ledger and diary. New memories written today earn bond (three
     * a day); ids that disappeared were forgotten or cleared, so their bubbles pop and chapter cards that quoted
     * them fall back to their wording without the quote. Bond and chapter never go down.
     */
    static Award sync(JSONObject g,JSONArray memories,JSONArray archive,JSONArray diary,long now,ZoneId zone)throws Exception {
        Award out=new Award();JSONObject known=g.getJSONObject("known");
        Set<String> live=new LinkedHashSet<>(),kept=new HashSet<>();String today=date(now,zone);
        if(memories!=null)for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);live.add(m.optString("id"));}
        kept.addAll(live);if(archive!=null)for(int i=0;i<archive.length();i++)kept.add(archive.getJSONObject(i).optString("id"));
        Set<String> before=strings(known.getJSONArray("memories"));int popped=0;
        for(String id:before)if(!live.contains(id))popped++;
        if(memories!=null)for(int i=0;i<memories.length();i++){JSONObject m=memories.getJSONObject(i);String id=m.optString("id");
            if(!before.contains(id)&&today.equals(date(m.optLong("at"),zone)))daily(g,"memory",now,zone,null,out);}
        Set<String> days=new LinkedHashSet<>();if(diary!=null)for(int i=0;i<diary.length();i++)days.add(diary.getJSONObject(i).optString("date"));
        for(String day:strings(known.getJSONArray("diary")))if(!days.contains(day))popped++;
        known.put("memories",new JSONArray(live)).put("diary",new JSONArray(days));
        if(popped>0)g.put("popped",g.optInt("popped")+popped);
        // A card keeps its quotes only while every quoted memory still exists, current or archived.
        JSONArray cards=g.getJSONArray("cards");
        for(int i=0;i<cards.length();i++){JSONObject c=cards.getJSONObject(i);JSONArray src=c.optJSONArray("sources");if(src==null||src.length()==0)continue;
            boolean intact=true;for(int j=0;j<src.length();j++)if(!kept.contains(src.optString(j)))intact=false;
            if(!intact)c.put("lines",c.getJSONArray("fallback")).put("sources",new JSONArray());}
        return out;
    }
    private static Set<String> strings(JSONArray a){Set<String> out=new LinkedHashSet<>();for(int i=0;i<a.length();i++)out.add(a.optString(i));return out;}
    /** Bubbles in the jar: current memories, diary pages and chapter cards. */
    static int bubbles(JSONObject g){JSONObject k=g.optJSONObject("known");JSONArray cards=g.optJSONArray("cards");return (k==null?0:k.optJSONArray("memories").length()+k.optJSONArray("diary").length())+(cards==null?0:cards.length());}

    /** Moves up as many chapters as both bond and the days since the first meeting allow. */
    static boolean advance(JSONObject g,Story s,long since,long now)throws Exception {
        boolean moved=false;int day=day(since,now);
        while(chapter(g)<MAX_CHAPTER){Chapter next=s.chapter(chapter(g)+1);if(bond(g)<next.bond||day<next.day)break;
            g.put("chapter",next.id);g.getJSONObject("chapterAt").put(String.valueOf(next.id),now);g.getJSONArray("pending").put(next.id);moved=true;}
        return moved;
    }
    static int pending(JSONObject g){JSONArray p=g.optJSONArray("pending");return p==null||p.length()==0?0:p.optInt(0);}

    // ---- unlocks and the action collection ----

    /** 0 for clips that are always there (daily routine, festivals, seasons), else the chapter that unlocks it. */
    static int chapterOf(Story s,String action){
        if(ActionPlan.FESTIVALS.contains(action)||ActionPlan.SEASONS.contains(action))return 0;
        Integer c=s.unlock.get(action);return c==null?1:c;
    }
    /** Clips she can play. Companions from before 0.5.0 keep every clip they already had (蓝鲸现世 included). */
    static Set<String> unlocked(JSONObject g,Story s,Collection<String> all){
        Set<String> out=new LinkedHashSet<>();boolean legacy=g.optBoolean("legacy");int chapter=chapter(g);
        for(String a:all)if(legacy||chapterOf(s,a)<=chapter)out.add(a);return out;
    }
    static boolean seen(JSONObject g,String action,long now)throws Exception {
        JSONObject seen=g.getJSONObject("seen");if(action==null||action.isEmpty()||seen.has(action))return false;seen.put(action,now);return true;
    }

    // ---- lore and the relationship stage given to the model ----

    /** Lore up to the current chapter only; later chapters stay unsaid. Custom personas get none. */
    static String lore(Story s,int chapter){
        StringBuilder out=new StringBuilder("你的来历（会随着相处慢慢想起来；只说到这里为止，不要补充新的经历、家人或同伴，也不要提前说出还没想起的事）：");
        for(int i=1;i<=Math.max(1,Math.min(MAX_CHAPTER,chapter));i++)out.append("\n- ").append(s.chapter(i).lore);
        return out.toString();
    }

    // ---- chapter scenes ----

    /** Real material for a scene: memories as the user said them, oldest first; never generated text. */
    static final class Material {
        final List<JSONObject> memories=new ArrayList<>();String you="你";int days,bubbles,feeds;
        Material(JSONArray active,String nickname,int days,int bubbles,int feeds){
            if(active!=null)for(int i=0;i<active.length();i++){JSONObject m=active.optJSONObject(i);if(m!=null&&!m.optString("text").trim().isEmpty())memories.add(m);}
            memories.sort(Comparator.comparingLong(m->m.optLong("at")));
            if(nickname!=null&&!nickname.trim().isEmpty())you=nickname.trim();this.days=days;this.bubbles=bubbles;this.feeds=feeds;
        }
    }
    static final class Scene {
        final int chapter;final String title;final List<String[]> steps=new ArrayList<>();final List<String> fallback=new ArrayList<>();final JSONArray sources=new JSONArray();
        Scene(int chapter,String title){this.chapter=chapter;this.title=title;}
        List<String> lines(){List<String> out=new ArrayList<>();for(String[] s:steps)out.add(s[1]);return out;}
    }
    static String quote(String text){String t=text.replaceAll("\\s+"," ").trim();return t.codePointCount(0,t.length())>40?t.substring(0,t.offsetByCodePoints(0,39))+"…":t;}
    /** Which memories a chapter quotes: the first for chapter 2, a pinned (or the second) one for 3, three spread out for the letter. */
    static List<JSONObject> picks(int chapter,Material m){
        List<JSONObject> all=m.memories,out=new ArrayList<>();if(all.isEmpty())return out;
        if(chapter==2)out.add(all.get(0));
        else if(chapter==3){JSONObject pick=null;for(JSONObject r:all)if(r.optBoolean("pinned")){pick=r;break;}out.add(pick!=null?pick:all.get(Math.min(1,all.size()-1)));}
        else if(chapter==4){out.add(all.get(0));if(all.size()>2)out.add(all.get(all.size()/2));if(all.size()>1)out.add(all.get(all.size()-1));}
        return out;
    }
    static String fill(String line,Map<String,String> values){
        if(line==null)return null;String out=line;
        for(Map.Entry<String,String> e:values.entrySet()){String key="{"+e.getKey()+"}";if(out.contains(key)){if(e.getValue()==null)return null;out=out.replace(key,e.getValue());}}
        return out;
    }
    static Scene scene(Story s,int chapter,boolean custom,Material m){
        Chapter c=s.chapter(chapter);Scene out=new Scene(c.id,c.title());List<JSONObject> used=picks(c.id,m);
        Map<String,String> real=values(m,used),plain=values(m,Collections.emptyList());
        for(String[] step:custom?c.neutral:c.scene){
            String line=fill(step[1],real);if(line==null)line=fill(step[2],real);
            String safe=fill(step[1],plain);if(safe==null)safe=fill(step[2],plain);
            if(line!=null){out.steps.add(new String[]{step[0],line});if(safe!=null)out.fallback.add(safe);}
        }
        out.steps.add(new String[]{"","（"+c.title()+" · 解锁："+c.unlocks+"）"});out.fallback.add("（"+c.title()+" · 解锁："+c.unlocks+"）");
        boolean quoted=false;for(String[] step:out.steps)for(JSONObject r:used)if(step[1].contains(quote(r.optString("text"))))quoted=true;
        if(quoted)for(JSONObject r:used)out.sources.put(r.optString("id"));
        return out;
    }
    private static Map<String,String> values(Material m,List<JSONObject> used){
        Map<String,String> v=new LinkedHashMap<>();v.put("you",m.you);v.put("days",String.valueOf(Math.max(1,m.days)));v.put("bubbles",String.valueOf(m.bubbles));v.put("feeds",String.valueOf(m.feeds));
        v.put("memory",used.isEmpty()?null:quote(used.get(0).optString("text")));
        StringBuilder letter=new StringBuilder();for(JSONObject r:used)letter.append(letter.length()>0?"、":"").append("「").append(quote(r.optString("text"))).append("」");
        v.put("letter",used.isEmpty()?null:letter.toString());return v;
    }
    /** After a scene has been shown: a chapter card for the story page, plus the one-time reward. */
    static Award shown(JSONObject g,Scene scene,long now,boolean recap)throws Exception {
        Award out=new Award();JSONArray p=g.getJSONArray("pending");for(int i=p.length()-1;i>=0;i--)if(p.optInt(i)==scene.chapter)p.remove(i);
        JSONArray cards=g.getJSONArray("cards");for(int i=0;i<cards.length();i++)if(cards.getJSONObject(i).optInt("chapter")==scene.chapter)return out;
        cards.put(new JSONObject().put("chapter",scene.chapter).put("title",scene.title).put("at",now).put("recap",recap).put("lines",new JSONArray(scene.lines())).put("fallback",new JSONArray(scene.fallback)).put("sources",scene.sources));
        while(cards.length()>CARDS)cards.remove(0);
        if(!recap)add(g,now,"chapter",EVENT_BOND,String.valueOf(scene.chapter),out);return out;
    }

    // ---- greetings and lines ----

    static String slot(int hour){return hour>=5&&hour<11?"morning":hour>=11&&hour<14?"noon":hour>=14&&hour<18?"afternoon":hour>=18&&hour<23?"evening":"night";}
    static String pick(JSONArray lines,Random random,String you){if(lines==null||lines.length()==0)return "";return lines.optString(random.nextInt(lines.length())).replace("{you}",you);}
    static String greeting(Story s,int hour,Random random,String you){return pick(s.greetings.optJSONArray(slot(hour)),random,you);}

    // ---- upgrading a companion from 0.4.x ----

    /**
     * One-time estimate for an existing companion from what is already on the phone, capped at what the same
     * number of days could have earned. Earlier chapters become recap cards; the current one plays once.
     * Every clip stays unlocked (legacy), so nothing the user had is taken away.
     */
    static JSONObject migrate(Story s,long now,long since,int chatDays,JSONArray memories,JSONArray diary,int interactions,Material m,boolean custom,String version)throws Exception {
        JSONObject g=base(now).put("legacy",true).put("migratedFrom",version);int day=Math.max(1,day(since,now));
        int memoryCount=memories==null?0:memories.length(),diaryCount=diary==null?0:diary.length();
        double estimate=chatDays*15+Math.min(memoryCount,100)*3+diaryCount*5+Math.min(interactions,200)*.5;
        g.put("bond",(int)Math.min(estimate,day*45.0));
        ledger(g,now,"migrated",g.optInt("bond"),version);
        int chapter=1;for(int i=2;i<=MAX_CHAPTER;i++){Chapter c=s.chapter(i);if(g.optInt("bond")>=c.bond&&day>=c.day)chapter=i;}
        g.put("chapter",chapter);for(int i=2;i<=chapter;i++)g.getJSONObject("chapterAt").put(String.valueOf(i),now);
        Set<String> ids=new LinkedHashSet<>();if(memories!=null)for(int i=0;i<memoryCount;i++)ids.add(memories.getJSONObject(i).optString("id"));
        Set<String> days=new LinkedHashSet<>();if(diary!=null)for(int i=0;i<diaryCount;i++)days.add(diary.getJSONObject(i).optString("date"));
        g.getJSONObject("known").put("memories",new JSONArray(ids)).put("diary",new JSONArray(days));
        for(int i=1;i<chapter;i++)shown(g,scene(s,i,custom,m),now,true);
        g.getJSONArray("pending").put(chapter);g.put("lastMet",now);
        return g;
    }
}
