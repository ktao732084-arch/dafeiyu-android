package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class FeedbackTest {
 static int checks;static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 /** Answers with a fixed status and body, and keeps what was posted. */
 static final class Fake extends HttpURLConnection {
  final int status;final String answer;final ByteArrayOutputStream posted=new ByteArrayOutputStream();final Map<String,String> headers=new HashMap<>();boolean disconnected;
  Fake(int status,String answer)throws Exception{super(new URL("https://relay.example/dafeiyu-feedback/submit"));this.status=status;this.answer=answer;}
  public void setRequestProperty(String k,String v){headers.put(k,v);}
  public OutputStream getOutputStream(){return posted;}
  public int getResponseCode(){return status;}
  public InputStream getInputStream()throws IOException{if(status>=400)throw new IOException("HTTP "+status);return new ByteArrayInputStream(answer.getBytes(StandardCharsets.UTF_8));}
  public InputStream getErrorStream(){return status>=400?new ByteArrayInputStream(answer.getBytes(StandardCharsets.UTF_8)):null;}
  public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
 }
 static String failure(int status)throws Exception{Fake f=new Fake(status,"{\"ok\":false}");Feedback.connections=u->f;try{Feedback.send("https://relay.example/x",new JSONObject());return null;}catch(IOException e){check(f.disconnected,"connection closed after "+status);return e.getMessage();}}
 public static void main(String[] args)throws Exception{
  // The relay address: HTTPS only, no credentials or fragment, empty when absent.
  check(Feedback.endpoint(new JSONObject().put("feedback_url","https://81.70.79.133/dafeiyu-feedback/submit")).equals("https://81.70.79.133/dafeiyu-feedback/submit"),"bundled https address accepted");
  for(String bad:new String[]{"http://81.70.79.133/x","https://user:pw@host/x","https://host/x#f","ftp://host/x","not a url",""})check(Feedback.endpoint(new JSONObject().put("feedback_url",bad)).isEmpty(),"rejects "+bad);
  check(Feedback.endpoint(new JSONObject()).isEmpty()&&Feedback.endpoint(null).isEmpty(),"no address, no sending");
  JSONObject channel=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args.length>0?args[0]:".","assets/update-channel.json")),StandardCharsets.UTF_8));
  check(!Feedback.endpoint(channel).isEmpty()&&channel.getString("manifest_url").startsWith("https://"),"the shipped channel has both addresses");
  // What can be sent.
  check(Feedback.problem("  \n",null)!=null&&Feedback.problem("打不开","")==null,"needs some text");
  String longest=String.join("",Collections.nCopies(Feedback.MAX_TEXT,"字"));check(Feedback.problem(longest,null)==null&&Feedback.problem(longest+"多",null)!=null,"text limit counts characters");
  check(Feedback.problem("emoji 😀"+String.join("",Collections.nCopies(Feedback.MAX_TEXT-7,"a")),null)==null,"an emoji counts once");
  check(Feedback.problem("x",String.join("",Collections.nCopies(Feedback.MAX_CONTACT+1,"c")))!=null,"contact limit");
  JSONObject p=Feedback.payload("id-1","  聊天页打不开 ","  qq 1 ","0.4.9.5",19,"14（API 34）","Xiaomi 2312");
  check(new TreeSet<>(Arrays.asList(JSONObject.getNames(p))).equals(new TreeSet<>(Arrays.asList("id","text","contact","app_version","version_code","android","device"))),"only these fields leave the phone");
  check(p.getString("text").equals("聊天页打不开")&&p.getString("contact").equals("qq 1")&&p.getLong("version_code")==19,"trimmed values");
  // Five an hour from this phone.
  List<Long> sent=new ArrayList<>();long now=10*Feedback.HOUR;for(int i=0;i<Feedback.PER_HOUR;i++){sent=Feedback.allow(sent,now+i);check(sent!=null,"send "+i);}
  check(Feedback.allow(sent,now+10)==null,"sixth within the hour waits");check(Feedback.allow(sent,now+Feedback.HOUR)!=null,"allowed an hour after the first");
  check(Feedback.allow(Arrays.asList(now+5000L,null),now).size()==1,"future or missing times are dropped");
  // Sending.
  Fake ok=new Fake(200,"{\"ok\":true,\"receipt\":\"#12\"}");Feedback.connections=u->ok;
  check(Feedback.send("https://relay.example/x",p).equals("#12"),"receipt returned");
  check(new JSONObject(ok.posted.toString("UTF-8")).toString().equals(p.toString())&&"POST".equals(ok.getRequestMethod())&&ok.headers.get("Content-Type").startsWith("application/json")&&!ok.getInstanceFollowRedirects()&&ok.disconnected,"posts exactly the payload as JSON, no redirects");
  Fake plain=new Fake(200,"ok");Feedback.connections=u->plain;check(Feedback.send("https://relay.example/x",p).isEmpty(),"odd 200 body still counts as sent");
  check(failure(429).contains("频繁"),"rate limited message");check(failure(413).contains("改短"),"too large message");check(failure(502).contains("HTTP 502"),"server trouble message");check(failure(302).contains("HTTP 302"),"redirects are not followed");
  Feedback.connections=u->{throw new UnknownHostException("relay.example");};try{Feedback.send("https://relay.example/x",p);check(false,"offline fails");}catch(IOException e){check(e.getMessage().contains("反馈地址无效")||e.getMessage().contains("网络"),"offline message: "+e.getMessage());}
  System.out.println("PASS: "+checks+" feedback checks");
 }
}
