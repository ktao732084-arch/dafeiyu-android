package com.xiaoxiao.pet;
import org.json.*;import java.nio.file.*;import java.util.*;
public final class TouchReplyTest {
 static int checks;static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static void add(TouchReply.Batch b,int n,long start,long epoch)throws Exception{for(int i=0;i<n;i++)b.add("main","tap","用户轻轻点了你",100000+start+i,start+i*100,epoch);}
 public static void main(String[] args)throws Exception{
  TouchReply.Batch b=new TouchReply.Batch();add(b,2,0,1);check(!b.ready(3000),"two gestures are not a burst");b.add("main","drag","用户挪动了你",100200,200,1);
  check(!b.ready(1999)&&b.ready(2000),"wait until gestures settle");TouchReply.Request first=b.take(2000);check(first!=null&&first.evidence.getInt("count")==3&&first.evidence.getJSONArray("events").length()==3,"actual three gestures provided");check(b.take(4000)==null,"one request per burst");check(b.current(first),"request initially current");
  add(b,3,3000,1);check(!b.current(first),"new gestures invalidate the old response");check(!b.ready(6000)&&b.ready(22000),"short anti-loop gap without a daily quota");check(b.take(22000)!=null,"later burst may respond");
  add(b,3,25000,1);check(b.expired(55201),"stale gestures are not replayed");b.clear();check(!b.current(first)&&b.count==0,"clear invalidates pending requests");
  add(b,2,60000,1);add(b,1,61000,2);check(b.count==1,"different memory epoch starts fresh");add(b,1,80000,2);check(b.count==1,"separate sparse gestures do not build a stale burst");
  b.clear();add(b,40,90000,2);check(b.count==40&&b.events.length()==TouchReply.MAX_EVENTS,"bounded prompt evidence");
  check(TouchReply.text("[安静陪伴]")==null&&TouchReply.text("")==null,"quiet does not create a bubble");check(TouchReply.text(String.join("",Collections.nCopies(90,"字")))==null,"long response not flashed");check(TouchReply.text("看看 https://example.com")==null,"structured response not flashed");check("我在呢，慢点戳。".equals(TouchReply.text("[tool] 我在呢，慢点戳。")),"reply prefix normalized");
  JSONObject state=AgentStore.empty(0);for(int i=0;i<230;i++)Initiative.interaction(state,"挪动",1L+i,"drag","main");check(state.getJSONArray("interactions").length()==200,"interaction history remains capped at 200");check("drag".equals(state.getJSONArray("interactions").getJSONObject(199).getString("kind")),"structured gesture kind retained");
  AgentStore db=new AgentStore(Files.createTempDirectory("touch-store").toFile());long now=System.currentTimeMillis();db.commit(db.epoch(),"你好","你好呀","calm",null,false,false,null,null,"old",now,null,false,7);JSONObject before=db.snapshot();
  check(db.commit(db.epoch(),"","我在呢，慢点戳。","playful",null,false,true,null,null,"reaction",now+1,null,true,null,null,2,"",first.evidence),"reaction stored");JSONObject after=db.snapshot(),turn=after.getJSONArray("turns").getJSONObject(1);
  check(after.getInt("userTurns")==before.getInt("userTurns")&&after.getLong("lastUser")==before.getLong("lastUser"),"reaction is not a user message");check(after.getLong("proactiveNextAt")==before.getLong("proactiveNextAt"),"reaction does not change idle schedule");check(turn.has("interaction")&&turn.getString("user").isEmpty()&&turn.getInt("displayed")==0,"reaction has origin and waits for display");
  db.shown("reaction",1);check(Initiative.context(db.snapshot(),now+2).getInt("unanswered_proactive_messages")==0,"touch reply is not unanswered unsolicited outreach");PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.now=now+2;JSONArray messages=PromptBuilder.build(in,"继续",false,db.snapshot(),null);check(messages.toString().contains(TouchReply.HISTORY_EVENT),"history distinguishes physical input from idle outreach");
  check(db.conversationRange(Long.MIN_VALUE,Long.MAX_VALUE,null,20).length()==1,"chat omits reaction while keeping normal conversation");
  check(db.historyRange(Long.MIN_VALUE,Long.MAX_VALUE,null,1).getJSONObject(0).getString("id").equals("reaction")&&messages.toString().contains("慢点戳"),"stored and displayed reaction remains available to the model");
  check(db.searchChat("慢点戳",40).length()==0,"reaction cannot reappear through chat search");
  JSONArray request=TouchReply.prompt(new JSONArray().put(new JSONObject().put("role","system").put("content","persona")).put(new JSONObject().put("role","user").put("content","<背景>kept</背景>旧的主动指令")),first);
  check(request.getJSONObject(0).getString("content").contains("只读取reply")&&!request.getJSONObject(1).getString("content").contains("旧的主动指令")&&request.toString().contains("interaction_event"),"prompt scopes the real gesture response without rescheduling");
  filteredArchive();System.out.println("PASS: "+checks+" touch-reply checks");
 }
 static JSONObject row(String id,long at,boolean interaction)throws Exception{JSONObject t=new JSONObject().put("id",id).put("at",at).put("user",interaction?"":"用户原话").put("reply",interaction?"摸摸回应":"普通聊天");if(interaction)t.put("interaction",new JSONObject().put("count",3));return t;}
 static void filteredArchive()throws Exception{
  java.io.File dir=Files.createTempDirectory("touch-archive").toFile();ChatArchive a=new ChatArchive(dir);a.put(row("a",10,false));a.put(row("b",20,false));for(int i=0;i<25;i++)a.put(row("archived"+i,30+i,true));
  JSONArray recent=new JSONArray().put(row("c",60,false)).put(row("d",70,false));for(int i=0;i<25;i++)recent.put(row("recent"+i,80+i,true));
  a=new ChatArchive(dir);JSONArray first=a.page(recent,null,3,Long.MIN_VALUE,Long.MAX_VALUE,true);check(first.length()==3&&first.getJSONObject(0).getString("id").equals("b")&&first.getJSONObject(2).getString("id").equals("d"),"page filled across archived and recent hidden reactions");
  JSONArray older=a.page(recent,first.getJSONObject(0).getString("pageKey"),3,Long.MIN_VALUE,Long.MAX_VALUE,true);check(older.length()==1&&older.getJSONObject(0).getString("id").equals("a"),"older cursor does not lose ordinary chats");
  check(a.page(recent,null,20,80,110,true).length()==0,"reaction-only date range is empty conversation");
  check(a.page(recent,null,1).getJSONObject(0).has("interaction")&&a.files().length==27,"filter never deletes persisted interaction evidence");
 }
}
