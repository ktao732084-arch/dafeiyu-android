package com.xiaoxiao.pet;
import org.json.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
public final class FeedbackTest {
 static int checks;static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static Map<String,String> form(String s)throws Exception{Map<String,String> out=new LinkedHashMap<>();for(String pair:s.split("&")){String[] kv=pair.split("=",2);out.put(URLDecoder.decode(kv[0],"UTF-8"),URLDecoder.decode(kv.length>1?kv[1]:"","UTF-8"));}return out;}
 public static void main(String[] args)throws Exception{
  // Only a 兔小巢 product page counts as a board.
  String good="https://support.qq.com/product/123456";
  check(Feedback.board(new JSONObject().put("feedback_url",good)).equals(good),"product page accepted");
  for(String bad:new String[]{"http://support.qq.com/product/123456","https://support.qq.com.evil.com/product/1234","https://evil.com/product/123456","https://support.qq.com/product/abc","https://support.qq.com/product/123456?x=1","https://u@support.qq.com/product/123456","https://support.qq.com:8443/product/123456","https://support.qq.com/product/123456#a",""})
   check(Feedback.board(new JSONObject().put("feedback_url",bad)).isEmpty(),"rejects "+bad);
  check(Feedback.board(new JSONObject()).isEmpty()&&Feedback.board(null).isEmpty(),"no board configured");
  JSONObject channel=new JSONObject(new String(Files.readAllBytes(Paths.get(args.length>0?args[0]:".","assets/update-channel.json")),StandardCharsets.UTF_8));
  check(channel.has("feedback_url")&&(channel.getString("feedback_url").isEmpty()||!Feedback.board(channel).isEmpty()),"shipped feedback address is empty or a valid board");
  check(channel.getString("manifest_url").startsWith("https://"),"update address untouched");
  // The device line: versions and phone only.
  URI page=new URI(Feedback.page(good,"0.4.9.5",19,"14（API 34）","Xiaomi 23127PN0CC"));
  check(page.getHost().equals("support.qq.com")&&page.getPath().equals("/product/123456"),"same board");
  Map<String,String> q=form(page.getRawQuery());
  check(q.keySet().equals(new LinkedHashSet<>(Arrays.asList("clientVersion","os","osVersion","clientInfo"))),"only device fields in the page address: "+q.keySet());
  check(q.get("clientVersion").equals("0.4.9.5 (19)")&&q.get("osVersion").equals("14（API 34）")&&q.get("clientInfo").equals("Xiaomi 23127PN0CC"),"values survive encoding");
  check(form(new URI(Feedback.page(good,"0.4.9.5",0,"","a&b=c")).getRawQuery()).get("clientInfo").equals("a&b=c"),"device text cannot add parameters");
  // Signing in this install.
  String id="0f8fad5b-d9cb-469f-a165-70867728950e";Map<String,String> login=form(Feedback.login(id,Feedback.nickname(id)));
  check(login.keySet().equals(new LinkedHashSet<>(Arrays.asList("openid","nickname","avatar"))),"login fields");
  check(login.get("openid").equals(id)&&login.get("nickname").equals("鱼友0f8f")&&login.get("nickname").length()<=8,"random id and short nickname");
  check(login.get("avatar").startsWith("https://")&&login.get("avatar").endsWith(".png"),"HTTPS PNG avatar");
  check(Feedback.nickname("").equals("鱼友0000")&&Feedback.nickname(null).length()<=8,"nickname without an id");
  // Navigation stays on Tencent's HTTPS pages.
  check(Feedback.inside(good)&&Feedback.inside("https://txc.qq.com/x")&&Feedback.inside("https://p.qpic.cn/a.png"),"board pages stay inside");
  check(!Feedback.inside("http://support.qq.com/product/1")&&!Feedback.inside("https://evil.com/?support.qq.com")&&!Feedback.inside("https://qq.com.evil.com/")&&!Feedback.inside("weixin://dl")&&!Feedback.inside("javascript:alert(1)"),"others leave the page");
  System.out.println("PASS: "+checks+" feedback checks");
 }
}
