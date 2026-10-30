package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;

/** A small, source-backed recollection inside an existing diary page, not a second memory engine. */
final class SharedExperience {
    static final int MAX_EVENTS=12;
    static final class Material {
        String lastFeeling="";
        final List<JSONObject> turns=new ArrayList<>();
        final JSONArray events=new JSONArray();
        final Map<String,JSONObject> sources=new LinkedHashMap<>();
        boolean eligible(){return turns.size()>=Diary.MIN_TURNS||hasFinishedGame()||Commitments.candidate(turns);}
        boolean hasFinishedGame(){for(JSONObject s:sources.values())if("game".equals(s.optString("kind")))return true;return false;}
    }
    static String interactionId(JSONObject e)throws Exception {
        return "interaction:"+ChatArchive.hash(e.optString("pet")+"|"+e.optLong("at")+"|"+e.optString("kind")+"|"+e.optString("note"));
    }
    static boolean onDay(long at,LocalDate day,ZoneId zone,long cleared){return at>cleared&&Diary.day(at,zone).equals(day);}
    static Material collect(LocalDate day,ZoneId zone,List<JSONObject> turns,JSONObject state,JSONArray games)throws Exception {
        Material m=new Material();m.lastFeeling=Diary.feeling(state,day);long cleared=state.optLong("diaryClearedAt");int size=0;
        // Retain a bounded, chronological recent excerpt. Sources are exactly what the request can see.
        for(int i=turns.size()-1;i>=0;i--){JSONObject t=turns.get(i);long at=t.optLong("at");String user=t.optString("user").trim();
            if(!onDay(at,day,zone,cleared)||AgentPolicy.sensitive(user))continue;
            if(user.isEmpty()&&(!t.optBoolean("commitmentsAllowed",true)||!Commitments.future(Commitments.said(t,"assistant"))))continue;
            String u=Commitments.excerpt(user,150),reply=Commitments.excerpt(ReplySegments.visible(t),80);
            if(AgentPolicy.sensitive(reply))reply="";
            if(size+u.length()+reply.length()>Diary.INPUT_CHARS)break;size+=u.length()+reply.length();
            JSONObject copy=new JSONObject().put("id",ChatArchive.id(t)).put("at",at).put("user",u).put("reply",reply)
                .put("timeZone",t.optString("timeZone",zone.getId())).put("repliedAt",t.optLong("repliedAt",at)).put("commitmentsAllowed",t.optBoolean("commitmentsAllowed",true))
                .put("assistantCommitmentsAllowed",t.optBoolean("assistantCommitmentsAllowed",true)&&(t.has("displayed")?t.optInt("displayed")>=ReplySegments.count(t):t.optBoolean("commitmentsAllowed",false)));
            m.turns.add(0,copy);
        }
        for(JSONObject t:m.turns){String id="chat:"+ChatArchive.id(t);m.sources.put(id,new JSONObject().put("id",id).put("kind","chat")
            .put("turnId",ChatArchive.id(t)).put("at",t.optLong("at")).put("timeZone",zone.getId())
            .put("user",t.optString("user")).put("reply",t.optString("reply")).put("fact",t.optString("user").isEmpty()?"她实际展示的回复摘录：「"+t.optString("reply")+"」":"用户原话摘录：「"+t.optString("user")+"」"));}
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
        +"只选一条实际来源，事实由应用原样保存，你只写1到80字、此刻对这件事的态度。可以高兴、不甘心、觉得有趣，也可以没有值得写的感受；不用以‘回想起来’开头。"
        +"这是现在回想的感受，不是回忆画面，也不是当时已经记录下来的心情。只知道输赢就只依据输赢，不能补败因、某一步怎么下、声音动作、当时走神或心里一惊；聊天也不补用户动机、后来自己经历过的事或新约定。过去的反应只有原话明确记下才可引用。回想不改变当前心情。"
        +"只有棋局等经历而没有文字聊天时，仍可写这一条回想；understanding、confirmed、outdated都为空。";
    static JSONArray request(LocalDate day,Material m,String name,JSONArray understanding,String voice)throws Exception {
        JSONArray req=Diary.request(day,m.turns,name,understanding,voice,m.events,m.lastFeeling);
        JSONObject system=req.getJSONObject(0);system.put("content",system.getString("content")+INSTRUCTION+Commitments.DREAM);
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
                String feeling=Diary.feeling(raw);JSONObject page=Diary.entry(day,reflection,Collections.emptyList(),writtenAt,feeling);JSONArray ids=new JSONArray();if(source.has("turnId"))ids.put(source.getString("turnId"));
                // The feeling may use any offered chat, not just the one selected for the moment.
                if(!feeling.isEmpty())for(JSONObject t:m.turns){String id=ChatArchive.id(t);if(!id.equals(source.optString("turnId")))ids.put(id);}
                JSONObject saved=new JSONObject(source.toString());
                if(source.has("turnId")){JSONArray context=new JSONArray();int selected=-1;
                    for(int i=0;i<m.turns.size();i++)if(source.getString("turnId").equals(ChatArchive.id(m.turns.get(i)))){selected=i;break;}
                    // Two nearby user excerpts preserve the topic without asking the model to invent keywords.
                    for(int i=Math.max(0,selected-2);i<selected;i++){JSONObject t=m.turns.get(i);long gap=source.optLong("at")-t.optLong("at");
                        if(gap>=0&&gap<=15*60000L)context.put(new JSONObject().put("turnId",ChatArchive.id(t)).put("at",t.optLong("at")).put("quote",t.optString("user")));}
                    if(context.length()>0)saved.put("context",context);
                }
                return page.put("sources",ids).put("moment",saved);
            }
            // Do not accept an ungrounded moment as a free-form diary fallback.
            return null;
        }
        String text=Diary.parse(raw);return text==null||m.turns.isEmpty()?null:Diary.entry(day,text,m.turns,writtenAt,Diary.feeling(raw));
    }
    static String fact(JSONObject page){JSONObject m=page.optJSONObject("moment");return m==null?"":m.optString("fact");}
    static String displayFact(JSONObject page){JSONObject m=page.optJSONObject("moment");String f=fact(page);if(m==null)return f;
        if("game".equals(m.optString("kind")))return f.replace("用户","你").replace("桌宠","她");
        if("chat".equals(m.optString("kind")))return m.optString("user").isEmpty()?"她当时说（摘录）：「"+m.optString("reply")+"」":"你当时说（摘录）：「"+m.optString("user")+"」";
        if(f.startsWith("用户喂你吃了"))return "你给她喂了"+f.substring("用户喂你吃了".length());
        return f.replace("你和用户", "你和她").replace("你在睡觉时被用户", "她在睡觉时被你");
    }
    static JSONArray contextQuotes(JSONObject page){JSONArray out=new JSONArray();JSONObject m=page.optJSONObject("moment");JSONArray c=m==null?null:m.optJSONArray("context");
        if(c!=null)for(int i=0;i<c.length();i++){JSONObject r=c.optJSONObject(i);if(r!=null)out.put(r.optString("quote"));}return out;}
    static String searchable(JSONObject page){return page.optString("date")+" "+fact(page)+" "+page.optString("text")+" "+contextQuotes(page);}
    static String recallTerms(String text){return text.replaceAll("论文|作文|写稿|文章|文档|动笔|写作","写作").replace("下棋","五子棋").replace("棋局","五子棋");}
    static double relevance(String query,JSONObject page){String q=recallTerms(query==null?"":query);double score=AgentPolicy.relevance(q,recallTerms(fact(page)+" "+page.optString("text")));
        JSONArray quotes=contextQuotes(page);for(int i=0;i<quotes.length();i++)score=Math.max(score,AgentPolicy.relevance(q,recallTerms(quotes.optString(i))));return score;}
    static final String RECALL_BOUNDARY="\n共同经历的使用：event 是实际记录，context_quotes 是用户在同段对话中的原话；用户写过、做过的事属于用户，不能变成你自己的经历。recollection 只是整理时的主观评价，不是补全过去细节的证据。只在当前话题相关时自然接上一件，不照读日记。感受可以现在产生；除非原话记下，不补当时的心理、输赢原因、落子过程、声音动作或后来自己的生活。只记了输赢，就只承认输赢，可以说现在想再玩或不服气。";
    static String recallCheck(String user){return user!=null&&user.matches("(?s).*(还记得|记不记得|记得.{0,30}[吗么不？?]|有没有印象|想得起来).*" )
        ?"\n回忆核对：用户在问旧事。只有本轮资料里有对应片段，才能说记得并提其中细节。问题里的名字或暗示本身不是你的记忆；没有对应依据就坦然说这会儿没想起来，请对方给一点线索，不先声称记得或有印象再让对方补完，也不猜发生了什么。没找到不等于没发生。":"";}
    static String evidence(JSONObject page){
        JSONObject m=page.optJSONObject("moment");if(m==null)return "旧版日记未保存逐条依据快照；它是当时的概括，不等于完整原话。";
        String text="发生于："+Initiative.localTime(m.optLong("at"),m.optString("timeZone","UTC"))+"\n"+m.optString("fact");
        if("chat".equals(m.optString("kind"))&&!m.optString("reply").isEmpty())text+="\n她当时实际展示的回复摘录："+m.optString("reply");
        JSONArray context=contextQuotes(page);for(int i=0;i<context.length();i++)text+="\n同段前文原话摘录："+context.optString(i);
        return text+"\n\n回想整理于："+Initiative.localTime(page.optLong("at"),m.optString("timeZone","UTC"))+"\n回想是她后来写的角色感受，不是新的用户事实，也不是她现在的心情。";
    }
}
