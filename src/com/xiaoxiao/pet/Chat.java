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
 static void settings(Activity a){ModelSetup.show(a);}
 static final Map<String,Runnable> presentations=new HashMap<>();
 static void interruptPresentation(String id){Runnable stop=presentations.get(id);if(stop!=null)stop.run();if(PetService.instance!=null){PetService.Pet p=PetService.instance.pets.get(id);if(p!=null)p.clearBubble();}}
 static void show(Context c,String role,String instance){ChatPage.show(c,role,instance);}
 static void openHistory(Context c){c.startActivity(new Intent(c,MainActivity.class).putExtra("openChat",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
 static String displayText(String value){return value.replace("\r\n","\n").replace('\r','\n').replaceAll("\n(?:[\\t ]*\n)+","\n").trim();}
 static void render(TextView v,JSONArray h){android.text.SpannableStringBuilder s=new android.text.SpannableStringBuilder();for(int i=0;i<h.length();i++){JSONObject m=h.optJSONObject(i);if(m==null)continue;if(s.length()>0)s.append("\n\n");boolean user="user".equals(m.optString("role"));String message=(user?"你：":"她：")+displayText(m.optString("content"));if(user)s.append(message);else s.append("→ ").append(message);}v.setText(s.length()==0?"我在呢。先说说你吧。\n\n也可以试试「记住：我喜欢蓝色」。":s);}


}
