package com.xiaoxiao.pet;
import org.json.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** Offline check of the local boundary against fixed real-model outputs. Never calls a model. */
public final class CommitmentsReplyReplay {
 public static void main(String[] args)throws Exception {
  Path rows=Paths.get(args[0],"rows");boolean identical=args.length<2;Path output=identical?null:Paths.get(args[1]);
  Set<String> freshCases=args.length>2?new HashSet<>(Arrays.asList(args[2].split(","))):Collections.emptySet();int skipped=0;
  if(output!=null){if(Files.exists(output))throw new IllegalArgumentException("Use a new output directory");Files.createDirectories(output.resolve("rows"));}
  int checked=0,changed=0;List<Path> files=new ArrayList<>();try(java.util.stream.Stream<Path> stream=Files.list(rows)){stream.filter(p->p.toString().endsWith(".json")).sorted().forEach(files::add);}
  JSONArray changedIds=new JSONArray();
  for(Path file:files){JSONObject row=new JSONObject(new String(Files.readAllBytes(file),StandardCharsets.UTF_8));
   if(freshCases.contains(row.getString("case"))){skipped++;continue;}
   if(!identical&&"extract".equals(row.getString("kind"))&&row.has("raw"))AgentTransport.requireStructured(row.getString("raw"));
   if(!"extract".equals(row.getString("kind"))&&!row.has("error")){
    JSONObject state=AgentStore.empty(0);JSONArray offered=row.getJSONArray("provided_commitments"),ledger=new JSONArray();
    // This guard only consumes the retrieved quotes, owners and statuses. Dates merely stabilize ordering.
    for(int i=0;i<offered.length();i++)ledger.put(new JSONObject(offered.getJSONObject(i).toString()).put("at",offered.length()-i).put("timeZone","Asia/Shanghai"));
    state.put("commitments",ledger);AgentReply reply=AgentReply.parse(row.getString("raw"));
    if(!reply.reply.equals(row.getString("model_reply"))||!reply.aside.equals(row.optString("model_aside")))throw new AssertionError("Parsing changed: "+row.getString("id"));
    if(!identical)try{AgentTransport.requireStructured(row.getString("raw"));}catch(java.io.IOException invalid){
     if("chain".equals(row.getString("kind")))throw new AssertionError("Changed chain requires fresh model calls: "+row.getString("id"));
     checked++;changed++;changedIds.put(row.getString("id"));System.out.println("REJECTED isolated protocol: "+row.getString("id"));
     row.put("original_reply",row.get("reply")).put("original_aside",row.optString("aside")).put("error",invalid.getClass().getSimpleName()+": "+invalid.getMessage());
     row.put("original_judge",row.opt("judge"));row.remove("judge");row.remove("reply");row.remove("aside");row.remove("reply_guard_applied");
     if(row.has("judge_repeat")){row.put("original_judge_repeat",row.get("judge_repeat"));row.remove("judge_repeat");}
     Files.write(output.resolve("rows").resolve(file.getFileName()),row.toString(2).getBytes(StandardCharsets.UTF_8));continue;
    }
    boolean applied=Commitments.replyBoundary(state,row.getString("user"),false,true).enforce(reply);checked++;
    boolean different=!reply.reply.equals(row.getString("reply"))||!reply.aside.equals(row.optString("aside"))||applied!=row.optBoolean("reply_guard_applied");
    if(different){if(identical||"chain".equals(row.getString("kind")))throw new AssertionError("Cannot reuse model history: "+row.getString("id"));
     changed++;changedIds.put(row.getString("id"));System.out.println("CHANGED isolated reply: "+row.getString("id"));
     row.put("original_judge",row.opt("judge"));row.remove("judge");if(row.has("judge_repeat")){row.put("original_judge_repeat",row.get("judge_repeat"));row.remove("judge_repeat");}
     row.put("needs_regrade",true);
    }
    row.put("reply",reply.reply).put("aside",reply.aside).put("reply_guard_applied",applied);
   }
   if(output!=null)Files.write(output.resolve("rows").resolve(file.getFileName()),row.toString(2).getBytes(StandardCharsets.UTF_8));
  }
  if(output!=null){JSONObject manifest=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0],"manifest.json")),StandardCharsets.UTF_8));
   manifest.put("mode","offline replay of fixed model outputs; no new product or judge requests").put("model_call_product_commit",manifest.getString("frozen_product_commit"))
    .put("reply_processing_product_commit",System.getenv("DAFEIYU_EVAL_PRODUCT_COMMIT")).put("replay_new_product_calls",0).put("replay_new_judge_calls",0).put("replay_changed_ids",changedIds)
    .put("chain_outputs_identical",freshCases.isEmpty()).put("replayed_chain_outputs_identical",true).put("requires_fresh_cases",new JSONArray(freshCases))
    .put("replayed_rows",files.size()-skipped).put("finished_rows",files.size()-skipped).put("finished",freshCases.isEmpty()).put("replay_source",args[0]);
   Files.write(output.resolve("manifest.json"),manifest.toString(2).getBytes(StandardCharsets.UTF_8));}
  System.out.println("REPLAY checked="+checked+" changed="+changed+" skipped="+skipped+"; retained multi-turn outputs identical; no network.");
 }
}
