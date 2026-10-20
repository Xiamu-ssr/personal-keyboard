package app.doudou.ime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** Explicit local practice editor; never presented as a live messaging app. */
public final class EditorActivity extends Activity {
  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.pad(this, root, 20, 12);
    TextView heading = Ui.text(this, "试试豆豆", 26, Ui.INK);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    root.addView(heading);
    Ui.gap(this, root, 10);
    root.addView(Ui.text(this, "本机练习区 · 不会发送到任何人", 13, Ui.SUB));
    TextView history = Ui.text(this, "试着输入 nihao，添加一个自己的热词，或点军师体验主动读屏。", 17, Ui.SUB);
    history.setGravity(Gravity.CENTER);
    history.setLineSpacing(Ui.dp(this, 5), 1);
    root.addView(history, new LinearLayout.LayoutParams(-1, 0, 1));
    EditText editor = Ui.edit(this, "写点什么…", "");
    editor.setId(android.view.View.generateViewId());
    editor.setMinLines(2);
    editor.setMaxLines(5);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    root.addView(editor);
    Ui.gap(this, root, 12);
    root.addView(Ui.button(this, "清空练习区", () -> editor.setText("")));
    Ui.content(this, root);
    editor.requestFocus();
    editor.postDelayed(
        () ->
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT),
        300);
  }
}
