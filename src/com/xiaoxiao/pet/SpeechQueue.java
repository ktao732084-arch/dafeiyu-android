package com.xiaoxiao.pet;
import java.util.*;

/** A cancellable presentation queue; no model calls and no sleeping on the UI thread. */
final class SpeechQueue {
    interface Clock {void later(Runnable task,long delay);void remove(Runnable task);}
    interface Sink {boolean visible();void show(String text,int shown,boolean preview);void stop(int shown);}
    final Clock clock;final Sink sink;Runnable task;List<String> parts;int shown;boolean preview,active;long generation;
    SpeechQueue(Clock clock,Sink sink){this.clock=clock;this.sink=sink;}
    void start(String reply){cancel();parts=ReplySegments.split(reply);preview=ReplySegments.previewOnly(reply);shown=0;active=true;long ticket=++generation;step(ticket);}
    void step(long ticket){
        if(!active||ticket!=generation)return;if(!sink.visible()){cancel();return;}
        if(preview){sink.show("我写了一段详细回复，点这里看全文。",0,true);finishLater(ticket,10000);return;}
        shown++;StringBuilder text=new StringBuilder();for(int i=Math.max(0,shown-2);i<shown;i++){if(text.length()>0)text.append("\n\n");text.append(parts.get(i).trim());}
        sink.show(text.toString(),shown,false);
        long hold=ReplySegments.holdMillis(parts.get(shown-1));
        if(shown==parts.size())finishLater(ticket,hold+2000);
        else {task=()->step(ticket);clock.later(task,hold+700);}
    }
    void finishLater(long ticket,long delay){task=()->{if(active&&ticket==generation)cancel();};clock.later(task,delay);}
    void cancel(){generation++;if(task!=null)clock.remove(task);task=null;if(active){active=false;sink.stop(shown);}}
}
