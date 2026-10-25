package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.clipboard.ClipboardHistory;
import app.doudou.ime.design.*;
import app.doudou.ime.history.HistoryStore;
import java.text.SimpleDateFormat;
import java.util.*;

/** Reuse local words without leaving the active editor. */
final class KeyboardLibrary {
  private final EditorHost host;
  private final Runnable back;
  private boolean history;
  private List<ClipboardHistory.Clip> cleared;
  KeyboardLibrary(EditorHost host, Runnable back) { this.host=host; this.back=back; }

  void show(boolean inputHistory) {
    if (host.privateMode()) { Ui.toast(host.context(), "隐私输入不读取历史"); return; }
    history=inputHistory;
    Context context=host.context();
    LinearLayout root=host.openPanel("library",KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout heading=Ui.row(context);
    heading.addView(Icons.button(context,R.drawable.ic_arrow_back,"返回工具",back),new LinearLayout.LayoutParams(Ui.dp(context,44),Ui.dp(context,48)));
    for (boolean tab:new boolean[]{false,true}) {
      TextView button=Ui.button(context,tab ? "输入历史" : "剪贴板",()->show(tab));
      button.setTextSize(15); button.setTextColor(history==tab ? Ui.ink() : Ui.sub());
      button.setBackground(Ui.pressable(context,history==tab ? Ui.specialKey() : 0,16));
      button.setSelected(history==tab);
      heading.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(context,44),1));
    }
    if (!history) {
      TextView clear=Ui.text(context,"清空",13,Ui.sub());
      clear.setGravity(Gravity.CENTER);clear.setFocusable(true);
      clear.setOnClickListener(v->{
        List<ClipboardHistory.Clip> values=host.services().clips.list();
        if(!values.isEmpty()){cleared=values;host.services().clips.clear();show(false);}
      });
      heading.addView(clear,new LinearLayout.LayoutParams(Ui.dp(context,54),Ui.dp(context,44)));
    }
    root.addView(heading);
    ScrollView scroll=new ScrollView(context);
    LinearLayout content=Ui.col(context); Ui.pad(context,content,12,6);
    scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    int count=0;
    if (history) {
      for(HistoryStore.Entry entry:host.services().histories.reusable(50)) {
        String text=entry.resultText;
        row(content,text,entry.type.equals("advisor") ? "帮我回" : "语音",entry.updated,null);count++;
      }
    } else {
      for(ClipboardHistory.Clip clip:host.services().clips.list()) {
        row(content,clip.text,"复制",clip.time,()->host.services().clips.remove(clip.id));count++;
      }
    }
    if(count==0) {
      TextView empty=Ui.text(context,history ? "还没有输入历史" : "复制的文字，会留在这里",17,Ui.ink());
      empty.setGravity(Gravity.CENTER); Ui.pad(context,empty,12,24);content.addView(empty);
      TextView detail=Ui.text(context,history ? "语音和帮我回的结果，开启历史后保存在手机。" : "仅本机保留最近 30 条、7 天。隐私输入和系统标记的敏感内容不记录。",13,Ui.sub());
      detail.setGravity(Gravity.CENTER);content.addView(detail);
    }
    TextView hint=Ui.text(context,history ? "最近 50 条 · 点一下填入当前输入框" : "点一下粘贴 · 长按删除 · 仅存本机",12,Ui.sub());
    hint.setGravity(Gravity.CENTER);
    if(!history && cleared!=null){
      hint.setText("已清空 · 撤销");hint.setTextColor(Ui.ink());hint.setFocusable(true);
      hint.setOnClickListener(v->{host.services().clips.restore(cleared);cleared=null;show(false);});
    }
    root.addView(hint,new LinearLayout.LayoutParams(-1,Ui.dp(context,cleared!=null && !history ? 44 : 30)));
  }

  private void row(LinearLayout parent,String value,String type,long time,Runnable remove) {
    Context context=host.context();LinearLayout card=Ui.col(context);
    Ui.pad(context,card,13,10);card.setBackground(Ui.pressable(context,Ui.hero(),14));
    TextView text=Ui.text(context,value,15,Ui.ink());text.setMaxLines(2);
    text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setLineSpacing(Ui.dp(context,2),1);
    card.addView(text);Ui.gap(context,card,5);
    card.addView(Ui.text(context,type+" · "+new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time)),11,Ui.sub()));
    card.setFocusable(true);card.setContentDescription("填入："+value);
    card.setOnClickListener(v->{host.commitComposing();if(host.insert(value))host.typing();});
    if(remove!=null) card.setOnLongClickListener(v->{remove.run();show(false);Ui.toast(context,"已删除这条剪贴板历史");return true;});
    parent.addView(card,new LinearLayout.LayoutParams(-1,-2));Ui.gap(context,parent,7);
  }
}
