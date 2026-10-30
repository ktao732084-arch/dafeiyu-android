package com.xiaoxiao.pet;

import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Self-hosted feedback. Only a per-install random identity and the visible device line leave the app. */
final class Feedback {
    static final String PATH="/dafeiyu-feedback/";
    static String board(JSONObject channel){
        String raw=channel==null?"":channel.optString("feedback_url","").trim();
        try{URI u=new URI(raw);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getRawUserInfo()==null&&u.getRawQuery()==null&&u.getRawFragment()==null&&(u.getPort()==-1||u.getPort()==443)&&PATH.equals(u.getRawPath())?raw:"";}catch(Exception e){return "";}
    }
    static Map<String,String> headers(String id,String version,long code,String android,String device){
        Map<String,String> result=new LinkedHashMap<>();
        if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid feedback identity");
        try{JSONObject meta=new JSONObject().put("app_version",(version==null?"":version)+(code>0?" ("+code+")":"")).put("android",android==null?"":android).put("device",device==null?"":device);
            result.put("Authorization","Bearer "+id);result.put("X-Dafeiyu-Device",Base64.getEncoder().encodeToString(meta.toString().getBytes(StandardCharsets.UTF_8)));
        }catch(JSONException e){throw new IllegalArgumentException(e);}return result;
    }
    static boolean inside(String board,String url){
        try{if(board(new JSONObject().put("feedback_url",board)).isEmpty())return false;URI base=new URI(board),u=new URI(url);int bp=base.getPort()==-1?443:base.getPort(),up=u.getPort()==-1?443:u.getPort();
            String path=u.getRawPath();return "https".equals(u.getScheme())&&u.getRawUserInfo()==null&&base.getHost().equalsIgnoreCase(u.getHost())&&bp==up&&path!=null&&path.startsWith(PATH)&&path.equals(u.normalize().getRawPath())&&!path.contains("%")&&!path.contains("\\");
        }catch(Exception e){return false;}
    }
}
