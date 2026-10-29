package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

public final class ReplyHistoryTest {
    static int checks;
    static void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
    static void turn(AgentStore db,String id,long at,String reply)throws Exception {check(db.commit(db.epoch(),"用户"+id,reply,"calm",null,false,false,null,null,id,at,null,true),"turn committed");}
    static final class Timer implements SpeechQueue.Clock {List<Runnable> tasks=new ArrayList<>();List<Long> delays=new ArrayList<>();public void later(Runnable r,long delay){tasks.add(r);delays.add(delay);}public void remove(Runnable r){tasks.remove(r);}void next(){if(!tasks.isEmpty())tasks.remove(0).run();}}
    static final String REPLY="今天的风很舒服，我就在这里安静陪你待一会儿，听你慢慢说今天发生了什么。等你忙完了，我们再一起聊点轻松的事情，或者想想周末有什么小期待。还有一件刚刚想到的特别重要的小事，我会等你有空的时候再慢慢告诉你。";
    public static void main(String[] args)throws Exception {
        segmentationRegression();
        naturalBubblesAndInvitations();
        for(String text:new String[]{"好呀",REPLY,"你好👩‍💻！今天很开心。",REPLY+"https://example.com/a?b=1", "```java\nint a = 1;\n```"}){List<String> parts=ReplySegments.split(text);check(parts.size()>=1&&parts.size()<=8,"bounded segments");check(String.join("",parts).equals(text),"lossless segmentation");}
        check(ReplySegments.split(REPLY).size()>3,"long clauses become shorter bubbles");check(ReplySegments.previewOnly(REPLY+REPLY+REPLY),"long answer links to complete history");
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
    static void segmentationRegression()throws Exception {
        String[][] examples={
            {"记下啦。", "以后聊到这件事，我会记得；", "想改的时候，到「记忆手账」里告诉我就好。"},
            {"会一点，不算大厨。", "煮面、炒个蛋、炖点简单的汤还行，", "复杂的就得边看教程边手忙脚乱了。"},
            {"刷不了哦。", "我这儿看不到你的屏幕，也开不了别的应用。", "倒是不介意听你讲讲刷到了什么。"},
            {"才艺啊……那我吹段笛子给你听，就当是手机里飘出来的声音。", "水平有限，你可别笑太大声。"},
            {"想看是想看，不过我这边只能听你说，看不到画面。", "你刷到什么好玩的，讲给我听听呗。"}
        };
        for(String[] expected:examples){String text=String.join("",expected);check(ReplySegments.splitV2(text).equals(Arrays.asList(expected)),"v2 persisted boundaries stay frozen");List<String> parts=ReplySegments.split(text);check(String.join("",parts).equals(text),"screenshot text preserved");for(String p:parts)check(p.length()<=28,"screenshot short bubble");}
        for(String text:new String[]{"", "好呀", "好呀。明天见！", "没有标点的一句话应该完整保留而不是从中间强行切断", "```java\nint x=1;\n```", "看看这个链接：https://example.com/a?b=1。稍后再聊。", "清单如下：\n- 苹果\n- 香蕉"})check(ReplySegments.split(text).equals(Collections.singletonList(text)),"single or structured reply preserved");
        for(String text:new String[]{"你好👩‍💻！今天工作顺利吗？有空一起聊聊。", "我说「慢慢来。别着急！」，等你准备好再继续。今天先休息。", "我们明天再聊吧。\r\n今天早点休息哦！", "今天想聊点什么呢？！ 我一直在这里等你。"}){List<String> parts=ReplySegments.split(text);check(String.join("",parts).equals(text),"unicode and whitespace preserved");for(String p:parts)check(!p.trim().isEmpty(),"no empty bubbles");}
        String quoted="我说「慢慢来。别着急！」，等你准备好再继续。今天先休息。";
        check(ReplySegments.split(quoted).get(0).contains("「慢慢来。别着急！」"),"quoted sentence stays intact");
        JSONObject old=new JSONObject().put("reply",String.join("",examples[1])).put("displayed",1).put("segmentDisplayedAt",new JSONArray().put(123));
        check(ReplySegments.count(old)==1&&ReplySegments.visible(old).equals(old.getString("reply"))&&ReplySegments.label(old).isEmpty(),"complete old reply stays complete");
        old.put("reply",REPLY);String prefix=ReplySegments.legacySplit(REPLY).get(0);
        check(ReplySegments.count(old)==3&&ReplySegments.visible(old).equals(prefix),"partial old reply keeps original visible prefix");
        check(old.getJSONArray("segmentDisplayedAt").getLong(0)==123,"old display timestamp untouched");
        old.put("segmentVersion",ReplySegments.VERSION);
        check(ReplySegments.visible(old).equals(ReplySegments.split(REPLY).get(0)),"new reply uses natural boundaries");
    }
    static void naturalBubblesAndInvitations()throws Exception {
        String numeric="今天是2026.10.29，金额是1,234.56。";check(String.join("",ReplySegments.split(numeric)).equals(numeric)&&ReplySegments.split(numeric).get(1).contains("1,234.56"),"numbers stay intact at clause boundaries");
        String text="刚刚那盘是你赢了，最后一手下得真漂亮。现在你赢两局，我赢一局，还没有平局——五子棋这回算你厉害，下次可没这么容易。";
        List<String> parts=ReplySegments.split(text);check(parts.size()>=3,"natural pauses break long commentary: "+parts);check(String.join("",parts).equals(text),"commentary stays lossless");for(String part:parts)check(part.codePointCount(0,part.length())<=28,"short commentary clauses: "+part);
        String pause="棋盘上的这个位置我还记着，你可别装作忘了……不过这一盘已经结束，想聊聊刚才的走法也行。";
        check(ReplySegments.split(pause).size()>=2&&ReplySegments.split(pause).get(0).endsWith("……"),"ellipsis is a natural pause: "+ReplySegments.split(pause));check(String.join("",ReplySegments.split(pause)).equals(pause),"ellipsis stays intact");
        JSONObject v2=new JSONObject().put("reply",text).put("segmentVersion",2).put("displayed",1).put("segmentDisplayedAt",new JSONArray().put(55));
        check(ReplySegments.visible(v2).equals(ReplySegments.splitV2(text).get(0)),"v2 displayed prefix not reinterpreted");check(v2.getJSONArray("segmentDisplayedAt").getLong(0)==55,"timestamps retained");
        for(String mention:new String[]{"五子棋刚才是你赢了，现在两胜一负。","我会下五子棋。","你想知道五子棋怎么玩吗？","你昨天说过想玩五子棋。","如果你想玩五子棋，改天再说。","我不想玩五子棋。","今天不下五子棋。","你说过「陪我下盘五子棋吧」。","例如：来一局五子棋吧。"})check(!GomokuInvite.offered("刚才谁赢了？",mention),"mention is not invitation: "+mention);
        for(String invite:new String[]{"陪我下盘五子棋吧。","要不要一起下五子棋？","想不想玩五子棋？","来一局五子棋。","我们继续下五子棋吧。"})check(GomokuInvite.offered("",invite),"explicit assistant invitation: "+invite);
        check(GomokuInvite.offered("我想玩五子棋","好呀，来吧。"),"user request offers board without repeated game keyword");
        check(!GomokuInvite.offered("我想玩五子棋","晚点再玩吧。"),"assistant postponement suppresses board");
        check(!GomokuInvite.offered("我不想玩五子棋","那我们聊点别的。"),"user refusal suppresses board");
        check(!GomokuInvite.offered("谁赢了五子棋？","我赢了一局。"),"user score question suppresses board");
        check(!GomokuInvite.offered("我想玩五子棋",""),"undisplayed reply has no action");
    }
}
