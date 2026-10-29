package com.xiaoxiao.pet;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;

/** Bounded HTTPS download; the release host is the only allowed redirect target. */
final class UpdateTransport {
    interface Connections {HttpURLConnection open(URL url)throws Exception;}
    interface Progress {void bytes(long received,long total);}
    static Connections connections=url->(HttpURLConnection)url.openConnection();
    static final class Token {
        volatile boolean cancelled;volatile HttpURLConnection connection;
        void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        void check()throws IOException {if(cancelled)throw new IOException("已取消更新操作");}
    }
    static HttpURLConnection open(URL origin,Token token)throws Exception {
        URL current=AppUpdate.https(origin.toString());
        for(int i=0;i<4;i++){
            token.check();HttpURLConnection c=connections.open(current);token.connection=c;
            try{c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Cache-Control","no-cache");
                token.check();int code=c.getResponseCode();
                if(code==200)return c;
                if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("更新重定向无效");URL next=AppUpdate.https(new URL(current,location).toString());if(!AppUpdate.sameOrigin(origin,next))throw new IOException("更新站点不能跳转到其他域名");current=next;}
                else throw new IOException("更新服务返回 HTTP "+code);
            }catch(Exception e){c.disconnect();throw e;}
            c.disconnect();
        }throw new IOException("更新重定向次数过多");
    }
    static String manifest(URL channel,Token token)throws Exception {
        HttpURLConnection c=open(channel,token);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){token.check();if(out.size()+n>65536)throw new IOException("版本信息过大");out.write(b,0,n);}token.check();return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }finally{c.disconnect();token.connection=null;}
    }
    static File download(AppUpdate release,File dir,Token token,Progress progress)throws Exception {
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建更新目录");
        File target=new File(dir,release.filename()),partial=new File(dir,release.filename()+".part");
        if(target.exists()&&target.length()==release.size&&AppUpdate.digest(target).equals(release.sha)){token.check();return target;}
        HttpURLConnection c=open(release.apk,token);long started=System.nanoTime(),count=0,last=0;boolean complete=false;
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");long advertised=c.getContentLengthLong();if(advertised>=0&&advertised!=release.size)throw new IOException("安装包大小与版本信息不一致");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){token.check();count+=n;if(count>release.size)throw new IOException("安装包超过声明大小");if(System.nanoTime()-started>30L*60*1000000000)throw new IOException("下载超时，请稍后重试");
                    out.write(b,0,n);hash.update(b,0,n);long now=System.nanoTime();if(now-last>300000000L){progress.bytes(count,release.size);last=now;}}
                out.getFD().sync();
            }
            token.check();if(count!=release.size||!AppUpdate.hex(hash.digest()).equals(release.sha))throw new IOException("安装包校验失败，请重新下载");
            try{Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            complete=true;progress.bytes(count,release.size);return target;
        }finally{c.disconnect();token.connection=null;if(!complete)Files.deleteIfExists(partial.toPath());}
    }
}
