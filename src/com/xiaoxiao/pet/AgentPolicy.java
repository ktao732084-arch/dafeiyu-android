package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/** Pure, deterministic decisions. The model can suggest an expression, not run code. */
final class AgentPolicy {
    static final Set<String> EMOTIONS = new HashSet<>(Arrays.asList("calm","happy","shy","curious","concerned","playful","sleepy"));
    static String emotion(String value) { return EMOTIONS.contains(value) ? value : "calm"; }
    /**
     * Her mood carries over between turns: the model names it with a strength (1–3) and a short cause in its
     * own words; it fades one step every MOOD_FADE_MINUTES without new events, back to calm. A simple model
     * of continuity, not an appraisal engine: the cause is her reading of this turn, never a memory.
     */
    static final int MOOD_FADE_MINUTES=40;
    static String moodName(String emotion){switch(emotion(emotion)){case "happy":return "开心";case "shy":return "害羞";case "curious":return "好奇";case "concerned":return "担心";case "playful":return "想闹一闹";case "sleepy":return "犯困";default:return "平静";}}
    static int moodLevel(Object value){int n=value instanceof Number?((Number)value).intValue():0;if(value instanceof String)try{n=Integer.parseInt(((String)value).trim());}catch(NumberFormatException e){n=0;}return n>=1&&n<=3?n:0;}
    static String moodCause(String value){
        if(value==null)return "";String t=value.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();int n=t.codePointCount(0,t.length());
        return n<2||n>30||sensitive(t)?"":t;
    }
    /** {emotion, level, cause, minutes} as of now; level 0 and calm once it has faded. */
    static JSONObject moodNow(JSONObject state,long now){
        JSONObject out=new JSONObject();String e=emotion(state.optString("emotion"));long at=state.optLong("emotionAt");
        // Moods saved before strengths existed count as mild.
        int level=state.has("moodLevel")?Math.max(1,Math.min(3,state.optInt("moodLevel",1))):1;long minutes=at>0?Math.max(0,(now-at)/60000):0;int left=level-(int)(minutes/MOOD_FADE_MINUTES);
        try{
            if(at<=0||"calm".equals(e)||left<=0)return out.put("emotion","calm").put("level",0);
            return out.put("emotion",e).put("level",left).put("cause",state.optString("moodCause","")).put("minutes",minutes);
        }catch(JSONException impossible){return out;}
    }
    /** The background line about her mood; the cause only when memory is on, since it may echo the chat. */
    static String moodLine(JSONObject mood,boolean withCause){
        int level=mood.optInt("level");if(level<=0)return "你此刻心情平静，这轮发生的事可以让它起变化。";
        String cause=withCause?mood.optString("cause"):"";
        return "你此刻的心情："+moodName(mood.optString("emotion"))+"（"+(level>=3?"很强烈":level==2?"比较明显":"淡淡的")+"）"+(cause.isEmpty()?"":"，起因："+cause)
            +"，约 "+mood.optLong("minutes")+" 分钟前。心情会延续，也会慢慢淡去，这轮发生的事可以改变它；不必说出来，让它自然带进语气里。";
    }
    static boolean quiet(int hour) { return hour >= 23 || hour < 8; }
    static Set<String> tokens(String s) {
        Set<String> out=new HashSet<>(); String clean=s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", " ");
        for(String part:clean.split("\\s+")) {
            if(part.isEmpty()) continue;
            if(part.matches("[a-z0-9]+")) out.add(part);
            else if(part.length()==1) out.add(part);
            else for(int i=0;i<part.length()-1;i++) out.add(part.substring(i,i+2));
        }
        return out;
    }
    static double relevance(String query,String content) {
        Set<String> a=tokens(query), b=tokens(content); if(a.isEmpty()||b.isEmpty()) return 0;
        // A one-character query ("猫") has no bigram, so match it as a substring instead.
        String lower=content.toLowerCase(Locale.ROOT);
        int common=0; for(String t:a)if(b.contains(t)||t.length()==1&&t.charAt(0)>127&&lower.contains(t)) common++;
        return common / Math.sqrt((double)a.size()*b.size());
    }
    static boolean sensitive(String text) {
        return text.matches("(?is).*(密码|口令|身份证|银行卡|api.?key|secret|token\\s*[:：=]|sk-[a-z0-9]|\\b\\d{11,}\\b).*" );
    }
    /** Health and dietary facts the reply must never contradict, whatever the topic. */
    static boolean critical(String text) {
        return text.matches("(?s).*(过敏|忌口|不能吃|不吃|吃素|素食|清真|乳糖不耐|糖尿病|高血压|哮喘|心脏病|癫痫|怀孕|孕期|哺乳|在吃药|服药|手术).*");
    }
    static boolean validEvidence(String user,String quote) {
        return quote!=null && quote.length()>=3 && quote.length()<=240 && user.contains(quote) && !sensitive(quote)
            && !quote.matches("(?s).*[?？].*") && !user.matches("(?s).*(假如|假设|假装|角色扮演|举个例子|例如我).*" );
    }
    /** The JSON envelope, including one a model wrapped in a short preface such as "好的～\n{...}". */
    static String envelope(String text) {
        if(text.startsWith("{"))return text;
        int open=text.indexOf('{'),close=text.lastIndexOf('}');
        return open>=0&&close>open&&text.indexOf("\"reply\"",open)>open?text.substring(open,close+1):null;
    }
    /** What the bubble may show while streaming: the reply field, or plain text when the model ignored JSON. */
    static String replyText(String text){return text.replaceFirst("(?i)^(?:\\s*\\[(?:tool|assistant)\\]\\s*)+","");}
    static String streamPreview(String raw) {
        String text=raw.replaceAll("(?s)<think>.*?</think>","").trim();
        if(text.startsWith("<think>"))return "";
        String visible=replyText(text.indexOf('{')>=0||text.startsWith("`")?partialReply(text):text);
        String lower=visible.toLowerCase(Locale.ROOT);if("[tool]".startsWith(lower)||"[assistant]".startsWith(lower))return "";
        return visible;
    }
    static boolean reasoningModel(String model) { return model.toLowerCase(Locale.ROOT).matches("(?s).*(reason|-r1|thinking|qwq).*"); }
    /** "：<message>" from an error body in OpenAI, Azure or plain-text form; never HTML pages or the caller's key. */
    static String providerError(String body,String secret) {
        String text=body==null?"":body.trim(),message="";
        try{JSONObject o=new JSONObject(text);Object error=o.opt("error");
            if(error instanceof JSONObject)message=((JSONObject)error).optString("message");else if(error instanceof String)message=(String)error;
            if(message.isEmpty())message=o.optString("message",o.optString("msg",o.optString("detail","")));}
        catch(JSONException e){if(!text.startsWith("<"))message=text;}
        if(secret!=null&&secret.length()>=4)message=message.replace(secret,"***");
        message=message.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        return message.isEmpty()?"":"："+clip(message,200);
    }
    /** A stable statement she made about herself, quoted verbatim from this reply ("我最爱吃小鱼干"). */
    static boolean validSelf(String reply,String quote) {
        return quote!=null && quote.length()>=4 && quote.length()<=80 && reply.contains(quote) && quote.contains("我")
            && !quote.contains("你") && !sensitive(quote) && !quote.matches("(?s).*[?？\\n].*");
    }
    static final String STAGE_NEW="你们刚认识不久：友好但有分寸，多了解对方，少用只有老朋友才会说的玩笑和亲昵称呼。",
        STAGE_FAMILIAR="你们已经熟悉了：可以自然地开玩笑、接上聊过的事，但不要假装认识很久。",
        STAGE_OLD="你们是老朋友了：说话可以更随意，偶尔提起真实记录里的共同经历；熟悉不等于越界，依然尊重对方的边界。";
    /** How well you know each other, from the first meeting and the days you actually talked; the persona itself never changes. */
    static String relationship(long since,long now,int chatDays,int userTurns){return relationship(since,now,chatDays,userTurns,0);}
    /** With a growth chapter (1–5) the stage follows it, so her familiarity matches the chapter the user sees: 1 new, 2–3 familiar, 4–5 old friends. */
    static String relationship(long since,long now,int chatDays,int userTurns,int chapter) {
        if(since<=0||now<since)return "";
        long days=(now-since)/86_400_000L+1;
        String stage=chapter>0?(chapter==1?STAGE_NEW:chapter<=3?STAGE_FAMILIAR:STAGE_OLD)
            :chatDays<3||userTurns<15?STAGE_NEW:chatDays<15?STAGE_FAMILIAR:STAGE_OLD;
        return "你们相识第"+days+"天，一起聊过"+chatDays+"天、"+userTurns+"轮。"+stage;
    }
    /**
     * Reminders against drift, from her last few replies: one opener over and over, the same catchphrase,
     * stage directions in brackets, or chat replies growing long. Cheap to compute; no extra model call.
     */
    static List<String> styleNotes(List<String> replies) {return styleNotes(replies,Collections.<String>emptyList());}
    /**
     * As above, but her style's signature expressions ("哼", "才不是") are part of who she is: they may recur,
     * and only draw a reminder when every one of the recent replies leans on them.
     */
    static List<String> styleNotes(List<String> replies,Collection<String> signature) {
        List<String> notes=new ArrayList<>();int n=replies.size();if(n<3)return notes;
        Map<String,Integer> openers=new HashMap<>();int brackets=0,length=0,emoji=0;
        for(String r:replies){String t=r.trim();if(t.length()>=2)openers.merge(t.substring(0,2),1,Integer::sum);if(t.matches("(?s).*[（(][^）)]{1,30}[）)].*"))brackets++;length+=t.length();emoji+=t.codePoints().filter(c->c>=0x1F300).count()>0?1:0;}
        for(Map.Entry<String,Integer> e:openers.entrySet())if(e.getValue()>=(signed(e.getKey(),signature)?n:3))notes.add("最近几句都以「"+e.getKey()+"」开头，这次换个开头。");
        String phrase=null;for(Map.Entry<String,Integer> e:phrases(replies).entrySet())if(e.getValue()>=(signed(e.getKey(),signature)?n:3)&&(phrase==null||e.getKey().length()>phrase.length()))phrase=e.getKey();
        if(phrase!=null)notes.add("「"+phrase+"」最近用得多了，这次别用。");
        if(brackets>=3)notes.add("最近括号里的动作描写多了，这次不用或只用一处。");
        if(length/n>140)notes.add("最近回复偏长，日常闲聊一到三句就好。");
        if(emoji>=3)notes.add("表情符号用得多了，这次少用。");
        return notes;
    }
    /** A 3–6 character phrase (not mere punctuation) that appears in at least three of the replies. */
    static String catchphrase(List<String> replies) {
        String best=null;for(Map.Entry<String,Integer> e:phrases(replies).entrySet())if(e.getValue()>=3&&(best==null||e.getKey().length()>best.length()))best=e.getKey();
        return best;
    }
    /** Each 3–6 character phrase and the number of replies it appears in. */
    private static Map<String,Integer> phrases(List<String> replies) {
        Map<String,Integer> seen=new HashMap<>();
        for(String r:replies){Set<String> once=new HashSet<>();for(int len=6;len>=3;len--)for(int i=0;i+len<=r.length();i++){String p=r.substring(i,i+len);if(p.matches("[\\p{L}\\p{N}…]+")&&p.matches(".*\\p{L}.*"))once.add(p);}for(String p:once)seen.merge(p,1,Integer::sum);}
        return seen;
    }
    /** Whether a repeated opener or phrase is one of her signature expressions, or part of one. */
    private static boolean signed(String text,Collection<String> signature) {
        for(String mark:signature){String m=mark.trim();if(!m.isEmpty()&&(text.startsWith(m)||m.contains(text)||m.length()>=2&&text.contains(m)))return true;}
        return false;
    }
    /**
     * Older replies in the prompt keep their first two sentences, so her own past wording does not snowball;
     * the second is kept because that is often where her character shows ("谁偷吃了。……分你一半。").
     */
    static String compressReply(String reply) {
        String t=reply.trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("[。！？!?…\\n]+").matcher(t);
        int end=t.length();for(int found=0;found<2&&m.find();found++)end=m.end();
        String kept=t.substring(0,end).trim();
        return kept.length()<=COMPRESSED_CHARS?kept:kept.substring(0,COMPRESSED_CHARS);
    }
    static final int COMPRESSED_CHARS=90,COMPRESSED_TURNS=4;
    /**
     * Whether history position i (of a window of size turns) is sent compressed: only the window's first
     * four, so the boundary moves with the window, four turns at a time, and the cached prefix holds.
     */
    static boolean compressed(int i,int size){return i<Math.min(COMPRESSED_TURNS,size-3);}
    /**
     * First turn index to send, given turns.length() and the lifetime turn count. The window holds 8–11
     * turns and moves four at a time, so consecutive requests share a prefix the provider can cache.
     */
    static int windowStart(int stored,int total) {
        total=Math.max(total,stored);int startAbsolute=total<=8?0:((total-8)/4)*4;
        return Math.max(0,startAbsolute-(total-stored));
    }
    static String clip(String text,int limit) { return text.length()<=limit ? text : text.substring(0,limit)+"…"; }
    /** Extract only the JSON reply string while it is being streamed, never metadata/reasoning. */
    static String partialReply(String json) {
        int key=json.indexOf("\"reply\""); if(key<0)return "";
        int p=json.indexOf(':',key+7); if(p<0)return "";
        p++;while(p<json.length()&&Character.isWhitespace(json.charAt(p)))p++;
        if(p>=json.length()||json.charAt(p++)!='"')return "";
        StringBuilder out=new StringBuilder();
        for(;p<json.length();p++) {
            char c=json.charAt(p);if(c=='"')break;
            if(c=='\\') {
                if(++p>=json.length())break; c=json.charAt(p);
                if(c=='u') { if(p+4>=json.length())break;try{out.append((char)Integer.parseInt(json.substring(p+1,p+5),16));p+=4;}catch(Exception e){break;} }
                else if(c=='n')out.append('\n');else if(c=='t')out.append('\t');else if(c=='r')out.append('\r');
                else if(c=='"'||c=='\\'||c=='/')out.append(c);
            } else out.append(c);
        }
        if(out.length()>0&&Character.isHighSurrogate(out.charAt(out.length()-1)))out.setLength(out.length()-1);
        return out.toString();
    }
}
