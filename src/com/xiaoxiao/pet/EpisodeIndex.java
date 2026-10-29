package com.xiaoxiao.pet;

import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Episodic recall over the whole conversation, not only the recent window: every turn the user and she
 * actually exchanged (archived ones included) is indexed in memory with BM25 over character bigrams and
 * ASCII words, so "上次说的那家火锅店" can bring back what was said weeks ago, with its date.
 * Only the user's words and her displayed reply are indexed; nothing is inferred. No model calls.
 */
final class EpisodeIndex {
    static final int LIMIT=5;
    static final double USER_WEIGHT=1.0,REPLY_WEIGHT=0.35,MIN_SCORE=1.5,K1=1.2,B=0.75;
    static final class Episode {
        final String id,user,reply;final long at;final Map<String,Integer> userTerms,replyTerms;final int userLength,replyLength;
        Episode(String id,long at,String user,String reply){
            this.id=id;this.at=at;this.user=user;this.reply=reply;userTerms=terms(user);replyTerms=terms(reply);userLength=size(userTerms);replyLength=size(replyTerms);
        }
    }
    private final Map<String,Episode> episodes=new LinkedHashMap<>();
    private final Map<String,Integer> userDf=new HashMap<>(),replyDf=new HashMap<>();
    private long userTotal,replyTotal;

    static Map<String,Integer> terms(String text) {
        Map<String,Integer> out=new HashMap<>();String clean=text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]"," ");
        for(String part:clean.split("\\s+")){
            if(part.isEmpty())continue;
            if(part.matches("[a-z0-9]+")){out.merge(part,1,Integer::sum);continue;}
            int[] cps=part.codePoints().toArray();
            if(cps.length==1)out.merge(part,1,Integer::sum);
            for(int i=0;i+1<cps.length;i++)out.merge(new String(cps,i,2),1,Integer::sum);
        }
        return out;
    }
    private static int size(Map<String,Integer> terms){int n=0;for(int v:terms.values())n+=v;return n;}

    int size(){return episodes.size();}
    boolean contains(String id){return episodes.containsKey(id);}
    /** A turn as stored (recent or archived). Proactive turns have no user words and are not indexed. */
    void add(JSONObject turn)throws Exception {
        String id=ChatArchive.id(turn),user=turn.optString("user").trim();
        if(user.isEmpty()||episodes.containsKey(id))return;
        Episode e=new Episode(id,turn.optLong("at"),user,ReplySegments.visible(turn).trim());episodes.put(id,e);
        for(String t:e.userTerms.keySet())userDf.merge(t,1,Integer::sum);for(String t:e.replyTerms.keySet())replyDf.merge(t,1,Integer::sum);
        userTotal+=e.userLength;replyTotal+=e.replyLength;
    }
    /** A reply segment became visible: re-index that turn with the text the user has now seen. */
    void update(JSONObject turn)throws Exception {remove(Collections.singleton(ChatArchive.id(turn)));add(turn);}
    void remove(Collection<String> ids) {
        for(String id:ids){Episode e=episodes.remove(id);if(e==null)continue;
            for(String t:e.userTerms.keySet())decrement(userDf,t);for(String t:e.replyTerms.keySet())decrement(replyDf,t);
            userTotal-=e.userLength;replyTotal-=e.replyLength;}
    }
    void clear(){episodes.clear();userDf.clear();replyDf.clear();userTotal=replyTotal=0;}
    private static void decrement(Map<String,Integer> df,String t){Integer n=df.get(t);if(n==null)return;if(n<=1)df.remove(t);else df.put(t,n-1);}

    private double bm25(Map<String,Integer> query,Map<String,Integer> doc,int length,Map<String,Integer> df,long total) {
        int n=episodes.size();if(n==0||length==0)return 0;double avg=Math.max(1,(double)total/n),score=0;
        for(String t:query.keySet()){Integer tf=doc.get(t);if(tf==null)continue;int d=df.getOrDefault(t,0);
            double idf=Math.log(1+(n-d+0.5)/(d+0.5));score+=idf*tf*(K1+1)/(tf+K1*(1-B+B*length/avg));}
        return score;
    }
    /**
     * The most relevant past exchanges for this message, oldest first so they read like a timeline.
     * exclude: turns already in the prompt window, and sources of corrected (stale) memories.
     */
    List<Episode> search(String query,Set<String> exclude,long now,int limit) {
        Map<String,Integer> q=terms(query);if(q.isEmpty())return Collections.emptyList();
        List<Object[]> scored=new ArrayList<>();
        for(Episode e:episodes.values()){
            if(exclude.contains(e.id))continue;
            double s=USER_WEIGHT*bm25(q,e.userTerms,e.userLength,userDf,userTotal)+REPLY_WEIGHT*bm25(q,e.replyTerms,e.replyLength,replyDf,replyTotal);
            if(s<MIN_SCORE)continue;
            double days=Math.max(0,(now-e.at)/86_400_000.0);scored.add(new Object[]{s+0.3*Math.exp(-days/30),e});
        }
        scored.sort((x,y)->Double.compare((Double)y[0],(Double)x[0]));
        List<Episode> out=new ArrayList<>();for(int i=0;i<Math.min(limit,scored.size());i++)out.add((Episode)scored.get(i)[1]);
        out.sort(Comparator.comparingLong(e->e.at));return out;
    }
    /** Literal search for the chat page: turns whose words or shown reply contain the text (case and spaces ignored), newest first. */
    List<Episode> find(String text,int limit) {
        String q=fold(text);if(q.isEmpty())return Collections.emptyList();List<Episode> out=new ArrayList<>();
        for(Episode e:episodes.values())if(fold(e.user).contains(q)||fold(e.reply).contains(q))out.add(e);
        out.sort((x,y)->Long.compare(y.at,x.at));return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    static String fold(String s){return s.toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    /** Prompt form: when, what the user said, and the first sentence of what she replied. */
    static JSONArray describe(List<Episode> found)throws Exception {
        JSONArray out=new JSONArray();SimpleDateFormat when=new SimpleDateFormat("yyyy-MM-dd E HH:mm",Locale.CHINA);
        for(Episode e:found){JSONObject row=new JSONObject().put("at",when.format(new Date(e.at))).put("user",AgentPolicy.clip(e.user,200));
            if(!e.reply.isEmpty())row.put("you",AgentPolicy.clip(AgentPolicy.compressReply(e.reply),80));out.put(row);}
        return out;
    }
}
