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
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
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
    toggle(input, "ASR 基础规整", "asrPolish", true);
    toggle(input, "按我的口吻整理", "personalRewrite", false);
    captionIn(input, "语音实时识别会携带少量热词与近期文字；个人整理开启后，再用文字模型按文风处理。");
    add(input);
    LinearLayout account =
        section("豆豆账户", app.cloud.loggedIn() ? app.cloud.phoneHint() : "登录后使用语音和军师；普通输入离线可用。");
    action(account, app.cloud.loggedIn() ? "账户、权益与云同步" : "手机号登录", () -> nav.open("account", 0));
    add(account);
    LinearLayout intelligence = section("军师与学习", "截图不进入历史或云备份；文字历史和学习候选留在本机。");
    toggle(intelligence, "军师使用图文理解", "advisorVision", false);
    captionIn(intelligence, "关闭时仅上传本地 OCR 文字；开启时，把本次截图一并交给图文模型，帮助理解气泡、表情与聊天结构。");
    toggle(intelligence, "定期整理新增历史", "cloudLearning", false);
    action(
        intelligence,
        "整理新增历史",
        () -> {
          Ui.toast(activity, "开始整理新增文字，完成后可在待学习里确认");
          app.worker.execute(
              () -> {
                String result;
                try {
                  result = new LearningBatch(app).run();
                } catch (Exception e) {
                  result = e.getMessage() == null ? "整理失败，新增历史保留" : e.getMessage();
                }
                final String message = result;
                activity.runOnUiThread(() -> Ui.toast(activity, message));
              });
        });
    captionIn(intelligence, "开启后约每五小时检查新增文字，分批处理；没有新增不调用。候选须经确认，不覆盖手动修订。");
    add(intelligence);
    LinearLayout privacy = section("数据与备份", "备份含人物、词库和本机历史，请保存到你信任的位置；不包含登录凭据和同步密钥。");
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
    caption("本地资料随时可导出。云同步在账户页按权益开启，和模型分析分别管理。");
    LinearLayout about =
        section(
            "豆豆输入法 " + BuildConfig.VERSION_NAME,
            "拼音：AOSP PinyinIME（Apache 2.0）\n"
                + "中文 OCR：Google ML Kit 本地模型\n"
                + "语音：Qwen Message，经豆豆 Cloud 代理。");
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
