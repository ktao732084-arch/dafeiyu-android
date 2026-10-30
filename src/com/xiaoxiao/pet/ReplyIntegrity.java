package com.xiaoxiao.pet;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import org.json.*;

/** Reject damaged text before it becomes a displayed reply or a memory source. */
final class ReplyIntegrity {
    static final class Corrupt extends IOException {
        Corrupt(){super("模型回复含损坏字符，重试后仍未恢复。消息还在，请重新发送。");}
    }
    interface Generate {String run(AgentTransport.Progress progress)throws Exception;}
    static CharsetDecoder decoder(){return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);}
    static String decode(byte[] bytes)throws IOException {try{return decoder().decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){throw new Corrupt();}}
    static boolean damaged(String text,boolean literalReplacement){
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(c=='\ufffd'&&!literalReplacement)return true;
            if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))return true;}
            else if(Character.isLowSurrogate(c))return true;
        }
        return false;
    }
    static void validate(Object value,boolean literalReplacement)throws Exception {
        if(value instanceof String){if(damaged((String)value,literalReplacement))throw new Corrupt();}
        else if(value instanceof JSONObject){JSONObject o=(JSONObject)value;java.util.Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();validate(k,literalReplacement);validate(o.get(k),literalReplacement);}}
        else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)validate(a.get(i),literalReplacement);}
    }
    static AgentReply generate(Generate generate,AgentTransport.Token token,AgentTransport.Progress progress,boolean literalReplacement)throws Exception {
        for(int attempt=0;;attempt++){
            token.check();
            try{
                String raw=generate.run(partial->{if(!damaged(partial,literalReplacement))progress.accept(partial);});token.check();
                // Validate both literal and JSON-escaped damage, including side fields used as memory.
                validate(raw,literalReplacement);try{validate(new JSONObject(raw),literalReplacement);}catch(JSONException plainText){}
                AgentReply reply=AgentReply.parse(raw);validate(reply.reply,literalReplacement);validate(reply.aside,literalReplacement);validate(reply.moodCause,literalReplacement);validate(reply.memories,literalReplacement);validate(reply.self,literalReplacement);
                return reply;
            }catch(Corrupt bad){token.check();if(attempt>=1)throw bad;}
        }
    }
    static String history(String reply){return damaged(reply,false)?"（此条旧回复含损坏字符，不可引用；以用户原话为准）":reply;}
    /** A context-only copy: do not rewrite the user's stored historical records. */
    static Object context(Object value)throws JSONException {
        if(value instanceof String)return history((String)value);
        if(value instanceof JSONArray){JSONArray out=new JSONArray(),a=(JSONArray)value;for(int i=0;i<a.length();i++)out.put(context(a.get(i)));return out;}
        if(value instanceof JSONObject){JSONObject out=new JSONObject(),o=(JSONObject)value;java.util.Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();out.put(k,context(o.get(k)));}return out;}
        return value;
    }
}
