package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.advisor.PaletteStore;
import app.doudou.ime.app.*;
import java.io.*;
import java.util.*;
import org.junit.Test;

/** Real management controls and transaction results; seed rows are removed after the run. */
public final class ManagementFlowTest {
  private UiAutomation ui;
  private MainActivity activity;

  @Test
  public void permanentlyDeniedMicrophoneOffersAnAppSettingsRoute() throws Exception {
    org.junit.Assume.assumeTrue(
        "Isolated emulator only", android.os.Build.HARDWARE.equals("ranchu"));
    android.app.Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    KeyboardApp app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    org.junit.Assume.assumeTrue(
        "External denied-permission fixture",
        app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            == android.content.pm.PackageManager.PERMISSION_DENIED);
    boolean setup = app.prefs().getBoolean("setupComplete", false),
        asked = app.prefs().getBoolean("microphoneRequested", false);
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    windows();
    android.app.Instrumentation.ActivityMonitor monitor = null;
    try {
      app.prefs()
          .edit()
          .putBoolean("setupComplete", false)
          .putBoolean("microphoneRequested", true)
          .commit();
      activity =
          (MainActivity)
              ins.startActivitySync(
                  new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("去系统设置允许麦克风");
      shot("onboarding-denied-microphone");
      android.content.IntentFilter filter =
          new android.content.IntentFilter(
              android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      filter.addDataScheme("package");
      monitor =
          ins.addMonitor(
              filter,
              new android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
              true);
      click("去系统设置允许麦克风");
      assertEquals(
          "Permission recovery opens app settings instead of a dead request", 1, monitor.getHits());
    } finally {
      if (monitor != null) ins.removeMonitor(monitor);
      if (activity != null) ins.runOnMainSync(activity::finish);
      shell("pm clear-permission-flags app.doudou.ime android.permission.RECORD_AUDIO user-fixed");
      shell("pm grant app.doudou.ime android.permission.RECORD_AUDIO");
      app.prefs()
          .edit()
          .putBoolean("setupComplete", setup)
          .putBoolean("microphoneRequested", asked)
          .commit();
    }
  }

  private void shell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd = ui.executeShellCommand(command);
        FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
      byte[] buffer = new byte[1024];
      while (input.read(buffer) != -1) {}
    }
  }

  private void windows() {
    android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
  }

  @Test
  public void firstUseCanVisitLoginAndReturnWithoutAPermissionWall() throws Exception {
    org.junit.Assume.assumeTrue(
        "Isolated emulator only", android.os.Build.HARDWARE.equals("ranchu"));
    android.app.Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    KeyboardApp app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    org.junit.Assume.assumeFalse(app.cloud.loggedIn());
    boolean existed = app.prefs().contains("setupComplete"),
        original = app.prefs().getBoolean("setupComplete", false);
    app.prefs().edit().putBoolean("setupComplete", false).commit();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    windows();
    try {
      activity =
          (MainActivity)
              ins.startActivitySync(
                  new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      await("欢迎用豆豆");
      assertNull(node("已就绪 · 在键盘点「帮我回」。"));
      shot("onboarding");
      click("登录或注册");
      await("欢迎回来");
      ins.runOnMainSync(activity::onBackPressed);
      await("欢迎用豆豆");
      for (int i = 0; i < 4 && node("开始使用") == null; i++) {
        for (AccessibilityWindowInfo w : ui.getWindows()) scroll(w.getRoot());
        SystemClock.sleep(180);
      }
      shot("onboarding-ready");
      click("开始使用");
      await("试试豆豆");
      assertTrue(app.prefs().getBoolean("setupComplete", false));
    } finally {
      if (activity != null) ins.runOnMainSync(activity::finish);
      android.content.SharedPreferences.Editor restore = app.prefs().edit();
      if (existed) restore.putBoolean("setupComplete", original);
      else restore.remove("setupComplete");
      restore.commit();
    }
  }

  private boolean scroll(AccessibilityNodeInfo node) {
    if (node == null) return false;
    if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
      return true;
    for (int i = 0; i < node.getChildCount(); i++) if (scroll(node.getChild(i))) return true;
    return false;
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
    if (n == null) return null;
    if (n.isVisibleToUser()
        && (label.contentEquals(n.getText() == null ? "" : n.getText())
            || label.contentEquals(
                n.getContentDescription() == null ? "" : n.getContentDescription()))) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo child = find(n.getChild(i), label);
      if (child != null) return child;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String label) {
    for (AccessibilityWindowInfo w : ui.getWindows()) {
      AccessibilityNodeInfo n = find(w.getRoot(), label);
      if (n != null) return n;
    }
    return null;
  }

  private AccessibilityNodeInfo await(String label) {
    long deadline = SystemClock.uptimeMillis() + 6000;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo n = node(label);
      if (n != null) return n;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Missing app control: " + label);
  }

  private void click(String label) {
    AccessibilityNodeInfo n = await(label);
    while (n != null && !n.isClickable()) n = n.getParent();
    assertNotNull(label, n);
    assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    SystemClock.sleep(150);
  }

  private void set(String label, String text) {
    Bundle b = new Bundle();
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
    assertTrue(await(label).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b));
    SystemClock.sleep(100);
  }

