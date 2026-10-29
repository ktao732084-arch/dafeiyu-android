package com.xiaoxiao.pet;

import org.json.*;

/** Validated scheduling metadata. Missing fields never become an invented model choice. */
final class InitiativePlan {
    Integer minutes;
    String source="missing",availability="unchanged",evidence="",action="",topic="";
    boolean repaired;
    static InitiativePlan parse(JSONObject o){
        InitiativePlan p=new InitiativePlan();if(o==null)return p;
        Object value=o.opt("next_check_minutes");
        if(value instanceof Number){double n=((Number)value).doubleValue();if(!Double.isNaN(n)&&!Double.isInfinite(n)&&n==Math.rint(n)&&n>=2&&n<=180){p.minutes=(int)n;p.source="model";}}
        String a=o.optString("availability");if("busy".equals(a)||"available".equals(a))p.availability=a;
        p.evidence=AgentPolicy.clip(o.optString("availability_evidence"),120);
        p.action=o.optString("initiative");p.topic=AgentPolicy.clip(o.optString("topic"),40).trim();return p;
    }
    boolean valid(){return minutes!=null;}
    JSONObject json()throws Exception{return new JSONObject().put("source",source).put("minutes",minutes==null?JSONObject.NULL:minutes).put("availability",availability).put("evidence",evidence).put("action",action).put("topic",topic).put("repaired",repaired);}
    /** Only direct, current, first-person declarations; historical/quoted/conditional questions are excluded. */
    static String explicitAvailability(String user){
        if(user==null||user.matches("(?s).*[?？吗\"“”「」『』].*")||user.matches("(?s).*(如果|假如|假设|举例|他说|她说|昨天|之前|上次|记得|翻译).*"))return "unchanged";
        String last="unchanged";int at=-1;
        String[] free={"我忙完了","我现在不忙","我不忙了","我有空了","我现在有空","我回来了","现在可以陪你聊","现在可以聊天"};
        String[] busy={"我现在有点忙","我现在很忙","我现在忙","我有点忙","我先忙","等我忙完","等忙完了再","等我回来再","先别打扰我","先不要打扰我"};
        for(String s:free){int i=user.lastIndexOf(s);if(i>at){at=i;last="available";}}
        for(String s:busy){int i=user.lastIndexOf(s);if(i>at){at=i;last="busy";}}return last;
    }
    void ground(String user,boolean proactive){
        if(proactive){availability="unchanged";evidence="";return;}
        String explicit=explicitAvailability(user);
        if(!"unchanged".equals(explicit)){availability=explicit;evidence=AgentPolicy.clip(user,120);}
        else if(evidence.isEmpty()||!user.contains(evidence)){availability="unchanged";evidence="";}
    }
    static boolean nag(String reply){return reply.matches("(?s).*(不打扰|不催|不唠叨|不吭声|没人理我|没理我|没接.{0,12}茬|还没.{0,8}(回答|回复)|先.{0,8}安静|等你(回来|忙完|有空)).*");}
    boolean quiet(String reply,JSONObject state){
        if(Initiative.quiet(reply)||"quiet".equals(action)||nag(reply))return true;
        JSONArray used=state.optJSONArray("initiativeTopics");
        if(!topic.isEmpty()&&used!=null)for(int i=0;i<used.length();i++)if(topic.equals(used.optString(i)))return true;
        return false;
    }
    static String sourceLabel(String source){switch(source){case "model":return "模型本轮安排";case "model_repair":return "模型补齐的安排";case "user_busy":return "按你说的先忙，等你回来";case "missing":return "模型未给出有效安排，已暂停主动消息";default:return "沿用之前的安排";}}
}
