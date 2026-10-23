package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import java.io.*;
import java.util.*;
import org.junit.Test;

/** Real management controls and transaction results; seed rows are removed after the run. */
public final class ManagementFlowTest {
  private UiAutomation ui;
  private MainActivity activity;

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
      await("系统权限");
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
      await("系统权限");
    } finally {
      ins.runOnMainSync(() -> activity.finish());
    }
  }
}
