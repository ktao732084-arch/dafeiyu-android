package com.xiaoxiao.pet;

import java.io.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.concurrent.atomic.AtomicInteger;import org.json.*;

/** Replays observed failures with reply-only model envelopes, plus encoding and cancellation boundaries. */
public final class CorrectionIntegrityTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        long now=Instant.parse("2026-10-30T10:00:00Z").toEpochMilli();ZoneId zone=ZoneId.of("Asia/Shanghai");
        String coffee="我最近上班最常买的是无糖美式，奶茶倒没怎么喝。";
        String coffeeFix="纠正一下，我刚才说错了：最近上班最常买的是无糖拿铁，不是美式。不加糖这个没变。";
        String park="这周六上午我打算去家附近的公园散步，不跑远。";
        String parkFix="公园那个安排改了，周六上午不去了，改到周日下午。还是家附近的公园，不是换地方。";
        JSONObject state=AgentStore.empty(0);int turn=0;
        for(String user:new String[]{coffee,park,coffeeFix,parkFix}){
            JSONArray candidates=MemoryLedger.candidates(user,new JSONArray(),null,"t"+(turn++),now);
            check(candidates.length()==1,"complete single fact: "+candidates);
            state=NativeMemoryEngine.evaluate(state,candidates,user,now,zone,null,new AgentTransport.Token()).state;
        }
        check(state.getJSONArray("memories").length()==2,"only current coffee and park: "+state);
        check(state.getJSONArray("archive").length()==2,"both old facts archived: "+state);
        for(int i=0;i<2;i++){
            JSONObject row=state.getJSONArray("memories").getJSONObject(i);
            if(row.getString("text").contains("公园")){
                check(row.getString("text").equals(parkFix),"scope retained verbatim");
                check("2026-11-01".equals(row.getJSONObject("engine").optString("event_date")),"replacement date, not cancelled date");
                check("2026-11-01T16:00:00Z".equals(row.getJSONObject("engine").optString("expires_at")),"expires after Sunday Shanghai");
            }else check(row.getString("text").equals(coffeeFix),"sugar qualifier retained");
        }
        JSONArray truncated=MemoryLedger.candidates(parkFix,new JSONArray().put("周六上午不去了").put("改到周日下午"),null,"partial",now);
        check(truncated.length()==1&&truncated.getJSONObject(0).getString("text").equals(parkFix),"model fragments cannot split a correction");
        for(String invalid:new String[]{"帮我润色：公园安排改了，改到周日。","假如我改到周日下午去公园呢？","我最常买什么咖啡？","纠正一下，我喜欢什么颜色？"})check(MemoryLedger.candidates(invalid,new JSONArray(),null,"no",now).length()==0,"not a fact: "+invalid);
        for(String task:new String[]{"再把我的通勤方式和咖啡一起说一下","代码例子结束啦","请把我刚才说的安排复述一遍"}){
            check(MemoryLedger.candidates(task,new JSONArray().put(task),null,"task",now).length()==0,"model cannot store a conversation command: "+task);
            JSONObject legacy=AgentStore.empty(0);legacy.getJSONArray("memories").put(MemoryLedger.record("old-task",task,"old",false,now));
            check(!MemoryLedger.context(legacy,"代码咖啡安排",new JSONArray().put("old-task")).contains(task),"old task excluded from ordinary recall");
        }
        check(!NativeMemoryEngine.autoFacts("我的项目代码已经写完了").isEmpty(),"actual project completion is still a fact");
        JSONObject recurring=MemoryLedger.record("recurring","我每周三去健身","r",false,now);NativeMemoryEngine.initialize(recurring,zone);check(!recurring.getJSONObject("engine").has("expires_at"),"recurring is not a dated event");
        JSONObject moved=MemoryLedger.record("moved","公园散步从周六上午改到周日下午，地点没变","move",false,now);NativeMemoryEngine.initialize(moved,zone);check("2026-11-01".equals(moved.getJSONObject("engine").optString("event_date")),"replacement wins even without an explicit cancellation word");
        File dir=java.nio.file.Files.createTempDirectory("correction-restart").toFile();AgentStore db=new AgentStore(dir);
        for(String user:new String[]{coffee,park,coffeeFix,parkFix}){MemoryBridge.Result plan=NativeMemoryEngine.evaluate(db.snapshot(),MemoryLedger.candidates(user,new JSONArray(),null,"saved"+(turn++),now),user,now,zone,null,new AgentTransport.Token());check(db.commit(db.epoch(),user,"哼，记下了。","calm",new JSONArray(),true,false,null,plan,"commit"+turn,now),"commit");}
        db=new AgentStore(dir);check(db.snapshot().getJSONArray("archive").length()==2,"corrections persisted after reopen");
        String context=MemoryLedger.context(db.snapshot(),"公园散步时间和咖啡",null);check(context.contains("改到周日下午")&&context.contains("无糖拿铁"),"latest facts available after reopen");

        String valid="哼，才没有得意。🐟";check(ReplyIntegrity.decode(valid.getBytes(StandardCharsets.UTF_8)).equals(valid),"UTF8 roundtrip");
        try{ReplyIntegrity.decode(new byte[]{(byte)0xe5,(byte)0x91});throw new AssertionError("invalid UTF8 accepted");}catch(ReplyIntegrity.Corrupt expected){}
        for(String broken:new String[]{"周日下\ufffd\ufffd\ufffd","bad\ud83d","bad\udc00"})check(ReplyIntegrity.damaged(broken,false),"corruption detected");
        AtomicInteger calls=new AtomicInteger(),badPreviews=new AtomicInteger();AgentTransport.Token token=new AgentTransport.Token();
        AgentReply reply=ReplyIntegrity.generate(progress->{int n=calls.incrementAndGet();String raw=n==1?"{\"reply\":\"周日下\\ufffd\"}":new JSONObject().put("reply",valid).put("aside","才不是特地记着的。").toString();progress.accept(n==1?"周日下\ufffd":valid);return raw;},token,p->{if(ReplyIntegrity.damaged(p,false))badPreviews.incrementAndGet();},false);
        check(calls.get()==2&&badPreviews.get()==0&&reply.reply.equals(valid)&&!reply.aside.isEmpty(),"one retry retains personality and aside");
        calls.set(0);try{ReplyIntegrity.generate(p->{calls.incrementAndGet();return "{\"reply\":\"\\ufffd\"}";},token,p->{},false);throw new AssertionError("repeated corruption accepted");}catch(ReplyIntegrity.Corrupt expected){check(calls.get()==2,"bounded retry");}
        calls.set(0);AgentTransport.Token cancelled=new AgentTransport.Token();try{ReplyIntegrity.generate(p->{calls.incrementAndGet();cancelled.cancel();throw new ReplyIntegrity.Corrupt();},cancelled,p->{},false);throw new AssertionError("cancel ignored");}catch(IOException expected){check(calls.get()==1,"cancel never retries");}
        check(ReplyIntegrity.generate(p->"{\"reply\":\"这个符号是�\"}",token,p->{},true).reply.contains("�"),"intentional user glyph can be discussed");
        check(!ReplyIntegrity.history("周日下�").contains("�")&&ReplyIntegrity.history(valid).equals(valid),"old corrupt reply masked without weakening intact style");
        JSONObject past=AgentStore.empty(0);past.getJSONArray("turns").put(new JSONObject().put("id","damaged-old").put("user","改到周日下午").put("reply","周日下�").put("at",now));
        PromptBuilder.Inputs input=new PromptBuilder.Inputs();input.persona="你是大肥鱼。";input.now=now;input.remember=true;input.timeZone="Asia/Shanghai";
        input.episodes=new JSONArray().put(new JSONObject().put("user","还是附近公园").put("reply","周日下�"));
        String prompt=PromptBuilder.build(input,"最后改到什么时候",false,past,null).toString();
        check(!prompt.contains("�")&&prompt.contains("改到周日下午")&&prompt.contains("还是附近公园"),"history and episodic context keep user evidence without replaying corrupted replies");
        check(past.toString().contains("�")&&input.episodes.toString().contains("�"),"context mask does not rewrite stored evidence");
        String tagged="哼，谁端着了。\n\n<aside>其实还挺高兴的。</aside>";AgentReply murmur=AgentReply.parse(tagged);murmur.recoverAside(false);
        check("哼，谁端着了。".equals(murmur.reply)&&"其实还挺高兴的。".equals(murmur.aside),"trailing murmur markup becomes the dedicated field");
        check(AgentReply.preview(tagged,false).equals(murmur.reply)&&AgentReply.preview("哼。\n<asi",false).equals("哼。"),"streamed markup never reaches main bubble");
        AgentReply literal=AgentReply.parse(tagged);literal.recoverAside(true);check(literal.reply.equals(tagged)&&AgentReply.preview(tagged,true).equals(tagged),"explicitly requested markup is preserved");
        AgentReply code=AgentReply.parse("示例：\n```html\n<aside>保留代码</aside>\n```");String originalCode=code.reply;code.recoverAside(false);check(code.reply.equals(originalCode),"code example is not reinterpreted as a murmur");
        System.out.println("PASS: source-complete corrections, cancellation dates, restart, UTF8, bounded repair and style preservation");
    }
}
