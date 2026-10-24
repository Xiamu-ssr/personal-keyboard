package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.PracticeActivity;
import java.io.*;
import java.util.*;
import org.junit.*;

/** Runs only against this app's real editor and IME, never another app's conversations. */
public class NativeFlowTest {
  Instrumentation instrumentation;
  UiAutomation ui;
  Activity activity;
  EditText editor;

  @Before
  public void open() {
    instrumentation = InstrumentationRegistry.getInstrumentation();
    ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    if (!android.os.Build.HARDWARE.equals("ranchu")
        && !android.os.Build.HARDWARE.equals("goldfish"))
      throw new AssertionError("This permission fixture is emulator-only");
    ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
    try {
      Context context = instrumentation.getTargetContext();
      String existing =
          android.provider.Settings.Secure.getString(
              context.getContentResolver(), "enabled_accessibility_services");
      String own = "app.doudou.ime/app.doudou.ime.capture.CaptureService";
      if (existing == null || !existing.contains("CaptureService"))
        android.provider.Settings.Secure.putString(
            context.getContentResolver(),
            "enabled_accessibility_services",
            existing == null || existing.isEmpty() ? own : existing + ":" + own);
      android.provider.Settings.Secure.putInt(
          context.getContentResolver(), "accessibility_enabled", 1);
    } finally {
      ui.dropShellPermissionIdentity();
    }
    try (android.os.ParcelFileDescriptor output =
        ui.executeShellCommand("ime set app.doudou.ime/.app.KeyboardService")) {
      try (java.io.FileInputStream in = new java.io.FileInputStream(output.getFileDescriptor())) {
        byte[] bytes = new byte[1024];
        while (in.read(bytes) != -1) {}
      }
    } catch (java.io.IOException e) {
      throw new AssertionError("Cannot select isolated emulator IME", e);
    }
    Intent intent =
        new Intent(instrumentation.getTargetContext(), PracticeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity = instrumentation.startActivitySync(intent);
    editor = field(activity.getWindow().getDecorView());
    assertNotNull(editor);
    instrumentation.waitForIdleSync();
    // The editor moves while adjustResize opens the IME. Never tap its old screen position.
    long keyboardDeadline = SystemClock.uptimeMillis() + 8000;
    do {
      instrumentation.runOnMainSync(
          () -> {
            if (activity.hasWindowFocus()) {
              editor.requestFocus();
              ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                  .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
          });
      instrumentation.waitForIdleSync();
      if (node("更多") != null) break;
      SystemClock.sleep(100);
    } while (SystemClock.uptimeMillis() < keyboardDeadline);
    await("更多");
    try {
      ui.waitForIdle(200, 3000);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new AssertionError("IME layout did not settle", e);
    }
    if (node("英") != null) click("英");
    await("q");
  }

  @After
  public void close() {
    if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
  }

  private EditText field(View v) {
    if (v instanceof EditText) return (EditText) v;
    if (v instanceof ViewGroup) {
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        EditText e = field(((ViewGroup) v).getChildAt(i));
        if (e != null) return e;
      }
    }
    return null;
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String text) {
    if (n == null) return null;
    if (n.isVisibleToUser()
        && (text.contentEquals(n.getText() == null ? "" : n.getText())
            || text.contentEquals(
                n.getContentDescription() == null ? "" : n.getContentDescription()))) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo found = find(n.getChild(i), text);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String text) {
    for (AccessibilityWindowInfo w : ui.getWindows()) {
      AccessibilityNodeInfo root = w.getRoot();
      if (root != null && "app.doudou.ime".contentEquals(root.getPackageName())) {
        AccessibilityNodeInfo result = find(root, text);
        if (result != null) return result;
      }
    }
    return null;
  }

  private AccessibilityNodeInfo await(String text) {
    long end = SystemClock.uptimeMillis() + 8000;
    while (SystemClock.uptimeMillis() < end) {
      AccessibilityNodeInfo n = node(text);
      if (n != null) return n;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Own app control missing: " + text);
  }

  private void click(String text) {
    long until = SystemClock.uptimeMillis() + 2000;
    while (SystemClock.uptimeMillis() < until) {
      AccessibilityNodeInfo target = await(text);
      AccessibilityNodeInfo action = target;
      for (int depth = 0; action != null && !action.isClickable() && depth < 3; depth++) {
        AccessibilityNodeInfo parent = action.getParent();
        if (parent == null || "android.widget.ListView".contentEquals(parent.getClassName())) break;
        action = parent;
      }
      if (action != null && action.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        SystemClock.sleep(180);
        return;
      }
      if (!target.isClickable()) {
        SystemClock.sleep(250);
        target = await(text);
        if (!target.refresh()) continue;
        Rect bounds = new Rect();
        target.getBoundsInScreen(bounds);
        long now = SystemClock.uptimeMillis();
        MotionEvent down =
            MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up =
            MotionEvent.obtain(
                now, now + 40, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        boolean delivered = ui.injectInputEvent(down, true) && ui.injectInputEvent(up, true);
        down.recycle();
        up.recycle();
        if (delivered) {
          SystemClock.sleep(120);
          return;
        }
      }
      // InputConnection restarts can replace the action row between lookup and dispatch.
      SystemClock.sleep(80);
    }
    fail("Control did not accept click: " + text);
  }

  private String value() {
    String[] value = {""};
    instrumentation.runOnMainSync(() -> value[0] = editor.getText().toString());
    return value[0];
  }

  private void clear() {
    instrumentation.runOnMainSync(() -> editor.setText(""));
    SystemClock.sleep(150);
  }

  private void screenshot(String name) {
    instrumentation.waitForIdleSync();
    try {
      ui.waitForIdle(120, 3000);
    } catch (java.util.concurrent.TimeoutException ignored) {
    }
    Bitmap b = ui.takeScreenshot();
    assertNotNull(b);
    try (FileOutputStream out =
        new FileOutputStream(new File(activity.getCacheDir(), "test-native-" + name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    } catch (IOException e) {
      throw new AssertionError(e);
    } finally {
      b.recycle();
    }
    if ("true".equals(InstrumentationRegistry.getArguments().getString("tour")))
      SystemClock.sleep(1600);
  }

  private int keyboardHeight() {
    for (AccessibilityWindowInfo w : ui.getWindows())
      if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        Rect r = new Rect();
        w.getBoundsInScreen(r);
        return r.height();
      }
    throw new AssertionError("No IME window");
  }

  @Test
  public void realPinyinKeysComposeAndCommitSentence() {
    for (char c : "nihao".toCharArray()) click("" + c);
    click("你好");
    assertEquals("你好", value());
    clear();
    for (char c : "woxiangni".toCharArray()) click("" + c);
    click("我想你");
    assertEquals("我想你", value());
    screenshot("typing");
    click("退格");
    assertEquals("我想", value());
    click("换行");
    assertEquals("我想\n", value());
  }

  @Test
  public void chineseEnterCommitsLiteralAndModernTermsAreCandidates() {
    for (char c : "hello".toCharArray()) click("" + c);
    click("换行");
    assertEquals("hello", value());
    clear();
    for (char c : "dalanguage".toCharArray()) click("" + c);
    click("换行");
    assertEquals("dalanguage", value());
    clear();
    for (char c : "rengongzhineng".toCharArray()) click("" + c);
    click("人工智能");
    assertEquals("人工智能", value());
    clear();
    for (char c : "haha".toCharArray()) click("" + c);
    click("展开");
    click("😂");
    assertEquals("😂", value());
    click("更多");
    click("符号");
    click("数学");
    click("≠");
    click("ABC");
    await("q");
    assertEquals("😂≠", value());
    screenshot("modern-input");
  }

  @Test
  public void advisorChecksAccountBeforeCapturingAndPreservesDraft() {
    instrumentation.runOnMainSync(() -> editor.setText("本机验收草稿"));
    instrumentation.waitForIdleSync();
    click("帮我回");
    await("登录后使用帮我回");
    await("去登录");
    assertNull(node("重试"));
    assertEquals("本机验收草稿", value());
    click("返回键盘");
    await("更多");
    assertEquals("本机验收草稿", value());
  }

  private String editorText(AccessibilityNodeInfo n) {
    if (n == null) return "";
    if ("android.widget.EditText".contentEquals(n.getClassName()))
      return n.getText() == null ? "" : n.getText().toString();
    for (int i = 0; i < n.getChildCount(); i++) {
      String value = editorText(n.getChild(i));
      if (!value.isEmpty()) return value;
    }
    return "";
  }

  @Test
  public void initialsCandidateCommitsAndExpands() {
    for (char c : "lll".toCharArray()) click("" + c);
    click("展开");
    click("六六六");
    assertEquals("六六六", value());
    await("更多");
    screenshot("initials");
  }

  @Test
  public void toolCardsStayInsideTheKeyboardAndExposeUsefulCollections() {
    int height = keyboardHeight();
    click("更多");
    await("常用工具");
    assertNull(node("q"));
    assertEquals("Opening tools does not resize the host editor", height, keyboardHeight());
    for (String title : new String[] {"剪贴板", "输入历史", "表情", "键盘选择", "符号", "隐私输入", "键盘设置"})
      await(title);
    assertNull(node("语音输入"));
    assertNull(node("原地军师"));
    assertNull(node("热词短语"));
    screenshot("tools");
    click("返回键盘");
    await("q");
    click("更多");
    click("表情");
    await("笑脸");
    assertEquals(height, keyboardHeight());
    assertNull(node("q"));
  }

  @Test
  public void clipboardAndHistoryReuseWordsInTheOriginalEditor() {
    app.doudou.ime.app.KeyboardApp app =
        (app.doudou.ime.app.KeyboardApp) instrumentation.getTargetContext().getApplicationContext();
    app.clips.clear();
    instrumentation.runOnMainSync(
        () -> {
          editor.setText("草稿：");
          editor.setSelection(editor.length());
          ((android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
              .setPrimaryClip(ClipData.newPlainText("fixture", "今晚七点见"));
        });
    long until = SystemClock.uptimeMillis() + 3000;
    while (app.clips.list().isEmpty() && SystemClock.uptimeMillis() < until) SystemClock.sleep(60);
    assertFalse(
        "The actual system clipboard listener retained the copied text",
        app.clips.list().isEmpty());
    click("更多");
    click("剪贴板");
    click("清空");
    await("已清空 · 撤销");
    click("已清空 · 撤销");
    await("填入：今晚七点见");
    SystemClock.sleep(8000);
    screenshot("clipboard");
    click("填入：今晚七点见");
    await("更多");
    assertEquals("草稿：今晚七点见", value());
    String id = app.histories.create("voice", "历史转写");
    app.histories.inserted(id, "之前说过的话", "");
    String raw = app.histories.create("voice", "未填入的转写");
    String withdrawn = app.histories.create("voice", "已撤回的转写");
    app.histories.inserted(withdrawn, "撤回结果", "");
    app.histories.withdraw(withdrawn);
    click("更多");
    click("输入历史");
    screenshot("keyboard-history");
    assertNull("Raw uninserted text is not offered as the user's result", node("填入：未填入的转写"));
    assertNull("Withdrawn text is not offered as the user's result", node("填入：已撤回的转写"));
    click("填入：之前说过的话");
    await("更多");
    assertEquals("草稿：今晚七点见之前说过的话", value());
    app.histories.remove(id);
    app.histories.remove(raw);
    app.histories.remove(withdrawn);
    click("更多");
    click("隐私输入");
    await("隐私输入已开启");
    instrumentation.runOnMainSync(
        () ->
            ((android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("fixture", "private sample not retained")));
    SystemClock.sleep(200);
    assertFalse(
        app.clips.list().stream().anyMatch(c -> c.text.equals("private sample not retained")));
    click("更多");
    click("隐私输入");
    await("更多");
    ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    SystemClock.sleep(200);
    instrumentation.runOnMainSync(
        () -> {
          editor.requestFocus();
          ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
              .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    await("更多");
    assertFalse(
        "A private copy is not imported after privacy is disabled and the keyboard reopens",
        app.clips.list().stream().anyMatch(c -> c.text.equals("private sample not retained")));
    instrumentation.runOnMainSync(
        () ->
            ((android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("fixture", "正常模式重新复制")));
    until = SystemClock.uptimeMillis() + 2000;
    while (app.clips.list().stream().noneMatch(c -> c.text.equals("正常模式重新复制"))
        && SystemClock.uptimeMillis() < until) SystemClock.sleep(40);
    assertTrue(
        "An explicit normal copy resumes collection",
        app.clips.list().stream().anyMatch(c -> c.text.equals("正常模式重新复制")));
    app.clips.clear();
  }

  @Test
  public void deniedLongHoldDoesNotAccidentallyInsertASpace() {
    instrumentation.runOnMainSync(
        () -> {
          editor.setText("原草稿");
          editor.setSelection(editor.length());
        });
    instrumentation.waitForIdleSync();
    Rect key = new Rect();
    await("空格").getBoundsInScreen(key);
    long down = SystemClock.uptimeMillis();
    MotionEvent press =
        MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, key.centerX(), key.centerY(), 0);
    assertTrue(ui.injectInputEvent(press, true));
    press.recycle();
    await("登录后使用语音");
    MotionEvent release =
        MotionEvent.obtain(
            down,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            key.centerX(),
            key.centerY(),
            0);
    assertTrue(ui.injectInputEvent(release, true));
    release.recycle();
    assertEquals("原草稿", value());
    click("返回键盘");
    await("更多");
    assertEquals("原草稿", value());
  }

  @Test
  public void noLearningEditorDisablesVoiceCapture() {
    instrumentation.runOnMainSync(
        () -> {
          editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
          editor.setImeOptions(
              android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                  | android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION);
          ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
              .restartInput(editor);
        });
    await("隐私输入");
    click("中");
    click("a");
    assertEquals("a", value());
    assertNull(node("帮我回"));
    assertNull(node("按住说话"));
    assertEquals("a", value());
  }
}
