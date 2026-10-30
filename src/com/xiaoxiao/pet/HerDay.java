package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;

/**
 * Her day so far (docs/CHARACTER_STATE.md, 她的今天): what she really did on screen today, from the clips that
 * played (ActionPlan.DOING), what she was fed and whether it is a hungry meal time. Only real playback and feed
 * records; the log sits in its own preference apart from the user's memories and starts over every day, so it
 * never grows into an invented ongoing life.
 */
final class HerDay {
    static final int MAX=40,SHOW=8,FEEDS=3;
    static final long SAME_FOR=30*60_000L;
    static final String KEY="her.day";

    /** Adds one clip she played; the same activity again within half an hour is the same stretch. */
    static JSONObject record(JSONObject log,String clip,long at,ZoneId zone)throws Exception {
        String doing=ActionPlan.doing(clip);if(doing==null)return log;
        String day=Diary.day(at,zone).toString();
        JSONObject out=log!=null&&day.equals(log.optString("date"))?new JSONObject(log.toString()):new JSONObject().put("date",day).put("items",new JSONArray());
        JSONArray items=out.getJSONArray("items");
        for(int i=items.length()-1;i>=0;i--){JSONObject e=items.getJSONObject(i);if(at-e.optLong("last")>SAME_FOR)break;if(doing.equals(e.optString("doing"))){e.put("last",Math.max(at,e.optLong("last")));return out;}}
        items.put(new JSONObject().put("doing",doing).put("at",at).put("last",at));while(items.length()>MAX)items.remove(0);
        return out;
    }
    static String part(int hour){return hour<5?"凌晨":hour<9?"早上":hour<12?"上午":hour<14?"中午":hour<18?"下午":"晚上";}

    /**
     * The background block, or "" when nothing is known. hungry: no meal yet today (GrowthStore.hungry), said only
     * at the meal times when her body grumbles too (ActionPlan.Moment.peckish), so words and screen agree.
     */
    static String block(JSONObject log,JSONObject state,boolean hungry,long now,ZoneId zone)throws Exception {
        LocalDate today=Diary.day(now,zone);List<String> lines=new ArrayList<>();
        // What she did, oldest first by part of the day, the most recent SHOW stretches.
        List<JSONObject> done=new ArrayList<>();
        if(log!=null&&today.toString().equals(log.optString("date"))){JSONArray items=log.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){JSONObject e=items.optJSONObject(i);if(e!=null&&e.optLong("at")<=now)done.add(e);}}
        if(done.size()>SHOW)done=done.subList(done.size()-SHOW,done.size());
        if(!done.isEmpty()){
            LinkedHashMap<String,LinkedHashSet<String>> parts=new LinkedHashMap<>();
            for(JSONObject e:done)parts.computeIfAbsent(part(Instant.ofEpochMilli(e.optLong("at")).atZone(zone).getHour()),k->new LinkedHashSet<>()).add(e.optString("doing"));
            StringBuilder b=new StringBuilder("今天在屏幕上真的做过：");boolean first=true;
            for(Map.Entry<String,LinkedHashSet<String>> p:parts.entrySet()){if(!first)b.append("；");first=false;b.append(p.getKey()).append(String.join("、",p.getValue()));}
            lines.add(b.append("。被问今天干了什么就照这些说，没做过的不编").toString());
        }
        // What she ate: the feed records of today, newest last.
        List<String> fed=new ArrayList<>();JSONArray a=state==null?null:state.optJSONArray("interactions");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e==null||!"feed".equals(e.optString("kind")))continue;long at=e.optLong("at");
            if(at<=now&&Diary.day(at,zone).equals(today))fed.add(clock(at,zone)+" "+e.optString("note"));}
        if(!fed.isEmpty())lines.add("今天吃到的："+String.join("；",fed.subList(Math.max(0,fed.size()-FEEDS),fed.size())));
        else if(hungry&&ActionPlan.Moment.of(LocalDateTime.ofInstant(Instant.ofEpochMilli(now),zone)).peckish())
            lines.add("到饭点了，今天还没吃上东西，肚子有点空，屏幕上也在嘀咕饿；想说就顺嘴提一句，不讨食，也不怪对方");
        if(lines.isEmpty())return "";
        StringBuilder b=new StringBuilder("\n你的今天（应用记录的真实经历，不是对方说的话）：");for(String l:lines)b.append("\n- ").append(l).append("。");
        return b.toString();
    }
    private static String clock(long at,ZoneId zone){LocalTime t=Instant.ofEpochMilli(at).atZone(zone).toLocalTime();return String.format(Locale.ROOT,"%02d:%02d",t.getHour(),t.getMinute());}

    /** The saved log, or null. */
    static JSONObject today(){try{String raw=Data.prefs().getString(KEY,"");return raw.isEmpty()?null:new JSONObject(raw);}catch(Exception e){return null;}}
    /** PetService.play: remembers a doing clip that really played. */
    static void played(String clip){
        if(ActionPlan.doing(clip)==null)return;
        try{Data.prefs().edit().putString(KEY,record(today(),clip,System.currentTimeMillis(),ZoneId.systemDefault()).toString()).apply();}catch(Exception e){Data.log("main","她的今天暂未记下");}
    }
}
