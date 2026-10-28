package com.xiaoxiao.pet;
import org.json.*;import java.nio.file.*;import java.util.*;

/** Replays tests/local-engine/replay.py output through LocalEngine; every write call must leave the same state. */
public final class LocalEngineReplay {
 static String diff(Object a,Object b,String path){
  if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;Set<String> keys=new TreeSet<>(x.keySet());keys.addAll(y.keySet());keys.removeAll(Arrays.asList("last_accessed","access_count"));
   for(String k:keys){if(!x.has(k)||!y.has(k))return path+"."+k+" only on "+(x.has(k)?"python":"java");String d=diff(x.get(k),y.get(k),path+"."+k);if(d!=null)return d;}return null;}
  if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return path+" length "+x.length()+" vs "+y.length();for(int i=0;i<x.length();i++){String d=diff(x.get(i),y.get(i),path+"["+i+"]");if(d!=null)return d;}return null;}
  if(a instanceof Number&&b instanceof Number)return Math.abs(((Number)a).doubleValue()-((Number)b).doubleValue())<1e-9?null:path+" "+a+" vs "+b;
  return String.valueOf(a).equals(String.valueOf(b))?null:path+" "+a+" vs "+b;
 }
 public static void main(String[] args)throws Exception{
  int calls=0,failed=0;
  for(String file:args){JSONArray cases=new JSONArray(new String(Files.readAllBytes(Paths.get(file)),"UTF-8"));
   for(int c=0;c<cases.length();c++){JSONObject cs=cases.getJSONObject(c);JSONObject state=AgentStore.empty(0);MemoryLedger.migrate(state);JSONArray cl=cs.getJSONArray("calls");
    for(int i=0;i<cl.length();i++){JSONObject call=cl.getJSONObject(i),want=call.getJSONObject("state");calls++;
     LocalEngine.Result got=LocalEngine.evaluate(state,call.getJSONArray("incoming"),call.getLong("now"),cs.getInt("offset"));
     String d=null;for(String k:new String[]{"memories","archive","audit"})if(d==null)d=diff(want.getJSONArray(k),got.state.getJSONArray(k),k);
     if(d==null)d=diff(call.getJSONArray("rejected"),got.rejected,"rejected");
     if(d!=null){failed++;System.out.println("MISMATCH "+cs.getString("case")+" offset "+cs.getInt("offset")+" call "+i+": "+d);break;}
     state=got.state;}}}
  System.out.println(calls+" write calls replayed, "+failed+" scenario(s) diverged");
  if(failed>0)System.exit(1);
 }
}
