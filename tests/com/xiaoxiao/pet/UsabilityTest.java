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
  System.out.println("PASS: "+checks+" chat search and notebook filter checks");
 }
 static String ids(List<JSONObject> rows){StringBuilder b=new StringBuilder();for(JSONObject r:rows)b.append(r.optString("id"));return b.toString();}
}
