package com.xiaoxiao.pet;

import org.json.*;
import java.net.*;
import java.io.*;
import java.math.BigDecimal;

/** Official read-only account endpoint; never sends a third-party provider's key to DeepSeek. */
final class DeepSeekBalance {
    static final String ENDPOINT="https://api.deepseek.com/user/balance";
    interface Client {Result read(ModelSettings.Config config,AgentTransport.Token token)throws Exception;}
    static Client client=DeepSeekBalance::request;
    static boolean supported(ModelSettings.Config config){return "https://api.deepseek.com:443".equals(config.address.origin)&&!config.keyHeader;}
    static final class Result {
        final boolean available;final String summary;
        Result(boolean available,String summary){this.available=available;this.summary=summary;}
    }
    static Result parse(JSONObject data)throws Exception {
        if(!(data.opt("is_available") instanceof Boolean))throw new IOException("余额响应格式不正确");
        JSONArray rows=data.getJSONArray("balance_infos");if(rows.length()==0||rows.length()>2)throw new IOException("服务未返回余额明细");
        StringBuilder text=new StringBuilder();java.util.Set<String> currencies=new java.util.HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String currency=row.getString("currency");
            if(!("CNY".equals(currency)||"USD".equals(currency))||!currencies.add(currency))throw new IOException("不支持的余额币种");
            if(i>0)text.append("\n");text.append("CNY".equals(currency)?"人民币 ¥":"美元 $").append(amount(row,"total_balance"));
            text.append("（充值 ").append(amount(row,"topped_up_balance")).append("，赠金 ").append(amount(row,"granted_balance")).append("）");
        }
        boolean available=data.getBoolean("is_available");if(!available)text.append("\n账户暂无可用于 API 调用的余额，请充值后重试。");
        return new Result(available,text.toString());
    }
    static String amount(JSONObject row,String name)throws Exception {
        Object raw=row.get(name);if(!(raw instanceof String)||!((String)raw).matches("-?[0-9]{1,12}(\\.[0-9]{1,8})?"))throw new IOException("余额金额格式不正确");
        return new BigDecimal((String)raw).toPlainString();
    }
    static Result request(ModelSettings.Config config,AgentTransport.Token token)throws Exception {
        if(!supported(config))throw new IOException("当前服务商暂不支持余额查询");token.check();
        HttpURLConnection con=(HttpURLConnection)new URL(ENDPOINT).openConnection();token.connection=con;
        try{
            token.check();con.setInstanceFollowRedirects(false);con.setConnectTimeout(8000);con.setReadTimeout(8000);con.setRequestMethod("GET");
            con.setRequestProperty("Authorization","Bearer "+config.secret);con.setRequestProperty("Accept","application/json");
            int code=con.getResponseCode();if(code!=200)throw new IOException("余额查询 HTTP "+code+"。"+ModelPresets.httpError(code));
            String body=AgentTransport.boundedRead(con.getInputStream(),32768);token.check();return parse(new JSONObject(body));
        }finally{token.connection=null;con.disconnect();}
    }
    static void cache(ModelSettings.Config config,Result result){
        try{ModelSettings.Config saved=ModelSettings.read();if(supported(config)&&saved.address.origin.equals(config.address.origin)&&saved.secret.equals(config.secret))
            Data.prefs().edit().putString("api.balance.summary",result.summary).putLong("api.balance.at",System.currentTimeMillis()).apply();
        }catch(Exception ignored){}
    }
    static String cached(){
        try{if(!supported(ModelSettings.read()))return "余额尚未查询";String text=Data.prefs().getString("api.balance.summary","");long at=Data.prefs().getLong("api.balance.at",0);
            if(!text.isEmpty()&&at>0)return "上次查询："+new java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(new java.util.Date(at))+"\n"+text;
        }catch(Exception ignored){}return "余额尚未查询";
    }
}
