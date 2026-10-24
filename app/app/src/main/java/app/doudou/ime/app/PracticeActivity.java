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
    TextView sender = Ui.text(this, "小林 · 示例消息", 13, Ui.SUB);
    Ui.pad(this, sender, 8, 4); root.addView(sender);
    LinearLayout note = Ui.card(this);
    TextView example = Ui.text(this, "今晚有空吗？要不要一起吃个饭？", 17, Ui.INK);
    example.setLineSpacing(Ui.dp(this, 5), 1); note.addView(example);
    root.addView(note);
    Ui.gap(this, root, 20);
    TextView help = Ui.text(this, "按住空格说话，或点「帮我回」试着回复。", 14, Ui.SUB);
    Ui.pad(this, help, 8, 4); root.addView(help);
    root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));
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
    editor.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT),
        300);
  }
}
