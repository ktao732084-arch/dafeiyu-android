package com.xiaoxiao.pet;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable personality per companion; initialization is not an ongoing settings switch. */
final class PersonaManager {
    static final String MANUAL = "manual", RANDOM = "random", CUSTOM = "custom", LEGACY = "legacy";
    static final String DEFAULT_ID = "classic";
    private static List<Persona> loaded;
    private static String sharedPrompt, selfCanon, characterNote;

    static final class Persona {
        final String id, name, description, prompt, character;
        final JSONObject voice,storyLines;
        final List<String[]> examples = new ArrayList<>();
        /** Her recognisable expressions: drift reminders let these recur instead of flagging them as a tic. */
        final List<String> signature = new ArrayList<>();
        Persona(JSONObject value) throws Exception {
            id = value.getString("id");name = value.getString("name");
            description = value.getString("description");prompt = value.getString("prompt");
            character = value.optString("character", "");
            voice=value.optJSONObject("voice");storyLines=value.optJSONObject("storyLines");
            JSONArray marks = value.optJSONArray("signature");
            if (marks != null) for (int i = 0; i < marks.length(); i++) signature.add(marks.getString(i));
            JSONArray pairs = value.optJSONArray("examples");
            if (pairs != null) for (int i = 0; i < pairs.length(); i++)
                examples.add(new String[]{pairs.getJSONArray(i).getString(0), pairs.getJSONArray(i).getString(1)});
            if (!id.matches("[a-z][a-z0-9_-]{0,39}") || name.trim().isEmpty() || prompt.trim().isEmpty())
                throw new IOException("人格预设格式不正确");
        }
    }

    static String key(String role, String name) { return "persona." + role + "." + name; }

    static synchronized List<Persona> presets() {
        if (loaded != null) return loaded;
        try {
            return load(new JSONObject(Data.read(Data.app.getAssets().open("personas.json"))));
        } catch (Exception error) {
            throw new IllegalStateException("人格预设读取失败，请重新安装完整 APK", error);
        }
    }

