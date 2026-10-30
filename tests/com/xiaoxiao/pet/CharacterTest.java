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
  db.commit(db.epoch(),"嗯","嗯，我在。","happy",null,true,false,null,null,"m2",t+30*60000,null,false,null,null,3,"换个说法继续高兴",false);
  check(db.snapshot().getLong("emotionAt")==t&&"m1".equals(db.snapshot().optString("moodSource")),"explicit carry preserves clock and original source even if cause is paraphrased");
  db.commit(db.epoch(),"然后呢","继续聊。","happy",null,true,false,null,null,"m3",t+60*60000,null,false,null,3,"又换了个说法");
  check(db.snapshot().getLong("emotionAt")==t&&db.mood(t+130*60000).getInt("level")==0,"legacy same-kind responses cannot repeatedly renew mood");
  AgentStore restarted=new AgentStore(new File(root,"mood"));check(restarted.mood(t+130*60000).getInt("level")==0,"mood still fades after restart");
  db.commit(db.epoch(),"你今天很厉害","谢谢，我有点得意。","happy",null,true,false,null,null,"m4",t+140*60000,null,false,null,null,2,"又被夸奖了",true);
  check(db.snapshot().getLong("emotionAt")==t+140*60000&&"m4".equals(db.snapshot().optString("moodSource")),"a new event can start another mood of the same kind");
  check(Boolean.FALSE.equals(AgentReply.parse("普通回复").moodChanged)&&Boolean.FALSE.equals(AgentReply.parse("{\"reply\":\"嗯\",\"mood_changed\":\"true\"}").moodChanged),"missing/plain or invalid change claims carry conservatively");
  check("calm".equals(gone.optString("emotion"))&&gone.optInt("level")==0,"and is gone after three steps");
  String line=AgentPolicy.moodLine(now,true);check(line.contains("开心")&&line.contains("很强烈")&&line.contains("被夸了有点得意")&&line.contains("10 分钟前"),"the mood line names feeling, strength, cause and time");
  check(!AgentPolicy.moodLine(now,false).contains("被夸"),"memory off: the cause stays out of the prompt");
  check(AgentPolicy.moodLine(gone,true).contains("平静"),"a faded mood reads as calm");
  JSONObject legacy=AgentStore.empty(0).put("emotion","playful").put("emotionAt",t);check(AgentPolicy.moodNow(legacy,t+30*60000).optInt("level")==1&&"calm".equals(AgentPolicy.moodNow(legacy,t+41*60000).optString("emotion")),"a mood saved before strengths existed counts as mild");
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.timeZone="Asia/Shanghai";in.now=t+10*60000;in.remember=true;in.feeling=now;
  String tail=last(PromptBuilder.build(in,"在吗",false,db.snapshot(),null));
  check(tail.contains("你此刻的心情：开心（很强烈）")&&!tail.contains("上次情绪"),"the carried-over mood reaches the next turn");
  String sys=PromptBuilder.build(in,"在吗",false,db.snapshot(),null).getJSONObject(0).getString("content");check(sys.contains("mood_level")&&sys.contains("mood_cause"),"the reply format asks for strength and cause");
  db.commit(db.epoch(),"我考过了","哼，我早说你可以。","happy",null,true,false,null,null,"m5",t+200*60000,null,false,null,null,2,"替TA考过高兴",true);
  db.commit(db.epoch(),"","晚上要不要庆祝一下？","happy",null,true,true,null,null,"p1",t+202*60000,null,false,null,null,2,"",false);
  check("替TA考过高兴".equals(db.snapshot().optString("moodCause"))&&"m5".equals(db.snapshot().optString("moodSource")),"a proactive turn that carries the mood keeps its cause");
  db.commit(db.epoch(),"","还在吗","happy",null,false,true,null,null,"p2",t+203*60000,null,false,null,null,2,"",false);
  check(!db.snapshot().has("moodCause"),"memory off: the cause is not kept");
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
  check("venting".equals(items.getJSONObject(0).optString("scene"))&&"work".equals(items.getJSONObject(0).optString("topic")),"legacy understanding receives narrow inferred scope");
  check(Understanding.context(u.snapshot(),"工作上被领导骂了，想吐槽").length()==1,"work venting uses work listening preference");
  check(Understanding.context(u.snapshot(),"工作这次我需要建议，怎么办").length()==0,"asking for advice does not activate listening-only preference");
  check(Understanding.context(u.snapshot(),"和朋友吵架了很难过").length()==0&&Understanding.context(u.snapshot()).length()==0,"unrelated and unknown situations do not activate scoped preferences");
  check(Understanding.parse("{\"understanding\":[{\"text\":\"TA吐槽工作时似乎希望先被听着\",\"evidence\":\"吐完舒服多了\",\"scene\":\"invented\"}]}",turns,u.snapshot(),day.toString()).empty(),"invalid scene labels are rejected");
  check(items.length()==1&&"d1".equals(items.getJSONObject(0).getJSONArray("evidence").getJSONObject(0).getString("turn")),"each item keeps the turn it quotes");
  in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.timeZone="Asia/Shanghai";in.now=y+86400000L;in.remember=true;in.diary=true;in.understanding=Understanding.context(u.snapshot(),"今天工作被领导骂了，想吐槽");
  tail=last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null));
  check(tail.contains("相处理解")&&tail.contains("先被听着")&&tail.contains("不要把它当成事实"),"understanding reaches the prompt as tact, not fact");
  in.diary=false;check(!last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null)).contains("先被听着"),"diary off: not used");
  in.diary=true;in.remember=false;check(!last(PromptBuilder.build(in,"在吗",false,u.snapshot(),null)).contains("先被听着"),"memory off: not used");
  // A later day confirms it, then a third retires it.
  Understanding.Update again=Understanding.parse("{\"diary\":\"x\",\"understanding\":[{\"text\":\"TA 吐槽工作的时候似乎更想先被听着，不急着要建议\",\"evidence\":\"吐完舒服多了\"}]}",turns,u.snapshot(),"2026-10-29");
  check(again.added.isEmpty()&&again.confirmed.containsKey(id),"a restatement confirms instead of duplicating");
  JSONObject s=u.snapshot();Understanding.apply(s,again,y+2*86400000L);check(Understanding.entries(s).getJSONObject(0).optInt("confirmed")==2&&Understanding.context(s,"工作被领导骂了，只想吐槽").getJSONObject(0).optInt("seen_days")==2,"confirmation adds evidence and a day");
  Understanding.Update retire=Understanding.parse("{\"diary\":\"x\",\"outdated\":[{\"id\":\""+id+"\",\"evidence\":\"明天还要早起\"}],\"confirmed\":[{\"id\":\""+id+"\",\"evidence\":\"吐完舒服多了\"}]}",turns,s,"2026-10-30");
  check(retire.outdated.containsKey(id)&&!retire.confirmed.containsKey(id),"retiring wins over confirming the same item");
  Understanding.apply(s,retire,y+3*86400000L);check(Understanding.entries(s).length()==0,"a retired item is removed");
  check(Understanding.parse("{\"diary\":\"x\",\"outdated\":[{\"id\":\""+id+"\",\"evidence\":\"我编的话\"}]}",turns,u.snapshot(),"2026-10-30").empty(),"retiring needs the user's words too");
  // Twelve at most, the least supported going first.
  JSONObject full=AgentStore.empty(0);Understanding.Update many=new Understanding.Update();for(int i=0;i<14;i++)many.added.add(new JSONObject().put("text","第"+i+"条相处方式似乎是这样的").put("evidence",new JSONArray().put(new JSONObject().put("quote","q").put("turn","x"+i))));
  Understanding.apply(full,many,1);check(Understanding.entries(full).length()==Understanding.KEEP,"at most twelve items");
  // Deletion paths: one by one, forgetting the quoted turn, clearing chats.
  long beforeDelete=u.epoch();u.forgetUnderstanding(id);check(u.epoch()>beforeDelete&&!u.addDiary(beforeDelete,Diary.entry(day.minusDays(2),"迟到日记不可恢复已删除理解。",turns,y),up),"late diary cannot restore a deleted understanding");check(Understanding.entries(u.snapshot()).length()==0,"the user can delete an item");
  JSONObject drop=AgentStore.empty(0);Understanding.apply(drop,up,1);Understanding.dropSources(drop,Collections.singleton("d1"));check(Understanding.entries(drop).length()==0,"forgetting the quoted turn removes the item");
  JSONObject keep=u.snapshot();Understanding.apply(keep,up,1);check(Understanding.entries(keep).length()==1,"(fixture)");
  u.addDiary(u.epoch(),Diary.entry(day.minusDays(1),"前一天的日记。",turns,y),up);check(Understanding.entries(u.snapshot()).length()==1,"(fixture saved)");
  u.commit(u.epoch(),"晚安","晚安。","sleepy",null,true,false,null,null,"n1",y+86400000L,null);check(Understanding.entries(u.snapshot()).length()==1,"a chat turn keeps the understanding written beside it");
  long beforeClear=u.epoch();u.clearUnderstanding();check(u.epoch()>beforeClear&&!u.addDiary(beforeClear,Diary.entry(day.minusDays(2),"迟到日记不可恢复已清空理解。",turns,y),up),"late diary cannot restore cleared understanding");
  u.clear(false);check(Understanding.entries(u.snapshot()).length()==0,"clearing chats clears the understanding");

  // Phrases: the user's own catchphrases, learned in the same diary call, each one verbatim from that day.
  AgentStore ph=new AgentStore(new File(root,"phrases"));
  String[] talk={"离谱他妈给离谱开门，领导又改需求了","哈哈好的","我真的会谢，又要加班"};
  for(int i=0;i<talk.length;i++)ph.commit(ph.epoch(),talk[i],"嗯。","calm",null,true,false,null,null,"p"+i,y+i*60000L,null);
  List<JSONObject> pturns=ph.dayTurns(day,zone);
  String system=Diary.request(day,pturns,"小鲸",Understanding.offered(ph.snapshot())).getJSONObject(0).getString("content");
  check(system.contains("口头禅")&&system.contains("\"phrases\":[]")&&system.contains("逐字"),"the diary call also asks for the user's own phrases, verbatim");
  String said2="{\"diary\":\"x\",\"phrases\":[{\"phrase\":\"离谱他妈给离谱开门\",\"when\":\"觉得事情很离谱时\"},{\"phrase\":\"哈哈\",\"when\":\"笑的时候\"},{\"phrase\":\"我编的梗\",\"when\":\"随便\"},{\"phrase\":\"我真的会谢\",\"when\":\"无奈吐槽时\"},{\"phrase\":\"离谱他妈给离谱开门\",\"when\":\"重复\"}]}";
  List<JSONObject> learned=Phrases.parse(said2,pturns,day.toString());
  check(learned.size()==2&&learned.get(0).getString("phrase").equals("离谱他妈给离谱开门")&&learned.get(0).getJSONObject("evidence").getString("turn").equals("p0"),"only the user's verbatim, uncommon phrases are kept, once each: "+learned);
  check(Phrases.parse("{\"phrases\":[{\"phrase\":\"密码是123456\",\"when\":\"x\"}]}",java.util.Collections.singletonList(new JSONObject().put("user","密码是123456").put("id","q")),day.toString()).isEmpty()&&!Phrases.valid("好的")&&!Phrases.valid("？？"),"secrets, common words and punctuation are not phrases");
  check(ph.addDiary(ph.epoch(),Diary.entry(day,"x",pturns,y+3600000L),null,learned)&&Phrases.entries(ph.snapshot()).length()==2,"phrases are saved with the diary");
  ph.commit(ph.epoch(),"晚安","晚安。","sleepy",null,true,false,null,null,"p9",y+86400000L,null);check(Phrases.entries(ph.snapshot()).length()==2,"a chat turn keeps the phrases written beside it");
  JSONObject again2=ph.snapshot();Phrases.apply(again2,Phrases.parse(said2,pturns,"2026-10-29"),y+2*86400000L);Phrases.apply(again2,Phrases.parse(said2,pturns,"2026-10-29"),y+2*86400000L);
  check(Phrases.entries(again2).getJSONObject(0).optInt("seen")==2,"seen again on another day counts once per day");
  JSONArray chosen=Phrases.context(ph.snapshot(),"领导又改需求了，太离谱");
  check(chosen.length()==2&&chosen.getJSONObject(0).getString("phrase").equals("离谱他妈给离谱开门"),"the phrase that fits what the user just said comes first");
  in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.timeZone="Asia/Shanghai";in.now=y+86400000L;in.remember=true;in.diary=true;in.phrases=chosen;
  check(last(PromptBuilder.build(in,"太离谱了",false,ph.snapshot(),null)).contains("对方常用的说法")&&last(PromptBuilder.build(in,"太离谱了",false,ph.snapshot(),null)).contains("离谱他妈给离谱开门"),"the phrases reach the prompt as habits to borrow now and then");
  in.diary=false;check(!last(PromptBuilder.build(in,"太离谱了",false,ph.snapshot(),null)).contains("对方常用的说法"),"diary off: not used");
  String pid=Phrases.entries(ph.snapshot()).getJSONObject(0).getString("id");long beforePhrase=ph.epoch();ph.forgetPhrase(pid);
  check(ph.epoch()>beforePhrase&&Phrases.entries(ph.snapshot()).length()==1,"a phrase can be deleted on its own");
  JSONObject drop2=ph.snapshot();Phrases.dropSources(drop2,java.util.Collections.singleton("p2"));check(Phrases.entries(drop2).length()==0,"forgetting the quoted turn removes the phrase");
  ph.clearUnderstanding();check(Phrases.entries(ph.snapshot()).length()==0,"clearing her understanding clears the phrases learned with it");
  ph.addDiary(ph.epoch(),Diary.entry(day.minusDays(1),"x",pturns,y),null,learned);ph.clear(false);check(Phrases.entries(ph.snapshot()).length()==0,"clearing chats clears the phrases");
  JSONObject many2=AgentStore.empty(0);List<JSONObject> lots=new java.util.ArrayList<>();for(int i=0;i<25;i++)lots.add(new JSONObject().put("phrase","说法"+i+"号").put("when","").put("evidence",new JSONObject().put("quote","说法"+i+"号").put("turn","t"+i).put("date","2026-10-28")));
  Phrases.apply(many2,lots,1);check(Phrases.entries(many2).length()==Phrases.KEEP,"at most twenty phrases");
  for(PersonaManager.Persona style:styles){
   for(String event:new String[]{"greeting_morning","greeting_noon","greeting_afternoon","greeting_evening","greeting_night","feed"})
    check(!"missing".equals(PersonaManager.voice(style,event,"missing",new Random(1))),style.id+" supplies local event voice "+event);
   check(PersonaManager.voice(style,"feed","",new Random(1)).contains("{food}"),"feeding retains actual food placeholder");
   check(!PersonaManager.storyLine(style,"这个给你……是我写的信，不准笑。").isEmpty(),"chapter voice remains available");
   String quoted="里面是你说过的「{memory}」。";check(quoted.equals(PersonaManager.storyLine(style,quoted)),"unmapped factual quote remains exact");
   JSONArray diary=Diary.request(day,turns,"小鲸",Understanding.offered(u.snapshot()),style.character);
   check(diary.getJSONObject(0).getString("content").contains(style.character)&&diary.getJSONObject(0).getString("content").contains("逐字"),"diary shares voice without losing evidence constraints");
  }
  check("neutral".equals(PersonaManager.voice(null,"feed","neutral",new Random(1)))&&"text".equals(PersonaManager.storyLine(null,"text")),"custom/no-style retains neutral text");
  phraseEdges();System.out.println("PASS: "+checks+" character checks");
 }
 static void phraseEdges()throws Exception{
  String day="2026-10-30";JSONObject candidate=new JSONObject().put("phrases",new JSONArray().put(new JSONObject().put("phrase","橘子汽水").put("when","口头禅")));
  List<JSONObject> secret=java.util.Collections.singletonList(new JSONObject().put("id","secret-fixture").put("user","这只是测试：我的登录密码是橘子汽水").put("reply","知道了"));
  check(Phrases.parse(candidate.toString(),secret,day).isEmpty(),"a harmless-looking substring from a sensitive user sentence is not learned");
  List<JSONObject> assistantOnly=java.util.Collections.singletonList(new JSONObject().put("id","assistant-only").put("user","今天怎么样").put("reply","橘子汽水"));
  check(Phrases.parse(candidate.toString(),assistantOnly,day).isEmpty(),"assistant words are not attributed to the user");
  check(!Phrases.valid("哈哈哈哈")&&!Phrases.valid("嗯嗯嗯嗯"),"repeated common filler is not a unique catchphrase");
  check(Phrases.valid("绝绝子")&&Phrases.valid("啊这"),"distinctive short expressions remain learnable");
 }
 static String last(JSONArray messages)throws Exception{return messages.getJSONObject(messages.length()-1).getString("content");}
}
