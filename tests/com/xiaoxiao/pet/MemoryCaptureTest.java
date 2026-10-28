package com.xiaoxiao.pet;
import org.json.*;import java.io.*;import java.nio.file.*;import java.util.*;

/** Rule capture (NativeMemoryEngine.autoFacts): recall and false-memory rates must not regress. Usage: <tests/memory-capture> <memory-service> */
public final class MemoryCaptureTest {
 static int[] rates(Path file)throws Exception{int tp=0,pos=0,fp=0,neg=0;for(String l:Files.readAllLines(file)){if(l.isEmpty())continue;boolean want=l.charAt(0)=='+',got=!NativeMemoryEngine.autoFacts(l.substring(1)).isEmpty();if(want){pos++;if(got)tp++;}else{neg++;if(got)fp++;}}return new int[]{tp,pos,fp,neg};}
 public static void main(String[] args)throws Exception{
  int[] t=rates(Paths.get(args[0],"tuning.txt")),b=rates(Paths.get(args[0],"blind.txt"));
  int caught=0,total=0,privacy=0;File[] files=new File(args[1],"upstream/eval/cases").listFiles((d,n)->n.endsWith(".json"));
  for(File f:files){String category=f.getName().replace(".json","");if(category.equals("noise"))continue;JSONArray cases=new JSONArray(new String(Files.readAllBytes(f.toPath()),"UTF-8"));
   for(int i=0;i<cases.length();i++){JSONArray sessions=cases.getJSONObject(i).getJSONArray("sessions");for(int j=0;j<sessions.length();j++){JSONArray say=sessions.getJSONObject(j).optJSONArray("say");if(say!=null)for(int k=0;k<say.length();k++){boolean got=!NativeMemoryEngine.autoFacts(say.getString(k)).isEmpty();if(category.equals("privacy")){if(got)privacy++;}else{total++;if(got)caught++;}}}}}
  System.out.println("PASS: rule capture tuning "+t[0]+"/"+t[1]+" facts, "+t[2]+"/"+t[3]+" false; blind "+b[0]+"/"+b[1]+", "+b[2]+"/"+b[3]+" false; shared scenarios "+caught+"/"+total+", privacy "+privacy);
  if(t[0]<46||t[2]>1||b[0]<22||b[2]>2||caught<total-2||privacy>0)throw new AssertionError("rule capture regressed");
  // Integration: the model's quotes come first; rules fill in what it missed, at most three, no duplicates.
  JSONArray c=MemoryLedger.candidates("我叫林晓。我对花生过敏，很严重。我养了一只橘猫叫团子。今天好累",new JSONArray().put("我对花生过敏"),null,"t",1L);
  if(c.length()!=3||!c.getJSONObject(0).getString("text").equals("我对花生过敏")||c.toString().contains("好累"))throw new AssertionError("candidates: "+c);
  if(MemoryLedger.candidates("哈哈哈你真好玩",new JSONArray(),null,"t",1L).length()!=0)throw new AssertionError("chatter captured");
  for(String q:new String[]{"我喜欢什么颜色？","我喜欢什么颜色","你知道我住哪里吗","我是不是很笨呢"})if(!NativeMemoryEngine.autoFacts(q).isEmpty())throw new AssertionError("question captured: "+q);
  if(NativeMemoryEngine.autoFacts("你猜我喜欢什么？我喜欢蓝色。").size()!=1)throw new AssertionError("a fact after a question in the same message is still kept");
 }
}
