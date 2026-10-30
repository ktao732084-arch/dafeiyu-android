package com.xiaoxiao.pet;

import java.io.File;

/** Exercises the existing core suites against Android's real Java/JSON implementations. */
public final class DeviceCoreChecks {
    interface Suite {void run()throws Exception;}
    static int failures,passed;
    static void run(String name,Suite suite){
        System.out.println("RUN "+name);
        try{suite.run();passed++;}catch(Throwable e){failures++;System.out.println("FAIL "+name);e.printStackTrace(System.out);}
    }
    public static void main(String[] args){
        String root=args[0];System.setProperty("java.io.tmpdir",root);
        System.out.println("Android core audit: "+System.getProperty("java.vm.name")+"; SDK "+android.os.Build.VERSION.SDK_INT);
        run("core",()->AgentCoreTest.main(new String[]{root+"/core"}));
        run("physics",()->MotionEngineTest.main(new String[0]));
        run("suspension physics",()->SuspensionMotionTest.main(new String[0]));
        run("API addresses",()->ApiEndpointTest.main(new String[0]));
        run("actions",()->ActionPlanTest.main(new String[0]));
        run("history and segments",()->ReplyHistoryTest.main(new String[0]));
        run("native memory replay",()->NativeReplayTest.main(new String[]{root+"/memory-service"}));
        run("dated recall",()->HistoryRecallTest.main(new String[0]));
        run("episodic index",()->EpisodeRecallTest.main(new String[]{root+"/episodes"}));
        run("rule capture",()->MemoryCaptureTest.main(new String[]{root+"/memory-capture",root+"/memory-service"}));
        run("corrections and reply integrity",()->CorrectionIntegrityTest.main(new String[0]));
        run("rewrite fidelity",()->RewriteGuardTest.main(new String[0]));
        run("diary",()->DiaryTest.main(new String[]{root+"/diary"}));
        run("bilateral commitments",()->CommitmentsTest.main(new String[]{root+"/commitments"}));
        run("Unicode regex",NativeRegexTest::run);
        run("nickname and reply polish",()->ChatPolishRulesTest.main(new String[0]));
        run("model initiative",()->InitiativeTest.main(new String[0]));
        run("chat search and notebook filter",()->UsabilityTest.main(new String[]{root+"/usability"}));
        run("gomoku rules, persistence and context",()->GomokuTest.main(new String[0]));
        run("growth main line",()->GrowthTest.main(new String[]{root,root+"/growth"}));

        run("updates",()->UpdateCoreTest.main(new String[]{root+"/updates"}));
        run("character and mood",()->CharacterTest.main(new String[]{root+"/personas.json",root+"/character"}));
        run("situation modules",()->SituationsTest.main(new String[]{root+"/personas.json"}));
        run("aside, her day, feeling and the jar",()->AliveTest.main(new String[]{root+"/alive"}));
        run("character state",()->CharacterStateTest.main(new String[]{root+"/character-state"}));
        run("touch reactions",()->TouchReplyTest.main(new String[0]));
        System.out.println("DEVICE SUITES: "+passed+" passed, "+failures+" failed");
        System.exit(failures==0?0:1);
    }
}
