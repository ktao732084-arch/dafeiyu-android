package com.xiaoxiao.pet;
import android.app.*;import android.os.SystemClock;import org.json.*;

/** A short response to a burst of deliberate pet gestures, separate from idle/proactive scheduling. */
final class TouchReply {
    static final int MIN_EVENTS=3,MAX_EVENTS=12;
    static final long BURST_MS=15000,SETTLE_MS=1800,EXPIRE_MS=30000,REQUEST_GAP_MS=20000;
    static final String HISTORY_EVENT="（用户对桌宠的触摸互动，不是文字发言）";
    static final class Request {
        final long generation,epoch,lastAt;final JSONObject evidence;
        Request(long generation,long epoch,long lastAt,JSONObject evidence){this.generation=generation;this.epoch=epoch;this.lastAt=lastAt;this.evidence=evidence;}
    }
    static final class Batch {
        JSONArray events=new JSONArray();long generation,epoch,firstAt,lastAt,lastAttempt=-REQUEST_GAP_MS;int count;
        void add(String pet,String kind,String note,long wall,long now,long epoch)throws JSONException {
            if(count>0&&(this.epoch!=epoch||now<lastAt||now-firstAt>BURST_MS))clear();
            if(count==0)firstAt=now;this.epoch=epoch;lastAt=now;generation++;count++;
            events.put(new JSONObject().put("pet",pet).put("kind",kind).put("note",AgentPolicy.clip(note,120)).put("at",wall));
            while(events.length()>MAX_EVENTS)events.remove(0);
        }
        void clear(){events=new JSONArray();count=0;generation++;}
        boolean expired(long now){return count==0||now<lastAt||now-lastAt>EXPIRE_MS;}
        boolean ready(long now){return count>=MIN_EVENTS&&!expired(now)&&now-lastAt>=SETTLE_MS&&now-lastAttempt>=REQUEST_GAP_MS;}
        Request take(long now)throws JSONException {
            if(!ready(now))return null;
            Request request=new Request(generation,epoch,lastAt,new JSONObject().put("count",count).put("events",new JSONArray(events.toString())));
            events=new JSONArray();count=0;lastAttempt=now;return request;
        }
        boolean current(Request request){return generation==request.generation;}
    }
    static boolean enabled(){return Data.prefs().getBoolean("agent.touchReply",true);}
    static void cancel(PetService.Pet pet){
        pet.touchReplies.clear();if(pet.touchReplyCheck!=null){pet.owner().handler.removeCallbacks(pet.touchReplyCheck);pet.touchReplyCheck=null;}
        AgentRuntime.interruptTouch(pet);
    }
    static void event(PetService service,PetService.Pet pet,String kind,String note,boolean react){
        long wall=System.currentTimeMillis(),epoch=AgentRuntime.touch(note,kind,pet.id,wall);
        if(!react||!enabled()||epoch<0){cancel(pet);return;}
        try{pet.touchReplies.add(pet.id,kind,note,wall,SystemClock.uptimeMillis(),epoch);}catch(JSONException invalid){cancel(pet);return;}
        if(pet.touchReplies.count<MIN_EVENTS)return;
        if(pet.touchReplyCheck!=null)service.handler.removeCallbacks(pet.touchReplyCheck);
        pet.touchReplyCheck=()->pump(service,pet);service.handler.postDelayed(pet.touchReplyCheck,SETTLE_MS);
    }
    static boolean allowed(PetService service,PetService.Pet pet){
        if(PetService.instance!=service||service.pets.get(pet.id)!=pet||!enabled()||!pet.visible||pet.frame==null||pet.docked||pet.docking||!service.animating())return false;
        if(MainActivity.active||Chat.isPetChatOpen(pet.id)||Data.prefs().getLong("agent.focusUntil",0)>System.currentTimeMillis()||AgentRuntime.nightQuiet())return false;
        KeyguardManager lock=service.getSystemService(KeyguardManager.class);NotificationManager nm=service.getSystemService(NotificationManager.class);
        if(lock!=null&&lock.isKeyguardLocked()||nm!=null&&nm.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL)return false;
        try{return !AgentRuntime.store().snapshot().optBoolean("proactiveWaiting");}catch(Exception unavailable){return false;}
    }
    static boolean free(PetService.Pet pet){return !pet.dragging&&!pet.moving&&!pet.thrown&&!pet.busy&&pet.scene==null&&pet.bubble==null&&(pet.speech==null||!pet.speech.active);}
    static void pump(PetService service,PetService.Pet pet){
        long now=SystemClock.uptimeMillis();
        if(pet.touchReplies.expired(now)||!allowed(service,pet)||!AgentRuntime.configured()||!PersonaManager.isInitialized(pet.role)){cancel(pet);return;}
        try{
            if(AgentRuntime.store().epoch()!=pet.touchReplies.epoch){cancel(pet);return;}
            if(!free(pet)||AgentRuntime.busy()||!pet.touchReplies.ready(now)){service.handler.postDelayed(pet.touchReplyCheck,500);return;}
            Request request=pet.touchReplies.take(now);pet.touchReplyCheck=null;
            if(request!=null)AgentRuntime.respondToTouch(service,pet,request);
        }catch(Exception unavailable){cancel(pet);Data.log(pet.role,"互动回应暂未完成");}
    }
    static JSONArray prompt(JSONArray messages,Request request)throws Exception {
        JSONObject system=messages.getJSONObject(0),last=messages.getJSONObject(messages.length()-1);
        system.put("content",system.getString("content")+"\n本轮是用户刚刚通过轻点、挪动或喂食发起的互动回应，不是定时主动聊天。只针对interaction_event中实际发生的动作，用已确定的性格自然接一句短话（最多两句、80字），可以俏皮，也可以表达被连续碰到的感受；不要编造动作之外的经历，不追问新话题，不索取回复。不要改变用户忙闲或主动时间；本轮只读取reply和emotion/mood字段，memories与self留空。若确实不适合回应，reply为[安静陪伴]。");
        String context=last.getString("content");int end=context.indexOf("</背景>");
        if(end>=0)context=context.substring(0,end+5);
        last.put("content",context+"\n"+HISTORY_EVENT+"\n"+new JSONObject().put("interaction_event",request.evidence));return messages;
    }
    static String text(String reply){
        String text=AgentPolicy.replyText(reply==null?"":reply).trim();
        return text.isEmpty()||text.contains("[安静陪伴]")||text.codePointCount(0,text.length())>80||ReplySegments.previewOnly(text)?null:text;
    }
}
