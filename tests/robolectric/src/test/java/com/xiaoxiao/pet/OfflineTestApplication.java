package com.xiaoxiao.pet;

/** Release APKs contain a live channel; framework tests must opt into fake update I/O. */
public class OfflineTestApplication extends android.app.Application {
    @Override public void onCreate(){super.onCreate();UpdateRuntime.channels=context->"";
        // Robolectric creates a new Application for each test; the growth singleton must follow it.
        try{java.lang.reflect.Field field=GrowthStore.class.getDeclaredField("instance");field.setAccessible(true);GrowthStore previous=(GrowthStore)field.get(null);if(previous!=null)previous.flush();field.set(null,null);GrowthStore.changed=null;AgentStore.deleted=null;}
        catch(Exception e){throw new IllegalStateException("Cannot isolate growth test state",e);}
    }
}
