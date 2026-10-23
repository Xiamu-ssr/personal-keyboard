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
      if (node("工具") != null) break;
      SystemClock.sleep(100);
    } while (SystemClock.uptimeMillis() < keyboardDeadline);
    await("工具");
    try {
      ui.waitForIdle(200, 3000);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new AssertionError("IME layout did not settle", e);
    }
    if (node("EN") != null) click("EN");
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
    long until=SystemClock.uptimeMillis()+2000;
    while(SystemClock.uptimeMillis()<until) {
      AccessibilityNodeInfo target=await(text);
      if(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {SystemClock.sleep(110);return;}
      // InputConnection restarts can replace the action row between lookup and dispatch.
      SystemClock.sleep(80);
    }
    fail("Control did not accept click: "+text);
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
  public void advisorChecksAccountBeforeCapturingAndPreservesDraft() {
    instrumentation.runOnMainSync(() -> editor.setText("本机验收草稿"));
    instrumentation.waitForIdleSync();
    click("军师");
    await("登录后使用军师");
    await("去登录");
    assertNull(node("重试"));
    assertEquals("本机验收草稿", value());
    click("返回"); await("工具");
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
    await("登录后使用语音");
    await("去登录");
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
