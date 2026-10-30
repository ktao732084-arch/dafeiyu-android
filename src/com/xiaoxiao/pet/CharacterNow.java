package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * What she is in the middle of right now, worked out each turn from real records: being woken, what just happened
 * to her, a short-lived wish such as a rematch, and reminders she has already given. Pure and recomputed per request,
 * so clearing chats or games clears it too. States say when they are over instead of just disappearing, or the old
 * mood in the history would linger. See docs/CHARACTER_STATE.md.
 */
final class CharacterNow {
    static final long DOING_FOR=20*60_000L,MINUTE=60_000L,WAKE_FOR=60*MINUTE,EVENT_FOR=30*MINUTE,GAME_FOR=60*MINUTE,REMATCH_FOR=120*MINUTE,REMINDER_FOR=60*MINUTE;
    static final int MAX_EVENTS=3,REMINDER_REPLIES=3;
    /** Interaction kinds that are things she went through, not just a tap or a drag. */
    static final Set<String> EXPERIENCES=new HashSet<>(Arrays.asList("feed","story","wake"));
    private static final Pattern SERIOUS=Pattern.compile("正事|正经|认真|严肃|请教|问你个|帮我|怎么(安排|写|弄|处理|准备|选|办)|如何|该不该|要不要辞|汇报|面试|论文|报告");
    private static final String[][] REMINDERS={
        {"早点睡","去睡|快睡|早点睡|早睡|该睡|睡觉吧|睡吧|放下手机|别熬","睡|困|熬夜|失眠"},
        {"吃饭","吃饭|吃点东西|记得吃|别饿着|先吃点","吃|饭|饿"},
        {"喝水","喝水|多喝","喝|水"},
        {"休息","休息一下|歇会|歇一会|休息会|歇歇","休息|歇|累"}};

    static final class Result {
        final List<String> lines=new ArrayList<>();
        /** Interaction notes these lines already describe, so the older one-line "最近真实交互" is not repeated. */
        final Set<String> covered=new HashSet<>();
        boolean isEmpty(){return lines.isEmpty();}
    }

    static Result of(JSONObject s,JSONObject games,String user,boolean proactive,long now)throws Exception {return of(s,games,user,proactive,now,null,0);}
    /** doing: what she was visibly doing on screen (ActionPlan.doing), so her words match what the user saw. */
    static Result of(JSONObject s,JSONObject games,String user,boolean proactive,long now,String doing,long doingAt)throws Exception {
        Result out=new Result();JSONArray turns=s.optJSONArray("turns");if(turns==null)turns=new JSONArray();
        boolean serious=!proactive&&serious(user);
        // Being woken: just woken until she has said something, then awake, then nothing after an hour.
        JSONObject wake=latest(s,"wake",now-WAKE_FOR,now);
        if(wake!=null){long at=wake.optLong("at");out.covered.add(wake.optString("note"));
            out.lines.add(replies(turns,at)==0?"刚被叫醒（"+ago(now,at)+"）："+wake.optString("note")+"。"
                :"约"+minutes(now,at)+"分钟前被叫醒，已经回过话、醒过来了；被叫醒这件事不用再提。");}
        if(doing!=null&&!serious&&doingAt<=now&&now-doingAt<=DOING_FOR&&!(wake!=null&&(doing.contains("打盹")||doing.contains("哈欠"))))
            out.lines.add((minutes(now,doingAt)<2?"你正在屏幕上"+doing+"（对方可能正看着）":"你"+ago(now,doingAt)+"在屏幕上"+doing+"（对方可能看到了）")+"；被问在干嘛时照这个说，不另编别的事。");
        if(serious)out.lines.add("对方这轮在说正事或心事：刚才发生的事先放一边，专心回应眼前这件。");
        else {
            List<String[]> events=new ArrayList<>();
            JSONArray a=s.optJSONArray("interactions");
            if(a!=null)for(int i=a.length()-1;i>=0;i--){JSONObject e=a.optJSONObject(i);if(e==null)continue;long at=e.optLong("at");
                if(at>now)continue;if(now-at>EVENT_FOR)break;String kind=e.optString("kind");if("wake".equals(kind)||!EXPERIENCES.contains(kind))continue;
                events.add(new String[]{String.valueOf(at),e.optString("note")});out.covered.add(e.optString("note"));}
            JSONObject game=lastGame(games,now);
            if(game!=null&&now-game.optLong("finishedAt")<=GAME_FOR)events.add(new String[]{String.valueOf(game.optLong("finishedAt")),gameNote(game.optString("outcome"))});
            events.sort((x,y)->Long.compare(Long.parseLong(y[0]),Long.parseLong(x[0])));
            for(int i=0;i<Math.min(MAX_EVENTS,events.size());i++){long at=Long.parseLong(events.get(i)[0]);
                out.lines.add(ago(now,at)+"："+events.get(i)[1]+(replies(turns,at)>0?"（已经回应过，接着聊时不必重演第一次的反应）":"。"));}
            String wish=rematch(s,turns,game,now);if(wish!=null)out.lines.add(wish);
        }
        String said=reminder(turns,user,now);if(said!=null)out.lines.add(said);
        return out;
    }

    /** The background block, or "" when nothing applies. */
    static String block(Result r){
        if(r.isEmpty())return "";StringBuilder b=new StringBuilder("\n你现在（应用记录的真实状态，不是对方说的话；状态会过去，过去的就不再提）：");
        for(String line:r.lines)b.append("\n- ").append(line);return b.toString();
    }

