package app.personalkeyboard.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;

public class MainActivity extends Activity {
    EditText editor; LinearLayout conversation; ScrollView scroll;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);showChat();}
    private void bubble(String s,boolean mine){
        LinearLayout row=Ui.row(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);Ui.pad(this,row,14,5);
        TextView t=Ui.text(this,s,15,Ui.INK);t.setMaxWidth(Ui.dp(this,288));Ui.pad(this,t,13,10);t.setBackground(Ui.bg(this,mine?Ui.SOFT:Ui.PANEL,14));row.addView(t);conversation.addView(row);
    }
    void showChat(){
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        LinearLayout header=Ui.row(this);Ui.pad(this,header,18,10);
        LinearLayout name=Ui.column(this);name.addView(Ui.text(this,"林然",19,Ui.INK));name.addView(Ui.text(this,"朋友 · 固定演示聊天",11,Ui.SUB));header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(Ui.button(this,"人物",()->startActivity(new Intent(this,PeopleActivity.class))),new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,38)));root.addView(header);
        TextView demo=Ui.text(this,"Personal Keyboard · 原生交互演示",11,Ui.SUB);Ui.pad(this,demo,18,2);root.addView(demo);
        scroll=new ScrollView(this);scroll.setFillViewport(true);conversation=Ui.column(this);conversation.setGravity(Gravity.BOTTOM);Ui.pad(this,conversation,0,12);scroll.addView(conversation);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView time=Ui.text(this,"今天 18:40",11,Ui.SUB);time.setGravity(Gravity.CENTER);conversation.addView(time);
        bubble("我看到有个新展，下次想一起去看看。",false);bubble("好呀，找个时间慢慢逛。",true);bubble("周六白天要加班，可能得晚一点。",false);bubble("没事，我们不赶时间。",true);bubble("这周末要不要一起去？",false);bubble("你如果忙，也可以改天。",false);
        LinearLayout compose=Ui.row(this);Ui.pad(this,compose,10,8);compose.setBackgroundColor(Ui.PANEL);
        editor=new EditText(this);editor.setText("这周有点忙，想约下周");editor.setTextSize(16);editor.setTextColor(Ui.INK);editor.setSingleLine(false);editor.setMaxLines(3);editor.setBackground(Ui.bg(this,Ui.BG,10));Ui.pad(this,editor,10,5);editor.setSelectAllOnFocus(false);editor.setSelection(editor.length());
        compose.addView(editor,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));Button send=Ui.button(this,"发送",()->{String value=editor.getText().toString();if(!value.isEmpty()){bubble(value,true);editor.setText("");scroll.post(()->scroll.fullScroll(ScrollView.FOCUS_DOWN));}});LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,40));sb.leftMargin=Ui.dp(this,6);compose.addView(send,sb);root.addView(compose);
        TextView switcher=Ui.text(this,"选择演示输入法",12,Ui.ACCENT);switcher.setGravity(Gravity.CENTER);Ui.pad(this,switcher,5,5);switcher.setOnClickListener(v->((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker());root.addView(switcher);
        Ui.content(this,root);editor.requestFocus();editor.postDelayed(()->((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT),300);scroll.post(()->scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
