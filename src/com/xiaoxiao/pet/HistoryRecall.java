package com.xiaoxiao.pet;

import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Explicit conversational history lookup, not extraction of new facts or a second model call. */
final class HistoryRecall {
    static final int MAX_SCAN=1000,MAX_RESULTS=24,MAX_PROMPT_CHARS=14000;
    static final long MAX_SCAN_BYTES=8L*1024*1024;
    static final Pattern DATE=Pattern.compile("(?<![0-9])(?:(\\d{4})[年/-])?(\\d{1,2})[月/-](\\d{1,2})(?:日|号)?(?![0-9])");
    static final Pattern WEEK=Pattern.compile("(上上|上|本|这)(?:个)?(?:周|星期|礼拜)([一二三四五六日天七])?");
    static final Pattern MONTH=Pattern.compile("(上上|上|本|这)(?:个)?月(?:(\\d{1,2})[日号])?");
    static final Pattern RECENT=Pattern.compile("(?:最近|过去|近)([一二两三四五六七八九十0-9]+)(?:个)?(天|日|周|星期)");
    static final class Plan {
        long from,to;String label="",zone,topic="",error;LocalDate start,end;
        Plan(ZoneId zone){this.zone=zone.getId();}
        boolean valid(){return error==null;}
    }
    static final class Scan {int total,examined,unreadable;boolean limited;final List<JSONObject> rows=new ArrayList<>();}
    static final class Result {
        final Plan plan;final JSONObject evidence;
        Result(Plan plan,JSONObject evidence){this.plan=plan;this.evidence=evidence;}
        boolean found(){return evidence.optJSONArray("records")!=null&&evidence.optJSONArray("records").length()>0;}
        String fallback(){
            String status=evidence.optString("status");
            if("clarify".equals(status))return plan.error;
            if("error".equals(status))return "这次聊天记录没能读出来，请稍后再试。我不能凭印象编出那段时间的聊天。";
            if(found())return null;
            if(evidence.optInt("matching_scanned_turns")>0)return "我找到了"+plan.label+"的记录，但内容超过了这次可用的摘录范围。可以按某一天或更具体的话题再查。";
            String scope=plan.label+(plan.topic.isEmpty()?"":"关于「"+plan.topic+"」");
            return evidence.optBoolean("scan_complete")?"我在"+scope+"没有找到可用的聊天记录。可能当时还没保存或后来清理过，不能据此判断我们没聊过。":"这次在"+scope+"的部分记录中没找到对应内容。还有记录未能检索完，可以缩小到某一天再查。";
        }
        String offline(){
            if(!found())return fallback();StringBuilder out=new StringBuilder("我查了"+plan.label+"的记录，先摘几条原话：");JSONArray rows=evidence.optJSONArray("records");
            for(int i=0;i<Math.min(3,rows.length());i++){JSONObject row=rows.optJSONObject(i);out.append("\n").append(row.optString("sent_local")).append(" 你：").append(row.optString("user_excerpt","（主动消息）"));}
            return out+"\n这些是部分原文摘录；接通模型后可以帮你归纳。";
        }
    }
    static boolean intent(String q){
        if(q.matches("(?s).*(?:今天|今晚|明天).*(?:想聊|聊点|聊些|准备聊|打算聊|要聊).*"))return false;
        if(q.matches("(?s).*(?:今天|今晚)聊什么[？?]?$")&&!q.contains("过"))return false;
        boolean chat=q.matches("(?s).*(?:聊|说过|说了|说的|提过|提的|提到|谈过|谈了|讨论|对话|聊天记录|推荐过|给过|回答过|建议过).*");
        boolean ask=q.matches("(?s).*(?:什么|哪些|吗|呢|啥|[？?]|回顾|总结|查找|查一下|找一下|翻一下|记得|回忆|那个|那部|那件).*");
        return chat&&ask||q.matches("(?s).*(?:回顾|回忆|总结)(?:一下)?(?:我们|咱们|咱俩)?(?:上周|上个星期|上个月|昨天|前天|这周|本周|最近).*" );
    }
    static Plan plan(String raw,long now,ZoneId zone){
        String q=raw==null?"":raw.trim();if(!intent(q))return null;
        if(!q.matches("(?s).*(?:[0-9]+[年/月-]|上周|本周|这周|上上周|星期|礼拜|上个?月|本月|这月|昨天|昨日|昨晚|前天|今天|今日|之前|以前|前阵子|当时|那天|历史|最近|过去|近[一二两三四五六七八九十0-9]).*"))return null;
        Plan p=new Plan(zone);LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();String rest=q;
        try{
            Matcher dates=DATE.matcher(q);List<LocalDate> found=new ArrayList<>();List<String> tokens=new ArrayList<>();
            while(dates.find()){boolean implicit=dates.group(1)==null;int year=implicit?today.getYear()+(q.contains("前年")?-2:q.contains("去年")?-1:0):Integer.parseInt(dates.group(1));LocalDate day=LocalDate.of(year,Integer.parseInt(dates.group(2)),Integer.parseInt(dates.group(3)));if(implicit&&!q.contains("今年")&&!q.contains("去年")&&!q.contains("前年")&&day.isAfter(today))day=day.minusYears(1);found.add(day);tokens.add(dates.group());}
            if(!found.isEmpty()){
                if(found.size()>2)return unclear(p,"一次可以查一个日期或一段连续日期，请把范围写清楚。比如：10月19日到10月25日聊过什么？");
                if(found.size()==2&&!q.matches("(?s).*(?:到|至|~|～|—).*"))return unclear(p,"你想查两个单独日期，还是它们之间的一整段时间？请写清起止范围。");
                p.start=found.get(0);p.end=found.size()==2?found.get(1):p.start;
                if(found.size()==1){Matcher tail=Pattern.compile("(?:到|至|~|～|—)\\s*(\\d{1,2})[日号]").matcher(q);if(tail.find()){p.end=p.start.withDayOfMonth(Integer.parseInt(tail.group(1)));rest=rest.replace(tail.group(),"");}}
                if(p.end.isBefore(p.start))return unclear(p,"结束日期早于开始日期，请写明完整的起止日期和年份。");
                for(String token:tokens)rest=rest.replace(token,"");
            }else{
                Matcher recent=RECENT.matcher(q),week=WEEK.matcher(q),month=MONTH.matcher(q);
                if(recent.find()){
                    long days=(long)number(recent.group(1))*(recent.group(2).equals("天")||recent.group(2).equals("日")?1:7);
                    if(days<1||days>366)return unclear(p,"请把范围缩小到一年内，或写明具体的开始和结束日期。");int n=(int)days;
                    p.start=today.minusDays(n-1);p.end=today;rest=rest.replace(recent.group(),"");
                }else if(week.find()){
                    if(q.substring(week.end()).matches("(?s)^\\s*(?:到|至|~|～|—).*"))return unclear(p,"请把这段范围写成具体日期，例如10月19日到10月23日。");
                    int shift="上上".equals(week.group(1))?-2:"上".equals(week.group(1))?-1:0;LocalDate monday=today.minusDays(today.getDayOfWeek().getValue()-1).plusWeeks(shift);
                    String day=week.group(2);p.start=day==null?monday:monday.plusDays("一二三四五六".indexOf(day)<0?6:"一二三四五六".indexOf(day));p.end=day==null?monday.plusDays(6):p.start;rest=rest.replace(week.group(),"");
                }else if(month.find()){
                    int shift="上上".equals(month.group(1))?-2:"上".equals(month.group(1))?-1:0;p.start=today.withDayOfMonth(1).plusMonths(shift);p.end=p.start.plusMonths(1).minusDays(1);if(month.group(2)!=null){p.start=p.start.withDayOfMonth(Integer.parseInt(month.group(2)));p.end=p.start;}rest=rest.replace(month.group(),"");
                }else{
                    String[] days={"大前天","前天","昨天","昨日","昨晚","今天","今日"};int[] shifts={-3,-2,-1,-1,-1,0,0};
                    for(int i=0;i<days.length;i++)if(q.contains(days[i])){p.start=today.plusDays(shifts[i]);p.end=p.start;rest=rest.replace(days[i],"");break;}
                    if(p.start==null)return unclear(p,"你想查哪段时间的聊天？可以说「上周聊过什么」「昨天提到的电影」，或写明具体日期。");
                }
            }
            if(p.start.isAfter(today))return unclear(p,"这个日期还没有到。你想查哪一个过去的日期？");
            p.from=p.start.atStartOfDay(zone).toInstant().toEpochMilli();p.to=Math.min(p.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),now+1);String part="";
            if(p.end.isAfter(today))p.end=today;
            if(p.start.equals(p.end)){
                int begin=0,end=24;if(q.contains("昨晚")||q.contains("晚上")){begin=18;}else if(q.contains("下午")){begin=12;end=18;}else if(q.contains("中午")){begin=11;end=14;}else if(q.contains("上午")||q.contains("早上")){begin=6;end=12;}else if(q.contains("凌晨")){end=6;}
                if(begin!=0||end!=24)part=String.format(Locale.ROOT," %02d:00–%02d:00",begin,end);
                p.from=p.start.atTime(begin,0).atZone(zone).toInstant().toEpochMilli();p.to=Math.min(end==24?p.start.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli():p.start.atTime(end,0).atZone(zone).toInstant().toEpochMilli(),now+1);
            }
            if(p.to<=p.from)return unclear(p,"这个时间段还没有发生，请换一个已经过去的时间段。");
            p.label=(p.start.equals(p.end)?p.start.toString():p.start+" 至 "+p.end)+part;
            rest=rest.replaceAll("前年|去年|今年|晚上|下午|中午|上午|早上|凌晨|到|至|~|～|—","");p.topic=topic(rest);return p;
        }catch(DateTimeException|NumberFormatException invalid){return unclear(p,"这个日期无法识别，请核对年月日。比如：2026年10月19日到10月25日聊过什么？");}
    }
    static Plan unclear(Plan p,String message){p.error=message;return p;}
    static int number(String s){if(s.matches("[0-9]+"))return Integer.parseInt(s);if(s.equals("两"))return 2;int ten=s.indexOf('十');if(ten>=0){int a=ten==0?1:"零一二三四五六七八九".indexOf(s.charAt(0));int b=ten==s.length()-1?0:"零一二三四五六七八九".indexOf(s.charAt(ten+1));return a*10+b;}return "零一二三四五六七八九".indexOf(s);}
    static String topic(String text){
        String s=text;for(String word:new String[]{"帮我总结一下","帮我回顾一下","帮我查一下","聊天记录","对话记录","总结一下","回顾一下","查找一下","记不记得","还记得","有没有","叫什么","提到过","提到的","讨论过","讨论的","说过的","聊过的","说的","聊的","聊过","聊了","说过","提过","讨论","聊天","聊","回顾","总结","查找","查一下","找一下","翻一下","回忆","记得","什么","哪些","我们","咱们","咱俩","关于","帮我","那个","那部","那件","说了","谈过","你","我","的","吗","呢","呀","啊","啥"})s=s.replace(word,"");
        s=AgentPolicy.clip(s.replaceAll("[^\\p{L}\\p{N}]",""),60);return Arrays.asList("建议","内容","话题","事情","事","东西","都","些","都些","了").contains(s)?"":s;
    }
    static JSONObject excerpt(JSONObject row,Plan plan)throws Exception{
        String user=row.optString("user"),visible=row.has("displayed")?ReplySegments.visible(row):"";ZoneId zone=ZoneId.of(plan.zone);boolean unknownZone=!row.has("timeZone");
        if(!unknownZone)try{zone=ZoneId.of(row.getString("timeZone"));}catch(DateTimeException invalid){unknownZone=true;}
        long at=row.optLong("receivedAt",row.optLong("at"));
        JSONObject out=new JSONObject().put("id",ChatArchive.id(row)).put("received_at",at).put("sent_local",Instant.ofEpochMilli(at).atZone(zone).toString()).put("user_excerpt",clip(user,plan.topic,180)).put("assistant_shown_excerpt",clip(visible,plan.topic,180))
            .put("user_truncated",user.length()>180).put("assistant_truncated",visible.length()>180).put("assistant_had_unshown_parts",!ReplySegments.label(row).isEmpty()).put("proactive",row.optBoolean("proactive"));
        out.put("assistant_visibility",row.has("displayed")?(visible.isEmpty()?"not_shown":"shown_prefix"):"legacy_unknown");
        out.put("source_zone_unknown",unknownZone);if(!zone.getId().equals(plan.zone))out.put("sent_in_query_zone",Instant.ofEpochMilli(at).atZone(ZoneId.of(plan.zone)).toString());
        if(!row.has("displayed")){out.put("assistant_saved_excerpt",clip(row.optString("reply"),plan.topic,180));out.put("assistant_truncated",row.optString("reply").length()>180);}
        for(String field:new String[]{"repliedAt","timeZone","displayedAt","lastDisplayedAt","displayTimeZone"})if(row.has(field))out.put(field,row.get(field));return out;
    }
    static String clip(String s,String topic,int limit){
        if(s.length()<=limit)return s;int hit=topic.isEmpty()?-1:s.indexOf(topic);
        if(hit<0&&!topic.isEmpty()){String lower=s.toLowerCase(Locale.ROOT);for(String term:AgentPolicy.tokens(topic)){int at=lower.indexOf(term);if(at>=0&&(hit<0||at<hit))hit=at;}}
        int start=hit<0?0:Math.min(s.length()-limit,Math.max(0,hit-40));if(start>0&&Character.isLowSurrogate(s.charAt(start)))start--;
        int end=Math.min(s.length(),start+limit);if(end<s.length()&&Character.isHighSurrogate(s.charAt(end-1)))end--;return (start>0?"…":"")+s.substring(start,end)+(end<s.length()?"…":"");
    }
    static Result build(Plan p,Scan scan)throws Exception{
        JSONObject result=new JSONObject().put("status",p.valid()?"ok":"clarify").put("query_zone",p.zone).put("topic",p.topic).put("range_basis","每轮用户发送时间；主动消息按发起时间");
        if(!p.valid())return new Result(p,result.put("message",p.error).put("records",new JSONArray()));
        result.put("from_inclusive",p.from).put("to_exclusive",p.to).put("range",p.label).put("matched_turns",scan.total).put("scanned_turns",scan.examined).put("unreadable_turns",scan.unreadable).put("scan_complete",!scan.limited&&scan.unreadable==0);
        List<JSONObject> relevant=new ArrayList<>();for(JSONObject t:scan.rows)if(p.topic.isEmpty()||AgentPolicy.relevance(p.topic,t.optString("user")+" "+ReplySegments.visible(t))>0)relevant.add(t);
        boolean topicFallback=!p.topic.isEmpty()&&relevant.isEmpty()&&!scan.rows.isEmpty();if(topicFallback)relevant.addAll(scan.rows);
        result.put("topic_match",topicFallback?"time_range_candidates":p.topic.isEmpty()?"overview":"literal_matches");
        List<JSONObject> chosen=new ArrayList<>();
        if(!p.topic.isEmpty()&&!topicFallback){relevant.sort((a,b)->Double.compare(AgentPolicy.relevance(p.topic,b.optString("user")+" "+ReplySegments.visible(b)),AgentPolicy.relevance(p.topic,a.optString("user")+" "+ReplySegments.visible(a))));chosen.addAll(relevant.subList(0,Math.min(MAX_RESULTS,relevant.size())));}
        else if(!relevant.isEmpty()){
            LinkedHashMap<String,JSONObject> days=new LinkedHashMap<>();for(JSONObject t:relevant)days.putIfAbsent(Instant.ofEpochMilli(t.optLong("at")).atZone(ZoneId.of(p.zone)).toLocalDate().toString(),t);
            List<JSONObject> firsts=new ArrayList<>(days.values());int n=Math.min(MAX_RESULTS,firsts.size());for(int i=0;i<n;i++)chosen.add(firsts.get(n==1?0:(int)((long)i*(firsts.size()-1)/(n-1))));
            for(int i=0;i<MAX_RESULTS&&chosen.size()<MAX_RESULTS;i++){JSONObject t=relevant.get((int)((long)i*(relevant.size()-1)/(MAX_RESULTS-1)));if(!chosen.contains(t))chosen.add(t);}
        }
        List<JSONObject> excerpts=new ArrayList<>();int chars=0;
        for(JSONObject t:chosen){JSONObject row=excerpt(t,p);int length=row.toString().length();if(chars+length>MAX_PROMPT_CHARS-1800)continue;excerpts.add(row);chars+=length;}
        excerpts.sort(Comparator.comparingLong(t->t.optLong("received_at")));JSONArray rows=new JSONArray(excerpts);
        boolean partial=scan.limited||scan.unreadable>0||rows.length()<relevant.size();for(int i=0;i<rows.length();i++)partial|=rows.getJSONObject(i).optBoolean("user_truncated")||rows.getJSONObject(i).optBoolean("assistant_truncated");result.put("records",rows).put("selected_turns",rows.length()).put("matching_scanned_turns",relevant.size()).put("partial",partial);
        if(rows.length()==0)result.put("status",scan.unreadable>0&&scan.rows.isEmpty()?"error":"empty");return new Result(p,result);
    }
}
