package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;
import org.json.*;import java.util.*;import java.text.SimpleDateFormat;

/** Paged conversation UI. The model still receives only its bounded recent context. */
final class ChatPage {
    final Context c;final String role,id;final boolean overlay;final Dialog dialog;final Handler handler=new Handler(Looper.getMainLooper());
    final LinearLayout rows;final ScrollView scroll;final EditText input;TextView status;final Button send,older;
    final SpeechQueue speech;final Runnable interrupt;JSONArray records=new JSONArray();boolean sending;String draft="",turnId;long from=Long.MIN_VALUE,to=Long.MAX_VALUE;
    ChatPage(Context context,String role,String id){
        this.c=context;this.role=role;this.id=id;overlay=!(c instanceof Activity);dialog=overlay?CompactPanel.create(c):ClientPage.create(c);
        LinearLayout box=column();box.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout top=new LinearLayout(c);TextView title=Apple.label(c,Data.name(role),20,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button close=button("收起");top.addView(close);close.setOnClickListener(v->dialog.dismiss());box.addView(top);
        older=button("更早的聊天");older.setOnClickListener(v->loadOlder());box.addView(older);
        LinearLayout navigation=new LinearLayout(c);Button byDate=button("按日期查看"),latest=button("回到最新");navigation.addView(byDate,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(latest,new LinearLayout.LayoutParams(0,-2,1));box.addView(navigation);
        byDate.setOnClickListener(v->{stopSpeaking();Calendar today=Calendar.getInstance();DatePickerDialog picker=new DatePickerDialog(c,(view,year,month,day)->{java.time.ZoneId zone=java.time.ZoneId.systemDefault();java.time.LocalDate date=java.time.LocalDate.of(year,month+1,day);from=date.atStartOfDay(zone).toInstant().toEpochMilli();to=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();loadLatest();status.setText("所选日期："+date+"（"+zone+"）");},today.get(Calendar.YEAR),today.get(Calendar.MONTH),today.get(Calendar.DAY_OF_MONTH));if(overlay)picker.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);picker.show();});
        latest.setOnClickListener(v->{stopSpeaking();from=Long.MIN_VALUE;to=Long.MAX_VALUE;loadLatest();status.setText("");});
        rows=column();scroll=new SpringScrollView(c);scroll.addView(rows);scroll.setBackground(Apple.shape(c,Apple.BACK,14));box.addView(scroll,new LinearLayout.LayoutParams(-1,overlay?dp(180):0,overlay?0:1));
        status=Apple.label(c,"",12,false);status.setTextColor(Apple.SECONDARY);box.addView(status);
        LinearLayout compose=new LinearLayout(c);compose.setGravity(Gravity.CENTER_VERTICAL);input=new EditText(c);input.setTextColor(Apple.INK);input.setHintTextColor(Apple.SECONDARY);input.setTextSize(15);input.setHint("今天想说些什么？");input.setMaxLines(4);input.setMinHeight(dp(48));input.setBackground(Apple.shape(c,Apple.BACK,12));input.setPadding(dp(10),dp(8),dp(10),dp(8));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8000)});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));send=button("发送");compose.addView(send);box.addView(compose);
        speech=new SpeechQueue(new SpeechQueue.Clock(){public void later(Runnable r,long delay){handler.postDelayed(r,delay);}public void remove(Runnable r){handler.removeCallbacks(r);}},new SpeechQueue.Sink(){
            public boolean visible(){return dialog.isShowing()&&(!(c instanceof Activity)||!((Activity)c).isFinishing());}
            public void show(String text,int shown,boolean preview){try{AgentRuntime.store().shown(turnId,shown);loadLatest();status.setText(preview?"详细回复已保存，点击查看全文":"");}catch(Exception e){status.setText("展示状态未保存，请查看聊天记录。");}}
            public void stop(int shown){}
        });interrupt=()->speech.cancel();
        dialog.setOnDismissListener(d->{speech.cancel();handler.removeCallbacksAndMessages(null);if(sending){sending=false;AgentRuntime.cancel();}PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();if(Chat.petChats.get(id)==dialog)Chat.petChats.remove(id);if(Chat.presentations.get(id)==interrupt)Chat.presentations.remove(id);});
        dialog.setContentView(box);send.setOnClickListener(v->send());
    }
    static void show(Context c,String role,String id){
        try{if(!PersonaManager.ensureInitialized(role)){Toast.makeText(c,"请先在首页完成初次相遇",1).show();return;}}catch(Exception e){Toast.makeText(c,e.getMessage(),1).show();return;}
        if(Chat.isPetChatOpen(id)){if(c instanceof Activity)Chat.closePet(id);else return;}AgentRuntime.interruptProactive();if(AgentRuntime.busy()){Toast.makeText(c,"她正在回复，请先停止当前回复",0).show();return;}
        Chat.interruptPresentation(id);new ChatPage(c,role,id).open();
    }
    void open(){Chat.presentations.put(id,interrupt);if(overlay){Chat.petChats.put(id,dialog);CompactPanel.showComposer(dialog,c,pet(),"大肥鱼-对话");}else ClientPage.show(dialog,"大肥鱼-聊天记录");loadLatest();}
    PetService.Pet pet(){return PetService.instance==null?null:PetService.instance.pets.get(id);}
    void loadLatest(){try{older.setText("更早的聊天");older.setEnabled(true);records=AgentRuntime.store().historyRange(from,to,null,20);render();scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}catch(Exception e){status.setText("聊天记录暂时无法读取，请稍后重试。");}}
    void loadOlder(){stopSpeaking();try{if(records.length()==0)return;JSONArray page=AgentRuntime.store().historyRange(from,to,records.getJSONObject(0).getString("pageKey"),20);if(page.length()==0){older.setText("已经到最早的记录");older.setEnabled(false);return;}int height=rows.getHeight(),position=scroll.getScrollY();for(int i=0;i<records.length();i++)page.put(records.getJSONObject(i));records=page;render();scroll.post(()->scroll.scrollTo(0,position+rows.getHeight()-height));}catch(Exception e){status.setText("更早的记录读取失败，请稍后重试。");}}
    void render()throws Exception {
        rows.removeAllViews();older.setVisibility(records.length()==0?View.GONE:View.VISIBLE);String previous="";
        if(records.length()==0)rows.addView(Apple.label(c,from==Long.MIN_VALUE?"我在呢。先说说你吧。\n\n也可以试试「记住：我喜欢蓝色」。":"这一天没有聊天记录。",16,false));
        for(int i=0;i<records.length();i++){
            JSONObject t=records.getJSONObject(i);String day=time(t,"yyyy年M月d日",t.optLong("at"));
            if(!day.equals(previous)){TextView heading=Apple.label(c,day,12,false);heading.setGravity(Gravity.CENTER);heading.setTextColor(Apple.SECONDARY);heading.setPadding(0,dp(10),0,dp(8));rows.addView(heading);previous=day;}
            if(!t.optString("user").isEmpty())bubble(t.optString("user"),"你 · "+time(t,"HH:mm:ss",t.optLong("at")),true,null);
            String visible=ReplySegments.visible(t),label=ReplySegments.label(t);bubble(visible.isEmpty()?"回复已保存，点击查看全文。":visible,(t.optBoolean("proactive")?"她主动说":"她")+" · "+time(t,"HH:mm:ss",t.optLong("repliedAt",t.optLong("at"))),false,label.isEmpty()?null:t);
        }
    }
    void bubble(String text,String caption,boolean user,JSONObject pending){
        LinearLayout wrapper=column();wrapper.setPadding(dp(8),dp(6),dp(8),dp(6));wrapper.setGravity(user?Gravity.RIGHT:Gravity.LEFT);
        TextView meta=Apple.label(c,caption,11,false);meta.setTextColor(Apple.SECONDARY);wrapper.addView(meta);
        TextView content=Apple.label(c,text,16,false);content.setTextIsSelectable(true);content.setLineSpacing(dp(3),1);content.setPadding(dp(12),dp(10),dp(12),dp(10));content.setBackground(Apple.shape(c,user?Apple.PANEL:Apple.BACK,14));
        content.setOnLongClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("聊天内容",text));Toast.makeText(c,"已复制",0).show();return true;});wrapper.addView(content);
        if(pending!=null){Button expand=button(ReplySegments.label(pending));expand.setOnClickListener(v->expand(pending));wrapper.addView(expand);}
        rows.addView(wrapper,new LinearLayout.LayoutParams(-1,-2));
    }
    void expand(JSONObject turn){
        stopSpeaking();
        TextView full=Apple.label(c,turn.optString("reply"),16,false);full.setPadding(dp(16),dp(12),dp(16),dp(12));full.setTextIsSelectable(true);ScrollView view=new SpringScrollView(c);view.addView(full);
        AlertDialog details=new Apple.Builder(c).setTitle("完整回复").setView(view).setPositiveButton("完成",null).create();if(overlay)details.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);details.show();
        try{AgentRuntime.store().shown(turn.getString("id"),ReplySegments.count(turn));turn.put("displayed",ReplySegments.count(turn));render();}catch(Exception e){status.setText("展示状态暂未保存");}
    }
    void send(){
        if(sending){AgentRuntime.cancel();return;}String message=input.getText().toString().trim();if(message.isEmpty())return;
        speech.cancel();from=Long.MIN_VALUE;to=Long.MAX_VALUE;PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();draft=message;turnId=null;
        AgentRuntime.Listener listener=new AgentRuntime.Listener(){
            public void saved(String id){turnId=id;}
            public void partial(String text){if(dialog.isShowing())status.setText("正在组织回复…");}
            public void done(String reply,String emotion){if(!dialog.isShowing())return;from=Long.MIN_VALUE;to=Long.MAX_VALUE;sending=false;send.setText("发送");input.setEnabled(true);status.setText("");loadLatest();
                PetService.Pet p=pet();if(p!=null)p.clearBubble();if(overlay&&p!=null&&p.visible&&!p.docked){AgentRuntime.express(id,emotion,reply,turnId);}else speech.start(reply);
            }
            public void error(String message){if(!dialog.isShowing())return;sending=false;send.setText("发送");input.setEnabled(true);input.setText(draft);status.setText(message);loadLatest();PetService.Pet p=pet();if(p!=null)p.clearBubble();}
        };
        if(!AgentRuntime.send(message,false,listener)){status.setText("她正在回复，请稍候");return;}sending=true;input.setText("");input.setEnabled(false);send.setText("停止");status.setText("正在想怎么说…");bubble(message,"你 · 正在发送",true,null);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));if(owner!=null&&owner.visible)owner.thinking();
    }
    void stopSpeaking(){speech.cancel();PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();}
    LinearLayout column(){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    static String time(JSONObject turn,String format,long at){SimpleDateFormat f=new SimpleDateFormat(format,Locale.CHINA);if(turn.has("timeZone"))f.setTimeZone(TimeZone.getTimeZone(turn.optString("timeZone")));return f.format(new Date(at));}
    int dp(int n){return Apple.dp(c,n);}
    Button button(String label){Button b=new Button(c);b.setText(label);Apple.button(b,false);return b;}
}
