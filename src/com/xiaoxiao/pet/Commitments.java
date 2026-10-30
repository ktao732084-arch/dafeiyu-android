package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/** Verbatim promises by either speaker. A record of saying it, never proof that it was done. */
final class Commitments {
    static final int KEEP=200;
    static final String[] STATE_KEYS={"commitments","commitmentsForgotten","commitmentsClearedAt","commitmentsFull"};
    static JSONArray entries(JSONObject state){JSONArray a=state.optJSONArray("commitments");return a==null?new JSONArray():a;}
    static void copyState(JSONObject from,JSONObject to)throws Exception {for(String key:STATE_KEYS){Object v=from.opt(key);if(v==null){to.remove(key);continue;}to.put(key,v instanceof JSONArray?new JSONArray(v.toString()):v instanceof JSONObject?new JSONObject(v.toString()):v);}}
    static String sourceKey(String speaker,String turn){return speaker+":"+turn;}
    static boolean blocked(JSONObject state,String speaker,JSONObject turn)throws Exception {
        if(turn.optLong("at")<=state.optLong("commitmentsClearedAt")||!turn.optBoolean("commitmentsAllowed",true))return true;
        JSONArray a=state.optJSONArray("commitmentsForgotten");String key=sourceKey(speaker,ChatArchive.id(turn));
        if(a!=null)for(int i=0;i<a.length();i++)if(key.equals(a.optString(i)))return true;return false;
    }
    static boolean safe(String text){return text!=null&&text.length()>=8&&text.length()<=160&&!AgentPolicy.sensitive(text)
        &&!text.matches("(?s).*吧[。！!]*$")
        &&!text.matches("(?s).*[？?吗么].*|.*(如果|假如|假设|要是|比如|例如|开玩笑|角色扮演|你说|他说|她说|曾经|当时|以前|已经|做完了|完成了|取消|不用了|不能|没法|不会记得|建议|推荐|不如|要不要|你可以|你应该|我没答应|我没有答应|我不答应|我想|希望|打算|也许|可能|大概|不一定|争取|尽量).*" )
        &&!text.matches("(?s).*(我|你|他|她)(是不是|会不会|能不能|该不该|是否).*" )
        &&!text.matches("(?s)^(?:(?:好|这样吧)[，, ]*)?(我(?:的)?(?:朋友|同事|妈妈|爸爸)|他|她|别人|你)(?:明天|今晚|会|答应|保证|负责).*" )
        &&!text.matches("(?s).*[「」『』“”\"].*");}
    /** High-precision synchronous fallback. Dream can classify other literal future commitments. */
    static boolean explicit(String text){if(!safe(text))return false;
        return text.matches("(?s).*(我(?:答应|保证|承诺|一定会)|说好了|说定了|一言为定).*" )
            ||text.matches("(?s).*(我(?:会|来|负责|一定).{0,8}(?:帮你|给你|陪你|替你|提醒你|发给你|回你)).*" )
            ||text.matches("(?s).*(?:明天|后天|今晚|晚上|周[一二三四五六日末]|下次|稍后|等会|一会|[一二三四五六七八九十两\\d]+(?:分钟|小时)后).{0,18}(?:我.{0,12})?(?:提醒你|发给你|给你发|帮你|陪你|回复你|告诉你|再来找你|再来陪你).*" );
    }
    static boolean future(String text){return explicit(text)||(safe(text)&&text.matches("(?s).*(明天|后天|今晚|晚上|周[一二三四五六日末]|下次|稍后|等会|一会|分钟后|小时后|我会|我来|我负责).*" )
        &&text.matches("(?s).*(给你|帮你|陪你|替你|提醒你|发你|找你|答应|承诺|保证).*"));}
    static String said(JSONObject turn,String speaker){
        if("user".equals(speaker))return turn.optString("user");
        if(!"assistant".equals(speaker)||!turn.optBoolean("assistantCommitmentsAllowed",turn.has("displayed")||turn.optBoolean("commitmentsAllowed",false)))return "";
        return ReplySegments.visible(turn);
    }
    static boolean grounded(String text,String quote){for(String sentence:text.split("(?<=[。！？!?；;])|\\n"))if(sentence.contains(quote)&&safe(sentence.trim()))return true;return false;}
    static boolean add(JSONObject state,JSONObject turn,String speaker,String quote,String via)throws Exception {
        quote=quote.trim();if(!Arrays.asList("user","assistant").contains(speaker)||blocked(state,speaker,turn)||!safe(quote)||!grounded(said(turn,speaker),quote))return false;
        JSONArray a=entries(state);String source=ChatArchive.id(turn);
        for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(source.equals(e.optString("source"))&&speaker.equals(e.optString("speaker"))&&(e.optString("quote").contains(quote)||quote.contains(e.optString("quote"))))return false;}
        if(a.length()>=KEEP){int drop=-1;for(int i=0;i<a.length();i++)if(!"open".equals(a.getJSONObject(i).optString("status"))){drop=i;break;}
            if(drop<0){state.put("commitmentsFull",true);return false;}forget(state,a.getJSONObject(drop).getString("id"));}
        String id=ChatArchive.hash(speaker+"|"+source+"|"+quote);
        a.put(new JSONObject().put("id",id).put("speaker",speaker).put("quote",quote).put("source",source).put("at","assistant".equals(speaker)?turn.optLong("repliedAt",turn.optLong("at")):turn.optLong("at"))
            .put("timeZone",turn.optString("timeZone",java.time.ZoneId.systemDefault().getId())).put("status","open").put("via",via));
        state.put("commitments",a).put("commitmentsFull",false);return true;
    }
    static void capture(JSONObject state,JSONObject turn,boolean assistant)throws Exception {
        String speaker=assistant?"assistant":"user";String text=said(turn,speaker);
        // A partially displayed sentence is not a promise yet. Completed preceding sentences remain usable.
        if(assistant&&turn.has("displayed")&&turn.optInt("displayed")<ReplySegments.count(turn)){
            int end=-1;for(int i=0;i<text.length();i++)if("。！？!?；;\n".indexOf(text.charAt(i))>=0)end=i;text=text.substring(0,end+1);}
        int accepted=0;for(String part:text.split("(?<=[。！？!?；;])|\\n")){String quote=part.trim();if(explicit(quote)){add(state,turn,speaker,quote,"local");if(++accepted>=2)break;}}
    }
    static boolean hasFuture(String text){for(String s:text.split("(?<=[。！？!?；;])|\\n"))if(future(s.trim()))return true;return false;}
    static boolean candidate(List<JSONObject> turns){for(JSONObject t:turns)if(t.optBoolean("commitmentsAllowed",true)&&(hasFuture(t.optString("user"))||hasFuture(said(t,"assistant"))))return true;return false;}
    static final String DREAM="额外返回 commitments 数组，最多4项；每项为 {\"source_id\":\"conversation中的source_id\",\"speaker\":\"user或assistant\",\"quote\":\"该说话者承诺的逐字原话\"}。分别检查 conversation.user（用户的话）和 conversation.you（你实际展示的话），双方都有承诺就分别摘出，不要只摘用户。只收明确答应将来做的事，不收建议、愿望、问题、假设、习惯、能力说明、引用或已经完成的事；不能把一方的话写成另一方的承诺，不扩写任务或时间。没有就空数组。这里只补记承诺，不判定完成，也不把它当成定时器。";
    static JSONArray dream(String raw,List<JSONObject> offered)throws Exception {
        JSONArray out=new JSONArray();JSONObject result;try{String json=AgentPolicy.envelope(raw);if(json==null)return out;result=new JSONObject(json);}catch(Exception invalid){return out;}
        JSONArray a=result.optJSONArray("commitments");if(a==null)a=new JSONArray();
        for(int i=0;i<Math.min(a.length(),4);i++){JSONObject p=a.optJSONObject(i);if(p==null||!(p.opt("source_id") instanceof String)||!(p.opt("speaker") instanceof String)||!(p.opt("quote") instanceof String))continue;
            String id=p.getString("source_id"),speaker=p.getString("speaker"),q=p.getString("quote").trim();if(!future(q))continue;
            for(JSONObject t:offered)if(id.equals("chat:"+ChatArchive.id(t))&&t.optBoolean("commitmentsAllowed",true)&&grounded(said(t,speaker),q)){
                offer(out,t,speaker,q);break;}}
        // A model may omit its own promise or the entire field. The same slow pass also checks literal future clauses.
        for(JSONObject t:offered){if(!t.optBoolean("commitmentsAllowed",true))continue;
            for(String speaker:new String[]{"user","assistant"})for(String sentence:said(t,speaker).split("(?<=[。！？!?；;])|\\n")){
                String q=sentence.trim();if(future(q)&&grounded(said(t,speaker),q))offer(out,t,speaker,q);}}
        return out;
    }
    static void offer(JSONArray out,JSONObject t,String speaker,String quote)throws Exception {for(int i=0;i<out.length();i++){JSONObject p=out.getJSONObject(i);
        if(ChatArchive.id(t).equals(ChatArchive.id(p.getJSONObject("turn")))&&speaker.equals(p.optString("speaker"))&&(p.optString("quote").contains(quote)||quote.contains(p.optString("quote"))))return;}
        if(out.length()<KEEP)out.put(new JSONObject().put("turn",t).put("speaker",speaker).put("quote",quote));}
    // Existing or rejected candidates spend no slots; neither speaker can crowd the other out of a slow pass.
    static int apply(JSONObject state,JSONArray proposed)throws Exception {int user=0,self=0;for(int i=0;i<proposed.length()&&user+self<4;i++){JSONObject p=proposed.getJSONObject(i);boolean hers="assistant".equals(p.optString("speaker"));if((hers?self:user)>=2)continue;
        if(add(state,p.getJSONObject("turn"),p.getString("speaker"),p.getString("quote"),"dream")){if(hers)self++;else user++;}}return user+self;}
    static void forget(JSONObject state,String id)throws Exception {JSONArray a=entries(state),forgot=state.optJSONArray("commitmentsForgotten");if(forgot==null)state.put("commitmentsForgotten",forgot=new JSONArray());
        for(int i=a.length()-1;i>=0;i--){JSONObject e=a.getJSONObject(i);if(!id.equals(e.optString("id")))continue;String key=sourceKey(e.optString("speaker"),e.optString("source"));boolean seen=false;for(int j=0;j<forgot.length();j++)if(key.equals(forgot.optString(j)))seen=true;if(!seen)forgot.put(key);a.remove(i);}
        state.put("commitmentsFull",false);}
    static void dropSources(JSONObject state,Collection<String> sources)throws Exception {JSONArray a=entries(state);for(int i=a.length()-1;i>=0;i--)if(sources.contains(a.getJSONObject(i).optString("source")))forget(state,a.getJSONObject(i).getString("id"));}
    static void clear(JSONObject state,long now)throws Exception {state.put("commitments",new JSONArray()).put("commitmentsForgotten",new JSONArray()).put("commitmentsClearedAt",now).put("commitmentsFull",false);}
    static void status(JSONObject state,String id,String status,long now)throws Exception {if(!Arrays.asList("done","cancelled","open").contains(status))throw new IllegalArgumentException("Unknown commitment status");
        JSONArray a=entries(state);for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(id.equals(e.optString("id")))e.put("status",status).put("statusAt",now).put("statusBy","user");}state.put("commitmentsFull",false);}
    static String statusLabel(JSONObject e){return "done".equals(e.optString("status"))?"已标记完成":"cancelled".equals(e.optString("status"))?"已取消":"待确认";}
    static String speakerLabel(JSONObject e){return "assistant".equals(e.optString("speaker"))?"她答应的":"你答应的";}
    static JSONArray context(JSONObject state,String query,boolean proactive)throws Exception {
        String q=query==null?"":query;boolean asking=q.matches("(?s).*(承诺|约定|答应|说好|说定|还欠|时间到了|该提醒).*" );List<JSONObject> picked=new ArrayList<>();
        JSONArray a=entries(state);for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);if(asking||SharedExperience.relevance(q,new JSONObject().put("text",e.optString("quote")))>.08||proactive&&"open".equals(e.optString("status")))picked.add(e);}
        picked.sort((a1,b1)->{int c=Double.compare(AgentPolicy.relevance(q,b1.optString("quote")),AgentPolicy.relevance(q,a1.optString("quote")));if(c!=0)return c;return Long.compare(b1.optLong("at"),a1.optLong("at"));});
        JSONArray out=new JSONArray();int users=0,selves=0;for(int i=0;i<picked.size()&&out.length()<6;i++){JSONObject e=picked.get(i);boolean hers="assistant".equals(e.optString("speaker"));if((asking||proactive)&&(hers?selves:users)>=3)continue;if(hers)selves++;else users++;out.put(new JSONObject().put("speaker",e.optString("speaker")).put("quote",e.optString("quote"))
            .put("said_at",Initiative.localTime(e.optLong("at"),e.optString("timeZone"))).put("status",e.optString("status")).put("status_by",e.optString("statusBy","not_confirmed")));}return out;
    }
    static JSONObject grouped(JSONArray entries)throws Exception {JSONArray user=new JSONArray(),self=new JSONArray();for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);("assistant".equals(e.optString("speaker"))?self:user).put(e);}
        return new JSONObject().put("用户答应你的",user).put("你答应用户的",self);}
    static final String GUIDANCE="\n双方约定，已按承诺人分组：原话中的‘我’指各组的说话者。‘用户答应你的’只能说成对方答应，‘你答应用户的’才是你自己的承诺。open只表示尚未确认是否兑现，不等于没做；done/cancelled是用户在手账标记的状态。按原话和原日期理解，别交换说话者、擅改时间或编完成过程。本轮用户说明进展优先，聊到相关事再自然接上，不反复催对方。答应提醒不等于已经建立精确定时器，不能声称已经执行；主动陪伴的开关和时机仍由应用控制。\n";
}
