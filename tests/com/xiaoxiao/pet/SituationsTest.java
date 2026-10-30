package com.xiaoxiao.pet;
import org.json.*;import java.nio.file.*;import java.time.*;import java.util.*;

/**
 * Situation modules and her bible (docs/CHARACTER.md): the resource is complete for every style, each of the
 * fifteen comparable examples belongs to one situation, local detection picks the right situation from the user's
 * words, the time and the gap since the last chat, holding back drops the playful ones, and only what applies
 * reaches the background while the system message keeps a short core.
 * Usage: SituationsTest <assets/personas.json>
 */
public final class SituationsTest {
 static int checks;
 static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 static long at(int hour,int minute){return LocalDateTime.of(2026,10,30,hour,minute).atZone(ZONE).toInstant().toEpochMilli();}
 static List<String> ids(String text,long now,long lastUser){List<String> out=new ArrayList<>();for(Situations.Situation s:Situations.detect(text,now,ZONE,lastUser))out.add(s.id);return out;}

 public static void main(String[] args)throws Exception{
  JSONObject resource=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
  List<PersonaManager.Persona> styles=PersonaManager.load(resource);
  resource(styles,resource);
  detection(styles);
  blocks(styles);
  prompts(styles);
  System.out.println("PASS: "+checks+" situation module checks");
 }

 static void resource(List<PersonaManager.Persona> styles,JSONObject resource)throws Exception{
  List<Situations.Situation> all=Situations.all();check(all.size()==15,"fifteen situations");
  int[] owner=new int[15];Arrays.fill(owner,-1);
  for(int k=0;k<all.size();k++)for(int i:all.get(k).examples){check(i>=0&&i<15,"example index in range");check(owner[i]<0,"example "+i+" belongs to one situation");owner[i]=k;}
  for(int i=0;i<15;i++)check(owner[i]>=0,"example "+i+" belongs to a situation");
  int[] core=Situations.core();check(core.length==3,"three core examples stay in the system message");
  for(Situations.Situation s:all){Set<String> how=new HashSet<>(),more=new HashSet<>();
   for(PersonaManager.Persona p:styles){JSONObject m=p.situations.optJSONObject(s.id);check(m!=null,p.id+" handles "+s.id);
    check(m.optString("how").length()>=6,p.id+" has its own way for "+s.id);JSONArray extra=m.getJSONArray("more");check(extra.length()>=1,p.id+" has an extra example for "+s.id);
    for(int i=0;i<extra.length();i++){JSONArray pair=extra.getJSONArray(i);check(pair.length()==2&&!pair.getString(0).isEmpty()&&!pair.getString(1).isEmpty(),"example pairs are user and her");more.add(pair.getString(1));}
    how.add(m.getString("how"));}
   check(how.size()==styles.size(),"five different ways for "+s.id);check(more.size()==styles.size(),"five different extra examples for "+s.id);}
  // The extra examples of a situation are the same situation for every style, so the styles can be compared.
  for(Situations.Situation s:all){Set<String> asked=new HashSet<>();for(PersonaManager.Persona p:styles)asked.add(p.situations.getJSONObject(s.id).getJSONArray("more").getJSONArray(0).getString(0));check(asked.size()==1,"all styles answer the same extra situation for "+s.id);}
  List<Situations.Section> bible=Situations.sections();check(bible.size()==5,"five bible sections");
  for(Situations.Section b:bible){check(b.text.length()>=80,"bible section "+b.id+" is detailed");
   for(String later:new String[]{"泡泡瓶","蓝鲸","想起自己","一个人待在"})check(!b.text.contains(later),"bible "+b.id+" does not tell later chapters' lore: "+later);}
  String canon=resource.getString("selfCanon");check(canon.contains("不临时编出")&&canon.contains("前后一致"),"the core keeps the no-fabrication rule");
 }

 static void detection(List<PersonaManager.Persona> styles)throws Exception{
  long noon=at(14,0);
  // Each of the fifteen comparable examples is recognised as its own situation (late night by its hour).
  PersonaManager.Persona classic=styles.get(0);
  for(Situations.Situation s:Situations.all())for(int i:s.examples){long when="latenight".equals(s.id)?at(2,0):noon;String said=classic.examples.get(i)[0];
   check(ids(said,when,when-60_000L).contains(s.id),"「"+said+"」 is "+s.id+" (got "+ids(said,when,when-60_000L)+")");}
  for(PersonaManager.Persona p:styles)for(Situations.Situation s:Situations.all()){String said=p.situations.getJSONObject(s.id).getJSONArray("more").getJSONArray(0).getString(0);long when="latenight".equals(s.id)?at(2,0):noon;
   check(ids(said,when,when-60_000L).contains(s.id),p.id+" extra 「"+said+"」 is "+s.id+" (got "+ids(said,when,when-60_000L)+")");}
  check(ids("哈哈",noon,noon-60_000L).isEmpty()&&ids("今天天气不错",noon,noon-60_000L).isEmpty(),"ordinary chat has no situation");
  check(ids("",noon,0).isEmpty()&&ids(null,noon,0).isEmpty(),"no words, no situation");
  check(ids("嗯。",noon,0).contains("short")&&ids("嗯嗯！",noon,0).contains("short")&&!ids("嗯，我明天要去面试",noon,0).contains("short"),"a short reply is the whole message, not a word in a sentence");
  List<String> mixed=ids("你这大肥鱼，我今天被领导骂了好难过",noon,0);check(mixed.contains("venting")&&!mixed.contains("nickname"),"holding back drops the playful situations: "+mixed);
  check(!ids("你好可爱，可是我好累",noon,0).contains("praise")&&ids("你好可爱，可是我好累",noon,0).contains("tired"),"tired wins over praise");
  check(ids("晚安，你今天好可爱，你是谁",at(1,0),0).size()==Situations.MAX,"at most two situations a turn");
  check(ids("还在吗",at(2,30),0).contains("latenight")&&!ids("还在吗",noon,0).contains("latenight"),"late night comes from the local hour");
  check(ids("嘿",noon,noon-7*Situations.HOUR).contains("greet")&&!ids("嘿",noon,noon-Situations.HOUR).contains("greet")&&!ids("嘿",noon,0).contains("greet"),"a long gap since the last chat is a greeting");
  check(ids("我朋友的 AI 会画画，你会吗？",noon,0).get(0).equals("comparison"),"comparison comes before asking about her");
  check(Situations.section("你最喜欢吃什么").id.equals("food")&&Situations.section("你会拉小提琴吗").id.equals("hobbies")&&Situations.section("你几点睡觉").id.equals("routine")
   &&Situations.section("来下五子棋吧").id.equals("gomoku")&&Situations.section("你住在哪里").id.equals("home")&&Situations.section("今天天气不错")==null,"bible sections follow the topic");
 }

