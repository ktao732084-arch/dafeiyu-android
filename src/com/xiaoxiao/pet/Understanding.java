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
    static final Set<String> SCENES=new HashSet<>(Arrays.asList("general","venting","advice","celebration","play"));
    static final Set<String> TOPICS=new HashSet<>(Arrays.asList("any","work","study","relationships","health"));
    static String scene(String text){
        if(text.matches("(?s).*(?:这次|现在)(?:我)?(?:要|需要|想要).*(?:建议|办法|方案).*"))return "advice";
        if(text.matches("(?s).*(先听|先被听|只想.*吐槽|别.*建议|不要.*建议|不急着.*建议|不想.*道理).*"))return "venting";
        if(text.matches("(?s).*(建议|怎么办|怎么做|帮我.*(想|选|分析)|给我.*(方案|办法)).*"))return "advice";
        if(text.matches("(?s).*(吐槽|烦死|委屈|难过|被.*骂|想哭|心烦).*"))return "venting";
        if(text.matches("(?s).*(好消息|升职|考过|通过了|成功了|太开心).*"))return "celebration";
        if(text.matches("(?s).*(开玩笑|逗我|讲.*笑话|玩.*游戏|猜谜).*"))return "play";
        return "general";
    }
    static String topic(String text){
        if(text.matches("(?s).*(工作|领导|老板|同事|加班|公司).*"))return "work";
        if(text.matches("(?s).*(学习|考试|作业|论文|老师|上课).*"))return "study";
        if(text.matches("(?s).*(朋友|家人|父母|恋爱|分手|男友|女友).*"))return "relationships";
        if(text.matches("(?s).*(身体|生病|医院|不舒服|疼痛).*"))return "health";
        return "any";
    }
    static String scope(JSONObject row,String key){return row.has(key)?row.optString(key):"scene".equals(key)?scene(row.optString("text")):topic(row.optString("text"));}
    static String scopeLabel(JSONObject row){String s=scope(row,"scene"),t=scope(row,"topic");
        String a="venting".equals(s)?"倾诉时":"advice".equals(s)?"求建议时":"celebration".equals(s)?"分享好消息时":"play".equals(s)?"玩笑互动时":"一般相处";
        String b="work".equals(t)?"工作·":"study".equals(t)?"学习·":"relationships".equals(t)?"人际·":"health".equals(t)?"身体状态·":"";return b+a;
    }

    static JSONArray entries(JSONObject state){JSONArray a=state.optJSONArray("understanding");return a==null?new JSONArray():a;}

    /** For the diary request: what she currently thinks, with ids the model may confirm or retire. */
    static JSONArray offered(JSONObject state)throws JSONException {
        JSONArray a=entries(state),out=new JSONArray();
        for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null)out.put(new JSONObject().put("id",u.optString("id")).put("text",u.optString("text")).put("scene",scope(u,"scene")).put("topic",scope(u,"topic")));}
        return out;
    }
    /** Appended to the diary instruction. */
    static final String INSTRUCTION="另外，可以根据这一天用户的原话，整理你对「和 TA 怎么相处更舒服」的理解。"
        +"understanding 最多2条新的，每条 {\"text\":\"……\",\"evidence\":\"用户当天原话片段\"}：text 写成相处分寸（何时多听少说、什么时候适合开玩笑、哪种提醒 TA 不喜欢、TA 更喜欢怎样被安慰或被帮忙），8到60字，用「似乎」「可能」留余地；"
        +"每条还要写 scene（general/venting/advice/celebration/play）和 topic（any/work/study/relationships/health）：分别表示一般相处/倾诉/求建议/分享好消息/玩笑，以及领域。限定在原话支持的范围，吐槽工作不代表所有场合都不要建议；明确的范围不能写成general或any。"
        +"evidence 必须逐字摘自当天用户说的话。只写这一天确实能看出来的，不写性格标签、心理诊断、外貌、隐私或敏感信息，没有把握就留空。"
        +"your_understanding 是你以前形成的理解：当天原话支持某条时，放进 confirmed：{\"id\":\"…\",\"evidence\":\"原话片段\"}；当天原话明显说明某条不对时，放进 outdated，格式相同。没有就空数组。";
    /** The whole answer of the diary call when it also reads the user (Diary.request). */
    static final String FORMAT="完整 JSON：{\"diary\":\"日记内容\",\"understanding\":[],\"confirmed\":[],\"outdated\":[],\"phrases\":[]}。";

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
            String scene=n.optString("scene",scene(text)),topic=n.optString("topic",topic(text));
            if(!SCENES.contains(scene)||!TOPICS.contains(topic))continue;
            // A restatement of something she already thinks confirms it instead of adding a near-duplicate.
            String same=similar(current,text,scene,topic);
            try{if(same!=null)out.confirmed.put(same,proof);else out.added.add(new JSONObject().put("text",text).put("scene",scene).put("topic",topic).put("evidence",new JSONArray().put(proof)));}catch(JSONException ignored){}
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
    private static String similar(JSONArray current,String text,String scene,String topic){
        for(int i=0;i<current.length();i++){JSONObject u=current.optJSONObject(i);if(u!=null&&scene.equals(scope(u,"scene"))&&topic.equals(scope(u,"topic"))&&AgentPolicy.relevance(text,u.optString("text"))>=0.6)return u.optString("id");}
        return null;
    }

    /** Applies an update: retire, confirm (more evidence, a later "last"), add; keeps the KEEP best supported. */
    static boolean apply(JSONObject state,Update update,long now)throws JSONException {
        if(update.empty())return false;JSONArray a=entries(state);List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject u=a.getJSONObject(i);if(!update.outdated.containsKey(u.optString("id")))rows.add(u);}
        for(JSONObject u:rows){JSONObject proof=update.confirmed.get(u.optString("id"));if(proof==null)continue;
            JSONArray ev=u.optJSONArray("evidence");if(ev==null)u.put("evidence",ev=new JSONArray());ev.put(proof);while(ev.length()>EVIDENCE_KEPT)ev.remove(0);
            u.put("confirmed",u.optInt("confirmed",1)+1).put("last",now);}
        for(JSONObject n:update.added)rows.add(new JSONObject().put("id",UUID.randomUUID().toString()).put("text",n.getString("text")).put("scene",scope(n,"scene")).put("topic",scope(n,"topic")).put("evidence",n.getJSONArray("evidence")).put("confirmed",1).put("first",now).put("last",now));
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
    static JSONArray context(JSONObject state){return context(state,"");}
    /** Conservative local scene matching; an unrecognised situation never activates a scoped preference. */
    static JSONArray context(JSONObject state,String query){
        String scene=scene(query),topic=topic(query);
        JSONArray a=entries(state),out=new JSONArray();List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject row=a.optJSONObject(i);if(row==null)continue;
            String s=scope(row,"scene"),t=scope(row,"topic");
            if(("general".equals(s)||s.equals(scene))&&("any".equals(t)||t.equals(topic)))rows.add(row);}
        rows.sort((x,y)->{int xs=("general".equals(scope(x,"scene"))?0:1)+("any".equals(scope(x,"topic"))?0:1),ys=("general".equals(scope(y,"scene"))?0:1)+("any".equals(scope(y,"topic"))?0:1);int c=Integer.compare(ys,xs);if(c==0)c=Integer.compare(y.optInt("confirmed",1),x.optInt("confirmed",1));return c!=0?c:Long.compare(y.optLong("last"),x.optLong("last"));});
        for(int i=0;i<Math.min(3,rows.size());i++)try{out.put(new JSONObject().put("text",rows.get(i).optString("text")).put("applies_when",scopeLabel(rows.get(i))).put("seen_days",rows.get(i).optInt("confirmed",1)));}catch(JSONException ignored){}
        return out;
    }
}
