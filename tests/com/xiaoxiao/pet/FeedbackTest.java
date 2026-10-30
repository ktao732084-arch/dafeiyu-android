package com.xiaoxiao.pet;
import org.json.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
public final class FeedbackTest {
 static int checks;static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args)throws Exception{
  String good="https://81.70.79.133/dafeiyu-feedback/";
  check(Feedback.board(new JSONObject().put("feedback_url",good)).equals(good),"self hosted board accepted");
  for(String bad:new String[]{"http://81.70.79.133/dafeiyu-feedback/","https://support.qq.com/product/123456","https://81.70.79.133/dafeiyu-feedback","https://81.70.79.133/dafeiyu-feedback/?x=1","https://u@81.70.79.133/dafeiyu-feedback/","https://81.70.79.133:8443/dafeiyu-feedback/","https://81.70.79.133/dafeiyu-feedback/#a",""})check(Feedback.board(new JSONObject().put("feedback_url",bad)).isEmpty(),"rejects invalid board "+bad);
  check(Feedback.board(new JSONObject()).isEmpty()&&Feedback.board(null).isEmpty(),"missing config");
  check(Feedback.board(new JSONObject().put("feedback_url","https://feedback.example.com/dafeiyu-feedback/")).length()>0,"HTTPS domain configurable");
  JSONObject channel=new JSONObject(new String(Files.readAllBytes(Paths.get(args.length>0?args[0]:".","assets/update-channel.json")),StandardCharsets.UTF_8));
  check(!Feedback.board(channel).isEmpty(),"packaged endpoint configured");check(channel.getString("manifest_url").startsWith("https://"),"updater preserved");
  String id="0f8fad5b-d9cb-469f-a165-70867728950e";Map<String,String> headers=Feedback.headers(id,"0.4.9.5",19,"15（API 35）","Xiaomi a&b=c");
  check(headers.size()==2&&headers.get("Authorization").equals("Bearer "+id),"only install identity and metadata headers");
  JSONObject meta=new JSONObject(new String(Base64.getDecoder().decode(headers.get("X-Dafeiyu-Device")),StandardCharsets.UTF_8));
  check(meta.length()==3&&meta.getString("app_version").equals("0.4.9.5 (19)")&&meta.getString("android").equals("15（API 35）")&&meta.getString("device").equals("Xiaomi a&b=c"),"only expected metadata, correct Unicode");
  check(new URI(good).getRawQuery()==null,"no credentials or device data in URL");
  boolean rejected=false;try{Feedback.headers("bad\r\nCookie: x",null,0,null,null);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid identity cannot inject headers");
  check(Feedback.inside(good,good)&&Feedback.inside(good,good+"api/reports")&&Feedback.inside(good,good+"style.css"),"own resources allowed");
  for(String bad:new String[]{"https://81.70.79.133/","https://81.70.79.133/dafeiyu-feedback-evil/","https://81.70.79.133/dafeiyu-feedback/../x","https://81.70.79.133/dafeiyu-feedback/%2e%2e/x","https://81.70.79.133/dafeiyu-feedback/%2Fx","https://81.70.79.133:8443/dafeiyu-feedback/","https://u@81.70.79.133/dafeiyu-feedback/","https://81.70.79.133.evil.com/dafeiyu-feedback/","https://support.qq.com/","http://81.70.79.133/dafeiyu-feedback/","javascript:alert(1)","file:///sdcard/a"})check(!Feedback.inside(good,bad),"untrusted navigation rejected "+bad);
  check(!Feedback.inside("",good),"no board never accepts a page");
  System.out.println("PASS: "+checks+" feedback checks");
 }
}
