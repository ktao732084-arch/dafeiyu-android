package com.xiaoxiao.pet;
import org.json.*;
import java.time.*;

/** Model-chosen check times with a small anti-loop floor, not a daily talking quota. */
final class Initiative {
    static final int MIN_MINUTES=2,MAX_MINUTES=180,DEFAULT_MINUTES=15,KEEP_INTERACTIONS=200,KEEP_CHECKS=100;
    static final long MIN_GAP=MIN_MINUTES*60_000L,IDLE_GRACE=30_000L;
    static boolean quiet(String reply){return reply.trim().startsWith("[安静陪伴]");}
    static int minutes(Object value){
        if(!(value instanceof Number))return DEFAULT_MINUTES;double n=((Number)value).doubleValue();
        if(Double.isNaN(n)||Double.isInfinite(n)||n!=Math.rint(n))return DEFAULT_MINUTES;
        return (int)Math.max(MIN_MINUTES,Math.min(MAX_MINUTES,n));
    }
    static long latestTouch(JSONObject s){JSONArray a=s.optJSONArray("interactions");long latest=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null)latest=Math.max(latest,e.optLong("at"));}return latest;}
    static long nextAt(JSONObject s){return s.optLong("proactiveNextAt",Math.max(s.optLong("lastUser"),latestTouch(s))+DEFAULT_MINUTES*60_000L);}
    static boolean due(JSONObject s,long now,long liveTouch){
        long stored=Math.max(s.optLong("lastUser"),latestTouch(s)),activity=Math.max(stored,liveTouch),attempt=s.optLong("lastAttempt");
        return stored>0&&now>=activity&&now-activity>=IDLE_GRACE&&now>=attempt&&now-attempt>=MIN_GAP&&now>=nextAt(s);
    }
    static void interaction(JSONObject s,String note,long at)throws Exception{
        JSONArray a=s.optJSONArray("interactions");if(a==null)s.put("interactions",a=new JSONArray());
        a.put(new JSONObject().put("at",at).put("timeZone",ZoneId.systemDefault().getId()).put("kind","touch").put("note",AgentPolicy.clip(note,120)));
        while(a.length()>KEEP_INTERACTIONS)a.remove(0);
        long next=nextAt(s);s.put("proactiveNextAt",Math.min(next,at+MIN_GAP));
    }
    static void reserve(JSONObject s,long now)throws Exception{s.put("proactiveNextAt",now+DEFAULT_MINUTES*60_000L);}
    static void decision(JSONObject s,String outcome,int delay,long now)throws Exception{
        int failures="error".equals(outcome)?Math.min(5,s.optInt("proactiveFailures")+1):0;
        int wait="error".equals(outcome)?Math.min(MAX_MINUTES,10*(1<<(failures-1))):minutes(delay);
        s.put("proactiveFailures",failures).put("proactiveNextAt",now+wait*60_000L).put("proactiveOutcome",outcome).put("proactiveDecidedAt",now);
        JSONArray a=s.optJSONArray("initiativeChecks");if(a==null)s.put("initiativeChecks",a=new JSONArray());
        a.put(new JSONObject().put("at",now).put("outcome",outcome).put("nextAt",s.getLong("proactiveNextAt")));
        while(a.length()>KEEP_CHECKS)a.remove(0);
    }
    static void afterUser(JSONObject s,int delay,long now)throws Exception{s.put("proactiveNextAt",now+minutes(delay)*60_000L).put("proactiveFailures",0);}
    static String query(JSONObject s){
        StringBuilder q=new StringBuilder();JSONArray turns=s.optJSONArray("turns");int count=0;
        if(turns!=null)for(int i=turns.length()-1;i>=0&&count<3;i--){String user=turns.optJSONObject(i).optString("user");if(!user.isEmpty()){q.append(AgentPolicy.clip(user,100)).append(' ');count++;}}
        return q.toString();
    }
    static String localTime(long at,String zone){try{return Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).toString();}catch(Exception invalid){return Instant.ofEpochMilli(at).toString();}}
    static JSONArray recentInteractions(JSONObject s)throws Exception{
        JSONArray events=s.optJSONArray("interactions"),recent=new JSONArray();
        if(events!=null)for(int i=Math.max(0,events.length()-12);i<events.length();i++){JSONObject e=new JSONObject(events.getJSONObject(i).toString());e.put("local_time",localTime(e.optLong("at"),e.optString("timeZone")));recent.put(e);}return recent;
    }
    static JSONObject context(JSONObject s,long now)throws Exception{
        JSONObject out=new JSONObject().put("now",now).put("last_user_at",s.optLong("lastUser")).put("last_check_at",s.optLong("lastAttempt"));
        out.put("recent_interactions",recentInteractions(s));
        JSONArray turns=s.optJSONArray("turns"),exchanges=new JSONArray();int unanswered=0;
        if(turns!=null){for(int i=Math.max(0,turns.length()-6);i<turns.length();i++){
            JSONObject t=turns.getJSONObject(i);exchanges.put(new JSONObject().put("at",t.optLong("at")).put("timeZone",t.optString("timeZone")).put("local_time",localTime(t.optLong("at"),t.optString("timeZone"))).put("proactive",t.optBoolean("proactive")).put("user",AgentPolicy.clip(t.optString("user"),140)).put("shown_reply",AgentPolicy.clip(ReplySegments.visible(t),140)));
        }for(int i=turns.length()-1;i>=0;i--){JSONObject t=turns.getJSONObject(i);if(!t.optString("user").isEmpty())break;if(t.optBoolean("proactive")&&!ReplySegments.visible(t).isEmpty())unanswered++;}}
        out.put("recent_exchanges",exchanges).put("unanswered_proactive_messages",unanswered).put("last_outcome",s.optString("proactiveOutcome"));return out;
    }
}