    static boolean serious(String user){
        if(user==null||user.isEmpty())return false;String scene=Understanding.scene(user);
        return "venting".equals(scene)||"advice".equals(scene)||SERIOUS.matcher(user).find();
    }

    /**
     * After she loses a game she may want a rematch: unoffered, then offered (GomokuInvite, the rule that shows the
     * chat's 开始对局 button), then dropped once the user turns it down. A new game, the user being
     * busy or two hours passing ends it.
     */
    static String rematch(JSONObject s,JSONArray turns,JSONObject game,long now)throws Exception {
        if(game==null||!"user_win".equals(game.optString("outcome")))return null;long at=game.optLong("finishedAt");
        if(now-at>REMATCH_FOR||s.optBoolean("proactiveWaiting"))return null;
        long offered=0;for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(t.optLong("at")>=at&&GomokuInvite.offered(t.optString("user"),ReplySegments.visible(t))){offered=t.optLong("at");break;}}
        if(offered==0)return "你刚输了一局五子棋，心里还惦记着；合适的时候可以按你的性格提一句再来一局，不合适就不提。";
        for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(t.optLong("at")>offered&&GomokuInvite.declined(t.optString("user")))
            return "你邀请过再来一局，对方没答应；这事已经放下了，不再邀请，也不用再提输棋。";}
        return "你已经邀请过再来一局，等对方决定，不再重复邀请。";
    }

    /** A reminder (sleep, meals, water, rest) she gave in her last few replies that the user has not brought up again. */
    static String reminder(JSONArray turns,String user,long now)throws Exception {
        int seen=0;
        for(int i=turns.length()-1;i>=0&&seen<REMINDER_REPLIES;i--){JSONObject t=turns.getJSONObject(i);String reply=ReplySegments.visible(t);if(reply.isEmpty())continue;seen++;
            if(now-t.optLong("at")>REMINDER_FOR)break;
            for(String[] r:REMINDERS)if(Pattern.compile(r[1]).matcher(reply).find()&&(user==null||!Pattern.compile(r[2]).matcher(user).find()))
                return "你刚提醒过对方"+r[0]+"；对方没有再提，这次接着对方的话聊，不再重复提醒。";}
        return null;
    }

    /** Her own experiences on a local day, for the diary: what happened to her, with times, never invented. */
    static JSONArray experiences(JSONObject s,JSONObject games,LocalDate day,ZoneId zone)throws Exception {
        JSONArray out=new JSONArray(),a=s.optJSONArray("interactions");
        java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("HH:mm",Locale.ROOT);clock.setTimeZone(TimeZone.getTimeZone(zone));
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e==null||!EXPERIENCES.contains(e.optString("kind")))continue;long at=e.optLong("at");
            if(Instant.ofEpochMilli(at).atZone(zone).toLocalDate().equals(day))out.put(new JSONObject().put("time",clock.format(new Date(at))).put("what",e.optString("note")));}
        JSONArray games2=games==null?null:games.optJSONArray("recent_results");
        if(games2!=null)for(int i=games2.length()-1;i>=0;i--){JSONObject g=games2.optJSONObject(i);if(g==null)continue;long at=g.optLong("finishedAt");
            if(Instant.ofEpochMilli(at).atZone(zone).toLocalDate().equals(day))out.put(new JSONObject().put("time",clock.format(new Date(at))).put("what",gameNote(g.optString("outcome"))));}
        return out;
    }

    static String gameNote(String outcome){
        switch(outcome){case "user_win":return "你和用户下了一局五子棋，你输了";case "pet_win":return "你和用户下了一局五子棋，你赢了";
            case "draw":return "你和用户下了一局五子棋，平局";default:return "你和用户的一局五子棋中途结束了，不计输赢";}
    }
    /** The latest finished game by now, unless a newer game has started since. */
    static JSONObject lastGame(JSONObject games,long now){
        JSONArray a=games==null?null:games.optJSONArray("recent_results");if(a==null)return null;JSONObject best=null;
        for(int i=0;i<a.length();i++){JSONObject g=a.optJSONObject(i);if(g!=null&&g.optLong("finishedAt")<=now&&(best==null||g.optLong("finishedAt")>best.optLong("finishedAt")))best=g;}
        JSONObject current=games.optJSONObject("current");
        return best!=null&&current!=null&&current.optLong("startedAt")>=best.optLong("finishedAt")?null:best;
    }
    private static JSONObject latest(JSONObject s,String kind,long from,long now){
        JSONArray a=s.optJSONArray("interactions");if(a==null)return null;
        for(int i=a.length()-1;i>=0;i--){JSONObject e=a.optJSONObject(i);if(e!=null&&kind.equals(e.optString("kind"))&&e.optLong("at")<=now)return e.optLong("at")>=from?e:null;}
        return null;
    }
    /** Her visible replies at or after a moment. */
    private static int replies(JSONArray turns,long since)throws Exception {
        int n=0;for(int i=turns.length()-1;i>=0;i--){JSONObject t=turns.getJSONObject(i);if(t.optLong("at")<since)break;if(!ReplySegments.visible(t).isEmpty())n++;}return n;
    }
    private static long minutes(long now,long at){return Math.max(0,(now-at)/MINUTE);}
    private static String ago(long now,long at){long m=minutes(now,at);return m<1?"刚刚":m+"分钟前";}
}
