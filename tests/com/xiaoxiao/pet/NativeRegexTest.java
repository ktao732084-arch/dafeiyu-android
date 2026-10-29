package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Run unchanged on both a desktop JVM and Android ART (app_process). No app data or model access. */
public final class NativeRegexTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args) {
        try{run();System.exit(0);}catch(Throwable failure){failure.printStackTrace(System.out);System.exit(1);}
    }
    static void run()throws Exception {
        System.out.println("Runtime: "+System.getProperty("java.vm.name")+" / "+System.getProperty("java.vm.version"));
        boolean acceptsFlag=true;try{Pattern.compile("",Pattern.UNICODE_CHARACTER_CLASS);}catch(IllegalArgumentException expected){acceptsFlag=false;}
        System.out.println("Platform accepts Unicode flag: "+acceptsFlag);
        String name="我叫k你以后就叫我kk吧";
        check(!NativeMemoryEngine.noise(name),"reported message accepted as a fact");
        check(NativeMemoryEngine.autoFacts(name).contains(name),"reported name captured without exception");
        check(NativeMemoryEngine.casual("好的！"),"casual recall path");
        check(NativeMemoryEngine.noise("53　"),"full-width trailing space");
        check(NativeMemoryEngine.noise("１２+３４"),"full-width digit noise");
        check(NativeMemoryEngine.noise("我的密码是：abc123456"),"secret rejected");
        check(NativeMemoryEngine.pattern("\\d+",0).matcher("１２٣").matches(),"Unicode decimal digits");
        check(NativeMemoryEngine.pattern("\\s+",0).matcher("　\u00a0").matches(),"Unicode whitespace");
        check(NativeMemoryEngine.pattern("\\w+",0).matcher("中文１２").matches(),"Unicode word characters");
        check(NativeMemoryEngine.pattern("hello",Pattern.CASE_INSENSITIVE).matcher("HELLO").matches(),"caller flags preserved");
        compileRules(NativeMemoryRules.DATA);
        long now=Instant.parse("2026-10-29T00:00:00Z").toEpochMilli();
        JSONObject row=MemoryLedger.record("name",name,"turn-name",false,now);
        JSONObject state=NativeMemoryEngine.evaluate(AgentStore.empty(0),new JSONArray().put(row),"",now,ZoneId.of("Asia/Shanghai"),null,new AgentTransport.Token()).state;
        check(state.getJSONArray("memories").length()==1,"name written by native engine");
        check(state.getJSONArray("memories").getJSONObject(0).getString("text").equals(name),"original name quote preserved");
        JSONObject dated=MemoryLedger.record("date","我下个月０９号要面试","turn-date",false,now);
        NativeMemoryEngine.initialize(dated,ZoneId.of("Asia/Shanghai"));
        check("2026-11-09".equals(dated.getJSONObject("engine").getString("event_date")),"full-width date grounded");
        JSONObject resolved=MemoryLedger.record("resolved","我明天（2026-10-30）要面试","turn-resolved",false,now);
        NativeMemoryEngine.initialize(resolved,ZoneId.of("Asia/Shanghai"));
        check(resolved.getString("text").equals(resolved.getJSONObject("engine").getString("content")),"resolved suffix not duplicated");
        JSONObject tomorrow=MemoryLedger.record("tomorrow","我明天要考试","turn-tomorrow",false,now);
        NativeMemoryEngine.initialize(tomorrow,ZoneId.of("Asia/Shanghai"));
        check("2026-10-30".equals(tomorrow.getJSONObject("engine").getString("event_date")),"ordinary relative date");
        System.out.println("PASS: "+checks+" native Unicode regex checks");
    }
    static void compileRules(JSONObject rules)throws Exception {
        for(String key:new String[]{"SECRET_PATTERNS","NOISE_PATTERNS","DISTRACTION_PATTERNS","CONVERSATION_NOISE"})compileArray(rules.getJSONArray(key));
        JSONObject fallback=rules.getJSONObject("FALLBACK_NOISE");Iterator<String> names=fallback.keys();while(names.hasNext())compileArray(fallback.getJSONArray(names.next()));
        check(NativeMemoryEngine.pattern(rules.getString("QUESTION_PATTERN"),0)!=null,"question rule compiles");
    }
    static void compileArray(JSONArray values){for(int i=0;i<values.length();i++)check(NativeMemoryEngine.pattern(values.optString(i),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)!=null,"rule compiles "+i);}
}
