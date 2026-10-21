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
import app.doudou.ime.capture.CaptureService;
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
    Intent intent =
        new Intent(instrumentation.getTargetContext(), PracticeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity = instrumentation.startActivitySync(intent);
    editor = field(activity.getWindow().getDecorView());
    assertNotNull(editor);
    instrumentation.waitForIdleSync();
    int[] position = new int[2];
    instrumentation.runOnMainSync(() -> editor.getLocationOnScreen(position));
    long now = SystemClock.uptimeMillis();
    ui.injectInputEvent(
        MotionEvent.obtain(
            now, now, MotionEvent.ACTION_DOWN, position[0] + 30, position[1] + 30, 0),
        true);
    ui.injectInputEvent(
        MotionEvent.obtain(
            now, now + 50, MotionEvent.ACTION_UP, position[0] + 30, position[1] + 30, 0),
        true);
    long keyboardDeadline = SystemClock.uptimeMillis() + 8000;
    while (node("工具") == null && SystemClock.uptimeMillis() < keyboardDeadline) {
      instrumentation.runOnMainSync(
          () -> {
            editor.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
          });
      SystemClock.sleep(250);
    }
    await("工具");
    if (node("EN") != null) click("EN");
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
    assertTrue(text, await(text).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    SystemClock.sleep(110);
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
  public void advisorCapturesOwnScreenAndPreservesDraft() {
    // Instrumentation restarts the process. Rebind only an already authorized service.
    String services =
        android.provider.Settings.Secure.getString(
            activity.getContentResolver(), "enabled_accessibility_services");
    String own = "app.doudou.ime/app.doudou.ime.capture.CaptureService";
    assertTrue(
        "Screenshot permission must already be enabled",
        services != null && services.contains(own));
    if (CaptureService.current == null) {
      ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
      try {
        List<String> keep = new ArrayList<>(Arrays.asList(services.split(":")));
        keep.remove(own);
        android.provider.Settings.Secure.putString(
            activity.getContentResolver(),
            "enabled_accessibility_services",
            String.join(":", keep));
        // Let AccessibilityManager observe removal before re-enabling the same service.
        SystemClock.sleep(700);
        android.provider.Settings.Secure.putString(
            activity.getContentResolver(), "enabled_accessibility_services", services);
      } finally {
        ui.dropShellPermissionIdentity();
      }
      long until = SystemClock.uptimeMillis() + 8000;
      while (CaptureService.current == null && SystemClock.uptimeMillis() < until)
        SystemClock.sleep(80);
    }
    assertNotNull("Already authorized screenshot service did not bind", CaptureService.current);
    instrumentation.runOnMainSync(() -> editor.setText("本机验收草稿"));
    instrumentation.waitForIdleSync();
    try {
      ui.waitForIdle(160, 3000);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new AssertionError("Practice editor did not settle after updating its draft", e);
    }
    int normal = keyboardHeight();
    click("军师");
    await("正在读取当前屏幕…");
    int shrunk = keyboardHeight();
    long resizedBy = SystemClock.uptimeMillis() + 2500;
    while (shrunk >= normal - 300 && SystemClock.uptimeMillis() < resizedBy) {
      SystemClock.sleep(20);
      shrunk = keyboardHeight();
    }
    System.out.println("IME height px normal=" + normal + " shrunk=" + shrunk);
    assertTrue("Capture must expose more of the host", shrunk < normal - 300);
    await("创建这个聊天对象");
    screenshot("advisor");
    click("查看 / 修正本次识字");
    String recognized = "";
    for (AccessibilityWindowInfo w : ui.getWindows())
      if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        recognized = editorText(w.getRoot());
      }
    assertTrue("OCR must contain the real local practice screen", recognized.contains("本机"));
    assertNotNull(await("返回"));
    click("返回");
    assertEquals("本机验收草稿", value());
    click("取消");
    await("工具");
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
    await("工具");
    screenshot("initials");
  }

  @Test
  public void toolsAndVoiceFailureRemainRecoverable() {
    click("工具");
    await("随手工具");
    for (String title : new String[] {"语音输入", "原地军师", "热词短语", "剪贴板", "移动光标", "撤销上屏", "隐私输入", "设置"})
      await(title);
    screenshot("tools");
    click("语音输入");
    await("开始说话");
    screenshot("voice");
    click("开始说话");
    // The isolated emulator is not signed in; no supplier call may be made.
    await("请先到设置登录豆豆账户");
    await("开始说话");
    click("返回");
    await("工具");
    assertEquals("", value());
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
    click("军师");
    await("此输入框不启用军师读屏。");
    assertEquals("a", value());
  }
}
