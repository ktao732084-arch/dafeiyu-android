package com.xiaoxiao.pet;

import org.json.*;import java.util.*;import java.util.regex.*;import java.time.*;import java.io.IOException;

/** Phone-native memory engine. Ports upstream rule/time operations; no Python/service dependency. */
final class NativeMemoryEngine {
    interface Intelligence {JSONObject request(String operation,JSONObject data,AgentTransport.Token token)throws Exception;}
    static final JSONObject RULES=NativeMemoryRules.DATA;
    static boolean contains(String text,JSONArray terms){if(terms==null)return false;String t=text.toLowerCase(Locale.ROOT);for(int i=0;i<terms.length();i++)if(t.contains(terms.optString(i).toLowerCase(Locale.ROOT)))return true;return false;}
    // Python's re treats \d \s \w \b as Unicode (full-width digits and spaces from Chinese IMEs); compiled once.
    private static final Map<String,Pattern> COMPILED=new java.util.concurrent.ConcurrentHashMap<>();
    static Pattern pattern(String regex,int flags){return COMPILED.computeIfAbsent(flags+":"+regex,k->Pattern.compile(regex,flags|Pattern.UNICODE_CHARACTER_CLASS));}
    static boolean patterns(String text,JSONArray patterns,boolean start){if(patterns==null)return false;for(int i=0;i<patterns.length();i++){Matcher m=pattern(patterns.optString(i),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);if(start?m.lookingAt():m.find())return true;}return false;}
    /** Python's str.strip(): also removes the full-width space U+3000 that trim() keeps. */
    static String strip(String s){int a=0,b=s.length();while(a<b&&(Character.isWhitespace(s.charAt(a))||Character.isSpaceChar(s.charAt(a))))a++;while(b>a&&(Character.isWhitespace(s.charAt(b-1))||Character.isSpaceChar(s.charAt(b-1))))b--;return s.substring(a,b);}
    static boolean noise(String text){
        String t=strip(text);if(t.codePointCount(0,t.length())<3||AgentPolicy.sensitive(t)||patterns(t,RULES.optJSONArray("SECRET_PATTERNS"),false))return true;
        if(patterns(t,RULES.optJSONArray("NOISE_PATTERNS"),false)||contains(t,RULES.optJSONArray("NOISE_KEYWORDS"))||patterns(t,RULES.optJSONArray("DISTRACTION_PATTERNS"),false))return true;
        boolean conversational=patterns(t,RULES.optJSONArray("CONVERSATION_NOISE"),false)||pattern(RULES.optString("QUESTION_PATTERN"),0).matcher(t).find();
        int index=t.indexOf('我');boolean fact=contains(t,RULES.optJSONArray("FACT_MARKERS"))||(index>=0&&normalize(t.substring(index)).length()>=6);
        if(conversational&&!fact)return true;JSONObject fallback=RULES.optJSONObject("FALLBACK_NOISE");Iterator<String> keys=fallback.keys();while(keys.hasNext())if(patterns(t,fallback.optJSONArray(keys.next()),true))return true;return false;
    }
    /** Interjections, acknowledgements, emoji and very short messages: the upstream fallback noise patterns. */
    static boolean casual(String text){String t=strip(text).replaceAll("[\\p{P}\\p{S}\\s]+$","");/* "好的！": upstream allows only half-width ! and ? */JSONObject fallback=RULES.optJSONObject("FALLBACK_NOISE");if(fallback==null)return false;Iterator<String> keys=fallback.keys();while(keys.hasNext())if(patterns(t,fallback.optJSONArray(keys.next()),true))return true;return false;}
    static String category(String text)throws Exception {JSONObject rules=RULES.getJSONObject("IMPORTANCE_RULES");for(String c:new String[]{"identity_health_safety","preference_relation_status","project_task_goal","temporary"})if(contains(text,rules.getJSONObject(c).getJSONArray("keywords")))return c;return "general_fact";}
    static double importance(String text,String category)throws Exception {
        double score=RULES.getJSONObject("IMPORTANCE_RULES").getJSONObject(category).getDouble("score"),boost=0;JSONObject signals=RULES.getJSONObject("EXPLICIT_SIGNALS");
        for(String key:new String[]{"boost_high","boost_medium","boost_low","reduce"}){JSONObject s=signals.getJSONObject(key);if(contains(text,s.getJSONArray("keywords"))){double b=s.getDouble("boost");boost=b>0?Math.max(boost,b):Math.min(boost,b);}}return Math.min(1,Math.max(0,score+boost));
    }
    static void initialize(JSONObject row,ZoneId zone)throws Exception {
        JSONObject e=row.optJSONObject("engine");if(e==null)e=new JSONObject();String category=category(row.getString("text"));
        if(!e.has("importance"))e.put("importance",Math.max(row.optBoolean("pinned")?0.8:0,importance(row.getString("text"),category)));
        if(!e.has("score"))e.put("score",e.optDouble("importance",0.5));if(!e.has("created"))e.put("created",Instant.ofEpochMilli(row.optLong("at")).toString());
        if(!e.has("is_identity"))e.put("is_identity",category.equals("identity_health_safety"));
        NativeMemoryTime.ground(row,e,zone,category.equals("identity_health_safety")||category.equals("preference_relation_status"));row.put("engine",e);
    }
    static void prepare(JSONObject state,long now,ZoneId zone)throws Exception {MemoryLedger.migrate(state);JSONArray a=state.getJSONArray("memories");for(int i=0;i<a.length();i++)initialize(a.getJSONObject(i),zone);MemoryLedger.expire(state,now);}
    static String content(JSONObject r){JSONObject e=r.optJSONObject("engine");return e==null?r.optString("text"):e.optString("content",r.optString("text"));}
    static String normalize(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_]","");}
    static Set<String> grams(String text){Set<String> out=new HashSet<>();Matcher m=Pattern.compile("[a-z0-9]+").matcher(text);while(m.find())out.add(m.group());String chinese=text.replaceAll("[^\\u4e00-\\u9fff]","");for(int i=1;i<chinese.length();i++)out.add(chinese.substring(i-1,i+1));return out;}
    static double similarity(String x,String y){String a=normalize(x),b=normalize(y);if(a.isEmpty()||b.isEmpty())return 0;if(a.contains(b)||b.contains(a))return 1;Set<String> aa=grams(a),bb=grams(b);int size=Math.min(aa.size(),bb.size());if(size==0)return 0;aa.retainAll(bb);return aa.size()/(double)size;}
    /** Equivalent no-junk longest-match decomposition: replacements forbid merging distinct facts. */
    static boolean substitution(String x,String y){return replacement(normalize(x),normalize(y),0,normalize(x).length(),0,normalize(y).length());}
    static boolean replacement(String a,String b,int alo,int ahi,int blo,int bhi){
        if(alo==ahi||blo==bhi)return false;int length=0,ai=alo,bj=blo;int[] last=new int[bhi-blo+1];
        for(int i=alo;i<ahi;i++){int[] next=new int[last.length];for(int j=blo;j<bhi;j++)if(a.charAt(i)==b.charAt(j)){int n=last[j-blo]+1;next[j-blo+1]=n;if(n>length){length=n;ai=i-n+1;bj=j-n+1;}}last=next;}
        return length==0||replacement(a,b,alo,ai,blo,bj)||replacement(a,b,ai+length,ahi,bj+length,bhi);
    }
    static JSONObject find(JSONObject state,String id)throws Exception {JSONArray rows=state.getJSONArray("memories");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("id").equals(id))return rows.getJSONObject(i);return null;}
    static void remove(JSONObject state,String id)throws Exception {JSONArray rows=state.getJSONArray("memories");for(int i=rows.length()-1;i>=0;i--)if(rows.getJSONObject(i).optString("id").equals(id))rows.remove(i);}
    static void supersede(JSONObject state,JSONObject old,JSONObject newer,long now,boolean model)throws Exception {
        JSONObject e=old.getJSONObject("engine");e.put("invalid_at",Instant.ofEpochMilli(newer.optLong("at",now)).toString()).put("superseded_by",newer.getString("id")).put("conflict_downgraded",true);
        MemoryLedger.event(state,"INVALIDATE",old.getString("id"),now);JSONObject event=state.getJSONArray("audit").getJSONObject(state.getJSONArray("audit").length()-1);event.put("by",newer.getString("id"));if(model)event.put("decided_by","model");
    }
    static void merge(JSONObject state,JSONObject old,JSONObject newer,long now,boolean model)throws Exception {
        LinkedHashSet<String> sources=new LinkedHashSet<>();for(JSONObject r:new JSONObject[]{old,newer}){JSONArray a=MemoryLedger.sources(r);for(int j=0;j<a.length();j++)sources.add(a.optString(j));}
        if(sources.size()>200)throw new IOException("这条记忆的来源过多，请整理手账");
        boolean pinned=old.optBoolean("pinned")||newer.optBoolean("pinned");
        if(normalize(newer.optString("text")).length()>normalize(old.optString("text")).length())for(String k:new String[]{"text","source","at","engine"})old.put(k,newer.get(k));
        old.put("pinned",pinned).put("sources",new JSONArray(sources));MemoryLedger.event(state,"MERGE",old.getString("id"),now);
        JSONObject event=state.getJSONArray("audit").getJSONObject(state.getJSONArray("audit").length()-1);event.put("from",newer.getString("id"));if(model)event.put("decided_by","model");
    }
    /** Returns ADD, MERGE or REJECT; handles both manual and automatic grounded records. */
    static String writeRules(JSONObject state,JSONObject row,long now,ZoneId zone)throws Exception {
        prepare(state,now,zone);String raw=row.getString("text");if(AgentPolicy.sensitive(raw)||patterns(raw,RULES.optJSONArray("SECRET_PATTERNS"),false)||(noise(raw)&&!row.optBoolean("pinned")))return "REJECT";initialize(row,zone);
        JSONArray rows=state.getJSONArray("memories");JSONObject target=null;double best=0;
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);double score=similarity(content(row),content(r));if(score>best){best=score;target=r;}}
        if(target!=null&&normalize(content(row)).equals(normalize(content(target)))){merge(state,target,row,now,false);return "MERGE";}
        boolean correction=contains(row.getString("text"),RULES.getJSONArray("OVERRIDE_SIGNALS_TIER1"))||contains(row.getString("text"),RULES.getJSONArray("OVERRIDE_SIGNALS_TIER2"));
        if(correction&&target!=null&&best>=0.3){supersede(state,target,row,now,false);MemoryLedger.expire(state,now);MemoryLedger.addLocal(state,row);return "CORRECT";}
        if(target!=null&&best>=0.6&&!substitution(content(row),content(target))){merge(state,target,row,now,false);return "MERGE";}
        MemoryLedger.addLocal(state,row);return "ADD";
    }
    static JSONArray describe(JSONArray rows)throws Exception {JSONArray out=new JSONArray();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);out.put(new JSONObject().put("id",r.getString("id")).put("quote",content(r)).put("observed_at",r.optLong("at")));}return out;}
    static void decisions(JSONObject state,JSONArray plans,JSONObject response,long now)throws Exception {
        JSONArray decisions=response.getJSONArray("decisions");if(decisions.length()>plans.length())throw new IOException("过多的记忆判断");Set<String> seen=new HashSet<>();
        for(int i=0;i<decisions.length();i++){
            JSONObject d=decisions.getJSONObject(i);String id=d.getString("new_id"),decision=d.getString("decision");if(!seen.add(id))throw new IOException("重复的记忆判断");
            JSONObject plan=null;for(int j=0;j<plans.length();j++)if(plans.getJSONObject(j).getString("new_id").equals(id))plan=plans.getJSONObject(j);if(plan==null)throw new IOException("未知的新记忆");
            JSONObject newer=find(state,id);if(newer==null)throw new IOException("记忆已变化");JSONArray targets=d.getJSONArray("targets"),allowed=plan.getJSONArray("candidates");Set<String> allow=new HashSet<>(),used=new HashSet<>();for(int j=0;j<allowed.length();j++)allow.add(allowed.getJSONObject(j).getString("id"));
            if(!(decision.equals("ADD")||decision.equals("DUPLICATE")||decision.equals("SUPERSEDE"))||targets.length()>5||(decision.equals("ADD")&&targets.length()!=0)||(decision.equals("DUPLICATE")&&targets.length()!=1)||(decision.equals("SUPERSEDE")&&targets.length()==0))throw new IOException("无效的记忆判断");
            for(int j=0;j<targets.length();j++){Object value=targets.get(j);if(!(value instanceof String)||!allow.contains(value)||!used.add((String)value))throw new IOException("记忆判断引用无效");JSONObject old=find(state,(String)value);if(old==null||!MemoryLedger.current(old,now))throw new IOException("旧记忆已变化");if(decision.equals("SUPERSEDE"))supersede(state,old,newer,now,true);else{merge(state,old,newer,now,true);remove(state,id);}}
        }
        MemoryLedger.expire(state,now);
    }
    static JSONArray rank(JSONObject state,String query,long now)throws Exception {
        List<JSONObject> rows=new ArrayList<>();JSONArray active=state.getJSONArray("memories");for(int i=0;i<active.length();i++){JSONObject r=active.getJSONObject(i);if(query.isEmpty()||similarity(query,content(r))>0)rows.add(r);}
        rows.sort((a,b)->Double.compare(rankScore(query,b,now),rankScore(query,a,now)));JSONArray ids=new JSONArray();for(int i=0;i<Math.min(10,rows.size());i++)ids.put(rows.get(i).optString("id"));return ids;
    }
    static double rankScore(String query,JSONObject row,long now){JSONObject e=row.optJSONObject("engine");double days=Math.max(0,(now-row.optLong("at"))/86400000.0);return similarity(query,content(row))*5+(row.optBoolean("pinned")?0.5:0)+(e==null?0.5:e.optDouble("importance",0.5))*0.35+Math.exp(-days*0.1)*0.35;}
    static MemoryBridge.Result evaluate(JSONObject original,JSONArray incoming,String query,long now,ZoneId zone,Intelligence intelligence,AgentTransport.Token token)throws Exception {
        JSONObject state=MemoryLedger.copy(original);prepare(state,now,zone);JSONArray plans=new JSONArray(),rejected=new JSONArray();boolean degraded=false,smart=false;
        for(int i=0;i<incoming.length();i++){token.check();JSONObject row=new JSONObject(incoming.getJSONObject(i).toString());JSONArray candidates=describe(state.getJSONArray("memories"));String op=writeRules(state,row,now,zone);if(op.equals("REJECT"))rejected.put(new JSONObject().put("id",row.getString("id")));else if(op.equals("ADD")&&candidates.length()>0)plans.put(new JSONObject().put("new_id",row.getString("id")).put("quote",content(row)).put("candidates",candidates));}
        if(intelligence!=null&&plans.length()>0)try{JSONObject response=intelligence.request("judge",new JSONObject().put("plans",plans),token);token.check();JSONObject checked=MemoryLedger.copy(state);decisions(checked,plans,response,now);state=checked;smart=true;}catch(Exception e){token.check();degraded=true;}
        MemoryLedger.expire(state,now);JSONArray hits=rank(state,query,now);
        // Retrieval uses the same chat model to select grounded IDs by meaning, not an extra vector server.
        // One extra model round-trip per message only when it can matter: more memories than the prompt carries
        // anyway (MemoryLedger.context sends them all up to CORE+10), and not for chatter like "哈哈" or "嗯".
        if(intelligence!=null&&incoming.length()==0&&!query.isEmpty()&&MemoryLedger.currentCount(state,now)>MemoryLedger.CORE+10&&!casual(query))try{
            JSONObject response=intelligence.request("recall",new JSONObject().put("query",query).put("memories",describe(state.getJSONArray("memories"))),token);token.check();JSONArray selected=response.getJSONArray("ids");if(selected.length()>10)throw new IOException("检索过多");Set<String> unique=new HashSet<>();for(int i=0;i<selected.length();i++){Object id=selected.get(i);if(!(id instanceof String)||!unique.add((String)id)||find(state,(String)id)==null)throw new IOException("无依据的检索");}hits=selected;smart=true;
        }catch(Exception e){token.check();degraded=true;}
        for(int i=0;i<hits.length();i++){JSONObject r=find(state,hits.getString(i));if(r!=null){JSONObject e=r.getJSONObject("engine");e.put("last_accessed",Instant.ofEpochMilli(now).toString()).put("access_count",e.optInt("access_count")+1);}}
        if(incoming.length()>0)state.put("vector_cache",new JSONObject());token.check();
        String status="手机内置记忆"+(degraded?" · 模型整理暂不可用，已使用本地规则":smart?" · 已使用当前模型辅助理解":" · 本地整理完成");return new MemoryBridge.Result(state,hits,false,status,rejected);
    }
}
