package com.xiaoxiao.pet;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;

public final class GomokuTest {
    static int checks;static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static Gomoku win(int[] points,int color)throws Exception{Gomoku g=new Gomoku();for(int i=0;i<5;i++){if(color==2)check(g.play(210+i*2),"black filler");check(g.play(points[i]),"winning line point");if(color==1&&i<4)check(g.play(210+i*2),"white filler");}return g;}
    public static void main(String[] args)throws Exception{
        for(int[] line:new int[][]{{0,1,2,3,4},{0,15,30,45,60},{0,16,32,48,64},{60,46,32,18,4}}){Gomoku g=win(line,1);check(g.winner==1&&g.over(),"all four winning directions");check(!g.play(100),"no moves after result");check(Gomoku.read(g.json()).winner==1,"validated winning replay");}
        check(win(new int[]{0,1,2,3,4},2).winner==2,"white victory is not attributed to user");
        Gomoku overline=new Gomoku();for(int p:new int[]{0,1,2,4,5})overline.board[p]=1;check(overline.play(3)&&overline.winner==1,"six stones win under freestyle rules");
        Gomoku edge=new Gomoku();for(int p:new int[]{13,14,15,16,17})edge.board[p]=1;check(!edge.wins(15,1),"rows never wrap across edge");
        Gomoku g=new Gomoku();check(!g.play(-1)&&!g.play(225),"bounds");check(g.play(112)&&!g.play(112)&&g.turn()==2,"occupied point and alternating turn");
        for(int p=0;p<4;p++)g.board[p]=2;check(g.bestMove()==4,"computer takes immediate win");g=new Gomoku();g.play(112);for(int p=0;p<4;p++)g.board[p]=1;check(g.bestMove()==4,"computer blocks immediate loss");
        Gomoku draw=new Gomoku();ArrayList<Integer> black=new ArrayList<>(),white=new ArrayList<>();for(int p=0;p<225;p++)(((p%15+2*(p/15))%4<2)?black:white).add(p);
        for(int i=0;i<113;i++){check(draw.play(black.get(i)),"draw black legal");if(i<112)check(draw.play(white.get(i)),"draw white legal");}check(draw.over()&&draw.winner==0&&"draw".equals(draw.outcome()),"full-board draw");
        check(Gomoku.read(draw.json()).moves.size()==225,"draw survives validation");
        JSONObject corrupt=new Gomoku().json().put("moves",new JSONArray().put(0).put(0));boolean rejected=false;try{Gomoku.read(corrupt);}catch(Exception expected){rejected=true;}check(rejected,"duplicate moves rejected instead of inventing board");
        File dir=Files.createTempDirectory("gomoku-store").toFile();GomokuStore store=new GomokuStore(dir);g=store.open();String id=g.id;g=store.move(id,0,112,false);check(g.turn()==2,"human move persisted before bot");g=new GomokuStore(dir).load();check(g.moves.size()==1&&g.board[112]==1,"restart resumes pending bot turn");g=store.move(id,1,-1,true);check(g.turn()==1&&g.moves.size()==2,"bot completes restored turn");
        rejected=false;try{store.move(id,0,10,false);}catch(Exception expected){rejected=true;}check(rejected&&store.load().moves.size()==2,"stale UI cannot duplicate a move");
        store.finish(id);JSONObject ctx=store.context();check(ctx.getInt("abandoned")==1&&ctx.getInt("user_wins")==0&&ctx.getInt("pet_wins")==0,"abandon is not defeat");store.finish(id);check(store.context().getInt("abandoned")==1,"result counted once");
        Gomoku winning=win(new int[]{0,1,2,3,4},1);ChatArchive.write(new File(store.results,winning.id+".json"),winning.json().toString().getBytes("UTF-8"));
        ctx=store.context();check(ctx.getInt("user_wins")==1&&ctx.getJSONArray("recent_results").length()==2,"actual result enters context stats");check(ctx.toString().contains("finished_local")&&ctx.toString().contains("startedAt"),"both timestamps retained");
        PromptBuilder.Inputs input=new PromptBuilder.Inputs();input.now=System.currentTimeMillis();input.remember=true;input.games=ctx;input.persona="固定人格";JSONArray request=PromptBuilder.build(input,"刚才谁赢了",false,AgentStore.empty(0),null);check(request.toString().contains("game_results")&&request.toString().contains(winning.id),"model receives authoritative game results");input.remember=false;check(!PromptBuilder.build(input,"你好",false,AgentStore.empty(0),null).toString().contains(winning.id),"memory off excludes game history");
        Gomoku active=store.fresh();store.clear();rejected=false;try{store.move(active.id,0,0,false);}catch(Exception expected){rejected=true;}check(rejected,"cleared game cannot be resurrected by delayed UI");check(store.context().getInt("user_wins")==0,"clear removes all game results");
        // Deterministic legal human choices, useful for full-board UI smoke; no direct device state editing.
        g=new Gomoku();ArrayList<String> script=new ArrayList<>();while(!g.over()){int p=0;while(g.board[p]!=0)p++;script.add(Gomoku.coordinate(p));check(g.play(p),"sample human move");if(!g.over()){int bot=g.bestMove();check(bot>=0&&g.board[bot]==0&&g.play(bot),"sample bot is legal");}}check(g.moves.size()<=225,"sample game terminates");
        System.out.println("UI_SMOKE_MOVES: "+script+"; outcome="+g.outcome());System.out.println("PASS: "+checks+" gomoku rules, persistence and context checks");
    }
}
