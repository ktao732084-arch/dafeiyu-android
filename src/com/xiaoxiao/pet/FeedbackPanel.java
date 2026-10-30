package com.xiaoxiao.pet;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.UUID;

/** 首页「关于 → 问题反馈」: the 兔小巢 board in a full page, already signed in as this install, screenshots allowed. */
final class FeedbackPanel {
    static final int PICK=43;
    static ValueCallback<Uri[]> picking;
    static String board(Context c){try{return Feedback.board(new JSONObject(Data.read(c.getAssets().open("update-channel.json"))));}catch(Exception e){return "";}}
    static String openid(){String id=Data.prefs().getString("feedback.openid","");if(id.isEmpty()){id=UUID.randomUUID().toString();Data.prefs().edit().putString("feedback.openid",id).apply();}return id;}
    static long code(Context c){try{return UpdateApk.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0));}catch(Exception e){return 0;}}

    static void show(MainActivity a){
        String board=board(a);
        if(board.isEmpty()){new Apple.Builder(a).setTitle("问题反馈").setMessage("这个版本还没有配置反馈页面。").setPositiveButton("知道了",null).show();return;}
        Dialog dialog=ClientPage.create(a);LinearLayout root=a.column();root.setBackgroundColor(Apple.BACK);root.setPadding(a.dp(12),a.dp(8),a.dp(12),0);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);Button close=new Button(a);close.setText("收起");Apple.button(close,false);close.setOnClickListener(v->dialog.dismiss());header.addView(close,new LinearLayout.LayoutParams(-2,a.dp(44)));
        TextView title=Apple.label(a,"问题反馈",18,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,a.dp(44),1));header.addView(new View(a),new LinearLayout.LayoutParams(a.dp(64),1));root.addView(header);
        TextView note=a.text("写下遇到的问题或想要的功能，可以附截图，开发者会在这里回复。只附带 App/Android 版本和机型，不含聊天、记忆或 Key。",12);note.setTextColor(Apple.SECONDARY);note.setPadding(a.dp(4),a.dp(6),a.dp(4),a.dp(8));root.addView(note);
        TextView failed=a.text("反馈页面打不开，检查网络后点这里重试",14);failed.setTextColor(Apple.BLUE);failed.setGravity(Gravity.CENTER);failed.setPadding(0,a.dp(24),0,a.dp(24));failed.setVisibility(View.GONE);root.addView(failed);
        WebView web=new WebView(a);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
        web.setBackgroundColor(Apple.BACK);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        String page=Feedback.page(board,a.version(),code(a),Build.VERSION.RELEASE+"（API "+Build.VERSION.SDK_INT+"）",(Build.MANUFACTURER+" "+Build.MODEL).trim()),id=openid();
        Runnable load=()->{failed.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);web.postUrl(page,Feedback.login(id,Feedback.nickname(id)).getBytes(java.nio.charset.StandardCharsets.UTF_8));};
        failed.setOnClickListener(v->load.run());
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String url=r.getUrl().toString();if(Feedback.inside(url))return false;
                if(url.startsWith("https://")||url.startsWith("http://"))try{a.startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception ignored){}
                return true;
            }
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){web.setVisibility(View.GONE);failed.setVisibility(View.VISIBLE);}}
        });
        web.setWebChromeClient(new WebChromeClient(){
            public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(picking!=null)picking.onReceiveValue(null);picking=callback;
                Intent pick=new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                try{a.startActivityForResult(Intent.createChooser(pick,"选择截图"),PICK);return true;}catch(Exception e){picking=null;return false;}
            }
        });
        dialog.setOnKeyListener((d,key,e)->{if(key!=KeyEvent.KEYCODE_BACK||e.getAction()!=KeyEvent.ACTION_UP)return key==KeyEvent.KEYCODE_BACK;if(web.canGoBack())web.goBack();else dialog.dismiss();return true;});
        dialog.setOnDismissListener(d->{chosen(Activity.RESULT_CANCELED,null);web.stopLoading();web.destroy();});
        dialog.setContentView(root);ClientPage.show(dialog,"大肥鱼-问题反馈");load.run();
    }
    /** MainActivity hands back the screenshot the user picked (or nothing). */
    static void chosen(int result,Intent data){
        ValueCallback<Uri[]> callback=picking;picking=null;if(callback==null)return;
        callback.onReceiveValue(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);
    }
}
