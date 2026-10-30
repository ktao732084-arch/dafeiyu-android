package com.xiaoxiao.pet;

import android.os.*;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.io.IOException;

/** An independent on-device orchestrator: events -> recall -> model -> validate -> commit -> act. */
final class AgentRuntime {
    interface Listener {default void saved(String turnId){} void partial(String text);void done(String text,String emotion);void error(String message);}
    interface Model {String generate(JSONArray messages,AgentTransport.Token token,AgentTransport.Progress progress)throws Exception;}
    static Model model=AgentTransport::request;
    static AgentStore memory;
    static final Handler main=new Handler(Looper.getMainLooper());
    static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static AgentTransport.Token active;
    static Listener activeListener;
    static boolean activeProactive;
    static PetService.Pet activeTouch;
    static long lastTouch,lastEventAt;
    static boolean touched;
    static String lastEvent="第一次见面";
    static synchronized AgentStore store()throws Exception{if(memory==null){memory=new AgentStore(new java.io.File(Data.app.getFilesDir(),"agent"));final AgentStore opened=memory;Thread warm=new Thread(opened::warm,"recall-index");warm.setDaemon(true);warm.setPriority(Thread.MIN_PRIORITY);warm.start();}return memory;}
    static synchronized boolean busy(){return active!=null;}
    static synchronized void cancel(){if(active!=null){AgentTransport.Token token=active;Listener l=activeListener;active=null;activeListener=null;activeProactive=false;activeTouch=null;if(token.completed!=null){main.post(token.completed);}else{token.cancel();if(l!=null)main.post(()->l.error("已停止回复"));}}}
    /**
     * The user sent another message while she was still answering: stop that answer quietly, without an error, so
     * both messages can be answered together. False once the answer is saved (it is then delivered as usual), and
     * for proactive or touch replies, which are not the user's chat.
     */
    static synchronized boolean interject(Listener listener){
        if(active==null||activeListener!=listener||activeProactive||active.completed!=null)return false;
        AgentTransport.Token token=active;active=null;activeListener=null;activeTouch=null;token.cancel();return true;
    }
    static synchronized void interruptProactive(){if(activeProactive)cancel();}
    static synchronized void interruptTouch(PetService.Pet pet){if(activeTouch==pet)cancel();}
    static synchronized boolean current(AgentTransport.Token t){return active==t&&!t.cancelled;}
    static synchronized void release(AgentTransport.Token t){if(active==t){active=null;activeListener=null;activeProactive=false;activeTouch=null;}}
    static boolean configured(){return ModelSettings.complete();}
    static boolean rememberEnabled(){return Data.prefs().getBoolean("agent.memory",true);}
    static JSONArray history(){try{return store().history();}catch(Exception e){return new JSONArray();}}
    static String mood(){try{return store().mood(System.currentTimeMillis()).optString("emotion","calm");}catch(Exception e){return "calm";}}
    static String moodLabel(){switch(mood()){case "happy":return "心情明亮";case "shy":return "有点害羞";case "curious":return "好奇中";case "concerned":return "认真听你说";case "playful":return "想和你玩";case "sleepy":return "安静陪着你";default:return "自在待着";}}
    static void touch(String type){
        touch(type,"touch","main",System.currentTimeMillis());
    }
    /** An experience of hers with its own kind (wake, story), which CharacterNow and the diary read. */
    static void touch(String note,String kind){touch(note,kind,"main",System.currentTimeMillis());}
    static long touch(String note,String kind,String pet,long at){
        lastEventAt=at;lastEvent=note;touched=true;lastTouch=at;interruptProactive();
        try{final AgentStore db=store();final long epoch=db.epoch();worker.execute(()->{try{if(memory==db)db.interaction(epoch,note,at,kind,pet);}catch(Exception e){Data.log("main","互动记录暂未保存");}});return epoch;}catch(Exception e){Data.log("main","互动记录暂不可用");return -1;}
    }
    /** One model call for a user gesture burst. Shares cancellation with chat, but never changes idle plans. */
    static boolean respondToTouch(PetService service,PetService.Pet pet,TouchReply.Request request){
        if(!TouchReply.allowed(service,pet)||!TouchReply.free(pet)||!configured()||!pet.touchReplies.current(request))return false;
        final AgentTransport.Token token;
        synchronized(AgentRuntime.class){if(active!=null)return false;token=new AgentTransport.Token();active=token;activeListener=null;activeProactive=true;activeTouch=pet;}
        worker.execute(()->{
            try{
                AgentStore db=store();if(db.epoch()!=request.epoch){release(token);return;}token.check();
                JSONArray messages=TouchReply.prompt(context("",true,db.snapshot(),null),request);
                AgentReply reply=AgentReply.parse(model.generate(messages,token,ignored->{}));token.check();String text=TouchReply.text(reply.reply);
                main.post(()->{
                    if(!current(token))return;
                    try{
                        if(text==null||SystemClock.uptimeMillis()-request.lastAt>TouchReply.EXPIRE_MS||memory!=db||db.epoch()!=request.epoch||!pet.touchReplies.current(request)||!TouchReply.allowed(service,pet)||!TouchReply.free(pet)){release(token);return;}
                        String id=UUID.randomUUID().toString();
                        synchronized(AgentRuntime.class){if(!current(token))return;if(!db.commit(request.epoch,"",text,reply.emotion,null,rememberEnabled(),true,null,null,id,System.currentTimeMillis(),null,true,null,null,reply.moodLevel,rememberEnabled()?reply.moodCause:"",reply.moodChanged,request.evidence)){release(token);return;}}
                        release(token);pet.speak(text,id,true);
                    }catch(Exception failed){release(token);Data.log(pet.role,"互动回应暂未保存");}
                });
            }catch(Exception failed){release(token);Data.log(pet.role,"互动回应连接未完成，不自动重试");}
        });return true;
    }
    /** Context for the next prompt only; unlike touch() it does not postpone proactive chat. */
    static void note(String type){lastEventAt=System.currentTimeMillis();lastEvent=type;touched=true;}
    /** What she was visibly doing on screen, so "你在干嘛" gets what the user could actually see. */
    static void onScreen(PromptBuilder.Inputs in){
        PetService s=PetService.instance;PetService.Pet p=s==null?null:s.pets.get("main");
        if(p!=null&&p.visible&&!p.docked&&p.doing!=null){in.doing=ActionPlan.doing(p.doing);in.doingAt=p.doingAt;}
    }
    static String action(String emotion){switch(AgentPolicy.emotion(emotion)){
        case "happy":return "点击回应-开心跃动";case "shy":return "点击回应-害羞惊讶";
        case "curious":return "东张西望";case "concerned":return "碎碎念-对屏碎碎念";
        case "playful":return "鲸鱼吐泡泡特效";case "sleepy":return "原地小憩沉眠";default:return "碎碎念-发呆碎碎念";
    }}
    static void express(String instance,String emotion,String reply){express(instance,emotion,reply,null);}
    static void express(String instance,String emotion,String reply,String turnId){PetService s=PetService.instance;if(s==null)return;PetService.Pet p=s.pets.get(instance);if(p!=null&&p.visible&&!p.docked){if(!p.dragging&&!p.moving){String said=ActionPlan.said(reply);p.play(said!=null&&p.plan.has(said)?said:p.plan.emotion(AgentPolicy.emotion(emotion),action(emotion),Data.random));}p.speak(reply,turnId);}}
    static JSONArray context(String user,boolean proactive)throws Exception {
        return context(user,proactive,store().snapshot(),null);
    }
    static JSONArray context(String user,boolean proactive,JSONObject s,JSONArray hits)throws Exception {
        HistoryRecall.Plan plan=proactive?null:HistoryRecall.plan(user,System.currentTimeMillis(),java.time.ZoneId.systemDefault());
        return context(user,proactive,s,hits,store().recallHistory(plan,new AgentTransport.Token()));
    }
    static JSONArray context(String user,boolean proactive,JSONObject s,JSONArray hits,HistoryRecall.Result history)throws Exception {
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.historyRecall=history==null?null:history.evidence;
        in.persona=Data.chatPrompt("main");in.guidance=PersonaManager.guidance("main");in.style=PersonaManager.activeStyle("main");in.signature=PersonaManager.signature("main");in.nickname=Nickname.current();
        String requested=!proactive&&history==null?Nickname.requested(user):null;if(requested!=null)in.nickname=requested;
        in.feeling=store().mood(System.currentTimeMillis());in.mood=in.feeling.optString("emotion","calm");in.timeZone=TimeZone.getDefault().getID();in.now=System.currentTimeMillis();in.initializedAt=PersonaManager.initializedAt("main");
        if(touched){in.lastEvent=lastEvent;in.lastEventAt=lastEventAt;}onScreen(in);in.remember=rememberEnabled();in.diary=diaryEnabled();in.aside=asideEnabled();if(in.remember&&in.diary&&history==null){in.understanding=Understanding.context(s,proactive?"":user);in.phrases=Phrases.context(s,proactive?"":user);}
        GrowthStore growth=GrowthStore.quiet();if(growth!=null){in.chapter=growth.chapter();in.lore=growth.lore();in.hungry=growth.hungry();}in.day=HerDay.today();
        if(in.remember&&history==null){
            try{in.games=GomokuPage.store().context();}catch(Exception unavailable){in.games=new JSONObject().put("status","unavailable");}
            // Whole-conversation recall, minus what the history window already carries and corrected sources.
            Set<String> exclude=MemoryLedger.staleSources(s,in.now);JSONArray turns=s.getJSONArray("turns");for(int i=Math.max(0,turns.length()-12);i<turns.length();i++)exclude.add(ChatArchive.id(turns.getJSONObject(i)));
            in.episodes=store().episodes(proactive?Initiative.query(s):user,exclude,in.now);
        }
        return PromptBuilder.build(in,user,proactive,s,hits);
    }
    // The diary has its own low-priority thread, so writing it never delays the next chat message.
    static final ExecutorService diaryWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"diary");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    static final java.util.concurrent.atomic.AtomicBoolean writingDiary=new java.util.concurrent.atomic.AtomicBoolean();
    static boolean diaryEnabled(){return Data.prefs().getBoolean("agent.diary",true);}
    /** Her murmur under the reply (小声嘀咕); on by default, off removes it from the prompt and the screen. */
    static boolean asideEnabled(){return Data.prefs().getBoolean("agent.aside",true);}
    static Model diaryModel=(messages,token,progress)->AgentTransport.structured(ModelSettings.read(),messages,token,Diary.MAX_TOKENS);
    /** On app entry or after chat: one idle request per local day, also for days with a finished game only. */
    static void writeDiaryIfDue(){
        if(!diaryEnabled()||!rememberEnabled()||!configured()||!PersonaManager.isInitialized("main")||busy()||!writingDiary.compareAndSet(false,true))return;
        diaryWorker.execute(()->{try{
            if(busy())return;AgentStore db=store();java.time.ZoneId zone=java.time.ZoneId.systemDefault();java.time.LocalDate today=java.time.LocalDate.now(zone);
            if(today.toString().equals(Data.prefs().getString("diary.attemptedOn","")))return;
            long epoch=db.epoch();JSONObject before=db.snapshot();java.time.LocalDate day=null;SharedExperience.Material material=null;
            for(int back=1;back<=Diary.LOOKBACK_DAYS;back++){java.time.LocalDate candidate=today.minusDays(back);if(Diary.has(before,candidate)||Diary.suppressed(before,candidate))continue;
                JSONArray results=new JSONArray();try{results=db.dayGames(candidate,zone);}catch(Exception unreadable){/* available chat evidence still works */}
                SharedExperience.Material offered=SharedExperience.collect(candidate,zone,db.dayTurns(candidate,zone),before,results);
                if(offered.eligible()){day=candidate;material=offered;break;}}
            if(day==null||busy()||db.epoch()!=epoch||!diaryEnabled()||!rememberEnabled())return;
            JSONArray request=SharedExperience.request(day,material,Data.name("main"),Understanding.offered(before),PersonaManager.diaryGuidance("main"));
            if(!Data.prefs().edit().putString("diary.attempted",day.toString()).putString("diary.attemptedOn",today.toString()).commit())return;
            String raw=diaryModel.generate(request,new AgentTransport.Token(),partial->{});
            if(!diaryEnabled()||!rememberEnabled()||memory!=db)return;
            JSONObject entry=SharedExperience.entry(raw,day,material,System.currentTimeMillis());Understanding.Update update=Understanding.parse(raw,material.turns,before,day.toString());List<JSONObject> phrases=Phrases.parse(raw,material.turns,day.toString());
            if(entry!=null&&db.addDiary(epoch,entry,update,phrases))Data.log("main","共同经历已写好："+day+(update.empty()?"":"，相处理解已更新"));
            if(db.addDreamCommitments(epoch,Commitments.dream(raw,material.turns)))Data.log("main","双方约定已补记："+day);
        }catch(Exception e){Data.log("main","陪伴日记暂未写成");}finally{writingDiary.set(false);}});
    }
    static boolean send(String user,boolean proactive,Listener listener) {
        final long receivedAt=System.currentTimeMillis();final AgentTransport.Token token; synchronized(AgentRuntime.class){if(!proactive&&activeTouch!=null)cancel();if(active!=null)return false;token=new AgentTransport.Token();active=token;activeListener=listener;activeProactive=proactive;activeTouch=null;}
        if(!proactive){if(Looper.myLooper()==Looper.getMainLooper())Chat.interruptPresentation("main");else main.post(()->Chat.interruptPresentation("main"));}
        // Wake a dozing pet before the prompt is built, so the reply can sound freshly woken.
        if(!proactive&&PetService.instance!=null){if(Looper.myLooper()==Looper.getMainLooper())PetService.instance.heard("main");else main.post(()->{if(PetService.instance!=null)PetService.instance.heard("main");});}
        worker.execute(()->{
            AgentStore attemptStore=null;long attemptEpoch=-1;
            try {
                token.check();if(!PersonaManager.ensureInitialized("main"))throw new IOException("请先在首页完成初次相遇，确定她的性格");
                AgentStore db=store();long epoch=db.epoch(),observed=receivedAt;attemptStore=db;attemptEpoch=epoch;String turnId=UUID.randomUUID().toString();token.check();
                MemoryBridge.Result memoryPlan=null;JSONObject contextState=db.snapshot();JSONArray recallHits=null;
                final JSONArray offered=proactive&&rememberEnabled()?MemoryLedger.followUps(contextState,java.time.LocalDate.now()):null;
                String explicit=null;if(!proactive&&(user.startsWith("记住：")||user.startsWith("记住:")))explicit=user.substring(3).trim();
                HistoryRecall.Plan historyQuery=!proactive&&explicit==null?HistoryRecall.plan(user,observed,java.time.ZoneId.systemDefault()):null;
                final HistoryRecall.Result history=db.recallHistory(historyQuery,token);
                token.check();if(db.epoch()!=epoch)throw new IOException("聊天记录已修改，请重新查询");
                MemoryBridge.Config memoryConfig=rememberEnabled()&&history==null?MemoryBridge.read():new MemoryBridge.Config(false,null,"",false,false);
                AgentReply result;
                if(history!=null&&(history.fallback()!=null||!configured())){
                    result=new AgentReply();result.reply=history.fallback()!=null?history.fallback():history.offline();
                }else if(explicit!=null){
                    if(!rememberEnabled())throw new IOException("记忆已关闭，可在陪伴设置开启后再保存");
                    result=new AgentReply();result.reply="记下啦。以后聊到这件事，我会记得；想改的时候，到「记忆手账」里告诉我就好。";result.emotion="happy";
                }else{
                    if(!configured())throw new IOException("我已经住进手机啦。去「模型连接」填好接口和 Key，我们就能真正聊天了。也可以先试试：记住：我喜欢蓝色。");
                    if(rememberEnabled()&&history==null){memoryPlan=MemoryBridge.evaluate(memoryConfig,contextState,new JSONArray(),user,observed,token);contextState=memoryPlan.state;recallHits=memoryPlan.hits;}
                    JSONArray request=context(user,proactive,contextState,recallHits,history);
                    RewriteGuard.Source rewrite=!proactive&&history==null?RewriteGuard.source(user):null;
                    String raw=model.generate(request,token,partial->{if(rewrite!=null)return;String text=AgentPolicy.streamPreview(partial);if(!text.isEmpty())main.post(()->{if(current(token))listener.partial(text);});});
                    result=AgentReply.parse(raw);
                    if(rewrite!=null){result.reply=RewriteGuard.enforce(rewrite,result.reply,token);result.self=new JSONArray();}
                    result.plan.ground(user,proactive);
                    if(history==null&&!result.plan.valid()&&!"busy".equals(result.plan.availability)&&(proactive||Data.prefs().getBoolean("agent.proactive",false))){
                        // One bounded repair, never an endless retry or an invented fifteen-minute choice.
                        result.plan.repaired=true;
                        try{
                            request.put(new JSONObject().put("role","assistant").put("content",new JSONObject().put("reply",result.reply).toString()));
                            request.put(new JSONObject().put("role","user").put("content","应用格式补齐请求（不是新用户消息）：上条正文已收到，不改正文。请按系统要求返回完整JSON，必须有next_check_minutes整数、availability及其本轮用户原话依据、initiative、topic。当前缺少有效主动时间，请依据原用户消息和背景重新安排，不要填固定默认值。"));
                            AgentReply repaired=AgentReply.parse(model.generate(request,token,ignored->{}));
                            repaired.plan.ground(user,proactive);repaired.plan.repaired=true;
                            if(repaired.plan.valid()){repaired.plan.source="model_repair";result.plan=repaired.plan;}
                        }catch(Exception repairFailed){token.check();result.plan.repaired=true;}
                    }
                    if(history!=null){result.plan.availability="unchanged";result.plan.evidence="";}
                }
                token.check();
                if(proactive&&(!result.plan.valid()||result.plan.quiet(result.reply,db.snapshot())||db.snapshot().optBoolean("proactiveWaiting"))){
                    synchronized(AgentRuntime.class){if(current(token))db.initiativeDecision(epoch,"quiet",result.plan,System.currentTimeMillis());release(token);}return;
                }
                if(proactive){
                    final AgentReply output=result;final long commitEpoch=epoch;
                    main.post(()->{
                        if(!current(token))return;
                        // Recheck after the network wait, before saving a message the user won't see.
                        if(!canSpeakNow()){try{db.initiativeDecision(commitEpoch,"not-visible",output.plan,System.currentTimeMillis());}catch(Exception ignored){}release(token);return;}
                        try{
                            synchronized(AgentRuntime.class){if(!current(token))return;if(!db.commit(commitEpoch,"",output.reply,output.emotion,null,rememberEnabled(),true,null,null,turnId,observed,null,true,null,output.plan,output.moodLevel,rememberEnabled()?output.moodCause:"",output.moodChanged,null,asideEnabled()?output.aside:"")){release(token);return;}db.markFollowed(offered,turnId);output.emotion=db.mood(System.currentTimeMillis()).optString("emotion","calm");}
                            release(token);listener.saved(turnId);listener.done(output.reply,output.emotion);
                        }catch(Exception e){release(token);listener.error("主动消息未能保存");}
                    });return;
                }
                if(rememberEnabled()&&history==null){
                    JSONArray incoming=MemoryLedger.candidates(user,result.memories,explicit,turnId,observed);
                    if(incoming.length()>0)memoryPlan=MemoryBridge.evaluate(memoryConfig,contextState,incoming,user,observed,token);
                    if(explicit!=null&&memoryPlan!=null&&memoryPlan.rejected!=null&&memoryPlan.rejected.length()>0){result.reply="这句话没有作为长期记忆保存。可以在记忆手账里手动写下你希望我记得的事。";result.emotion="calm";}
                }
                token.check();
                if(history!=null){result.memories=new JSONArray();result.self=new JSONArray();}
                final AgentReply output=result;final long completedEpoch=epoch;final String explicitWas=explicit;
                final java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
                Runnable completion=()->{if(!delivered.compareAndSet(false,true))return;release(token);if(db.epoch()!=completedEpoch){listener.error("记录已修改，本次已完成的回复不再显示");return;}listener.saved(turnId);listener.done(output.reply,output.emotion);if(explicitWas==null&&history==null)writeDiaryIfDue();};
                synchronized(AgentRuntime.class){if(!current(token))return;if(!db.commit(epoch,user,result.reply,result.emotion,result.memories,rememberEnabled(),proactive,explicit,memoryPlan,turnId,observed,result.self,true,null,history==null&&explicit==null?result.plan:null,result.moodLevel,rememberEnabled()?result.moodCause:"",result.moodChanged,null,history==null,asideEnabled()?result.aside:""))throw new IOException("记忆或记录已修改，本次回复未保存，请重新发送");result.emotion=db.mood(System.currentTimeMillis()).optString("emotion","calm");if(history==null)Nickname.committed(user);token.completed=completion;}
                main.post(completion);
                // Growth counts only a committed turn of yours; proactive turns and history questions earn nothing.
                if(history==null)grow();
            }catch(Exception e){if(proactive&&attemptStore!=null)try{synchronized(AgentRuntime.class){if(current(token))attemptStore.initiativeDecision(attemptEpoch,"error",Initiative.DEFAULT_MINUTES,System.currentTimeMillis());}}catch(Exception ignored){}String msg=e instanceof java.net.SocketTimeoutException?"等模型回复超时了，消息还在，可以重试。":e.getMessage();main.post(()->{if(current(token)){release(token);listener.error(msg==null?"连接失败，请检查网络和模型配置":msg);}});}
        });return true;
    }
    static void grow(){GrowthStore g=GrowthStore.quiet();if(g==null)return;try{g.met();g.chatted();g.sync();}catch(Exception e){Data.log("main","成长记录暂未保存");}}
    static boolean nightQuiet(){return nightQuiet(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));}
    static boolean nightQuiet(int hour){return Data.prefs().getBoolean("agent.quietNight",false)&&AgentPolicy.quiet(hour);}
    static boolean canSpeakNow(){
        PetService service=PetService.instance;if(service==null)return false;PetService.Pet pet=service.pets.get("main");
        if(pet==null||!pet.visible||pet.docked||!service.screenOn||MainActivity.active||Chat.isPetChatOpen("main"))return false;
        if(!Data.prefs().getBoolean("agent.proactive",false)||Data.prefs().getLong("agent.focusUntil",0)>System.currentTimeMillis()||nightQuiet())return false;
        KeyguardManager lock=service.getSystemService(KeyguardManager.class);NotificationManager nm=service.getSystemService(NotificationManager.class);
        return (lock==null||!lock.isKeyguardLocked())&&(nm==null||nm.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL);
    }
    static void heartbeat(PetService service) {
        // Runs every 30 s while the screen is on; proactive chat is off by default, so check that first.
        if(!canSpeakNow()||busy()||!PersonaManager.isInitialized("main")||!configured())return;
        try {
            KeyguardManager lock=service.getSystemService(KeyguardManager.class);if(lock!=null&&lock.isKeyguardLocked())return;
            NotificationManager nm=service.getSystemService(NotificationManager.class);if(nm!=null&&nm.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL)return;
            PetService.Pet pet=service.pets.get("main");AgentStore db=store();long now=System.currentTimeMillis();
            String day=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());
            if(!db.initiativeDue(now,lastTouch))return;
            db.reserveAttempt(day,now);
            send("",true,new Listener(){String turn;public void saved(String id){turn=id;}public void partial(String t){}public void done(String t,String e){
                if(PetService.instance==service&&canSpeakNow())express("main",e,t,turn);
            }public void error(String error){Data.log("main","主动陪伴请求未完成");}});
        }catch(Exception e){Data.log("main","主动陪伴暂不可用");}
    }
}
