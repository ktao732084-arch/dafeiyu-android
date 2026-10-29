package com.xiaoxiao.pet;

import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;

/** One durable current board and one immutable result per game. No network, credentials or fabricated chat turns. */
final class GomokuStore {
    final File dir,current,results;
    GomokuStore(File agentDir)throws Exception{dir=new File(agentDir,"gomoku");results=new File(dir,"results");current=new File(dir,"current.json");if(!results.isDirectory()&&!results.mkdirs())throw new IOException("无法创建棋局目录");}
    private JSONObject read(File f)throws Exception{if(f.length()>64000)throw new IOException("棋局文件过大");return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}
    private void write(File f,JSONObject data)throws Exception{ChatArchive.write(f,data.toString().getBytes(StandardCharsets.UTF_8));}
    Gomoku load()throws Exception{synchronized(GomokuStore.class){if(!current.exists())return null;Gomoku g=Gomoku.read(read(current));File result=new File(results,g.id+".json");if(result.exists())return Gomoku.read(read(result));if(g.over())write(result,g.json());return g;}}
    Gomoku open()throws Exception{synchronized(GomokuStore.class){Gomoku g=load();return g==null?fresh():g;}}
    Gomoku fresh()throws Exception{synchronized(GomokuStore.class){Gomoku old=load();if(old!=null&&!old.over()&&!old.moves.isEmpty()){old.abandon();save(old);}Gomoku g=new Gomoku();save(g);return g;}}
    private void save(Gomoku g)throws Exception{if(g.over()){File f=new File(results,g.id+".json");if(!f.exists())write(f,g.json());}write(current,g.json());}
    Gomoku move(String id,int revision,int point,boolean computer)throws Exception{synchronized(GomokuStore.class){Gomoku g=load();if(g==null||!g.id.equals(id)||g.moves.size()!=revision)throw new IOException("棋局已更新，请重新进入");
        if(g.over()||g.turn()!=(computer?2:1))return g;int chosen=computer?g.bestMove():point;
        if(!g.play(chosen))throw new IOException("这里已经有棋子，请换一个交点");save(g);return g;}}
    Gomoku finish(String id)throws Exception{synchronized(GomokuStore.class){Gomoku g=load();if(g==null||!g.id.equals(id))throw new IOException("棋局已更新");g.abandon();save(g);return g;}}
    JSONObject context()throws Exception{synchronized(GomokuStore.class){
        ArrayList<JSONObject> rows=new ArrayList<>();int win=0,loss=0,draw=0,ended=0,unreadable=0;File[] files=results.listFiles((d,n)->n.endsWith(".json"));if(files==null)throw new IOException("对局记录暂不可读");
        // Recover a terminal current board whose result write was interrupted before the archive existed.
        Gomoku active=load();files=results.listFiles((d,n)->n.endsWith(".json"));if(files==null)throw new IOException("对局记录暂不可读");
        for(File f:files)try{Gomoku g=Gomoku.read(read(f));if(!g.over())throw new IOException();switch(g.outcome()){case "user_win":win++;break;case "pet_win":loss++;break;case "draw":draw++;break;case "abandoned":ended++;break;}
            rows.add(new JSONObject().put("id",g.id).put("startedAt",g.startedAt).put("finishedAt",g.finishedAt).put("timeZone",g.zone).put("finished_local",Initiative.localTime(g.finishedAt,g.zone)).put("moves",g.moves.size()).put("outcome",g.outcome()));
        }catch(Exception broken){unreadable++;}
        rows.sort((a,b)->Long.compare(b.optLong("finishedAt"),a.optLong("finishedAt")));JSONArray recent=new JSONArray();for(int i=0;i<Math.min(8,rows.size());i++)recent.put(rows.get(i));
        JSONObject out=new JSONObject().put("game","gomoku").put("user_wins",win).put("pet_wins",loss).put("draws",draw).put("abandoned",ended).put("unreadable",unreadable).put("recent_results",recent);
        if(active!=null&&!active.over())out.put("current",new JSONObject().put("id",active.id).put("moves",active.moves.size()).put("startedAt",active.startedAt).put("turn",active.turn()==1?"user":"pet"));return out;
    }}
    void clear()throws Exception{synchronized(GomokuStore.class){File[] files=results.listFiles();if(files==null)throw new IOException("无法清除棋局记录");for(File f:files)if(f.isFile())Files.delete(f.toPath());Files.deleteIfExists(current.toPath());Files.deleteIfExists(new File(current+".tmp").toPath());}}
}