    /** Parses the bundled presets; also how JVM tests and the persona eval read assets/personas.json. */
    static synchronized List<Persona> load(JSONObject resource) throws Exception {
        if (resource.getInt("schemaVersion") != 1) throw new IOException("人格预设版本不支持");
        JSONArray values = resource.getJSONArray("presets");
        ArrayList<Persona> result = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            Persona item = new Persona(values.getJSONObject(i));
            if (!ids.add(item.id)) throw new IOException("人格预设编号重复");
            result.add(item);
        }
        if (result.size() < 2 || !ids.contains(DEFAULT_ID)) throw new IOException("人格预设不完整");
        sharedPrompt = resource.getString("sharedPrompt");
        selfCanon = resource.optString("selfCanon", "");
        characterNote = resource.optString("characterNote", "");
        loaded = Collections.unmodifiableList(result);
        return loaded;
    }

    static Persona find(String id) {
        for (Persona item : presets()) if (item.id.equals(id)) return item;
        return null;
    }

    static Persona activeStyle(String role){return !isInitialized(role)||CUSTOM.equals(mode(role))?null:find(activeId(role));}
    /** Local event wording shares the selected style; custom personalities retain neutral fallbacks. */
    static String voice(String role,String event,String fallback){return voice(activeStyle(role),event,fallback,Data.random);}
    static String voice(Persona style,String event,String fallback,java.util.Random random){
        if(style==null||style.voice==null)return fallback;Object value=style.voice.opt(event);
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;return a.length()==0?fallback:a.optString(random.nextInt(a.length()),fallback);}
        return value instanceof String?(String)value:fallback;
    }
    static String storyLine(Persona style,String line){
        if(line==null||style==null||style.storyLines==null)return line;String changed=style.storyLines.optString(line,line);
        return slots(line).equals(slots(changed))?changed:line;
    }
    private static java.util.Set<String> slots(String line){java.util.Set<String> found=new java.util.HashSet<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{[a-z]+\\}").matcher(line);while(m.find())found.add(m.group());return found;}
    static String diaryGuidance(String role){Persona style=activeStyle(role);return prompt(role)+(style==null?"":"\n写日记也沿用这个表达方式，但不照搬示例或补写经历：\n"+style.character);}

    /**
     * Her fixed traits, the active style's character sheet and its example dialogues. Unlike the frozen prompt
     * these are read at request time, so companions initialized before they existed get them too (the same
     * personality, described in more detail); a custom persona gets none of them.
     */
    static String guidance(String role) {
        presets();
        if (!isInitialized(role) || CUSTOM.equals(mode(role))) return "";
        return guidance(find(activeId(role)));
    }

    static String guidance(Persona item) {
        StringBuilder out = new StringBuilder(selfCanon);
        if (item != null && !item.character.isEmpty()) {
            out.append(out.length() > 0 ? "\n\n" : "").append("你的性格细节（").append(item.name).append("）：\n").append(item.character);
            if (!characterNote.isEmpty()) out.append("\n").append(characterNote);
        }
        if (item != null && !item.examples.isEmpty()) {
            out.append(out.length() > 0 ? "\n\n" : "").append("同一种你在不同情境下的口吻（体会语气和立场，不要照抄）：");
            for (String[] pair : item.examples) out.append("\n用户：").append(pair[0]).append("\n你：").append(pair[1]);
        }
        return out.toString();
    }

    /** The active style's signature expressions; empty for a custom persona. */
    static List<String> signature(String role) {
        presets();
        if (!isInitialized(role) || CUSTOM.equals(mode(role))) return Collections.emptyList();
        Persona item = find(activeId(role));
        return item == null ? Collections.<String>emptyList() : item.signature;
    }

    static long initializedAt(String role) { return Data.prefs().getLong(key(role, "initializedAt"), 0); }

    static boolean isInitialized(String role) { return Data.prefs().getBoolean(key(role, "locked"), false); }

    /** Returns false only for an unused installation. An unreadable old state is an error, not a reset. */
    static synchronized boolean ensureInitialized(String role) throws IOException {
        if (Data.role(role) == null) throw new IOException("角色不存在");
        if (isInitialized(role)) {
            if (Data.prefs().getString(key(role, "frozenPrompt"), "").trim().isEmpty())
                throw new IOException("已保存的人格无法读取，请保留应用数据后重试");
            return true;
        }
        // Once this version has seen a fresh installation, later notes are not legacy data.
        // The user may configure the app or write a memory before finishing the first meeting.
        if (Data.prefs().getBoolean(key(role, "pending"), false)) return false;
        if (Data.prefs().contains("profile." + role + ".prompt")) {
            freeze(role, CUSTOM, CUSTOM, "原有自定义人格", Data.persona(role));
            return true;
        }
        if (hasLegacyState(role)) {
            // Keep the exact old prompt rather than silently replacing an established companion.
            freeze(role, LEGACY, DEFAULT_ID, "经典傲娇（沿用原人格）", Data.persona(role));
            return true;
        }
        if (!Data.prefs().edit().putBoolean(key(role, "pending"), true).commit())
            throw new IOException("初始化状态保存失败，请重试");
        return false;
    }

    private static boolean hasLegacyState(String role) throws IOException {
        File directory = new File(Data.app.getFilesDir(), "agent");
        File state = new File(directory, "companion-v1.json");
        File backup = new File(state + ".bak"), pending = new File(state + ".tmp");
        try {
            if (state.exists()) {
                // Uses the store's validated backup recovery; never its history() error-swallowing path.
                new AgentStore(directory).snapshot();
                return true;
            }
            if (backup.exists()) {
                JSONObject saved = new JSONObject(Data.read(new FileInputStream(backup)));
                if (saved.optInt("schema") != 1 || saved.optJSONArray("turns") == null || saved.optJSONArray("memories") == null)
                    throw new IOException("旧版记忆备份格式不正确");
                return true;
            }
            if (pending.exists()) throw new IOException("发现未完成保存的旧版记忆，请保留数据后检查");
            File oldChat = new File(Data.app.getFilesDir(), "chat-" + role + ".json");
            if (oldChat.exists()) {
                new JSONArray(Data.read(new FileInputStream(oldChat)));
                return true;
            }
            return false;
        } catch (Exception error) {
            throw new IOException("旧版陪伴记录无法读取，暂不重新初始化人格，请保留应用数据", error);
        }
    }

    static String mode(String role) { return Data.prefs().getString(key(role, "mode"), ""); }
    static String activeId(String role) { return Data.prefs().getString(key(role, "active"), ""); }
    static String label(String role) {
        return isInitialized(role) ? Data.prefs().getString(key(role, "name"), "已固定的人格") : "还未初次相遇";
    }

    static String prompt(String role) {
        try {
            if (!ensureInitialized(role)) throw new IOException("请先在首页完成「初次相遇」，选定她的性格后再聊天");
            return Data.prefs().getString(key(role, "frozenPrompt"), "");
        } catch (IOException error) { throw new IllegalStateException(error.getMessage(), error); }
    }

    static synchronized void initialize(String role, String id) throws IOException {
        rejectReinitialization(role);
        Persona item = find(id);
        if (item == null) throw new IOException("没有找到这个人格预设");
        freeze(role, MANUAL, id, item.name, presetPrompt(item));
    }

    /** Draw once at final initialization. There is intentionally no reroll or unlock API. */
    static synchronized String initializeRandom(String role) throws IOException {
        rejectReinitialization(role);
        List<Persona> choices = presets();
        Persona item = choices.get(Data.random.nextInt(choices.size()));
        freeze(role, RANDOM, item.id, item.name, presetPrompt(item));
        return item.name;
    }

    private static void rejectReinitialization(String role) throws IOException {
        if (ensureInitialized(role)) throw new IOException("她的性格已经在初次相遇时确定，不能再次选择");
    }

    static String presetPrompt(Persona item) {
        return sharedPrompt + "\n\n你的稳定性格：" + item.name + "。\n" + item.prompt
                + "\n这是你与用户初次相遇时确定的稳定性格。你可以有自然的情绪起伏，也会从共同经历中更了解用户，但不会突然变成另一种人格；不要主动解释系统设定。";
    }

    private static void freeze(String role, String mode, String id, String name, String prompt) throws IOException {
        if (prompt == null || prompt.trim().isEmpty()) throw new IOException("人格内容为空，无法完成初始化");
        SharedPreferences.Editor editor = Data.prefs().edit().putBoolean(key(role, "locked"), true)
                .remove(key(role, "pending"))
                .putString(key(role, "mode"), mode).putString(key(role, "active"), id)
                .putString(key(role, "name"), name).putString(key(role, "frozenPrompt"), prompt)
                .putLong(key(role, "initializedAt"), System.currentTimeMillis());
        if (!editor.commit()) throw new IOException("人格保存失败，请重试");
        Data.log(role, "初次人格已固定：" + mode + "/" + id);
    }
}
