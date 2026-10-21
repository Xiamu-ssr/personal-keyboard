package app.doudou.ime.input;

import android.content.ContextWrapper;
import android.view.Gravity;
import android.widget.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.Ui;
import java.util.function.Consumer;

/** Panel rendering and editor commands; no model, capture or input-state decisions. */
public abstract class PanelController extends ContextWrapper {
  protected final EditorHost host;
  protected final KeyboardApp app;
  protected LinearLayout root;

  protected PanelController(EditorHost h) {
    super(h.context());
    host = h;
    app = h.services();
  }

  protected void reset(String mode, int height) {
    root = host.openPanel(mode, height);
  }

  protected void typing() {
    host.typing();
  }

  protected void commitComposing() {
    host.commitComposing();
  }

  protected boolean privateMode() {
    return host.privateMode();
  }

  protected void snapshot() {
    host.snapshot();
  }

  protected boolean insert(String text) {
    return host.insert(text);
  }

  protected boolean protectedInsert(String text) {
    return host.protectedInsert(text);
  }

  protected void editLocal(String title, String value, Consumer<String> callback) {
    host.editLocal(title, value, callback);
  }

  protected void settings(String page) {
    host.settings(page);
  }

  protected void history(String title, String value, String source) {
    host.history(title, value, source);
  }

  protected void message(String title, String detail, Runnable retry) {
    host.message(title, detail, retry);
  }

  protected void panelHeading(String title, Runnable back) {
    LinearLayout h = Ui.row(this);
    h.addView(
        Ui.icon(this, "返回", back), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    TextView label = Ui.text(this, title, 16, Ui.INK);
    label.setGravity(Gravity.CENTER_VERTICAL);
    label.setSingleLine(true);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    h.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    root.addView(h);
  }
}
