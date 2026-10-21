package app.doudou.ime.app;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** Small page host. Domain screens own their actions and data, navigation owns the shell. */
public abstract class Screen {
  protected final Activity activity;
  protected final KeyboardApp app;
  protected final Navigator nav;
  protected LinearLayout body;

  protected Screen(Activity a, KeyboardApp app, Navigator nav) {
    activity = a;
    this.app = app;
    this.nav = nav;
  }

  public final void render(LinearLayout target) {
    body = target;
    draw();
  }

  protected abstract void draw();

  protected void add(View view) {
    body.addView(view, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(activity, body, 14);
  }

  protected void caption(String value) {
    TextView t = Ui.text(activity, value, 12, Ui.SUB);
    t.setLineSpacing(Ui.dp(activity, 4), 1);
    add(t);
  }

  protected LinearLayout section(String title, String detail) {
    LinearLayout c = Ui.card(activity);
    TextView h = Ui.text(activity, title, 15, Ui.INK);
    h.setTypeface(null, android.graphics.Typeface.BOLD);
    c.addView(h);
    if (!detail.isEmpty()) captionIn(c, detail);
    return c;
  }

  protected void captionIn(LinearLayout card, String value) {
    Ui.gap(activity, card, 8);
    TextView t = Ui.text(activity, value, 12, Ui.SUB);
    t.setLineSpacing(Ui.dp(activity, 4), 1);
    card.addView(t);
  }

  protected void action(LinearLayout card, String title, Runnable action) {
    Ui.gap(activity, card, 5);
    card.addView(Ui.divider(activity));
    card.addView(Ui.cell(activity, "", title, "", "", action));
  }

  protected boolean enabled() {
    InputMethodManager imm =
        (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    for (InputMethodInfo i : imm.getEnabledInputMethodList())
      if (i.getPackageName().equals(activity.getPackageName())) return true;
    return false;
  }

  public void result(int request, int result, android.content.Intent data) {}
}
