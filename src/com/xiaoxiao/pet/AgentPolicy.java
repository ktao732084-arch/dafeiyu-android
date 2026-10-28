package com.xiaoxiao.pet;

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
            else for(int i=0;i<part.length()-1;i++) out.add(part.substring(i,i+2));
        }
        return out;
    }
    static double relevance(String query,String content) {
        Set<String> a=tokens(query), b=tokens(content); if(a.isEmpty()||b.isEmpty()) return 0;
        int common=0; for(String t:a)if(b.contains(t)) common++;
        return common / Math.sqrt((double)a.size()*b.size());
    }
    static boolean sensitive(String text) {
        return text.matches("(?is).*(密码|口令|身份证|银行卡|api.?key|secret|token\\s*[:：=]|sk-[a-z0-9]|\\b\\d{11,}\\b).*" );
    }
    static boolean validEvidence(String user,String quote) {
        return quote!=null && quote.length()>=3 && quote.length()<=240 && user.contains(quote) && !sensitive(quote)
            && !quote.matches("(?s).*[?？].*") && !user.matches("(?s).*(假如|假设|假装|角色扮演|举个例子|例如我).*" );
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
