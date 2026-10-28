package com.xiaoxiao.pet;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Executed only by Android/Robolectric tests; never touches production preferences or files. */
final class PersonaChecks {
    private int checks;
    private void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError("persona: " + description);
    }
    interface Operation { void run() throws Exception; }
    private void rejects(Operation operation, String description) throws Exception {
        boolean rejected = false;
        try { operation.run(); } catch (IOException expected) { rejected = true; }
        check(rejected, description);
    }
    static int run(Context context) throws Exception {
        return new PersonaChecks().exercise(context);
    }

    private static final class IsolatedContext extends ContextWrapper {
        final File directory;
        final String namespace;
        IsolatedContext(Context base) throws IOException {
            super(base);namespace = "persona-test-" + UUID.randomUUID();
            directory = new File(base.getCacheDir(), namespace);
            if (!directory.mkdirs()) throw new IOException("Cannot create isolated test directory");
        }
        @Override public File getFilesDir() { return directory; }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String ignored, int mode) {
            return getBaseContext().getSharedPreferences(namespace, mode);
        }
    }

    private IsolatedContext fresh(Context base) throws IOException {
        IsolatedContext context = new IsolatedContext(base);Data.app = context;return context;
    }
    private void write(File file, String text) throws IOException {
        File parent = file.getParentFile();if (!parent.exists() && !parent.mkdirs()) throw new IOException("mkdir failed");
        try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
    private int exercise(Context base) throws Exception {
        Context original = Data.app;
        try {
            fresh(base);
            check(PersonaManager.presets().size() == 5, "five real presets, random is not a sixth persona");
            check(!PersonaManager.ensureInitialized("main"), "fresh installation requires a choice");
            check(Data.prefs().getBoolean(PersonaManager.key("main", "pending"), false), "fresh status survives process recreation");
            boolean noPrompt = false;
            try { PersonaManager.prompt("main"); } catch (IllegalStateException expected) { noPrompt = true; }
            check(noPrompt, "uninitialized runtime prompt is rejected");
            rejects(() -> PersonaManager.initialize("main", "missing-style"), "invalid initial preset rejected");
            check(!PersonaManager.isInitialized("main"), "invalid choice cannot lock empty personality");
            PersonaManager.initialize("main", "gentle");
            String frozen = PersonaManager.prompt("main");
            check(PersonaManager.isInitialized("main"), "manual choice persisted");
            check("gentle".equals(PersonaManager.activeId("main")) && frozen.contains("温柔陪伴"), "selected style actually injected");
            rejects(() -> PersonaManager.initialize("main", "classic"), "second manual initialization blocked");
            rejects(() -> PersonaManager.initializeRandom("main"), "random cannot change a fixed companion");
            Data.prefs().edit().putString("profile.main.prompt", "a later profile edit").commit();
            check(frozen.equals(PersonaManager.prompt("main")), "legacy profile field cannot alter frozen prompt");
            AgentStore local = new AgentStore(new File(Data.app.getFilesDir(), "agent"));
            local.remember("我喜欢蓝色");local.clear(true);
            check(PersonaManager.ensureInitialized("main") && frozen.equals(PersonaManager.prompt("main")), "clearing all memories preserves personality");

            IsolatedContext random = fresh(base);
            PersonaManager.initializeRandom("main");
            String randomId = PersonaManager.activeId("main"), randomPrompt = PersonaManager.prompt("main");
            check(PersonaManager.find(randomId) != null && PersonaManager.RANDOM.equals(PersonaManager.mode("main")), "random initializes a valid preset");
            // Obtain preferences again, as another activity/process would, rather than a cached selected object.
            Data.app = new ContextWrapper(random) {
                @Override public File getFilesDir() { return random.directory; }
            };
            for (int i = 0; i < 8; i++) check(randomId.equals(PersonaManager.activeId("main")) && randomPrompt.equals(PersonaManager.prompt("main")), "random remains fixed on subsequent reads " + i);
            rejects(() -> PersonaManager.initializeRandom("main"), "reopening cannot reroll random");

            fresh(base);
            String oldCustom = "旧版自定义鲸鱼娘：安静但很可靠。";
            Data.prefs().edit().putString("profile.main.prompt", oldCustom).commit();
            check(PersonaManager.ensureInitialized("main"), "existing custom persona migrates");
            check(PersonaManager.CUSTOM.equals(PersonaManager.mode("main")) && oldCustom.equals(PersonaManager.prompt("main")), "old custom text frozen exactly");
            Data.prefs().edit().remove("profile.main.prompt").commit();
            check(oldCustom.equals(PersonaManager.prompt("main")), "migration does not depend on legacy mutable key");

            fresh(base);
            String oldDefault = Data.persona("main");
            new AgentStore(new File(Data.app.getFilesDir(), "agent")).remember("我喜欢下雨天");
            check(PersonaManager.ensureInitialized("main") && PersonaManager.LEGACY.equals(PersonaManager.mode("main")), "old state migrates original default");
            check(oldDefault.equals(PersonaManager.prompt("main")), "old default is not silently rewritten to new preset");

            fresh(base);
            check(!PersonaManager.ensureInitialized("main"), "new install marked pending before optional notes");
            new AgentStore(new File(Data.app.getFilesDir(), "agent")).remember("初始化前写的手账");
            check(!PersonaManager.ensureInitialized("main"), "new-version memory cannot be mistaken for old user state");
            PersonaManager.initialize("main", "quirky");
            check("quirky".equals(PersonaManager.activeId("main")), "pending user still gets their chosen personality");

            fresh(base);
            write(new File(Data.app.getFilesDir(), "agent/companion-v1.json"), "broken old state");
            rejects(() -> PersonaManager.ensureInitialized("main"), "unreadable old state cannot masquerade as new install");
            check(!PersonaManager.isInitialized("main") && !Data.prefs().getBoolean(PersonaManager.key("main", "pending"), false), "failed migration neither locks nor resets identity");

            fresh(base);
            Data.prefs().edit().putBoolean(PersonaManager.key("main", "locked"), true).commit();
            rejects(() -> PersonaManager.ensureInitialized("main"), "missing frozen text errors instead of recreating personality");
            return checks;
        } finally {
            // The app's logger resolves Data.app asynchronously; drain it while still isolated.
            try { Data.logWriter.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS); }
            finally { Data.app = original; }
        }
    }
}
