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
  public void managementNavigationRevisionSearchAndUndo() throws Exception {
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
    List<Long> rows = new ArrayList<>();
    long word = app.store.save(0, 0, "word", 0, "豆豆输入法", "doudou", "", "manual");
    rows.add(word);
    rows.add(app.store.save(0, 0, "word", 0, "六六六", "lll", "", "manual"));
    rows.add(app.store.save(0, 0, "word", 0, "林间", "linjian", "", "manual"));
    rows.add(app.store.save(0, 0, "style", 0, "自然一点", "短句，少用感叹号，保留自己的意思。", "", "manual"));
    long person = app.store.save(0, 0, "person", 0, "林间", "朋友 · 本机验收示例", "", "manual");
    rows.add(person);
    rows.add(app.store.save(0, 0, "memory", person, "周末约球", "周六下午有空；这是验收示例资料。", "event", "manual"));
    try {
      click("记忆");
      await("豆豆输入法");
      click("豆豆输入法");
      set("豆豆输入法", "豆豆词库");
      click("保存");
      await("豆豆词库");
      assertEquals("豆豆词库", app.store.get(word).title);
      AccessibilityNodeInfo row = await("豆豆词库");
      while (row != null && !row.isLongClickable()) row = row.getParent();
      assertNotNull(row);
      assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK));
      click("删除");
      assertTrue(app.store.get(word).deleted);
      click("撤销");
      await("豆豆词库");
      assertFalse(app.store.get(word).deleted);
      set("搜索这份记忆", "没有这个热词");
      await("没有找到");
      set("没有这个热词", "");
      await("豆豆词库");
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
      click("设置");
      await("系统权限");
      shot("settings");
      click("输入");
      await("试试豆豆");
      shot("home");
    } finally {
      for (long id : rows) app.store.delete(id);
      ins.runOnMainSync(() -> activity.finish());
    }
  }

  @Test
  public void accountIsOptionalAndShowsUnconfiguredSmsHonestly() throws Exception {
    org.junit.Assume.assumeFalse(
        new app.doudou.ime.cloud.CloudClient(
                InstrumentationRegistry.getInstrumentation().getTargetContext())
            .publicRequest("/healthz", null)
            .getJSONObject("readiness")
            .getBoolean("sms"));
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
      await("手机号");
      set("手机号", "13800001234");
      click("获取验证码");
      await("短信登录尚待企业资质、签名和模板配置；本地输入仍可使用");
      set("13800001234", "");
      shot("account");
      click("返回");
      await("系统权限");
    } finally {
      ins.runOnMainSync(() -> activity.finish());
    }
  }
}
