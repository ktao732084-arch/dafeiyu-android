package com.xiaoxiao.pet;
import org.json.*;import java.io.IOException;

/** The only model-produced fields accepted by the runtime. No executable tools. */
final class AgentReply {
 String reply,emotion="calm";JSONArray memories=new JSONArray();
 static AgentReply parse(String raw)throws Exception{
  AgentReply result=new AgentReply();String text=raw.replaceAll("(?s)<think>.*?</think>","").trim();
  if(text.startsWith("```")){int nl=text.indexOf('\n'),end=text.lastIndexOf("```");if(nl>=0&&end>nl)text=text.substring(nl+1,end).trim();}
  if(text.startsWith("{")){
   JSONObject o;try{o=new JSONObject(text);}catch(JSONException e){throw new IOException("模型返回的回复格式不完整，请重试");}
   Object reply=o.opt("reply");if(!(reply instanceof String))throw new IOException("模型回复格式不正确，请重试");
   result.reply=(String)reply;result.emotion=AgentPolicy.emotion(o.optString("emotion"));JSONArray m=o.optJSONArray("memories");if(m!=null)result.memories=m;
  }else result.reply=text;
  if(result.reply==null||result.reply.trim().isEmpty())throw new IOException("模型没有返回可显示的回复");
  result.reply=AgentPolicy.clip(result.reply.trim(),5000);return result;
 }
}
