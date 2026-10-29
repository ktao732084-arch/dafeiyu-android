package com.xiaoxiao.pet;

import android.content.SharedPreferences;
import org.json.*;
import java.io.IOException;
import java.util.regex.*;

/** A direct user naming request updates the shared preference; model guesses never do. */
final class Nickname {
    static final String KEY="agent.nickname",MIGRATED="agent.nickname.migrated";
    static final String NAME="[\\p{L}\\p{N}_·.\\- ]{1,30}?";
    static final Pattern COMMAND=UnicodeRegex.compile("^(?:(?:从现在起|以后|今后|往后|请你|你可以|你|请|就|都|直接|还是)\\s*){0,5}(?:叫我|喊我|称呼我)\\s*("+NAME+")(?:吧|就好|就行|就可以了)?$",0);
    static final Pattern INTRO=UnicodeRegex.compile("^(?:我叫|我的名字(?:是|叫)|我的昵称(?:是|叫))\\s*("+NAME+")$",0);
    static String requested(String raw){
        if(raw==null)return null;String text=raw.trim().replaceFirst("^记住[:：]\\s*","");
        if(text.matches("(?s).*[?？\"'“”‘’「」].*")||text.matches("(?s).*[吗呢么][。！!]*$")||text.matches("(?s).*(如果|假如|假设|假装|比如|例如|举例|角色扮演|测试台词|转述|引用|他说|她说|你说|以前|之前|曾经|昨天|去年|不要|别再|别叫|不希望|不能叫|不叫|不准).*")||AgentPolicy.sensitive(text))return null;
        String command=null,intro=null;
        for(String clause:text.split("[，,。；;！!\\n]+")){
            String s=clause.trim();
            // Also accepts the reported unpunctuated "我叫k你以后就叫我kk吧".
            s=s.replaceFirst("^(?:我叫|我的名字(?:是|叫))[\\p{L}\\p{N}_·-]{1,30}?(?=你(?:以后|今后)|以后|今后)","");
            Matcher m=COMMAND.matcher(s);if(m.matches()){String name=valid(m.group(1));if(name!=null)command=name;continue;}
            m=INTRO.matcher(s);if(m.matches()){String name=valid(m.group(1));if(name!=null)intro=name;}
        }
        return command!=null?command:intro;
    }
    static String valid(String value){String name=value.trim();return name.isEmpty()||name.length()>30||name.matches("(?s)^(你|他|她|起床|吃饭|睡觉|回家|开会|去|来|做).*|.*(叫我|称呼我|以后|名字|什么|不是|改名|记得|记住|不用|不必|但是|因为|所以|可以).*" )?null:name;}
    static SharedPreferences.Editor put(SharedPreferences.Editor editor,String value,String origin){
        return editor.putString(KEY,value).putString("agent.nickname.origin",origin).putBoolean(MIGRATED,true);
    }
    static void manual(String value)throws IOException{
        if(value.length()>30||value.matches("(?s).*[\\p{Cntrl}].*"))throw new IOException("称呼最多30个字符，不能含换行");
        AgentRuntime.cancel();synchronized(Nickname.class){if(!put(Data.prefs().edit(),value.trim(),"manual").commit())throw new IOException("称呼保存失败，请重试");}
    }
    static void profile(String role,String name,String nick)throws IOException{
        AgentRuntime.cancel();synchronized(Nickname.class){if(!put(Data.prefs().edit().putString("profile."+role+".name",name),nick,"manual").commit())throw new IOException("保存失败，请重试");}
    }
    static synchronized void committed(String user){String name=requested(user);if(name!=null)put(Data.prefs().edit(),name,"chat").apply();}
    static String current(){
        SharedPreferences prefs=Data.prefs();
        if(!prefs.getBoolean(MIGRATED,false))try{
            String name=prefs.getString(KEY,"");
            if(name.isEmpty())name=fromMemories(AgentRuntime.store().snapshot(),System.currentTimeMillis());
            // Storage reads happen outside this lock; a manual edit during that read must win.
            synchronized(Nickname.class){if(!prefs.getBoolean(MIGRATED,false))put(prefs.edit(),name,name.isEmpty()?"none":prefs.getString(KEY,"").isEmpty()?"memory-migration":"manual").apply();}
        }catch(Exception unavailable){/* Retry migration when storage becomes available; never block chatting. */}
        return prefs.getString(KEY,"");
    }
    static String fromMemories(JSONObject state,long now){
        String result="";long latest=Long.MIN_VALUE;JSONArray rows=state.optJSONArray("memories");
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||!MemoryLedger.current(row,now)||row.optLong("at")<latest)continue;String candidate=requested(row.optString("text"));if(candidate!=null){result=candidate;latest=row.optLong("at");}}
        return result;
    }
}
