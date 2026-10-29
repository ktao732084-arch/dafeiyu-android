package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;

/** A small, source-backed recollection inside an existing diary page, not a second memory engine. */
final class SharedExperience {
    static final int MAX_EVENTS=12;
    static final class Material {
        final List<JSONObject> turns=new ArrayList<>();
        final JSONArray events=new JSONArray();
        final Map<String,JSONObject> sources=new LinkedHashMap<>();
        boolean eligible(){return turns.size()>=Diary.MIN_TURNS||hasFinishedGame();}
        boolean hasFinishedGame(){for(JSONObject s:sources.values())if("game".equals(s.optString("kind")))return true;return false;}
    }
    static String interactionId(JSONObject e)throws Exception {
        return "interaction:"+ChatArchive.hash(e.optString("pet")+"|"+e.optLong("at")+"|"+e.optString("kind")+"|"+e.optString("note"));
    }
    static boolean onDay(long at,LocalDate day,ZoneId zone,long cleared){return at>cleared&&Diary.day(at,zone).equals(day);}
    static Material collect(LocalDate day,ZoneId zone,List<JSONObject> turns,JSONObject state,JSONArray games)throws Exception {
        Material m=new Material();long cleared=state.optLong("diaryClearedAt");int size=0;
        // Retain a bounded, chronological recent excerpt. Sources are exactly what the request can see.
        for(int i=turns.size()-1;i>=0;i--){JSONObject t=turns.get(i);long at=t.optLong("at");String user=t.optString("user").trim();
            if(user.isEmpty()||!onDay(at,day,zone,cleared)||AgentPolicy.sensitive(user))continue;
            String u=AgentPolicy.clip(user,150),reply=AgentPolicy.clip(ReplySegments.visible(t),80);
            if(AgentPolicy.sensitive(reply))reply="";
            if(size+u.length()+reply.length()>Diary.INPUT_CHARS)break;size+=u.length()+reply.length();
            JSONObject copy=new JSONObject().put("id",ChatArchive.id(t)).put("at",at).put("user",u).put("reply",reply);
            m.turns.add(0,copy);
        }
        for(JSONObject t:m.turns){String id="chat:"+ChatArchive.id(t);m.sources.put(id,new JSONObject().put("id",id).put("kind","chat")
            .put("turnId",ChatArchive.id(t)).put("at",t.optLong("at")).put("timeZone",zone.getId())
            .put("user",t.optString("user")).put("reply",t.optString("reply")).put("fact","用户原话摘录：「"+t.optString("user")+"」"));}
        List<JSONObject> events=new ArrayList<>();JSONArray interactions=state.optJSONArray("interactions");
        if(interactions!=null)for(int i=0;i<interactions.length();i++){JSONObject e=interactions.optJSONObject(i);if(e==null)continue;
            long at=e.optLong("at");String note=e.optString("note");if(!CharacterNow.EXPERIENCES.contains(e.optString("kind"))||!onDay(at,day,zone,cleared)||note.isEmpty()||AgentPolicy.sensitive(note))continue;
            events.add(new JSONObject().put("id",interactionId(e)).put("kind","interaction").put("at",at).put("timeZone",e.optString("timeZone",zone.getId()))
                .put("eventKind",e.optString("kind")).put("fact",note));}
        // Finished game records are durable; this input comes from the day's archive, not recent_results (last 8).
        if(games!=null)for(int i=0;i<games.length();i++){JSONObject g=games.optJSONObject(i);if(g==null)continue;long at=g.optLong("finishedAt");String outcome=g.optString("outcome");
            if(g.optString("id").isEmpty()||!onDay(at,day,zone,cleared)||!Arrays.asList("user_win","pet_win","draw").contains(outcome))continue;
            String fact="五子棋对局："+("user_win".equals(outcome)?"用户赢，桌宠输。":"pet_win".equals(outcome)?"桌宠赢，用户输。":"平局。");
            events.add(new JSONObject().put("id","game:"+g.getString("id")).put("kind","game").put("gameId",g.getString("id"))
                .put("at",at).put("timeZone",g.optString("timeZone",zone.getId())).put("outcome",outcome).put("fact",fact));}
        // Keep games in the bounded selection even on a day with many physical interactions.
        events.sort((a,b)->{int c=Boolean.compare("game".equals(b.optString("kind")),"game".equals(a.optString("kind")));return c!=0?c:Long.compare(b.optLong("at"),a.optLong("at"));});
        List<JSONObject> picked=new ArrayList<>(events.subList(0,Math.min(MAX_EVENTS,events.size())));picked.sort(Comparator.comparingLong(e->e.optLong("at")));
        for(JSONObject e:picked){m.events.put(new JSONObject().put("source_id",e.getString("id")).put("time",Initiative.localTime(e.getLong("at"),e.getString("timeZone"))).put("what",e.getString("fact")));m.sources.put(e.getString("id"),e);}
        return m;
    }
    static final String INSTRUCTION="可以从素材中选一个值得留下的共同片段，额外返回 moment：{\"source_id\":\"素材中已有的source_id\",\"reflection\":\"你现在回想这件事的一句话感受\"}，没有就null。"
        +"只选一条实际来源，事实由应用原样保存，你只写1到80字的回想；回想不是当时已经记录下来的心情，不推测用户动机，不补写其他事件或约定，不改变当前心情。"
        +"只有棋局等经历而没有文字聊天时，仍可写这一条回想；understanding、confirmed、outdated都为空。";
    static JSONArray request(LocalDate day,Material m,String name,JSONArray understanding,String voice)throws Exception {
        JSONArray req=Diary.request(day,m.turns,name,understanding,voice,m.events);
        JSONObject system=req.getJSONObject(0);system.put("content",system.getString("content")+INSTRUCTION);
        JSONObject material=new JSONObject(req.getJSONObject(1).getString("content"));JSONArray talk=material.getJSONArray("conversation");
        for(int i=0;i<talk.length();i++)talk.getJSONObject(i).put("source_id","chat:"+ChatArchive.id(m.turns.get(i)));
        while(m.turns.size()>talk.length()){JSONObject omitted=m.turns.remove(m.turns.size()-1);m.sources.remove("chat:"+ChatArchive.id(omitted));}
        req.getJSONObject(1).put("content",material.toString());return req;
    }
    static JSONObject entry(String raw,LocalDate day,Material m,long writtenAt)throws Exception {
        JSONObject output=null;try{String json=AgentPolicy.envelope(raw==null?"":raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$",""));if(json!=null)output=new JSONObject(json);}catch(JSONException ignored){}
        JSONObject proposed=output==null?null:output.optJSONObject("moment");
        if(output!=null&&output.has("moment")&&!output.isNull("moment")&&proposed==null)return null;
        if(proposed!=null){JSONObject source=m.sources.get(proposed.optString("source_id"));String reflection=proposed.optString("reflection").trim();
            if(!(proposed.opt("source_id") instanceof String)||!(proposed.opt("reflection") instanceof String))return null;
            int length=reflection.codePointCount(0,reflection.length());
            if(source!=null&&length>0&&length<=80&&!AgentPolicy.sensitive(reflection)&&!reflection.matches("(?s).*[\\p{Cntrl}].*")){
                JSONObject page=Diary.entry(day,reflection,Collections.emptyList(),writtenAt);JSONArray ids=new JSONArray();if(source.has("turnId"))ids.put(source.getString("turnId"));
                return page.put("sources",ids).put("moment",new JSONObject(source.toString()));
            }
            // Do not accept an ungrounded moment as a free-form diary fallback.
            return null;
        }
        String text=Diary.parse(raw);return text==null||m.turns.isEmpty()?null:Diary.entry(day,text,m.turns,writtenAt);
    }
    static String fact(JSONObject page){JSONObject m=page.optJSONObject("moment");return m==null?"":m.optString("fact");}
    static String displayFact(JSONObject page){JSONObject m=page.optJSONObject("moment");String f=fact(page);if(m==null)return f;
        if("game".equals(m.optString("kind")))return f.replace("用户","你").replace("桌宠","她");
        if("chat".equals(m.optString("kind")))return "你当时说（摘录）：「"+m.optString("user")+"」";
        if(f.startsWith("用户喂你吃了"))return "你给她喂了"+f.substring("用户喂你吃了".length());
        return f.replace("你和用户", "你和她").replace("你在睡觉时被用户", "她在睡觉时被你");
    }
    static String searchable(JSONObject page){return page.optString("date")+" "+fact(page)+" "+page.optString("text");}
    static String evidence(JSONObject page){
        JSONObject m=page.optJSONObject("moment");if(m==null)return "旧版日记未保存逐条依据快照；它是当时的概括，不等于完整原话。";
        String text="发生于："+Initiative.localTime(m.optLong("at"),m.optString("timeZone","UTC"))+"\n"+m.optString("fact");
        if("chat".equals(m.optString("kind"))&&!m.optString("reply").isEmpty())text+="\n她当时实际展示的回复摘录："+m.optString("reply");
        return text+"\n\n回想整理于："+Initiative.localTime(page.optLong("at"),m.optString("timeZone","UTC"))+"\n回想是她后来写的角色感受，不是新的用户事实，也不是她现在的心情。";
    }
}
