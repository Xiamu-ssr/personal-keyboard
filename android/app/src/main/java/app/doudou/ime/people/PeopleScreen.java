package app.doudou.ime.people;

import android.app.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.*;
import java.util.*;

public final class PeopleScreen extends Screen {
  private long person;
  private final MemoryEditor editor;

  public PeopleScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
    editor = new MemoryEditor(a, app, nav);
  }

  public void person(long id) {
    person = id;
  }

  @Override
  protected void draw() {
    if (person > 0) detail();
    else list();
  }

  private void list() {
    List<MemoryStore.Item> all = app.store.list("person", 0);
    if (!all.isEmpty()) {
      LinearLayout card = Ui.card(activity);
      card.addView(
          new RelationshipGraph(activity, all, id -> nav.open("person", id)),
          new LinearLayout.LayoutParams(-1, Ui.dp(activity, 220)));
      add(card);
    } else Ui.empty(activity, body, "把重要的人，记在这里", "事实、事件和偏好各自有来源；同名不自动合并。");
    add(Ui.primary(activity, "＋ 添加人物", () -> editor.edit(null, "person", 0)));
    if (!all.isEmpty()) {
      LinearLayout list = Ui.card(activity);
      Ui.pad(activity, list, 14, 2);
      for (int n = 0; n < all.size(); n++) {
        MemoryStore.Item item = all.get(n);
        list.addView(
            Ui.cell(
                activity,
                "人物",
                item.title,
                item.body.isEmpty() ? "还没有备注" : item.body,
                "",
                () -> nav.open("person", item.id)));
        if (n < all.size() - 1) list.addView(Ui.divider(activity));
      }
      add(list);
    }
    caption("人物资料按应用和身份隔离，读到的文字不自动变成事实。");
  }

  private void detail() {
    MemoryStore.Item item = app.store.get(person);
    if (item == null || item.deleted) {
      nav.open("people", 0);
      return;
    }
    LinearLayout profile = Ui.card(activity);
    TextView name = Ui.text(activity, item.title, 25, Ui.INK);
    name.setTypeface(null, android.graphics.Typeface.BOLD);
    profile.addView(name);
    captionIn(profile, item.body.isEmpty() ? "慢慢了解，也允许随时修正。" : item.body);
    action(profile, "编辑人物资料", () -> editor.edit(item, "person", 0));
    add(profile);
    add(Ui.primary(activity, "＋ 补充记忆", () -> editor.edit(null, "memory", person)));
    List<MemoryStore.Item> memories = app.store.list("memory", person);
    for (MemoryStore.Item memory : memories) {
      LinearLayout card = section(memory.title, memory.body);
      captionIn(
          card,
          type(memory.aux)
              + " · "
              + MemoryEditor.sourceName(memory.source)
              + " · "
              + Ui.when(memory.updated));
      action(card, "修订记忆", () -> editor.edit(memory, "memory", person));
      action(card, "删除", () -> editor.remove(memory));
      add(card);
    }
    if (memories.isEmpty()) caption("还没有记忆。事实、事件、偏好和推断会分别标注。");
    TextView delete =
        Ui.button(
            activity,
            "删除人物及其记忆",
            () ->
                new AlertDialog.Builder(activity)
                    .setTitle("删除这个人物？")
                    .setMessage("人物与所属记忆一起移出，可立即撤销。")
                    .setPositiveButton(
                        "删除",
                        (d, w) -> {
                          app.store.delete(item.id);
                          nav.open("people", 0);
                          nav.notice(
                              "已删除",
                              () -> {
                                app.store.restore(item.id);
                                nav.refresh();
                              });
                        })
                    .setNegativeButton("取消", null)
                    .show());
    delete.setTextColor(Ui.RED);
    add(delete);
  }

  private String type(String key) {
    return key.equals("event")
        ? "事件"
        : key.equals("preference") ? "偏好" : key.equals("inference") ? "推断" : "事实";
  }
}
