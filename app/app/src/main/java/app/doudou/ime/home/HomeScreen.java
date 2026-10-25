package app.doudou.ime.home;

import android.app.*;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import app.doudou.ime.advisor.PaletteStore;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.design.Icons;
import app.doudou.ime.R;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.people.RelationshipGraph;
import java.util.*;
import org.json.*;

public final class HomeScreen extends Screen {
  public HomeScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  @Override
  protected void draw() {
    LinearLayout practice = Ui.card(activity);
    practice.setBackground(Ui.bg(activity, Ui.hero(), 22));
    TextView title = Ui.text(activity, "试试豆豆", 22, Ui.ink());
    title.setTypeface(null, Typeface.BOLD);
    practice.addView(title);
    Ui.gap(activity, practice, 14);
    LinearLayout scenes = Ui.row(activity);
    practiceAction(scenes, "聊天", false);
    practiceAction(scenes, "评论", true);
    practice.addView(scenes);
    add(practice);
    LinearLayout palette = Ui.col(activity);
    TextView label = Ui.text(activity, "调色盘", 21, Ui.ink());
    label.setTypeface(null, Typeface.BOLD);
    palette.addView(label);
    Ui.gap(activity, palette, 12);
    try {
      JSONArray slots = new PaletteStore(app).cached().getJSONArray("slots");
      for (int row = 0; row < 2; row++) {
        LinearLayout line = Ui.row(activity);
        for (int column = 0; column < 3; column++) {
          int index = row * 3 + column;
          JSONObject slot = slots.getJSONObject(index);
          int color = PaletteStore.COLORS[index];
          LinearLayout tile = Ui.col(activity);
          tile.setMinimumHeight(Ui.dp(activity,
              72 * Math.max(1, activity.getResources().getConfiguration().fontScale)));
          Ui.pad(activity, tile, 14, 10);
          tile.setGravity(Gravity.CENTER_VERTICAL);
          tile.setBackground(Ui.pressable(activity, Ui.panel(), 18));
          View dot = new View(activity);
          dot.setBackground(Ui.bg(activity, color, 8));
          LinearLayout identity = Ui.row(activity);
          identity.setGravity(Gravity.CENTER_VERTICAL);
          identity.addView(dot, new LinearLayout.LayoutParams(Ui.dp(activity, 14), Ui.dp(activity, 14)));
          identity.addView(new Space(activity), new LinearLayout.LayoutParams(0, 1, 1));
          identity.addView(Icons.image(activity, R.drawable.ic_edit_note, Ui.sub()),
              new LinearLayout.LayoutParams(Ui.dp(activity, 16), Ui.dp(activity, 16)));
          tile.addView(identity);
          Ui.gap(activity, tile, 8);
          TextView name = Ui.text(activity, slot.getString("name"), 17, Ui.ink());
          name.setMaxLines(2);
          name.setEllipsize(android.text.TextUtils.TruncateAt.END);
          tile.addView(name);
          tile.setContentDescription("编辑" + slot.getString("name"));
          tile.setFocusable(true);
          tile.setOnClickListener(v -> editSlot(index));
          LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
          size.setMargins(0, 0, column < 2 ? Ui.dp(activity, 8) : 0, 0);
          line.addView(tile, size);
        }
        palette.addView(line, new LinearLayout.LayoutParams(-1, -2));
        if (row == 0) Ui.gap(activity, palette, 8);
      }
    } catch (Exception e) {
      palette.addView(Ui.button(activity, "重新加载调色盘", nav::refresh));
    }
    add(palette);
    LinearLayout previews = Ui.row(activity);
    previews.setGravity(Gravity.TOP);
    previews.setBaselineAligned(false);
    List<MemoryStore.Item> styles = app.store.list("style", 0);
    LinearLayout style = Ui.card(activity);
    Ui.pad(activity, style, 12, 12);
    style.addView(Ui.cell(activity, "", "我的文风", "", "", () -> nav.open("style", 0)));
    if (styles.isEmpty()) {
      TextView waiting = Ui.text(activity, "还在认识你的表达", 15, Ui.sub());
      Ui.pad(activity, waiting, 0, 8);
      style.addView(waiting);
    } else {
      MemoryStore.Item first = styles.get(0);
      TextView scene = Ui.text(activity, first.title, 14, Ui.appAccent());
      scene.setMaxLines(2);
      scene.setEllipsize(android.text.TextUtils.TruncateAt.END);
      style.addView(scene);
      Ui.gap(activity, style, 8);
      TextView rules = Ui.text(activity, first.body, 15, Ui.ink());
      rules.setMaxLines(3);
      rules.setEllipsize(android.text.TextUtils.TruncateAt.END);
      rules.setLineSpacing(Ui.dp(activity, 3), 1);
      style.addView(rules);
    }
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
    left.rightMargin = Ui.dp(activity, 8);
    previews.addView(style, left);
    List<MemoryStore.Item> people = app.store.list("person", 0);
    LinearLayout graph = Ui.card(activity);
    Ui.pad(activity, graph, 12, 12);
    graph.addView(Ui.cell(activity, "", "人物星图", "", "", () -> nav.open("people", 0)));
    if (!people.isEmpty()) {
      RelationshipGraph preview =
          new RelationshipGraph(
              activity,
              people.subList(0, Math.min(3, people.size())),
              id -> nav.open("person", id));
      preview.setMinimumHeight(0);
      preview.setContentDescription("人物星图预览");
      graph.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 120)));
    } else {
      TextView empty = Ui.text(activity, "还没有人物", 15, Ui.sub());
      Ui.pad(activity, empty, 0, 8);
      graph.addView(empty);
    }
    previews.addView(graph, new LinearLayout.LayoutParams(0, -1, 1));
    add(previews);
  }

  private void practiceAction(LinearLayout row, String text, boolean comments) {
    TextView button =
        Ui.button(
            activity,
            text + "  ›",
            () ->
                activity.startActivity(
                    new Intent(activity, PracticeActivity.class)
                        .putExtra("scene", comments ? "comments" : "chat")));
    button.setTextColor(Ui.appAccent());
    button.setBackground(Ui.pressable(activity, Ui.appTint(), 14));
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, Ui.dp(activity, 50), 1);
    size.setMargins(0, 0, comments ? 0 : Ui.dp(activity, 10), 0);
    row.addView(button, size);
  }

  private void editSlot(int index) {
    if (!app.cloud.loggedIn()) { nav.open("account", 0); return; }
    activity.startActivity(new Intent(activity, app.doudou.ime.advisor.PaletteActivity.class).putExtra("slot", index));
  }
}
