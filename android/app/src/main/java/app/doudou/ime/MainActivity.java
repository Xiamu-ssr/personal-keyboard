package app.doudou.ime;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private DoudouApp app;
  private LinearLayout root, body, notice;
  private String page = "home", memoryTab = "word";
  private long person;
  private static final int EXPORT = 100, IMPORT = 101;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (DoudouApp) getApplication();
    if (app.prefs().getString("installation", "").isEmpty())
      app.prefs().edit().putString("installation", UUID.randomUUID().toString()).apply();
    if (state != null) {
      page = state.getString("page", "home");
      person = state.getLong("person");
    }
    String go = getIntent().getStringExtra("page");
    if (go != null) page = go;
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
    super.onSaveInstanceState(b);
  }

  private void go(String next) {
    page = next;
    person = 0;
    render();
  }

  private void render() {
    root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.pad(this, root, 20, 8);
    LinearLayout heading = Ui.row(this);
    TextView title =
        Ui.text(
            this,
            page.equals("home")
                ? "豆豆输入法"
                : page.equals("memory")
                    ? "我的记忆"
                    : page.equals("people") ? "人物关系" : page.equals("person") ? "人物资料" : "设置",
            28,
            Ui.INK);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1));
    title.setGravity(Gravity.CENTER_VERTICAL);
    if (page.equals("person")) heading.addView(Ui.button(this, "返回", () -> go("people")));
    root.addView(heading);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = Ui.col(this);
    Ui.gap(this, body, 10);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    switch (page) {
      case "home":
        home();
        break;
      case "memory":
        memory();
        break;
      case "people":
        people();
        break;
      case "person":
        person();
        break;
      default:
        settings();
    }
    notice = Ui.col(this);
    root.addView(notice);
    LinearLayout nav = Ui.row(this);
    Ui.pad(this, nav, 0, 10);
    String[] labels = {"输入", "记忆", "人物", "设置"}, routes = {"home", "memory", "people", "settings"};
    for (int i = 0; i < labels.length; i++) {
      final String route = routes[i];
      TextView b = Ui.button(this, labels[i], () -> go(route));
      boolean selected = page.equals(route) || (page.equals("person") && route.equals("people"));
      b.setTextColor(selected ? Ui.BG : Ui.SUB);
      b.setBackground(Ui.bg(this, selected ? Ui.ACCENT : Ui.BG, 18));
      nav.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
    }
    root.addView(nav);
    Ui.content(this, root);
  }

  private void add(View v) {
    body.addView(v, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(this, body, 14);
  }

  private void caption(String text) {
    TextView v = Ui.text(this, text, 13, Ui.SUB);
    v.setLineSpacing(Ui.dp(this, 4), 1);
    add(v);
  }

  private LinearLayout section(String title, String subtitle) {
    LinearLayout c = Ui.card(this);
    TextView h = Ui.text(this, title, 19, Ui.INK);
    h.setTypeface(null, android.graphics.Typeface.BOLD);
    c.addView(h);
    if (!subtitle.isEmpty()) {
      Ui.gap(this, c, 10);
      TextView d = Ui.text(this, subtitle, 14, Ui.SUB);
      d.setLineSpacing(Ui.dp(this, 4), 1);
      c.addView(d);
    }
    return c;
  }

  private void action(LinearLayout card, String label, Runnable action) {
    Ui.gap(this, card, 14);
    card.addView(Ui.button(this, label, action), new LinearLayout.LayoutParams(-1, -2));
  }

  private boolean enabled() {
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    for (InputMethodInfo i : imm.getEnabledInputMethodList())
      if (i.getPackageName().equals(getPackageName())) return true;
    return false;
  }

  private void home() {
    LinearLayout hero = section("少一点操作，多一点自己。", "日常打字 · 说话整理 · 原地军师");
    Ui.gap(this, hero, 20);
    TextView mark = Ui.text(this, "豆豆", 44, Ui.ACCENT);
    mark.setTypeface(null, android.graphics.Typeface.BOLD);
    hero.addView(mark);
    Ui.gap(this, hero, 8);
    hero.addView(Ui.text(this, "0.1 · 自用预览版", 12, Ui.SUB));
    add(hero);
    boolean active =
        Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD)
                != null
            && Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD)
                .startsWith(getPackageName() + "/");
    LinearLayout setup =
        section(
            active ? "键盘已就绪" : enabled() ? "最后一步，切换到豆豆" : "先启用你的键盘",
            "拼音和词库可离线使用。语音取决于系统服务；文字模型可以以后再接。");
    action(
        setup,
        enabled() ? "选择豆豆输入法" : "开启输入法",
        () -> {
          if (enabled())
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
          else startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
    action(setup, "打开练习输入框", () -> startActivity(new Intent(this, EditorActivity.class)));
    add(setup);
    int words = app.store.list("word", 0).size(), people = app.store.list("person", 0).size();
    LinearLayout stats = section("属于你的表达", words + " 个热词    ·    " + people + " 位人物");
    action(stats, "管理热词和文风", () -> go("memory"));
    add(stats);
    caption("本机保存个性化资料。军师只在你点击时读屏；建议只填入输入框，发送始终由你决定。");
  }

  private void memory() {
    LinearLayout tabs = Ui.row(this);
    String[] kinds = {"word", "style", "history", "feedback"}, labels = {"热词", "文风", "历史", "待学习"};
    for (int i = 0; i < kinds.length; i++) {
      final String k = kinds[i];
      TextView b =
          Ui.button(
              this,
              labels[i],
              () -> {
                memoryTab = k;
                render();
              });
      if (k.equals(memoryTab)) b.setTextColor(Ui.ACCENT);
      tabs.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    add(tabs);
    if (memoryTab.equals("word")) {
      caption("热词会进入本地候选和语音整理提示。拼音可填 nihao；不填则仅供整理使用。");
      add(Ui.primary(this, "＋ 添加热词", () -> edit(null, "word", 0)));
    }
    if (memoryTab.equals("style")) {
      caption("明确写下自己喜欢的表达方式。语音整理和军师共用，不会把某个人的偏好套到所有人。");
      add(Ui.primary(this, "＋ 添加文风", () -> edit(null, "style", 0)));
    }
    if (memoryTab.equals("history")) caption("只保留语音结果、主动读屏的文字和你采纳的回复；普通逐字输入不归档。可在设置中关闭。");
    if (memoryTab.equals("feedback")) caption("这里只展示能对应到豆豆上屏文字的修改。输入框清空或消失不算删除，确认后才加入热词；模型分类以后再接。");
    List<Store.Item> items = app.store.list(memoryTab, 0);
    if (items.isEmpty()) caption("还没有记录，从一次真实使用开始。");
    for (Store.Item i : items) {
      LinearLayout c = section(i.title, i.body);
      TextView source =
          Ui.text(
              this,
              Ui.when(i.updated) + " · " + sourceName(i.source) + " · v" + i.revision,
              11,
              Ui.SUB);
      Ui.gap(this, c, 10);
      c.addView(source);
      if (memoryTab.equals("feedback")) {
        action(
            c,
            "确认为热词",
            () -> {
              edit(null, "word", 0, i.aux, "");
            });
        action(c, "忽略", () -> remove(i));
      } else if (memoryTab.equals("history")) {
        action(
            c,
            "复制文字",
            () -> {
              ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("豆豆记录", i.body));
              Ui.toast(this, "已复制");
            });
        action(c, "删除记录", () -> remove(i));
      } else {
        action(c, "编辑", () -> edit(i, i.kind, i.owner));
        action(c, "删除", () -> remove(i));
      }
      add(c);
    }
  }

  private String sourceName(String source) {
    if (source.startsWith("capture:")) return "主动读屏";
    if (source.startsWith("input:")) return "上屏修改";
    if (source.startsWith("voice")) return "语音";
    if (source.startsWith("import:")) return "导入";
    if (source.equals("manual")) return "本人补充";
    return source.equals("candidate") ? "候选学习" : "本机记录";
  }

  private void people() {
    caption("资料保留来源；同名不自动合并。人物记忆不会因为换了聊天 App 就自动互通。");
    List<Store.Item> all = app.store.list("person", 0);
    if (!all.isEmpty()) {
      Graph graph =
          new Graph(
              this,
              all,
              id -> {
                person = id;
                page = "person";
                render();
              });
      add(graph);
    }
    add(Ui.primary(this, "＋ 添加人物", () -> edit(null, "person", 0)));
    for (Store.Item i : all) {
      LinearLayout c = section(i.title, i.body.isEmpty() ? "还没有备注" : i.body);
      action(
          c,
          "查看资料与记忆",
          () -> {
            person = i.id;
            page = "person";
            render();
          });
      add(c);
    }
    if (all.isEmpty()) caption("从一个你希望记住的人开始。关系图会随资料一起生长。");
  }

  private void person() {
    Store.Item p = app.store.get(person);
    if (p == null || p.deleted) {
      go("people");
      return;
    }
    LinearLayout c = section(p.title, p.body);
    Ui.gap(this, c, 8);
    c.addView(
        Ui.text(
            this, "范围：" + (p.aux.isEmpty() ? "手动确认" : p.aux) + " · v" + p.revision, 12, Ui.SUB));
    action(c, "编辑人物", () -> edit(p, "person", 0));
    action(
        c,
        "删除人物和其记忆",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("删除这个人物？")
                .setMessage("人物及所属记忆会一起移出，可立即撤销。")
                .setPositiveButton(
                    "删除",
                    (d, w) -> {
                      long id = p.id;
                      app.store.delete(id);
                      go("people");
                      undo(id);
                    })
                .setNegativeButton("取消", null)
                .show());
    add(c);
    add(Ui.primary(this, "＋ 补充事实、事件或偏好", () -> edit(null, "memory", p.id)));
    for (Store.Item i : app.store.list("memory", p.id)) {
      LinearLayout m = section(i.title, i.body);
      Ui.gap(this, m, 8);
      m.addView(
          Ui.text(
              this,
              (i.aux.isEmpty() ? "fact" : i.aux)
                  + " · "
                  + sourceName(i.source)
                  + " · "
                  + Ui.when(i.updated),
              12,
              Ui.SUB));
      action(m, "修订", () -> edit(i, "memory", p.id));
      action(m, "删除", () -> remove(i));
      add(m);
    }
  }

  private void edit(Store.Item item, String kind, long owner) {
    edit(item, kind, owner, "", "");
  }

  private void edit(Store.Item item, String kind, long owner, String initial, String initialBody) {
    LinearLayout fields = Ui.col(this);
    Ui.pad(this, fields, 20, 14);
    EditText title =
        Ui.edit(
            this, kind.equals("word") ? "热词" : "名称 / 记忆标题", item == null ? initial : item.title);
    fields.addView(title);
    Ui.gap(this, fields, 12);
    EditText body =
        Ui.edit(
            this,
            kind.equals("word") ? "拼音（可选）" : "内容 / 备注",
            item == null ? initialBody : item.body);
    body.setMinLines(kind.equals("word") ? 1 : 3);
    fields.addView(body);
    Ui.gap(this, fields, 12);
    EditText aux =
        Ui.edit(
            this,
            kind.equals("person")
                ? "应用范围（如 com.tencent.mm，可留空）"
                : kind.equals("memory") ? "类型：fact / event / preference / inference" : "备注（可选）",
            item == null ? (kind.equals("memory") ? "fact" : "") : item.aux);
    if (!kind.equals("style")) fields.addView(aux);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(fields);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(item == null ? "添加" : "修订资料")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        String extra = aux.getText().toString().trim();
                        if (kind.equals("memory")
                            && !Arrays.asList("fact", "event", "preference", "inference")
                                .contains(extra)) throw new IllegalArgumentException("请选择四种记忆类型之一");
                        app.store.save(
                            item == null ? 0 : item.id,
                            item == null ? 0 : item.revision,
                            kind,
                            owner,
                            title.getText().toString(),
                            body.getText().toString(),
                            extra,
                            item == null ? "manual" : item.source);
                        dialog.dismiss();
                        render();
                      } catch (Exception e) {
                        Ui.toast(this, e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void remove(Store.Item i) {
    app.store.delete(i.id);
    render();
    undo(i.id);
  }

  private void undo(long id) {
    notice.removeAllViews();
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.text(this, "已删除", 14, Ui.SUB), new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    bar.addView(
        Ui.button(
            this,
            "撤销",
            () -> {
              app.store.restore(id);
              render();
            }));
    notice.addView(bar);
  }

  private void settings() {
    LinearLayout permissions = section("系统权限", "键盘、麦克风与主动截屏分别管理。");
    action(
        permissions,
        "键盘：" + (enabled() ? "已启用" : "未启用"),
        () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
    action(
        permissions,
        "麦克风："
            + (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ? "已允许"
                : "未允许"),
        () -> requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 20));
    action(
        permissions,
        "主动截屏：" + (CaptureService.current != null ? "已开启" : "未开启"),
        () ->
            new AlertDialog.Builder(this)
                .setTitle("只在点军师时读屏")
                .setMessage("用于截取当前屏幕并在本机识字。不后台读屏、不代发消息。系统的辅助功能提示可能更宽泛，豆豆仅申请截屏能力。")
                .setPositiveButton(
                    "去系统设置",
                    (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("取消", null)
                .show());
    add(permissions);
    LinearLayout input = section("输入与语音", "");
    toggle(input, "按键轻振", "haptic", true);
    toggle(input, "学习本地候选排序", "learning", true);
    toggle(input, "保存主动使用历史", "history", true);
    toggle(input, "允许系统在线语音", "onlineVoice", false);
    captionIn(input, "在线语音由手机的系统服务处理音频；关闭时只尝试本地服务。专用 ASR、文风整理可稍后配置。");
    add(input);
    LinearLayout model =
        section(
            "文字模型 · 可稍后配置",
            app.prefs().getString("modelId", "").isEmpty()
                ? "尚未连接；不会生成演示回复。"
                : app.prefs().getString("modelId", ""));
    action(model, "配置服务", this::modelSettings);
    add(model);
    LinearLayout privacy = section("数据与备份", "备份含人物、词库和本机历史，请保存到你信任的位置；不包含模型密钥。");
    action(
        privacy,
        "历史保留：" + app.prefs().getInt("retentionDays", 30) + " 天",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("历史保留天数")
                .setItems(
                    new String[] {"7 天", "30 天", "90 天", "不自动清理"},
                    (d, w) -> {
                      app.prefs()
                          .edit()
                          .putInt("retentionDays", new int[] {7, 30, 90, 0}[w])
                          .apply();
                      app.store.pruneHistory(new int[] {7, 30, 90, 0}[w]);
                      render();
                    })
                .show());
    action(
        privacy,
        "导出本地资料",
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "豆豆输入法-备份.json")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                EXPORT));
    action(
        privacy,
        "导入资料（作为新记录）",
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                IMPORT));
    action(
        privacy,
        "清除全部个性化资料",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("清除全部本地资料？")
                .setMessage("词库、人物、文风、历史和学习数据会永久删除。此操作无法撤销，建议先导出。")
                .setPositiveButton(
                    "清除",
                    (d, w) -> {
                      app.store.clearAll();
                      app.clearDecoderLearning();
                      render();
                      Ui.toast(this, "已清除本地资料");
                    })
                .setNegativeButton("取消", null)
                .show());
    add(privacy);
    caption("豆豆同步尚未接入，当前资料只在本机。不会自动上传聊天或截图。");
    LinearLayout about =
        section(
            "豆豆输入法 0.1.0-alpha.1",
            "拼音：AOSP PinyinIME（Apache 2.0）\n中文 OCR：Google ML Kit 本地模型\n系统 ASR 的可用性因设备而异。");
    action(
        about,
        "开源许可",
        () -> {
          try (InputStream in = getAssets().open("aosp-notice.txt")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bytes = new byte[2048];
            int n;
            while ((n = in.read(bytes)) != -1) b.write(bytes, 0, n);
            new AlertDialog.Builder(this)
                .setTitle("第三方许可")
                .setMessage(b.toString("UTF-8"))
                .setPositiveButton("关闭", null)
                .show();
          } catch (Exception e) {
            Ui.toast(this, "许可文件暂不可读");
          }
        });
    add(about);
  }

  private void captionIn(LinearLayout c, String text) {
    Ui.gap(this, c, 10);
    TextView t = Ui.text(this, text, 12, Ui.SUB);
    t.setLineSpacing(Ui.dp(this, 3), 1);
    c.addView(t);
  }

  private void toggle(LinearLayout c, String title, String key, boolean def) {
    Switch s = new Switch(this);
    s.setText(title);
    s.setTextSize(15);
    s.setTextColor(Ui.INK);
    s.setMinHeight(Ui.dp(this, 56));
    s.setChecked(app.prefs().getBoolean(key, def));
    s.setOnCheckedChangeListener((b, v) -> app.prefs().edit().putBoolean(key, v).apply());
    c.addView(s);
  }

  private void modelSettings() {
    LinearLayout f = Ui.col(this);
    Ui.pad(this, f, 20, 12);
    EditText base = Ui.edit(this, "HTTPS Base URL", app.prefs().getString("modelBase", "")),
        model = Ui.edit(this, "模型 ID", app.prefs().getString("modelId", "")),
        key = Ui.edit(this, "新 API Key（留空保留原值）", "");
    base.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    key.setInputType(129);
    key.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    f.addView(base);
    Ui.gap(this, f, 10);
    f.addView(model);
    Ui.gap(this, f, 10);
    f.addView(key);
    try {
      String saved = new Vault(this).get();
      if (!saved.isEmpty()) captionIn(f, "已保存：" + Vault.hint(saved));
    } catch (Exception e) {
      captionIn(f, "原密钥无法解密，请重新填写");
    }
    Switch protocol = new Switch(this);
    protocol.setText("使用 Anthropic 协议");
    protocol.setTextColor(Ui.INK);
    protocol.setChecked(app.prefs().getBoolean("modelAnthropic", false));
    f.addView(protocol);
    captionIn(f, "保存后，只有主动点整理或生成回复才会发送相关文字；不上传原截图。服务端自行判断额度。");
    ScrollView scroll = new ScrollView(this);
    scroll.addView(f);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("自定义文字模型")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNeutralButton(
                "移除服务",
                (x, w) -> {
                  try {
                    new Vault(this).set("");
                  } catch (Exception ignored) {
                  }
                  app.prefs().edit().remove("modelBase").remove("modelId").apply();
                  render();
                })
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        String b = ModelClient.validateBase(base.getText().toString().trim());
                        if (model.getText().toString().trim().isEmpty())
                          throw new IllegalArgumentException("请填模型 ID");
                        String k = key.getText().toString().trim();
                        if (!k.isEmpty()) new Vault(this).set(k);
                        if (new Vault(this).get().isEmpty())
                          throw new IllegalArgumentException("请填写 API Key");
                        app.prefs()
                            .edit()
                            .putString("modelBase", b)
                            .putString("modelId", model.getText().toString().trim())
                            .putBoolean("modelAnthropic", protocol.isChecked())
                            .apply();
                        d.dismiss();
                        render();
                      } catch (Exception e) {
                        Ui.toast(this, e.getMessage() == null ? "保存失败" : e.getMessage());
                      }
                    }));
    d.show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    android.net.Uri uri = data.getData();
    app.worker.execute(
        () -> {
          try {
            if (request == EXPORT) {
              try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(app.store.exportData().toString(2).getBytes("UTF-8"));
              }
              runOnUiThread(() -> Ui.toast(this, "备份已导出"));
            }
            if (request == IMPORT) {
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                  if (out.size() + n > 5_000_000) throw new IOException("备份超过 5MB");
                  out.write(b, 0, n);
                }
              }
              int count = app.store.importData(new JSONObject(out.toString("UTF-8")));
              runOnUiThread(
                  () -> {
                    render();
                    Ui.toast(this, "已导入 " + count + " 项");
                  });
            }
          } catch (Exception e) {
            runOnUiThread(() -> Ui.toast(this, "操作失败，原有资料保留"));
          }
        });
  }

  private interface PersonTap {
    void select(long id);
  }

  private static final class Graph extends View {
    final List<Store.Item> people;
    final PersonTap callback;
    final Paint paint = new Paint(3);
    final List<PointF> points = new ArrayList<>();

    Graph(Context c, List<Store.Item> p, PersonTap cb) {
      super(c);
      people = p;
      callback = cb;
      setMinimumHeight(Ui.dp(c, 280));
      setContentDescription("人物关系图，下面的列表提供全部人物入口");
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      points.clear();
      float cx = getWidth() / 2f,
          cy = getHeight() / 2f,
          r = Math.min(getWidth(), getHeight()) * .34f;
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setStrokeWidth(Ui.dp(getContext(), 1));
      int n = Math.min(8, people.size());
      for (int i = 0; i < n; i++) {
        double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
        float x = cx + (float) Math.cos(angle) * r, y = cy + (float) Math.sin(angle) * r;
        points.add(new PointF(x, y));
        paint.setColor(Ui.LINE);
        c.drawLine(cx, cy, x, y, paint);
      }
      node(c, cx, cy, "我", true);
      for (int i = 0; i < n; i++) {
        PointF p = points.get(i);
        String name = people.get(i).title;
        node(c, p.x, p.y, name.length() > 4 ? name.substring(0, 4) : name, false);
      }
    }

    void node(Canvas c, float x, float y, String name, boolean me) {
      paint.setColor(me ? Ui.ACCENT : Ui.KEY);
      c.drawCircle(x, y, Ui.dp(getContext(), 29), paint);
      paint.setTextSize(Ui.dp(getContext(), 13));
      paint.setColor(me ? Ui.BG : Ui.INK);
      c.drawText(name, x, y + Ui.dp(getContext(), 5), paint);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_UP) {
        for (int i = 0; i < points.size(); i++)
          if (Math.hypot(e.getX() - points.get(i).x, e.getY() - points.get(i).y)
              < Ui.dp(getContext(), 34)) {
            performClick();
            callback.select(people.get(i).id);
            break;
          }
        return true;
      }
      return true;
    }

    @Override
    public boolean performClick() {
      return super.performClick();
    }
  }
}
