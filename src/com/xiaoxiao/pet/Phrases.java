package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/**
 * The user's own way of saying things ("离谱他妈给离谱开门", "绝了"), learned slowly so she can pick them up the way
 * friends borrow each other's catchphrases. Like Understanding, it forms only in the once-a-day diary call, and a
 * phrase is kept only if it appears verbatim in what the user said that day; it is a habit of speech, never a fact
 * about the user. Deletable one by one, and forgetting or clearing the quoted turns removes it.
 */
final class Phrases {
    static final int KEEP=20,PER_DAY=3,MIN_PHRASE=2,MAX_PHRASE=20,MAX_WHEN=20,EVIDENCE_KEPT=3,SHOWN=3;
    /** Too common to be anyone's own way of talking. */
    private static final Set<String> COMMON=new HashSet<>(Arrays.asList("好的","嗯嗯","哈哈","哈哈哈","是的","谢谢","好吧","不是","可以","没事","知道了","晚安","早安","你好","在吗","对啊","真的","算了","是啊","好啊"));
    static final String INSTRUCTION="另外，留意这一天用户自己的说话习惯：口头禅、常用的梗、语气词或独特的说法。"
        +"phrases 最多3条，每条 {\"phrase\":\"用户原话里逐字出现的说法\",\"when\":\"TA 在什么情境下这么说\"}：phrase 2到20字，必须逐字摘自当天用户说的话，是表达方式而不是事实内容；"
        +"when 不超过20字，比如「觉得事情很离谱时」。不收「好的」「哈哈」这类人人都说的话，不收人名、地点、密码或任何隐私，没有就空数组。";

    static JSONArray entries(JSONObject state){JSONArray a=state.optJSONArray("phrases");return a==null?new JSONArray():a;}

    /** New phrases from one diary answer, each with {quote, turn, date} evidence; ones she already knows count as seen again. */
    static List<JSONObject> parse(String raw,List<JSONObject> turns,String day){
        List<JSONObject> out=new ArrayList<>();if(raw==null)return out;
        String json=AgentPolicy.envelope(raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$",""));if(json==null)return out;
        JSONArray a;try{a=new JSONObject(json).optJSONArray("phrases");}catch(JSONException e){return out;}if(a==null)return out;
        Set<String> seen=new HashSet<>();
        for(int i=0;i<a.length()&&out.size()<PER_DAY;i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;
            String phrase=clean(p.optString("phrase")),when=clean(p.optString("when"));
            if(!valid(phrase)||when.codePointCount(0,when.length())>MAX_WHEN||AgentPolicy.sensitive(when)||!seen.add(phrase))continue;
            for(JSONObject t:turns)if(t.optString("user").contains(phrase))try{out.add(new JSONObject().put("phrase",phrase).put("when",when).put("evidence",new JSONObject().put("quote",phrase).put("turn",ChatArchive.id(t)).put("date",day)));break;}catch(Exception ignored){}
        }
        return out;
    }
    static boolean valid(String phrase){
        int n=phrase.codePointCount(0,phrase.length());
        return n>=MIN_PHRASE&&n<=MAX_PHRASE&&!COMMON.contains(phrase)&&!AgentPolicy.sensitive(phrase)&&phrase.matches("(?s).*[\\p{L}].*");
    }
    private static String clean(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();}

    /** Adds new phrases, counts known ones as seen on another day, keeps the KEEP most used and most recent. */
    static boolean apply(JSONObject state,List<JSONObject> found,long now)throws JSONException {
        if(found.isEmpty())return false;JSONArray a=entries(state);List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++)rows.add(a.getJSONObject(i));
        for(JSONObject f:found){JSONObject same=null;for(JSONObject r:rows)if(r.optString("phrase").equals(f.getString("phrase")))same=r;
            if(same!=null){JSONArray ev=same.optJSONArray("evidence");if(ev==null)same.put("evidence",ev=new JSONArray());
                boolean newDay=true;for(int j=0;j<ev.length();j++)if(f.getJSONObject("evidence").optString("date").equals(ev.optJSONObject(j)==null?"":ev.optJSONObject(j).optString("date")))newDay=false;
                if(!newDay)continue;ev.put(f.getJSONObject("evidence"));while(ev.length()>EVIDENCE_KEPT)ev.remove(0);same.put("seen",same.optInt("seen",1)+1).put("last",now);}
            else rows.add(new JSONObject().put("id",UUID.randomUUID().toString()).put("phrase",f.getString("phrase")).put("when",f.getString("when")).put("evidence",new JSONArray().put(f.getJSONObject("evidence"))).put("seen",1).put("first",now).put("last",now));}
        while(rows.size()>KEEP){JSONObject weakest=rows.get(0);for(JSONObject r:rows)if(r.optInt("seen",1)<weakest.optInt("seen",1)||r.optInt("seen",1)==weakest.optInt("seen",1)&&r.optLong("last")<weakest.optLong("last"))weakest=r;rows.remove(weakest);}
        state.put("phrases",new JSONArray(rows));return true;
    }
    /** Drops evidence quoted from these turns; a phrase left without evidence goes too. */
    static void dropSources(JSONObject state,Collection<String> turnIds){
        JSONArray a=state.optJSONArray("phrases");if(a==null||turnIds.isEmpty())return;
        for(int i=a.length()-1;i>=0;i--){JSONObject p=a.optJSONObject(i);JSONArray ev=p==null?null:p.optJSONArray("evidence");if(ev==null){a.remove(i);continue;}
            for(int j=ev.length()-1;j>=0;j--){JSONObject e=ev.optJSONObject(j);if(e==null||turnIds.contains(e.optString("turn")))ev.remove(j);}
            if(ev.length()==0)a.remove(i);}
    }
    static boolean remove(JSONObject state,String id){
        JSONArray a=state.optJSONArray("phrases");if(a==null)return false;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&id.equals(p.optString("id"))){a.remove(i);return true;}}
        return false;
    }
    /**
     * A few phrases for this turn: ones whose situation or wording touches what the user just said first, then the
     * most used. Only a handful, so she borrows a phrase now and then instead of parroting all of them.
     */
    static JSONArray context(JSONObject state,String query){
        JSONArray a=entries(state),out=new JSONArray();List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)rows.add(a.optJSONObject(i));
        String q=query==null?"":query;
        rows.sort((x,y)->{int c=Double.compare(fit(y,q),fit(x,q));if(c==0)c=Integer.compare(y.optInt("seen",1),x.optInt("seen",1));return c!=0?c:Long.compare(y.optLong("last"),x.optLong("last"));});
        for(int i=0;i<Math.min(SHOWN,rows.size());i++)try{out.put(new JSONObject().put("phrase",rows.get(i).optString("phrase")).put("when",rows.get(i).optString("when")).put("seen_days",rows.get(i).optInt("seen",1)));}catch(JSONException ignored){}
        return out;
    }
    private static double fit(JSONObject row,String query){return query.isEmpty()?0:Math.max(AgentPolicy.relevance(query,row.optString("when")),AgentPolicy.relevance(query,row.optString("phrase")));}
}
