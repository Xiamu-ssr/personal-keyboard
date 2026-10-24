package app.doudou.ime.memory;

import android.app.*;
import android.content.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import java.util.*;

/** Vocabulary, style and evidence are independent collections with one consistent list UI. */
public final class MemoryScreen extends Screen {
  private String tab = "history", query = "";
  private LinearLayout results;
  private final MemoryEditor editor;

  public MemoryScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
    editor = new MemoryEditor(a, app, nav);
  }

  public void restore(String value) {
    if (Arrays.asList("word", "style", "history").contains(value)) tab = value;
  }

  public String selected() {
    return tab;
  }

  @Override
  protected void draw() {
    LinearLayout tabs = Ui.row(activity);
    tabs.setBackground(Ui.bg(activity, Ui.PANEL, 22));
    Ui.pad(activity, tabs, 4, 4);
    String[] keys = {"history", "word", "style"}, labels = {"历史", "热词", "文风"};
    for (int i = 0; i < keys.length; i++) {
      final String key = keys[i];
      TextView pill =
          Ui.button(
              activity,
              labels[i],
              () -> {
                tab = key;
                query = "";
                nav.refresh();
              });
      pill.setTextSize(13);
      pill.setMinHeight(Ui.dp(activity, 36));
      pill.setPadding(0, 0, 0, 0);
      pill.setBackground(
          Ui.pressable(
              activity, tab.equals(key) ? Ui.SELECTED : android.graphics.Color.TRANSPARENT, 18));
      pill.setTextColor(tab.equals(key) ? Ui.INK : Ui.SUB);
      tabs.addView(pill, new LinearLayout.LayoutParams(0, Ui.dp(activity, 36), 1));
    }
    add(tabs);
    LinearLayout tools = Ui.row(activity);
    EditText search = Ui.edit(activity, "搜索这份记忆", query);
    search.setSingleLine(true);
    search.setTextSize(14);
    search.setMinHeight(Ui.dp(activity, 44));
    tools.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(activity, 44), 1));
    if (tab.equals("word") || tab.equals("style")) {
      View plus = Ui.icon(activity, "添加", () -> editor.edit(null, tab, 0));
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44));
      lp.leftMargin = Ui.dp(activity, 8);
      tools.addView(plus, lp);
    }
    add(tools);
    results = Ui.col(activity);
    body.addView(results);
    fill();
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            query = s.toString();
            fill();
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void fill() {
    results.removeAllViews();
    if (tab.equals("history")) {
      new app.doudou.ime.history.HistoryView(activity, app, nav).draw(results, query);
      return;
    }
    List<MemoryStore.Item> found = new ArrayList<>();
    List<MemoryStore.Item> candidates = new ArrayList<>(app.store.list(tab, 0));
    for (MemoryStore.Item item : candidates)
      if ((item.title + item.body)
          .toLowerCase(Locale.ROOT)
          .contains(query.toLowerCase(Locale.ROOT))) found.add(item);
    if (found.isEmpty()) {
      Ui.empty(
          activity,
          results,
          query.isEmpty() ? (tab.equals("word") ? "还没有热词" : "还在认识你的表达") : "没有找到",
          "");
      return;
    }
    LinearLayout list = Ui.card(activity);
    Ui.pad(activity, list, 14, 2);
    results.addView(list);
    for (int n = 0; n < found.size(); n++) {
      MemoryStore.Item item = found.get(n);
      String detail =
          tab.equals("word")
              ? (item.body.isEmpty() ? "" : item.body)
                  + " · "
                  + MemoryEditor.sourceName(item.source)
              : item.body;
      if (detail.length() > 96) detail = detail.substring(0, 96) + "…";
      View row = Ui.cell(activity, "", item.title, detail, "", () -> open(item));
      row.setOnLongClickListener(
          v -> {
            menu(item, row);
            return true;
          });
      list.addView(row);
      new MemoryConflicts(activity, app, nav).draw(list, item);
      if (n < found.size() - 1) list.addView(Ui.divider(activity));
    }
  }

  private void open(MemoryStore.Item item) {

    if (tab.equals("word") || tab.equals("style")) {
      editor.edit(item, item.kind, item.owner);
      return;
    }
  }

  private void menu(MemoryStore.Item item, View anchor) {
    PopupMenu menu = new PopupMenu(activity, anchor);
    menu.getMenu()
        .add("删除")
        .setOnMenuItemClickListener(
            i -> {
              editor.remove(item);
              return true;
            });
    menu.show();
  }
}
