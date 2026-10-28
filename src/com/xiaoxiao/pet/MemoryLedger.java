package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;
import java.time.*;

/** Phone-owned state and provenance. Engine metadata is separate from user quotes. */
final class MemoryLedger {
    // Phone capacity. The prompt still carries only the relevant few (CORE+10); the optional Python service
    // keeps its own protocol limit of SERVICE_ACTIVE, beyond which MemoryBridge uses the phone engine.
    static final int MAX_ACTIVE=500,MAX_ARCHIVE=1000,MAX_AUDIT=512,SERVICE_ACTIVE=120,SERVICE_ARCHIVE=240;
    static void migrate(JSONObject s)throws Exception {
        if(!s.has("archive"))s.put("archive",new JSONArray());
        if(!s.has("audit"))s.put("audit",new JSONArray());
        if(!s.has("vector_cache"))s.put("vector_cache",new JSONObject());
        s.put("memoryVersion",2);
    }
    static JSONObject copy(JSONObject s)throws Exception{JSONObject n=new JSONObject(s.toString());migrate(n);return n;}
    static JSONObject record(String id,String text,String source,boolean pinned,long at)throws Exception {
        return new JSONObject().put("id",id).put("text",text).put("source",source).put("pinned",pinned).put("at",at);
    }
    static JSONArray sources(JSONObject r){JSONArray a=r.optJSONArray("sources");return a==null?new JSONArray().put(r.optString("source")):a;}
    static long timestamp(String value){try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
    static boolean current(JSONObject r,long now){JSONObject e=r.optJSONObject("engine");if(e==null)return true;long expires=timestamp(e.optString("expires_at"));return (e.isNull("invalid_at")||e.optString("invalid_at").isEmpty())&&!e.optBoolean("conflict_downgraded")&&(expires==0||expires>now);}
    static void event(JSONObject state,String op,String id,long now)throws Exception {
        JSONArray a=state.getJSONArray("audit");a.put(new JSONObject().put("op",op).put("id",id).put("at",now));while(a.length()>MAX_AUDIT)a.remove(0);
    }
    static void expire(JSONObject s,long now)throws Exception {
        migrate(s);JSONArray a=s.getJSONArray("memories"),archive=s.getJSONArray("archive");
        for(int i=a.length()-1;i>=0;i--){JSONObject r=a.getJSONObject(i);if(!current(r,now)){
            JSONObject e=r.optJSONObject("engine");if(e==null)e=new JSONObject();
            e.put("archived_reason",e.optBoolean("conflict_downgraded")||!e.optString("invalid_at").isEmpty()?"superseded":"expired");
            e.put("archived_at",Instant.ofEpochMilli(now).toString());r.put("engine",e);archive.put(r);a.remove(i);event(s,"ARCHIVE",r.optString("id"),now);
        }}
        while(archive.length()>MAX_ARCHIVE)archive.remove(0);
    }
    static void addLocal(JSONObject s,JSONObject record)throws Exception {
        migrate(s);JSONArray a=s.getJSONArray("memories");
        for(int i=0;i<a.length();i++){JSONObject old=a.getJSONObject(i);if(old.optString("text").equals(record.optString("text"))){
            LinkedHashSet<String> sources=new LinkedHashSet<>();JSONArray previous=sources(old);for(int j=0;j<previous.length();j++)sources.add(previous.optString(j));sources.add(record.optString("source"));
            while(sources.size()>200)sources.remove(sources.iterator().next());old.put("sources",new JSONArray(sources));
            if(record.optBoolean("pinned"))old.put("pinned",true);return;
        }}
        if(a.length()>=MAX_ACTIVE){int remove=-1;for(int i=0;i<a.length();i++)if(!a.getJSONObject(i).optBoolean("pinned")){remove=i;break;}if(remove<0)throw new java.io.IOException("记忆已满，请先删除几条");a.remove(remove);}
        a.put(record);event(s,"ADD",record.optString("id"),record.optLong("at"));s.put("vector_cache",new JSONObject());
    }
    /** Dated things from yesterday to tomorrow, active or expired (not corrected), for her to keep in mind or ask about. */
    static JSONArray dueEvents(JSONObject s,LocalDate today)throws Exception {
        JSONArray out=new JSONArray();
        for(String pool:new String[]{"memories","archive"}){JSONArray rows=s.optJSONArray(pool);if(rows==null)continue;for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i),e=r.optJSONObject("engine");if(e==null||e.optString("event_date").isEmpty()||"superseded".equals(e.optString("archived_reason")))continue;
            LocalDate day;try{day=LocalDate.parse(e.optString("event_date"));}catch(Exception bad){continue;}
            long offset=java.time.temporal.ChronoUnit.DAYS.between(today,day);if(offset<-1||offset>1)continue;
            out.put(new JSONObject().put("id",r.optString("id")).put("quote",e.optString("content",r.optString("text"))).put("when",offset<0?"昨天":offset==0?"今天":"明天"));
        }}
        return out;
    }
    /** Due events from today or yesterday she has not asked about yet; a proactive turn may follow up on one. */
    static JSONArray followUps(JSONObject s,LocalDate today)throws Exception {
        JSONArray due=dueEvents(s,today),out=new JSONArray();Set<String> done=new HashSet<>();JSONArray f=s.optJSONArray("followedUp");
        // Asked only if that proactive reply reached the screen: a turn still at displayed=0 was never seen.
        Map<String,JSONObject> turns=new HashMap<>();JSONArray t=s.optJSONArray("turns");if(t!=null)for(int i=0;i<t.length();i++)turns.put(t.getJSONObject(i).optString("id"),t.getJSONObject(i));
        if(f!=null)for(int i=0;i<f.length();i++){JSONObject entry=f.optJSONObject(i);if(entry==null){done.add(f.optString(i));continue;}
            JSONObject turn=turns.get(entry.optString("turn"));if(turn==null||!turn.has("displayed")||turn.optInt("displayed")>0)done.add(entry.optString("id"));}
        for(int i=0;i<due.length();i++){JSONObject d=due.getJSONObject(i);if(!"明天".equals(d.optString("when"))&&!done.contains(d.optString("id")))out.put(d);}
        return out;
    }
    static JSONArray candidates(String user,JSONArray quotes,String explicit,String turn,long now)throws Exception {
        JSONArray a=new JSONArray();if(explicit!=null){if(explicit.trim().isEmpty()||explicit.length()>240||!user.contains(explicit)||AgentPolicy.sensitive(explicit))throw new java.io.IOException("请填写 1–240 字，不含密码或证件号码");a.put(record(UUID.randomUUID().toString(),explicit,turn,true,now));}
        else {
            // The chat model's quotes first; then sentences the local rules recognise as facts, so a fact is kept
            // even when the model forgot to quote it. At most three per turn (the service protocol's limit).
            List<String> kept=new ArrayList<>();
            if(quotes!=null)for(int i=0;i<quotes.length()&&kept.size()<3;i++){String q=quotes.optString(i,"");if(AgentPolicy.validEvidence(user,q)&&!kept.contains(q))kept.add(q);}
            for(String f:NativeMemoryEngine.autoFacts(user)){if(kept.size()>=3)break;boolean covered=false;for(String k:kept)if(k.contains(f)||f.contains(k))covered=true;if(!covered)kept.add(f);}
            for(String q:kept)a.put(record(UUID.randomUUID().toString(),q,turn,false,now));
        }
        return a;
    }
    static Set<String> staleSources(JSONObject s,long now)throws Exception {
        Set<String> out=new HashSet<>();for(String pool:new String[]{"memories","archive"}){JSONArray a=s.optJSONArray(pool);if(a==null)continue;for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);if(pool.equals("archive")||!current(r,now)){JSONArray sources=sources(r);for(int j=0;j<sources.length();j++)out.add(sources.optString(j));}}}return out;
    }
    static String context(JSONObject s,String query,JSONArray hits)throws Exception {
        JSONArray a=s.getJSONArray("memories");List<JSONObject> ranked=new ArrayList<>();long now=System.currentTimeMillis();
        Map<String,Integer> order=new HashMap<>();if(hits!=null)for(int i=0;i<hits.length();i++)order.put(hits.optString(i),i);
        List<JSONObject> core=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);if(!current(r,now))continue;if(hits==null||order.containsKey(r.optString("id")))ranked.add(r);if(core(r))core.add(r);}
        ranked.sort((x,y)->hits==null?Double.compare(score(query,y,now),score(query,x,now)):Integer.compare(order.get(x.optString("id")),order.get(y.optString("id"))));
        // Safety facts, identity and pinned notes ride along even when retrieval misses them:
        // "推荐个零食" shares no words with "我对花生过敏". Up to CORE of them, then the ranked hits.
        core.sort((x,y)->{int c=Boolean.compare(AgentPolicy.critical(y.optString("text")),AgentPolicy.critical(x.optString("text")));return c!=0?c:Double.compare(score(query,y,now),score(query,x,now));});
        LinkedHashMap<String,JSONObject> chosen=new LinkedHashMap<>();
        for(JSONObject r:core.subList(0,Math.min(CORE,core.size())))chosen.put(r.optString("id"),r);
        for(JSONObject r:ranked){if(chosen.size()>=CORE+10)break;chosen.putIfAbsent(r.optString("id"),r);}
        // When everything current fits, retrieval has nothing to choose: a memory sharing no words with the
        // question (and missed by the rules) still rides along, after the hits.
        if(hits!=null&&currentCount(s,now)<=CORE+10){List<JSONObject> rest=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);if(current(r,now)&&!chosen.containsKey(r.optString("id")))rest.add(r);}
            rest.sort((x,y)->Double.compare(score(query,y,now),score(query,x,now)));for(JSONObject r:rest)chosen.put(r.optString("id"),r);}
        List<JSONObject> picked=new ArrayList<>(chosen.values());
        JSONArray out=new JSONArray();for(int i=0;i<picked.size();i++){JSONObject r=picked.get(i),e=r.optJSONObject("engine");out.put(new JSONObject().put("quote",r.optString("text")).put("observed_at",r.optLong("at")).put("resolved",e==null?r.optString("text"):e.optString("content",r.optString("text"))).put("pinned",r.optBoolean("pinned")));}return out.toString();
    }
    static final int CORE=5;
    static int currentCount(JSONObject s,long now)throws Exception{JSONArray a=s.getJSONArray("memories");int n=0;for(int i=0;i<a.length();i++)if(current(a.getJSONObject(i),now))n++;return n;}
    static boolean core(JSONObject r){JSONObject e=r.optJSONObject("engine");return r.optBoolean("pinned")||e!=null&&e.optBoolean("is_identity")||AgentPolicy.critical(r.optString("text"));}
    static double score(String q,JSONObject m,long now){return AgentPolicy.relevance(q,m.optString("text"))*5+(m.optBoolean("pinned")?2:0)+Math.max(0,1-(now-m.optLong("at"))/(30.0*86400000));}
    static JSONArray historicalContext(JSONObject s,String query)throws Exception {
        JSONArray out=new JSONArray();if(!query.matches("(?s).*(以前|曾经|过去|之前|后来|变化|历史).*"))return out;
        JSONArray archive=s.optJSONArray("archive");if(archive==null)return out;
        List<JSONObject> rows=new ArrayList<>();for(int i=0;i<archive.length();i++){JSONObject r=archive.getJSONObject(i);if(AgentPolicy.relevance(query,r.optString("text"))>0.1)rows.add(r);}
        rows.sort((a,b)->Double.compare(AgentPolicy.relevance(query,b.optString("text")),AgentPolicy.relevance(query,a.optString("text"))));
        for(int i=0;i<Math.min(5,rows.size());i++){JSONObject r=rows.get(i);out.put(new JSONObject().put("quote",r.optString("text")).put("observed_at",r.optLong("at")).put("status","历史记录，不能当作当前事实"));}return out;
    }
    static Set<String> forget(JSONObject s,String id)throws Exception {
        migrate(s);Set<String> ids=new HashSet<>(),sources=new HashSet<>();ids.add(id);boolean changed;
        do{changed=false;for(String pool:new String[]{"memories","archive"}){JSONArray rows=s.getJSONArray(pool);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i),e=r.optJSONObject("engine");String by=e==null?"":e.optString("superseded_by",e.optString("downgrade_reason"));if(ids.contains(r.optString("id"))||(!by.isEmpty()&&ids.contains(by))){if(ids.add(r.optString("id")))changed=true;if(!by.isEmpty()&&ids.add(by))changed=true;JSONArray a=sources(r);for(int j=0;j<a.length();j++)sources.add(a.optString(j));}}}}while(changed);
        for(String pool:new String[]{"memories","archive"}){JSONArray rows=s.getJSONArray(pool);for(int i=rows.length()-1;i>=0;i--)if(ids.contains(rows.getJSONObject(i).optString("id")))rows.remove(i);}
        JSONArray turns=s.getJSONArray("turns");for(int i=turns.length()-1;i>=0;i--)if(sources.contains(turns.getJSONObject(i).optString("id")))turns.remove(i);
        JSONArray audit=s.getJSONArray("audit");for(int i=audit.length()-1;i>=0;i--){JSONObject e=audit.getJSONObject(i);if(ids.contains(e.optString("id"))||ids.contains(e.optString("by"))||ids.contains(e.optString("from")))audit.remove(i);}
        s.put("vector_cache",new JSONObject());return sources;
    }
}
