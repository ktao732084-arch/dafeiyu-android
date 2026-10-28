package com.xiaoxiao.pet;

import java.net.*;
import java.io.IOException;
import java.util.Locale;

/** Shared validation for saved configuration, connection probes and runtime requests. */
final class ApiEndpoint {
    final String base,endpoint,origin,host;
    ApiEndpoint(String configured)throws IOException {
        try {
            String input=configured==null?"":configured.trim();URI uri=new URI(input);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isEmpty())throw new IOException("请填写完整的 HTTPS 接口地址");
            if(uri.getUserInfo()!=null)throw new IOException("接口地址不能包含账号密码，请单独填写 API Key");
            if(uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new IOException("接口地址不能包含查询参数或 # 片段，请填写基础地址或完整 /chat/completions 地址");
            if(uri.getPort()>65535||uri.getPort()==0)throw new IOException("接口端口无效");
            host=uri.getHost().toLowerCase(Locale.ROOT);
            int port=uri.getPort()<0?443:uri.getPort();origin="https://"+host+":"+port;
            String path=uri.getRawPath()==null?"":uri.getRawPath().replaceAll("/+$","");
            String authority=uri.getRawAuthority();base="https://"+authority+path;
            endpoint=base+(path.endsWith("/chat/completions")?"":"/chat/completions");
        }catch(URISyntaxException e){throw new IOException("接口地址格式不正确",e);}
    }
    static void validateModel(String model)throws IOException{if(model==null||model.trim().isEmpty()||model.length()>200||model.matches("(?s).*[\\p{Cntrl}].*"))throw new IOException("请填写有效的模型名称");}
    static void validateSecret(String secret)throws IOException{if(secret==null||secret.isEmpty()||secret.length()>8192||secret.matches("(?s).*[\\p{Cntrl}].*"))throw new IOException("请填写有效的 API Key");}
}
