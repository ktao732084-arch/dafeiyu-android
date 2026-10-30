package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.util.concurrent.atomic.AtomicInteger;

public final class RewriteGuardTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static String verdict(boolean ok,String replacement)throws Exception{return new JSONObject().put("faithful",ok).put("replacement",replacement).toString();}
 public static void main(String[] args)throws Exception {
  RewriteGuard.Reviewer original=RewriteGuard.reviewer;
  try{
   String text="我明天搬去成都，我不吃辣。";
   RewriteGuard.Source source=RewriteGuard.source("帮我润色给朋友的消息：‘"+text+"’只输出正文，不要点评。");
   check(source!=null&&source.text.equals(text),"quoted source extracted without instructions");
   check(RewriteGuard.source("再帮我改一句给朋友的话：我十分钟后到。只输出这句话，别加其他的。").text.equals("我十分钟后到。"),"colon source excludes format request");
   check(RewriteGuard.source("请润色：我不吃辣。")!=null,"direct request supported");
   check(RewriteGuard.source("帮我把“我不吃辣。”改成更客气的语气").text.equals("我不吃辣。"),"style change keeps original");
   for(String q:new String[]{"我不吃辣","刚才你润色错了，说的是‘我不吃辣’","帮我把‘明天见’改成‘后天见’","帮我润色‘明天见’，把明天改成后天","请帮我翻译：我不吃辣","帮我写一句工作群消息"})check(RewriteGuard.source(q)==null,"ordinary/explicit content change/ambiguous request not frozen: "+q);
   for(String bad:new String[]{"我明天搬去成都，我不太吃辣。","我明天搬去成都，我不太能吃辣。","我后天搬去成都，我不吃辣。"}){
    RewriteGuard.reviewer=(m,t)->verdict(true,"");check(RewriteGuard.enforce(source,bad,new AgentTransport.Token()).equals(text),"local locks override a false positive review");
   }
   RewriteGuard.Source budget=RewriteGuard.source("帮我润色：‘我只能周六下午到，预算最多两百，不接受周日改期。’");
   check(!budget.keeps("我周六下午能到，预算大概两百，可以改到周日。"),"exclusive time, upper bound and refusal protected");
   check(!budget.keeps("我只能周六下午到，预算最多三百，不接受周日改期。"),"amount protected");
   check(budget.keeps(budget.text),"unchanged constraints pass");
   AtomicInteger calls=new AtomicInteger();String repaired="我明天搬去成都，先说明一下，我不吃辣。";
   RewriteGuard.reviewer=(m,t)->{int n=calls.incrementAndGet();return verdict(n==2,n==1?repaired:"");};
   check(RewriteGuard.enforce(source,"我明天搬成都，不太吃辣",new AgentTransport.Token()).equals(repaired)&&calls.get()==2,"repair receives independent second review");
   calls.set(0);RewriteGuard.reviewer=(m,t)->{calls.incrementAndGet();return verdict(false,repaired);};
   check(RewriteGuard.enforce(source,"changed",new AgentTransport.Token()).equals(text)&&calls.get()==2,"failed second review falls back, no loop");
   calls.set(0);RewriteGuard.reviewer=(m,t)->{calls.incrementAndGet();return verdict(false,"我明天搬去成都，不太能吃辣。");};
   check(RewriteGuard.enforce(source,"changed",new AgentTransport.Token()).equals(text)&&calls.get()==1,"unsafe repair never reaches second review");
   for(String invalid:new String[]{"not JSON","{\"faithful\":\"true\",\"replacement\":\"\"}","{\"faithful\":true}"}){RewriteGuard.reviewer=(m,t)->invalid;check(RewriteGuard.enforce(source,"changed",new AgentTransport.Token()).equals(text),"invalid verdict keeps source");}
   RewriteGuard.reviewer=(m,t)->{throw new IOException("offline");};check(RewriteGuard.enforce(source,"changed",new AgentTransport.Token()).equals(text),"network failure keeps source");
   AgentTransport.Token cancelled=new AgentTransport.Token();RewriteGuard.reviewer=(m,t)->{t.cancel();throw new IOException("cancelled");};
   boolean threw=false;try{RewriteGuard.enforce(source,"changed",cancelled);}catch(IOException expected){threw=true;}check(threw,"cancellation propagates instead of fallback commit");
   RewriteGuard.reviewer=(m,t)->{throw new AssertionError("unnecessary model call");};
   check(RewriteGuard.enforce(source,text,new AgentTransport.Token()).equals(text),"exact source needs no review");
   check(RewriteGuard.enforce(null,"哼，不服",new AgentTransport.Token()).equals("哼，不服"),"ordinary personality chat bypasses guard");
   String data=RewriteGuard.review(source,"candidate",true).getJSONObject(1).getString("content");check(new JSONObject(data).getString("source").equals(text),"source supplied verbatim as data");
   System.out.println("PASS: "+checks+" rewrite fidelity checks");
  }finally{RewriteGuard.reviewer=original;}
 }
}
