package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import java.util.*;

/** Discoverable local symbol and emoji collections; no network and no unsupported modes. */
public final class KeyboardExtras {
  private final EditorHost host;
  public KeyboardExtras(EditorHost host) {
    this.host = host;
  }

  public void emoji() {
    new EmojiPanel(host).show();
  }

  public void symbols() { host.symbols(); }

  public void keyboards() {
    Context c = host.context();
    LinearLayout root = host.openPanel("keyboards", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    root.addView(Ui.button(c, "返回键盘", host::typing));
    root.addView(
        Ui.cell(
            c,
            "工具",
            "中文 26 键",
            "",
            "",
            () -> {
              host.services().prefs().edit().putBoolean("preferredEnglish", false).apply();
              host.typing();
            }));
    root.addView(
        Ui.cell(
            c,
            "工具",
            "English QWERTY",
            "",
            "",
            () -> {
              host.services().prefs().edit().putBoolean("preferredEnglish", true).apply();
              host.typing();
            }));
  }

}
