package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.util.*;

/** Chat search and the notebook filter: literal, newest first, only what was said or shown, and gone once deleted. */
public final class UsabilityTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);root.mkdirs();AgentStore db=new AgentStore(new File(root,"search"));
  long day=86_400_000L,start=1_760_000_000_000L;int n=0;
  for(int d=0;d<40;d++)for(int k=0;k<5;k++){
   String user="今天第"+k+"件小事",reply="嗯嗯，我在呢。";
   if(d==2&&k==1){user="楼下新开了一家 Hot Pot 火锅店";reply="下次带我云吃一口！";}
   if(d==30&&k==3){user="又去吃了那家火锅";reply="好吃吗？";}
   if(d==35&&k==0){user="晚上吃什么好";reply="要不吃面条？";}
   db.commit(db.epoch(),user,reply,"calm",null,false,false,null,null,"turn"+(n++),start+d*day+k*3600_000L,null);}
  JSONArray hot=db.searchChat("火锅",40);
  check(hot.length()==2,"both hotpot turns, including the archived one: "+hot);
  check(hot.getJSONObject(0).getString("id").equals("turn153")&&hot.getJSONObject(1).getString("id").equals("turn11"),"newest first");
  check(db.searchChat("hotpot",40).length()==1&&db.searchChat("HOT POT",40).length()==1,"case and spaces are ignored");
  check(db.searchChat("面条",40).length()==1,"her shown reply is searched too");
  check(db.searchChat("小事",3).length()==3,"the limit holds");
  check(db.searchChat("  ",40).length()==0&&db.searchChat("不存在的词",40).length()==0,"blank or missing text finds nothing");
  // The page key lets the chat page open exactly there: a page ending just after it includes the turn.
  JSONObject first=hot.getJSONObject(1);JSONArray page=db.historyRange(Long.MIN_VALUE,Long.MAX_VALUE,first.getString("pageKey")+"￿",20);
  check(page.length()==12&&page.getJSONObject(page.length()-1).getString("id").equals("turn11"),"page ends at the found turn: "+page.length());
  db.clear(false);check(db.searchChat("火锅",40).length()==0,"clearing chat clears search");
  // Notebook: newest first, hand-written vs captured, text filter.
  JSONArray memories=new JSONArray().put(MemoryLedger.record("a","我喜欢蓝色","你手动保存",true,1)).put(MemoryLedger.record("b","我妹妹在成都读书","turn5",false,2))
   .put(MemoryLedger.record("c","我不吃香菜","你手动修改",false,3)).put(MemoryLedger.record("d","最近在学蓝调吉他","turn9",false,4));
  check(ids(MemoryLedger.browse(memories,"",MemoryLedger.ALL)).equals("dcba"),"newest first");
  check(ids(MemoryLedger.browse(memories,"",MemoryLedger.MANUAL)).equals("ca"),"only hand-written");
  check(ids(MemoryLedger.browse(memories,"",MemoryLedger.CAPTURED)).equals("db"),"only captured from chat");
  check(ids(MemoryLedger.browse(memories," 蓝 ",MemoryLedger.ALL)).equals("da")&&ids(MemoryLedger.browse(memories,"蓝",MemoryLedger.CAPTURED)).equals("d"),"text filter combines with kind");
  // Display progress is applied in memory at once and written by the background writer; a later full save keeps it.
  File dir=new File(root,"display");AgentStore shown=new AgentStore(dir);
  shown.commit(shown.epoch(),"你好","嗯嗯我在呢。","calm",null,false,false,null,null,"p1",start,null,true);
  check(turn(shown,"p1").getInt("displayed")==0,"waits for display");
  long t0=System.nanoTime();shown.shown("p1",1);long shownMs=(System.nanoTime()-t0)/1_000_000;
  check(turn(shown,"p1").getInt("displayed")==1,"visible to readers immediately");
  shown.commit(shown.epoch(),"再见","拜拜。","calm",null,false,false,null,null,"p2",start+1000,null,true);shown.awaitWrites();
  AgentStore reopened=new AgentStore(dir);check(turn(reopened,"p1").getInt("displayed")==1&&turn(reopened,"p2")!=null,"a full save after it keeps the display progress");
  shown.shown("p2",1);shown.awaitWrites();check(turn(new AgentStore(dir),"p2").getInt("displayed")==1,"the background write reaches the disk");
  System.out.println("  shown() on the calling thread: "+shownMs+" ms");
  // The archive listing is read once and kept in step with writes and deletions.
  AgentStore paged=new AgentStore(new File(root,"archive"));
  for(int i=0;i<300;i++)paged.commit(paged.epoch(),"话"+i,"好"+i,"calm",null,false,false,null,null,"a"+i,start+i*60_000L,null,false);
  check(count(paged)==300,"all turns page through the cached listing");
  File[] files=new File(root,"archive/chat-history").listFiles((d,name)->name.endsWith(".json"));check(files.length==140&&files[0].delete(),"older turns are archived files");
  check(count(paged)==299,"a file removed behind its back is skipped, not an error");
  check(count(new AgentStore(new File(root,"archive")))==299,"a restart lists the folder again");
  paged.clear(false);check(count(paged)==0&&count(new AgentStore(new File(root,"archive")))==0,"clearing empties the listing and the folder");
  System.out.println("PASS: "+checks+" chat search, notebook filter and storage checks");
 }
 static JSONObject turn(AgentStore db,String id)throws Exception{JSONArray t=db.snapshot().getJSONArray("turns");for(int i=0;i<t.length();i++)if(id.equals(t.getJSONObject(i).optString("id")))return t.getJSONObject(i);return null;}
 static int count(AgentStore db)throws Exception{int total=0;String before=null;while(true){JSONArray page=db.historyRange(Long.MIN_VALUE,Long.MAX_VALUE,before,40);if(page.length()==0)return total;total+=page.length();before=page.getJSONObject(0).getString("pageKey");}}
 static String ids(List<JSONObject> rows){StringBuilder b=new StringBuilder();for(JSONObject r:rows)b.append(r.optString("id"));return b.toString();}
}
