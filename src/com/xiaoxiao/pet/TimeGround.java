package com.xiaoxiao.pet;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/**
 * Java port of the memory engine's temporal.py: annotates relative dates with absolute ones, so
 * "我下周三有考试" said on 2026-10-28 reads "我下周三（2026-11-04）有考试" forever, and reports the
 * first day-precise date as the event date. Same rules and same deliberate gaps as the Python original:
 * "每周三" and a bare "周三" stay as they are, and "X月X日" without a year is not guessed.
 */
final class TimeGround {
    static final class Result {
        final String text;final LocalDate event;
        Result(String text,LocalDate event){this.text=text;this.event=event;}
    }
    private static final Map<String,Integer> WEEKDAYS=new HashMap<>(),DAY_OFFSETS=new LinkedHashMap<>();
    private static final Pattern TOKEN,ANNOTATED=Pattern.compile("^（\\d{4}");
    static {
        String[] w={"一","二","三","四","五","六","日","天","七"};int[] wi={0,1,2,3,4,5,6,6,6};
        for(int i=0;i<w.length;i++)WEEKDAYS.put(w[i],wi[i]);
        String[] d={"大前天","前天","昨天","昨晚","昨日","今天","今晚","今早","今日","明天","明早","明晚","明日","后天","大后天"};int[] di={-3,-2,-1,-1,-1,0,0,0,0,1,1,1,1,2,3};
        for(int i=0;i<d.length;i++)DAY_OFFSETS.put(d[i],di[i]);
        // Longer words first, so "大后天" is not taken by "后天".
        List<String> days=new ArrayList<>(DAY_OFFSETS.keySet());days.sort((a,b)->b.length()-a.length());
        TOKEN=Pattern.compile("("+String.join("|",days)+")"
            +"|(?<!每)(下下|下|这|本|上)(?:个)?(?:周|星期|礼拜)([一二三四五六日天七])?"
            +"|(?<!每)(下|这|本|上)(?:个)?月(?:(\\d{1,2})[号日])?"
            +"|(?<!每)(明年|今年|去年)",Pattern.UNICODE_CHARACTER_CLASS);
    }

    static Result ground(String text,LocalDate observed) {
        StringBuilder out=new StringBuilder();int last=0;LocalDate event=null;Matcher m=TOKEN.matcher(text);
        while(m.find()) {
            if(ANNOTATED.matcher(text.substring(m.end())).find())continue;
            String label=null;LocalDate day=null;
            if(m.group(1)!=null){day=observed.plusDays(DAY_OFFSETS.get(m.group(1)));label=day.toString();}
            else if(m.group(2)!=null){
                int shift="上".equals(m.group(2))?-1:"下".equals(m.group(2))?1:"下下".equals(m.group(2))?2:0;
                LocalDate monday=observed.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(shift);
                if(m.group(3)!=null){day=monday.plusDays(WEEKDAYS.get(m.group(3)));label=day.toString();}
                else label=monday+" 当周";
            }
            else if(m.group(4)!=null){
                int shift="上".equals(m.group(4))?-1:"下".equals(m.group(4))?1:0;
                YearMonth month=YearMonth.from(observed).plusMonths(shift);
                if(m.group(5)!=null){
                    int dom=Integer.parseInt(m.group(5));
                    if(dom<1||dom>month.lengthOfMonth())continue;
                    day=month.atDay(dom);label=day.toString();
                }
                else label=month.toString();
            }
            else if(m.group(6)!=null){
                int shift="去年".equals(m.group(6))?-1:"明年".equals(m.group(6))?1:0;
                label=(observed.getYear()+shift)+"年";
            }
            if(label==null)continue;
            out.append(text,last,m.end()).append("（").append(label).append("）");last=m.end();
            if(event==null&&day!=null)event=day;
        }
        out.append(text.substring(last));
        return new Result(out.toString(),event);
    }
}
