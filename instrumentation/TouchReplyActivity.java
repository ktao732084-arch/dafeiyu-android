package com.xiaoxiao.pet;
public final class TouchReplyActivity extends android.app.Activity {
 public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView note=new android.widget.TextView(this);note.setText("互动气泡验收 · 受控模型\n连续三次触摸，等待动作结束后回应");note.setTextSize(18);note.setPadding(28,50,28,28);note.setTextColor(0xff404650);note.setBackgroundColor(0xfff2f4f8);setContentView(note);}
}
