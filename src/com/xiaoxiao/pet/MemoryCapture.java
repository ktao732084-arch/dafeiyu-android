package com.xiaoxiao.pet;

import java.util.*;
import java.util.regex.Pattern;

/** Automatic facts must be assertions, not the text the user asked us to write for someone else. */
final class MemoryCapture {
    private static final Pattern TASK=Pattern.compile("(?s)^(?:(?:请|麻烦|能不能|能否|可以)(?:你)?\\s*)?(?:帮我|给我|替我).*(?:写|改|润色|翻译|复述|生成|输出|概括|总结|解释|算)"
        +"|^(?:这|那|刚才这|刚才那|刚才的).{0,8}(?:句|消息|文案|文字|文本|说法).{0,8}(?:改|写|润色|翻译|复述|输出)"
        +"|^(?:请你?|麻烦你?).*(?:改写|重写|润色|翻译|复述|输出|生成文案)"
        +"|^(?:只要|只需|只输出).*(?:一句|结果|答案|正文|那句话)");
    private static final Pattern LOCAL_DIRECTION=Pattern.compile("^(?:你)?(?:先|这次|本次)?(?:别|不要|不用)(?:再|继续)?(?:给我|帮我|安排|推荐|列|加|输出|点评)");
    private static final Pattern REMEMBER=Pattern.compile("^(?:(?:请|麻烦|能不能|可以)(?:你)?)?(?:帮我|给我)?(?:记住|记下|记牢)");
    private static final Pattern QUESTION=Pattern.compile("(?s).*(?:[?？]\\s*|[吗呢么][。.!！\\s~～…]*)$");
    static boolean instruction(String text){String t=text.trim().replaceFirst("^(?:再|先|也|顺便|另外|那就)[，,\\s]*","");return !REMEMBER.matcher(t).find()&&(TASK.matcher(t).find()||LOCAL_DIRECTION.matcher(t).find());}

    /** Keep punctuation inside quoted task material attached to its request. */
    static List<int[]> statements(String text){
        List<int[]> out=new ArrayList<>();StringBuilder closing=new StringBuilder();int start=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);int open="“‘「『".indexOf(c);
            if(open>=0)closing.append("”’」』".charAt(open));
            else if(closing.length()>0&&c==closing.charAt(closing.length()-1))closing.setLength(closing.length()-1);
            else if(c=='\"'&&(i==0||text.charAt(i-1)!='\\'))closing.append('\"');
            if(closing.length()==0&&"。！？!?；;\n".indexOf(c)>=0){out.add(new int[]{start,i+1});start=i+1;}
        }
        if(start<text.length())out.add(new int[]{start,text.length()});return out;
    }
    static boolean eligible(String user,String quote){
        if(!AgentPolicy.validEvidence(user,quote)||instruction(quote))return false;
        List<int[]> spans=statements(user);
        for(int at=user.indexOf(quote);at>=0;at=user.indexOf(quote,at+1)){
            boolean allowed=true;
            for(int[] span:spans)if(at<span[1]&&at+quote.length()>span[0]){String source=user.substring(span[0],span[1]);if(instruction(source)||QUESTION.matcher(source).matches()){allowed=false;break;}}
            if(allowed)return true;
        }
        return false;
    }
}
