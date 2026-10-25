package app.doudou.ime.settings;

import app.doudou.ime.design.Skin;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.account.AccountSummary;
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.design.Ui;
import app.doudou.ime.setup.PermissionActions;
import java.io.*;

/** Essential personal preferences and permission status, directly visible on My. */
public final class SettingsScreen extends Screen {
  private String page = "settings";

  public SettingsScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  public void page(String value) {
    page = value;
  }

  @Override
  protected void draw() {
    if (page.equals("about")) {
      about();
      return;
    }
    add(AccountSummary.create(activity, app, () -> nav.open("account", 0)));
    LinearLayout appearance = Ui.card(activity);
    appearance.addView(Ui.text(activity, "皮肤", 16, Ui.ink()));
    Ui.gap(activity, appearance, 12);
    LinearLayout skins = Ui.row(activity);
    for (boolean light : new boolean[] {true, false}) {
      boolean selected = light == Skin.light();
      TextView choice = Ui.button(activity, light ? "亮色" : "暗色", () -> {
        if (light == Skin.light()) return;
        Skin.select(activity, light);
        activity.recreate();
      });
      choice.setSelected(selected);
      choice.setTextColor(selected ? Ui.appAccent() : Ui.ink());
      choice.setBackground(Ui.pressable(activity, selected ? Ui.appTint() : Ui.surface(), 14));
      choice.setContentDescription((light ? "亮色皮肤" : "暗色皮肤") + (selected ? "，已选择" : ""));
      LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, Ui.dp(activity, 52), 1);
      if (!light) size.leftMargin = Ui.dp(activity, 10);
      skins.addView(choice, size);
    }
    appearance.addView(skins); add(appearance);
    LinearLayout keys = Ui.card(activity);
    Switch vibration = new Switch(activity);
    vibration.setText("按键轻振");
    vibration.setTextSize(16);
    vibration.setTextColor(Ui.ink());
    vibration.setMinHeight(Ui.dp(activity, 52));
    vibration.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.appAccent()));
    vibration.setChecked(app.prefs().getBoolean("haptic", true));
    vibration.setOnCheckedChangeListener(
        (v, on) -> app.prefs().edit().putBoolean("haptic", on).apply());
    keys.addView(vibration);
    keys.addView(Ui.divider(activity));
    int days = app.prefs().getInt("retentionDays", 30);
    keys.addView(
        Ui.cell(
            activity,
            "",
            "历史保留",
            "",
            days == 0 ? "永久" : days + "天",
            () ->
                new AlertDialog.Builder(activity)
                    .setTitle("历史保留")
                    .setItems(
                        new String[] {"7天", "30天", "90天", "永久"},
                        (d, w) -> {
                          int value = new int[] {7, 30, 90, 0}[w];
                          app.prefs().edit().putInt("retentionDays", value).apply();
                          app.histories.prune(value);
                          nav.refresh();
                        })
                    .show()));
    add(keys);
    LinearLayout permissions = Ui.card(activity);
    permissions.addView(
        Ui.cell(
            activity,
            "工具",
            "豆豆键盘",
            "",
            enabled() ? "已开启" : "去开启",
            () -> activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
    permissions.addView(Ui.divider(activity));
    permissions.addView(
        Ui.cell(
            activity,
            "语音",
            "麦克风",
            "",
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ? "已允许"
                : "去允许",
            () -> PermissionActions.microphone(activity, app)));
    permissions.addView(Ui.divider(activity));
    permissions.addView(
        Ui.cell(
            activity,
            "军师",
            "主动截屏",
            "",
            CaptureService.current != null ? "已开启" : "去开启",
            () ->
                new AlertDialog.Builder(activity)
                    .setTitle("允许帮我回看聊天")
                    .setMessage(
                        "点「帮我回」时截取当前画面，并向上翻看一屏。截图上传生成回复，历史保存在手机。不会读取其他聊天，也不会替你发送。自动翻页后会尝试回到原位置。")
                    .setPositiveButton(
                        "去开启",
                        (d, w) ->
                            activity.startActivity(
                                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("取消", null)
                    .show()));
    if (android.os.Build.VERSION.SDK_INT >= 33
        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      permissions.addView(Ui.divider(activity));
      permissions.addView(
          Ui.cell(
              activity, "语音", "录音通知", "", "去允许", () -> PermissionActions.notifications(activity)));
    }
    add(permissions);
    LinearLayout about = Ui.card(activity);
    about.addView(
        Ui.cell(
            activity,
            "闪光",
            "关于豆豆",
            "",
            BuildConfig.VERSION_NAME.split("-", 2)[0],
            () -> nav.open("about", 0)));
    add(about);
  }

  private void about() {
    LinearLayout c = section("豆豆输入法", BuildConfig.VERSION_NAME);
    action(
        c,
        "版本与下载",
        () ->
            activity.startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://github.com/Xiamu-ssr/personal-keyboard/releases"))));
    action(
        c,
        "开源许可",
        () -> {
          StringBuilder notices = new StringBuilder();
          for (String file : new String[] {"aosp-notice.txt", "wanxiang-notice.txt", "licenses/androidx-emoji.txt", "licenses/noto-emoji.txt"})
            try (InputStream in = activity.getAssets().open(file)) {
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              byte[] b = new byte[4096];
              int n;
              while ((n = in.read(b)) != -1) out.write(b, 0, n);
              notices.append(out.toString("UTF-8")).append("\n\n");
            } catch (Exception ignored) {
            }
          new AlertDialog.Builder(activity)
              .setTitle("第三方许可")
              .setMessage(notices)
              .setPositiveButton("关闭", null)
              .show();
        });
    add(c);
  }
}
