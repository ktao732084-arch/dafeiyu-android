package com.xiaoxiao.pet;
import org.json.*;import java.nio.file.*;import java.io.*;

public final class InitiativeTest {
    static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        long now=1_800_000_000_000L;JSONObject s=AgentStore.empty(0).put("lastUser",now-5*60000).put("lastAttempt",now-3*60000).put("proactiveNextAt",now-1).put("attempts",99);
        check(Initiative.due(s,now,0),"due after model time even above old daily quota");
        check(!Initiative.due(s,now,now-1000),"brief interaction grace");
        check(!Initiative.due(s.put("proactiveNextAt",now+60000),now,0),"respects chosen next time");
        check(!Initiative.due(s.put("proactiveNextAt",now-1).put("lastAttempt",now-30000),now,0),"anti-loop floor");
        check(!Initiative.due(AgentStore.empty(0),now,now-60000),"stale live touch cannot revive cleared records");
        check(!Initiative.due(s.put("lastAttempt",now+60000),now,0),"clock rollback does not spam");
        for(Object v:new Object[]{null,"soon",2.5,Double.NaN,Double.POSITIVE_INFINITY})check(Initiative.minutes(v)==15,"invalid timing falls back");
        check(Initiative.minutes(-4)==2&&Initiative.minutes(999999999999L)==180,"model timing bounds");
        check(AgentReply.parse("{\"reply\":\"好\",\"next_check_minutes\":7}").nextCheckMinutes==7,"reply carries timing");
        check(AgentReply.parse("普通回复").nextCheckMinutes==15,"old model response remains compatible");
        check(Initiative.quiet("[安静陪伴]。")&&!Initiative.quiet("我想和你说句话"),"quiet sentinel never leaks as a chat bubble");
        s=AgentStore.empty(0);Initiative.interaction(s,"用户点了你",now);check(s.getLong("proactiveNextAt")==now+120000,"touch creates an earlier opportunity");
        for(int i=0;i<210;i++)Initiative.interaction(s,"用户拖动了你",now+i);check(s.getJSONArray("interactions").length()==200,"recent physical events bounded");
        check(s.getJSONArray("interactions").getJSONObject(199).getLong("at")==now+209,"interaction timestamp preserved");
        Initiative.decision(s,"quiet",17,now);check(s.getLong("proactiveNextAt")==now+17*60000,"silence rescheduled by model");
        Initiative.decision(s,"error",2,now);check(s.getLong("proactiveNextAt")==now+10*60000,"failure backs off");
        Initiative.decision(s,"error",2,now);check(s.getLong("proactiveNextAt")==now+20*60000,"repeated failure backs off further");
        Initiative.decision(s,"speak",3,now);check(s.getInt("proactiveFailures")==0&&s.getLong("proactiveNextAt")==now+3*60000,"success restores model timing");
        File dir=Files.createTempDirectory("initiative").toFile();AgentStore db=new AgentStore(dir);
        db.commit(db.epoch(),"我刚考完试","辛苦啦","calm",null,false,false,null,null,"exam",now-300000,null,false);
        db.interaction("用户点了你",now-180000);db.reserveAttempt("2027-01-15",now-180000);db.initiativeDecision(db.epoch(),"quiet",2,now-180000);
        db=new AgentStore(dir);check(db.initiativeDue(now,0),"scheduling and interactions survive restart");
        JSONObject ctx=Initiative.context(db.snapshot(),now);check(ctx.toString().contains("我刚考完试")&&ctx.toString().contains("用户点了你"),"real exchanges and touch chronology reach context");
        db.commit(db.epoch(),"","未展示的主动内容","calm",null,false,true,null,null,"pending",now,null,true,5);
        check(!Initiative.context(db.snapshot(),now).toString().contains("未展示的主动内容"),"unshown proactive reply excluded");
        db.shown("pending",1);check(Initiative.context(db.snapshot(),now).getInt("unanswered_proactive_messages")==1,"shown unanswered initiative counted");
        PromptBuilder.Inputs input=new PromptBuilder.Inputs();input.persona="固定人格：安静但有自己的主见";input.now=now;input.remember=true;input.timeZone="Asia/Shanghai";
        JSONArray request=PromptBuilder.build(input,"",true,db.snapshot(),null);check(request.getJSONObject(0).getString("content").startsWith(input.persona),"fixed persona retained");
        check(request.toString().contains("initiative_context")&&request.toString().contains("用户点了你")&&request.toString().contains("next_check_minutes"),"autonomy receives evidence and schedule contract");
        check(PromptBuilder.build(input,"我刚才对你做了什么",false,db.snapshot(),null).toString().contains("用户点了你"),"normal chat can also use recorded interactions after restart");
        check(Initiative.recentInteractions(db.snapshot()).getJSONObject(0).has("local_time"),"model sees readable timestamps");
        long epoch=db.epoch();db.clear(false);check(!db.interaction(epoch,"迟到的点击",now),"old interaction cannot return after clear");
        check(!db.initiativeDecision(epoch,"quiet",2,now),"old decision cannot return after clear");
        check(!db.initiativeDue(now+900000,now-60000)&&db.snapshot().getJSONArray("interactions").length()==0,"clearing history removes events and eligibility");
        System.out.println("PASS: "+checks+" model initiative checks");
    }
}
