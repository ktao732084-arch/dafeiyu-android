package com.xiaoxiao.pet;

import android.app.*;import android.content.*;import android.os.*;import android.graphics.Color;import android.provider.Settings;import android.view.*;import android.widget.*;import android.text.InputType;
import org.json.*;import java.util.*;import java.util.concurrent.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;import java.security.KeyStore;import android.security.keystore.*;import android.util.Base64;

final class Chat {
 static final ExecutorService executor=Executors.newFixedThreadPool(2);
 static final Map<String,Dialog> petChats=new HashMap<>();
 static boolean isPetChatOpen(String id){Dialog d=petChats.get(id);return d!=null&&d.isShowing();}
 static void closePet(String id){Dialog d=petChats.get(id);if(d!=null)d.dismiss();}
 static final Set<String> busy=Collections.synchronizedSet(new HashSet<>());
 static synchronized javax.crypto.SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("xiaoxiao-api")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("xiaoxiao-api",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (javax.crypto.SecretKey)ks.getKey("xiaoxiao-api",null);}
 static String encrypt(String text)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.getIV(),2)+":"+Base64.encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)),2);}
 static String decrypt(String value)throws Exception{if(value.isEmpty())return "";String[] v=value.split(":");if(v.length!=2)throw new IOException("已保存的 Key 无法读取，请重新填写");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(v[0],2)));return new String(c.doFinal(Base64.decode(v[1],2)),StandardCharsets.UTF_8);}
 static void secret(String text)throws Exception{Data.prefs().edit().putString("api.secret",encrypt(text)).commit();}
 static String secret()throws Exception{return decrypt(Data.prefs().getString("api.secret",""));}
 static EditText field(Context c,LinearLayout parent,String hint,String text,boolean password){EditText e=new EditText(c);e.setText(text);e.setHint(hint);e.setTextSize(15);e.setTextColor(Apple.INK);e.setHintTextColor(Apple.SECONDARY);e.setPadding(Apple.dp(c,14),Apple.dp(c,12),Apple.dp(c,14),Apple.dp(c,12));e.setBackground(Apple.shape(c,Apple.BACK,10));e.setSingleLine(true);if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2);item.topMargin=Apple.dp(c,12);parent.addView(e,item);return e;}
 static void settings(Activity a){
  LinearLayout l=new LinearLayout(a);l.setOrientation(1);int pad=Apple.dp(a,16);l.setPadding(pad,Apple.dp(a,8),pad,Apple.dp(a,12));
  TextView help=Apple.label(a,"填写兼容 OpenAI 格式的 HTTPS 接口。消息与选中的记忆会发送给该服务商；Key 加密保存在本机。",13,false);help.setTextColor(Apple.SECONDARY);l.addView(help);
  EditText url=field(a,l,"API 地址（例如 https://…/v1）",Data.prefs().getString("api.url",""),false);
  EditText model=field(a,l,"模型名称（例如 deepseek-chat）",Data.prefs().getString("api.model",""),false);
  EditText token=field(a,l,"API Key（留空保留原值）","",true);
  Switch auth=new Switch(a);auth.setTextColor(Apple.INK);auth.setText("使用 api-key 请求头");auth.setPadding(0,Apple.dp(a,12),0,Apple.dp(a,12));
  auth.setChecked(Data.prefs().getBoolean("api.header",false));l.addView(auth);
  TextView status=Apple.label(a,"测试连接会发送一条简短消息，以实际模型回复核验接口。",12,false);status.setTextColor(Apple.SECONDARY);status.setPadding(0,Apple.dp(a,8),0,Apple.dp(a,8));
  Button test=new Button(a);test.setText("测试连接");Apple.button(test,false);l.addView(test,new LinearLayout.LayoutParams(-1,Apple.dp(a,48)));l.addView(status);
  test.setOnClickListener(v->probe(url,model,token,auth,status,test));
  ScrollView scroll=new SpringScrollView(a);scroll.addView(l);
  AlertDialog dialog=new Apple.Builder(a).setTitle("AI 接口设置").setView(scroll).setNegativeButton("取消",null)
   .setNeutralButton("清除 Key",(d,w)->{try{ModelSettings.clear();if(a instanceof MainActivity)((MainActivity)a).render();}catch(Exception e){Toast.makeText(a,e.getMessage(),0).show();}Data.log("app","AI Key 已清除");})
   .setPositiveButton("保存",null).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String u=url.getText().toString().trim();
   try{
    ModelSettings.save(u,model.getText().toString(),token.getText().toString(),auth.isChecked());
    Data.log("app","AI 设置已调整（不记录 Key）");dialog.dismiss();if(a instanceof MainActivity)((MainActivity)a).render();
   }catch(Exception e){status.setText(e.getMessage()==null?"设置保存失败，请检查填写内容。":e.getMessage());}
  });
 }
 static String selectedSecret(String base,EditText token)throws Exception{return ModelSettings.selectedSecret(base,token.getText().toString());}
 static void probe(EditText url,EditText model,EditText token,Switch auth,TextView status,Button test){
  final String base=url.getText().toString().trim(),selectedModel=model.getText().toString().trim(),apiSecret;final boolean header=auth.isChecked();
  try{
   if(base.isEmpty()||selectedModel.isEmpty())throw new IOException("请先填写 API 地址和模型名称。");
   new ApiEndpoint(base);ApiEndpoint.validateModel(selectedModel);
   apiSecret=selectedSecret(base,token);if(apiSecret.isEmpty())throw new IOException("请先填写 API Key，再测试连接。");
  }catch(Exception e){status.setText(e.getMessage()==null?"请检查接口设置。":e.getMessage());return;}
  test.setEnabled(false);status.setText("正在等待模型回复…");long start=SystemClock.elapsedRealtime();
  executor.execute(()->{
   try{
    JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content","请只回复：连接成功。"));
    String answer=request(messages,base,selectedModel,apiSecret,header,48,20000);
    double seconds=(SystemClock.elapsedRealtime()-start)/1000.0;
    String result="连接成功 · "+String.format(java.util.Locale.ROOT,"%.1f",seconds)+" 秒\n模型回复："+(answer.length()>100?answer.substring(0,100)+"…":answer);
    Data.log("app","AI 连接测试成功 durationMs="+(SystemClock.elapsedRealtime()-start));
    new Handler(Looper.getMainLooper()).post(()->status.setText(result));
   }catch(Exception e){
    Data.log("app","AI 连接测试失败："+e.getClass().getSimpleName());
    new Handler(Looper.getMainLooper()).post(()->status.setText("连接失败："+(e.getMessage()==null?"请检查网络和接口设置。":e.getMessage())));
   }finally{new Handler(Looper.getMainLooper()).post(()->test.setEnabled(true));}
  });
 }
 static void show(Context c,String role,String instance){
  try{if(!PersonaManager.ensureInitialized(role)){Toast.makeText(c,"请先回首页完成初次相遇，确定她的性格",0).show();return;}}catch(Exception e){Toast.makeText(c,e.getMessage(),1).show();return;}
  boolean overlay=!(c instanceof Activity);if(overlay&&isPetChatOpen(instance))return;
  AgentRuntime.interruptProactive();
  if(AgentRuntime.busy()){Toast.makeText(c,"她正在回复；请先在当前对话停止，或稍等一下",0).show();return;}
  Dialog dialog=overlay?CompactPanel.create(c):ClientPage.create(c);LinearLayout l=new LinearLayout(c);l.setOrientation(1);l.setPadding(Apple.dp(c,16),Apple.dp(c,12),Apple.dp(c,16),Apple.dp(c,12));
  LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=Apple.label(c,Data.name(role),20,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button close=new Button(c);close.setText("收起");Apple.button(close,false);top.addView(close,new LinearLayout.LayoutParams(Apple.dp(c,70),Apple.dp(c,46)));close.setOnClickListener(v->dialog.dismiss());l.addView(top);
  TextView text=Apple.label(c,"",16,false);text.setTextIsSelectable(true);text.setLineSpacing(Apple.dp(c,6),1);text.setPadding(Apple.dp(c,12),Apple.dp(c,12),Apple.dp(c,12),Apple.dp(c,12));ScrollView scroll=new SpringScrollView(c);scroll.addView(text);scroll.setBackground(Apple.shape(c,Apple.BACK,14));if(!overlay)l.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render(text,AgentRuntime.history());
  TextView status=Apple.label(c,"",12,false);status.setTextColor(Apple.SECONDARY);status.setPadding(0,Apple.dp(c,6),0,Apple.dp(c,6));l.addView(status);
  LinearLayout compose=new LinearLayout(c);compose.setGravity(Gravity.CENTER_VERTICAL);EditText input=new EditText(c);input.setTextColor(Apple.INK);input.setHintTextColor(Apple.SECONDARY);input.setTextSize(15);input.setHint("今天想说些什么？");input.setMaxLines(4);input.setMinHeight(Apple.dp(c,48));input.setBackground(Apple.shape(c,Apple.BACK,12));input.setPadding(Apple.dp(c,10),Apple.dp(c,8),Apple.dp(c,10),Apple.dp(c,8));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8000)});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button send=new Button(c);send.setText("发送");Apple.button(send,true);compose.addView(send,new LinearLayout.LayoutParams(Apple.dp(c,74),Apple.dp(c,48)));l.addView(compose);
  final boolean[] sending={false};final String[] draft={""};
  PetService.Pet owner=PetService.instance==null?null:PetService.instance.pets.get(instance);
  dialog.setOnDismissListener(d->{if(sending[0]){sending[0]=false;AgentRuntime.cancel();}if(petChats.get(instance)==dialog)petChats.remove(instance);if(owner!=null)owner.clearBubble();});
  dialog.setContentView(l);if(overlay){petChats.put(instance,dialog);CompactPanel.showComposer(dialog,c,owner,"大肥鱼-对话");}else{ClientPage.show(dialog,"大肥鱼-聊天");scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
  send.setOnClickListener(v->{
   if(sending[0]){AgentRuntime.cancel();return;}
   String message=input.getText().toString().trim();if(message.isEmpty())return;draft[0]=message;
   AgentRuntime.Listener listener=new AgentRuntime.Listener(){
    public void partial(String reply){if(!dialog.isShowing())return;status.setText("正在说…");if(!overlay){render(text,AgentRuntime.history());text.append("\n\n你："+draft[0]+"\n\n她："+reply);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}else if(owner!=null&&owner.visible){owner.streamReply(reply);}}
    public void done(String reply,String emotion){if(!dialog.isShowing())return;sending[0]=false;send.setText("发送");input.setEnabled(true);status.setText("");render(text,AgentRuntime.history());AgentRuntime.express(instance,emotion,reply);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    public void error(String message){if(!dialog.isShowing())return;sending[0]=false;send.setText("发送");input.setEnabled(true);input.setText(draft[0]);status.setText(message);render(text,AgentRuntime.history());if(owner!=null)owner.clearBubble();}
   };
   if(!AgentRuntime.send(message,false,listener)){status.setText("她正在回复，请稍候");return;}sending[0]=true;input.setText("");input.setEnabled(false);send.setText("停止");status.setText("正在想怎么说…");if(!overlay){render(text,AgentRuntime.history());text.append("\n\n你："+message);}if(owner!=null&&owner.visible){owner.play("工作状态-思考冒泡");owner.thinking();}
  });
 }
 static String displayText(String value){return value.replace("\r\n","\n").replace('\r','\n').replaceAll("\n(?:[\\t ]*\n)+","\n").trim();}
 static void render(TextView v,JSONArray h){android.text.SpannableStringBuilder s=new android.text.SpannableStringBuilder();for(int i=0;i<h.length();i++){JSONObject m=h.optJSONObject(i);if(m==null)continue;if(s.length()>0)s.append("\n\n");boolean user="user".equals(m.optString("role"));String message=(user?"你：":"她：")+displayText(m.optString("content"));if(user)s.append(message);else s.append("→ ").append(message);}v.setText(s.length()==0?"我在呢。先说说你吧。\n\n也可以试试「记住：我喜欢蓝色」。":s);}


 static String request(JSONArray messages)throws Exception{return request(messages,Data.prefs().getString("api.url",""),Data.prefs().getString("api.model",""),secret(),Data.prefs().getBoolean("api.header",false),600,60000);}
 static String request(JSONArray messages,String configuredBase,String selectedModel,String apiSecret,boolean apiKeyHeader,int tokenLimit,int timeout)throws Exception{ApiEndpoint address=new ApiEndpoint(configuredBase);ApiEndpoint.validateModel(selectedModel);ApiEndpoint.validateSecret(apiSecret);URL url=new URL(address.endpoint);HttpURLConnection con=(HttpURLConnection)url.openConnection();try{con.setInstanceFollowRedirects(false);con.setConnectTimeout(15000);con.setReadTimeout(timeout);con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json");if(apiKeyHeader)con.setRequestProperty("api-key",apiSecret);else con.setRequestProperty("Authorization","Bearer "+apiSecret);JSONObject payload=new JSONObject().put("model",selectedModel).put("messages",messages).put("stream",false).put("max_tokens",tokenLimit);if("api.xiaomimimo.com".equals(url.getHost()))payload.put("thinking",new JSONObject().put("type","disabled"));byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=con.getOutputStream()){out.write(body);}int code=con.getResponseCode();if(code<200||code>=300)throw new IOException("接口返回 HTTP "+code+"，请检查地址、模型、Key 或额度。");String data=AgentTransport.boundedRead(con.getInputStream(),1024*1024);String answer=new JSONObject(data).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");if(answer.trim().isEmpty())throw new IOException("接口没有返回文本回复");return answer;}finally{con.disconnect();}}
}
