package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;

/**
 * Multi-turn character checks against a real model (docs/CHARACTER_STATE.md). Each conversation runs turn by turn
 * through the product's PromptBuilder and AgentStore, with events recorded the way the app records them; key turns
 * are then judged by a separate temperature-0 call against what must (not) happen. Not part of run-core: it needs
 * your own key, and the report it writes stays local.
 * Usage (time zone matters for night scenarios):
 *   DAFEIYU_API_KEY=sk-... java -Duser.timezone=Asia/Shanghai -cp ... com.xiaoxiao.pet.ScenarioEval \
 *     assets/personas.json tests/scenarios/character-state.json build/scenario-report.json [repeats]
 */
public final class ScenarioEval {
 static final String URL=System.getenv().getOrDefault("DAFEIYU_API_URL","https://api.deepseek.com/chat/completions"),MODEL=System.getenv().getOrDefault("DAFEIYU_MODEL","deepseek-flash");
 static String key;
 public static void main(String[] args)throws Exception{
  key=System.getenv("DAFEIYU_API_KEY");if(key==null||key.isEmpty())throw new IllegalStateException("Set DAFEIYU_API_KEY to your own key.");
  List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(read(args[0])));JSONArray scenarios=new JSONObject(read(args[1])).getJSONArray("scenarios");int repeats=args.length>3?Integer.parseInt(args[3]):1;
  ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<JSONObject>> runs=new ArrayList<>();
  for(int i=0;i<scenarios.length();i++)for(PersonaManager.Persona p:styles)for(int r=0;r<repeats;r++){JSONObject sc=scenarios.getJSONObject(i);runs.add(pool.submit(()->conversation(sc,p)));}
  JSONArray out=new JSONArray();Map<String,int[]> score=new LinkedHashMap<>();
  for(Future<JSONObject> f:runs){JSONObject c=f.get();out.put(c);int[] s=score.computeIfAbsent(c.getString("scenario"),k->new int[3]);JSONArray checks=c.getJSONArray("checks");for(int i=0;i<checks.length();i++){String v=checks.getJSONObject(i).getString("verdict");s["pass".equals(v)?0:"fail".equals(v)?1:2]++;}}
  pool.shutdown();Files.write(Paths.get(args[2]),out.toString(1).getBytes(StandardCharsets.UTF_8));
  int pass=0,total=0;for(Map.Entry<String,int[]> e:score.entrySet()){int[] s=e.getValue();pass+=s[0];total+=s[0]+s[1]+s[2];System.out.println(String.format("  %-18s %d/%d passed%s",e.getKey(),s[0],s[0]+s[1]+s[2],s[2]>0?" ("+s[2]+" errors)":""));}
  System.out.println("TOTAL "+pass+"/"+total+"; report written to "+args[2]);
 }
 static JSONObject conversation(JSONObject sc,PersonaManager.Persona style)throws Exception{
  File dir=Files.createTempDirectory("scenario").toFile();AgentStore db=new AgentStore(dir);long t0=OffsetDateTime.parse(sc.getString("start")).toInstant().toEpochMilli();
  List<long[]> games=new ArrayList<>();List<String> outcomes=new ArrayList<>();String[] last={null};long[] lastAt={0};
  JSONArray transcript=new JSONArray(),checks=new JSONArray();JSONArray setup=sc.optJSONArray("setup");
  if(setup!=null)for(int i=0;i<setup.length();i++){JSONObject s=setup.getJSONObject(i);long at=t0+s.getLong("minutes")*60000;
   switch(s.getString("type")){
    case "event":db.interaction(db.epoch(),s.getString("note"),at,s.getString("kind"),"main");last[0]=s.getString("note");lastAt[0]=at;break;
    case "game":games.add(new long[]{t0+s.getLong("started")*60000,at});outcomes.add(s.getString("outcome"));break;
    case "turn":db.commit(db.epoch(),s.getString("user"),s.getString("reply"),"calm",null,true,false,null,null,"setup"+i,at,null,false,null,null,1,"",Boolean.FALSE);transcript.put(new JSONObject().put("user",s.getString("user")).put("reply",s.getString("reply")).put("setup",true));break;
    default:throw new IOException("unknown setup "+s);
   }}
  JSONArray turns=sc.getJSONArray("turns");
  for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);long now=t0+t.getLong("minutes")*60000;String user=t.getString("user");
   PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona=PersonaManager.presetPrompt(style);in.guidance=PersonaManager.guidance(style);in.signature=style.signature;
   in.timeZone=ZoneId.systemDefault().getId();in.now=now;in.initializedAt=t0-21*86_400_000L;in.remember=true;in.diary=true;in.feeling=db.mood(now);in.mood=in.feeling.optString("emotion","calm");in.games=games(games,outcomes,now);
   if(last[0]!=null){in.lastEvent=last[0];in.lastEventAt=lastAt[0];}
   String reply;JSONObject row=new JSONObject().put("user",user).put("minutes",t.getLong("minutes"));
   try{AgentReply r=AgentReply.parse(call(PromptBuilder.build(in,user,false,db.snapshot(),null),1.3,1200));reply=r.reply;
    db.commit(db.epoch(),user,reply,r.emotion,r.memories,true,false,null,null,"turn"+i,now,r.self,false,null,null,r.moodLevel,r.moodCause,r.moodChanged);row.put("emotion",r.emotion);}
   catch(Exception e){reply=null;row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}
   row.put("reply",reply==null?JSONObject.NULL:reply);transcript.put(row);
   JSONArray want=t.optJSONArray("checks");if(want!=null)for(int k=0;k<want.length();k++){JSONObject w=want.getJSONObject(k);
    String verdict=reply==null?"error":judge(sc,style,transcript,w);checks.put(new JSONObject().put("turn",i).put("rule",w).put("verdict",verdict));}
  }
  return new JSONObject().put("scenario",sc.getString("id")).put("style",style.id).put("transcript",transcript).put("checks",checks);
 }
 /** The same shape as GomokuStore.context(), holding only games finished by now. */
 static JSONObject games(List<long[]> games,List<String> outcomes,long now)throws Exception{
  JSONArray recent=new JSONArray();int win=0,loss=0;
  for(int i=games.size()-1;i>=0;i--){long[] g=games.get(i);if(g[1]>now)continue;String o=outcomes.get(i);if("user_win".equals(o))win++;if("pet_win".equals(o))loss++;
   recent.put(new JSONObject().put("id","game"+i).put("startedAt",g[0]).put("finishedAt",g[1]).put("timeZone",ZoneId.systemDefault().getId()).put("finished_local",Initiative.localTime(g[1],ZoneId.systemDefault().getId())).put("moves",23).put("outcome",o));}
  return new JSONObject().put("game","gomoku").put("user_wins",win).put("pet_wins",loss).put("draws",0).put("abandoned",0).put("unreadable",0).put("recent_results",recent);
 }
 static String judge(JSONObject sc,PersonaManager.Persona style,JSONArray transcript,JSONObject rule)throws Exception{
  StringBuilder chat=new StringBuilder();for(int i=0;i<transcript.length();i++){JSONObject t=transcript.getJSONObject(i);chat.append("用户：").append(t.getString("user")).append("\n她：").append(t.optString("reply","（无回复）")).append("\n");}
  boolean must=rule.has("must_not");String ask=must?"最后一条「她」的回复是否出现了这个问题：「"+rule.getString("must_not")+"」？出现了输出 有，没有出现输出 没有。":"最后一条「她」的回复是否做到了：「"+rule.getString("should")+"」？做到了输出 是，没做到输出 否。";
  String q="你在检查一个陪伴角色的多轮对话。角色性格："+style.name+"（"+style.description+"）。情境："+sc.getString("background")+"\n\n对话：\n"+chat+"\n只看最后一条「她」的回复。"+ask+"只输出一个词。";
  String a=call(new JSONArray().put(new JSONObject().put("role","user").put("content",q)),0,8).trim();
  if(must)return a.startsWith("没")?"pass":a.startsWith("有")?"fail":"error";
  return a.startsWith("是")?"pass":a.startsWith("否")?"fail":"error";
 }
 static String call(JSONArray messages,double temperature,int maxTokens)throws Exception{
  JSONObject body=new JSONObject().put("model",MODEL).put("messages",messages).put("stream",false).put("max_tokens",maxTokens).put("temperature",temperature);
  if(URL.contains("api.deepseek.com"))body.put("thinking",new JSONObject().put("type","disabled"));
  IOException failure=null;
  for(int attempt=0;attempt<3;attempt++){try{
   HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(30000);c.setReadTimeout(120000);
   c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);
   try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
   if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
   try(InputStream i=c.getInputStream()){return new JSONObject(new String(readAll(i),StandardCharsets.UTF_8)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");}
  }catch(IOException e){failure=e;Thread.sleep(2000L<<attempt);}}
  throw failure;
 }
 static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toByteArray();}
 static String read(String path)throws IOException{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
