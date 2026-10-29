package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs unchanged on the host JVM and Android ART; all network responses are local fixtures. */
public final class UpdateCoreTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    interface Attempt {void run()throws Exception;}
    static void rejected(Attempt action,String name)throws Exception {boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,name);}
    static JSONObject manifest(byte[] body)throws Exception {return new JSONObject().put("schema",1).put("package_name","com.dafeiyu.companion").put("version_code",11).put("version_name","0.4.7").put("min_sdk",26).put("size",body.length).put("sha256",AppUpdate.hex(MessageDigest.getInstance("SHA-256").digest(body))).put("apk_url","https://updates.example.test/releases/11.apk").put("release_notes","更新测试");}
    static final class Reply extends HttpURLConnection {
        final byte[] body;final int status;final String location;boolean closed;final long length;
        Reply(URL url,byte[] body,int status,String location,long length){super(url);this.body=body;this.status=status;this.location=location;this.length=length;}
        public int getResponseCode(){return status;}public String getHeaderField(String name){return "Location".equalsIgnoreCase(name)?location:null;}
        public long getContentLengthLong(){return length;}public InputStream getInputStream(){return new ByteArrayInputStream(body);}
        public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
    }
    public static void main(String[] args)throws Exception {
        checks=0;File root=new File(args[0]);root.mkdirs();byte[] body="signed-apk-fixture-bytes".getBytes(StandardCharsets.UTF_8);URL channel=AppUpdate.https("https://updates.example.test/stable.json");
        JSONObject valid=manifest(body);AppUpdate release=new AppUpdate(valid.toString(),"com.dafeiyu.companion",channel);
        check(release.newer(10,26)&&!release.newer(11,26)&&!release.newer(12,26),"numeric version comparison");
        check(!release.newer(10,25),"minimum Android version");
        check(AppUpdate.due(0,1)&&!AppUpdate.due(1000,2000)&&AppUpdate.due(1000,1000+AppUpdate.DAY)&&AppUpdate.due(2000,1000),"daily throttle and clock rollback");
        for(String invalid:new String[]{"http://updates.example.test/stable.json","https://user:password@updates.example.test/x","https://updates.example.test/x#fragment","https://updates.example.test:0/x"})rejected(()->AppUpdate.https(invalid),"reject unsafe URL");
        rejected(()->new AppUpdate(new JSONObject(valid.toString()).put("apk_url","https://other.example.test/x.apk").toString(),"com.dafeiyu.companion",channel),"off-site package rejected");
        rejected(()->new AppUpdate(new JSONObject(valid.toString()).put("version_code","11").toString(),"com.dafeiyu.companion",channel),"typed version required");
        rejected(()->new AppUpdate(new JSONObject(valid.toString()).put("size",AppUpdate.MAX_APK+1).toString(),"com.dafeiyu.companion",channel),"package limit");
        rejected(()->new AppUpdate(new JSONObject(valid.toString()).put("sha256","bad").toString(),"com.dafeiyu.companion",channel),"checksum syntax");
        rejected(()->new AppUpdate(valid.toString(),"other.app",channel),"package identity");
        UpdateTransport.Connections original=UpdateTransport.connections;
        try{
            AtomicInteger requests=new AtomicInteger();UpdateTransport.connections=url->{requests.incrementAndGet();return new Reply(url,valid.toString().getBytes(StandardCharsets.UTF_8),200,null,-1);};
            check(UpdateTransport.manifest(channel,new UpdateTransport.Token()).contains("0.4.7"),"manifest fetch");
            UpdateTransport.connections=url->new Reply(url,new byte[65537],200,null,-1);rejected(()->UpdateTransport.manifest(channel,new UpdateTransport.Token()),"bounded manifest body");
            UpdateTransport.connections=url->new Reply(url,new byte[0],302,"https://other.example.test/x",0);rejected(()->UpdateTransport.manifest(channel,new UpdateTransport.Token()),"cross-origin redirect rejected");
            UpdateTransport.connections=url->new Reply(url,new byte[0],302,"http://updates.example.test/x",0);rejected(()->UpdateTransport.manifest(channel,new UpdateTransport.Token()),"TLS downgrade rejected");
            UpdateTransport.connections=url->new Reply(url,new byte[0],302,"/loop",0);rejected(()->UpdateTransport.manifest(channel,new UpdateTransport.Token()),"redirect loop bounded");
            UpdateTransport.connections=url->url.getPath().equals("/stable.json")?new Reply(url,new byte[0],302,"/next.json",0):new Reply(url,valid.toString().getBytes(StandardCharsets.UTF_8),200,null,-1);
            check(UpdateTransport.manifest(channel,new UpdateTransport.Token()).contains("0.4.7"),"same-origin redirect");
            UpdateTransport.connections=url->new Reply(url,body,200,null,body.length);File download=UpdateTransport.download(release,new File(root,"good"),new UpdateTransport.Token(),(a,b)->{});
            check(download.length()==body.length&&AppUpdate.digest(download).equals(release.sha),"download size and digest");
            UpdateTransport.connections=url->{throw new IOException("offline");};check(UpdateTransport.download(release,download.getParentFile(),new UpdateTransport.Token(),(a,b)->{}).equals(download),"verified cached package reusable");
            UpdateTransport.connections=url->new Reply(url,"corrupt".getBytes(StandardCharsets.UTF_8),200,null,-1);File bad=new File(root,"bad");
            rejected(()->UpdateTransport.download(release,bad,new UpdateTransport.Token(),(a,b)->{}),"truncated body rejected");check(!new File(bad,release.filename()).exists()&&!new File(bad,release.filename()+".part").exists(),"failed download leaves no installable file");
            byte[] altered=body.clone();altered[0]^=1;UpdateTransport.connections=url->new Reply(url,altered,200,null,body.length);
            rejected(()->UpdateTransport.download(release,bad,new UpdateTransport.Token(),(a,b)->{}),"same-size corrupted body rejected");
            UpdateTransport.Token cancelled=new UpdateTransport.Token();cancelled.cancel();rejected(()->UpdateTransport.download(release,bad,cancelled,(a,b)->{}),"cancelled request cannot download");
            UpdateTransport.connections=url->new Reply(url,new byte[body.length+1],200,null,-1);rejected(()->UpdateTransport.download(release,bad,new UpdateTransport.Token(),(a,b)->{}),"streamed oversize rejected");
            UpdateTransport.connections=url->new Reply(url,new byte[0],503,null,0);rejected(()->UpdateTransport.manifest(channel,new UpdateTransport.Token()),"HTTP failure handled");
        }finally{UpdateTransport.connections=original;}
        System.out.println("PASS: "+checks+" update core checks");
    }
}
