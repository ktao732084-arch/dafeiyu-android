package com.xiaoxiao.pet;
import java.time.*;import java.util.*;import java.util.regex.*;import org.json.*;

/** Android-native port of upstream temporal.py (MIT), retaining original quote separately. */
final class NativeMemoryTime {
    static final Map<String,Integer> DAYS=new LinkedHashMap<>();
    static {String[] words={"大前天","大后天","前天","昨天","昨晚","昨日","今天","今晚","今早","今日","明天","明早","明晚","明日","后天"};int[] shifts={-3,3,-2,-1,-1,-1,0,0,0,0,1,1,1,1,2};for(int i=0;i<words.length;i++)DAYS.put(words[i],shifts[i]);}
    static final Pattern TOKENS=UnicodeRegex.compile("("+String.join("|",DAYS.keySet())+")|(?<!每)(下下|下|这|本|上)(?:个)?(?:周|星期|礼拜)([一二三四五六日天七])?|(?<!每)(下|这|本|上)(?:个)?月(?:(\\d{1,2})[号日])?|(?<!每)(明年|今年|去年)",0); // Includes full-width digits on Android and desktop JVMs.
    static final Pattern RESOLVED=UnicodeRegex.compile("^（\\d{4}.*",Pattern.DOTALL);
    static void ground(JSONObject row,JSONObject e,ZoneId zone,boolean permanent)throws Exception {
        if(!e.isNull("valid_at")&&!e.optString("valid_at").isEmpty())return;
        long at=row.optLong("at");LocalDate observed=Instant.ofEpochMilli(at).atZone(zone).toLocalDate();String text=row.getString("text");Matcher m=TOKENS.matcher(text);StringBuilder resolved=new StringBuilder();int end=0;LocalDate event=null;
        while(m.find()){
            if(RESOLVED.matcher(text.substring(m.end())).matches())continue;
            String label=null;LocalDate date=null;
            try{
                if(m.group(1)!=null){date=observed.plusDays(DAYS.get(m.group(1)));}
                else if(m.group(2)!=null){String p=m.group(2);int shift="下下".equals(p)?2:"下".equals(p)?1:"上".equals(p)?-1:0;LocalDate monday=observed.minusDays(observed.getDayOfWeek().getValue()-1).plusWeeks(shift);String day=m.group(3);if(day!=null){int d="一二三四五六".indexOf(day);date=monday.plusDays(d<0?6:d);}else label=monday+" 当周";}
                else if(m.group(4)!=null){String p=m.group(4);LocalDate first=observed.withDayOfMonth(1).plusMonths("下".equals(p)?1:"上".equals(p)?-1:0);if(m.group(5)!=null)date=first.withDayOfMonth(Integer.parseInt(m.group(5)));else label=String.format(Locale.ROOT,"%04d-%02d",first.getYear(),first.getMonthValue());}
                else if(m.group(6)!=null)label=(observed.getYear()+("明年".equals(m.group(6))?1:"去年".equals(m.group(6))?-1:0))+"年";
            }catch(DateTimeException invalid){continue;}
            if(date!=null){label=date.toString();if(event==null)event=date;}if(label==null)continue;
            resolved.append(text,end,m.end()).append('（').append(label).append('）');end=m.end();
        }
        resolved.append(text.substring(end));e.put("content",resolved.toString()).put("valid_at",Instant.ofEpochMilli(at).toString()).put("observed_zone",zone.getId());
        if(event!=null){e.put("event_date",event.toString());if(!event.isBefore(observed)&&!permanent)e.put("expires_at",event.plusDays(1).atStartOfDay(zone).toInstant().toString());}
    }
}
