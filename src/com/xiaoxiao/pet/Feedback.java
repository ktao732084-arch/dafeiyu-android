package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * In-app problem reports. The app never holds a GitHub token or mail password: it posts the user's words, an optional
 * contact and the app/Android/phone versions to the relay on the update server (deploy/feedback), which files them as an
 * issue in a private repository. No chat, memory, key or model address is attached. Pure Java apart from the caller.
 */
final class Feedback {
    interface Connections {HttpURLConnection open(URL url)throws Exception;}
    static Connections connections=url->(HttpURLConnection)url.openConnection();
    static final int MAX_TEXT=2000,MAX_CONTACT=100,PER_HOUR=5;
    static final long HOUR=3_600_000L;

    /** The relay address from update-channel.json: HTTPS only, no credentials; empty when this build has none. */
    static String endpoint(JSONObject channel){
        String raw=channel==null?"":channel.optString("feedback_url","").trim();if(raw.isEmpty())return "";
        try{URI u=new URI(raw);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null&&u.getRawFragment()==null?raw:"";}catch(Exception e){return "";}
    }
    /** Why this report cannot be sent yet, or null. */
    static String problem(String text,String contact){
        String t=text==null?"":text.trim();
        if(t.isEmpty())return "先写下遇到的问题";
        if(t.codePointCount(0,t.length())>MAX_TEXT)return "最多 "+MAX_TEXT+" 字，可以分两次发";
        if(contact!=null&&contact.trim().codePointCount(0,contact.trim().length())>MAX_CONTACT)return "联系方式太长了";
        return null;
    }
    /** What goes over the wire, and nothing else; id lets the relay drop a retried duplicate. */
    static JSONObject payload(String id,String text,String contact,String version,long code,String android,String device)throws JSONException{
        return new JSONObject().put("id",id).put("text",text.trim()).put("contact",contact==null?"":contact.trim())
            .put("app_version",version==null?"":version).put("version_code",code).put("android",android==null?"":android).put("device",device==null?"":device);
    }
    /** Sends with the last hour's send times (wall-clock ms); null when another report would be too many. */
    static List<Long> allow(List<Long> sent,long now){
        List<Long> recent=new ArrayList<>();for(Long t:sent)if(t!=null&&t<=now&&now-t<HOUR)recent.add(t);
        if(recent.size()>=PER_HOUR)return null;recent.add(now);return recent;
    }
    /** Posts the report; returns the relay's receipt number (may be empty). Throws with a message fit for the user. */
    static String send(String endpoint,JSONObject report)throws IOException{
        HttpURLConnection c;
        try{c=connections.open(new URL(endpoint));}catch(Exception e){throw new IOException("反馈地址无效");}
        try{
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setDoOutput(true);c.setRequestMethod("POST");
            byte[] body=report.toString().getBytes(StandardCharsets.UTF_8);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            int code=c.getResponseCode();String answer=read(code<400?c.getInputStream():c.getErrorStream());
            if(code==200){try{return new JSONObject(answer).optString("receipt","");}catch(JSONException e){return "";}}
            throw new IOException(code==429?"发得有点频繁，过一会儿再试":code==400||code==413?"内容格式不对，改短一点再试":"反馈服务暂时不可用（HTTP "+code+"），稍后再试");
        }catch(SocketTimeoutException|UnknownHostException|ConnectException e){throw new IOException("网络连不上反馈服务，稍后再试");}
        finally{c.disconnect();}
    }
    static String read(InputStream in)throws IOException{
        if(in==null)return "";
        try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=s.read(b))!=-1&&out.size()<8192)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    }
}
