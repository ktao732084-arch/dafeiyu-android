package com.xiaoxiao.pet;

import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * In-app problem reports through 兔小巢 (Tencent's free feedback board): the app opens the product page in a WebView,
 * so there is no server, token or password of ours anywhere. Each install posts under a random id and nickname, so the
 * user can come back and read the developer's replies; the board also receives the app/Android/phone versions.
 * No chat, memory, key or model address is sent. Pure Java apart from the page.
 */
final class Feedback {
    static final String HOST="support.qq.com";
    /** Shown next to her posts; 兔小巢 needs an HTTPS PNG/JPG for the logged-in state. */
    static final String AVATAR="https://cdn.jsdelivr.net/gh/ktao732084-arch/dafeiyu-android@main/assets/main/portrait.png";

    /** The board from update-channel.json: https://support.qq.com/product/<digits>, else empty (no board in this build). */
    static String board(JSONObject channel){
        String raw=channel==null?"":channel.optString("feedback_url","").trim();
        try{URI u=new URI(raw);return "https".equals(u.getScheme())&&HOST.equals(u.getHost())&&u.getPort()==-1&&u.getRawUserInfo()==null&&u.getRawQuery()==null&&u.getRawFragment()==null&&u.getPath()!=null&&u.getPath().matches("/product/\\d{3,12}")?raw:"";}catch(Exception e){return "";}
    }
    /** The page with the device line 兔小巢 shows beside each post. */
    static String page(String board,String version,long code,String android,String device){
        return board+"?clientVersion="+enc(version+(code>0?" ("+code+")":""))+"&os=Android&osVersion="+enc(android)+"&clientInfo="+enc(device);
    }
    /** Form body that logs this install in: a random openid, a short nickname (8 characters at most) and her portrait. */
    static String login(String openid,String nickname){return "openid="+enc(openid)+"&nickname="+enc(nickname)+"&avatar="+enc(AVATAR);}
    static String nickname(String openid){String hex=openid==null?"":openid.replaceAll("[^0-9a-fA-F]","");return "鱼友"+(hex.length()>=4?hex.substring(0,4):"0000");}
    /** Links that stay inside the page; everything else opens in the browser or is ignored. */
    static boolean inside(String url){try{URI u=new URI(url);return "https".equals(u.getScheme())&&u.getHost()!=null&&(u.getHost().equals(HOST)||u.getHost().endsWith(".qq.com")||u.getHost().endsWith(".gtimg.cn")||u.getHost().endsWith(".qpic.cn"));}catch(Exception e){return false;}}
    static String enc(String s){try{return URLEncoder.encode(s==null?"":s,StandardCharsets.UTF_8.name());}catch(Exception e){return "";}}
}
