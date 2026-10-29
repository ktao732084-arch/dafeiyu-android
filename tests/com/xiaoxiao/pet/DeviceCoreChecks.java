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
        run("API addresses",()->ApiEndpointTest.main(new String[0]));
        run("actions",()->ActionPlanTest.main(new String[0]));
        run("history and segments",()->ReplyHistoryTest.main(new String[0]));
        run("native memory replay",()->NativeReplayTest.main(new String[]{root+"/memory-service"}));
        run("dated recall",()->HistoryRecallTest.main(new String[0]));
        run("episodic index",()->EpisodeRecallTest.main(new String[]{root+"/episodes"}));
        run("rule capture",()->MemoryCaptureTest.main(new String[]{root+"/memory-capture",root+"/memory-service"}));
        run("diary",()->DiaryTest.main(new String[]{root+"/diary"}));
        run("Unicode regex",NativeRegexTest::run);
        System.out.println("DEVICE SUITES: "+passed+" passed, "+failures+" failed");
        System.exit(failures==0?0:1);
    }
}
