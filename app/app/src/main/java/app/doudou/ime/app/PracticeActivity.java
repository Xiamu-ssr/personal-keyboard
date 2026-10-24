package app.doudou.ime.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** A local practice editor. Sample notes are not a conversation or generated replies. */
public final class PracticeActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.pad(this, root, 16, 8);
    LinearLayout header = Ui.row(this);
    header.addView(
        Ui.icon(this, "返回", this::finish),
        new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 44)));
    TextView title = Ui.text(this, "试试豆豆", 22, Ui.INK);
    title.setTypeface(null, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    title.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(header);
    Ui.gap(this, root, 8);
    TextView privacy = Ui.text(this, "练习对话 · 不会发送给任何人", 12, Ui.SUB);
    Ui.pad(this, privacy, 8, 8); root.addView(privacy);
    Ui.gap(this, root, 18);
    ScrollView chat=new ScrollView(this){
      @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        boolean bottom=oldH==0 || getChildCount()==0 || getScrollY()+oldH>=getChildAt(0).getHeight()-Ui.dp(getContext(),4);
        super.onSizeChanged(w,h,oldW,oldH);
        if(bottom)post(()->fullScroll(View.FOCUS_DOWN));
      }
    };chat.setFillViewport(true);
    chat.setContentDescription("示例聊天记录");
    LinearLayout messages=Ui.col(this);chat.addView(messages);
    root.addView(chat,new LinearLayout.LayoutParams(-1,0,1));
    String[] examples={
      "小林|最近忙完了吗？", "我|项目昨天交了，终于能歇一下。",
      "小林|那找个时间一起吃饭吧。", "我|好啊，你想吃什么？",
      "小林|想吃火锅，听说附近新开了一家。", "我|我都行，别太辣就好。",
      "小林|可以点鸳鸯锅，我来找位置。", "我|那我下班后过去。",
      "小林|地铁口出来走五分钟就到。", "我|行，确定了把地址发我。",
      "小林|对了，听说你最近在做输入法？", "我|对，还在打磨语音和帮我回。",
      "小林|等做好了我也来试试。", "我|没问题，到时候叫你。",
      "小林|今晚有空吗？要不要一起吃个饭？"};
    for(String example:examples){
      String[] parts=example.split("\\|",2);
      TextView sender=Ui.text(this,parts[0]+" · 示例",12,Ui.SUB);
      Ui.pad(this,sender,8,5);messages.addView(sender);
      LinearLayout note=Ui.card(this);
      TextView line=Ui.text(this,parts[1],17,Ui.INK);line.setLineSpacing(Ui.dp(this,4),1);note.addView(line);
      messages.addView(note);Ui.gap(this,messages,12);
    }
    LinearLayout composer = Ui.row(this);
    EditText editor = Ui.edit(this, "写点什么…", "");
    editor.setId(View.generateViewId());
    editor.setMinLines(1);
    editor.setMaxLines(4);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    composer.addView(editor, new LinearLayout.LayoutParams(0, -2, 1));
    View clear = Ui.icon(this, "清空练习区", () -> editor.setText(""));
    LinearLayout.LayoutParams clearSize =
        new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48));
    clearSize.leftMargin = Ui.dp(this, 6);
    composer.addView(clear, clearSize);
    root.addView(composer);
    Ui.gap(this, root, 8);
    Ui.content(this, root);
    editor.requestFocus();
    chat.post(()->chat.fullScroll(View.FOCUS_DOWN));
    editor.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT),
        300);
  }
}
