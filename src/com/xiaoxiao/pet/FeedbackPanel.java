package com.xiaoxiao.pet;

import android.app.*;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** 首页「关于 → 问题反馈」: write, optionally leave a contact, tap send. The draft survives closing and failures. */
final class FeedbackPanel {
    static final ExecutorService SEND=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"feedback");t.setDaemon(true);return t;});
    static String endpoint(MainActivity a){try{return Feedback.endpoint(new JSONObject(Data.read(a.getAssets().open("update-channel.json"))));}catch(Exception e){return "";}}
    static String device(){return (Build.MANUFACTURER+" "+Build.MODEL).trim();}
    static String android(){return Build.VERSION.RELEASE+"（API "+Build.VERSION.SDK_INT+"）";}
    static long code(MainActivity a){try{return UpdateApk.code(a.getPackageManager().getPackageInfo(a.getPackageName(),0));}catch(Exception e){return 0;}}
    static List<Long> sent(SharedPreferences p){List<Long> out=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString("feedback.sent","[]"));for(int i=0;i<a.length();i++)out.add(a.getLong(i));}catch(JSONException ignored){}return out;}

    static void show(MainActivity a){
        SharedPreferences prefs=Data.prefs();String url=endpoint(a);
        LinearLayout l=a.column();l.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
        GroupedList.note(l,"遇到的问题、想要的功能都可以写，会直接发给开发者。不会附带聊天记录、记忆或 Key。");
        EditText text=Chat.field(a,l,"哪里不对？在做什么的时候出现的？",prefs.getString("feedback.draft",""),false);
        text.setSingleLine(false);text.setMinLines(5);text.setMaxLines(10);text.setGravity(Gravity.TOP|Gravity.START);text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);text.setContentDescription("反馈内容");
        EditText contact=Chat.field(a,l,"联系方式（选填，方便回复你）",prefs.getString("feedback.contact",""),false);contact.setContentDescription("联系方式");
        TextView info=a.text("会一起发送：大肥鱼 "+a.version()+" · Android "+android()+" · "+device(),12);info.setTextColor(Apple.SECONDARY);info.setPadding(0,a.dp(10),0,0);l.addView(info);
        TextView status=a.text(url.isEmpty()?"这个版本没有配置反馈地址，暂时不能发送。":"",14);status.setPadding(0,a.dp(8),0,0);l.addView(status);
        Button send=a.button(l,"发送",null);Apple.button(send,true);send.setEnabled(!url.isEmpty());
        TextWatcher keep=new TextWatcher(){public void beforeTextChanged(CharSequence s,int x,int y,int z){}public void onTextChanged(CharSequence s,int x,int y,int z){}
            public void afterTextChanged(Editable s){prefs.edit().putString("feedback.draft",text.getText().toString()).putString("feedback.contact",contact.getText().toString()).apply();}};
        text.addTextChangedListener(keep);contact.addTextChangedListener(keep);
        ScrollView scroll=new SpringScrollView(a);scroll.addView(l);AlertDialog dialog=new Apple.Builder(a).setTitle("问题反馈").setView(scroll).setPositiveButton("关闭",null).show();
        send.setOnClickListener(v->{
            String body=text.getText().toString(),who=contact.getText().toString(),problem=Feedback.problem(body,who);
            if(problem!=null){status.setText(problem);return;}
            long now=System.currentTimeMillis();List<Long> allowed=Feedback.allow(sent(prefs),now);
            if(allowed==null){status.setText("一小时内最多发 "+Feedback.PER_HOUR+" 条，过一会儿再发吧。草稿已保存。");return;}
            JSONObject report;try{report=Feedback.payload(UUID.randomUUID().toString(),body,who,a.version(),code(a),android(),device());}catch(JSONException e){status.setText("整理反馈失败");return;}
            send.setEnabled(false);status.setText("正在发送…");
            SEND.execute(()->{
                String receipt=null,error=null;try{receipt=Feedback.send(url,report);}catch(Exception e){error=e.getMessage()==null?"发送失败，稍后再试":e.getMessage();}
                String ok=receipt,failed=error;
                a.runOnUiThread(()->{
                    if(ok!=null){prefs.edit().putString("feedback.sent",new JSONArray(allowed).toString()).putString("feedback.draft","").apply();Data.log("main","已发送问题反馈");}
                    if(a.isFinishing()||a.isDestroyed()||!dialog.isShowing()){if(ok!=null)Toast.makeText(a,"反馈已发送，谢谢！",Toast.LENGTH_SHORT).show();return;}
                    send.setEnabled(true);
                    if(ok==null){status.setText(failed+"。草稿已保存。");return;}
                    text.setText("");status.setText(ok.isEmpty()?"已收到，谢谢！":"已收到，谢谢！反馈编号 "+ok);
                });
            });
        });
    }
}
