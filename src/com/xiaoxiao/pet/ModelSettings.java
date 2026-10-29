package com.xiaoxiao.pet;
import java.util.*;import java.io.IOException;

/** One preferences snapshot per request; one commit for endpoint/model/encrypted credential. */
final class ModelSettings {
    static final class Config {
        final ApiEndpoint address;final String model,secret;final boolean keyHeader,stream;
        Config(String base,String model,String secret,boolean header,boolean stream)throws Exception{
            address=new ApiEndpoint(base);ApiEndpoint.validateModel(model);ApiEndpoint.validateSecret(secret);
            this.model=model.trim();this.secret=secret;keyHeader=header;this.stream=stream;
        }
    }
    static String value(Map<String,?> data,String name){Object v=data.get(name);return v instanceof String?(String)v:"";}
    static boolean flag(Map<String,?> data,String name,boolean fallback){Object v=data.get(name);return v instanceof Boolean?(Boolean)v:fallback;}
    static boolean complete(){
        Map<String,?> data=Data.prefs().getAll();try{new ApiEndpoint(value(data,"api.url"));ApiEndpoint.validateModel(value(data,"api.model"));return !value(data,"api.secret").isEmpty();}catch(Exception e){return false;}
    }
    static Config read()throws Exception {
        Map<String,?> data=Data.prefs().getAll();String base=value(data,"api.url");ApiEndpoint address=new ApiEndpoint(base);
        String origin=value(data,"api.secret.origin");if(!origin.isEmpty()&&!address.origin.equals(origin))throw new IOException("接口与已保存的 Key 不匹配，请重新配置");
        return new Config(base,value(data,"api.model"),Chat.decrypt(value(data,"api.secret")),flag(data,"api.header",false),flag(data,"agent.stream",true));
    }
    static String selectedSecret(String base,String entered)throws Exception {
        ApiEndpoint target=new ApiEndpoint(base);String text=entered==null?"":entered.trim();if(!text.isEmpty()){ApiEndpoint.validateSecret(text);return text;}
        Map<String,?> previous=Data.prefs().getAll();String origin=value(previous,"api.secret.origin");
        if(origin.isEmpty()&&!value(previous,"api.url").isEmpty())origin=new ApiEndpoint(value(previous,"api.url")).origin;
        if(origin.isEmpty()||!origin.equals(target.origin))throw new IOException("更换接口服务商时请重新填写 Key");
        String secret=Chat.decrypt(value(previous,"api.secret"));ApiEndpoint.validateSecret(secret);return secret;
    }
    static synchronized void save(String base,String model,String entered,boolean header)throws Exception {
        ApiEndpoint address=new ApiEndpoint(base);ApiEndpoint.validateModel(model);String secret=selectedSecret(address.base,entered);
        String encrypted=Chat.encrypt(secret);AgentRuntime.cancel();
        if(!Data.prefs().edit().putString("api.url",address.base).putString("api.model",model.trim()).putBoolean("api.header",header)
            .putString("api.secret",encrypted).putString("api.secret.origin",address.origin).remove("api.balance.summary").remove("api.balance.at").commit())throw new IOException("模型设置保存失败，请重试");
    }
    static synchronized void clear()throws IOException {AgentRuntime.cancel();if(!Data.prefs().edit().remove("api.secret").remove("api.secret.origin").remove("api.balance.summary").remove("api.balance.at").commit())throw new IOException("清除失败，请重试");}
}
