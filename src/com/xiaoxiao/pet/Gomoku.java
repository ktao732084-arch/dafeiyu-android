package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/**
 * Freestyle gomoku: black first, any unbroken line of at least five wins, no forbidden moves. Her strength is
 * chosen once per game (by the model from her personality and mood, or by fallbackLevel) and saved with the game,
 * so a resumed board keeps the same opponent. The level is never shown to the user; she sees it in her records.
 */
final class Gomoku {
    static final int SIZE=15,CELLS=SIZE*SIZE;
    /**
     * Her levels, weakest first: 菜鸟 plays like an ordinary person, 大师 searches runs of fours and threes.
     * Games saved before levels existed are read as 中等; a new game is "" until a level is chosen.
     */
    static final String ROOKIE="rookie",LOWER="lower",MIDDLE="middle",UPPER="upper",MASTER="master";
    static final List<String> LEVELS=Collections.unmodifiableList(Arrays.asList(ROOKIE,LOWER,MIDDLE,UPPER,MASTER));
    static Random random=new Random();
    final String id;final long startedAt;final String zone;
    String level,levelReason="",levelBy="";
    final int[] board=new int[CELLS];final ArrayList<Integer> moves=new ArrayList<>();
    int winner;boolean abandoned;long finishedAt;
    Gomoku(){this(MIDDLE);}
    Gomoku(String level){this(UUID.randomUUID().toString(),System.currentTimeMillis(),TimeZone.getDefault().getID(),level);}
    Gomoku(String id,long at,String zone){this(id,at,zone,MIDDLE);}
    Gomoku(String id,long at,String zone,String level){if(!level.isEmpty()&&!LEVELS.contains(level))throw new IllegalArgumentException("棋力等级无效");this.id=id;this.startedAt=at;this.zone=zone;this.level=level;}
    /**
     * Without the model: a level from her personality, nudged down when she is worried about the user or the user
     * lost the last three games, and up when the user won the last three. recent is newest first, as in context().
     */
    static String fallbackLevel(String persona,String emotion,JSONArray recent,Random r){
        int[] range;switch(persona==null?"":persona){
            case "gentle":range=new int[]{0,1};break;case "classic":range=new int[]{2,3};break;case "energetic":range=new int[]{2,3};break;
            case "steady":range=new int[]{2,4};break;case "quirky":range=new int[]{0,4};break;default:range=new int[]{1,3};}
        int pick=range[0]+r.nextInt(range[1]-range[0]+1);
        if("concerned".equals(emotion)||"sleepy".equals(emotion))pick--;
        int userWins=0,userLosses=0;for(int i=0;recent!=null&&i<recent.length()&&userWins+userLosses<3;i++){String o=recent.optJSONObject(i)==null?"":recent.optJSONObject(i).optString("outcome");if("user_win".equals(o))userWins++;else if("pet_win".equals(o))userLosses++;}
        if(userLosses==3)pick--;if(userWins==3)pick++;
        return LEVELS.get(Math.max(0,Math.min(LEVELS.size()-1,pick)));
    }
    /** {level, reason} from the model's answer, or null when it named no known level. */
    static String[] parseChoice(String raw){
        if(raw==null)return null;String json=AgentPolicy.envelope(raw.trim().replaceAll("(?s)^```(?:json)?\\s*|\\s*```$",""));
        if(json==null){int open=raw.indexOf('{'),close=raw.lastIndexOf('}');json=open>=0&&close>open?raw.substring(open,close+1):null;}
        if(json==null)return null;
        try{JSONObject o=new JSONObject(json);String level=o.optString("level").trim().toLowerCase(Locale.ROOT);if(!LEVELS.contains(level))return null;return new String[]{level,AgentPolicy.moodCause(o.optString("reason",""))};}catch(JSONException e){return null;}
    }
    /** Records the level once; a later choice (say, a slow model answer) does not change a game already decided. */
    boolean decide(String chosen,String reason,String by){if(!level.isEmpty()||!LEVELS.contains(chosen))return false;level=chosen;levelReason=reason==null?"":reason;levelBy=by;return true;}
    static String levelLabel(String level){switch(level){case ROOKIE:return "菜鸟";case LOWER:return "中下";case UPPER:return "中上";case MASTER:return "大师";default:return "中等";}}
    int turn(){return moves.size()%2+1;}
    boolean over(){return winner!=0||abandoned||moves.size()==CELLS;}
    String outcome(){return abandoned?"abandoned":winner==1?"user_win":winner==2?"pet_win":over()?"draw":"playing";}
    static String label(String outcome){switch(outcome){case "user_win":return "你赢了";case "pet_win":return "大肥鱼赢了";case "draw":return "平局";case "abandoned":return "主动结束（不计胜负）";default:return "进行中";}}
    boolean play(int point){
        if(over()||point<0||point>=CELLS||board[point]!=0)return false;
        int color=turn();board[point]=color;moves.add(point);
        if(wins(point,color))winner=color;if(over())finishedAt=System.currentTimeMillis();return true;
    }
    boolean wins(int p,int color){for(int[] d:new int[][]{{1,0},{0,1},{1,1},{1,-1}})if(1+count(p,color,d[0],d[1])+count(p,color,-d[0],-d[1])>=5)return true;return false;}
    int count(int p,int color,int dx,int dy){int x=p%SIZE+dx,y=p/SIZE+dy,n=0;while(x>=0&&x<SIZE&&y>=0&&y<SIZE&&board[y*SIZE+x]==color){n++;x+=dx;y+=dy;}return n;}
    int bestMove(){
        if(over())return -1;int color=turn(),other=3-color;
        for(int p=0;p<CELLS;p++)if(board[p]==0&&wins(p,color))return p;
        // 菜鸟 plays like an ordinary person: now and then it misses a four it should have blocked.
        boolean careless=ROOKIE.equals(level)&&random.nextInt(8)==0;
        if(!careless)for(int p=0;p<CELLS;p++)if(board[p]==0&&wins(p,other))return p;
        List<Integer> options=candidates(color,MASTER.equals(level)?12:10);if(options.isEmpty())return -1;
        switch(level.isEmpty()?MIDDLE:level){
            // Good-looking moves, not always the best one: 菜鸟 among the first five, 中下 among the first three.
            case ROOKIE:return casual(options,5);
            case LOWER:return casual(options,3);
            case MIDDLE:return options.get(0);
            case UPPER:{budget=8000;int win=vcf(color,10);if(win>=0)return win;return defendThreats(options,color,5,8,2);}
            default:{
                // 大师: wins through fours, then through open threes, and defends against both.
                budget=16000;int win=vcf(color,16);if(win>=0)return win;
                threats=300;win=vct(color,4);if(win>=0)return win;
                return defendThreats(options,color,8,12,3);
            }
        }
    }
    /** One of the first n options, the better ones more often (weights 1, 1/2, 1/3, …). */
    int casual(List<Integer> options,int n){
        n=Math.min(n,options.size());double total=0;for(int i=0;i<n;i++)total+=1.0/(i+1);
        double r=random.nextDouble()*total;for(int i=0;i<n;i++){r-=1.0/(i+1);if(r<=0)return options.get(i);}return options.get(0);
    }
    /** Among moves safe from the opponent's fours, prefer one that leaves the opponent no open-three win either. */
    int defendThreats(List<Integer> options,int color,int checked,int fours,int threes){
        int fallback=-1;
        for(int i=0;i<Math.min(checked,options.size());i++){int p=options.get(i);board[p]=color;budget=fours*600;boolean loses=vcf(3-color,fours)>=0;
            if(!loses){if(fallback<0)fallback=p;threats=40*threes;boolean pressed=vct(3-color,threes)>=0;if(!pressed){board[p]=0;return p;}}board[p]=0;}
        return fallback>=0?fallback:options.get(0);
    }
    /** Search work left for vcf(); a slow phone never waits on an unbounded sequence. */
    int budget;
    /**
     * Victory by continuous fours: a first move from which color can keep making fours, the opponent each time
     * forced to block the one winning point, until a double four or a five. -1 when none is found in depth fours.
     */
    int vcf(int color,int depth){
        if(depth<=0||budget<=0)return -1;int other=3-color,threat=-1;
        for(int p=0;p<CELLS;p++)if(board[p]==0&&wins(p,other)){threat=p;break;}
        for(int p=0;p<CELLS;p++){
            if(board[p]!=0||(threat>=0&&p!=threat)||--budget<0)continue;
            if(wins(p,color))return p;
            board[p]=color;List<Integer> fives=fives(p,color);boolean found=false;
            if(fives.size()>=2)found=!anyFive(other);
            else if(fives.size()==1){int q=fives.get(0);if(!wins(q,other)){board[q]=other;found=vcf(color,depth-1)>=0;board[q]=0;}}
            board[p]=0;if(found)return p;
        }
        return -1;
    }
    /** Work left for vct(), in positions examined. */
    int threats;
    /**
     * Victory by continuous threats: like vcf() but an open three also counts, the defender answering at any of
     * the points that would stop it becoming an open four. -1 when none is found within depth threats.
     */
    int vct(int color,int depth){
        if(depth<=0||threats<=0)return -1;int other=3-color;
        if(anyFive(other))return -1;
        for(int p:candidates(color,10)){
            if(--threats<0)return -1;if(wins(p,color))return p;
            board[p]=color;boolean found=false;List<Integer> fives=fives(p,color);
            if(fives.size()>=2)found=true;
            else{
                List<Integer> replies=fives.size()==1?fives:openFours(p,color);
                // Against a three the defender may also answer with a four of their own; count those answers too,
                // so only threats that survive every reply are trusted.
                if(fives.isEmpty()&&!replies.isEmpty())for(int q:candidates(other,12))if(!replies.contains(q)){board[q]=other;if(!fives(q,other).isEmpty())replies.add(q);board[q]=0;}
                if(!replies.isEmpty()){found=true;
                    for(int r:replies){board[r]=other;boolean held=!wins(r,other)&&vct(color,depth-1)>=0;board[r]=0;if(!held){found=false;break;}}}
            }
            board[p]=0;if(found)return p;
        }
        return -1;
    }
    /** Points on lines through p where color's next stone would make two fives at once (an open or double four). */
    List<Integer> openFours(int p,int color){
        List<Integer> out=new ArrayList<>();int x=p%SIZE,y=p/SIZE;
        for(int[] d:DIRECTIONS)for(int k=-4;k<=4;k++){int nx=x+k*d[0],ny=y+k*d[1];if(k==0||nx<0||nx>=SIZE||ny<0||ny>=SIZE)continue;int q=ny*SIZE+nx;
            if(board[q]!=0||out.contains(q))continue;board[q]=color;if(fives(q,color).size()>=2)out.add(q);board[q]=0;}
        return out;
    }
    /** Empty points on lines through p where color would now complete five. */
    List<Integer> fives(int p,int color){
        List<Integer> out=new ArrayList<>();int x=p%SIZE,y=p/SIZE;
        for(int[] d:DIRECTIONS)for(int k=-4;k<=4;k++){int nx=x+k*d[0],ny=y+k*d[1];if(k==0||nx<0||nx>=SIZE||ny<0||ny>=SIZE)continue;int q=ny*SIZE+nx;if(board[q]==0&&!out.contains(q)&&wins(q,color))out.add(q);}
        return out;
    }
    boolean anyFive(int color){for(int p=0;p<CELLS;p++)if(board[p]==0&&wins(p,color))return true;return false;}
    // Window scores: a run of five cells holding only one colour is worth W[stones in it].
    static final long[] W={0,1,12,150,6000,1_000_000};
    static final int[][] DIRECTIONS={{1,0},{0,1},{1,1},{1,-1}};
    /**
     * What placing color at empty p changes, summed over the 20 five-cell windows through p, from color's side:
     * its own windows grow and the opponent's windows through p are blocked.
     */
    long delta(int p,int color){
        long change=0;int x0=p%SIZE,y0=p/SIZE;
        for(int[] d:DIRECTIONS)for(int s=-4;s<=0;s++){
            int sx=x0+s*d[0],sy=y0+s*d[1],ex=sx+4*d[0],ey=sy+4*d[1];if(sx<0||sx>=SIZE||ex<0||ex>=SIZE||sy<0||sy>=SIZE||ey<0||ey>=SIZE)continue;
            int mine=0,theirs=0;for(int k=0;k<5;k++){int c=board[(sy+k*d[1])*SIZE+sx+k*d[0]];if(c==color)mine++;else if(c!=0)theirs++;}
            if(theirs==0)change+=W[Math.min(5,mine+1)]-W[mine];else if(mine==0)change+=W[theirs];
        }
        return change;
    }
    /** Empty points within two of a stone (the centre on an empty board), best-looking first (attack plus defence, small random tie-break), at most limit. */
    List<Integer> candidates(int color,int limit){
        boolean[] near=new boolean[CELLS];boolean any=false;
        for(int p=0;p<CELLS;p++)if(board[p]!=0){any=true;int x=p%SIZE,y=p/SIZE;for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){int nx=x+dx,ny=y+dy;if(nx>=0&&nx<SIZE&&ny>=0&&ny<SIZE&&board[ny*SIZE+nx]==0)near[ny*SIZE+nx]=true;}}
        List<Integer> out=new ArrayList<>();if(!any){out.add(CELLS/2);return out;}
        final long[] value=new long[CELLS];for(int p=0;p<CELLS;p++)if(near[p]){value[p]=delta(p,color)+delta(p,3-color)+random.nextInt(3);out.add(p);}
        out.sort((a,b)->Long.compare(value[b],value[a]));return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    void abandon(){if(!over()){abandoned=true;finishedAt=System.currentTimeMillis();}}
    JSONObject json()throws Exception{return new JSONObject().put("schema",1).put("id",id).put("startedAt",startedAt).put("timeZone",zone).put("level",level).put("levelReason",levelReason).put("levelBy",levelBy).put("moves",new JSONArray(moves)).put("outcome",outcome()).put("finishedAt",finishedAt);}
    static Gomoku read(JSONObject j)throws Exception{
        String id=j.getString("id");if(!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalArgumentException("棋局编号无效");
        Gomoku g=new Gomoku(id,j.getLong("startedAt"),j.getString("timeZone"),j.has("level")?j.getString("level"):MIDDLE);g.levelReason=j.optString("levelReason","");g.levelBy=j.has("level")?j.optString("levelBy",""):"legacy";JSONArray a=j.getJSONArray("moves");if(a.length()>CELLS)throw new IllegalArgumentException("棋局步数无效");
        for(int i=0;i<a.length();i++){Object p=a.get(i);if(!(p instanceof Number)||((Number)p).doubleValue()!=((Number)p).intValue()||!g.play(((Number)p).intValue()))throw new IllegalArgumentException("棋局落子记录无效");}
        if("abandoned".equals(j.optString("outcome"))){if(g.over())throw new IllegalArgumentException("棋局结果冲突");g.abandon();}
        if(!g.outcome().equals(j.getString("outcome")))throw new IllegalArgumentException("棋局结果校验失败");
        g.finishedAt=j.optLong("finishedAt");if(g.over()&&g.finishedAt<g.startedAt)throw new IllegalArgumentException("棋局时间无效");return g;
    }
    static String coordinate(int p){return String.valueOf((char)('A'+p%SIZE))+(p/SIZE+1);}
}
