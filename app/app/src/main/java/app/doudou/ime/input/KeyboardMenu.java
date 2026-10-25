package app.doudou.ime.input;

import android.content.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;

/** A keyboard-sized tool surface. Opening it never launches a popup over the host editor. */
public final class KeyboardMenu {
  private final EditorHost host;

  public KeyboardMenu(EditorHost host) {
    this.host = host;
  }

  public void show() {
    Context context = host.context();
    host.cancelVoice();
    host.commitComposing();
    LinearLayout root = host.openPanel("tools", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout heading = Ui.row(context);
    View back = Icons.button(context, R.drawable.ic_arrow_back, "返回键盘", host::typing);
    heading.addView(back, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
    TextView title = Ui.text(context, "常用工具", 15, Ui.sub());
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView privacy =
        Ui.button(context, host.privateMode() ? "隐私已开" : "隐私输入", host::togglePrivacy);
    privacy.setContentDescription("隐私输入");
    privacy.setSelected(host.privateMode());
    privacy.setTextSize(13);
    privacy.setTextColor(host.privateMode() ? Ui.accent() : Ui.sub());
    if (host.canUndoInsertion())
      heading.addView(
          Ui.button(context, "撤销", host::undoInsertion),
          new LinearLayout.LayoutParams(Ui.dp(context, 64), Ui.dp(context, 48)));
    heading.addView(privacy, new LinearLayout.LayoutParams(-2, Ui.dp(context, 48)));
    root.addView(heading, new LinearLayout.LayoutParams(-1, Ui.dp(context, 48)));

    LinearLayout grid = Ui.col(context);
    Ui.pad(context, grid, 14, 10);
    LinearLayout first = row(grid), second = row(grid);
    KeyboardLibrary library = new KeyboardLibrary(host, this::show);
    KeyboardExtras extras = new KeyboardExtras(host);
    tile(first, R.drawable.ic_emoji, "表情", extras::emoji, false);
    View clipboard =
        tile(first, R.drawable.ic_content_paste, "剪贴板", () -> library.show(false), false);
    View history = tile(first, R.drawable.ic_edit_note, "输入历史", () -> library.show(true), false);
    clipboard.setEnabled(!host.privateMode());
    history.setEnabled(!host.privateMode());
    clipboard.setAlpha(host.privateMode() ? .4f : 1);
    history.setAlpha(host.privateMode() ? .4f : 1);
    tile(second, R.drawable.ic_grid_view, "键盘选择", extras::keyboards, false);
    tile(second, R.drawable.ic_symbols, "符号", extras::symbols, false);
    tile(second, R.drawable.ic_settings, "键盘设置", () -> host.settings("settings"), false);
    root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private LinearLayout row(LinearLayout parent) {
    LinearLayout row = Ui.row(host.context());
    parent.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
    return row;
  }

  private View tile(
      LinearLayout row, int resource, String name, Runnable action, boolean selected) {
    Context context = host.context();
    LinearLayout tile = Ui.col(context);
    tile.setGravity(Gravity.CENTER);
    tile.setBackground(Ui.pressable(context, selected ? Ui.selected() : Ui.hero(), 18));
    tile.setContentDescription(name);
    tile.setStateDescription(selected ? "已开启" : null);
    tile.setFocusable(true);
    tile.setSelected(selected);
    tile.setOnClickListener(v -> action.run());
    tile.addView(
        Icons.image(context, resource, Ui.ink()),
        new LinearLayout.LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));
    Ui.gap(context, tile, 9);
    TextView label = Ui.text(context, name, 14, Ui.ink());
    label.setGravity(Gravity.CENTER);
    label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    tile.addView(label);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
    size.setMargins(Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 6));
    row.addView(tile, size);
    return tile;
  }
}
