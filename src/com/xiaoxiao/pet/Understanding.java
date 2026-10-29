package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/**
 * Her understanding of how to be with the user ("TA 吐槽工作时似乎更想先被听着"), kept apart from what the user
 * said (memories) and from her own diary. It forms slowly: the once-a-day diary call may add, confirm or retire
 * an item, and every one of those steps must quote the user's own words from that day, verbatim. Items are a
 * sense of tact for the prompt, never facts about the user; forgetting the quoted turns removes them.
 */
final class Understanding {
    static final int KEEP=12,PER_DAY=2,MIN_TEXT=8,MAX_TEXT=60,MIN_QUOTE=3,MAX_QUOTE=80,EVIDENCE_KEPT=3;
    // Labels and diagnoses are not tact: she may notice what helps, not decide what the user is.
    private static final String LABELS="(?s).*(抑郁症|焦虑症|躁郁|人格障碍|心理疾病|心理问题|精神病|自恋|玻璃心|情商低|性格缺陷|有病).*";

    static JSONArray entries(JSONObject state){JSONArray a=state.optJSONArray("understanding");return a==null?new JSONArray():a;}

    /** For the diary request: what she currently thinks, with ids the model may confirm or retire. */
    static JSONArray offered(JSONObject state)throws JSONException {
        JSONArray a=entries(state),out=new JSONArray();
        for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null)out.put(new JSONObject().put("id",u.optString("id")).put("text",u.optString("text")));}
        return out;
    }
    /** Appended to the diary instruction. */
    static final String INSTRUCTION="另外，可以根据这一天用户的原话，整理你对「和 TA 怎么相处更舒服」的理解。"
        +"understanding 最多2条新的，每条 {\"text\":\"……\",\"evidence\":\"用户当天原话片段\"}：text 写成相处分寸（何时多听少说、什么时候适合开玩笑、哪种提醒 TA 不喜欢、TA 更喜欢怎样被安慰或被帮忙），8到60字，用「似乎」「可能」留余地；"
        +"evidence 必须逐字摘自当天用户说的话。只写这一天确实能看出来的，不写性格标签、心理诊断、外貌、隐私或敏感信息，没有把握就留空。"
        +"your_understanding 是你以前形成的理解：当天原话支持某条时，放进 confirmed：{\"id\":\"…\",\"evidence\":\"原话片段\"}；当天原话明显说明某条不对时，放进 outdated，格式相同。没有就空数组。"
        +"完整 JSON：{\"diary\":\"日记内容\",\"understanding\":[],\"confirmed\":[],\"outdated\":[]}。";

    /** What one diary answer changes, every step checked against the day's turns. */
    static final class Update {
        final List<JSONObject> added=new ArrayList<>();final Map<String,JSONObject> confirmed=new LinkedHashMap<>(),outdated=new LinkedHashMap<>();
        boolean empty(){return added.isEmpty()&&confirmed.isEmpty()&&outdated.isEmpty();}
    }
    static Update parse(String raw,List<JSONObject> turns,JSONObject state,String day){
        Update out=new Update();if(raw==null)return out;
        String json=AgentPolicy.envelope(raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$",""));if(json==null)return out;
        JSONObject o;try{o=new JSONObject(json);}catch(JSONException e){return out;}
        Set<String> known=new HashSet<>();JSONArray current=entries(state);for(int i=0;i<current.length();i++)known.add(current.optJSONObject(i)==null?"":current.optJSONObject(i).optString("id"));
        JSONArray news=o.optJSONArray("understanding");
        if(news!=null)for(int i=0;i<news.length()&&out.added.size()<PER_DAY;i++){
            JSONObject n=news.optJSONObject(i);if(n==null)continue;String text=text(n.optString("text"));JSONObject proof=evidence(n.optString("evidence"),turns,day);
            if(text==null||proof==null)continue;
            // A restatement of something she already thinks confirms it instead of adding a near-duplicate.
            String same=similar(current,text);
            try{if(same!=null)out.confirmed.put(same,proof);else out.added.add(new JSONObject().put("text",text).put("evidence",new JSONArray().put(proof)));}catch(JSONException ignored){}
        }
        for(String key:new String[]{"confirmed","outdated"}){JSONArray a=o.optJSONArray(key);if(a==null)continue;
            for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r==null||!known.contains(r.optString("id")))continue;JSONObject proof=evidence(r.optString("evidence"),turns,day);if(proof==null)continue;
                ("confirmed".equals(key)?out.confirmed:out.outdated).put(r.optString("id"),proof);}}
        for(String id:out.outdated.keySet())out.confirmed.remove(id);
        return out;
    }
    private static String text(String value){
        String t=value==null?"":value.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();int n=t.codePointCount(0,t.length());
        return n<MIN_TEXT||n>MAX_TEXT||AgentPolicy.sensitive(t)||t.matches(LABELS)?null:t;
    }
    /** {quote, turn, date} when the quote is the user's own words in one of the day's turns. */
    private static JSONObject evidence(String quote,List<JSONObject> turns,String day){
        String q=quote==null?"":quote.trim();int n=q.codePointCount(0,q.length());if(n<MIN_QUOTE||n>MAX_QUOTE||AgentPolicy.sensitive(q))return null;
        for(JSONObject t:turns)if(t.optString("user").contains(q))try{return new JSONObject().put("quote",q).put("turn",ChatArchive.id(t)).put("date",day);}catch(Exception e){return null;}
        return null;
    }
    private static String similar(JSONArray current,String text){
        for(int i=0;i<current.length();i++){JSONObject u=current.optJSONObject(i);if(u!=null&&AgentPolicy.relevance(text,u.optString("text"))>=0.6)return u.optString("id");}
        return null;
    }

    /** Applies an update: retire, confirm (more evidence, a later "last"), add; keeps the KEEP best supported. */
    static boolean apply(JSONObject state,Update update,long now)throws JSONException {
        if(update.empty())return false;JSONArray a=entries(state);List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject u=a.getJSONObject(i);if(!update.outdated.containsKey(u.optString("id")))rows.add(u);}
        for(JSONObject u:rows){JSONObject proof=update.confirmed.get(u.optString("id"));if(proof==null)continue;
            JSONArray ev=u.optJSONArray("evidence");if(ev==null)u.put("evidence",ev=new JSONArray());ev.put(proof);while(ev.length()>EVIDENCE_KEPT)ev.remove(0);
            u.put("confirmed",u.optInt("confirmed",1)+1).put("last",now);}
        for(JSONObject n:update.added)rows.add(new JSONObject().put("id",UUID.randomUUID().toString()).put("text",n.getString("text")).put("evidence",n.getJSONArray("evidence")).put("confirmed",1).put("first",now).put("last",now));
        // Over the limit, the least supported and least recent go first.
        while(rows.size()>KEEP){JSONObject weakest=rows.get(0);for(JSONObject u:rows)if(u.optInt("confirmed",1)<weakest.optInt("confirmed",1)||u.optInt("confirmed",1)==weakest.optInt("confirmed",1)&&u.optLong("last")<weakest.optLong("last"))weakest=u;rows.remove(weakest);}
        state.put("understanding",new JSONArray(rows));return true;
    }
    /** Drops evidence quoted from these turns; an item left without evidence goes too. */
    static void dropSources(JSONObject state,Collection<String> turnIds){
        JSONArray a=state.optJSONArray("understanding");if(a==null||turnIds.isEmpty())return;
        for(int i=a.length()-1;i>=0;i--){JSONObject u=a.optJSONObject(i);JSONArray ev=u==null?null:u.optJSONArray("evidence");if(ev==null){a.remove(i);continue;}
            for(int j=ev.length()-1;j>=0;j--){JSONObject e=ev.optJSONObject(j);if(e==null||turnIds.contains(e.optString("turn")))ev.remove(j);}
            if(ev.length()==0)a.remove(i);}
    }
    static boolean remove(JSONObject state,String id){
        JSONArray a=state.optJSONArray("understanding");if(a==null)return false;
        for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null&&id.equals(u.optString("id"))){a.remove(i);return true;}}
        return false;
    }
    /** For the chat prompt: up to six items, best supported first, with how often they held. */
    static JSONArray context(JSONObject state){
        JSONArray a=entries(state),out=new JSONArray();List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)rows.add(a.optJSONObject(i));
        rows.sort((x,y)->{int c=Integer.compare(y.optInt("confirmed",1),x.optInt("confirmed",1));return c!=0?c:Long.compare(y.optLong("last"),x.optLong("last"));});
        for(int i=0;i<Math.min(6,rows.size());i++)try{out.put(new JSONObject().put("text",rows.get(i).optString("text")).put("seen_days",rows.get(i).optInt("confirmed",1)));}catch(JSONException ignored){}
        return out;
    }
}
