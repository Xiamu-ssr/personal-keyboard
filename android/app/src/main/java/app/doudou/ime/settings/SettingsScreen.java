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
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.design.Ui;
import app.doudou.ime.model.TextModelClient;
import app.doudou.ime.security.Vault;
import java.io.*;
import java.util.*;
import org.json.*;

public final class SettingsScreen extends Screen {
  private static final int EXPORT = 100, IMPORT = 101;

  public SettingsScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  @Override
  protected void draw() {
    LinearLayout permissions = section("系统权限", "键盘、麦克风与主动截屏分别管理。");
    action(
        permissions,
        "键盘：" + (enabled() ? "已启用" : "未启用"),
        () -> activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
    action(
        permissions,
        "麦克风："
            + (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ? "已允许"
                : "未允许"),
        () -> activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 20));
    action(
        permissions,
        "主动截屏：" + (CaptureService.current != null ? "已开启" : "未开启"),
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("只在点军师时读屏")
                .setMessage("用于截取当前屏幕并在本机识字。不后台读屏、不代发消息。系统的辅助功能提示可能更宽泛，豆豆仅申请截屏能力。")
                .setPositiveButton(
                    "去系统设置",
                    (d, w) ->
                        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
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
            new AlertDialog.Builder(activity)
                .setTitle("历史保留天数")
                .setItems(
                    new String[] {"7 天", "30 天", "90 天", "不自动清理"},
                    (d, w) -> {
                      app.prefs()
                          .edit()
                          .putInt("retentionDays", new int[] {7, 30, 90, 0}[w])
                          .apply();
                      app.store.pruneHistory(new int[] {7, 30, 90, 0}[w]);
                      nav.refresh();
                    })
                .show());
    action(
        privacy,
        "导出本地资料",
        () ->
            activity.startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "豆豆输入法-备份.json")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                EXPORT));
    action(
        privacy,
        "导入资料（作为新记录）",
        () ->
            activity.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                IMPORT));
    action(
        privacy,
        "清除全部个性化资料",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("清除全部本地资料？")
                .setMessage("词库、人物、文风、历史和学习数据会永久删除。此操作无法撤销，建议先导出。")
                .setPositiveButton(
                    "清除",
                    (d, w) -> {
                      app.store.clearAll();
                      app.clearDecoderLearning();
                      nav.refresh();
                      Ui.toast(activity, "已清除本地资料");
                    })
                .setNegativeButton("取消", null)
                .show());
    add(privacy);
    caption("豆豆同步尚未接入，当前资料只在本机。不会自动上传聊天或截图。");
    LinearLayout about =
        section(
            "豆豆输入法 0.2.0-alpha.1",
            "拼音：AOSP PinyinIME（Apache 2.0）\n中文 OCR：Google ML Kit 本地模型\n系统 ASR 的可用性因设备而异。");
    action(
        about,
        "开源许可",
        () -> {
          try (InputStream in = activity.getAssets().open("aosp-notice.txt")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bytes = new byte[2048];
            int n;
            while ((n = in.read(bytes)) != -1) b.write(bytes, 0, n);
            new AlertDialog.Builder(activity)
                .setTitle("第三方许可")
                .setMessage(b.toString("UTF-8"))
                .setPositiveButton("关闭", null)
                .show();
          } catch (Exception e) {
            Ui.toast(activity, "许可文件暂不可读");
          }
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

  private void modelSettings() {
    LinearLayout f = Ui.col(activity);
    Ui.pad(activity, f, 20, 12);
    EditText base = Ui.edit(activity, "HTTPS Base URL", app.prefs().getString("modelBase", "")),
        model = Ui.edit(activity, "模型 ID", app.prefs().getString("modelId", "")),
        key = Ui.edit(activity, "新 API Key（留空保留原值）", "");
    base.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    key.setInputType(129);
    key.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    f.addView(base);
    Ui.gap(activity, f, 10);
    f.addView(model);
    Ui.gap(activity, f, 10);
    f.addView(key);
    try {
      String saved = new Vault(activity).get();
      if (!saved.isEmpty()) captionIn(f, "已保存：" + Vault.hint(saved));
    } catch (Exception e) {
      captionIn(f, "原密钥无法解密，请重新填写");
    }
    Switch protocol = new Switch(activity);
    protocol.setText("使用 Anthropic 协议");
    protocol.setTextColor(Ui.INK);
    protocol.setChecked(app.prefs().getBoolean("modelAnthropic", false));
    f.addView(protocol);
    captionIn(f, "保存后，只有主动点整理或生成回复才会发送相关文字；不上传原截图。服务端自行判断额度。");
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(f);
    AlertDialog d =
        new AlertDialog.Builder(activity)
            .setTitle("自定义文字模型")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNeutralButton(
                "移除服务",
                (x, w) -> {
                  try {
                    new Vault(activity).set("");
                  } catch (Exception ignored) {
                  }
                  app.prefs().edit().remove("modelBase").remove("modelId").apply();
                  nav.refresh();
                })
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        String b = TextModelClient.validateBase(base.getText().toString().trim());
                        if (model.getText().toString().trim().isEmpty())
                          throw new IllegalArgumentException("请填模型 ID");
                        String k = key.getText().toString().trim();
                        if (!k.isEmpty()) new Vault(activity).set(k);
                        if (new Vault(activity).get().isEmpty())
                          throw new IllegalArgumentException("请填写 API Key");
                        app.prefs()
                            .edit()
                            .putString("modelBase", b)
                            .putString("modelId", model.getText().toString().trim())
                            .putBoolean("modelAnthropic", protocol.isChecked())
                            .apply();
                        d.dismiss();
                        nav.refresh();
                      } catch (Exception e) {
                        Ui.toast(activity, e.getMessage() == null ? "保存失败" : e.getMessage());
                      }
                    }));
    d.show();
  }

  @Override
  public void result(int request, int result, Intent data) {
    if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
    android.net.Uri uri = data.getData();
    app.worker.execute(
        () -> {
          try {
            if (request == EXPORT) {
              try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                out.write(app.store.exportData().toString(2).getBytes("UTF-8"));
              }
              activity.runOnUiThread(() -> Ui.toast(activity, "备份已导出"));
            }
            if (request == IMPORT) {
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) != -1) {
                  if (out.size() + n > 5_000_000) throw new IOException("备份超过 5MB");
                  out.write(b, 0, n);
                }
              }
              int count = app.store.importData(new JSONObject(out.toString("UTF-8")));
              activity.runOnUiThread(
                  () -> {
                    nav.refresh();
                    Ui.toast(activity, "已导入 " + count + " 项");
                  });
            }
          } catch (Exception e) {
            activity.runOnUiThread(() -> Ui.toast(activity, "操作失败，原有资料保留"));
          }
        });
  }
}
