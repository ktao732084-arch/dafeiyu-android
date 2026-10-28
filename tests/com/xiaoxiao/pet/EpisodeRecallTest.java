package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.util.*;

/** Whole-conversation recall: weeks-old exchanges come back by meaning-bearing words, and forgetting removes them. */
public final class EpisodeRecallTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static String recalled(JSONArray a){return a.toString();}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);root.mkdirs();AgentStore db=new AgentStore(new File(root,"episodes"));
  String[] chatter={"今天好累啊","哈哈哈你真好玩","晚饭吃了面条","下雨了不想出门","刚开完会","周末想睡懒觉","这个电视剧还挺好看","有点困了","早上地铁好挤","中午随便吃了点"};
  String[][] facts={{"我们家楼下新开了一家重庆火锅，叫老码头，味道很正","那下次带我云吃一口！"},{"我妹妹下个月要去成都读研究生了","哇，替她开心，成都好吃的多。"},{"我最近在学游泳，蛙泳总是沉下去","蛙泳先练腿，慢慢来就好。"}};
  long day=86_400_000L,start=1_760_000_000_000L;int n=0;
  for(int d=0;d<60;d++)for(int k=0;k<6;k++){
   String user=chatter[(d*6+k)%chatter.length],reply="嗯嗯，我在呢。";
   if(d==3&&k==2){user=facts[0][0];reply=facts[0][1];}if(d==9&&k==4){user=facts[1][0];reply=facts[1][1];}if(d==20&&k==1){user=facts[2][0];reply=facts[2][1];}
   db.commit(db.epoch(),user,reply,"calm",null,false,false,null,null,"turn"+(n++),start+d*day+k*3600_000L,null);}
  long now=start+61*day;JSONObject s=db.snapshot();
  check(s.getJSONArray("turns").length()==160&&n==360,"most of the 360 turns are archived, outside the model state");
  long t0=System.nanoTime();JSONArray hot=db.episodes("上次说的那家火锅店叫什么来着",new HashSet<>(),now);long builtMs=(System.nanoTime()-t0)/1_000_000;
  check(recalled(hot).contains("老码头"),"weeks-old hotpot place comes back from the archive: "+hot);
  check(hot.getJSONObject(0).getString("at").startsWith("2025-")&&hot.toString().contains("云吃一口"),"with its date and her reply");
  check(recalled(db.episodes("你妹妹去哪里上学来着",new HashSet<>(),now)).contains("成都读研究生"),"paraphrased question finds the sister's plan");
  check(recalled(db.episodes("游泳",new HashSet<>(),now)).contains("蛙泳"),"a single topic word is enough");
  check(db.episodes("你好呀",new HashSet<>(),now).length()==0,"a greeting recalls nothing");
  check(db.episodes("火锅",Collections.singleton("turn20"),now).toString().indexOf("老码头")<0,"turns in the prompt window or from corrected memories are excluded");
  long t1=System.nanoTime();for(int i=0;i<50;i++)db.episodes("最近游泳怎么样",new HashSet<>(),now);long searchMs=(System.nanoTime()-t1)/1_000_000/50;
  System.out.println("  index of "+n+" turns built in "+builtMs+" ms; search "+searchMs+" ms");
  // Forgetting a memory removes its source turns from recall; clearing chat removes everything.
  JSONObject plan=NativeMemoryEngine.evaluate(db.snapshot(),new JSONArray().put(MemoryLedger.record("swim","我最近在学游泳，蛙泳总是沉下去","turn121",false,start+20*day)),"",now,java.time.ZoneOffset.UTC,null,new AgentTransport.Token()).state;
  db.commit(db.epoch(),"好的","好","calm",null,true,false,null,new MemoryBridge.Result(plan,null,false,"",new JSONArray()),"turn-x",now,null);
  String swimId=null;JSONArray mem=db.snapshot().getJSONArray("memories");for(int i=0;i<mem.length();i++)if(mem.getJSONObject(i).getString("text").contains("蛙泳"))swimId=mem.getJSONObject(i).getString("id");
  check(swimId!=null,"swimming became a memory");db.forget(swimId);
  check(!recalled(db.episodes("游泳",new HashSet<>(),now)).contains("蛙泳"),"forgotten memory's source turn is gone from recall");
  check(!recalled(new AgentStore(new File(root,"episodes")).episodes("游泳",new HashSet<>(),now)).contains("蛙泳"),"and stays gone after a restart rebuilds the index");
  db.clear(false);check(db.episodes("火锅",new HashSet<>(),now).length()==0&&new AgentStore(new File(root,"episodes")).episodes("火锅",new HashSet<>(),now).length()==0,"clearing chat clears recall");
  System.out.println("PASS: "+checks+" episodic recall checks");
 }
}
