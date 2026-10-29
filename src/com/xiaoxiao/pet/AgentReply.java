package com.xiaoxiao.pet;
import org.json.*;import java.io.IOException;

/** The only model-produced fields accepted by the runtime. No executable tools. */
final class AgentReply {
 String reply,emotion="calm",moodCause="";int moodLevel;int nextCheckMinutes=Initiative.DEFAULT_MINUTES;InitiativePlan plan=new InitiativePlan();JSONArray memories=new JSONArray(),self=new JSONArray();
 static AgentReply parse(String raw)throws Exception{
  AgentReply result=new AgentReply();String text=AgentPolicy.replyText(raw.replaceAll("(?s)<think>.*?</think>","").trim());
  if(text.startsWith("```")){int nl=text.indexOf('\n'),end=text.lastIndexOf("```");if(nl>=0&&end>nl)text=text.substring(nl+1,end).trim();}
  String json=AgentPolicy.envelope(text);
  if(json!=null){
   JSONObject o;try{o=new JSONObject(json);}catch(JSONException e){throw new IOException("模型返回的回复格式不完整，请重试");}
   Object reply=o.opt("reply");if(!(reply instanceof String))throw new IOException("模型回复格式不正确，请重试");
   result.reply=(String)reply;result.plan=InitiativePlan.parse(o);result.nextCheckMinutes=Initiative.minutes(o.opt("next_check_minutes"));result.emotion=AgentPolicy.emotion(o.optString("emotion"));result.moodLevel=AgentPolicy.moodLevel(o.opt("mood_level"));result.moodCause=AgentPolicy.moodCause(o.optString("mood_cause",null));JSONArray m=o.optJSONArray("memories");if(m!=null)result.memories=m;JSONArray self=o.optJSONArray("self");if(self!=null)result.self=self;
  }else result.reply=text;
  if(result.reply!=null)result.reply=AgentPolicy.replyText(result.reply);
  if(result.reply==null||result.reply.trim().isEmpty())throw new IOException("模型没有返回可显示的回复");
  result.reply=AgentPolicy.clip(result.reply.trim(),5000);return result;
 }
}
