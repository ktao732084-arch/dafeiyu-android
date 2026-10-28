package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/** Pure, deterministic decisions. The model can suggest an expression, not run code. */
final class AgentPolicy {
    static final Set<String> EMOTIONS = new HashSet<>(Arrays.asList("calm","happy","shy","curious","concerned","playful","sleepy"));
    static String emotion(String value) { return EMOTIONS.contains(value) ? value : "calm"; }
    static boolean quiet(int hour) { return hour >= 23 || hour < 8; }
    static boolean mayInitiate(long now, long lastUser, long lastAttempt, int attempts, int hour,
                               boolean enabled, boolean screen, boolean visible, boolean focused, boolean busy) {
        return enabled && screen && visible && !focused && !busy && !quiet(hour) && attempts < 3
            && lastUser > 0 && now >= lastUser && now-lastUser >= 30*60_000L
            && now >= lastAttempt && now-lastAttempt >= 90*60_000L;
    }
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
    static String streamPreview(String raw) {
        String text=raw.replaceAll("(?s)<think>.*?</think>","").trim();
        if(text.startsWith("<think>"))return "";
        if(text.indexOf('{')>=0||text.startsWith("`"))return partialReply(text);
        return text;
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
    /** How well you know each other, from the first meeting and the days you actually talked; the persona itself never changes. */
    static String relationship(long since,long now,int chatDays,int userTurns) {
        if(since<=0||now<since)return "";
        long days=(now-since)/86_400_000L+1;
        String stage=chatDays<3||userTurns<15?"你们刚认识不久：友好但有分寸，多了解对方，少用只有老朋友才会说的玩笑和亲昵称呼。"
            :chatDays<15?"你们已经熟悉了：可以自然地开玩笑、接上聊过的事，但不要假装认识很久。"
            :"你们是老朋友了：说话可以更随意，偶尔提起真实记录里的共同经历；熟悉不等于越界，依然尊重对方的边界。";
        return "你们相识第"+days+"天，一起聊过"+chatDays+"天、"+userTurns+"轮。"+stage;
    }
    /**
     * Reminders against drift, from her last few replies: one opener over and over, the same catchphrase,
     * stage directions in brackets, or chat replies growing long. Cheap to compute; no extra model call.
     */
    static List<String> styleNotes(List<String> replies) {
        List<String> notes=new ArrayList<>();int n=replies.size();if(n<3)return notes;
        Map<String,Integer> openers=new HashMap<>();int brackets=0,length=0,emoji=0;
        for(String r:replies){String t=r.trim();if(t.length()>=2)openers.merge(t.substring(0,2),1,Integer::sum);if(t.matches("(?s).*[（(][^）)]{1,30}[）)].*"))brackets++;length+=t.length();emoji+=t.codePoints().filter(c->c>=0x1F300).count()>0?1:0;}
        for(Map.Entry<String,Integer> e:openers.entrySet())if(e.getValue()>=3)notes.add("最近几句都以「"+e.getKey()+"」开头，这次换个开头。");
        String phrase=catchphrase(replies);if(phrase!=null)notes.add("「"+phrase+"」最近用得多了，这次别用。");
        if(brackets>=3)notes.add("最近括号里的动作描写多了，这次不用或只用一处。");
        if(length/n>140)notes.add("最近回复偏长，日常闲聊一到三句就好。");
        if(emoji>=3)notes.add("表情符号用得多了，这次少用。");
        return notes;
    }
    /** A 3–6 character phrase (not mere punctuation) that appears in at least three of the replies. */
    static String catchphrase(List<String> replies) {
        Map<String,Integer> seen=new HashMap<>();
        for(String r:replies){Set<String> once=new HashSet<>();for(int len=6;len>=3;len--)for(int i=0;i+len<=r.length();i++){String p=r.substring(i,i+len);if(p.matches("[\\p{L}\\p{N}…]+")&&p.matches(".*\\p{L}.*"))once.add(p);}for(String p:once)seen.merge(p,1,Integer::sum);}
        String best=null;for(Map.Entry<String,Integer> e:seen.entrySet())if(e.getValue()>=3&&(best==null||e.getKey().length()>best.length()))best=e.getKey();
        return best;
    }
    /** Older replies in the prompt keep their first sentence only, so her own past wording does not snowball. */
    static String compressReply(String reply) {
        String t=reply.trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("[。！？!?…\\n]").matcher(t);
        int end=m.find()?m.end():t.length();String first=t.substring(0,end).trim();
        return first.length()<=60?first:first.substring(0,60);
    }
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
