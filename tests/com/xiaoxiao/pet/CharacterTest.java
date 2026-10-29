package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.util.*;

/** Her character: the bundled styles, signature-aware drift notes, mood carried between turns, and her understanding of the user. */
public final class CharacterTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  // Usage: CharacterTest <assets/personas.json> <scratch dir>
  List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)));
  File root=new File(args[1]);root.mkdirs();
  check(styles.size()==5,"five styles");
  Set<String> situations=null;
  for(PersonaManager.Persona p:styles){
   check(p.character.length()>=200&&p.character.contains("说话方式")&&p.character.contains("收敛"),p.id+" has a character sheet that also says when to hold back");
   check(p.signature.size()>=3,p.id+" has signature expressions");
   check(p.examples.size()>=15,p.id+" has at least fifteen examples");
   Set<String> asked=new HashSet<>();for(String[] e:p.examples)asked.add(e[0]);
   if(situations==null)situations=asked;else check(situations.equals(asked),p.id+" answers the same situations as the others, so they can be compared");
   String g=PersonaManager.guidance(p);
   check(g.contains("性格细节（"+p.name+"）")&&g.contains(p.character)&&g.contains("不是要你变得没有个性")&&g.contains(p.examples.get(0)[1]),p.id+" guidance carries the sheet, the note and the examples");
  }
  // The styles really differ: no two give the same answer to the same situation.
  for(int i=0;i<styles.size();i++)for(int j=i+1;j<styles.size();j++)for(int k=0;k<styles.get(i).examples.size();k++)
   check(!styles.get(i).examples.get(k)[1].equals(styles.get(j).examples.get(k)[1]),styles.get(i).id+" and "+styles.get(j).id+" answer differently: "+styles.get(i).examples.get(k)[0]);

  // Drift notes: her signature may recur; only all-four repetition draws a note, other tics still do at three.
  List<String> classic=Arrays.asList("哼，才不是胖，这叫圆润。","才不是想你了。……好吧，有一点。","今天下雨了。","哼，才不是担心你。");
  List<String> sig=Arrays.asList("哼","才不是","……","行吧","算你");
  check(!AgentPolicy.styleNotes(classic,sig).toString().contains("才不是")&&!AgentPolicy.styleNotes(classic,sig).toString().contains("「哼，」"),"signature in three of four replies is her voice, not drift: "+AgentPolicy.styleNotes(classic,sig));
  check(AgentPolicy.styleNotes(classic).toString().contains("才不是"),"without a signature the same replies are flagged");
  List<String> every=Arrays.asList("才不是呢，我只是路过。","才不是担心你。","才不是想你了。","才不是啦。");
  check(AgentPolicy.styleNotes(every,sig).toString().contains("才不是"),"leaning on the signature in every reply is still drift");
  check(AgentPolicy.styleNotes(Arrays.asList("报告：今天没下雨呀。","报告：我吃了三块饼干。","报告：五子棋又输了。","晚安。"),Arrays.asList("报告")).isEmpty()&&!AgentPolicy.styleNotes(Arrays.asList("嗯嗯，今天没下雨呀。","嗯嗯，我吃了三块饼干。","嗯嗯，五子棋又输了。","晚安。"),Arrays.asList("报告")).isEmpty(),"a signature opener may lead three replies, another opener may not");
  check(!AgentPolicy.compressed(4,10)&&AgentPolicy.compressed(3,10)&&!AgentPolicy.compressed(0,3)&&AgentPolicy.compressed(1,5)&&!AgentPolicy.compressed(2,5),"only the window's first four turns are compressed, and never the last three");
  check("谁偷吃了，这叫品鉴。……你要的话，分你一半。".equals(AgentPolicy.compressReply("谁偷吃了，这叫品鉴。……你要的话，分你一半。下次给我留。")),"the second sentence, where her character often shows, survives compression");

  // Sampling: conversational temperature only for DeepSeek chat, never for memory calls or other providers.
  JSONObject payload=new JSONObject();ModelPresets.chat("api.deepseek.com","deepseek-flash",payload);check(payload.optDouble("temperature")==1.3,"DeepSeek chat uses its conversational temperature");
  payload=new JSONObject();ModelPresets.chat("api.deepseek.com","deepseek-reasoner",payload);ModelPresets.chat("api.openai.com","gpt-5",payload);check(!payload.has("temperature"),"reasoning models and other providers keep their defaults");

  // Mood: strength and cause from the reply, fading a step every 40 minutes.
  AgentReply r=AgentReply.parse("{\"reply\":\"哼，才不是高兴。\",\"emotion\":\"happy\",\"mood_level\":3,\"mood_cause\":\"被夸了有点得意\"}");
  check(r.moodLevel==3&&"被夸了有点得意".equals(r.moodCause),"mood strength and cause are read");
  check(AgentReply.parse("{\"reply\":\"好\",\"mood_level\":9,\"mood_cause\":\"他说密码是abc123456\"}").moodLevel==0&&AgentReply.parse("{\"reply\":\"好\",\"mood_cause\":\"他说密码是abc123456\"}").moodCause.isEmpty()&&AgentReply.parse("{\"reply\":\"好\",\"mood_level\":\"2\"}").moodLevel==2,"out-of-range strengths and secret causes are dropped");
  AgentStore db=new AgentStore(new File(root,"mood"));long t=1_800_000_000_000L;
  db.commit(db.epoch(),"你下棋进步了","哼，那当然。","happy",null,true,false,null,null,"m1",t,null,false,null,3,"被夸了有点得意");
  JSONObject now=db.mood(t+10*60000),later=db.mood(t+50*60000),gone=db.mood(t+130*60000);
  check("happy".equals(now.optString("emotion"))&&now.optInt("level")==3&&"被夸了有点得意".equals(now.optString("cause"))&&now.optLong("minutes")==10,"a fresh mood keeps its strength and cause");
  check(later.optInt("level")==2&&"happy".equals(later.optString("emotion")),"it fades a step after 40 minutes");
  check("calm".equals(gone.optString("emotion"))&&gone.optInt("level")==0,"and is gone after three steps");
  String line=AgentPolicy.moodLine(now,true);check(line.contains("开心")&&line.contains("很强烈")&&line.contains("被夸了有点得意")&&line.contains("10 分钟前"),"the mood line names feeling, strength, cause and time");
  check(!AgentPolicy.moodLine(now,false).contains("被夸"),"memory off: the cause stays out of the prompt");
  check(AgentPolicy.moodLine(gone,true).contains("平静"),"a faded mood reads as calm");
  JSONObject legacy=AgentStore.empty(0).put("emotion","playful").put("emotionAt",t);check(AgentPolicy.moodNow(legacy,t+30*60000).optInt("level")==1&&"calm".equals(AgentPolicy.moodNow(legacy,t+41*60000).optString("emotion")),"a mood saved before strengths existed counts as mild");
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.timeZone="Asia/Shanghai";in.now=t+10*60000;in.remember=true;in.feeling=now;
  String tail=last(PromptBuilder.build(in,"在吗",false,db.snapshot(),null));
  check(tail.contains("你此刻的心情：开心（很强烈）")&&!tail.contains("上次情绪"),"the carried-over mood reaches the next turn");
  String sys=PromptBuilder.build(in,"在吗",false,db.snapshot(),null).getJSONObject(0).getString("content");check(sys.contains("mood_level")&&sys.contains("mood_cause"),"the reply format asks for strength and cause");
  db.clear(false);check(!db.snapshot().has("moodCause"),"clearing chats clears the mood's cause");

  // Understanding: formed in the diary call, every step backed by the user's own words that day.
  ZoneId zone=ZoneId.systemDefault();LocalDate day=LocalDate.of(2026,10,28);long y=day.atTime(20,0).atZone(zone).toInstant().toEpochMilli();
  AgentStore u=new AgentStore(new File(root,"understanding"));
  String[] said={"今天又被领导骂了","你别给我讲道理，我就想吐槽一下","吐完舒服多了","明天还要早起"};
  for(int i=0;i<said.length;i++)u.commit(u.epoch(),said[i],"嗯，我在听。","concerned",null,true,false,null,null,"d"+i,y+i*60000L,null);
  List<JSONObject> turns=u.dayTurns(day,zone);
  JSONArray req=Diary.request(day,turns,"小鲸",Understanding.offered(u.snapshot()));
  check(req.getJSONObject(0).getString("content").contains("相处分寸")&&req.getJSONObject(0).getString("content").contains("逐字")&&req.getJSONObject(1).getString("content").contains("your_understanding"),"the diary call also asks for evidence-backed tact");
  check(!Diary.request(day,turns,"小鲸").getJSONObject(0).getString("content").contains("相处分寸"),"the plain diary request is unchanged");
  String answer="{\"diary\":\"陪他吐槽了领导。\",\"understanding\":[{\"text\":\"TA 吐槽工作时似乎更想先被听着，不急着要建议\",\"evidence\":\"你别给我讲道理，我就想吐槽一下\"},"
   +"{\"text\":\"TA 似乎很喜欢猫\",\"evidence\":\"我家猫好可爱\"},{\"text\":\"TA 有点玻璃心，说不得\",\"evidence\":\"今天又被领导骂了\"}],\"confirmed\":[{\"id\":\"nope\",\"evidence\":\"吐完舒服多了\"}],\"outdated\":[]}";
  Understanding.Update up=Understanding.parse(answer,turns,u.snapshot(),day.toString());
  check(up.added.size()==1&&up.added.get(0).getString("text").contains("先被听着")&&up.confirmed.isEmpty(),"only items quoting the day's words, without labels, about known ids, are kept");
  long epoch=u.epoch();check(u.addDiary(epoch,Diary.entry(day,Diary.parse(answer),turns,y+3600000L),up),"diary and understanding are saved together");
  JSONArray items=Understanding.entries(u.snapshot());String id=items.getJSONObject(0).getString("id");
  check(items.length()==1&&"d1".equals(items.getJSONObject(0).getJSONArray("evidence").getJSONObject(0).getString("turn")),"each item keeps the turn it quotes");
  in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.timeZone="Asia/Shanghai";in.now=y+86400000L;in.remember=true;in.diary=true;in.understanding=Understanding.context(u.snapshot());
  tail=last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null));
  check(tail.contains("相处理解")&&tail.contains("先被听着")&&tail.contains("不要把它当成事实"),"understanding reaches the prompt as tact, not fact");
  in.diary=false;check(!last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null)).contains("先被听着"),"diary off: not used");
  in.diary=true;in.remember=false;check(!last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null)).contains("先被听着"),"memory off: not used");
  // A later day confirms it, then a third retires it.
  Understanding.Update again=Understanding.parse("{\"diary\":\"x\",\"understanding\":[{\"text\":\"TA 吐槽工作的时候似乎更想先被听着，不急着要建议\",\"evidence\":\"吐完舒服多了\"}]}",turns,u.snapshot(),"2026-10-29");
  check(again.added.isEmpty()&&again.confirmed.containsKey(id),"a restatement confirms instead of duplicating");
  JSONObject s=u.snapshot();Understanding.apply(s,again,y+2*86400000L);check(Understanding.entries(s).getJSONObject(0).optInt("confirmed")==2&&Understanding.context(s).getJSONObject(0).optInt("seen_days")==2,"confirmation adds evidence and a day");
  Understanding.Update retire=Understanding.parse("{\"diary\":\"x\",\"outdated\":[{\"id\":\""+id+"\",\"evidence\":\"明天还要早起\"}],\"confirmed\":[{\"id\":\""+id+"\",\"evidence\":\"吐完舒服多了\"}]}",turns,s,"2026-10-30");
  check(retire.outdated.containsKey(id)&&!retire.confirmed.containsKey(id),"retiring wins over confirming the same item");
  Understanding.apply(s,retire,y+3*86400000L);check(Understanding.entries(s).length()==0,"a retired item is removed");
  check(Understanding.parse("{\"diary\":\"x\",\"outdated\":[{\"id\":\""+id+"\",\"evidence\":\"我编的话\"}]}",turns,u.snapshot(),"2026-10-30").empty(),"retiring needs the user's words too");
  // Twelve at most, the least supported going first.
  JSONObject full=AgentStore.empty(0);Understanding.Update many=new Understanding.Update();for(int i=0;i<14;i++)many.added.add(new JSONObject().put("text","第"+i+"条相处方式似乎是这样的").put("evidence",new JSONArray().put(new JSONObject().put("quote","q").put("turn","x"+i))));
  Understanding.apply(full,many,1);check(Understanding.entries(full).length()==Understanding.KEEP,"at most twelve items");
  // Deletion paths: one by one, forgetting the quoted turn, clearing chats.
  u.forgetUnderstanding(id);check(Understanding.entries(u.snapshot()).length()==0,"the user can delete an item");
  JSONObject drop=AgentStore.empty(0);Understanding.apply(drop,up,1);Understanding.dropSources(drop,Collections.singleton("d1"));check(Understanding.entries(drop).length()==0,"forgetting the quoted turn removes the item");
  JSONObject keep=u.snapshot();Understanding.apply(keep,up,1);check(Understanding.entries(keep).length()==1,"(fixture)");
  u.addDiary(u.epoch(),Diary.entry(day.minusDays(1),"前一天的日记。",turns,y),up);check(Understanding.entries(u.snapshot()).length()==1,"(fixture saved)");
  u.commit(u.epoch(),"晚安","晚安。","sleepy",null,true,false,null,null,"n1",y+86400000L,null);check(Understanding.entries(u.snapshot()).length()==1,"a chat turn keeps the understanding written beside it");
  u.clear(false);check(Understanding.entries(u.snapshot()).length()==0,"clearing chats clears the understanding");
  System.out.println("PASS: "+checks+" character checks");
 }
 static String last(JSONArray messages)throws Exception{return messages.getJSONObject(messages.length()-1).getString("content");}
}
