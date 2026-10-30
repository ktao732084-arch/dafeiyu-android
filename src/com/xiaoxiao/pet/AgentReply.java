package com.xiaoxiao.pet;
import org.json.*;import java.io.IOException;

/** The only model-produced fields accepted by the runtime. No executable tools. */
final class AgentReply {
 String reply,emotion="calm",moodCause="",aside="";int moodLevel;Boolean moodChanged;int nextCheckMinutes=Initiative.DEFAULT_MINUTES;InitiativePlan plan=new InitiativePlan();JSONArray memories=new JSONArray(),self=new JSONArray();
 /** The reply string of a broken envelope, only when it comes first and is closed by a quote and then a comma or the brace. */
 static String complete(String json){
  int p=json.indexOf('{')+1;while(p<json.length()&&Character.isWhitespace(json.charAt(p)))p++;
  if(!json.startsWith("\"reply\"",p))return null;p+=7;while(p<json.length()&&Character.isWhitespace(json.charAt(p)))p++;
  if(p>=json.length()||json.charAt(p++)!=':')return null;while(p<json.length()&&Character.isWhitespace(json.charAt(p)))p++;
  if(p>=json.length()||json.charAt(p)!='"')return null;int start=p++;
  for(;p<json.length();p++){char c=json.charAt(p);if(c=='\\'){p++;continue;}if(c=='"')break;}
  if(p>=json.length())return null;int end=p++;while(p<json.length()&&Character.isWhitespace(json.charAt(p)))p++;
  if(p>=json.length()||json.charAt(p)!=','&&json.charAt(p)!='}')return null;
  // Raw line breaks and stray escapes such as \[ are what break an otherwise finished reply string.
  String body=json.substring(start,end+1).replace("\n","\\n").replace("\r","\\r").replaceAll("\\\\([^\"\\\\/bfnrtu])","$1");
  try{return new JSONObject("{\"r\":"+body+"}").getString("r");}catch(JSONException e){return null;}
 }
 static AgentReply parse(String raw)throws Exception{
  AgentReply result=new AgentReply();String text=AgentPolicy.replyText(raw.replaceAll("(?s)<think>.*?</think>","").trim());
  if(text.startsWith("```")){int nl=text.indexOf('\n'),end=text.lastIndexOf("```");if(nl>=0&&end>nl)text=text.substring(nl+1,end).trim();}
  String json=AgentPolicy.envelope(text);
  if(json!=null){
   JSONObject o;try{o=new JSONObject(json);}catch(JSONException e){
    // A slip in a later field ("self":["…"}) should not cost a finished reply: keep it like a plain-text answer.
    String kept=complete(json);if(kept==null)throw new IOException("模型返回的回复格式不完整，请重试");
    result.reply=AgentPolicy.replyText(kept);result.moodChanged=Boolean.FALSE;
    if(result.reply.trim().isEmpty())throw new IOException("模型没有返回可显示的回复");result.reply=AgentPolicy.clip(result.reply.trim(),5000);return result;}
   Object reply=o.opt("reply");if(!(reply instanceof String))throw new IOException("模型回复格式不正确，请重试");
   if(o.has("mood_changed"))result.moodChanged=o.opt("mood_changed") instanceof Boolean?(Boolean)o.opt("mood_changed"):Boolean.FALSE;
   else if(!o.has("emotion"))result.moodChanged=Boolean.FALSE;
   result.reply=(String)reply;result.plan=InitiativePlan.parse(o);result.nextCheckMinutes=Initiative.minutes(o.opt("next_check_minutes"));result.emotion=AgentPolicy.emotion(o.optString("emotion"));result.moodLevel=AgentPolicy.moodLevel(o.opt("mood_level"));result.moodCause=AgentPolicy.moodCause(o.optString("mood_cause",null));result.aside=o.opt("aside") instanceof String?(String)o.opt("aside"):"";JSONArray m=o.optJSONArray("memories");if(m!=null)result.memories=m;JSONArray self=o.optJSONArray("self");if(self!=null)result.self=self;
  }else {result.reply=text;result.moodChanged=Boolean.FALSE;}
  if(result.reply!=null)result.reply=AgentPolicy.replyText(result.reply);
  if(result.reply==null||result.reply.trim().isEmpty())throw new IOException("模型没有返回可显示的回复");
  result.reply=AgentPolicy.clip(result.reply.trim(),5000);result.aside=AgentPolicy.aside(result.aside,result.reply);return result;
 }
}
