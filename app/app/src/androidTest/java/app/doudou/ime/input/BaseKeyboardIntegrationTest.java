package app.doudou.ime.input;

import static org.junit.Assert.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.input.engine.PinyinEngine;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;

/** End-to-end service wiring through our synthetic practice editor; no accounts or model calls. */
public class BaseKeyboardIntegrationTest {
  private Instrumentation ins;
  private UiAutomation ui;

  private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
    if (node == null) return null;
    if (node.isVisibleToUser() && (text.contentEquals(node.getText() == null ? "" : node.getText())
        || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())))
      return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo result = find(node.getChild(i), text);
      if (result != null) return result;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String text) {
    for (AccessibilityWindowInfo window : ui.getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root != null && "app.doudou.ime".contentEquals(root.getPackageName())) {
          AccessibilityNodeInfo result = find(root, text);
          if (result != null) return result;
        }
      }
    return null;
  }

  private AccessibilityNodeInfo await(String text) {
    long until = SystemClock.uptimeMillis() + 8000;
    while (SystemClock.uptimeMillis() < until) {
      AccessibilityNodeInfo result = node(text);
      if (result != null) return result;
      SystemClock.sleep(60);
    }
    throw new AssertionError("Own IME control missing: " + text);
  }

  private void click(String text) {
    AccessibilityNodeInfo target = await(text);
    if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
      // Picker labels may expose accessibility on a non-clickable child. Drive a real tap and
      // let the subsequent editor/state assertion verify the result.
      android.graphics.Rect bounds = new android.graphics.Rect();
      target.getBoundsInScreen(bounds);
      long now = SystemClock.uptimeMillis();
      MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
          bounds.exactCenterX(), bounds.exactCenterY(), 0);
      MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP,
          bounds.exactCenterX(), bounds.exactCenterY(), 0);
      down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      assertTrue(text, ui.injectInputEvent(down, true));
      assertTrue(text, ui.injectInputEvent(up, true));
      down.recycle(); up.recycle();
    }
    SystemClock.sleep(70);
  }

  private EditText field(View view) {
    if (view instanceof EditText) return (EditText) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        EditText result = field(((ViewGroup) view).getChildAt(i));
        if (result != null) return result;
      }
    return null;
  }

  private void type(String raw) { for (char letter : raw.toCharArray()) click("" + letter); }

  private void screenshot(Activity activity, String name) throws Exception {
    ins.waitForIdleSync();
    try { ui.waitForIdle(180, 2500); } catch (java.util.concurrent.TimeoutException ignored) {}
    Bitmap bitmap = ui.takeScreenshot();
    try (FileOutputStream out = new FileOutputStream(
        new File(activity.getExternalFilesDir(null), "host-" + name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  @Test public void composingCorrectionPagingBackAndToolsUseTheLiveHost() throws Exception {
    Assume.assumeTrue("Owned emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    Activity activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      EditText edit = field(activity.getWindow().getDecorView());
      assertNotNull(edit);
      ins.runOnMainSync(() -> {
        edit.setText(""); edit.requestFocus();
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
            .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
      });
      await("更多");
      if (node("英") != null) click("英");
      for (String workspace : new String[] {"输入", "录音", "帮我回", "翻译"}) assertNotNull(await(workspace));
      type("nihso");
      await("正在输入拼音：nihso"); await("你好");
      ins.runOnMainSync(() -> assertEquals("nihso", edit.getText().toString()));
      screenshot(activity, "pinyin-correction");
      click("你好"); await("更多");
      ins.runOnMainSync(() -> assertEquals("你好", edit.getText().toString()));
      screenshot(activity, "typing");

      type("shi"); await("是"); click("展开"); await("拼音候选词网格");
      ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
      await("正在输入拼音：shi"); await("q"); await("是");
      click("展开"); await("拼音候选词网格");
      screenshot(activity, "candidate-first");
      KeyboardApp app = (KeyboardApp) activity.getApplication();
      PinyinEngine.Query query = app.worker.submit(
          () -> app.pinyin.query("shi", app.store.frequencies())).get(30, TimeUnit.SECONDS);
      int pages = (query.size() + CandidatePanel.PAGE_SIZE - 1) / CandidatePanel.PAGE_SIZE;
      assertTrue(pages > 1);
      for (int page = 1; page < pages; page++) click("下一页");
      assertFalse(await("下一页").isEnabled());
      String selected = query.page((pages - 1) * CandidatePanel.PAGE_SIZE, 1).get(0).text;
      screenshot(activity, "candidate-last");
      click(selected); await("更多");
      ins.runOnMainSync(() -> assertEquals("你好" + selected, edit.getText().toString()));

      type("nihsao"); await("正在输入拼音：nihsao"); click("换行"); await("更多");
      ins.runOnMainSync(() -> assertEquals("你好" + selected + "nihsao", edit.getText().toString()));
      click("更多"); click("表情"); await("返回键盘");
      // The picker loads its offline categories asynchronously; inspect a populated page rather
      // than capturing the first, temporarily empty layout frame.
      click("表情符号"); click("😀表情符号"); await("返回键盘");
      screenshot(activity, "emoji-return"); click("返回键盘"); await("更多");
      click("录音"); await("开始录音");
      screenshot(activity, "recording-standby");
      click("输入"); await("更多");
      ins.runOnMainSync(() -> assertEquals("你好" + selected + "nihsao😀", edit.getText().toString()));
      click("123"); await("1"); screenshot(activity, "numbers");
      click("ABC"); await("q");
      android.util.Log.i("BaseKeyboardQA", "Host integration PASS: spelling, correction, "
          + pages + " pages, Android Back, last-page select, raw Enter, emoji return, recording standby");
    } finally { ins.runOnMainSync(activity::finish); }
  }
}
