package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** Static release manifest, independent of chat, model credentials and memory. */
final class AppUpdate {
    static final long DAY=86400000L, MAX_APK=512L*1024*1024;
    final long code,size;final int minSdk;final String name,notes,sha,packageName;final URL apk;final String json;
    AppUpdate(String raw,String expectedPackage,URL channel)throws Exception {
        if(raw.length()>65536)throw new IOException("版本信息过大");JSONObject o=new JSONObject(raw);
        if(number(o,"schema")!=1)throw new IOException("不支持的更新格式");
        packageName=o.getString("package_name");if(!expectedPackage.equals(packageName))throw new IOException("更新包不属于此应用");
        code=number(o,"version_code");size=number(o,"size");long sdk=number(o,"min_sdk");
        if(code<1||code>Integer.MAX_VALUE||size<1||size>MAX_APK||sdk<26||sdk>1000)throw new IOException("更新参数无效");minSdk=(int)sdk;
        name=o.getString("version_name");notes=o.optString("release_notes","");sha=o.getString("sha256").toLowerCase(Locale.ROOT);
        if(name.isEmpty()||name.length()>40||notes.length()>8000||!sha.matches("[a-f0-9]{64}"))throw new IOException("更新信息无效");
        apk=https(o.getString("apk_url"));if(!sameOrigin(channel,apk))throw new IOException("安装包必须来自配置的更新站点");json=o.toString();
    }
    static long number(JSONObject o,String key)throws Exception {Object v=o.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new IOException("更新数字字段无效："+key);return ((Number)v).longValue();}
    static URL https(String value)throws Exception {
        URI uri=new URI(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isEmpty()||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535)throw new IOException("更新地址必须是有效的 HTTPS 地址");
        return uri.toURL();
    }
    static boolean sameOrigin(URL a,URL b){return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?443:a.getPort())==(b.getPort()<0?443:b.getPort());}
    static boolean due(long last,long now){return last==0||now<last||now-last>=DAY;}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static String digest(File file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}
    String filename(){return code+"-"+sha+".apk";}
    boolean newer(long installed,int sdk){return code>installed&&minSdk<=sdk;}
}
