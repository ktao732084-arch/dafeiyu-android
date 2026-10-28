package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * The memory engine's rule-mode write path, in Java, for when no memory service is connected:
 * secret and noise filtering, importance and category, date grounding with expiry for future plans,
 * then add, merge a fuller rewording, or correct an older memory (strong corrections invalidate and
 * archive it, weak ones downgrade it). Mirrors companion_api.evaluate with semantic search and the
 * LLM judge off; retrieval is not ported, the phone keeps its own ranking. Keywords, patterns and
 * thresholds come from MemoryRules, generated from the vendored Python by tools/export-memory-rules.py.
 */
final class LocalEngine {
    static final int MAX_ACTIVE=120,MAX_ARCHIVE=240,MAX_AUDIT=512;
    static final Set<String> ENGINE_FIELDS=new HashSet<>(Arrays.asList("content","created","importance","score","entities","valid_at",
        "event_date","expires_at","invalid_at","superseded_by","override_tier","conflict_downgraded","downgrade_reason",
        "downgrade_at","archived_at","archived_reason","is_identity","last_accessed","access_count","updated","merged_from"));
    // Python's re is Unicode-aware for \d \s \w \b and its IGNORECASE folds Unicode case.
    private static final int SEARCH=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.UNICODE_CHARACTER_CLASS;
    private static final List<Pattern> SECRETS=compile(MemoryRules.SECRET_PATTERNS,SEARCH),FILTER=compile(MemoryRules.FILTER_PATTERNS,SEARCH),
        CONVERSATION=compile(MemoryRules.CONVERSATION_NOISE,SEARCH),DISTRACTION=compile(MemoryRules.DISTRACTION_PATTERNS,SEARCH);
    private static final Pattern QUESTION=Pattern.compile(MemoryRules.QUESTION_PATTERN,Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<String,List<Pattern>> ENGINE_NOISE=new LinkedHashMap<>();
    static {
        for(String[] row:MemoryRules.ENGINE_NOISE){List<Pattern> p=new ArrayList<>();for(int i=1;i<row.length;i++)p.add(Pattern.compile(row[i],Pattern.UNICODE_CHARACTER_CLASS));ENGINE_NOISE.put(row[0],p);}
    }
    private static List<Pattern> compile(String[] patterns,int flags){List<Pattern> out=new ArrayList<>();for(String p:patterns)out.add(Pattern.compile(p,flags));return out;}
    private static boolean search(List<Pattern> patterns,String text){for(Pattern p:patterns)if(p.matcher(text).find())return true;return false;}

    // ---- text helpers with Python semantics ----
    static boolean space(int c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    static String strip(String s){int a=0,b=s.length();while(a<b&&space(s.charAt(a)))a++;while(b>a&&space(s.charAt(b-1)))b--;return s.substring(a,b);}
    /** Python's \w: letters, numbers and underscore (not combining marks). */
    static boolean word(int c){int t=Character.getType(c);return c=='_'||Character.isLetter(c)||t==Character.DECIMAL_DIGIT_NUMBER||t==Character.LETTER_NUMBER||t==Character.OTHER_NUMBER;}
    static String words(String s){StringBuilder b=new StringBuilder();s.codePoints().filter(LocalEngine::word).forEach(b::appendCodePoint);return b.toString();}
    static int length(String s){return s.codePointCount(0,s.length());}

    static boolean secret(String text){return search(SECRETS,text);}
    /** NoiseFilter.is_noise: trivia, commands, greetings and questions without a fact about the user, too short, unimportant. */
    static boolean filtered(String raw,double importance) {
        String content=strip(raw),lower=content.toLowerCase(Locale.ROOT);
        if(secret(content)||search(FILTER,content))return true;
        for(String k:MemoryRules.FILTER_KEYWORDS)if(lower.contains(k))return true;
        if((search(CONVERSATION,content)||QUESTION.matcher(content).find())&&!hasFact(content))return true;
        if(search(DISTRACTION,content)||length(content)<3)return true;
        return importance<0.2;
    }
    static boolean hasFact(String text) {
        for(String m:MemoryRules.FACT_MARKERS)if(text.contains(m))return true;
        int i=text.indexOf('我');return i>=0&&length(words(text.substring(i)))>=6;
    }
    /** memory.is_noise: the reason a message is not stored, or null. */
    static String noise(String raw) {
        String content=strip(raw);
        if(secret(content))return "secret";
        if(filtered(content,0.5))return "noise_filter";
        for(Map.Entry<String,List<Pattern>> e:ENGINE_NOISE.entrySet())for(Pattern p:e.getValue())if(p.matcher(content).lookingAt())return e.getKey();
        return null;
    }
    /** memory.calculate_importance: {score, category}. */
    static Object[] importance(String content) {
        String lower=content.toLowerCase(Locale.ROOT);double base=MemoryRules.GENERAL_SCORE;String category="general_fact";
        found: for(int c=0;c<MemoryRules.CATEGORIES.length;c++)for(String k:MemoryRules.CATEGORY_KEYWORDS[c])if(content.contains(k)||lower.contains(k)){base=MemoryRules.CATEGORY_SCORES[c];category=MemoryRules.CATEGORIES[c];break found;}
        double boost=0;
        for(int s=0;s<MemoryRules.SIGNAL_KEYWORDS.length;s++)for(String k:MemoryRules.SIGNAL_KEYWORDS[s])if(content.contains(k)){double b=MemoryRules.SIGNAL_BOOSTS[s];boost=b>0?Math.max(boost,b):Math.min(boost,b);break;}
        return new Object[]{Math.min(1.0,Math.max(0.0,base+boost)),category};
    }

    // ---- MemoryOperator ----
    static String normalize(String text){return words(text.toLowerCase(Locale.ROOT));}
    static Set<String> bigrams(String normalized) {
        Set<String> grams=new HashSet<>();Matcher m=Pattern.compile("[a-z0-9]+").matcher(normalized);while(m.find())grams.add(m.group());
        List<Integer> han=new ArrayList<>();normalized.codePoints().filter(c->c>=0x4e00&&c<=0x9fff).forEach(han::add);
        for(int i=0;i+1<han.size();i++)grams.add(new String(Character.toChars(han.get(i)))+new String(Character.toChars(han.get(i+1))));
        return grams;
    }
    static double similarity(String a,String b) {
        String n1=normalize(a),n2=normalize(b);if(n1.isEmpty()||n2.isEmpty())return 0;
        if(n1.contains(n2)||n2.contains(n1))return 1.0;
        Set<String> g1=bigrams(n1),g2=bigrams(n2);if(g1.isEmpty()||g2.isEmpty())return 0;
        Set<String> common=new HashSet<>(g1);common.retainAll(g2);return (double)common.size()/Math.min(g1.size(),g2.size());
    }
    static int overrideTier(String content) {
        for(String s:MemoryRules.OVERRIDE_TIER1)if(content.contains(s))return 1;
        for(String s:MemoryRules.OVERRIDE_TIER2)if(content.contains(s))return 2;
        return 0;
    }
    /** Whether one wording swaps a word for another ("张三喜欢" vs "李四喜欢"): difflib's SequenceMatcher has a replace opcode. */
    static boolean substitution(String x,String y) {
        int[] a=normalize(x).codePoints().toArray(),b=normalize(y).codePoints().toArray();
        Map<Integer,List<Integer>> b2j=new HashMap<>();for(int j=0;j<b.length;j++)b2j.computeIfAbsent(b[j],k->new ArrayList<>()).add(j);
        List<int[]> blocks=new ArrayList<>();Deque<int[]> queue=new ArrayDeque<>();queue.push(new int[]{0,a.length,0,b.length});
        while(!queue.isEmpty()) {
            int[] q=queue.pop();int[] m=longest(a,b,b2j,q[0],q[1],q[2],q[3]);int i=m[0],j=m[1],k=m[2];
            if(k>0){blocks.add(m);if(q[0]<i&&q[2]<j)queue.push(new int[]{q[0],i,q[2],j});if(i+k<q[1]&&j+k<q[3])queue.push(new int[]{i+k,q[1],j+k,q[3]});}
        }
        blocks.sort((p,r)->p[0]!=r[0]?Integer.compare(p[0],r[0]):p[1]!=r[1]?Integer.compare(p[1],r[1]):Integer.compare(p[2],r[2]));
        blocks.add(new int[]{a.length,b.length,0});
        int i=0,j=0;for(int[] m:blocks){if(i<m[0]&&j<m[1])return true;i=m[0]+m[2];j=m[1]+m[2];}
        return false;
    }
    /** SequenceMatcher.find_longest_match without junk; ties go to the earliest match in a, then in b. */
    private static int[] longest(int[] a,int[] b,Map<Integer,List<Integer>> b2j,int alo,int ahi,int blo,int bhi) {
        int besti=alo,bestj=blo,best=0;Map<Integer,Integer> j2len=new HashMap<>();
        for(int i=alo;i<ahi;i++){Map<Integer,Integer> next=new HashMap<>();
            for(int j:b2j.getOrDefault(a[i],Collections.emptyList())){if(j<blo)continue;if(j>=bhi)break;int k=j2len.getOrDefault(j-1,0)+1;next.put(j,k);if(k>best){besti=i-k+1;bestj=j-k+1;best=k;}}
            j2len=next;}
        while(besti>alo&&bestj>blo&&a[besti-1]==b[bestj-1]){besti--;bestj--;best++;}
        while(besti+best<ahi&&bestj+best<bhi&&a[besti+best]==b[bestj+best])best++;
        return new int[]{besti,bestj,best};
    }

    // ---- companion_api.evaluate ----
    static final class Result {
        final JSONObject state;final JSONArray rejected;
        Result(JSONObject state,JSONArray rejected){this.state=state;this.rejected=rejected;}
    }
    static String iso(long millis,ZoneOffset zone) {
        OffsetDateTime t=Instant.ofEpochMilli(millis).atOffset(zone);
        String base=t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));int micros=t.getNano()/1000;
        String offset=zone.getTotalSeconds()==0?"+00:00":zone.getId();
        return base+(micros==0?"":String.format(Locale.ROOT,".%06d",micros))+offset;
    }
    static String nowIso(long now){return Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));}

    /**
     * One write request on the phone. original is the stored state (memories, archive, audit), incoming
     * at most a few new quote records; offsetMinutes is the device's UTC offset now, as the service gets it.
     */
    static Result evaluate(JSONObject original,JSONArray incoming,long now,int offsetMinutes)throws Exception {
        JSONObject state=MemoryLedger.copy(original);ZoneOffset zone=ZoneOffset.ofTotalSeconds(offsetMinutes*60);
        Map<String,JSONObject> originals=new HashMap<>();
        for(String pool:new String[]{"memories","archive"}){JSONArray rows=state.getJSONArray(pool);for(int i=0;i<rows.length();i++)originals.put(rows.getJSONObject(i).getString("id"),rows.getJSONObject(i));}
        for(int i=0;i<incoming.length();i++)originals.put(incoming.getJSONObject(i).getString("id"),new JSONObject(incoming.getJSONObject(i).toString()));
        List<JSONObject> active=new ArrayList<>(),archived=new ArrayList<>();
        JSONArray rows=state.getJSONArray("memories");for(int i=0;i<rows.length();i++)active.add(toEngine(rows.getJSONObject(i),zone));
        rows=state.getJSONArray("archive");for(int i=0;i<rows.length();i++)archived.add(toEngine(rows.getJSONObject(i),zone));
        List<JSONObject> history=new ArrayList<>();
        archiveStale(active,archived,history,now);
        JSONArray rejected=new JSONArray();List<JSONObject> events=new ArrayList<>();
        for(int n=0;n<incoming.length();n++) {
            JSONObject row=incoming.getJSONObject(n);String reason=noise(row.getString("text"));
            if(reason!=null){rejected.put(new JSONObject().put("id",row.getString("id")).put("reason",reason));continue;}
            active.addAll(deduplicate(toEngine(row,zone),active,events,now));
            if(active.size()>MAX_ACTIVE)throw new java.io.IOException("记忆已满，请先删除几条");
        }
        // Reattach quote provenance after merges; the engine's annotated content stays separate from the user's quote.
        for(JSONObject event:events) {
            if("MERGE".equals(event.optString("op"))) {
                JSONObject target=originals.get(event.getString("id")),from=originals.get(event.getString("from"));
                JSONArray sources=lastSources(MemoryLedger.sources(target),MemoryLedger.sources(from)); // before source is overwritten
                target.put("text",from.getString("text")).put("source",from.getString("source")).put("at",from.getLong("at"))
                    .put("sources",sources).put("pinned",target.optBoolean("pinned")||from.optBoolean("pinned"));
            } else if("NOOP".equals(event.optString("op"))) {
                JSONObject from=originals.get(event.getString("id"));String targetId=event.optString("duplicate_of",null);
                if(targetId==null)for(JSONObject r:active)if(normalize(r.getString("content")).equals(normalize(from.getString("text")))){targetId=r.getString("id");break;}
                JSONObject target=targetId==null?null:originals.get(targetId);
                if(target!=null)target.put("sources",lastSources(MemoryLedger.sources(target),new JSONArray().put(from.getString("source")))).put("pinned",target.optBoolean("pinned")||from.optBoolean("pinned"));
            }
        }
        history.addAll(events);
        archiveStale(active,archived,history,now);
        JSONArray memories=new JSONArray(),archive=new JSONArray();
        for(JSONObject r:active)memories.put(toPhone(r,originals));
        for(int i=Math.max(0,archived.size()-MAX_ARCHIVE);i<archived.size();i++)archive.put(toPhone(archived.get(i),originals));
        JSONArray audit=state.getJSONArray("audit");
        for(JSONObject e:history){JSONObject a=new JSONObject();for(String k:new String[]{"op","id","by","from","reason","decided_by"})if(e.has(k))a.put(k,e.get(k));a.put("at",now);audit.put(a);}
        while(audit.length()>MAX_AUDIT)audit.remove(0);
        state.put("memories",memories).put("archive",archive).put("audit",audit).put("vector_cache",new JSONObject());
        return new Result(state,rejected);
    }
    private static JSONArray lastSources(JSONArray first,JSONArray second) {
        LinkedHashSet<String> all=new LinkedHashSet<>();for(JSONArray a:new JSONArray[]{first,second})for(int i=0;i<a.length();i++)all.add(a.optString(i));
        List<String> list=new ArrayList<>(all);return new JSONArray(list.subList(Math.max(0,list.size()-200),list.size()));
    }
    static JSONObject toEngine(JSONObject row,ZoneOffset zone)throws Exception {
        JSONObject record=new JSONObject(),engine=row.optJSONObject("engine");
        if(engine!=null)for(Iterator<String> k=engine.keys();k.hasNext();){String key=k.next();if(ENGINE_FIELDS.contains(key))record.put(key,engine.get(key));}
        record.put("id",row.getString("id")).put("source",row.getString("source"));
        if(!record.has("content"))record.put("content",row.getString("text"));
        if(!record.has("created"))record.put("created",iso(row.getLong("at"),ZoneOffset.UTC));
        Object[] weight=importance(row.getString("text"));double w=(Double)weight[0];String category=(String)weight[1];
        if(!record.has("importance"))record.put("importance",row.optBoolean("pinned")?Math.max(w,0.8):w);
        if(!record.has("score"))record.put("score",record.getDouble("importance"));
        if(!record.has("entities"))record.put("entities",new JSONArray());
        if(!record.has("is_identity"))record.put("is_identity","identity_health_safety".equals(category));
        if(record.optString("valid_at").isEmpty())groundTime(record,category,row.getLong("at"),zone);
        return record;
    }
    /** memory.ground_record_time plus the service's conversion of expires_at to the device's local midnight. */
    static void groundTime(JSONObject record,String category,long at,ZoneOffset zone)throws Exception {
        LocalDate observed=Instant.ofEpochMilli(at).atOffset(zone).toLocalDate();
        TimeGround.Result g=TimeGround.ground(record.getString("content"),observed);
        record.put("content",g.text).put("valid_at",iso(at,zone));
        if(g.event==null)return;
        record.put("event_date",g.event.toString());
        if(!g.event.isBefore(observed)&&!Arrays.asList(MemoryRules.PERMANENT_CATEGORIES).contains(category)&&record.optString("expires_at").isEmpty())
            record.put("expires_at",iso(g.event.plusDays(1).atStartOfDay().toInstant(zone).toEpochMilli(),ZoneOffset.UTC));
    }
    static long parse(String iso){try{return OffsetDateTime.parse(iso.replace("Z","+00:00")).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
    private static void archiveStale(List<JSONObject> active,List<JSONObject> archived,List<JSONObject> history,long now)throws Exception {
        for(Iterator<JSONObject> it=active.iterator();it.hasNext();) {
            JSONObject r=it.next();long expires=parse(r.optString("expires_at"));String reason=!r.optString("invalid_at").isEmpty()?"superseded":expires!=0&&expires<=now?"expired":null;
            if(reason==null)continue;
            r.put("archived_reason",reason).put("archived_at",nowIso(now));it.remove();archived.add(r);
            history.add(new JSONObject().put("op","ARCHIVE").put("id",r.getString("id")).put("reason",reason));
        }
    }
    /** memory._deduplicate_with_operator for one new record: the records it adds (none, or itself). */
    private static List<JSONObject> deduplicate(JSONObject fresh,List<JSONObject> existing,List<JSONObject> events,long now)throws Exception {
        String content=fresh.getString("content");
        if(filtered(content,fresh.getDouble("importance"))){events.add(new JSONObject().put("op","NOOP").put("id",fresh.getString("id")));return Collections.emptyList();}
        if(existing.isEmpty()){events.add(new JSONObject().put("op","ADD").put("id",fresh.getString("id")));return Collections.singletonList(fresh);}
        JSONObject target=null;double best=0;
        for(JSONObject old:existing){double s=similarity(content,old.getString("content"));if(s>best){best=s;target=old;}}
        if(target!=null&&normalize(content).equals(normalize(target.getString("content")))){events.add(new JSONObject().put("op","NOOP").put("id",fresh.getString("id")));return Collections.emptyList();}
        int tier=overrideTier(content);
        if(tier>0&&target!=null&&best>=MemoryRules.MIN_OVERLAP) {
            supersede(target,fresh,tier,events,now);
            events.add(new JSONObject().put("op","ADD").put("id",fresh.getString("id")));return Collections.singletonList(fresh);
        }
        if(target!=null&&best>=MemoryRules.MERGE_OVERLAP&&!substitution(content,target.getString("content"))) {
            if(length(normalize(content))>length(normalize(target.getString("content")))) {
                events.add(new JSONObject().put("op","MERGE").put("id",target.getString("id")).put("from",fresh.getString("id")));
                target.put("content",content).put("importance",Math.max(target.optDouble("importance",0),fresh.optDouble("importance",0)))
                    .put("score",Math.max(target.optDouble("score",0),fresh.optDouble("score",0))).put("updated",nowIso(now)).put("merged_from",fresh.getString("id"));
            } else events.add(new JSONObject().put("op","NOOP").put("id",fresh.getString("id")).put("duplicate_of",target.getString("id")));
            return Collections.emptyList();
        }
        events.add(new JSONObject().put("op","ADD").put("id",fresh.getString("id")));return Collections.singletonList(fresh);
    }
    private static void supersede(JSONObject target,JSONObject fresh,int tier,List<JSONObject> events,long now)throws Exception {
        double penalty=tier==1?MemoryRules.TIER1_PENALTY:MemoryRules.TIER2_PENALTY;
        target.put("score",target.optDouble("score",target.optDouble("importance",0.5))*penalty).put("override_tier",tier)
            .put("conflict_downgraded",true).put("downgrade_reason",fresh.getString("id")).put("downgrade_at",nowIso(now));
        if(tier==1)target.put("invalid_at",fresh.optString("created",nowIso(now))).put("superseded_by",fresh.getString("id"));
        events.add(new JSONObject().put("op",tier==1?"INVALIDATE":"DOWNGRADE").put("id",target.getString("id")).put("by",fresh.getString("id")));
    }
    private static JSONObject toPhone(JSONObject record,Map<String,JSONObject> originals)throws Exception {
        JSONObject row=new JSONObject(originals.get(record.getString("id")).toString()),engine=new JSONObject();
        for(Iterator<String> k=record.keys();k.hasNext();){String key=k.next();if(ENGINE_FIELDS.contains(key))engine.put(key,record.get(key));}
        return row.put("engine",engine);
    }
}
