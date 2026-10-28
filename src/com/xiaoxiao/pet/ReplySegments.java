package com.xiaoxiao.pet;
import java.util.*;
import org.json.*;

/** Lossless local segmentation. Long/structured answers open in history instead of flashing past. */
final class ReplySegments {
    static boolean previewOnly(String text){return text.length()>240||text.contains("```")||text.matches("(?s).*https?://.*")||text.matches("(?s).*\\n\\s*(?:[-*]|[0-9]+[.)、])\\s+.*");}
    static List<String> split(String text){
        if(text.length()<45||previewOnly(text))return Collections.singletonList(text);
        List<Integer> ends=new ArrayList<>();
        for(int i=0;i<text.length();i++)if("。！？!?；;\n".indexOf(text.charAt(i))>=0){int end=i+1;while(end<text.length()&&"。！？!?…’”\"）)] \n".indexOf(text.charAt(end))>=0)end++;ends.add(end);i=end-1;}
        List<String> parts=new ArrayList<>();int start=0,target=Math.max(20,(text.length()+3)/4);
        for(int end:ends)if(parts.size()<2&&end-start>=target&&text.length()-end>=12){parts.add(text.substring(start,end));start=end;}
        parts.add(text.substring(start));return parts;
    }
    static long holdMillis(String part){return Math.max(3500,Math.min(10000,1500+part.codePointCount(0,part.length())*100L));}
    static int count(JSONObject turn){return split(turn.optString("reply")).size();}
    static String visible(JSONObject turn){
        String reply=turn.optString("reply");if(!turn.has("displayed"))return reply;
        List<String> parts=split(reply);int n=Math.min(parts.size(),turn.optInt("displayed"));StringBuilder out=new StringBuilder();
        for(int i=0;i<n;i++)out.append(parts.get(i));return out.toString();
    }
    static String label(JSONObject turn){if(!turn.has("displayed")||turn.optInt("displayed")>=count(turn))return "";return turn.optInt("displayed")==0?"未展示 · 点开全文":"部分已展示 · 剩余未展示";}
}
