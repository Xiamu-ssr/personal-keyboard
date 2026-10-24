package app.doudou.ime.settings;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.account.AccountSummary;
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.setup.PermissionActions;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.LearningBatch;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SettingsScreen extends Screen {
  private static final int EXPORT = 100, IMPORT = 101;

  public SettingsScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  private String page = "settings";

  public void page(String value) { page = value; }

  @Override
  protected void draw() {
    if (page.equals("input-settings")) { input(); return; }
    if (page.equals("data-settings")) { data(); return; }
    if (page.equals("permissions")) { permissions(); return; }
    if (page.equals("about")) { about(); return; }
    add(AccountSummary.create(activity, app, () -> nav.open("account", 0)));
    LinearLayout options = Ui.card(activity);
    options.addView(Ui.cell(activity, "语音", "语音与键盘", "", "", () -> nav.open("input-settings", 0)));
    options.addView(Ui.divider(activity));
    options.addView(Ui.cell(activity, "隐私", "数据与隐私", "", "", () -> nav.open("data-settings", 0)));
    options.addView(Ui.divider(activity));
    options.addView(Ui.cell(activity, "设置", "系统权限", "", "", () -> nav.open("permissions", 0)));
    add(options);
    LinearLayout support = Ui.card(activity);
    support.addView(Ui.cell(activity, "闪光", "关于豆豆", "", BuildConfig.VERSION_NAME.split("-", 2)[0], () -> nav.open("about", 0)));
    add(support);
  }

  private void input() {
    LinearLayout voice = section("说话的方式", "按住空格说话，松手结束；左上撤回，右上换行。");
    toggle(voice, "按我的口吻整理", "personalRewrite", false);
    captionIn(voice, "识别后再按你的文风整理，原意保持不变。");
    add(voice);
    LinearLayout keys = section("键盘", "");
    toggle(keys, "按键轻振", "haptic", true);
    toggle(keys, "记住常用候选", "learning", true);
    add(keys);
  }

  private void permissions() {
    LinearLayout c = section("按需开启", "语音需要麦克风；军师需要主动截屏。");
    c.addView(Ui.cell(activity, "工具", "豆豆键盘", "", enabled() ? "已启用" : "去开启", () -> activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
    c.addView(Ui.divider(activity));
    c.addView(Ui.cell(activity, "语音", "麦克风", "", activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ? "已允许" : "去允许", () -> PermissionActions.microphone(activity,app)));
    c.addView(Ui.divider(activity));
    c.addView(Ui.cell(activity, "军师", "主动截屏", "只在你点「帮我回」时截图", CaptureService.current != null ? "已开启" : "去开启", () -> new AlertDialog.Builder(activity)
      .setTitle("允许军师看当前屏幕")
      .setMessage("点「帮我回」时截图；点「多看一屏」时翻到较早聊天再截图，上传用于生成回复。开启历史后保存在手机，可随时删除。")
      .setPositiveButton("去开启", (d, w) -> activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
      .setNegativeButton("取消", null).show()));
    add(c);
  }

  private void data() {
    LinearLayout history = section("本机历史", "文字和军师截图保存在手机。");
    toggle(history, "保留使用历史", "history", true);
    int days = app.prefs().getInt("retentionDays", 30);
    action(history, "自动清理 · " + (days == 0 ? "不自动清理" : days + " 天"), () -> new AlertDialog.Builder(activity)
      .setTitle("历史保留时长").setItems(new String[] {"7 天", "30 天", "90 天", "不自动清理"}, (d, w) -> {
        int value = new int[] {7, 30, 90, 0}[w];
        app.prefs().edit().putInt("retentionDays", value).apply();
        app.histories.prune(value); nav.refresh();
      }).show());
    action(history, "查看历史", () -> nav.open("history", 0));
    add(history);
    LinearLayout memory = section("个人记忆", "热词、文风和人物随账户同步，手机保留一份缓存。");
    action(memory, "立即同步", () -> {
      Ui.toast(activity, "正在同步");
      app.worker.execute(() -> {
        String result;
        try { result = new LearningBatch(app).run(); }
        catch (Exception e) { result = "暂时无法同步，稍后会自动重试"; }
        final String message = result;
        activity.runOnUiThread(() -> Ui.toast(activity, message));
      });
    });
    add(memory);
    LinearLayout backup = section("备份与恢复", "备份包含本机历史和截图，请妥善保存。");
    action(backup, "导出本地资料", () -> activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "豆豆输入法-备份.json").addCategory(Intent.CATEGORY_OPENABLE), EXPORT));
    action(backup, "从备份恢复", () -> activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), IMPORT));
    add(backup);
    TextView clear = Ui.button(activity, "清除本机历史", () -> new AlertDialog.Builder(activity)
      .setTitle("清除本机历史和截图？")
      .setMessage("本机历史、截图和候选排序学习将删除；云端个人记忆保留。建议先导出备份。")
      .setPositiveButton("清除", (d, w) -> { app.histories.clear(); app.store.clearLocalLearning(); app.clearDecoderLearning(); nav.refresh(); Ui.toast(activity, "已清除本机历史"); })
      .setNegativeButton("取消", null).show());
    clear.setTextColor(Ui.RED); add(clear);
  }

  private void about() {
    LinearLayout about = section("豆豆输入法", BuildConfig.VERSION_NAME);
    action(about, "版本与下载", () -> activity.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Xiamu-ssr/personal-keyboard/releases"))));
    action(about, "开源许可", () -> {
      try (InputStream in = activity.getAssets().open("aosp-notice.txt")) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] bytes = new byte[2048]; int n;
        while ((n = in.read(bytes)) != -1) b.write(bytes, 0, n);
        new AlertDialog.Builder(activity).setTitle("第三方许可").setMessage(b.toString("UTF-8")).setPositiveButton("关闭", null).show();
      } catch (Exception e) { Ui.toast(activity, "许可文件暂不可读"); }
    });
    add(about);
  }

  private void toggle(LinearLayout c, String title, String key, boolean def) {
    Switch s = new Switch(activity);
    s.setText(title);
    s.setTextSize(15);
    s.setTextColor(Ui.INK);
    s.setMinHeight(Ui.dp(activity, 52));
    s.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
    s.setChecked(app.prefs().getBoolean(key, def));
    s.setOnCheckedChangeListener((b, v) -> app.prefs().edit().putBoolean(key, v).apply());
    c.addView(s);
  }

  @Override
  public void result(int request, int result, Intent data) {
    if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
    android.net.Uri uri = data.getData();
    final String account = app.cloud.userId();
    app.worker.execute(
        () -> {
          try {
            if (request == EXPORT) {
              try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                out.write(
                    app.histories
                        .backup(32L * 1024 * 1024, account)
                        .put("personalMemory", app.store.cache(account))
                        .toString(2)
                        .getBytes("UTF-8"));
              }
              activity.runOnUiThread(() -> Ui.toast(activity, "备份已导出"));
            }
            if (request == IMPORT) {
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                  if (out.size() + n > 50_000_000) throw new IOException("备份超过50MB");
                  out.write(b, 0, n);
                }
              }
              JSONObject backup = new JSONObject(out.toString("UTF-8"));
              if (backup.optInt("schema") == 2) app.histories.migrateLegacy(backup, account);
              else app.histories.restore(backup, account);
              activity.runOnUiThread(
                  () -> {
                    nav.refresh();
                    Ui.toast(activity, "历史已恢复，已有记录保持");
                  });
            }
          } catch (Exception e) {
            activity.runOnUiThread(() -> Ui.toast(activity, "操作失败，原有资料保留"));
          }
        });
  }
}
