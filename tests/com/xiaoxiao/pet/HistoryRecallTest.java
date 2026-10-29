package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

public final class HistoryRecallTest {
    static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");static final long NOW=ZonedDateTime.of(2026,10,29,12,0,0,0,ZONE).toInstant().toEpochMilli();
    static HistoryRecall.Plan plan(String q){return HistoryRecall.plan(q,NOW,ZONE);}
    static JSONObject row(String id,long at,String user,String reply)throws Exception{return new JSONObject().put("id",id).put("at",at).put("receivedAt",at).put("user",user).put("reply",reply);}
    public static void main(String[] args)throws Exception{
        HistoryRecall.Plan week=plan("我们上周聊过什么？");check(week.valid(),"week parsed");check(week.start.equals(LocalDate.of(2026,10,19))&&week.end.equals(LocalDate.of(2026,10,25)),"previous calendar week Monday through Sunday");check(week.topic.isEmpty(),"overview has no accidental topic");
        check(week.from==LocalDate.of(2026,10,19).atStartOfDay(ZONE).toInstant().toEpochMilli()&&week.to==LocalDate.of(2026,10,26).atStartOfDay(ZONE).toInstant().toEpochMilli(),"half-open range in device zone");
        check(plan("上周三说了什么").start.equals(LocalDate.of(2026,10,21)),"weekday");check(plan("上上周我们聊过什么").start.equals(LocalDate.of(2026,10,12)),"two weeks ago");
        check(plan("昨天聊过的那个电影叫什么").topic.equals("电影"),"time and boilerplate removed from topic");
        check(plan("2026年10月19日到10月25日聊过什么").to==week.to,"explicit mixed full and short dates");check(plan("10月19日到25日聊过什么").from==week.from,"short range endpoint");
        check(plan("去年10月19日聊过什么").start.getYear()==2025,"explicit relative year");check(plan("最近七天聊过什么").start.equals(LocalDate.of(2026,10,23)),"rolling calendar days");
        check(plan("昨晚说过什么").to-plan("昨晚说过什么").from==6*3600000L,"evening window");
        check(plan("2026年2月30日聊过什么").error!=null,"invalid date clarifies");check(plan("2027年10月19日聊过什么").error!=null,"future date clarifies");check(plan("之前聊过什么").error!=null,"vague range clarifies");
        check(plan("今天聊点什么")==null&&plan("昨天我看了电影")==null,"ordinary conversation does not search");check(plan("上周一到周五聊过什么").error!=null,"unsupported shorthand does not silently search one day");
        check(plan("你记得我说过什么吗")==null,"ordinary memory question keeps existing recall path");check(plan("最近613566757周聊过什么").error!=null,"large rolling range cannot overflow into a few days");
        check(HistoryRecall.clip(String.join("",Collections.nCopies(500,"长"))+"电影星际穿越","电影来着",180).contains("星际穿越"),"long record excerpt includes matched topic near the end");
        JSONObject zoned=HistoryRecall.excerpt(row("z",week.from+3600000,"昨天看电影","回应").put("timeZone","America/Los_Angeles"),week);check(zoned.getString("sent_local").startsWith("2026-10-18")&&zoned.getString("sent_in_query_zone").startsWith("2026-10-19"),"original timezone preserved separately from query timezone");
        long january=ZonedDateTime.of(2026,1,8,10,0,0,0,ZONE).toInstant().toEpochMilli();check(HistoryRecall.plan("上个月聊过什么",january,ZONE).start.equals(LocalDate.of(2025,12,1)),"month crosses year");
        ZoneId ny=ZoneId.of("America/New_York");long november=ZonedDateTime.of(2026,11,3,12,0,0,0,ny).toInstant().toEpochMilli();HistoryRecall.Plan dst=HistoryRecall.plan("2026年11月1日聊过什么",november,ny);check(dst.to-dst.from==25*3600000L,"DST day uses timezone rules");
        File dir=Files.createTempDirectory("time-recall").toFile();AgentStore db=new AgentStore(dir);String quote="我喜欢电影星际穿越";
        db.commit(0,quote,"它讲了探索和亲情。","calm",new JSONArray().put(quote),true,false,null,null,"old-film",week.from+3600000,null,false);String fact=db.snapshot().getJSONArray("memories").getJSONObject(0).getString("id");
        for(int i=0;i<165;i++)db.commit(db.epoch(),"今天的新话题"+i,"新回复","calm",null,false,false,null,null,"new-"+i,NOW+i,null,false);
        check(db.snapshot().getJSONArray("turns").length()==160,"working state bounded");HistoryRecall.Result found=db.recallHistory(week,new AgentTransport.Token());check(found.found()&&found.evidence.toString().contains("星际穿越"),"archive beyond 160 turns reaches recall");check(!found.evidence.toString().contains("今天的新话题"),"outside dates excluded");
        check(found.evidence.getJSONArray("records").getJSONObject(0).getString("id").equals("old-film"),"stable source id retained");db.forget(fact);check(!db.recallHistory(week,new AgentTransport.Token()).found(),"forgotten archive source excluded");
        HistoryRecall.Scan scan=new HistoryRecall.Scan();scan.total=2;scan.examined=2;scan.rows.add(row("movie",week.from,"昨天看了星际穿越，真感动","HIDDEN_UNSHOWN_ANSWER").put("displayed",0));scan.rows.add(row("food",week.from+86400000,"想去吃火锅","去试试吧"));
        HistoryRecall.Result semantic=HistoryRecall.build(plan("上周聊过的电影叫什么"),scan);check(semantic.evidence.optString("topic_match").equals("time_range_candidates"),"synonyms leave time-range evidence for the answer model");check(!semantic.evidence.toString().contains("HIDDEN_UNSHOWN_ANSWER"),"unshown answer not exposed");
        scan=new HistoryRecall.Scan();for(int i=0;i<100;i++)scan.rows.add(row("r"+i,week.from+(i%7)*86400000L+i,"记录"+i+String.join("",Collections.nCopies(300,"长")),"回应"));scan.total=100;scan.examined=100;HistoryRecall.Result many=HistoryRecall.build(week,scan);
        check(many.evidence.toString().length()<HistoryRecall.MAX_PROMPT_CHARS,"bounded model evidence");check(many.evidence.optBoolean("partial"),"sampling and excerpts disclosed");check(many.evidence.getJSONArray("records").length()<=24,"bounded number of records");
        Set<String> days=new HashSet<>();JSONArray records=many.evidence.getJSONArray("records");for(int i=0;i<records.length();i++)days.add(records.getJSONObject(i).getString("sent_local").substring(0,10));check(days.size()==7,"weekly overview covers all populated days");
        ChatArchive archive=new ChatArchive(Files.createTempDirectory("recall-budget").toFile());for(int i=0;i<1002;i++){JSONObject t=row("budget"+i,week.from+i,"一条记录","回应");Files.write(new File(archive.dir,ChatArchive.key(t)+".json").toPath(),t.toString().getBytes("UTF-8"));}
        HistoryRecall.Scan bounded=archive.search(new JSONArray(),week,new AgentTransport.Token());check(bounded.total==1002&&bounded.examined==1000&&bounded.limited,"scan cap is explicit");
        AgentTransport.Token cancelled=new AgentTransport.Token();cancelled.cancel();try{archive.search(new JSONArray(),week,cancelled);throw new AssertionError("cancellation swallowed");}catch(IOException expected){check(cancelled.cancelled,"cancel propagated");}
        ChatArchive corrupt=new ChatArchive(Files.createTempDirectory("recall-corrupt").toFile());JSONObject t=row("broken",week.from,"x","y");Files.write(new File(corrupt.dir,ChatArchive.key(t)+".json").toPath(),"broken".getBytes("UTF-8"));HistoryRecall.Result broken=HistoryRecall.build(week,corrupt.search(new JSONArray(),week,new AgentTransport.Token()));check(broken.evidence.optString("status").equals("error"),"corrupt record does not masquerade as no conversation");
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.now=NOW;in.historyRecall=many.evidence;String prompt=PromptBuilder.build(in,"上周聊过什么",false,AgentStore.empty(0),null).toString();check(prompt.contains("historical_chat_search")&&prompt.contains("不能冒充完整总结"),"evidence and scope instructions reach prompt");
        System.out.println("PASS: "+checks+" temporal history recall checks");
    }
}
