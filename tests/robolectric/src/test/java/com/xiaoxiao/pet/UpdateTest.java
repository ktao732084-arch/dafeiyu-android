package com.xiaoxiao.pet;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class UpdateTest {
    Context c;UpdateRuntime.Channels channels;UpdateTransport.Connections connections;
    @Before public void init()throws Exception {UpdateRuntime.cancel();drain();c=RuntimeEnvironment.getApplication();Data.app=null;Data.roles.clear();Data.init(c);Data.prefs().edit().clear().commit();UpdateRuntime.prefs(c).edit().clear().commit();channels=UpdateRuntime.channels;connections=UpdateTransport.connections;UpdateRuntime.foreground.clear();MainActivity.active=false;TestKeyStore.install();}
    @After public void cleanup()throws Exception {UpdateRuntime.cancel();drain();UpdateRuntime.channels=channels;UpdateTransport.connections=connections;UpdateRuntime.foreground.clear();}
    static void drain()throws Exception {UpdateRuntime.worker.submit(()->{}).get(10,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();}
    static JSONObject metadata()throws Exception {return new JSONObject().put("schema",1).put("package_name","com.dafeiyu.companion").put("version_code",100).put("version_name","test-next").put("min_sdk",26).put("size",5).put("sha256","a".repeat(64)).put("apk_url","https://updates.example.test/releases/100.apk").put("release_notes","测试更新说明");}
    static class Reply extends HttpURLConnection {
        byte[] body;Reply(URL u,String body){super(u);this.body=body.getBytes(StandardCharsets.UTF_8);}public int getResponseCode(){return 200;}public InputStream getInputStream(){return new ByteArrayInputStream(body);}public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
    }
    @Test public void unconfiguredBuildNeverChecksNetworkAndManualCheckExplains()throws Exception {
        UpdateRuntime.channels=context->"";UpdateTransport.connections=url->{throw new AssertionError("unexpected network");};AtomicReference<String> message=new AtomicReference<>();
        assertFalse(UpdateRuntime.check(c,false,null));assertFalse(UpdateRuntime.check(c,true,(m,r)->message.set(m)));drain();assertTrue(message.get().contains("尚未配置"));assertEquals(0,UpdateRuntime.prefs(c).getLong("lastAttempt",0));
        MainActivity a=UiTest.ready();UiTest.click(a.content,"检查更新");drain();assertTrue(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains("尚未配置"));
    }
    @Test public void autoChecksOnceDailyManualBypassesAndIgnoreOnlyAffectsNotice()throws Exception {
        UpdateRuntime.channels=context->"https://updates.example.test/stable.json";AtomicInteger calls=new AtomicInteger();String raw=metadata().toString();
        UpdateTransport.connections=url->{calls.incrementAndGet();return new Reply(url,raw);};
        assertTrue(UpdateRuntime.check(c,false,null));drain();assertFalse(UpdateRuntime.check(c,false,null));assertEquals(1,calls.get());
        UpdateRuntime.ignore(c,100);AtomicReference<AppUpdate> found=new AtomicReference<>();assertTrue(UpdateRuntime.check(c,true,(m,r)->found.set(r)));drain();assertNotNull(found.get());assertEquals(2,calls.get());
        UpdateRuntime.prefs(c).edit().putBoolean("auto",false).putLong("lastAttempt",0).commit();assertFalse(UpdateRuntime.check(c,false,null));assertTrue(UpdateRuntime.check(c,true,(m,r)->{}));drain();assertEquals(3,calls.get());
    }
    @Test public void serverFailureDoesNotPretendAlreadyLatestOrRemoveCachedRelease()throws Exception {
        UpdateRuntime.channels=context->"https://updates.example.test/stable.json";UpdateRuntime.prefs(c).edit().putString("candidate",metadata().toString()).putString("source",UpdateRuntime.channel(c)).commit();
        UpdateTransport.connections=url->{throw new IOException("offline");};AtomicReference<String> message=new AtomicReference<>();UpdateRuntime.check(c,true,(m,r)->message.set(m));drain();
        assertTrue(message.get().contains("检查更新失败"));assertNotNull(UpdateRuntime.cached(c));assertEquals(0,UpdateRuntime.prefs(c).getLong("lastSuccess",0));
    }
    @Test public void disabledAutoDuringRequestSuppressesLateNotification()throws Exception {
        UpdateRuntime.channels=context->"https://updates.example.test/stable.json";CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);String raw=metadata().toString();
        UpdateTransport.connections=url->{entered.countDown();release.await(5,TimeUnit.SECONDS);return new Reply(url,raw);};
        try{assertTrue(UpdateRuntime.check(c,false,null));assertTrue(entered.await(2,TimeUnit.SECONDS));assertFalse(UpdateRuntime.check(c,true,(m,r)->{}));UpdateRuntime.prefs(c).edit().putBoolean("auto",false).commit();}finally{release.countDown();}
        drain();assertEquals(0,UpdateRuntime.prefs(c).getLong("noticedAt",0));
    }
    @Test public void cachedReleaseFromAnotherBuildChannelIsNotReused()throws Exception {
        UpdateRuntime.channels=context->"https://updates.example.test/stable.json";UpdateRuntime.prefs(c).edit().putString("candidate",metadata().toString()).putString("source","https://old.example.test/stable.json").commit();assertNull(UpdateRuntime.cached(c));
    }
    static PackageInfo info(String name,int version,byte signature){PackageInfo p=new PackageInfo();p.packageName=name;p.setLongVersionCode(version);p.applicationInfo=new ApplicationInfo();p.applicationInfo.minSdkVersion=26;p.signatures=new Signature[]{new Signature(new byte[]{signature})};return p;}
    @Test public void packageVersionSdkAndSignerMustAllMatch()throws Exception {
        AppUpdate update=new AppUpdate(metadata().toString(),c.getPackageName(),AppUpdate.https("https://updates.example.test/stable.json"));PackageInfo current=info(c.getPackageName(),10,(byte)1),valid=info(c.getPackageName(),100,(byte)1);
        UpdateApk.validateInfo(update,current,valid,34);
        for(PackageInfo wrong:new PackageInfo[]{info("other.app",100,(byte)1),info(c.getPackageName(),101,(byte)1),info(c.getPackageName(),100,(byte)2)}){try{UpdateApk.validateInfo(update,current,wrong,34);fail();}catch(IOException expected){}}
        valid.applicationInfo.minSdkVersion=35;try{UpdateApk.validateInfo(update,current,valid,34);fail();}catch(IOException expected){}
    }
    @Test public void providerOnlyExposesExactPrivateApkAndReadGrant()throws Exception {
        AppUpdate update=new AppUpdate(metadata().toString(),c.getPackageName(),AppUpdate.https("https://updates.example.test/stable.json"));File dir=UpdateApk.directory(c);dir.mkdirs();File file=new File(dir,update.filename());Files.write(file.toPath(),new byte[]{1,2,3});
        Uri uri=UpdateApk.uri(c,update);assertEquals(file.getCanonicalFile(),UpdateApk.resolve(c,uri));Intent intent=UpdateApk.installIntent(c,update);assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals(uri,intent.getData());assertNotEquals(0,intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for(Uri wrong:new Uri[]{Uri.parse("content://"+c.getPackageName()+".updates/../agent/companion-v1.json"),uri.buildUpon().appendQueryParameter("path","agent").build(),Uri.parse("content://other.updates/"+update.filename())}){try{UpdateApk.resolve(c,wrong);fail();}catch(IOException expected){}}
        UpdateFileProvider provider=Robolectric.buildContentProvider(UpdateFileProvider.class).create().get();try{provider.openFile(uri,"w");fail();}catch(SecurityException expected){}
        assertEquals(3,provider.query(uri,null,null,null,null).getCount()==1?file.length():0);
    }
    @Test public void installPermissionUsesOwnSettingsAndReturnsToExplicitConfirmation()throws Exception {
        UpdateRuntime.channels=context->"";MainActivity a=UiTest.ready();
        AppUpdate update=new AppUpdate(metadata().toString(),c.getPackageName(),AppUpdate.https("https://updates.example.test/stable.json"));
        Shadows.shadowOf(a.getPackageManager()).setCanRequestPackageInstalls(false);
        UpdateRuntime.install(a,update);UiTest.click(UiTest.dialog(),"前往设置");
        Intent settings=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(settings);
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,settings.getAction());assertEquals("package:"+c.getPackageName(),settings.getDataString());
        assertTrue(UpdateRuntime.prefs(c).getBoolean("awaitingPermission",false));UpdateRuntime.enter(a);drain();
        assertFalse(UpdateRuntime.prefs(c).getBoolean("awaitingPermission",false));assertNull(Shadows.shadowOf(a).getNextStartedActivity());assertTrue(UpdatePanel.visible());
    }
    @Test public void tamperedReadyApkCanBeDownloadedAgainInsteadOfGettingStuck()throws Exception {
        UpdateRuntime.channels=context->"https://updates.example.test/stable.json";String raw=metadata().toString();UpdateTransport.connections=url->new Reply(url,raw);
        MainActivity a=UiTest.ready();AppUpdate update=new AppUpdate(raw,c.getPackageName(),AppUpdate.https(UpdateRuntime.channel(c)));
        UpdateRuntime.prefs(c).edit().putString("candidate",raw).putString("source",UpdateRuntime.channel(c)).putString("ready",raw).commit();
        File file=new File(UpdateApk.directory(c),update.filename());file.getParentFile().mkdirs();Files.write(file.toPath(),"wrong".getBytes(StandardCharsets.UTF_8));
        Shadows.shadowOf(a.getPackageManager()).setCanRequestPackageInstalls(true);UpdateRuntime.install(a,update);drain();drain();
        assertEquals("",UpdateRuntime.prefs(c).getString("ready",""));assertFalse(file.exists());assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        assertTrue(UiTest.texts(UiTest.dialog().getWindow().getDecorView()).contains("下载更新（"));
    }
    @Test public void downloadDigestFailureNeverSetsReadyOrTouchesCompanionData()throws Exception {
        AppUpdate update=new AppUpdate(metadata().toString(),c.getPackageName(),AppUpdate.https("https://updates.example.test/stable.json"));AgentRuntime.memory=new AgentStore(Files.createTempDirectory("update-memory").toFile());AgentRuntime.store().remember("我喜欢蓝色");String before=AgentRuntime.store().snapshot().toString();
        UpdateTransport.connections=url->new Reply(url,"wrong");AtomicInteger failed=new AtomicInteger();assertTrue(UpdateRuntime.download(c,update,new UpdateRuntime.DownloadListener(){public void progress(int p){}public void done(File f){fail("corrupt APK accepted");}public void error(String m){failed.incrementAndGet();}}));drain();
        assertEquals(1,failed.get());assertEquals("",UpdateRuntime.prefs(c).getString("ready",""));assertEquals(before,AgentRuntime.store().snapshot().toString());
    }
}
