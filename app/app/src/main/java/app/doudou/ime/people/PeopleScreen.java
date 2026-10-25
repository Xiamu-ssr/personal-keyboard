package app.doudou.ime.people;

import android.app.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.*;
import java.util.*;
import org.json.*;

/** A compact optional profile and a separate key/value notebook. */
public final class PeopleScreen extends Screen {
  private long person;
  private final MemoryEditor editor;
  private final PersonFields fields;

  public PeopleScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav); editor = new MemoryEditor(a, app, nav); fields = new PersonFields(a);
  }
  public void person(long id) { person = id; }
  @Override protected void draw() { if (person > 0) detail(); else list(); }

  private void list() {
    List<MemoryStore.Item> all = app.store.list("person", 0);
    if (all.size() > 1) {
      LinearLayout graph = Ui.card(activity);
      graph.addView(new RelationshipGraph(activity, all, id -> nav.open("person", id)),
          new LinearLayout.LayoutParams(-1, Ui.dp(activity, 220)));
      add(graph);
    } else if (all.isEmpty()) Ui.empty(activity, body, "还没有人物", "");
    add(Ui.button(activity, "添加人物", () -> editor.edit(null, "person", 0)));
    if (all.isEmpty()) return;
    LinearLayout list = Ui.card(activity); Ui.pad(activity, list, 14, 2);
    for (int i = 0; i < all.size(); i++) {
      MemoryStore.Item item = all.get(i);
      list.addView(Ui.cell(activity, "", item.title, item.body, "", () -> nav.open("person", item.id)));
      if (i + 1 < all.size()) list.addView(Ui.divider(activity));
    }
    add(list);
  }

  private void detail() {
    MemoryStore.Item item = app.store.get(person);
    if (item == null || item.deleted) { nav.open("people", 0); return; }
    LinearLayout heading = Ui.row(activity);
    TextView name = Ui.text(activity, item.title, 25, Ui.ink()); name.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    View more = Ui.icon(activity, "更多", () -> {});
    more.setContentDescription("人物操作");
    more.setOnClickListener(v -> {
      PopupMenu menu = new PopupMenu(activity, more);
      menu.getMenu().add("编辑称呼与别名").setOnMenuItemClickListener(m -> { editor.edit(item, "person", 0); return true; });
      menu.getMenu().add("查看来源").setOnMenuItemClickListener(m -> { editor.source(item); return true; });
      menu.getMenu().add("删除人物").setOnMenuItemClickListener(m -> {
        new AlertDialog.Builder(activity).setTitle("删除这个人物？").setMessage("人物与所属记忆将一起删除。")
            .setPositiveButton("删除", (d,w) -> editor.remove(item)).setNegativeButton("取消", null).show();
        return true;
      });
      menu.show();
    });
    heading.addView(more, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48))); add(heading);
    List<MemoryStore.Item> memories = app.store.list("memory", person);
    Set<Long> used = new HashSet<>();
    LinearLayout basic = Ui.card(activity); Ui.pad(activity, basic, 14, 2);
    for (int i = 0; i < fields.fields.size(); i++) {
      JSONObject field = fields.fields.get(i); String label = field.optString("label");
      List<MemoryStore.Item> matches = new ArrayList<>();
      for (MemoryStore.Item memory : memories)
        if (fields.matches(field, memory) && (!field.optString("kind").equals("person") || memory.body.equals(item.title))) {
          matches.add(memory); used.add(memory.id);
        }
      String value = field.optString("kind").equals("person") ? item.title : values(matches);
      basic.addView(row(label, value, () -> {
        if (field.optString("kind").equals("person")) editor.edit(item, "person", 0);
        else if (matches.size() <= 1) editor.property(matches.isEmpty() ? null : matches.get(0), person, label, field.optString("kind").equals("relation"));
        else choose(matches);
      }));
      if (i + 1 < fields.fields.size()) basic.addView(Ui.divider(activity));
    }
    add(basic);
    try {
      JSONArray identities = new JSONObject(item.aux).optJSONArray("identities");
      if (identities != null && identities.length() > 0) {
        LinearLayout accounts = Ui.card(activity); Ui.pad(activity, accounts, 14, 2);
        for (int i = 0; i < identities.length(); i++) {
          JSONObject identity = identities.getJSONObject(i);
          accounts.addView(row(identity.optString("platform"), identity.optString("nickname"), null));
          if (i + 1 < identities.length()) accounts.addView(Ui.divider(activity));
        }
        add(accounts);
      }
    } catch (JSONException ignored) {}
    TextView title = Ui.text(activity, "记忆", 19, Ui.ink()); title.setTypeface(null, android.graphics.Typeface.BOLD); add(title);
    LinearLayout notes = Ui.card(activity); Ui.pad(activity, notes, 14, 2);
    for (MemoryStore.Item memory : memories) if (!used.contains(memory.id)) {
      notes.addView(row(memory.title, memory.body, () -> inspect(memory)));
      notes.addView(Ui.divider(activity));
    }
    notes.addView(Ui.button(activity, "添加记忆", () -> editor.edit(null, "memory", person))); add(notes);
    new MemoryConflicts(activity, app, nav).draw(body, item);
  }

  private String values(List<MemoryStore.Item> items) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    for (MemoryStore.Item item : items) values.add(item.body);
    return android.text.TextUtils.join("；", values);
  }

  private View row(String key, String value, Runnable action) {
    LinearLayout row = Ui.row(activity); row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(Ui.dp(activity, 50)); Ui.pad(activity, row, 0, 10);
    TextView label = Ui.text(activity, key, 15, Ui.sub());
    row.addView(label, new LinearLayout.LayoutParams(Ui.dp(activity, 92), -2));
    TextView text = Ui.text(activity, value.isEmpty() ? "未填写" : value, 16, value.isEmpty() ? Ui.sub() : Ui.ink());
    text.setMaxLines(3); text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      row.setBackground(Ui.pressable(activity, android.graphics.Color.TRANSPARENT, 8));
      row.setFocusable(true); row.setOnClickListener(v -> action.run());
      row.setContentDescription(key + "：" + (value.isEmpty() ? "未填写" : value));
    }
    return row;
  }

  private void choose(List<MemoryStore.Item> items) {
    String[] labels = new String[items.size()];
    for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).body;
    new AlertDialog.Builder(activity).setTitle("选择资料").setItems(labels, (d,w) -> inspect(items.get(w))).show();
  }

  private void inspect(MemoryStore.Item item) {
    TextView text = Ui.text(activity, item.body, 18, Ui.ink()); Ui.pad(activity, text, 22, 14); text.setTextIsSelectable(true);
    ScrollView scroll = new ScrollView(activity); scroll.addView(text);
    new AlertDialog.Builder(activity).setTitle(item.title).setView(scroll)
        .setPositiveButton("修改", (d,w) -> editor.edit(item, "memory", person))
        .setNeutralButton("来源", (d,w) -> editor.source(item))
        .setNegativeButton("更多", (d,w) -> new AlertDialog.Builder(activity)
            .setItems(new String[]{"删除这条记忆", "取消"}, (a,i) -> { if (i == 0) editor.remove(item); }).show()).show();
  }
}