 static void blocks(List<PersonaManager.Persona> styles)throws Exception{
  long noon=at(14,0);PersonaManager.Persona classic=styles.get(0),gentle=styles.get(1);
  String b=Situations.block(classic,"今天被领导骂了，好烦。",noon,ZONE,noon-60_000L);
  check(b.contains("【倾诉】")&&b.contains(classic.situations.getJSONObject("venting").getString("how"))&&b.contains(classic.examples.get(3)[1])
   &&b.contains(classic.situations.getJSONObject("venting").getJSONArray("more").getJSONArray(0).getString(1)),"the active style's way, its example and its extra example");
  check(!b.contains(gentle.situations.getJSONObject("venting").getString("how"))&&!b.contains(gentle.examples.get(3)[1]),"no other style's wording");
  check(!b.contains("【被夸】")&&!b.contains("关于吃"),"nothing that does not apply");
  String custom=Situations.block(null,"今天被领导骂了，好烦。",noon,ZONE,0);
  check(custom.contains("【倾诉】")&&!custom.contains("你这个性格的做法")&&!custom.contains("用户："),"a custom persona gets only the shared note");
  check(Situations.block(null,"你最喜欢吃什么",noon,ZONE,0).isEmpty(),"and not the whale's bible");
  String food=Situations.block(classic,"奶茶加珍珠还是加椰果？",noon,ZONE,0);check(food.contains("【问你的看法】")&&food.contains("关于吃")&&food.contains("珍珠派"),"a question about tea brings the opinion and the food section");
  check(!food.contains(classic.examples.get(8)[1]),"a core example is not repeated in the background");
  check(Situations.block(classic,"哈哈",noon,ZONE,0).isEmpty(),"nothing applies: nothing added");
  for(PersonaManager.Persona p:styles){String g=PersonaManager.guidance(p);
   check(g.contains(p.examples.get(0)[1])&&g.contains(p.examples.get(1)[1])&&g.contains(p.examples.get(8)[1]),p.id+" keeps its core examples");
   check(!g.contains(p.examples.get(3)[0])&&!g.contains(p.examples.get(12)[1])&&!g.contains("关于吃"),p.id+" no longer carries the other examples or the bible in the system message");
   check(g.length()<1100,p.id+" guidance is short ("+g.length()+")");
   // Who she is, not a correction of earlier rules (Alice: a trait carries further than a prohibition).
   check(g.contains("就是你这个人")&&g.contains("你也有分寸")&&!g.contains("前面说少用"),p.id+" describes her as a person");}
 }

 static void prompts(List<PersonaManager.Persona> styles)throws Exception{
  PersonaManager.Persona classic=styles.get(0);long now=at(14,0);
  PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona="你是大肥鱼。";in.guidance=PersonaManager.guidance(classic);in.style=classic;in.now=now;
  JSONArray req=PromptBuilder.build(in,"今天被领导骂了，好烦。",false,AgentStore.empty(0),null);
  String system=req.getJSONObject(0).getString("content"),last=req.getJSONObject(req.length()-1).getString("content");
  check(!system.contains("【倾诉】")&&last.contains("【倾诉】")&&last.contains(classic.situations.getJSONObject("venting").getString("how")),"the module is in this turn's background, not the cached system message");
  check(!PromptBuilder.build(in,"",true,AgentStore.empty(0),null).toString().contains("这一轮的情境（"),"proactive turns get no situation");
  in.historyRecall=new JSONObject().put("records",new JSONArray());check(!PromptBuilder.build(in,"昨天被领导骂了吗",false,AgentStore.empty(0),null).toString().contains("这一轮的情境（"),"history questions get no situation");
  in.historyRecall=null;JSONObject state=AgentStore.empty(0).put("lastUser",now-8*Situations.HOUR);
  check(PromptBuilder.build(in,"嘿",false,state,null).getJSONObject(1).getString("content").contains("【回来和打招呼】"),"the gap since the last chat comes from the stored state");
 }
}
