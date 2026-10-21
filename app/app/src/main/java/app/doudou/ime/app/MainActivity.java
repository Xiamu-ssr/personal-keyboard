package app.doudou.ime.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.home.HomeScreen;
import app.doudou.ime.memory.MemoryScreen;
import app.doudou.ime.people.PeopleScreen;
import app.doudou.ime.settings.SettingsScreen;

/** Android lifecycle and navigation only. Each page is owned by its business domain. */
public final class MainActivity extends Activity implements Navigator {
  private KeyboardApp app;
  private String page = "home";
  private long person;
  private LinearLayout notice;
  private HomeScreen home;
  private MemoryScreen memory;
  private PeopleScreen people;
  private SettingsScreen settings;
  private app.doudou.ime.account.AccountScreen account;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (KeyboardApp) getApplication();
    if (app.prefs().getString("installation", "").isEmpty())
      app.prefs().edit().putString("installation", java.util.UUID.randomUUID().toString()).apply();
    home = new HomeScreen(this, app, this);
    memory = new MemoryScreen(this, app, this);
    people = new PeopleScreen(this, app, this);
    settings = new SettingsScreen(this, app, this);
    account = new app.doudou.ime.account.AccountScreen(this, app, this);
    if (state != null) {
      page = state.getString("page", "home");
      person = state.getLong("person");
      memory.restore(state.getString("memoryTab", "word"));
    }
    String requested = getIntent().getStringExtra("page");
    if (requested != null && !requested.isEmpty()) page = requested;
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (app != null) render();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    b.putString("page", page);
    b.putLong("person", person);
    b.putString("memoryTab", memory.selected());
    super.onSaveInstanceState(b);
  }

  @Override
  public void open(String next, long id) {
    if (next.equals("words") || next.equals("style")) {
      memory.restore(next.equals("words") ? "word" : "style");
      page = "memory";
    } else page = next;
    person = id;
    render();
  }

  @Override
  public void refresh() {
    render();
  }

  @Override
  public void notice(String text, Runnable undo) {
    notice.removeAllViews();
    LinearLayout row = Ui.row(this);
    Ui.pad(this, row, 18, 4);
    row.setBackground(Ui.bg(this, Ui.SURFACE, 14));
    row.addView(Ui.text(this, text, 13, Ui.INK), new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(Ui.button(this, "撤销", undo));
    notice.addView(row);
  }

  private void render() {
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    LinearLayout heading = Ui.row(this);
    Ui.pad(this, heading, 20, 10);
    if (page.equals("person") || page.equals("account"))
      heading.addView(
          Ui.icon(this, "返回", () -> open(page.equals("account") ? "settings" : "people", 0)),
          new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 48)));
    String title =
        page.equals("home")
            ? "豆豆输入法"
            : page.equals("memory")
                ? "我的记忆"
                : page.equals("people")
                    ? "人物关系"
                    : page.equals("person") ? "人物资料" : page.equals("account") ? "账户与权益" : "设置";
    TextView label = Ui.text(this, title, 28, Ui.INK);
    label.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1));
    label.setGravity(Gravity.CENTER_VERTICAL);
    if (page.equals("home")) {
      TextView mark = Ui.text(this, "0.4", 11, Ui.ACCENT);
      Ui.pad(this, mark, 10, 6);
      mark.setBackground(Ui.bg(this, Ui.SELECTED, 20));
      heading.addView(mark);
    }
    root.addView(heading);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 10));
    LinearLayout body = Ui.col(this);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (page.equals("home")) home.render(body);
    else if (page.equals("memory")) memory.render(body);
    else if (page.equals("people") || page.equals("person")) {
      people.person(person);
      people.render(body);
    } else if (page.equals("account")) account.render(body);
    else settings.render(body);
    notice = Ui.col(this);
    Ui.pad(this, notice, 20, 0);
    root.addView(notice);
    root.addView(Ui.divider(this));
    LinearLayout nav = Ui.row(this);
    nav.setBackgroundColor(Ui.PANEL);
    Ui.pad(this, nav, 12, 8);
    String[] labels = {"输入", "记忆", "人物", "设置"},
        icons = {"首页", "词典", "人物", "设置"},
        routes = {"home", "memory", "people", "settings"};
    for (int i = 0; i < labels.length; i++) {
      final String route = routes[i];
      boolean selected =
          page.equals(route)
              || (page.equals("person") && route.equals("people"))
              || (page.equals("account") && route.equals("settings"));
      nav.addView(
          Ui.nav(this, labels[i], icons[i], selected, () -> open(route, 0)),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1));
    }
    root.addView(nav);
    Ui.content(this, root);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    settings.result(request, result, data);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] names, int[] grants) {
    super.onRequestPermissionsResult(request, names, grants);
    render();
  }
}
