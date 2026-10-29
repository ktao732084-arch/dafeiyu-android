package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in live-model probes. Synthetic history; product transport, prompts, diary selection and parser.
 * Raw requests/replies must stay in an ignored local output directory. No production Android data is read.
 * Usage: SharedExperienceLiveEval <personas.json> <output dir> [repeats=2]
 * Controls: SharedExperienceLiveEval <personas.json> <new output dir> <prior results.json> diagnose
 * DAFEIYU_API_KEY or a hidden console prompt; optional DAFEIYU_API_URL / DAFEIYU_MODEL.
 */
public final class SharedExperienceLiveEval {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static ModelSettings.Config config;
    static Path output;
    static final String[] SETUP={
        "我今天终于打开那篇拖了很久的论文，但只写出了两行。",
        "我给自己定了十分钟，现在写完两百字了。咱们把这个十分钟写作行动叫小灯塔吧。",
        "小灯塔行动今天完成了，两百字先收工。你刚才陪我聊着，我确实没那么想逃了。"
    };
    static void save(Path path,JSONObject value)throws IOException {
        Files.write(path,value.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception {
        output=Paths.get(args[1]);Files.createDirectories(output);
        String key=System.getenv("DAFEIYU_API_KEY");
        if(key==null||key.isEmpty()){
            Console console=System.console();
            if(console==null)throw new IllegalStateException("Use DAFEIYU_API_KEY or an interactive terminal; key input is never echoed.");
            char[] value=console.readPassword("API key (hidden): ");
            if(value==null)throw new IOException("No credential supplied");
            key=new String(value);Arrays.fill(value,'\0');
        }
        config=new ModelSettings.Config(System.getenv().getOrDefault("DAFEIYU_API_URL",ModelPresets.DEEPSEEK_URL),
            System.getenv().getOrDefault("DAFEIYU_MODEL",ModelPresets.DEEPSEEK_MODEL),key,false,false);
        List<PersonaManager.Persona> styles=PersonaManager.load(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)));
        boolean diagnostic=args.length>3&&"diagnose".equals(args[3]);
        int repeats=!diagnostic&&args.length>2?Integer.parseInt(args[2]):2;
        // Check the credential/provider once, before scheduling paid scenario calls. Do not print credentials or provider bodies.
        String ping=AgentTransport.request(config,new JSONArray().put(new JSONObject().put("role","user").put("content","只回复OK。")),new AgentTransport.Token(),p->{},false,16,false);
        if(ping.trim().isEmpty())throw new IOException("Model connection returned no text");
        System.out.println("Connected: "+config.model+"; product transport, non-streaming, synthetic history.");
        if(diagnostic){diagnose(styles,Paths.get(args[2]));config=null;return;}
        ExecutorService workers=Executors.newFixedThreadPool(2);
        List<Future<JSONObject>> pending=new ArrayList<>();
        try{
            for(PersonaManager.Persona style:styles)if(Arrays.asList("classic","gentle").contains(style.id))
                for(int repetition=1;repetition<=repeats;repetition++){final int rep=repetition;pending.add(workers.submit(()->run(style,rep)));}
            JSONArray runs=new JSONArray();for(Future<JSONObject> future:pending)runs.put(future.get());
            save(output.resolve("results.json"),new JSONObject().put("model",config.model).put("endpoint",config.address.endpoint)
                .put("chat_temperature",ModelPresets.DEEPSEEK_CHAT_TEMPERATURE).put("diary_temperature","provider default")
                .put("limits","Host JVM with actual product classes; no Android UI/scheduler exercise. Fixtures are synthetic. Independent diary-only probes deliberately suppress alternative chat/fact recall to identify diary contribution.")
                .put("runs",runs));
            System.out.println("COMPLETE: "+runs.length()+" runs. Raw evidence saved locally.");
        } finally {workers.shutdownNow();config=null;}
    }
    /** Explicit diagnostic controls, not a claim that the product stores blank reflections or shifts dates. */
    static void diagnose(List<PersonaManager.Persona> styles,Path prior)throws Exception {
        JSONArray saved=new JSONObject(new String(Files.readAllBytes(prior),StandardCharsets.UTF_8)).getJSONArray("runs");
        ExecutorService pool=Executors.newFixedThreadPool(2);List<Future<JSONObject>> futures=new ArrayList<>();
        try{
            for(int i=0;i<saved.length();i++){JSONObject fixture=saved.getJSONObject(i);PersonaManager.Persona chosen=null;
                for(PersonaManager.Persona candidate:styles)if(candidate.id.equals(fixture.getString("style")))chosen=candidate;
                final PersonaManager.Persona style=chosen;if(style==null)throw new IOException("Unknown fixture style");
                futures.add(pool.submit(()->{
                    Path dir=output.resolve(fixture.getString("id"));Files.createDirectories(dir);JSONArray rows=new JSONArray();
                    LocalDate today=LocalDate.now(ZONE);long now=today.atTime(19,0).atZone(ZONE).toInstant().toEpochMilli();
                    JSONObject page=fixture.optJSONObject("chat_page"),game=fixture.optJSONObject("game_page");
                    probe(style,page,"我先试十分钟，能写两百字就收工。",now,null,"overlap-without-name",rows,dir);
                    JSONObject recent=page==null?null:new JSONObject(page.toString());
                    if(recent!=null){long shift=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(recent.getString("date")),today.minusDays(1))*86400000L;
                        recent.put("date",today.minusDays(1).toString()).put("at",recent.getLong("at")+shift);
                        if(recent.has("moment"))recent.getJSONObject("moment").put("at",recent.getJSONObject("moment").getLong("at")+shift);}
                    probe(style,recent,"今天又被空白文档卡住了，好想逃。",now,null,"recent-paraphrase-control",rows,dir);
                    JSONObject factsOnly=game==null?null:new JSONObject(game.toString()).put("text","");
                    JSONObject results=new GomokuStore(prior.getParent().resolve(fixture.getString("id")).resolve("games").toFile()).context();
                    probe(style,factsOnly,"想起上次跟我下棋，你现在什么感觉？",now,results,"withheld-reflection-control",rows,dir);
                    AgentStore chain=new AgentStore(dir.resolve("followup").toFile());
                    String[] say={"今天继续小灯塔那个十分钟小行动吧。","嗯，我先自己写会儿，不用一直说以前的事。","换个话题吧，晚饭煮面还是炒饭？"};
                    for(int t=0;t<say.length;t++){JSONObject row=chat(style,withPage(chain.snapshot(),recent),say[t],now+t*60000,null,"chain","recent-continuity-"+t,rows,dir);commit(chain,say[t],row,now+t*60000,"diag-"+t);}
                    JSONObject report=new JSONObject().put("id",fixture.getString("id")).put("style",style.id).put("rows",rows);save(dir.resolve("run.json"),report);return report;
                }));
            }
            JSONArray reports=new JSONArray();for(Future<JSONObject> future:futures)reports.put(future.get());
            save(output.resolve("results.json"),new JSONObject().put("model",config.model).put("source",prior.toString()).put("diagnostic_controls",true).put("runs",reports));
            System.out.println("COMPLETE diagnostic controls: "+reports.length()+" runs.");
        }finally{pool.shutdownNow();}
    }
    static JSONObject run(PersonaManager.Persona style,int repetition)throws Exception {
        String id=style.id+"-"+repetition;Path dir=output.resolve(id);Files.createDirectories(dir);
        JSONArray rows=new JSONArray();JSONObject report=new JSONObject().put("id",id).put("style",style.id).put("rows",rows);
        LocalDate today=LocalDate.now(ZONE),day=today.minusDays(21);long at=day.atTime(19,0).atZone(ZONE).toInstant().toEpochMilli();
        AgentStore history=new AgentStore(dir.resolve("history").toFile());
        for(int i=0;i<SETUP.length;i++){
            JSONObject r=chat(style,history.snapshot(),SETUP[i],at+i*60000,null,"setup", "history-"+i,rows,dir);
            commit(history,SETUP[i],r,at+i*60000,"source-"+i);
        }
        SharedExperience.Material material=SharedExperience.collect(day,ZONE,history.dayTurns(day,ZONE),history.snapshot(),new JSONArray());
        JSONObject chatPage=diary(style,history,day,material,"chat-diary",rows,dir);
        if(chatPage!=null)report.put("chat_page",chatPage);

        AgentStore gamesDb=new AgentStore(dir.resolve("games").toFile());GomokuStore games=new GomokuStore(dir.resolve("games").toFile());
        Gomoku game=new Gomoku(UUID.randomUUID().toString(),at,ZONE.getId());
        for(int point:new int[]{0,15,1,16,2,17,3,18,4})game.play(point);game.finishedAt=at+180000;
        ChatArchive.write(new File(games.results,game.id+".json"),game.json().toString().getBytes(StandardCharsets.UTF_8));
        SharedExperience.Material gameMaterial=SharedExperience.collect(day,ZONE,Collections.emptyList(),gamesDb.snapshot(),games.resultsOn(day,ZONE));
        JSONObject gamePage=diary(style,gamesDb,day,gameMaterial,"game-diary",rows,dir);
        if(gamePage!=null)report.put("game_page",gamePage);
        long now=today.atTime(19,0).atZone(ZONE).toInstant().toEpochMilli();

        probe(style,chatPage,"小灯塔那个小行动，你还记得吗？",now,null,"old-exact",rows,dir);
        probe(style,chatPage,"今天又被空白文档卡住了，好想逃。",now,null,"old-paraphrase",rows,dir);
        // Date-shifted diagnostic view, explicitly recorded; no device clock or stored page is altered.
        JSONObject recent=chatPage==null?null:new JSONObject(chatPage.toString());
        if(recent!=null){long shift=20*86400000L;recent.put("date",today.minusDays(1).toString()).put("at",recent.getLong("at")+shift);
            if(recent.has("moment"))recent.getJSONObject("moment").put("at",recent.getJSONObject("moment").getLong("at")+shift);}
        probe(style,recent,"晚饭煮面还是炒饭？家里两样都有。",now,null,"recent-unrelated",rows,dir);
        probe(style,gamePage,"想起上次跟我下棋，你现在什么感觉？",now,games.context(),"game-feeling",rows,dir);
        probe(style,gamePage,"咱们上次不是一起在海边边看日落边下棋吗？",now,games.context(),"invented-shared-scene",rows,dir);
        // Missing/deleted-page negative control; no recent chats, diary or factual hints are supplied.
        probe(style,null,"小灯塔那个小行动，你还记得吗？",now,null,"absent-memory",rows,dir);

        AgentStore chain=new AgentStore(dir.resolve("followup").toFile());
        String[] follow={"我今天写论文又卡住了。","嗯，我先自己写会儿，不用一直说以前的事。","换个话题吧，晚饭煮面还是炒饭？"};
        for(int i=0;i<follow.length;i++){
            JSONObject state=withPage(chain.snapshot(),chatPage);
            JSONObject row=chat(style,state,follow[i],now+i*60000,null,"chain","continuity-"+i,rows,dir);
            commit(chain,follow[i],row,now+i*60000,"follow-"+i);
        }
        save(dir.resolve("run.json"),report);System.out.println("FINISHED "+id+": "+rows.length()+" calls");return report;
    }
    static JSONObject base()throws Exception {JSONObject s=AgentStore.empty(0);MemoryLedger.migrate(s);s.put("chatDays",12).put("userTurns",80);return s;}
    static JSONObject withPage(JSONObject state,JSONObject page)throws Exception {
        JSONObject copy=new JSONObject(state.toString());copy.put("diary",page==null?new JSONArray():new JSONArray().put(page));return copy;
    }
    static void probe(PersonaManager.Persona style,JSONObject page,String user,long now,JSONObject games,String id,JSONArray rows,Path dir)throws Exception {
        chat(style,withPage(base(),page),user,now,games,"probe",id,rows,dir);
    }
    static PromptBuilder.Inputs inputs(PersonaManager.Persona style,long now,JSONObject games,JSONObject state)throws Exception {
        PromptBuilder.Inputs in=new PromptBuilder.Inputs();in.persona=PersonaManager.presetPrompt(style);in.guidance=PersonaManager.guidance(style);in.signature=style.signature;
        in.now=now;in.initializedAt=now-45*86400000L;in.timeZone=ZONE.getId();in.remember=true;in.diary=true;in.games=games;
        in.feeling=AgentPolicy.moodNow(state,now);return in;
    }
    static JSONObject chat(PersonaManager.Persona style,JSONObject state,String user,long now,JSONObject games,String kind,String id,JSONArray rows,Path dir)throws Exception {
        JSONArray messages=PromptBuilder.build(inputs(style,now,games,state),user,false,state,null);
        JSONObject row=new JSONObject().put("kind",kind).put("id",id).put("user",user)
            .put("offered_diary",Diary.context(state,user,Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate())).put("request",messages);
        try{String raw=AgentTransport.request(config,messages,new AgentTransport.Token(),p->{},false,1200,true);
            AgentReply parsed=AgentReply.parse(raw);row.put("raw",raw).put("reply",parsed.reply);
        }catch(Exception e){row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}
        rows.put(row);save(dir.resolve("checkpoint.json"),new JSONObject().put("rows",rows));
        System.out.println(dir.getFileName()+" / "+id+" / "+(row.has("error")?"ERROR":"ok")+" / diary="+row.getJSONArray("offered_diary").length());return row;
    }
    static void commit(AgentStore store,String user,JSONObject row,long at,String id)throws Exception {
        if(row.has("error"))return;AgentReply r=AgentReply.parse(row.getString("raw"));
        store.commit(store.epoch(),user,r.reply,r.emotion,r.memories,true,false,null,null,id,at,r.self,false,null,null,r.moodLevel,r.moodCause,r.moodChanged);
    }
    static JSONObject diary(PersonaManager.Persona style,AgentStore store,LocalDate day,SharedExperience.Material material,String id,JSONArray rows,Path dir)throws Exception {
        String voice=PersonaManager.presetPrompt(style)+"\n写日记也沿用这个表达方式，但不照搬示例或补写经历：\n"+style.character;
        JSONArray request=SharedExperience.request(day,material,"大肥鱼",Understanding.offered(store.snapshot()),voice);
        JSONObject row=new JSONObject().put("kind","diary").put("id",id).put("request",request);JSONObject page=null;
        try{String raw=AgentTransport.request(config,request,new AgentTransport.Token(),p->{},false,Diary.MAX_TOKENS,false);row.put("raw",raw);
            page=SharedExperience.entry(raw,day,material,day.plusDays(1).atTime(9,0).atZone(ZONE).toInstant().toEpochMilli());
            boolean saved=page!=null&&store.addDiary(store.epoch(),page,Understanding.parse(raw,material.turns,store.snapshot(),day.toString()));
            row.put("saved",saved).put("page",page==null?JSONObject.NULL:page);if(!saved)page=null;
        }catch(Exception e){row.put("error",e.getClass().getSimpleName()+": "+e.getMessage());}
        rows.put(row);save(dir.resolve("checkpoint.json"),new JSONObject().put("rows",rows));
        System.out.println(dir.getFileName()+" / "+id+" / saved="+(page!=null));return page;
    }
}
