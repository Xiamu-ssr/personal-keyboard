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
  private String tab = "word", query = "";
  private LinearLayout results;
  private final MemoryEditor editor;

  public MemoryScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
    editor = new MemoryEditor(a, app, nav);
  }

  public void restore(String value) {
    if (Arrays.asList("word", "style", "history", "feedback").contains(value)) tab = value;
  }

  public String selected() {
    return tab;
  }

  @Override
  protected void draw() {
    LinearLayout tabs = Ui.row(activity);
    tabs.setBackground(Ui.bg(activity, Ui.PANEL, 22));
    Ui.pad(activity, tabs, 4, 4);
    String[] keys = {"word", "style", "history", "feedback"}, labels = {"热词", "文风", "历史", "待学习"};
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
    String detail =
        tab.equals("word")
            ? "热词同时帮助打字和语音识别。"
            : tab.equals("style")
                ? "这里是你的表达习惯，不混入某个人的偏好。"
                : tab.equals("history") ? "语音、截图与结果稿，保存在手机。" : "核对文风和人物资料的更新建议。";
    caption(detail);
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
    List<MemoryStore.Item> candidates =
        new ArrayList<>(app.store.list(tab.equals("feedback") ? "proposal" : tab, 0));
    for (MemoryStore.Item item : candidates)
      if ((item.title + item.body)
          .toLowerCase(Locale.ROOT)
          .contains(query.toLowerCase(Locale.ROOT))) found.add(item);
    if (found.isEmpty()) {
      Ui.empty(
          activity,
          results,
          query.isEmpty() ? "让记忆从这里开始" : "没有找到",
          query.isEmpty() ? "添加一个常用词，或保留一次自己的表达。" : "换个词试试，原有资料没有改变。");
      return;
    }
    LinearLayout list = Ui.card(activity);
    Ui.pad(activity, list, 14, 2);
    results.addView(list);
    for (int n = 0; n < found.size(); n++) {
      MemoryStore.Item item = found.get(n);
      String detail =
          tab.equals("word")
              ? (item.body.isEmpty() ? "仅用于整理" : item.body)
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
      if (n < found.size() - 1) list.addView(Ui.divider(activity));
    }
  }

  private void open(MemoryStore.Item item) {
    if (item.kind.equals("proposal")) {
      try {
        org.json.JSONObject p = new org.json.JSONObject(item.body);
        new AlertDialog.Builder(activity)
            .setTitle("确认学习建议")
            .setMessage(candidateText(p))
            .setPositiveButton(
                "采纳",
                (d, w) -> {
                  confirm(item.globalId, true);
                })
            .setNeutralButton(
                "忽略",
                (d, w) -> {
                  confirm(item.globalId, false);
                })
            .setNegativeButton("返回", null)
            .show();
      } catch (Exception e) {
        Ui.toast(activity, "候选格式不正确，原资料保留");
      }
      return;
    }

    if (tab.equals("word") || tab.equals("style")) {
      editor.edit(item, item.kind, item.owner);
      return;
    }
    AlertDialog.Builder d =
        new AlertDialog.Builder(activity)
            .setTitle(item.title)
            .setMessage(
                item.body
                    + "\n\n"
                    + MemoryEditor.sourceName(item.source)
                    + " · "
                    + Ui.when(item.updated));
    if (tab.equals("feedback") && !item.source.startsWith("sync-conflict:"))
      d.setPositiveButton("确认为热词", (x, w) -> editor.edit(null, "word", 0, item.aux, ""));
    else
      d.setPositiveButton(
          "复制文字",
          (x, w) -> {
            ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("豆豆记忆", item.body));
            Ui.toast(activity, "已复制");
          });
    d.setNeutralButton("删除", (x, w) -> editor.remove(item)).setNegativeButton("返回", null).show();
  }

  private void menu(MemoryStore.Item item, View anchor) {
    PopupMenu menu = new PopupMenu(activity, anchor);
    menu.getMenu()
        .add("删除")
        .setOnMenuItemClickListener(
            i -> {
              if (item.kind.equals("proposal")) confirm(item.globalId, false);
              else editor.remove(item);
              return true;
            });
    menu.show();
  }

  private void confirm(String id, boolean accept) {
    app.network.execute(
        () -> {
          try {
            app.personalization.candidate(id, accept);
            activity.runOnUiThread(nav::refresh);
          } catch (Exception e) {
            activity.runOnUiThread(() -> Ui.toast(activity, e.getMessage()));
          }
        });
  }

  private String candidateText(org.json.JSONObject candidate) throws org.json.JSONException {
    org.json.JSONObject op = candidate.getJSONObject("operation"),
        value = op.optJSONObject("value");
    if (op.optString("action").equals("delete"))
      return "建议移除这项资料。\n\n" + candidate.optString("reason");
    String text =
        value.optString(
            "text",
            value.optString("name", value.optString("scene", value.optString("key", "关系"))));
    if (value.has("value")) text += "：" + value.getString("value");
    if (value.has("rules")) text += "\n" + value.getJSONArray("rules").join("、");
    if (value.has("type")) text += "：" + value.getString("type");
    return text + "\n\n" + candidate.optString("reason");
  }
}
