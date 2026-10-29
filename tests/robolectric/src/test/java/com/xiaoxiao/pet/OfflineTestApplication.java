package com.xiaoxiao.pet;

/** Release APKs contain a live channel; framework tests must opt into fake update I/O. */
public class OfflineTestApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();UpdateRuntime.channels=context->"";}
}
