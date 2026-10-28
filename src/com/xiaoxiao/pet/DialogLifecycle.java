package com.xiaoxiao.pet;
import android.app.*;import android.content.*;import java.util.*;

/** Dialogs created with Activity contexts must not outlive the window owner. */
final class DialogLifecycle {
 private static final Map<Activity,Set<Dialog>> windows=new WeakHashMap<>();
 static void track(Dialog dialog){
  Context context=dialog.getContext();Set<Context> visited=Collections.newSetFromMap(new IdentityHashMap<Context,Boolean>());
  while(context!=null&&visited.add(context)){
   if(context instanceof Activity){Activity owner=(Activity)context;Set<Dialog> set=windows.get(owner);if(set==null){set=Collections.newSetFromMap(new WeakHashMap<Dialog,Boolean>());windows.put(owner,set);}set.add(dialog);dialog.setOwnerActivity(owner);return;}
   if(context instanceof ContextWrapper)context=((ContextWrapper)context).getBaseContext();else return;
  }
 }
 static void close(Activity owner){Set<Dialog> set=windows.remove(owner);if(set!=null)for(Dialog dialog:new ArrayList<>(set))if(dialog!=null)dialog.dismiss();}
}
