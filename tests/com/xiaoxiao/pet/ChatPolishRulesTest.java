package com.xiaoxiao.pet;
import org.json.*;

public final class ChatPolishRulesTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        for(String s:new String[]{"以后叫我kk","我叫kk，以后就叫我kk吧。","我叫k你以后就叫我kk吧","你以后就叫我kk吧","请叫我kk","记住：我叫kk","我叫kk"})check("kk".equals(Nickname.requested(s)),"explicit naming: "+s);
        check("小K".equals(Nickname.requested("我叫王某，今后请称呼我小K就好")),"preferred address wins over identity");
        check("Mary Jane".equals(Nickname.requested("以后叫我Mary Jane")),"spaces and case preserved");
        for(String s:new String[]{"我叫什么？","你以后叫我kk吗","他说以后叫我kk","如果改名，我叫kk","以前我叫kk","昨天我说我叫kk","不要叫我kk","以后叫我起床","叫我去吃饭","我叫你kk","我叫kk不用记住","她的名字叫kk","我的猫叫kk","比如我叫kk","以后叫我\"kk\"","今天我们聊过什么"})check(Nickname.requested(s)==null,"not a naming request: "+s);
        check("我查了记录".equals(AgentReply.parse("[tool] 我查了记录").reply),"plain prefix removed");
        check("记住了".equals(AgentReply.parse("{\"reply\":\"[assistant] [tool] 记住了\"}").reply),"JSON field prefix removed");
        check("正文里的 [tool] 是引用".equals(AgentReply.parse("正文里的 [tool] 是引用").reply),"interior marker retained");
        check("`[tool]` 是标签".equals(AgentReply.parse("`[tool]` 是标签").reply),"quoted marker retained");
        check(AgentPolicy.streamPreview("[to").isEmpty(),"partial channel marker not exposed");
        check("你好".equals(AgentPolicy.streamPreview("{\"reply\":\"[tool] 你好")),"streamed field cleaned");
        JSONObject legacy=new JSONObject().put("reply","[tool] 老记录的原文").put("displayed",1);
        check("老记录的原文".equals(ReplySegments.visible(legacy))&&legacy.getString("reply").startsWith("[tool]"),"legacy display cleaned without rewriting history");
        try{AgentReply.parse("[tool]");throw new AssertionError("empty role prefix accepted");}catch(java.io.IOException expected){checks++;}
        System.out.println("PASS: "+checks+" nickname and reply presentation checks");
    }
}
