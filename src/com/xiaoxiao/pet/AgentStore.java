package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One atomic transaction for a conversational turn, mood and its grounded memories. */
final class AgentStore {
    static final int MAX_FILE_BYTES=4*1024*1024;
    // Older versions bounded turn count but not UTF-8 size. Read their valid files once,
    // then compact them to the same size limit used for every subsequent write.
    private static final int MAX_RECOVERY_BYTES=16*1024*1024;
    private final File file;
    private final ChatArchive transcript;
    private JSONObject state;
    AgentStore(File dir) throws Exception {
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建记忆目录");
        file=new File(dir,"companion-v1.json");transcript=new ChatArchive(dir);
        if(file.exists()) {
            try { state=read(file); }
            catch(Exception first) {
                File backup=new File(file+".bak");
                if(!backup.exists())throw new IOException("记忆文件无法读取，请先导出数据后再重置",first);
                state=read(backup);state.put("recovered",true);
            }
        } else if(new File(file+".bak").exists()){
            state=read(new File(file+".bak"));state.put("recovered",true);save(state);
        } else state=empty(0);
        JSONArray recoveredTurns=state.getJSONArray("turns");for(int i=recoveredTurns.length()-1;i>=0;i--)if(transcript.deleted(ChatArchive.id(recoveredTurns.getJSONObject(i))))recoveredTurns.remove(i);
        NativeMemoryEngine.prepare(state,System.currentTimeMillis(),java.time.ZoneId.systemDefault());
        if(state.toString().getBytes(StandardCharsets.UTF_8).length>MAX_FILE_BYTES)save(state);
    }
    static JSONObject empty(long epoch)throws Exception {
        return new JSONObject().put("schema",1).put("epoch",epoch).put("turns",new JSONArray())
            .put("memories",new JSONArray()).put("emotion","calm").put("lastUser",0).put("lastAttempt",0);
    }
    private static JSONObject read(File f)throws Exception {
        if(f.length()>MAX_RECOVERY_BYTES)throw new IOException("记忆文件过大");
        JSONObject o=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
        if(o.optInt("schema")!=1||o.optJSONArray("turns")==null||o.optJSONArray("memories")==null)throw new IOException("不支持的记忆格式");
        return o;
    }
    synchronized JSONObject snapshot()throws Exception {return new JSONObject(state.toString());}
    synchronized long epoch(){return state.optLong("epoch");}
    // Cheap reads for the 30 s heartbeat and the home screen; snapshot() copies the whole (up to 4 MiB) state.
    synchronized long lastUser(){return state.optLong("lastUser");}
    synchronized long lastAttempt(){return state.optLong("lastAttempt");}
    synchronized int attempts(String day){return day.equals(state.optString("attemptDay"))?state.optInt("attempts"):0;}
    synchronized String emotion(long now,long ttl){return now-state.optLong("emotionAt")<ttl?AgentPolicy.emotion(state.optString("emotion")):"calm";}
    synchronized JSONArray history() {
        JSONArray h=new JSONArray();JSONArray turns=state.optJSONArray("turns");
        try {for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(!t.optString("user").isEmpty())h.put(new JSONObject().put("role","user").put("content",t.getString("user")));h.put(new JSONObject().put("role","assistant").put("content",t.getString("reply")));}}catch(Exception ignored){}
        return h;
    }
    private void save(JSONObject next)throws Exception {save(next,true);}
    private void save(JSONObject next,boolean preserveHistory)throws Exception {
        JSONArray previous=new JSONArray(state.getJSONArray("turns").toString());
        byte[] bytes=boundedBytes(next);
        if(preserveHistory)transcript.evicted(previous,next.getJSONArray("turns"));
        File tmp=new File(file+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);out.getFD().sync();}
        // Backup only the last validated in-memory state, not a possibly corrupt source file.
        File backup=new File(file+".bak");
        try(FileOutputStream out=new FileOutputStream(backup)){out.write(state.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        state=next;
    }
    private static byte[] boundedBytes(JSONObject next)throws Exception {
        byte[] bytes=next.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length<=MAX_FILE_BYTES)return bytes;
        JSONArray turns=next.getJSONArray("turns");int length=bytes.length;
        // Keep complete recent turns and all long-term memories. UTF-8 size includes
        // JSON escaping; a count of Java characters is not a reliable disk bound.
        while(length>MAX_FILE_BYTES&&turns.length()>1){
            int removed=turns.getJSONObject(0).toString().getBytes(StandardCharsets.UTF_8).length;
            turns.remove(0);length-=removed+1;
        }
        bytes=next.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_FILE_BYTES)throw new IOException("这轮对话过大，请缩短后重试");
        return bytes;
    }
    synchronized boolean commit(long epoch,String user,String reply,String emotion,JSONArray quotes,boolean remember,boolean proactive)throws Exception {
        return commit(epoch,user,reply,emotion,quotes,remember,proactive,null);
    }
    synchronized boolean commit(long epoch,String user,String reply,String emotion,JSONArray quotes,boolean remember,boolean proactive,String explicit)throws Exception {
        return commit(epoch,user,reply,emotion,quotes,remember,proactive,explicit,null,null,0);
    }
    synchronized boolean commit(long epoch,String user,String reply,String emotion,JSONArray quotes,boolean remember,boolean proactive,String explicit,MemoryBridge.Result plan,String turnId,long observed)throws Exception {
        return commit(epoch,user,reply,emotion,quotes,remember,proactive,explicit,plan,turnId,observed,null);
    }
    synchronized boolean commit(long epoch,String user,String reply,String emotion,JSONArray quotes,boolean remember,boolean proactive,String explicit,MemoryBridge.Result plan,String turnId,long observed,JSONArray self)throws Exception {
        return commit(epoch,user,reply,emotion,quotes,remember,proactive,explicit,plan,turnId,observed,self,false);
    }
    synchronized boolean commit(long epoch,String user,String reply,String emotion,JSONArray quotes,boolean remember,boolean proactive,String explicit,MemoryBridge.Result plan,String turnId,long observed,JSONArray self,boolean awaitingDisplay)throws Exception {
        if(epoch!=epoch())return false;
        JSONObject next=plan==null?snapshot():MemoryLedger.copy(plan.state);long now=observed>0?observed:System.currentTimeMillis();String tid=turnId==null?UUID.randomUUID().toString():turnId;
        MemoryLedger.migrate(next);if(plan!=null)next.put("memoryStatus",plan.status);
        if(explicit!=null){
            if(!remember||proactive)throw new IOException("当前不能写入记忆");
            if(explicit.trim().isEmpty()||explicit.length()>240||!user.contains(explicit))throw new IOException("记忆请填写 1–240 个字");
            if(AgentPolicy.sensitive(explicit))throw new IOException("请不要把密码、密钥或证件号码保存为记忆");
            if(plan==null){if(next.getJSONArray("memories").length()>=120)throw new IOException("记忆已满，请先删除几条");addMemory(next,explicit,tid,true,now);}
        }
        JSONArray turns=next.getJSONArray("turns");
        // Presentation may advance while the model works on an older memory snapshot.
        JSONArray live=state.getJSONArray("turns");Map<String,JSONObject> presentation=new HashMap<>();for(int i=0;i<live.length();i++)presentation.put(ChatArchive.id(live.getJSONObject(i)),live.getJSONObject(i));
        for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i),current=presentation.get(ChatArchive.id(t));if(current!=null)for(String field:new String[]{"displayed","displayedAt","lastDisplayedAt","segmentDisplayedAt","displayTimeZone"})if(current.has(field))t.put(field,current.get(field));}
        JSONArray pendingSelf=next.optJSONArray("self"),liveSelf=state.optJSONArray("self");if(pendingSelf!=null&&liveSelf!=null)for(int i=0;i<pendingSelf.length();i++)for(int j=0;j<liveSelf.length();j++){JSONObject pending=pendingSelf.getJSONObject(i),current=liveSelf.getJSONObject(j);if(pending.optString("source").equals(current.optString("source"))&&pending.optString("text").equals(current.optString("text"))&&current.optBoolean("shown",true))pending.put("shown",true);}
        // Lifetime counters: turnCount anchors the prompt's history window, the others the relationship stage.
        next.put("turnCount",next.optInt("turnCount",turns.length())+1);
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        JSONObject turn=new JSONObject().put("id",tid).put("at",now).put("receivedAt",now).put("repliedAt",System.currentTimeMillis()).put("timeZone",zone.getId()).put("utcOffsetMinutes",zone.getRules().getOffset(java.time.Instant.ofEpochMilli(now)).getTotalSeconds()/60).put("user",user).put("reply",reply).put("proactive",proactive);
        if(awaitingDisplay)turn.put("displayed",0);turns.put(turn);
        while(turns.length()>160)turns.remove(0);
        next.put("emotion",AgentPolicy.emotion(emotion)).put("emotionAt",now);
        if(!proactive){
            next.put("lastUser",now).put("userTurns",next.optInt("userTurns")+1);
            String day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
            if(!day.equals(next.optString("lastChatDay")))next.put("lastChatDay",day).put("chatDays",next.optInt("chatDays")+1);
        }
        if(remember&&self!=null)for(int i=0;i<Math.min(2,self.length());i++){String quote=self.optString(i,"");if(AgentPolicy.validSelf(reply,quote))addSelf(next,quote.trim(),tid,now,!awaitingDisplay);}
        if(plan==null&&remember&&!proactive&&quotes!=null)for(int i=0;i<Math.min(3,quotes.length());i++) {
            String quote=quotes.optString(i,"");if(AgentPolicy.validEvidence(user,quote))addMemory(next,quote,tid,false,now);
        }
        save(next);return true;
    }
    static final int MAX_SELF=40;
    /** What she has said about herself, verbatim from her replies, so she stays consistent ("我最爱吃小鱼干"). */
    private static void addSelf(JSONObject next,String text,String turn,long now,boolean shown)throws Exception {
        JSONArray a=next.optJSONArray("self");if(a==null)next.put("self",a=new JSONArray());
        for(int i=a.length()-1;i>=0;i--)if(text.equals(a.getJSONObject(i).optString("text")))a.remove(i);
        a.put(new JSONObject().put("text",text).put("source",turn).put("at",now).put("shown",shown));
        while(a.length()>MAX_SELF)a.remove(0);
    }
    /** Up to eight of her own past statements, most relevant to this turn first, then the most recent. */
    static JSONArray selfContext(JSONObject snapshot,String query)throws Exception {
        JSONArray a=snapshot.optJSONArray("self"),out=new JSONArray();if(a==null)return out;
        List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++)if(a.getJSONObject(i).optBoolean("shown",true))rows.add(a.getJSONObject(i));
        rows.sort((x,y)->{int c=Double.compare(AgentPolicy.relevance(query,y.optString("text")),AgentPolicy.relevance(query,x.optString("text")));return c!=0?c:Long.compare(y.optLong("at"),x.optLong("at"));});
        for(int i=0;i<Math.min(8,rows.size());i++)out.put(rows.get(i).optString("text"));
        return out;
    }
    /** Due events offered in proactive turn `turn`; they count as asked once that reply is shown (MemoryLedger.followUps). */
    synchronized void markFollowed(JSONArray events,String turn)throws Exception {
        if(events==null||events.length()==0)return;JSONObject next=snapshot();JSONArray f=next.optJSONArray("followedUp");if(f==null)next.put("followedUp",f=new JSONArray());
        for(int i=0;i<events.length();i++)f.put(new JSONObject().put("id",events.getJSONObject(i).optString("id")).put("turn",turn));while(f.length()>50)f.remove(0);save(next);
    }
    synchronized void clearSelf()throws Exception {JSONObject next=snapshot();next.put("self",new JSONArray()).put("epoch",epoch()+1);save(next);purgeBackup();}
    private static void addMemory(JSONObject next,String text,String source,boolean pinned,long now)throws Exception {
        NativeMemoryEngine.writeRules(next,MemoryLedger.record(UUID.randomUUID().toString(),text,source,pinned,now),now,java.time.ZoneId.systemDefault());
    }
    synchronized void remember(String text)throws Exception {
        remember(text,true);
    }
    synchronized void remember(String text,boolean pinned)throws Exception {
        if(text.trim().isEmpty()||text.length()>240)throw new IOException("记忆请填写 1–240 个字");
        if(AgentPolicy.sensitive(text))throw new IOException("请不要把密码、密钥或证件号码保存为记忆");
        JSONObject next=snapshot();if(next.getJSONArray("memories").length()>=120)throw new IOException("记忆已满，请先删除几条");
        addMemory(next,text.trim(),"你手动保存",pinned,System.currentTimeMillis());next.put("epoch",epoch()+1);save(next);
    }
    synchronized void editMemory(String id,String text,boolean pinned)throws Exception {
        if(text.trim().isEmpty()||text.length()>240||AgentPolicy.sensitive(text))throw new IOException("请填写 1–240 字，不含密码或证件号码");
        JSONObject next=snapshot();transcript.forget(MemoryLedger.forget(next,id));MemoryLedger.addLocal(next,MemoryLedger.record(id,text.trim(),"你手动修改",pinned,System.currentTimeMillis()));
        next.put("epoch",epoch()+1);save(next,false);purgeBackup();
    }
    private static void removeSourceTurn(JSONObject next,String source)throws Exception {
        JSONArray turns=next.getJSONArray("turns");for(int j=turns.length()-1;j>=0;j--)if(source.equals(turns.getJSONObject(j).optString("id")))turns.remove(j);
    }
    synchronized void forget(String id)throws Exception {
        JSONObject next=snapshot();transcript.forget(MemoryLedger.forget(next,id));
        next.put("epoch",epoch()+1);save(next,false);purgeBackup();
    }
    synchronized void clear(boolean all)throws Exception {
        transcript.clear(state.getJSONArray("turns"));
        JSONObject next=all?empty(epoch()+1):snapshot().put("turns",new JSONArray()).put("epoch",epoch()+1);
        MemoryLedger.migrate(next);save(next,false);purgeBackup();
    }
    synchronized JSONArray historyPage(String before,int limit)throws Exception {return transcript.page(state.getJSONArray("turns"),before,Math.max(1,Math.min(limit,40)));}
    synchronized JSONArray historyRange(long fromInclusive,long toExclusive,String before,int limit)throws Exception {return transcript.page(state.getJSONArray("turns"),before,Math.max(1,Math.min(limit,40)),fromInclusive,toExclusive);}
    synchronized void shown(String id,int count)throws Exception {
        if(id==null||transcript.deleted(id))return;JSONObject next=snapshot();JSONArray turns=next.getJSONArray("turns");
        for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(id.equals(ChatArchive.id(t))){int n=Math.min(count,ReplySegments.count(t));if(t.has("displayed")&&n>t.optInt("displayed")){ChatArchive.markShown(t,n);JSONArray self=next.optJSONArray("self");if(self!=null)for(int j=0;j<self.length();j++){JSONObject row=self.getJSONObject(j);if(id.equals(row.optString("source"))&&ReplySegments.visible(t).contains(row.optString("text")))row.put("shown",true);}save(next,false);}return;}}
        transcript.shown(id,count);
    }
    private void purgeBackup()throws Exception {Files.deleteIfExists(new File(file+".bak").toPath());}
    synchronized void reserveAttempt(String day,long now)throws Exception {
        JSONObject next=snapshot();int n=day.equals(next.optString("attemptDay"))?next.optInt("attempts"):0;
        next.put("attemptDay",day).put("attempts",n+1).put("lastAttempt",now);save(next);
    }
    synchronized String memoryContext(String query)throws Exception {
        return MemoryLedger.context(state,query,null);
    }
    private static double score(String q,JSONObject m){return AgentPolicy.relevance(q,m.optString("text"))*5+(m.optBoolean("pinned")?2:0)+Math.max(0,1-(System.currentTimeMillis()-m.optLong("at"))/(30.0*86400000));}
    synchronized String recall(String query)throws Exception {
        return recall(state,query);
    }
    static String recall(JSONObject snapshot,String query)throws Exception {
        JSONArray a=snapshot.getJSONArray("turns");List<JSONObject> hits=new ArrayList<>();Set<String> stale=MemoryLedger.staleSources(snapshot,System.currentTimeMillis());
        for(int i=0;i<Math.max(0,a.length()-8);i++){JSONObject t=a.getJSONObject(i);if(!stale.contains(t.optString("id"))&&AgentPolicy.relevance(query,t.optString("user"))>.12)hits.add(t);}
        hits.sort((x,y)->Double.compare(AgentPolicy.relevance(query,y.optString("user")),AgentPolicy.relevance(query,x.optString("user"))));
        StringBuilder out=new StringBuilder();for(int i=0;i<Math.min(3,hits.size());i++){JSONObject t=hits.get(i);out.append("\n").append(new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(new Date(t.optLong("at")))).append(" 用户曾说：").append(AgentPolicy.clip(t.optString("user"),300));}
        return out.toString();
    }
}
