package com.xiaoxiao.pet;
import java.util.*;
import org.json.*;

/** Lossless local segmentation. Long/structured answers open in history instead of flashing past. */
final class ReplySegments {
    static final int VERSION=4;
    static boolean previewOnly(String text){return text.length()>240||text.contains("```")||text.matches("(?s).*https?://.*")||text.matches("(?s).*\\n\\s*(?:[-*]|[0-9]+[.)、])\\s+.*");}
    static List<String> split(String text){return splitNatural(text,true,true);}
    static List<String> splitV2(String text){return splitNatural(text,false);}
    static List<String> splitNatural(String text,boolean shorter){return splitNatural(text,shorter,false);}
    static List<String> splitNatural(String text,boolean shorter,boolean sentenceBubbles){
        if((!sentenceBubbles&&text.codePointCount(0,text.length())<=12)||previewOnly(text))return Collections.singletonList(text);
        List<String> sentences=new ArrayList<>();int start=0;StringBuilder closing=new StringBuilder();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);int open="“‘「『（([".indexOf(c);
            if(open>=0)closing.append("”’」』）)]".charAt(open));
            else if(closing.length()>0&&c==closing.charAt(closing.length()-1))closing.setLength(closing.length()-1);
            boolean pause=shorter&&i+1<text.length()&&((c=='…'&&text.charAt(i+1)=='…')||(c=='—'&&text.charAt(i+1)=='—'))&&text.codePointCount(start,i)>=6;
            if(closing.length()==0&&("。！？!?；;\n".indexOf(c)>=0||pause)){
                int end=i+1;while(end<text.length()&&("。！？!?；;…\r\n ".indexOf(text.charAt(end))>=0||shorter&&text.charAt(end)=='—'))end++;
                if(text.substring(start,end).trim().length()>=(sentenceBubbles?1:4)){sentences.add(text.substring(start,end));start=end;}
                i=end-1;
            }
        }
        if(start<text.length()){
            String tail=text.substring(start);
            if(!sentences.isEmpty()&&(sentenceBubbles?tail.trim().isEmpty():tail.trim().length()<4)){int last=sentences.size()-1;sentences.set(last,sentences.get(last)+tail);}
            else sentences.add(tail);
        }
        List<String> parts=new ArrayList<>();
        for(String sentence:sentences){
            start=0;closing.setLength(0);
            for(int i=0;i<sentence.length();i++){
                char c=sentence.charAt(i);int open="“‘「『（([".indexOf(c);
                if(open>=0)closing.append("”’」』）)]".charAt(open));
                else if(closing.length()>0&&c==closing.charAt(closing.length()-1))closing.setLength(closing.length()-1);
                // A comma is a fallback for a long clause, never a cut inside a quotation or number.
                if(closing.length()==0&&"，,".indexOf(c)>=0&&!(c==','&&i>0&&i+1<sentence.length()&&Character.isDigit(sentence.charAt(i-1))&&Character.isDigit(sentence.charAt(i+1)))
                    &&sentence.codePointCount(start,sentence.length())>(shorter?22:28)&&sentence.codePointCount(start,i+1)>=(shorter?8:12)&&sentence.codePointCount(i+1,sentence.length())>=6){
                    parts.add(sentence.substring(start,i+1));start=i+1;
                }
            }
            parts.add(sentence.substring(start));
        }
        return parts.isEmpty()?Collections.singletonList(text):parts;
    }
    // Persisted display counts refer to the segmentation used when the reply was first saved.
    // Never reinterpret an old count using new boundaries: that can hide or disclose old text.
    static List<String> parts(JSONObject turn){String text=turn.optString("reply");int version=turn.optInt("segmentVersion",1);return version>=4?split(text):version==3?splitNatural(text,true):version==2?splitV2(text):legacySplit(text);}
    static List<String> legacySplit(String text){
        if(text.length()<45||previewOnly(text))return Collections.singletonList(text);
        List<Integer> ends=new ArrayList<>();
        for(int i=0;i<text.length();i++)if("。！？!?；;\n".indexOf(text.charAt(i))>=0){int end=i+1;while(end<text.length()&&"。！？!?…’”\"）)] \n".indexOf(text.charAt(end))>=0)end++;ends.add(end);i=end-1;}
        List<String> parts=new ArrayList<>();int start=0,target=Math.max(20,(text.length()+3)/4);
        for(int end:ends)if(parts.size()<2&&end-start>=target&&text.length()-end>=12){parts.add(text.substring(start,end));start=end;}
        parts.add(text.substring(start));return parts;
    }
    static long holdMillis(String part){return Math.max(3500,Math.min(10000,1500+part.codePointCount(0,part.length())*100L));}
    static int count(JSONObject turn){return parts(turn).size();}
    static String visible(JSONObject turn){
        String reply=turn.optString("reply");if(!turn.has("displayed"))return AgentPolicy.replyText(reply);
        List<String> parts=parts(turn);int n=Math.min(parts.size(),turn.optInt("displayed"));StringBuilder out=new StringBuilder();
        for(int i=0;i<n;i++)out.append(parts.get(i));return AgentPolicy.replyText(out.toString());
    }
    static String label(JSONObject turn){if(!turn.has("displayed")||turn.optInt("displayed")>=count(turn))return "";return turn.optInt("displayed")==0?"未展示 · 点开全文":"部分已展示 · 剩余未展示";}
}
