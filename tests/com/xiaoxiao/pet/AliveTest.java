package com.xiaoxiao.pet;
import org.json.*;import java.io.File;

/**
 * The 活人感 additions (docs/CHARACTER.md): her murmur beside the reply (小声嘀咕) is cleaned, saved with the turn,
 * asked for only when switched on, and carried in her history only for fully shown turns.
 * Usage: AliveTest <state directory>
 */
public final class AliveTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}

 public static void main(String[] args)throws Exception{
  aside(args[0]);
  System.out.println("PASS: "+checks+" alive checks");
 }

 static void aside(String dir)throws Exception{
  // Cleaning: one short line of hers, never a copy of the reply, a label, a secret or a wrapped markdown italic.
  check(AgentPolicy.aside("又在赶 ddl 啊…","好，我们先把第三节理顺。").equals("又在赶 ddl 啊…"),"a short murmur is kept");
  check(AgentPolicy.aside("*这个点了还在忙啊…*","先睡吧。").equals("这个点了还在忙啊…"),"markdown italics are removed");
  check(AgentPolicy.aside("（还好啦，都是我该干的）","嗯。").equals("还好啦，都是我该干的"),"wrapping brackets are removed");
  check(AgentPolicy.aside("","好。").isEmpty()&&AgentPolicy.aside(null,"好。").isEmpty()&&AgentPolicy.aside(3,"好。").isEmpty(),"no murmur, no line");
  check(AgentPolicy.aside("我其实一直在想这件事到底该怎么办才好呢，真的很想帮上忙，可是又怕说多了打扰你","好。").isEmpty(),"too long is dropped");
  check(AgentPolicy.aside("先睡吧","嗯，先睡吧。明天再说。").isEmpty(),"repeating the reply is dropped");
  check(AgentPolicy.aside("[tool] 记一下","好。").isEmpty(),"a channel label is dropped");
  check(AgentPolicy.aside("……","好。").isEmpty(),"punctuation alone is dropped");

  // Parsing: the field travels with the reply; a broken envelope keeps the reply and drops the murmur.
  AgentReply r=AgentReply.parse("{\"reply\":\"好，我们先把第三节理顺。\",\"aside\":\"又在赶 ddl 啊…\",\"emotion\":\"calm\"}");
  check(r.reply.equals("好，我们先把第三节理顺。")&&r.aside.equals("又在赶 ddl 啊…"),"aside is parsed");
  check(AgentReply.parse("{\"reply\":\"好。\",\"aside\":\"好。\"}").aside.isEmpty(),"aside equal to the reply is dropped");
  check(AgentReply.parse("{\"reply\":\"好。\"}").aside.isEmpty()&&AgentReply.parse("就这样吧").aside.isEmpty(),"no aside field, no murmur");

  // Saving: the murmur is stored with its turn and can be looked up for the pet bubble.
  AgentStore db=new AgentStore(new File(dir,"aside"));long t=1_800_000_000_000L;
  check(db.commit(db.epoch(),"帮我看看报告","好，我们先把第三节理顺。","calm",null,true,false,null,null,"a1",t,null,false,null,null,0,"",Boolean.FALSE,null,"又在赶 ddl 啊…"),"commit with aside");
  check(db.commit(db.epoch(),"好的","嗯。","calm",null,true,false,null,null,"a2",t+60_000L,null,false,null,null,0,"",Boolean.FALSE),"commit without aside");
  check(db.aside("a1").equals("又在赶 ddl 啊…")&&db.aside("a2").isEmpty()&&db.aside("missing").isEmpty()&&db.aside(null).isEmpty(),"aside lookup by turn");
  JSONArray turns=db.snapshot().getJSONArray("turns");check(turns.getJSONObject(0).optString("aside").equals("又在赶 ddl 啊…")&&!turns.getJSONObject(1).has("aside"),"only a real murmur is saved");

  // Prompt: asked for as a habit of hers only when switched on; past murmurs ride along with their replies.
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.now=t+120_000L;in.aside=true;
  JSONArray on=PromptBuilder.build(in,"嗯",false,db.snapshot(),null);String system=on.getJSONObject(0).getString("content");
  check(system.contains("你有小声嘀咕的习惯")&&!system.contains("可以偶尔"),"the murmur is a habit, not a permission");
  check(new JSONObject(on.getJSONObject(2).getString("content")).optString("aside").equals("又在赶 ddl 啊…")&&!new JSONObject(on.getJSONObject(4).getString("content")).has("aside"),"her past murmur is in her history turn");
  check(on.getJSONObject(on.length()-1).getString("content").contains("包含reply、aside、"),"the turn protocol names aside");
  in.aside=false;JSONArray off=PromptBuilder.build(in,"嗯",false,db.snapshot(),null);
  check(!off.toString().contains("小声嘀咕")&&!off.toString().contains("aside"),"switched off: no murmur anywhere in the request");
 }
}
