package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

public final class ReplyHistoryTest {
    static int checks;
    static void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
    static void turn(AgentStore db,String id,long at,String reply)throws Exception {check(db.commit(db.epoch(),"用户"+id,reply,"calm",null,false,false,null,null,id,at,null,true),"turn committed");}
    static final class Timer implements SpeechQueue.Clock {List<Runnable> tasks=new ArrayList<>();List<Long> delays=new ArrayList<>();public void later(Runnable r,long delay){tasks.add(r);delays.add(delay);}public void remove(Runnable r){tasks.remove(r);}void next(){if(!tasks.isEmpty())tasks.remove(0).run();}}
    static final String REPLY="今天的风很舒服，我就在这里安静陪你待一会儿，听你慢慢说今天发生了什么。等你忙完了，我们再一起聊点轻松的事情，或者想想周末有什么小期待。还有一件刚刚想到的特别重要的小事，我会等你有空的时候再慢慢告诉你。";
    public static void main(String[] args)throws Exception {
        for(String text:new String[]{"好呀",REPLY,"你好👩‍💻！今天很开心。",REPLY+"https://example.com/a?b=1", "```java\nint a = 1;\n```"}){List<String> parts=ReplySegments.split(text);check(parts.size()>=1&&parts.size()<=3,"bounded segments");check(String.join("",parts).equals(text),"lossless segmentation");}
        check(ReplySegments.split(REPLY).size()==3,"natural three parts");check(ReplySegments.previewOnly(REPLY+REPLY+REPLY),"long answer links to complete history");
        Timer timer=new Timer();List<Integer> shown=new ArrayList<>();boolean[] visible={true};SpeechQueue q=new SpeechQueue(timer,new SpeechQueue.Sink(){public boolean visible(){return visible[0];}public void show(String t,int count,boolean preview){shown.add(count);}public void stop(int count){}});
        q.start(REPLY);check(shown.equals(Arrays.asList(1)),"first part immediate");Runnable stale=timer.tasks.get(0);q.cancel();stale.run();check(shown.size()==1,"cancelled callback cannot speak");
        q.start(REPLY);timer.next();check(shown.get(shown.size()-1)==2,"second part after readable pause");visible[0]=false;timer.next();check(shown.get(shown.size()-1)==2,"hidden surface stops remaining parts");
        visible[0]=true;q.start(REPLY+REPLY+REPLY);check(shown.get(shown.size()-1)==0,"preview does not claim full text shown");q.cancel();
        for(long delay:timer.delays)check(delay>=3500&&delay<=12000,"bounded reading time");
        File dir=Files.createTempDirectory("reply-history").toFile();AgentStore db=new AgentStore(dir);long base=Instant.parse("2026-10-29T00:00:00Z").toEpochMilli();
        for(int i=0;i<180;i++)turn(db,"t"+i,base+i,REPLY);
        check(db.snapshot().getJSONArray("turns").length()==160,"model state remains bounded");db=new AgentStore(dir);
        Set<String> ids=new HashSet<>();String before=null;int pages=0;while(true){JSONArray page=db.historyPage(before,20);if(page.length()==0)break;pages++;for(int i=0;i<page.length();i++)check(ids.add(page.getJSONObject(i).getString("id")),"no duplicates across pages");before=page.getJSONObject(0).getString("pageKey");}
        check(ids.size()==180&&pages==9,"all history survives rollover and restart");
        JSONArray range=db.historyRange(base+5,base+10,null,20);check(range.length()==5,"inclusive/exclusive millisecond range");check(range.getJSONObject(0).getLong("receivedAt")==base+5,"original timestamp preserved in archive");
        JSONObject first=range.getJSONObject(0);check(first.has("repliedAt")&&first.has("timeZone")&&first.has("utcOffsetMinutes"),"completion and timezone metadata present");
        db.shown("t5",1);first=db.historyRange(base+5,base+6,null,1).getJSONObject(0);check(first.getJSONArray("segmentDisplayedAt").length()==1&&first.has("displayedAt"),"archive stores display timestamp");long firstShown=first.getLong("displayedAt");db.shown("t5",3);first=db.historyRange(base+5,base+6,null,1).getJSONObject(0);check(first.getLong("displayedAt")==firstShown&&first.getJSONArray("segmentDisplayedAt").length()==3,"first display time stable");
        JSONObject snap=db.snapshot();MemoryBridge.Result plan=new MemoryBridge.Result(snap,new JSONArray(),false,"test",new JSONArray());db.shown("t179",1);
        check(db.commit(db.epoch(),"新问题","新回复","calm",null,false,false,null,plan,"next",base+200,null,true),"commit using earlier recall snapshot");
        JSONObject latest=db.historyRange(base+179,base+180,null,1).getJSONObject(0);check(latest.getInt("displayed")==1&&latest.has("displayedAt"),"old memory plan preserves newer delivery metadata");
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.now=System.currentTimeMillis();String request=PromptBuilder.build(in,"你好",false,db.snapshot(),null).toString();check(!request.contains("特别重要的小事"),"unshown assistant text excluded from context");
        byte[] old=Files.readAllBytes(new File(dir,"companion-v1.json").toPath());db.clear(false);check(db.historyPage(null,20).length()==0,"clear removes archive and working history");
        Files.write(new File(dir,"companion-v1.json").toPath(),old);db=new AgentStore(dir);check(db.historyPage(null,20).length()==0,"tombstones prevent backup resurrection");
        File forgetDir=Files.createTempDirectory("history-forget").toFile();AgentStore forget=new AgentStore(forgetDir);
        forget.commit(0,"我喜欢海边散步","记住了","calm",new JSONArray().put("我喜欢海边散步"),true,false,null,null,"source",base,null,true);
        String memoryId=forget.snapshot().getJSONArray("memories").getJSONObject(0).getString("id");for(int i=0;i<161;i++)turn(forget,"other"+i,base+i+1,"普通回复");forget.forget(memoryId);
        check(forget.historyRange(base,base+1,null,20).length()==0,"forget purges an archived direct source");
        check(new AgentStore(forgetDir).historyRange(base,base+1,null,20).length()==0,"forget survives restart");
        System.out.println("PASS: "+checks+" history, timestamps and segment checks");
    }
}
