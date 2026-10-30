package com.xiaoxiao.pet;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class AgentTransport {
    interface Progress {void accept(String raw);}
    static final class Token {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        volatile Runnable completed;
        void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        void check()throws IOException{if(cancelled)throw new IOException("已停止回复");}
    }
    static String request(JSONArray messages,Token token,Progress progress)throws Exception {
        ModelSettings.Config config=ModelSettings.read();return request(config,messages,token,progress,config.stream,1200,true);
    }
    static String request(ModelSettings.Config config,JSONArray messages,Token token,Progress progress,boolean streaming,int maxTokens)throws Exception {
        return request(config,messages,token,progress,streaming,maxTokens,false);
    }
    /** chat: a companion reply, which may use the provider's conversational sampling (ModelPresets.chat). */
    static String request(ModelSettings.Config config,JSONArray messages,Token token,Progress progress,boolean streaming,int maxTokens,boolean chat)throws Exception {
        return request(config,messages,token,progress,streaming,maxTokens,chat,false);
    }
    static String precise(ModelSettings.Config config,JSONArray messages,Token token,int maxTokens)throws Exception {
        return request(config,messages,token,ignored->{},false,maxTokens,false,true);
    }
    private static String request(ModelSettings.Config config,JSONArray messages,Token token,Progress progress,boolean streaming,int maxTokens,boolean chat,boolean precise)throws Exception {
        token.check();
        String model=config.model,secret=config.secret;URL url=new URL(config.address.endpoint);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();token.connection=con;
        try {
            // Reasoning models spend output tokens and minutes on hidden thinking before the reply.
            boolean thinking=AgentPolicy.reasoningModel(model);
            token.check();con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(thinking?180000:60000);
            con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");
            if(config.keyHeader)con.setRequestProperty("api-key",secret);
            else con.setRequestProperty("Authorization","Bearer "+secret);
            boolean stream=streaming;
            JSONObject payload=new JSONObject().put("model",model).put("messages",messages).put("stream",stream).put("max_tokens",thinking?Math.max(maxTokens,8192):maxTokens);
            ModelPresets.apply(url.getHost(),model,payload);if(chat)ModelPresets.chat(url.getHost(),model,payload);
            if(precise&&"api.deepseek.com".equals(url.getHost())&&!thinking)payload.put("temperature",0);
            try(OutputStream out=con.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int code=con.getResponseCode();if(code<200||code>=300)throw new IOException("模型返回 HTTP "+code+errorDetail(con.getErrorStream(),secret)+"。"+ModelPresets.httpError(code));
            String type=con.getContentType();
            if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream")) {
                StringBuilder answer=new StringBuilder();long start=System.currentTimeMillis();long last=0;boolean ended=false;int received=0;
                try(BufferedReader in=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8))) {
                    String line;while((line=in.readLine())!=null){
                        token.check();received+=line.length();if(received>1024*1024||System.currentTimeMillis()-start>(thinking?300000:90000))throw new IOException("回复过长或超时，请重试");
                        if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.equals("[DONE]")){ended=true;break;}
                        JSONObject event;try{event=new JSONObject(data);}catch(JSONException e){continue;}
                        if(event.has("error"))throw new IOException("模型流式回复中断，请重试");
                        JSONArray choices=event.optJSONArray("choices");if(choices==null||choices.length()==0)continue;
                        JSONObject choice=choices.getJSONObject(0),delta=choice.optJSONObject("delta");
                        if(delta!=null&&!delta.isNull("content"))answer.append(delta.optString("content",""));
                        String finish=choice.optString("finish_reason","");
                        if("length".equals(finish))throw new IOException(thinking?"推理模型的思考用完了本轮输出额度，请重试或换用非推理模型":"本次回复被模型截断，请缩短问题后重试");
                        if("content_filter".equals(finish))throw new IOException("本次回复不完整，请缩短问题后重试");
                        if("stop".equals(finish))ended=true;
                        if(answer.length()>16000)throw new IOException("模型回复超过本轮长度限制");
                        long now=System.currentTimeMillis();if(now-last>100){progress.accept(answer.toString());last=now;}
                    }
                }
                token.check();if(!ended)throw new IOException("连接中断，未保存不完整回复，请重试");
                if(answer.toString().trim().isEmpty())throw new IOException("模型没有返回文本");return answer.toString();
            }
            String body=boundedRead(con.getInputStream(),1024*1024);token.check();
            JSONObject choice=new JSONObject(body).getJSONArray("choices").getJSONObject(0);
            if("length".equals(choice.optString("finish_reason"))||"content_filter".equals(choice.optString("finish_reason")))throw new IOException("模型回复不完整，请重试");
            String content=choice.getJSONObject("message").optString("content","");if(content.trim().isEmpty())throw new IOException("模型没有返回文本");return content;
        } finally {token.connection=null;con.disconnect();}
    }
    /** The provider's own error message ("model not found", "insufficient balance"), bounded and with the key masked. */
    static String errorDetail(InputStream stream,String secret) {
        if(stream==null)return "";
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while(out.size()<16384&&(n=in.read(b))!=-1)out.write(b,0,n);return AgentPolicy.providerError(out.toString("UTF-8"),secret);}
        catch(IOException e){return "";}
    }
    static String boundedRead(InputStream stream,int limit)throws IOException {
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("接口响应过大");out.write(b,0,n);}return out.toString("UTF-8");}
    }
}
