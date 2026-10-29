package com.xiaoxiao.pet;

import android.app.*;
import android.widget.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

final class UpdatePanel {
    static WeakReference<Dialog> window=new WeakReference<>(null);
    static boolean visible(){Dialog d=window.get();return d!=null&&d.isShowing();}
    static void message(MainActivity a,String text){if(!a.isFinishing()&&!a.isDestroyed())Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
    static String size(AppUpdate release){return String.format(Locale.ROOT,"%.1f MB",release.size/1048576.0);}
    static void offer(MainActivity a,AppUpdate release){new Apple.Builder(a).setTitle("发现新版本 "+release.name).setMessage(release.notes+"\n\n安装包："+size(release)).setPositiveButton("查看更新",(d,w)->show(a)).setNegativeButton("稍后",null).setNeutralButton("忽略此版本",(d,w)->UpdateRuntime.ignore(a,release.code)).show();}
    static void show(MainActivity a){
        Dialog previous=window.get();if(previous!=null)previous.dismiss();
        LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));l.addView(a.text("当前版本 "+a.version(),16));
        GroupedList.toggle(l,"自动检查新版本",UpdateRuntime.prefs(a).getBoolean("auto",true),(v,on)->UpdateRuntime.prefs(a).edit().putBoolean("auto",on).apply());
        l.addView(a.text("打开应用或桌宠运行时，每天最多自动检查一次。完全退出后，下次启动再检查。下载由你确认，安装还需要系统确认。",12));
        TextView status=a.text("点击下方检查更新",14);l.addView(status);LinearLayout details=a.column();l.addView(details);
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);AlertDialog dialog=new Apple.Builder(a).setTitle("应用更新").setView(scroll).setPositiveButton("关闭",null).show();window=new WeakReference<>(dialog);
        UpdateRuntime.CheckListener checked=(text,release)->{if(!dialog.isShowing()||a.isFinishing()||a.isDestroyed())return;status.setText(text);populate(a,details,status,release==null?UpdateRuntime.cached(a):release,dialog);};
        a.button(l,"检查更新",v->{status.setText("正在检查…");UpdateRuntime.check(a,true,checked);});populate(a,details,status,UpdateRuntime.cached(a),dialog);
        UpdateRuntime.check(a,true,checked);
    }
    static void populate(MainActivity a,LinearLayout box,TextView status,AppUpdate release,Dialog dialog){
        box.removeAllViews();if(!UpdateRuntime.available(a,release))return;
        box.addView(a.text("新版本 "+release.name+" · "+size(release),17));box.addView(a.text(release.notes,14));
        File ready=UpdateRuntime.ready(a,release);
        if(ready!=null)a.button(box,"安装更新",v->UpdateRuntime.install(a,release));
        else {Button download=a.button(box,"下载更新（"+size(release)+"）",v->{});final UpdateTransport.Token[] owned={null};
            Button cancel=a.button(box,"取消下载",v->{if(owned[0]!=null)owned[0].cancel();});cancel.setVisibility(android.view.View.GONE);
            download.setOnClickListener(v->{download.setEnabled(false);status.setText("正在下载…");boolean started=UpdateRuntime.download(a,release,new DownloadView(a,dialog,box,status,download,cancel,release));
                if(started){owned[0]=UpdateRuntime.active;cancel.setVisibility(android.view.View.VISIBLE);}
            });}
    }
    static final class DownloadView implements UpdateRuntime.DownloadListener {
        final WeakReference<MainActivity> host;final WeakReference<Dialog> dialog;final WeakReference<LinearLayout> box;final WeakReference<TextView> status;final WeakReference<Button> download,cancel;final AppUpdate release;
        DownloadView(MainActivity a,Dialog d,LinearLayout b,TextView s,Button start,Button stop,AppUpdate r){host=new WeakReference<>(a);dialog=new WeakReference<>(d);box=new WeakReference<>(b);status=new WeakReference<>(s);download=new WeakReference<>(start);cancel=new WeakReference<>(stop);release=r;}
        boolean live(){return host.get()!=null&&!host.get().isFinishing()&&!host.get().isDestroyed()&&dialog.get()!=null&&dialog.get().isShowing()&&status.get()!=null&&box.get()!=null;}
        public void progress(int percent){if(live())status.get().setText("正在下载 "+percent+"%");}
        public void done(File file){if(live()){status.get().setText("下载与校验完成，请确认安装");populate(host.get(),box.get(),status.get(),release,dialog.get());}}
        public void error(String text){if(live()){status.get().setText(text);if(download.get()!=null)download.get().setEnabled(true);if(cancel.get()!=null)cancel.get().setVisibility(android.view.View.GONE);}}
    }
}
