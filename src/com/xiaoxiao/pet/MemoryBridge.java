package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Protocol 1 client for agent-anthropomorphic-memory; no DSH dependency. */
final class MemoryBridge {
    static final class Address {
        final String base,origin;
        Address(String input)throws Exception {
            URI u=new URI(input.trim());String host=u.getHost(),scheme=u.getScheme();
            boolean local="http".equalsIgnoreCase(scheme)&&("127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host));
            if(host==null||(!local&&!"https".equalsIgnoreCase(scheme))||u.getUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new IOException("请填写 HTTPS 服务地址；USB 联调可用 http://127.0.0.1:8787");
            base=u.toString().replaceAll("/+$","");origin=scheme.toLowerCase(Locale.ROOT)+"://"+host.toLowerCase(Locale.ROOT)+":"+(u.getPort()<0?(local?80:443):u.getPort());
        }
    }
    static final class Config {
        final boolean enabled,semantic,judge;final Address address;final String secret;
        Config(boolean enabled,Address address,String secret,boolean semantic,boolean judge){this.enabled=enabled;this.address=address;this.secret=secret;this.semantic=semantic;this.judge=judge;}
    }
    static Config read()throws Exception {
        Map<String,?> p=Data.prefs().getAll();boolean enabled=ModelSettings.flag(p,"memory.service.enabled",false);
        if(!enabled)return new Config(false,null,"",false,false);
        Address address=new Address(ModelSettings.value(p,"memory.service.url"));
        if(!address.origin.equals(ModelSettings.value(p,"memory.service.origin")))throw new IOException("记忆服务地址与凭据不匹配，请重新保存");
        String secret=Chat.decrypt(ModelSettings.value(p,"memory.service.secret"));ApiEndpoint.validateSecret(secret);
        return new Config(true,address,secret,ModelSettings.flag(p,"memory.service.semantic",true),ModelSettings.flag(p,"memory.service.judge",true));
    }
    static void save(boolean enabled,String url,String entered,boolean semantic,boolean judge)throws Exception {
        if(!enabled){AgentRuntime.cancel();if(!Data.prefs().edit().putBoolean("memory.service.enabled",false).commit())throw new IOException("保存失败");return;}
        Address address=new Address(url);String secret=entered.trim();
        if(secret.isEmpty()){
            if(!address.origin.equals(Data.prefs().getString("memory.service.origin","")))throw new IOException("更换记忆服务时请重新填写访问令牌");
            secret=Chat.decrypt(Data.prefs().getString("memory.service.secret",""));
        }
        ApiEndpoint.validateSecret(secret);if(secret.length()<32||secret.matches("(?s).*\\s.*"))throw new IOException("访问令牌至少 32 位，不能含空白");
        String encrypted=Chat.encrypt(secret);AgentRuntime.cancel();
        if(!Data.prefs().edit().putBoolean("memory.service.enabled",true).putString("memory.service.url",address.base).putString("memory.service.origin",address.origin).putString("memory.service.secret",encrypted).putBoolean("memory.service.semantic",semantic).putBoolean("memory.service.judge",judge).commit())throw new IOException("保存失败");
    }
    interface Client {JSONObject call(Config config,String path,JSONObject body,AgentTransport.Token token)throws Exception;}
    static Client client=MemoryBridge::http;
    static volatile String lastStatus="手机本地记忆";
    static JSONObject http(Config config,String path,JSONObject payload,AgentTransport.Token token)throws Exception {
        token.check();HttpURLConnection con=(HttpURLConnection)new URL(config.address.base+path).openConnection();token.connection=con;
        try {
            token.check();con.setInstanceFollowRedirects(false);con.setConnectTimeout(payload!=null&&recallOnly(payload)?3000:5000);con.setReadTimeout(payload==null?10000:recallOnly(payload)?recallTimeout(payload):90000);
            con.setRequestProperty("Authorization","Bearer "+config.secret);con.setRequestProperty("Accept","application/json");
            if(payload!=null){byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);if(data.length>3*1024*1024)throw new IOException("记忆请求超过容量限制");con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");con.setFixedLengthStreamingMode(data.length);try(OutputStream out=con.getOutputStream()){out.write(data);}}
            int code=con.getResponseCode();token.check();if(code!=200)throw new IOException("记忆服务 HTTP "+code);
            JSONObject result=new JSONObject(AgentTransport.boundedRead(con.getInputStream(),3*1024*1024));token.check();return result;
        } finally {token.connection=null;con.disconnect();}
    }
    // The lookup before each reply blocks the chat, so it gets seconds, not the 90 s a write may need for an
    // LLM judgement; on timeout evaluate() answers from the phone's own memory. The first semantic lookup
    // also builds the vector cache, so it may take longer once.
    static final int RECALL_TIMEOUT=8000,WARMUP_TIMEOUT=45000;
    static boolean recallOnly(JSONObject payload){JSONArray incoming=payload.optJSONArray("incoming");return incoming!=null&&incoming.length()==0;}
    static int recallTimeout(JSONObject payload){
        JSONObject state=payload.optJSONObject("state");JSONObject cache=state==null?null:state.optJSONObject("vector_cache");JSONArray rows=state==null?null:state.optJSONArray("memories");
        return payload.optBoolean("semantic")&&rows!=null&&rows.length()>0&&(cache==null||cache.length()==0)?WARMUP_TIMEOUT:RECALL_TIMEOUT;
    }
    static final class Result {
        final JSONObject state;final JSONArray hits;final boolean remote;final String status;final JSONArray rejected;
        Result(JSONObject state,JSONArray hits,boolean remote,String status,JSONArray rejected){this.state=state;this.hits=hits;this.remote=remote;this.status=status;this.rejected=rejected;}
    }
    static Result evaluate(Config config,JSONObject original,JSONArray incoming,String query,long now,AgentTransport.Token token)throws Exception {
        JSONObject state=MemoryLedger.copy(original);MemoryLedger.expire(state,now);token.check();
        if(config.enabled)try {
            JSONObject wireState=new JSONObject();for(String k:new String[]{"memories","archive","audit","vector_cache"})wireState.put(k,state.get(k));
            JSONObject body=new JSONObject().put("protocol",1).put("now",now).put("utc_offset_minutes",TimeZone.getDefault().getOffset(now)/60000).put("query",query).put("state",wireState).put("incoming",incoming).put("semantic",config.semantic).put("judge",config.judge);
            JSONObject response=client.call(config,"/v1/evaluate",body,token);token.check();validate(response,state,incoming);
            JSONObject result=response.getJSONObject("state");for(String k:new String[]{"memories","archive","audit","vector_cache"})state.put(k,result.get(k));
            JSONObject features=response.optJSONObject("features");String status="完整记忆引擎已连接";
            if(features!=null)status+=" · 语义检索"+("none".equals(features.optString("semantic","none"))?"未启用":"已配置")+" · 智能写入"+(features.optBoolean("judge")?"已配置":"未启用");
            if(response.optBoolean("degraded"))status+=" · 部分组件失败，已用规则补充";
            lastStatus=status;return new Result(state,response.getJSONArray("hits"),true,status,response.optJSONArray("rejected"));
        }catch(Exception failure){token.check();lastStatus="记忆服务暂不可用，本轮使用手机本地记忆";}
        for(int i=0;i<incoming.length();i++)MemoryLedger.addLocal(state,incoming.getJSONObject(i));
        String status=config.enabled?lastStatus:"手机本地记忆";lastStatus=status;return new Result(state,null,false,status,new JSONArray());
    }
    static void validate(JSONObject response,JSONObject previous,JSONArray incoming)throws Exception {
        if(response.optInt("protocol")!=1)throw new IOException("不兼容的记忆服务协议");
        JSONObject state=response.getJSONObject("state");Map<String,JSONObject> known=new HashMap<>();Set<String> knownSources=new HashSet<>();
        for(JSONArray rows:new JSONArray[]{previous.getJSONArray("memories"),previous.getJSONArray("archive"),incoming})for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);known.put(r.getString("id"),r);JSONArray sources=MemoryLedger.sources(r);for(int j=0;j<sources.length();j++)knownSources.add(sources.optString(j));}
        Set<String> returned=new HashSet<>(),active=new HashSet<>();
        for(String pool:new String[]{"memories","archive"}){JSONArray rows=state.getJSONArray(pool);if(rows.length()>(pool.equals("memories")?120:240))throw new IOException("服务返回过多记忆");for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);String id=r.getString("id");if(!known.containsKey(id)||!returned.add(id))throw new IOException("服务返回未知记忆");if(pool.equals("memories"))active.add(id);
            boolean evidence=false;for(JSONObject k:known.values())if(k.optString("text").equals(r.optString("text"))&&k.optString("source").equals(r.optString("source"))&&k.optLong("at")==r.optLong("at")){evidence=true;break;}
            if(!evidence)throw new IOException("服务返回的记忆没有用户原话依据");
            JSONArray sources=MemoryLedger.sources(r);if(sources.length()>200)throw new IOException("来源过多");for(int j=0;j<sources.length();j++)if(!knownSources.contains(sources.optString(j)))throw new IOException("来源不匹配");
            JSONObject e=r.optJSONObject("engine");if(e!=null){String content=e.optString("content",r.optString("text"));if(content.length()>1500||!content.replaceAll("（\\d{4}[^）]{0,45}）","").equals(r.optString("text").replaceAll("（\\d{4}[^）]{0,45}）","")))throw new IOException("服务改写了原话");String by=e.optString("superseded_by",e.optString("downgrade_reason"));if(!by.isEmpty()&&!known.containsKey(by))throw new IOException("更正指向未知记忆");}
        }}
        JSONArray existing=previous.getJSONArray("memories");for(int i=0;i<existing.length();i++)if(!returned.contains(existing.getJSONObject(i).getString("id")))throw new IOException("服务遗漏已有记忆");
        JSONArray hits=response.getJSONArray("hits");if(hits.length()>10)throw new IOException("检索结果过多");for(int i=0;i<hits.length();i++)if(!active.contains(hits.getString(i)))throw new IOException("检索引用失效记忆");
        if(state.getJSONArray("audit").length()>512||state.getJSONObject("vector_cache").toString().length()>1800000)throw new IOException("记忆索引超过限制");
    }
}
