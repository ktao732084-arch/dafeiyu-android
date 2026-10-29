package com.xiaoxiao.pet;

import org.json.JSONObject;

/** Official DeepSeek defaults checked against api-docs.deepseek.com on 2026-10-28. */
final class ModelPresets {
    static final String DEEPSEEK_URL="https://api.deepseek.com";
    static final String DEEPSEEK_MODEL="deepseek-flash";
    static final String KEY_URL="https://platform.deepseek.com/api_keys";
    static final String BALANCE_URL="https://platform.deepseek.com/top_up";
    static boolean isRecommended(String url,String model,boolean keyHeader){
        try{return !keyHeader&&DEEPSEEK_MODEL.equals(model)&&new ApiEndpoint(DEEPSEEK_URL).endpoint.equals(new ApiEndpoint(url).endpoint);}catch(Exception e){return false;}
    }
    static void apply(String host,String model,JSONObject payload)throws Exception {
        // Flash/V4 defaults to thinking on the server. Explicitly use non-thinking for companion chat,
        // connection checks and memory calls; leave legacy reasoning models and other providers alone.
        if("api.xiaomimimo.com".equals(host)||("api.deepseek.com".equals(host)&&
            (DEEPSEEK_MODEL.equals(model)||model.startsWith("deepseek-v4-"))))
            payload.put("thinking",new JSONObject().put("type","disabled"));
    }
    /**
     * Companion replies only. DeepSeek's parameter guide suggests 1.3 for general conversation (its default is
     * 1.0, which reads flatter in character); memory, diary and connection calls keep the default. Other
     * providers use different temperature ranges, so they keep their own defaults.
     */
    static final double DEEPSEEK_CHAT_TEMPERATURE=1.3;
    static void chat(String host,String model,JSONObject payload)throws Exception {
        if("api.deepseek.com".equals(host)&&!AgentPolicy.reasoningModel(model))payload.put("temperature",DEEPSEEK_CHAT_TEMPERATURE);
    }
    static String httpError(int code){
        switch(code){
            case 401:return "API Key 无效或已失效，请重新复制完整的 Key。";
            case 402:return "API 账户余额不足，请到服务商开放平台充值后重试。";
            case 429:return "请求太频繁或服务暂时限流，请稍后重试。";
            case 500:case 503:return "模型服务暂时繁忙，请稍后重试。";
            default:return "请检查接口地址、模型名称和 API Key。";
        }
    }
}
