package com.xiaoxiao.pet;
import android.app.*;import android.view.*;import android.widget.*;

/** Names can change; the personality chosen at first meeting is never edited here. */
final class ProfilePage {
 static void show(MainActivity a,String role){
  LinearLayout form=a.column();form.setPadding(a.dp(18),a.dp(8),a.dp(18),a.dp(18));
  TextView note=a.text("她的性格在初次相遇那天就定下了，这里只改名字和称呼。",14);note.setTextColor(Apple.SECONDARY);form.addView(note);
  EditText name=Chat.field(a,form,"她的名字",Data.name(role),false);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});name.setContentDescription("她的名字");
  EditText nickname=Chat.field(a,form,"她怎么称呼你",Data.prefs().getString("agent.nickname",""),false);nickname.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});nickname.setContentDescription("她对你的称呼");
  TextView error=a.text("",13);error.setTextColor(Apple.DANGER);form.addView(error);
  AlertDialog dialog=new Apple.Builder(a).setTitle("名字与称呼").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
   String n=name.getText().toString().trim(),nick=nickname.getText().toString().trim();
   if(n.isEmpty()||n.codePointCount(0,n.length())>40||n.matches("(?s).*[\\p{Cntrl}].*"))throw new java.io.IOException("名字请填写 1–40 个字，不能含换行");
   if(nick.matches("(?s).*[\\p{Cntrl}].*"))throw new java.io.IOException("称呼不能含换行");
   AgentRuntime.cancel();if(!Data.prefs().edit().putString("profile."+role+".name",n).putString("agent.nickname",nick).commit())throw new java.io.IOException("保存失败，请重试");
   a.render();dialog.dismiss();Toast.makeText(a,"记住啦，她还是原来的她",0).show();
  }catch(Exception e){error.setText(e.getMessage());}});
 }
}
