package com.xiaoxiao.pet;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Situation modules (docs/CHARACTER.md): instead of putting every example and detail of her character in the
 * system message, the app recognises this turn's situation from the user's own words, the time and the gap since
 * the last chat, and adds only that module to the background: a shared note on what the situation calls for,
 * the active style's way of handling it and its examples. Topical parts of her bible (food, gomoku, hobbies,
 * routine, where she lives) come the same way. Local rules only; no model call. Read from assets/personas.json.
 */
final class Situations {
    static final int MAX=2;
    static final long HOUR=3_600_000L;
    private static final Pattern TRAILING=Pattern.compile("[\\s。！!？?~～,，、]+$");

    static final class Situation {
        final String id,name,note,scene;final boolean settle,playful;final Pattern triggers,exact;final int fromHour,toHour,absentHours;final int[] examples;
        Situation(JSONObject o)throws Exception{
            id=o.getString("id");name=o.getString("name");note=o.getString("note");scene=o.optString("scene","");
            settle=o.optBoolean("settle");playful=o.optBoolean("playful");
            triggers=o.has("triggers")?Pattern.compile(o.getString("triggers"),Pattern.CASE_INSENSITIVE):null;
            exact=o.has("exact")?Pattern.compile("(?:"+o.getString("exact")+")"):null;
            JSONArray hours=o.optJSONArray("hours");fromHour=hours==null?-1:hours.getInt(0);toHour=hours==null?-1:hours.getInt(1);
            absentHours=o.optInt("absentHours",0);
            JSONArray a=o.getJSONArray("examples");examples=new int[a.length()];for(int i=0;i<a.length();i++)examples[i]=a.getInt(i);
            if(note.trim().isEmpty()||triggers==null&&exact==null&&fromHour<0&&absentHours==0&&scene.isEmpty())throw new IllegalStateException("情境缺少说明或触发条件："+id);
        }
        /** Whether this situation applies; text is the user's message, trimmed. */
        boolean matches(String text,String detectedScene,int hour,long now,long lastUser){
            if(exact!=null&&exact.matcher(TRAILING.matcher(text).replaceAll("")).matches())return true;
            if(triggers!=null&&triggers.matcher(text).find())return true;
            if(!scene.isEmpty()&&scene.equals(detectedScene))return true;
            if(fromHour>=0&&hour>=fromHour&&hour<toHour)return true;
            return absentHours>0&&lastUser>0&&now-lastUser>=absentHours*HOUR;
        }
    }
    /** A topical part of her bible, sent only when the user's words touch it. */
    static final class Section {
        final String id,name,text;final Pattern triggers;
        Section(JSONObject o)throws Exception{id=o.getString("id");name=o.getString("name");text=o.getString("text");triggers=Pattern.compile(o.getString("triggers"),Pattern.CASE_INSENSITIVE);}
    }

    private static List<Situation> situations=Collections.emptyList();
    private static List<Section> bible=Collections.emptyList();
    private static int[] core=new int[0];

    /** Parses the situations, bible and core examples of personas.json (PersonaManager.load). */
    static synchronized void load(JSONObject resource)throws Exception{
        List<Situation> s=new ArrayList<>();JSONArray a=resource.optJSONArray("situations");
        if(a!=null)for(int i=0;i<a.length();i++)s.add(new Situation(a.getJSONObject(i)));
        List<Section> b=new ArrayList<>();JSONArray c=resource.optJSONArray("bible");
        if(c!=null)for(int i=0;i<c.length();i++)b.add(new Section(c.getJSONObject(i)));
        JSONArray k=resource.optJSONArray("coreExamples");int[] ce=new int[k==null?0:k.length()];for(int i=0;i<ce.length;i++)ce[i]=k.getInt(i);
        situations=Collections.unmodifiableList(s);bible=Collections.unmodifiableList(b);core=ce;
    }
    static List<Situation> all(){return situations;}
    static List<Section> sections(){return bible;}
    static int[] core(){return core.clone();}
    static boolean isCore(int example){for(int c:core)if(c==example)return true;return false;}

    /**
     * The situations of this turn, highest priority (resource order) first, at most MAX. When one calls for her
     * to hold back (settle: venting, tired, serious, help, correction), the playful ones are left out.
     */
    static List<Situation> detect(String user,long now,ZoneId zone,long lastUser){
        List<Situation> out=new ArrayList<>();String text=user==null?"":user.trim();if(text.isEmpty())return out;
        String scene=Understanding.scene(text);int hour=Instant.ofEpochMilli(now).atZone(zone).getHour();boolean settle=false;
        for(Situation s:situations)if(s.matches(text,scene,hour,now,lastUser)){out.add(s);settle|=s.settle;}
        if(settle)out.removeIf(s->s.playful);
        return out.size()>MAX?new ArrayList<>(out.subList(0,MAX)):out;
    }
    /** The first bible section the user's words touch, or null. */
    static Section section(String user){
        if(user==null||user.trim().isEmpty())return null;
        for(Section s:bible)if(s.triggers.matcher(user).find())return s;return null;
    }

    /**
     * The background block for this turn, or "" when nothing applies. style null: a custom persona (or none), which
     * gets only the shared notes; the styles' wording, examples and her bible belong to the preset character.
     */
    static String block(PersonaManager.Persona style,String user,long now,ZoneId zone,long lastUser){
        List<Situation> found=detect(user,now,zone,lastUser);Section part=style==null?null:section(user);
        if(found.isEmpty()&&part==null)return "";
        StringBuilder b=new StringBuilder();
        if(!found.isEmpty()){
            b.append("\n这一轮的情境（应用按对方这句话和时间判断的，可能不准；贴合才参考，不要说出情境名）：");
            for(Situation s:found){
                b.append("\n【").append(s.name).append("】").append(s.note);
                JSONObject move=style==null?null:style.situations.optJSONObject(s.id);if(move==null)continue;
                b.append("\n你这个性格的做法：").append(move.optString("how"));
                List<String[]> pairs=new ArrayList<>();
                for(int i:s.examples)if(!isCore(i)&&i>=0&&i<style.examples.size())pairs.add(style.examples.get(i));
                JSONArray more=move.optJSONArray("more");if(more!=null)for(int i=0;i<more.length();i++){JSONArray m=more.optJSONArray(i);if(m!=null)pairs.add(new String[]{m.optString(0),m.optString(1)});}
                if(!pairs.isEmpty()){b.append("\n口吻参考（体会语气，不要照抄）：");for(String[] x:pairs)b.append("\n用户：").append(x[0]).append("\n你：").append(x[1]);}
            }
        }
        if(part!=null)b.append("\n").append(part.text);
        return b.toString();
    }
}
