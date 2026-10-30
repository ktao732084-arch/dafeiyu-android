package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.File;import java.util.concurrent.*;

/** Local playable board; a model invitation opens this same resumable game. */
final class GomokuPage {
    static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"gomoku");t.setDaemon(true);return t;});
    static java.lang.ref.WeakReference<GomokuPage> current=new java.lang.ref.WeakReference<>(null);
    final Activity activity;final Dialog dialog;final Handler main=new Handler(Looper.getMainLooper());
    final TextView status,remark;final Button again,end;final Board board;Gomoku game;boolean working;
    /** The previewed point: the first tap puts a translucent stone here, a second tap on it plays the move. */
    int pending=-1;
    /** The game whose level is being chosen and since when; her first move waits a little for that answer. */
    String choosing;long choosingSince;
    static final long LEVEL_WAIT_MS=4000;
    static GomokuStore store()throws Exception{return new GomokuStore(new File(Data.app.getFilesDir(),"agent"));}
    static void launch(Context c){if(c instanceof Activity)show((Activity)c);else c.startActivity(new Intent(c,MainActivity.class).putExtra("openGomoku",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    static void show(Activity a){GomokuPage old=current.get();if(old!=null&&old.dialog.isShowing())return;AgentRuntime.interruptProactive();Chat.interruptPresentation("main");GomokuPage page=new GomokuPage(a);current=new java.lang.ref.WeakReference<>(page);ClientPage.show(page.dialog,"大肥鱼-五子棋");page.run(()->store().open());}
    interface Task{Gomoku run()throws Exception;}
    GomokuPage(Activity a){
        activity=a;dialog=ClientPage.create(a);LinearLayout root=new LinearLayout(a);root.setOrientation(1);root.setBackgroundColor(Apple.BACK);root.setPadding(dp(12),dp(8),dp(12),dp(10));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);Button close=button("收起棋盘",v->dialog.dismiss());header.addView(close,new LinearLayout.LayoutParams(-2,dp(44)));
        TextView title=Apple.label(a,"和大肥鱼下五子棋",18,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));Button records=button("对局记录",v->history());header.addView(records,new LinearLayout.LayoutParams(-2,dp(44)));root.addView(header);
        status=Apple.label(a,"正在打开棋局…",15,true);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(6),0,dp(2));root.addView(status);
        remark=Apple.label(a,"你执黑先手，她执白。",13,false);remark.setTextColor(Apple.SECONDARY);remark.setGravity(Gravity.CENTER);remark.setPadding(0,dp(2),0,dp(4));root.addView(remark);
        FrameLayout frame=new FrameLayout(a);board=new Board(a);frame.addView(board,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        TextView rules=Apple.label(a,"15路自由规则 · 连成五子或以上获胜 · 无禁手 · 离线可玩",12,false);rules.setTextColor(Apple.SECONDARY);rules.setGravity(Gravity.CENTER);rules.setPadding(0,dp(5),0,dp(5));root.addView(rules);
        LinearLayout actions=new LinearLayout(a);again=button("重新开局",v->restart());end=button("结束本局",v->finish());actions.addView(again,new LinearLayout.LayoutParams(0,dp(44),1));actions.addView(end,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(actions);
        dialog.setContentView(root);dialog.setOnDismissListener(d->{main.removeCallbacksAndMessages(null);if(current.get()==this)current.clear();});
    }
    int dp(int n){return Apple.dp(activity,n);}
    Button button(String title,View.OnClickListener click){Button b=new Button(activity);b.setText(title);b.setTextSize(13);Apple.button(b,false);b.setOnClickListener(click);return b;}
    void run(Task task){
        if(working)return;working=true;render();WORK.execute(()->{Gomoku g=null;Exception error=null;try{g=task.run();}catch(Exception e){error=e;}
            // A finished game may complete the chapter goal (play or win one); growth reads the saved results itself.
            if(g!=null&&g.over()){GrowthStore growth=GrowthStore.quiet();if(growth!=null)try{growth.sync();}catch(Exception e){Data.log("main","成长记录暂未同步");}}
            final Gomoku result=g;final Exception failure=error;main.post(()->{working=false;if(!dialog.isShowing()||activity.isFinishing()||activity.isDestroyed())return;if(failure!=null){status.setText("棋局没有保存成功");remark.setText(failure.getMessage());again.setEnabled(true);end.setEnabled(false);board.update();return;}game=result;if(pending>=0&&(game.over()||game.turn()!=1||game.board[pending]!=0))pending=-1;render();chooseLevel();if(!game.over()&&game.turn()==2)main.postDelayed(this::computer,450);});});
    }
    /** A tap on the board: preview the point first; tapping the previewed point again plays it. */
    void tap(int p){if(working||game==null||game.over()||game.turn()!=1||game.board[p]!=0)return;if(pending==p){play(p);return;}pending=p;render();}
    void play(int p){if(working||game==null||game.over()||game.turn()!=1||game.board[p]!=0)return;pending=-1;String id=game.id;int rev=game.moves.size();run(()->store().move(id,rev,p,false));}
    void computer(){
        if(!dialog.isShowing()||working||game==null||game.over()||game.turn()!=2)return;
        // Give her own choice of level a moment to arrive before falling back to one picked without the model.
        if(game.level.isEmpty()&&game.id.equals(choosing)&&AgentRuntime.configured()&&System.currentTimeMillis()-choosingSince<LEVEL_WAIT_MS){main.postDelayed(this::computer,400);return;}
        String id=game.id;int rev=game.moves.size();run(()->{GomokuStore s=store();return s.move(id,rev,-1,true,GomokuLevel.fallback(s.context()),"");});
    }
    /** Starts her level choice for a new game once; the result is stored, never shown. */
    void chooseLevel(){
        if(game==null||game.over()||!game.level.isEmpty()||game.id.equals(choosing))return;
        choosing=game.id;choosingSince=System.currentTimeMillis();final String id=game.id;
        GomokuLevel.choose(id,()->{if(game!=null&&id.equals(game.id))WORK.execute(()->{try{Gomoku fresh=store().load();main.post(()->{if(game!=null&&fresh!=null&&fresh.id.equals(game.id)&&fresh.moves.size()==game.moves.size())game.level=fresh.level;});}catch(Exception ignored){}});});
    }
    void render(){
        again.setEnabled(!working);end.setEnabled(!working&&game!=null&&!game.over()&&!game.moves.isEmpty());board.update();if(game==null)return;
        again.setText(game.over()?"再来一局":"重新开局");
        status.setText(game.over()?Gomoku.label(game.outcome()):game.turn()==1?"你执黑 · 轮到你落子":"她执白 · 正在想下一步…");
        String line=game.over()?game.winner==1?"这局你赢啦，我记下了。":game.winner==2?"这局是我赢了，想再来一盘吗？":game.abandoned?"这局先到这里，不算谁输。":"势均力敌，平局也记下啦。":pending>=0?"预下在 "+Gomoku.coordinate(pending)+" · 再点一次落子，点别处换位置":"第 "+(game.moves.size()+1)+" 手 · 点一下预下，再点一次落子";
        remark.setText(line);
    }
    void restart(){if(working)return;pending=-1;if(game!=null&&!game.over()&&!game.moves.isEmpty())new Apple.Builder(activity).setTitle("结束这局，重新开始？").setMessage("当前这局会记为主动结束，不计胜负。").setNegativeButton("继续下",null).setPositiveButton("重新开局",(d,w)->run(()->store().fresh())).show();else run(()->store().fresh());}
    void finish(){if(working||game==null||game.over())return;String id=game.id;new Apple.Builder(activity).setTitle("结束这局？").setMessage("记为主动结束，不会算你输。只想暂时离开，可以用收起棋盘。").setNegativeButton("继续下",null).setPositiveButton("结束本局",(d,w)->run(()->store().finish(id))).show();}
    void history(){WORK.execute(()->{String text;try{JSONObject s=store().context();StringBuilder b=new StringBuilder("你赢 "+s.getInt("user_wins")+" 局 · 她赢 "+s.getInt("pet_wins")+" 局 · 平局 "+s.getInt("draws")+" 局\n主动结束 "+s.getInt("abandoned")+" 局，不计胜负\n\n最近对局：\n");JSONArray rows=s.getJSONArray("recent_results");if(rows.length()==0)b.append("还没有结束的棋局。\n");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA);f.setTimeZone(java.util.TimeZone.getTimeZone(row.getString("timeZone")));b.append(f.format(new java.util.Date(row.getLong("finishedAt")))).append("  ").append(Gomoku.label(row.getString("outcome"))).append(" · ").append(row.getInt("moves")).append("手\n");}if(s.optInt("unreadable")>0)b.append("部分记录暂不可读，以上统计不完整。\n");b.append("\n完整棋谱保存在本机。开启记忆时，她聊天会参考战绩和最近8局。忘记全部对话和记忆会一起清除棋局。");text=b.toString();}catch(Exception e){text="对局记录暂时打不开："+e.getMessage();}final String shown=text;main.post(()->{if(dialog.isShowing()&&!activity.isFinishing())new Apple.Builder(activity).setTitle("五子棋对局记录").setMessage(shown).setPositiveButton("知道了",null).show();});});}
    final class Board extends android.view.ViewGroup {
        final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final View[] cells=new View[Gomoku.CELLS];float margin,step,start;
        Board(Context c){super(c);setWillNotDraw(false);setContentDescription("15路五子棋棋盘");for(int i=0;i<cells.length;i++){final int p=i;View cell=new View(c);cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);cell.setOnClickListener(v->tap(p));cells[i]=cell;addView(cell);}update();}
        void update(){for(int i=0;i<cells.length;i++){int color=game==null?0:game.board[i];cells[i].setContentDescription((color==1?"黑子 ":color==2?"白子 ":i==pending?"已预下 ":"预下 ")+Gomoku.coordinate(i)+(color==0&&i==pending?"，再点一次落子":""));cells[i].setEnabled(!working&&game!=null&&!game.over()&&game.turn()==1&&color==0);}invalidate();}
        protected void onMeasure(int w,int h){int size=Math.min(MeasureSpec.getSize(w),MeasureSpec.getSize(h));setMeasuredDimension(size,size);margin=dp(18);step=Math.max(1,(size-2*margin)/Gomoku.SIZE);start=margin+step/2;for(View v:cells)v.measure(MeasureSpec.makeMeasureSpec((int)Math.ceil(step),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)Math.ceil(step),MeasureSpec.EXACTLY));}
        protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<cells.length;i++){int x=Math.round(margin+(i%15)*step),y=Math.round(margin+(i/15)*step);cells[i].layout(x,y,Math.round(x+step),Math.round(y+step));}}
        protected void onDraw(Canvas c){
            pen.setColor(Apple.dark?0xffb69867:0xffe9cf9f);c.drawRoundRect(margin/2,margin/2,getWidth()-margin/2,getHeight()-margin/2,dp(10),dp(10),pen);
            pen.setColor(0xff665037);pen.setStrokeWidth(dp(1));float last=start+14*step;for(int i=0;i<15;i++){float a=start+i*step;c.drawLine(start,a,last,a,pen);c.drawLine(a,start,a,last,pen);}
            pen.setTextAlign(Paint.Align.CENTER);pen.setTextSize(dp(9));for(int i=0;i<15;i++){c.drawText(String.valueOf((char)('A'+i)),start+i*step,margin/2,pen);c.drawText(""+(i+1),margin/2,start+i*step+dp(3),pen);}
            for(int p:new int[]{3*15+3,3*15+11,7*15+7,11*15+3,11*15+11})c.drawCircle(start+p%15*step,start+p/15*step,dp(2),pen);
            if(game==null)return;for(int i=0;i<225;i++)if(game.board[i]!=0){float x=start+i%15*step,y=start+i/15*step;pen.setColor(0x33000000);c.drawCircle(x+1,y+2,step*.42f,pen);pen.setColor(game.board[i]==1?0xff242a33:0xfffafafa);c.drawCircle(x,y,step*.41f,pen);}
            if(!game.moves.isEmpty()){int p=game.moves.get(game.moves.size()-1);pen.setColor(0xffe96e45);c.drawCircle(start+p%15*step,start+p/15*step,step*.10f,pen);}
            // The previewed point: a translucent black stone with a ring, not yet a move.
            if(pending>=0&&game.board[pending]==0){float x=start+pending%15*step,y=start+pending/15*step;pen.setColor(0x66242a33);c.drawCircle(x,y,step*.41f,pen);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(2));pen.setColor(0xff1670ef);c.drawCircle(x,y,step*.45f,pen);pen.setStyle(Paint.Style.FILL);pen.setStrokeWidth(dp(1));}
        }
    }
}
