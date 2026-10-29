package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * Keeps growth-main.json and applies the rules in Growth. Writes are atomic (temp file, fsync, rename) and run
 * on one background thread, newest state last. Lock order: this store never calls AgentStore while holding its
 * own lock, so AgentStore may notify it from inside its own transactions.
 */
final class GrowthStore {
    /** What the store needs from the rest of the app; the JVM tests supply their own. */
    interface Host {
        long since();boolean initialized();boolean custom();String nickname();
        /** The memory/chat state (AgentStore snapshot), or null when it cannot be read. */
        JSONObject agent();
    }
    static final String VERSION_BEFORE="0.4.x";
    private static GrowthStore instance;
    private static Growth.Story story;
    /** Called (on any thread) after the unlocked set or the hunger state may have changed. */
    static volatile Runnable changed;

    final File file;final Growth.Story s;final Host host;final Random random;
    private JSONObject g;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"growth-writer");t.setDaemon(true);return t;});

    GrowthStore(File file,Growth.Story s,Host host,Random random){
        this.file=file;this.s=s;this.host=host;this.random=random;
        JSONObject loaded=null;
        for(File f:new File[]{file,new File(file+".bak")}){if(loaded!=null||!f.exists())continue;try{JSONObject o=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));if(o.optInt("schemaVersion")==Growth.SCHEMA)loaded=o;}catch(Exception unreadable){/* try the backup, then rebuild */}}
        try{
            if(loaded==null){
                long now=System.currentTimeMillis();
                if(host.initialized()){
                    // A companion from 0.4.x (or a lost growth file): estimate once from what the phone already has.
                    JSONObject a=host.agent();if(a==null)a=new JSONObject();JSONArray memories=a.optJSONArray("memories"),diary=a.optJSONArray("diary"),touches=a.optJSONArray("interactions");
                    Growth.Material m=new Growth.Material(memories,host.nickname(),Growth.day(host.since(),now),0,0);
                    loaded=Growth.migrate(s,now,host.since(),a.optInt("chatDays"),memories,diary,touches==null?0:touches.length(),m,host.custom(),file.exists()?"recovered":VERSION_BEFORE);
                }else loaded=Growth.fresh(now);
                g=loaded;persist();
            }else g=loaded;
        }catch(Exception e){throw new IllegalStateException("成长记录无法建立",e);}
    }

    static synchronized Growth.Story story(){
        if(story!=null)return story;
        try{return story=new Growth.Story(new JSONObject(Data.read(Data.app.getAssets().open("story.json"))));}
        catch(Exception e){throw new IllegalStateException("剧情资源读取失败，请重新安装完整 APK",e);}
    }
    static synchronized GrowthStore get(){
        if(instance!=null)return instance;
        Host host=new Host(){
            public long since(){return PersonaManager.initializedAt("main");}
            public boolean initialized(){return PersonaManager.isInitialized("main");}
            public boolean custom(){return PersonaManager.CUSTOM.equals(PersonaManager.mode("main"));}
            public String nickname(){try{return Nickname.current();}catch(Exception e){return "";}}
            public JSONObject agent(){try{return AgentRuntime.store().snapshot();}catch(Exception e){return null;}}
        };
        GrowthStore store=new GrowthStore(new File(Data.app.getFilesDir(),"growth-main.json"),story(),host,Data.random);
        // Forgetting or clearing in the memory ledger pops bubbles and strips quotes from chapter cards.
        AgentStore.deleted=()->store.writer.execute(()->{try{store.sync();}catch(Exception e){Data.log("main","成长记录同步暂未完成");}});
        return instance=store;
    }
    /** For callers that must not fail because of growth (chat, taps): null when the store cannot be opened. */
    static GrowthStore quiet(){try{return get();}catch(Exception e){Data.log("main","成长记录暂不可用");return null;}}

    private void persist(){
        final String json=g.toString();
        writer.execute(()->{try{
            File tmp=new File(file+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            // Back up the validated new snapshot, not the previous one: deleted quotes must not return
            // through recovery after the primary file is damaged. Both writes use atomic replacement.
            if(file.exists())ChatArchive.write(new File(file+".bak"),json.getBytes(StandardCharsets.UTF_8));
            try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){Data.log("main","成长记录写入失败");}});
    }
    /** Waits for queued writes; tests and export use it. */
    void flush()throws Exception{writer.submit(()->{}).get(10,TimeUnit.SECONDS);}

    private ZoneId zone(){return ZoneId.systemDefault();}
    private long now(){return System.currentTimeMillis();}
    /** Applies one change: chapters advance afterwards, the file is saved and listeners hear about new unlocks. */
    private interface Change<T>{T apply(long now)throws Exception;}
    private <T> T change(Change<T> c)throws Exception{
        T out;boolean moved;
        synchronized(this){long now=now();out=c.apply(now);moved=Growth.advance(g,s,host.since(),now);persist();}
        if(moved&&out instanceof Growth.Award)((Growth.Award)out).chapterUp=true;
        Runnable listener=changed;if(listener!=null&&moved)listener.run();
        return out;
    }

    Growth.Award met()throws Exception{return change(now->Growth.met(g,s,now,zone(),random));}
    Growth.Award chatted()throws Exception{return change(now->Growth.chatted(g,now,zone()));}
    Growth.Award touched()throws Exception{return change(now->Growth.touched(g,now,zone()));}
    Growth.Award read(String id)throws Exception{return change(now->Growth.read(g,id,now));}
    String feed(Growth.Food food,Growth.Award out)throws Exception{String r=change(now->Growth.feed(g,food,now,zone(),out));Runnable l=changed;if(r==null&&l!=null)l.run();return r;}
    String startTrip(boolean snack)throws Exception{return change(now->Growth.startTrip(g,snack,now,random));}
    /** Ends a trip, early or on time; null when there was none. */
    String endTrip(Growth.Award out)throws Exception{return change(now->Growth.endTrip(g,s,now,zone(),random,out));}
    /** Re-reads memories and diary: bubbles, the memory reward and quote stripping (Growth.sync). */
    Growth.Award sync()throws Exception{
        JSONObject a=host.agent();if(a==null)return new Growth.Award();
        return change(now->Growth.sync(g,a.optJSONArray("memories"),a.optJSONArray("archive"),a.optJSONArray("diary"),now,zone()));
    }
    /** First time a clip is played: recorded for the action and seasonal collections. Saved only when new. */
    synchronized void seen(String action){try{if(Growth.seen(g,action,now()))persist();}catch(Exception ignored){}}

    /** The scene for the next pending chapter, built from real memories; null when none is waiting. */
    synchronized int pending(){return Growth.pending(g);}
    Growth.Scene nextScene()throws Exception{
        if(pending()==0)return null;sync();JSONObject a=host.agent();
        synchronized(this){
            int chapter=Growth.pending(g);if(chapter==0)return null;
            Growth.Material m=new Growth.Material(a==null?null:a.optJSONArray("memories"),host.nickname(),Growth.day(host.since(),now()),Growth.bubbles(g),g.optInt("feeds"));
            return Growth.scene(s,chapter,host.custom(),m);
        }
    }
    Growth.Award shown(Growth.Scene scene)throws Exception{return change(now->Growth.shown(g,scene,now,false));}

    synchronized JSONObject snapshot(){try{return new JSONObject(g.toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    synchronized int chapter(){return Growth.chapter(g);}
    synchronized Set<String> unlocked(Collection<String> all){return Growth.unlocked(g,s,all);}
    synchronized boolean hungry(){try{return Growth.hungry(g,now(),zone());}catch(Exception e){return false;}}
    synchronized long lastFed(){return g.optLong("lastFed");}
    synchronized JSONObject trip(){JSONObject t=Growth.trip(g);try{return t==null?null:new JSONObject(t.toString());}catch(JSONException e){return null;}}
    synchronized boolean tripDue(){return Growth.tripDue(g,now());}
    synchronized JSONObject unread(){JSONObject c=Growth.unread(g);try{return c==null?null:new JSONObject(c.toString());}catch(JSONException e){return null;}}
    /** The lore lines for the prompt; empty for custom personas, whose own persona may not be a whale. */
    String lore(){return host.custom()?"":Growth.lore(s,chapter());}
    /** All clips when the store is unavailable, so a growth failure never empties her repertoire. */
    static Set<String> unlockedOrAll(Collection<String> all){GrowthStore g=quiet();return g==null?new LinkedHashSet<>(all):g.unlocked(all);}
}