  private void shot(String name) throws Exception {
    SystemClock.sleep(200);
    Bitmap b = ui.takeScreenshot();
    assertNotNull(b);
    try (FileOutputStream out =
        new FileOutputStream(new File(activity.getCacheDir(), "test-native-" + name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally {
      b.recycle();
    }
  }

  @Test
  public void managementReadsCanonicalCacheAndSearchesWithoutLocalMutations() throws Exception {
    android.app.Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo service = ui.getServiceInfo();
    service.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(service);
    activity =
        (MainActivity)
            ins.startActivitySync(
                new Intent(ins.getTargetContext(), MainActivity.class)
                    .putExtra("page", "home")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    KeyboardApp app = (KeyboardApp) activity.getApplication();
    org.json.JSONObject original = app.store.cache(app.cloud.userId());
    long originalVersion = app.store.cacheVersion(app.cloud.userId());
    String personId = java.util.UUID.randomUUID().toString();
    org.json.JSONObject fixture =
        new org.json.JSONObject()
            .put(
                "hotwords",
                new org.json.JSONArray()
                    .put(
                        new org.json.JSONObject()
                            .put("id", java.util.UUID.randomUUID().toString())
                            .put("text", "豆豆输入法")
                            .put("variants", new org.json.JSONArray())))
            .put(
                "styles",
                new org.json.JSONArray()
                    .put(
                        new org.json.JSONObject()
                            .put("id", java.util.UUID.randomUUID().toString())
                            .put("scene", "自然一点")
                            .put("rules", new org.json.JSONArray().put("短句，保留自己的意思。"))
                            .put("examples", new org.json.JSONArray())))
            .put(
                "people",
                new org.json.JSONArray()
                    .put(
                        new org.json.JSONObject()
                            .put("id", personId)
                            .put("name", "林间")
                            .put("aliases", new org.json.JSONArray().put("小林"))
                            .put(
                                "facts",
                                new org.json.JSONArray()
                                    .put(
                                        new org.json.JSONObject()
                                            .put("id", java.util.UUID.randomUUID().toString())
                                            .put("key", "周末约球")
                                            .put("value", "周六下午有空；这是本机验收资料。")
                                            .put("scope", "验收")))
                            .put("relations", new org.json.JSONArray())))
            .put("candidates", new org.json.JSONArray());
    app.store.cache(app.cloud.userId(), 1, fixture);
    try {
      click("记忆");
      click("热词");
      await("豆豆输入法");
      set("搜索这份记忆", "没有这个热词");
      await("没有找到");
      set("没有这个热词", "");
      await("豆豆输入法");
      shot("memory");
      click("文风");
      await("自然一点");
      shot("style");
      click("人物");
      await("林间");
      shot("people");
      click("林间");
      await("周末约球");
      shot("person");
      click("我的");
      await("主动截屏");
      shot("settings");
      click("输入");
      await("试试豆豆");
      shot("home");
    } finally {
      app.store.cache(app.cloud.userId(), originalVersion, original);
      ins.runOnMainSync(() -> activity.finish());
    }
  }

  @Test
  public void accountIsOptionalAndInvitationRegistrationNeedsNoSms() throws Exception {
    android.app.Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    activity =
        (MainActivity)
            ins.startActivitySync(
                new Intent(ins.getTargetContext(), MainActivity.class)
                    .putExtra("page", "account")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      await("欢迎回来");
      click("受邀注册");
      await("创建豆豆账户");
      await("邀请码");
      click("忘记密码");
      await("找回账户");
      await("恢复码");
      click("已有账号");
      await("欢迎回来");
      shot("account");
      click("返回");
      if (((KeyboardApp) activity.getApplication()).prefs().getBoolean("setupComplete", false))
        await("主动截屏");
      else await("欢迎用豆豆");
    } finally {
      ins.runOnMainSync(() -> activity.finish());
    }
  }

  @Test
  public void livePaletteEditAndCanonicalMemoryAreImmediatelyVisible() throws Exception {
    org.junit.Assume.assumeTrue(
        "Explicit owned emulator live fixture",
        android.os.Build.HARDWARE.equals("ranchu")
            && "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    android.app.Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    KeyboardApp app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    org.json.JSONObject qa =
        new org.json.JSONObject(
            new String(
                java.nio.file.Files.readAllBytes(
                    new File(app.getCacheDir(), "product-qa.json").toPath()),
                "UTF-8"));
    app.cloud.login(qa.getString("handle"), qa.getString("password"));
    app.personalization.activate();
    app.personalization.sync();
    PaletteStore store = new PaletteStore(app);
    org.json.JSONObject original = store.sync();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    windows();
    activity =
        (MainActivity)
            ins.startActivitySync(
                new Intent(app, MainActivity.class)
                    .putExtra("page", "home")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      await("编辑自然");
      await("我的文风");
      shot("home");
      click("编辑自然");
      await("编辑表达");
      click("参考搭配");
      click("俏皮");
      await("编辑表达");
      set(original.getJSONArray("slots").getJSONObject(0).getString("name"), "随口说");
      click("保存");
      await("编辑随口说");
      org.json.JSONObject changed = store.sync();
      assertEquals("随口说", changed.getJSONArray("slots").getJSONObject(0).getString("name"));
      assertTrue(changed.getJSONArray("slots").getJSONObject(0).getJSONObject("attributes").getJSONArray("rhetoric").toString().contains("humor"));
      shot("palette-edited");
      store.save(app.cloud.userId(), changed, 0, original.getJSONArray("slots").getJSONObject(0).getString("name"), original.getJSONArray("slots").getJSONObject(0).getJSONObject("attributes"));
      click("人物");
      await("沐星");
      click("沐星");
      await("北辰实验室");
      shot("person-live");
      click("记忆");
      click("文风");
      await("工作沟通/即时消息");
      shot("style-live");
    } finally {
      if (activity != null) ins.runOnMainSync(activity::finish);
      app.cloud.logout();
      app.personalization.activate();
    }
  }
}
