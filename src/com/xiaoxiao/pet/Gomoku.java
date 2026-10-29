package com.xiaoxiao.pet;

import org.json.*;
import java.util.*;

/** Freestyle gomoku: black first, any unbroken line of at least five wins, no forbidden moves. */
final class Gomoku {
    static final int SIZE=15,CELLS=SIZE*SIZE;
    final String id;final long startedAt;final String zone;
    final int[] board=new int[CELLS];final ArrayList<Integer> moves=new ArrayList<>();
    int winner;boolean abandoned;long finishedAt;
    Gomoku(){this(UUID.randomUUID().toString(),System.currentTimeMillis(),TimeZone.getDefault().getID());}
    Gomoku(String id,long at,String zone){this.id=id;this.startedAt=at;this.zone=zone;}
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
        for(int p=0;p<CELLS;p++)if(board[p]==0&&wins(p,other))return p;
        int best=-1;double score=-1;
        for(int p=0;p<CELLS;p++)if(board[p]==0){double value=shape(p,color)*1.05+shape(p,other)+(14-Math.abs(p%SIZE-7)-Math.abs(p/SIZE-7))*.01;
            if(value>score){best=p;score=value;}}
        return best;
    }
    double shape(int p,int color){double total=0;int x=p%SIZE,y=p/SIZE;for(int[] d:new int[][]{{1,0},{0,1},{1,1},{1,-1}}){
        int a=count(p,color,d[0],d[1]),b=count(p,color,-d[0],-d[1]),n=1+a+b,open=0;
        int ax=x+(a+1)*d[0],ay=y+(a+1)*d[1],bx=x-(b+1)*d[0],by=y-(b+1)*d[1];
        if(empty(ax,ay))open++;if(empty(bx,by))open++;
        if(open>0)total+=(n>=4?12000:n==3?700:n==2?35:1)*(open==2?3:1);
    }return total;}
    boolean empty(int x,int y){return x>=0&&x<SIZE&&y>=0&&y<SIZE&&board[y*SIZE+x]==0;}
    void abandon(){if(!over()){abandoned=true;finishedAt=System.currentTimeMillis();}}
    JSONObject json()throws Exception{return new JSONObject().put("schema",1).put("id",id).put("startedAt",startedAt).put("timeZone",zone).put("moves",new JSONArray(moves)).put("outcome",outcome()).put("finishedAt",finishedAt);}
    static Gomoku read(JSONObject j)throws Exception{
        String id=j.getString("id");if(!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalArgumentException("棋局编号无效");
        Gomoku g=new Gomoku(id,j.getLong("startedAt"),j.getString("timeZone"));JSONArray a=j.getJSONArray("moves");if(a.length()>CELLS)throw new IllegalArgumentException("棋局步数无效");
        for(int i=0;i<a.length();i++){Object p=a.get(i);if(!(p instanceof Number)||((Number)p).doubleValue()!=((Number)p).intValue()||!g.play(((Number)p).intValue()))throw new IllegalArgumentException("棋局落子记录无效");}
        if("abandoned".equals(j.optString("outcome"))){if(g.over())throw new IllegalArgumentException("棋局结果冲突");g.abandon();}
        if(!g.outcome().equals(j.getString("outcome")))throw new IllegalArgumentException("棋局结果校验失败");
        g.finishedAt=j.optLong("finishedAt");if(g.over()&&g.finishedAt<g.startedAt)throw new IllegalArgumentException("棋局时间无效");return g;
    }
    static String coordinate(int p){return String.valueOf((char)('A'+p%SIZE))+(p/SIZE+1);}
}
