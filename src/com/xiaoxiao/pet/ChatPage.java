package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import android.content.res.ColorStateList;import android.graphics.*;import android.graphics.drawable.*;import android.os.*;import android.view.*;import android.view.animation.*;import android.widget.*;
import org.json.*;import java.util.*;import java.text.SimpleDateFormat;

/** Paged conversation UI in a messaging-app layout. The model still receives only its bounded recent context. */
final class ChatPage {
    static final long STAMP_GAP=5*60*1000L;static final int PAGE=20,SEARCH_LIMIT=40;
    static java.lang.ref.WeakReference<ChatPage> current=new java.lang.ref.WeakReference<>(null);
    final Context c;final String role,id;final boolean overlay;final Dialog dialog;final Handler handler=new Handler(Looper.getMainLooper());
    final LinearLayout rows;final ScrollView scroll;final EditText input;TextView status;final TextView presence,filter,setup;final Button send,older,jump;final FrameLayout stage;final int bubbleWidth,listHeight;int lifted;
    final SpeechQueue speech;final Runnable interrupt,refresh;JSONArray records=new JSONArray();boolean sending,paged,nearBottom=true;String draft="",turnId,lastDay,anchor,focus;long from=Long.MIN_VALUE,to=Long.MAX_VALUE,lastStamp;View typing;
    ChatPage(Context context,String role,String id){
        this.c=context;this.role=role;this.id=id;overlay=!(c instanceof Activity);dialog=overlay?CompactPanel.create(c):ClientPage.create(c);
        bubbleWidth=Math.round(Math.min(c.getResources().getDisplayMetrics().widthPixels,overlay?dp(360):Integer.MAX_VALUE)*.66f);
        LinearLayout box=column();box.setBackgroundColor(Apple.PANEL);
        // Header: back, her avatar, name and presence, then the history menu.
        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(4),dp(6),dp(4),dp(6));
        ImageButton close=icon(Glyph.BACK,"收起聊天");close.setOnClickListener(v->dialog.dismiss());top.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        View face=new View(c);face.setBackground(Avatar.of(c,role,38));LinearLayout.LayoutParams faceSize=new LinearLayout.LayoutParams(dp(38),dp(38));faceSize.rightMargin=dp(10);top.addView(face,faceSize);
        LinearLayout who=column();TextView title=Apple.label(c,Data.name(role),17,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);who.addView(title);
        presence=Apple.label(c,"",12,false);presence.setTextColor(Apple.SECONDARY);presence.setPadding(0,dp(4),0,0);who.addView(presence);top.addView(who,new LinearLayout.LayoutParams(0,-2,1));idle();
        ImageButton more=icon(Glyph.MORE,"聊天记录选项");more.setOnClickListener(v->menu(v));top.addView(more,new LinearLayout.LayoutParams(dp(44),dp(44)));
        box.addView(top);box.addView(Apple.hairline(c),new LinearLayout.LayoutParams(-1,1));
        filter=Apple.label(c,"",13,false);filter.setTextColor(Apple.BLUE);filter.setGravity(Gravity.CENTER);filter.setPadding(dp(16),dp(9),dp(16),dp(9));filter.setBackground(Apple.shape(c,Apple.TINT,0));filter.setVisibility(View.GONE);filter.setOnClickListener(v->latest());box.addView(filter);
        // Conversation: grey backdrop, white bubbles for her and blue ones for you.
        LinearLayout list=column();list.setPadding(dp(12),dp(6),dp(12),dp(12));
        older=new Button(c);older.setText("查看更早的消息");Apple.button(older,false);older.setTextSize(13);older.setBackground(null);older.setOnClickListener(v->loadOlder());list.addView(older,new LinearLayout.LayoutParams(-1,dp(40)));
        rows=column();list.addView(rows);scroll=new SpringScrollView(c);scroll.setFillViewport(true);scroll.setBackgroundColor(Apple.FILL);scroll.addView(list);
        listHeight=overlay?dp(240):0;stage=new FrameLayout(c);stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));box.addView(stage,new LinearLayout.LayoutParams(-1,listHeight,overlay?0:1));
        // Scrolled up to read? A small chip brings you back to the newest message.
        jump=new Button(c);jump.setText("↓ 最新消息");Apple.button(jump,false);jump.setTextSize(13);jump.setMinHeight(dp(34));jump.setPadding(dp(14),0,dp(14),0);jump.setElevation(dp(3));jump.setVisibility(View.GONE);jump.setOnClickListener(v->scroll.fullScroll(View.FOCUS_DOWN));
        GradientDrawable chip=Apple.shape(c,Apple.PANEL,17);jump.setBackground(new RippleDrawable(ColorStateList.valueOf((Apple.BLUE&0xffffff)|0x24000000),chip,null));
        FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,dp(34),Gravity.BOTTOM|Gravity.END);corner.setMargins(0,0,dp(14),dp(12));stage.addView(jump,corner);
        scroll.setOnScrollChangeListener((v,x,y,ox,oy)->bottom());
        // When the keyboard shrinks the list, keep the newest message in view if you were reading it.
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t<ob-ot&&nearBottom)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});
        setup=Apple.label(c,"还没接通模型，现在她只能帮你记东西（用「记住：」开头）。点这里去连接 ›",13,false);setup.setTextColor(Apple.BLUE);setup.setLineSpacing(dp(2),1);setup.setPadding(dp(16),dp(10),dp(16),dp(10));setup.setBackground(Apple.shape(c,Apple.TINT,0));setup.setOnClickListener(v->connect());box.addView(setup);
        status=Apple.label(c,"",12,false);status.setTextColor(Apple.SECONDARY);status.setGravity(Gravity.CENTER);status.setLineSpacing(dp(2),1);status.setPadding(dp(16),dp(8),dp(16),dp(8));status.setBackgroundColor(Apple.FILL);status.setVisibility(View.GONE);
        status.addTextChangedListener(new Watcher(()->status.setVisibility(status.getText().length()==0?View.GONE:View.VISIBLE)));box.addView(status);
        // Composer: a rounded field and a pill-shaped send button.
        box.addView(Apple.hairline(c),new LinearLayout.LayoutParams(-1,1));
        LinearLayout compose=new LinearLayout(c);compose.setGravity(Gravity.BOTTOM);compose.setPadding(dp(12),dp(8),dp(12),dp(10));
        input=new EditText(c);input.setTextColor(Apple.INK);input.setHintTextColor(Apple.SECONDARY);input.setTextSize(16);input.setHint("想和她说点什么…");input.setMaxLines(5);input.setMinHeight(dp(40));input.setBackground(Apple.shape(c,Apple.FILL,20));input.setPadding(dp(16),dp(9),dp(16),dp(9));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8000)});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=new Button(c);send.setText("发送");Apple.button(send,true);GradientDrawable pill=new GradientDrawable();pill.setColor(Apple.PRIMARY);pill.setCornerRadius(dp(20));send.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),pill,null));send.setMinHeight(dp(40));
        LinearLayout.LayoutParams sendSize=new LinearLayout.LayoutParams(-2,dp(40));sendSize.leftMargin=dp(8);compose.addView(send,sendSize);box.addView(compose);
        input.addTextChangedListener(new Watcher(this::sendState));sendState();
        speech=new SpeechQueue(new SpeechQueue.Clock(){public void later(Runnable r,long delay){handler.postDelayed(r,delay);}public void remove(Runnable r){handler.removeCallbacks(r);}},new SpeechQueue.Sink(){
            public boolean visible(){return dialog.isShowing()&&(!(c instanceof Activity)||!((Activity)c).isFinishing());}
            public void show(String text,int shown,boolean preview){try{AgentRuntime.store().shown(turnId,shown);loadLatest();status.setText(preview?"她写了一段长长的回复，点气泡下方就能看全文":"");}catch(Exception e){status.setText("展示状态没保存上，可以在聊天记录里查看。");}}
            public void stop(int shown){}
        });interrupt=()->speech.cancel();refresh=()->{if(dialog.isShowing()&&!sending&&!paged&&from==Long.MIN_VALUE)loadLatest();};
        dialog.setOnDismissListener(d->{if(Chat.historyViews.get(id)==refresh)Chat.historyViews.remove(id);speech.cancel();handler.removeCallbacksAndMessages(null);if(sending){sending=false;AgentRuntime.cancel();}PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();if(Chat.petChats.get(id)==dialog)Chat.petChats.remove(id);if(Chat.presentations.get(id)==interrupt)Chat.presentations.remove(id);if(c instanceof MainActivity&&!((MainActivity)c).isFinishing()&&!((MainActivity)c).isDestroyed())((MainActivity)c).render();});
        // Returning from 模型连接 hides the banner straight away.
        box.getViewTreeObserver().addOnWindowFocusChangeListener(focused->{if(focused)setup.setVisibility(AgentRuntime.configured()?View.GONE:View.VISIBLE);});
        dialog.setContentView(box);send.setOnClickListener(v->send());
    }
    static void show(Context c,String role,String id){
        try{if(!PersonaManager.ensureInitialized(role)){Toast.makeText(c,"先回首页和她初次相遇吧",1).show();return;}}catch(Exception e){Toast.makeText(c,e.getMessage(),1).show();return;}
        if(Chat.isPetChatOpen(id)){if(c instanceof Activity)Chat.closePet(id);else return;}
        // This screen's chat page is already open (for example behind 模型连接): bring it up to date instead of stacking another.
        ChatPage open=current.get();if(open!=null&&open.c==c&&open.id.equals(id)&&open.dialog.isShowing()){open.loadLatest();return;}AgentRuntime.interruptProactive();if(AgentRuntime.busy()){Toast.makeText(c,"她还在回上一句，先停下当前回复吧",0).show();return;}
        Chat.interruptPresentation(id);new ChatPage(c,role,id).open();
    }
    void open(){current=new java.lang.ref.WeakReference<>(this);Chat.presentations.put(id,interrupt);Chat.historyViews.put(id,refresh);if(overlay){Chat.petChats.put(id,dialog);CompactPanel.showComposer(dialog,c,pet(),"大肥鱼-对话");dialog.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(this::keyboard);}else ClientPage.show(dialog,"大肥鱼-聊天");loadLatest();}
    PetService.Pet pet(){return PetService.instance==null?null:PetService.instance.pets.get(id);}
    void idle(){presence.setText("在线 · "+AgentRuntime.moodLabel());presence.setTextColor(Apple.SECONDARY);}
    /** Tracks whether the newest message is on screen, and shows the jump chip when it is not. */
    void bottom(){View list=scroll.getChildAt(0);nearBottom=list==null||scroll.getScrollY()+scroll.getHeight()>=list.getHeight()-dp(64);jump.setVisibility(nearBottom||records.length()==0?View.GONE:View.VISIBLE);}
    void connect(){
        if(c instanceof Activity){ModelSetup.show((Activity)c);return;}
        dialog.dismiss();c.startActivity(new Intent(c,MainActivity.class).putExtra("openModel",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    /**
     * The pet's chat window floats beside her, so the keyboard can cover it. Whatever part the keyboard
     * still covers after the system's own adjustment is measured from the window's insets: a bottom panel
     * moves up by that much, and the list shrinks if the panel would otherwise run off the top. Self-correcting:
     * once nothing is covered it stops, and everything returns when the keyboard closes.
     */
    void keyboard(){
        if(!overlay||Build.VERSION.SDK_INT<30||!dialog.isShowing())return;
        Window w=dialog.getWindow();WindowInsets insets=w.getDecorView().getRootWindowInsets();if(insets==null)return;
        WindowManager.LayoutParams a=w.getAttributes();LinearLayout.LayoutParams list=(LinearLayout.LayoutParams)stage.getLayoutParams();
        if(!insets.isVisible(WindowInsets.Type.ime())){
            if(lifted!=0||list.height!=listHeight){a.y-=lifted;lifted=0;w.setAttributes(a);list.height=listHeight;stage.setLayoutParams(list);}
            return;
        }
        int covered=insets.getInsets(WindowInsets.Type.ime()).bottom;if(covered<=0)return;
        int y=a.y,height=list.height;
        if((a.gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM){
            WindowMetrics metrics=c.getSystemService(WindowManager.class).getMaximumWindowMetrics();
            android.graphics.Insets bars=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            int frame=metrics.getBounds().height()-bars.top-bars.bottom;
            y+=covered;int overflow=y+w.getDecorView().getHeight()-(frame-dp(12));
            if(overflow>0){height=Math.max(dp(96),height-overflow);y-=overflow-(list.height-height);}
        }else height=Math.max(dp(96),height-covered);
        // Only act on a real change, so a keyboard that still covers the minimum panel cannot cause a layout loop.
        if(y>a.y){lifted+=y-a.y;a.y=y;w.setAttributes(a);}
        if(height!=list.height){list.height=height;stage.setLayoutParams(list);}
    }
    void sendState(){send.setAlpha(sending||input.getText().toString().trim().length()>0?1f:.45f);}
    void menu(View anchor){
        PopupMenu menu=new PopupMenu(c,anchor);menu.getMenu().add(0,3,0,"搜索聊天");menu.getMenu().add(0,1,1,"按日期查看");menu.getMenu().add(0,2,2,"回到最新");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==3)search();else if(item.getItemId()==1)pickDate();else latest();return true;});menu.show();
    }
    void pickDate(){
        stopSpeaking();Calendar today=Calendar.getInstance();
        DatePickerDialog picker=new DatePickerDialog(c,(view,year,month,day)->{java.time.ZoneId zone=java.time.ZoneId.systemDefault();java.time.LocalDate date=java.time.LocalDate.of(year,month+1,day);from=date.atStartOfDay(zone).toInstant().toEpochMilli();to=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            anchor=focus=null;filter.setText("只看 "+year+"年"+(month+1)+"月"+day+"日 的聊天"+zoneNote(zone)+" · 点这里回到最新");filter.setVisibility(View.VISIBLE);status.setText("");loadLatest();},today.get(Calendar.YEAR),today.get(Calendar.MONTH),today.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());if(overlay)picker.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);picker.show();
    }
    /** Days are cut in the phone's current time zone; only worth saying when the chats were recorded in another one. */
    static String zoneNote(java.time.ZoneId zone){
        try{JSONArray last=AgentRuntime.store().historyRange(Long.MIN_VALUE,Long.MAX_VALUE,null,1);if(last.length()==0)return "";String recorded=last.getJSONObject(0).optString("timeZone");
            if(recorded.isEmpty()||java.time.ZoneId.of(recorded).getRules().equals(zone.getRules()))return "";}catch(Exception unknown){return "";}
        return "（按手机当前时区 "+TimeZone.getTimeZone(zone).getDisplayName(false,TimeZone.SHORT,Locale.CHINA)+"）";
    }
    void latest(){stopSpeaking();from=Long.MIN_VALUE;to=Long.MAX_VALUE;anchor=focus=null;filter.setVisibility(View.GONE);loadLatest();status.setText("");}
    void loadLatest(){setup.setVisibility(AgentRuntime.configured()?View.GONE:View.VISIBLE);try{paged=false;older.setText("查看更早的消息");older.setEnabled(true);records=AgentRuntime.store().historyRange(from,to,anchor,PAGE);render();scroll.post(()->{scroll.fullScroll(View.FOCUS_DOWN);bottom();});}catch(Exception e){status.setText("聊天记录暂时打不开，稍后再试试。");}}
    void loadOlder(){stopSpeaking();try{if(records.length()==0)return;JSONArray page=AgentRuntime.store().historyRange(from,to,records.getJSONObject(0).getString("pageKey"),PAGE);if(page.length()==0){older.setText("已经翻到最早的聊天啦");older.setEnabled(false);return;}boolean earliest=page.length()<PAGE;int height=rows.getHeight(),position=scroll.getScrollY();for(int i=0;i<records.length();i++)page.put(records.getJSONObject(i));records=page;paged=true;render();if(earliest){older.setText("已经翻到最早的聊天啦");older.setEnabled(false);}scroll.post(()->scroll.scrollTo(0,position+rows.getHeight()-height));}catch(Exception e){status.setText("更早的聊天没读出来，稍后再试试。");}}
    void render()throws Exception {
        rows.removeAllViews();typing=null;lastDay=null;lastStamp=Long.MIN_VALUE;older.setVisibility(records.length()==0||!paged&&records.length()<PAGE?View.GONE:View.VISIBLE);
        if(records.length()==0){empty();return;}
        for(int i=0;i<records.length();i++){
            JSONObject t=records.getJSONObject(i);long at=t.optLong("at"),replied=t.optLong("repliedAt",at);
            if(focus!=null&&focus.equals(t.optString("id"))){TextView mark=Apple.label(c,"— 搜索到的这一句 —",12,false);mark.setTextColor(Apple.BLUE);mark.setGravity(Gravity.CENTER);mark.setPadding(0,dp(14),0,0);rows.addView(mark,new LinearLayout.LayoutParams(-1,-2));lastDay=null;}
            if(!t.optString("user").isEmpty()){stamp(t,at);message(t.optString("user"),true,null,false);}
            stamp(t,replied);segments(t);
        }
    }
    /** Each shown segment is its own bubble, like several short messages in a row; the rest waits behind the full-text pill. */
    void segments(JSONObject t){
        List<String> parts=ReplySegments.split(t.optString("reply"));int shown=t.has("displayed")?Math.min(parts.size(),t.optInt("displayed")):parts.size();
        JSONObject pending=ReplySegments.label(t).isEmpty()?null:t;boolean proactive=t.optBoolean("proactive");
        if(shown==0){message("回复已经保存好了，点开看看全文吧。",false,pending,proactive);return;}
        for(int i=0;i<shown;i++){boolean last=i==shown-1;message(i==0?AgentPolicy.replyText(parts.get(i).trim()):parts.get(i).trim(),false,last?pending:null,last&&proactive);}
    }
    void empty(){
        LinearLayout hello=column();hello.setGravity(Gravity.CENTER_HORIZONTAL);hello.setPadding(dp(24),dp(overlay?12:56),dp(24),dp(24));
        View face=new View(c);face.setBackground(Avatar.of(c,role,72));hello.addView(face,new LinearLayout.LayoutParams(dp(72),dp(72)));
        boolean filtered=from!=Long.MIN_VALUE;
        TextView line=Apple.label(c,filtered?"这一天我们没有聊天哦。":"我在呢，先说说你吧～",16,true);line.setGravity(Gravity.CENTER);line.setPadding(0,dp(14),0,0);hello.addView(line);
        if(!filtered){
            TextView tip=Apple.label(c,"想让她记住什么，可以这样开头：",13,false);tip.setTextColor(Apple.SECONDARY);tip.setPadding(0,dp(10),0,dp(10));hello.addView(tip);
            Button example=new Button(c);example.setText("记住：我喜欢蓝色");Apple.button(example,false);example.setTextSize(14);example.setOnClickListener(v->{input.setText("记住：我喜欢蓝色");input.setSelection(input.length());input.requestFocus();});hello.addView(example,new LinearLayout.LayoutParams(-2,dp(38)));
        }
        rows.addView(hello,new LinearLayout.LayoutParams(-1,-2));
    }
    /** A centred time label when the day changes or five quiet minutes have passed, as messaging apps do. */
    void stamp(JSONObject turn,long at){
        String day=time(turn,"yyyyMMdd",at);if(day.equals(lastDay)&&at-lastStamp<STAMP_GAP){lastStamp=Math.max(lastStamp,at);return;}
        lastDay=day;lastStamp=at;TextView label=Apple.label(c,when(turn,at),12,false);label.setTextColor(Apple.SECONDARY);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(12),0,dp(6));rows.addView(label,new LinearLayout.LayoutParams(-1,-2));
    }
    static String when(JSONObject turn,long at){
        TimeZone zone=turn.has("timeZone")?TimeZone.getTimeZone(turn.optString("timeZone")):TimeZone.getDefault();
        Calendar now=Calendar.getInstance(zone),then=Calendar.getInstance(zone);then.setTimeInMillis(at);String clock=time(turn,"HH:mm",at);
        if(now.get(Calendar.YEAR)==then.get(Calendar.YEAR)&&now.get(Calendar.DAY_OF_YEAR)==then.get(Calendar.DAY_OF_YEAR))return clock;
        now.add(Calendar.DAY_OF_YEAR,-1);if(now.get(Calendar.YEAR)==then.get(Calendar.YEAR)&&now.get(Calendar.DAY_OF_YEAR)==then.get(Calendar.DAY_OF_YEAR))return "昨天 "+clock;
        return time(turn,now.get(Calendar.YEAR)==then.get(Calendar.YEAR)?"M月d日 ":"yyyy年M月d日 ",at)+clock;
    }
    View message(String text,boolean user,JSONObject pending,boolean proactive){
        LinearLayout line=new LinearLayout(c);line.setGravity(user?Gravity.END:Gravity.START);line.setPadding(0,dp(5),0,dp(5));
        LinearLayout body=column();body.setGravity(user?Gravity.END:Gravity.START);
        TextView content;
        if(user){
            // Constant blue with white text in both appearances, like the primary button; not palette-mapped.
            content=new TextView(c);content.setText(text);content.setTextSize(16);content.setTextColor(Color.WHITE);content.setIncludeFontPadding(false);
            GradientDrawable shape=new GradientDrawable();shape.setColor(Apple.PRIMARY);float r=dp(18),tip=dp(6);shape.setCornerRadii(new float[]{r,r,tip,tip,r,r,r,r});content.setBackground(shape);
        }else{content=Apple.label(c,text,16,false);content.setBackground(Apple.corners(c,Apple.PANEL,6,18,18,18));}
        content.setMaxWidth(bubbleWidth);content.setLineSpacing(dp(3),1);content.setPadding(dp(13),dp(10),dp(13),dp(10));
        content.setOnLongClickListener(v->{bubbleMenu(v,text,user);return true;});
        if(pending!=null)content.setOnClickListener(v->expand(pending));
        // Let the text measure its own width and height; MATCH_PARENT can inherit the short footer width.
        body.addView(content,new LinearLayout.LayoutParams(-2,-2));
        if(proactive){TextView note=Apple.label(c,"她主动来找你",11,false);note.setTextColor(Apple.SECONDARY);note.setPadding(dp(4),dp(5),0,0);body.addView(note);}
        if(pending!=null){Button expand=new Button(c);expand.setText(ReplySegments.label(pending));Apple.button(expand,false);expand.setTextSize(12);expand.setMinHeight(dp(30));expand.setPadding(dp(12),0,dp(12),0);expand.setOnClickListener(v->expand(pending));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.topMargin=dp(6);body.addView(expand,p);}
        View face=new View(c);face.setBackground(user?Avatar.me(c,36):Avatar.of(c,role,36));LinearLayout.LayoutParams faceSize=new LinearLayout.LayoutParams(dp(36),dp(36));
        if(user){faceSize.leftMargin=dp(8);line.addView(body);line.addView(face,faceSize);}else{faceSize.rightMargin=dp(8);line.addView(face,faceSize);line.addView(body);}
        rows.addView(line,new LinearLayout.LayoutParams(-1,-2));return line;
    }
    /** Three breathing dots in her bubble while the reply is on its way. */
    void typing(boolean on){
        if(typing!=null){rows.removeView(typing);typing=null;}if(!on)return;
        LinearLayout line=new LinearLayout(c);line.setPadding(0,dp(5),0,dp(5));View face=new View(c);face.setBackground(Avatar.of(c,role,36));LinearLayout.LayoutParams faceSize=new LinearLayout.LayoutParams(dp(36),dp(36));faceSize.rightMargin=dp(8);line.addView(face,faceSize);
        LinearLayout dots=new LinearLayout(c);dots.setGravity(Gravity.CENTER_VERTICAL);dots.setPadding(dp(14),0,dp(14),0);dots.setBackground(Apple.corners(c,Apple.PANEL,6,18,18,18));dots.setContentDescription("她正在输入");
        for(int i=0;i<3;i++){View dot=new View(c);dot.setBackground(Apple.shape(c,Apple.SECONDARY,4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(7),dp(7));if(i>0)p.leftMargin=dp(5);dots.addView(dot,p);
            if(UiMotion.enabled()){AlphaAnimation pulse=new AlphaAnimation(1f,.25f);pulse.setDuration(480);pulse.setStartOffset(i*160L);pulse.setRepeatMode(Animation.REVERSE);pulse.setRepeatCount(Animation.INFINITE);dot.startAnimation(pulse);}}
        line.addView(dots,new LinearLayout.LayoutParams(-2,dp(38)));rows.addView(line,new LinearLayout.LayoutParams(-1,-2));typing=line;
    }
    /** Long-press: copy, and for your own words, ask her to remember them without typing 「记住：」. */
    void bubbleMenu(View anchor,String text,boolean user){
        PopupMenu menu=new PopupMenu(c,anchor);menu.getMenu().add(0,1,0,"复制");if(user)menu.getMenu().add(0,2,1,"让她记住这句");
        menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)copy(text);else rememberLine(text);return true;});menu.show();
    }
    void copy(String text){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("聊天内容",text));Toast.makeText(c,"已复制",0).show();}
    /** Saved like a notebook entry (not pinned). Waits while she is replying: saving then would discard that reply. */
    void rememberLine(String raw){
        if(sending||AgentRuntime.busy()){Toast.makeText(c,"她还在回话，等她说完再记吧",0).show();return;}
        String text=raw.trim().replaceFirst("^记住[:：]\\s*","");
        if(text.length()>240){Toast.makeText(c,"这句有点长，可以去记忆手账写一条精简的",1).show();return;}
        try{AgentRuntime.store().remember(text,false);Toast.makeText(c,"记下了，可以在记忆手账里修改或固定",0).show();}
        catch(Exception e){Toast.makeText(c,e.getMessage()==null?"没记上，请稍后再试":e.getMessage(),1).show();}
    }
    /** Searches everything you said and everything she showed, across the whole history; a result opens the chat there. */
    void search(){
        stopSpeaking();LinearLayout box=column();box.setPadding(dp(18),dp(4),dp(18),dp(8));
        EditText query=new EditText(c);query.setHint("搜你们聊过的话");query.setTextSize(15);query.setTextColor(Apple.INK);query.setHintTextColor(Apple.SECONDARY);query.setSingleLine(true);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        query.setPadding(dp(16),dp(11),dp(16),dp(11));query.setBackground(Apple.shape(c,Apple.FILL,14));query.setContentDescription("搜索聊天");box.addView(query,new LinearLayout.LayoutParams(-1,-2));
        TextView note=Apple.label(c,"会找你说过的话和她展示出来的回复。",12,false);note.setTextColor(Apple.SECONDARY);note.setLineSpacing(dp(2),1);note.setPadding(dp(4),dp(10),dp(4),dp(6));box.addView(note);
        LinearLayout results=column();box.addView(results);ScrollView view=new SpringScrollView(c);view.addView(box);
        AlertDialog found=new Apple.Builder(c).setTitle("搜索聊天").setView(view).setNegativeButton("关闭",null).setPositiveButton("搜索",null).create();
        if(overlay)found.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);found.show();
        Runnable run=()->{String q=query.getText().toString().trim();if(q.isEmpty())return;note.setText("正在找…");results.removeAllViews();
            Chat.executor.execute(()->{JSONArray rows;try{rows=AgentRuntime.store().searchChat(q,SEARCH_LIMIT);}catch(Exception e){rows=null;}final JSONArray hits=rows;
                handler.post(()->{if(found.isShowing())results(found,note,results,q,hits);});});};
        found.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->run.run());
        query.setOnEditorActionListener((v,action,event)->{run.run();return true;});query.requestFocus();
    }
    void results(AlertDialog found,TextView note,LinearLayout results,String q,JSONArray hits){
        if(hits==null){note.setText("聊天记录暂时打不开，稍后再试试。");return;}
        if(hits.length()==0){note.setText("没找到包含「"+q+"」的聊天。换个说法试试，或者用「按日期查看」。");return;}
        note.setText(hits.length()>=SEARCH_LIMIT?"先列出最近的 "+SEARCH_LIMIT+" 条，点一条回到当时的聊天。":"找到 "+hits.length()+" 条，点一条回到当时的聊天。");
        LinearLayout group=GroupedList.group(results);
        for(int i=0;i<hits.length();i++){JSONObject hit=hits.optJSONObject(i);if(hit==null)continue;String reply=hit.optString("reply");
            String line=when(hit,hit.optLong("at"))+"\n你："+HistoryRecall.clip(hit.optString("user"),q,60).replace('\n',' ')+(reply.isEmpty()?"":"\n她："+HistoryRecall.clip(reply,q,60).replace('\n',' '));
            GroupedList.entry(group,line,v->{found.dismiss();jump(hit,q);});}
    }
    /** Opens that day with the found turn and the few that followed it, marked, so it reads in context. */
    void jump(JSONObject hit,String q){
        try{java.time.ZoneId zone=java.time.ZoneId.systemDefault();long at=hit.optLong("at");java.time.LocalDate day=java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate();
            from=day.atStartOfDay(zone).toInstant().toEpochMilli();to=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            // Page keys sort by time; ending the page just after the found turn's key includes it. Up to three later turns follow when the day has them.
            JSONArray after=AgentRuntime.store().historyRange(at+1,to,null,40);String end=hit.getString("pageKey");
            if(after.length()>0&&after.length()<40)end=after.getJSONObject(Math.min(2,after.length()-1)).getString("pageKey");
            anchor=end+"\uffff";focus=hit.getString("id");
            filter.setText("「"+AgentPolicy.clip(q,12)+"」· "+when(hit,at)+" · 点这里回到最新");filter.setVisibility(View.VISIBLE);status.setText("");loadLatest();
        }catch(Exception e){status.setText("没能打开那段聊天，稍后再试试。");}
    }
    void expand(JSONObject turn){
        stopSpeaking();
        TextView full=Apple.label(c,AgentPolicy.replyText(turn.optString("reply")),16,false);full.setLineSpacing(dp(4),1);full.setPadding(dp(20),dp(12),dp(20),dp(12));full.setTextIsSelectable(true);ScrollView view=new SpringScrollView(c);view.addView(full);
        AlertDialog details=new Apple.Builder(c).setTitle("她的完整回复").setView(view).setPositiveButton("看完了",null).create();if(overlay)details.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);details.show();
        try{AgentRuntime.store().shown(turn.getString("id"),ReplySegments.count(turn));turn.put("displayed",ReplySegments.count(turn));render();}catch(Exception e){status.setText("展示状态暂时没保存上");}
    }
    void send(){
        if(sending){AgentRuntime.cancel();return;}String message=input.getText().toString().trim();if(message.isEmpty())return;
        speech.cancel();from=Long.MIN_VALUE;to=Long.MAX_VALUE;anchor=focus=null;filter.setVisibility(View.GONE);PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();draft=message;turnId=null;
        AgentRuntime.Listener listener=new AgentRuntime.Listener(){
            public void saved(String id){turnId=id;}
            public void partial(String text){if(dialog.isShowing())presence.setText("正在组织语言…");}
            public void done(String reply,String emotion){if(!dialog.isShowing())return;from=Long.MIN_VALUE;to=Long.MAX_VALUE;anchor=focus=null;sending=false;send.setText("发送");input.setEnabled(true);sendState();idle();status.setText("");loadLatest();
                PetService.Pet p=pet();if(p!=null)p.clearBubble();if(overlay&&p!=null&&p.visible&&!p.docked){AgentRuntime.express(id,emotion,reply,turnId);}else speech.start(reply);
            }
            public void error(String message){if(!dialog.isShowing())return;sending=false;send.setText("发送");input.setEnabled(true);input.setText(draft);sendState();idle();status.setText(message);loadLatest();PetService.Pet p=pet();if(p!=null)p.clearBubble();}
        };
        if(!AgentRuntime.send(message,false,listener)){status.setText("她还在回上一句，稍等一下哦");return;}
        sending=true;input.setText("");input.setEnabled(false);send.setText("停止");sendState();status.setText("");presence.setText("对方正在输入…");presence.setTextColor(Apple.BLUE);
        if(records.length()==0)rows.removeAllViews();stamp(new JSONObject(),System.currentTimeMillis());message(message,true,null,false);typing(true);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));if(owner!=null&&owner.visible)owner.thinking();
    }
    void stopSpeaking(){speech.cancel();PetService.Pet owner=pet();if(owner!=null)owner.clearBubble();}
    LinearLayout column(){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
    ImageButton icon(int kind,String description){ImageButton b=new ImageButton(c);b.setImageDrawable(new Glyph(c,kind));b.setContentDescription(description);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18656580),null,null));UiMotion.press(b);return b;}
    static String time(JSONObject turn,String format,long at){SimpleDateFormat f=new SimpleDateFormat(format,Locale.CHINA);if(turn.has("timeZone"))f.setTimeZone(TimeZone.getTimeZone(turn.optString("timeZone")));return f.format(new Date(at));}
    int dp(int n){return Apple.dp(c,n);}
    static final class Watcher implements android.text.TextWatcher {
        final Runnable changed;Watcher(Runnable changed){this.changed=changed;}
        public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void onTextChanged(CharSequence s,int a,int b,int n){}public void afterTextChanged(android.text.Editable s){changed.run();}
    }
    /** Header glyphs on a 24-unit grid, drawn in the current ink colour. */
    static final class Glyph extends Drawable {
        static final int BACK=0,MORE=1;final int size,kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(Context c,int kind){size=Apple.dp(c,24);this.kind=kind;setBounds(0,0,size,size);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(size/24f,size/24f);p.setColor(Apple.INK);
            if(kind==BACK){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path g=new Path();g.moveTo(15,5);g.lineTo(8,12);g.lineTo(15,19);c.drawPath(g,p);}
            else{p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(5.5f+i*6.5f,12,1.8f,p);}
            c.restore();}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
