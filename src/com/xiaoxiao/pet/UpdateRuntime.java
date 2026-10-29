package com.xiaoxiao.pet;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.concurrent.*;

/** Update I/O never shares the conversation executor and never starts a model request. */
final class UpdateRuntime {
    interface CheckListener {void done(String message,AppUpdate release);}
    interface DownloadListener {void progress(int percent);void done(File file);void error(String message);}
    interface Channels {String read(Context context)throws Exception;}
    static Channels channels=c->new JSONObject(Data.read(c.getAssets().open("update-channel.json"))).optString("manifest_url","");
    static final Handler main=new Handler(Looper.getMainLooper());
    static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static volatile UpdateTransport.Token active;
    static WeakReference<MainActivity> foreground=new WeakReference<>(null);
    static final int NOTICE=4206;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("app-updates",Context.MODE_PRIVATE);}
    static String channel(Context c){try{return channels.read(c).trim();}catch(Exception e){return "";}}
    static long installedCode(Context c)throws Exception{return UpdateApk.code(UpdateApk.installed(c));}
    static AppUpdate cached(Context c){try{String url=channel(c);if(url.isEmpty()||!url.equals(prefs(c).getString("source","")))return null;return new AppUpdate(prefs(c).getString("candidate",""),c.getPackageName(),AppUpdate.https(url));}catch(Exception e){return null;}}
    static boolean available(Context c,AppUpdate release){try{return release!=null&&release.newer(installedCode(c),Build.VERSION.SDK_INT);}catch(Exception e){return false;}}
    static File ready(Context c,AppUpdate release){if(release==null||!release.json.equals(prefs(c).getString("ready","")))return null;File f=new File(UpdateApk.directory(c),release.filename());return f.isFile()?f:null;}
    static void enter(MainActivity a){foreground=new WeakReference<>(a);
        if(prefs(a).getBoolean("awaitingPermission",false)){prefs(a).edit().remove("awaitingPermission").apply();UpdatePanel.show(a);return;}
        notice(a.getApplicationContext());check(a.getApplicationContext(),false,null);
    }
    static void leave(MainActivity a){if(foreground.get()==a)foreground.clear();}
    static void tick(Context c){check(c.getApplicationContext(),false,null);}
    static synchronized boolean check(Context context,boolean manual,CheckListener listener){
        Context c=context.getApplicationContext();String endpoint=channel(c);
        if(endpoint.isEmpty()){deliver(listener,"更新服务尚未配置，暂时请使用官方发布的安装包",null);return false;}
        long now=System.currentTimeMillis();if(!manual&&(!prefs(c).getBoolean("auto",true)||!AppUpdate.due(prefs(c).getLong("lastAttempt",0),now)))return false;
        if(active!=null){deliver(listener,"更新操作正在进行，请稍后再试",cached(c));return false;}
        final UpdateTransport.Token token=new UpdateTransport.Token();active=token;prefs(c).edit().putLong("lastAttempt",now).apply();
        worker.execute(()->{try{
            URL url=AppUpdate.https(endpoint);AppUpdate release=new AppUpdate(UpdateTransport.manifest(url,token),c.getPackageName(),url);token.check();
            prefs(c).edit().putString("candidate",release.json).putString("source",endpoint).putLong("lastSuccess",System.currentTimeMillis()).apply();
            long installed=installedCode(c);String message=release.code<=installed?"当前已是最新版本":release.minSdk>Build.VERSION.SDK_INT?"发现新版本，但它不支持当前 Android 系统":"发现新版本 "+release.name;
            if(release.code<=installed){prefs(c).edit().remove("ready").apply();clean(c,null);}
            deliver(listener,message,release);
            if(!manual)main.post(()->{if(!token.cancelled)notice(c);});
        }catch(Exception e){deliver(listener,"检查更新失败，请检查网络后重试",null);}
        finally{synchronized(UpdateRuntime.class){if(active==token)active=null;}}});return true;
    }
    static void deliver(CheckListener listener,String message,AppUpdate release){if(listener!=null)main.post(()->listener.done(message,release));}
    static void ignore(Context c,long code){prefs(c).edit().putLong("ignored",code).apply();NotificationManager nm=c.getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(NOTICE);}
    static void notice(Context c){
        if(!prefs(c).getBoolean("auto",true))return;AppUpdate release=cached(c);if(!available(c,release)||prefs(c).getLong("ignored",0)==release.code)return;
        long now=System.currentTimeMillis();if(prefs(c).getLong("noticedCode",0)==release.code&&!AppUpdate.due(prefs(c).getLong("noticedAt",0),now))return;
        MainActivity a=foreground.get();boolean shown=false;
        if(a!=null&&MainActivity.active&&!a.isFinishing()&&!a.isDestroyed()&&!AgentRuntime.busy()&&!Chat.isPetChatOpen("main")&&PersonaManager.isInitialized("main")&&!UpdatePanel.visible()){
            UpdatePanel.offer(a,release);shown=true;
        }else if(a==null&&!MainActivity.active){
            NotificationManager nm=c.getSystemService(NotificationManager.class);
            if(nm!=null&&nm.areNotificationsEnabled())try{
                nm.createNotificationChannel(new NotificationChannel("app-updates","版本更新",NotificationManager.IMPORTANCE_DEFAULT));
                Intent open=new Intent(c,MainActivity.class).putExtra("openUpdate",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pending=PendingIntent.getActivity(c,NOTICE,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                nm.notify(NOTICE,new Notification.Builder(c,"app-updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("大肥鱼有新版本 "+release.name).setContentText("点击查看更新内容，确认后下载").setContentIntent(pending).setAutoCancel(true).build());shown=true;
            }catch(SecurityException denied){}
        }
        if(shown)prefs(c).edit().putLong("noticedCode",release.code).putLong("noticedAt",now).apply();
    }
    static synchronized boolean download(Context context,AppUpdate release,DownloadListener listener){
        Context c=context.getApplicationContext();if(active!=null){listener.error("已有更新操作正在进行");return false;}if(!available(c,release)){listener.error("这个更新不适用于当前版本");return false;}
        final UpdateTransport.Token token=new UpdateTransport.Token();active=token;
        worker.execute(()->{File file=null;try{
            file=UpdateTransport.download(release,UpdateApk.directory(c),token,(n,total)->main.post(()->listener.progress((int)(n*100/total))));
            token.check();UpdateApk.verify(c,release,file);token.check();prefs(c).edit().putString("ready",release.json).apply();clean(c,file);
            final File verified=file;main.post(()->listener.done(verified));
        }catch(Exception e){if(file!=null)file.delete();String message=token.cancelled?"下载已取消":e.getMessage();main.post(()->listener.error(message==null?"下载失败，请稍后重试":message));}
        finally{synchronized(UpdateRuntime.class){if(active==token)active=null;}}});return true;
    }
    static void cancel(){UpdateTransport.Token token=active;if(token!=null)token.cancel();}
    static void install(MainActivity a,AppUpdate release){
        if(!a.getPackageManager().canRequestPackageInstalls()){
            new Apple.Builder(a).setTitle("允许安装更新").setMessage("请在系统设置中允许大肥鱼安装应用，然后返回继续安装。原有记忆会随覆盖安装保留。").setNegativeButton("稍后",null).setPositiveButton("前往设置",(d,w)->{
                try{prefs(a).edit().putBoolean("awaitingPermission",true).apply();a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){prefs(a).edit().remove("awaitingPermission").apply();UpdatePanel.message(a,"无法打开系统设置，请在系统应用设置中允许安装未知应用");}
            }).show();return;
        }
        final UpdateTransport.Token token;
        synchronized(UpdateRuntime.class){if(active!=null){UpdatePanel.message(a,"更新操作进行中，请稍后再试");return;}active=token=new UpdateTransport.Token();}
        worker.execute(()->{try{File file=ready(a,release);if(file==null)throw new IOException("请先下载安装包");UpdateApk.verify(a,release,file);token.check();
            main.post(()->{if(a.isFinishing()||a.isDestroyed()||token.cancelled)return;try{AgentRuntime.cancel();a.startActivity(UpdateApk.installIntent(a,release));}catch(Exception e){UpdatePanel.message(a,"无法打开系统安装器，请稍后重试");}});
        }catch(Exception e){prefs(a).edit().remove("ready").apply();new File(UpdateApk.directory(a),release.filename()).delete();main.post(()->{if(!a.isFinishing()&&!a.isDestroyed()){UpdatePanel.message(a,"安装前校验失败，请重新下载更新");UpdatePanel.show(a);}});}
        finally{synchronized(UpdateRuntime.class){if(active==token)active=null;}}});
    }
    static void clean(Context c,File keep){File[] files=UpdateApk.directory(c).listFiles();if(files!=null)for(File file:files)if((keep==null||!file.equals(keep))&&file.getName().matches("[0-9]+-[a-f0-9]{64}\\.apk(?:\\.part)?"))file.delete();}
}
