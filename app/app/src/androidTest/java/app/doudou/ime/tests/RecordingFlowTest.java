package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.recording.*;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.*;

/** Real microphone and durable-file checks, guarded to the owned disposable emulator. */
public final class RecordingFlowTest {
  private Instrumentation ins;
  private UiAutomation ui;
  private KeyboardApp app;
  private Activity activity;
  private EditText editor;

  @Before
  public void open() throws Exception {
    Assume.assumeTrue("Owned emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    if (app.cloud.loggedIn()) app.cloud.logout();
    app.personalization.activate();
    app.histories.clear();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    activity =
        ins.startActivitySync(
            new Intent(app, PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    editor = field(activity.getWindow().getDecorView());
    ins.runOnMainSync(
        () -> {
          editor.setText("草稿不动");
          editor.setSelection(editor.length());
          editor.requestFocus();
          ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
              .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    long deadline = SystemClock.uptimeMillis() + 8000;
    while (node("点击录音") == null && SystemClock.uptimeMillis() < deadline) {
      ins.runOnMainSync(
          () -> {
            editor.requestFocus();
            ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
          });
      SystemClock.sleep(100);
    }
    if (node("点击录音") == null) shot("recording-open-error");
    await("点击录音", 1000);
  }

  @After
  public void close() throws Exception {
    if (RecordingService.current != null) RecordingService.current.finish();
    if (activity != null) ins.runOnMainSync(activity::finish);
    if (app != null && app.cloud.loggedIn()) app.cloud.logout();
  }

  private EditText field(View view) {
    if (view instanceof EditText) return (EditText) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        EditText found = field(((ViewGroup) view).getChildAt(i));
        if (found != null) return found;
      }
    return null;
  }

  private ScrollView chat(View view) {
    if (view instanceof ScrollView) return (ScrollView) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        ScrollView found = chat(((ViewGroup) view).getChildAt(i));
        if (found != null) return found;
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
      AccessibilityNodeInfo n = w.getRoot();
      if (n != null && "app.doudou.ime".contentEquals(n.getPackageName())) {
        AccessibilityNodeInfo found = find(n, text);
        if (found != null) return found;
      }
    }
    return null;
  }

  private AccessibilityNodeInfo await(String text, long timeout) {
    long until = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < until) {
      AccessibilityNodeInfo n = node(text);
      if (n != null) return n;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Missing product control: " + text);
  }

  private void click(String text) {
    AccessibilityNodeInfo n = await(text, 8000);
    while (n != null && !n.isClickable()) n = n.getParent();
    assertNotNull(text, n);
    assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    SystemClock.sleep(120);
  }

  private void shot(String name) throws Exception {
    SystemClock.sleep(180);
    Bitmap image = ui.takeScreenshot();
    assertNotNull(image);
    try (FileOutputStream out =
        new FileOutputStream(new File(app.getCacheDir(), "test-native-" + name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
    if ("true".equals(InstrumentationRegistry.getArguments().getString("tour")))
      SystemClock.sleep(1200);
  }

  private String value() {
    final String[] text = {""};
    ins.runOnMainSync(() -> text[0] = editor.getText().toString());
    return text[0];
  }

  private void event(int action, float x, float y, long down) {
    MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
    assertTrue(ui.injectInputEvent(e, true));
    e.recycle();
  }

  private void waitActive() throws Exception {
    long until = SystemClock.uptimeMillis() + 6000;
    while (SystemClock.uptimeMillis() < until) {
      if (RecordingService.current != null
          && RecordingService.current.active()
          && RecordingService.current.bytes() > 0) return;
      SystemClock.sleep(100);
    }
    throw new AssertionError("Real foreground recorder did not capture audio");
  }

  private void waitStopped() throws Exception {
    long until = SystemClock.uptimeMillis() + 25000;
    while (RecordingService.current != null && SystemClock.uptimeMillis() < until)
      SystemClock.sleep(100);
    assertNull("Recording service closes after finalization", RecordingService.current);
  }

  @Test
  public void localAudioSurvivesKeyboardDismissalAndChunkRotation() throws Exception {
    click("点击录音");
    waitActive();
    assertNull(node("q"));
    String id = RecordingService.current.id();
    long start = SystemClock.uptimeMillis();
    shot("recording");
    assertEquals("草稿不动", value());
    click("返回");
    await("q", 3000);
    click("，");
    assertEquals("草稿不动，", value());
    ins.runOnMainSync(
        () ->
            ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(editor.getWindowToken(), 0));
    SystemClock.sleep(1200);
    assertTrue(RecordingService.current.active());
    while (SystemClock.uptimeMillis() - start < 46200) SystemClock.sleep(500);
    RecordingStore store = new RecordingStore(app, id);
    assertTrue(
        "45-second durable chunk rotation", store.snapshot().getJSONArray("chunks").length() >= 2);
    assertTrue(store.size() >= 45L * 32000);
    ins.runOnMainSync(
        () -> {
          editor.requestFocus();
          ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
              .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    click("● 录音中");
    assertEquals(id, RecordingService.current.id());
    shot("recording-long");
    click("结束");
    waitStopped();
    await("已保存", 3000);
    shot("recording-completed");
    store = new RecordingStore(app, id);
    assertFalse(store.snapshot().getBoolean("active"));
    assertTrue(store.size() > 45L * 32000);
    assertEquals("草稿不动，", value());
    assertEquals("recording", app.histories.get(id).type);
    assertEquals(id, app.histories.get(id).audioRef);
    assertEquals("Offline recording has no invented transcript", "", app.histories.get(id).asrText);
    JSONObject proof =
        new JSONObject()
            .put("bytes", store.size())
            .put("chunks", store.snapshot().getJSONArray("chunks").length())
            .put("draftPreserved", true)
            .put("keyboardDismissed", true)
            .put("reopenedSameId", true);
    try (FileOutputStream out =
        new FileOutputStream(new File(app.getCacheDir(), "product-recording-local-proof.json"))) {
      out.write(proof.toString().getBytes("UTF-8"));
    }
  }

  private void network(boolean enabled) throws Exception {
    String action = enabled ? "enable" : "disable";
    for (String service : new String[] {"wifi", "data"})
      try (ParcelFileDescriptor fd = ui.executeShellCommand("svc " + service + " " + action);
          InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        while (in.read() != -1) {}
      }
    long until = SystemClock.uptimeMillis() + 12000;
    android.net.ConnectivityManager cm =
        (android.net.ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    while ((cm.getActiveNetwork() != null) != enabled && SystemClock.uptimeMillis() < until)
      SystemClock.sleep(100);
    assertEquals("Owned emulator network state", enabled, cm.getActiveNetwork() != null);
  }

  @Test
  public void disconnectedRecordingKeepsAudioAndCanTranscribeAfterReconnect() throws Exception {
    Assume.assumeTrue(
        "Explicit live model fixture",
        "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    JSONObject qa =
        new JSONObject(
            new String(
                java.nio.file.Files.readAllBytes(
                    new File(app.getCacheDir(), "product-qa.json").toPath()),
                "UTF-8"));
    app.cloud.login(qa.getString("handle"), qa.getString("password"));
    app.personalization.activate();
    String id;
    try {
      network(false);
      click("点击录音");
      waitActive();
      id = RecordingService.current.id();
      File marker = new File(app.getCacheDir(), "product-voice-ready");
      marker.createNewFile();
      SystemClock.sleep(13000);
      marker.delete();
      assertTrue(
          "Network failure cannot stop local microphone capture",
          RecordingService.current.active());
      shot("recording-offline");
      click("结束");
      waitStopped();
      RecordingStore saved = new RecordingStore(app, id);
      assertTrue(saved.size() > 10L * 32000);
      assertEquals("", saved.text(false));
      assertEquals("草稿不动", value());
    } finally {
      network(true);
    }
    RecordingStore saved = new RecordingStore(app, id);
    long bytes = saved.size();
    RecordingTranscriber.enqueue(app, id);
    long until = SystemClock.uptimeMillis() + 90000;
    while (new RecordingStore(app, id).text(false).isEmpty()
        && SystemClock.uptimeMillis() < until) {
      java.util.List<androidx.work.WorkInfo> work =
          androidx.work.WorkManager.getInstance(app)
              .getWorkInfosForUniqueWork("recording:" + id)
              .get();
      if (!work.isEmpty() && work.get(0).getState().isFinished()) break;
      SystemClock.sleep(300);
    }
    saved = new RecordingStore(app, id);
    assertFalse("The stored audio is transcribed after reconnect", saved.text(false).isEmpty());
    assertEquals("Audio remains reusable", bytes, saved.size());
    assertEquals(saved.text(false), app.histories.get(id).asrText);
    assertEquals("草稿不动", value());
    JSONObject proof =
        new JSONObject()
            .put("bytes", bytes)
            .put("transcript", saved.text(false))
            .put("reconnected", true)
            .put("draftPreserved", true);
    try (FileOutputStream out =
        new FileOutputStream(new File(app.getCacheDir(), "product-recording-resume-proof.json"))) {
      out.write(proof.toString().getBytes("UTF-8"));
    }
  }

  @Test
  public void liveRecordingWritesSpeechWithoutInsertingIntoTheEditor() throws Exception {
    Assume.assumeTrue(
        "Explicit live model fixture",
        "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    JSONObject qa =
        new JSONObject(
            new String(
                java.nio.file.Files.readAllBytes(
                    new File(app.getCacheDir(), "product-qa.json").toPath()),
                "UTF-8"));
    app.cloud.login(qa.getString("handle"), qa.getString("password"));
    app.personalization.activate();
    click("点击录音");
    waitActive();
    String id = RecordingService.current.id();
    File marker = new File(app.getCacheDir(), "product-voice-ready");
    marker.createNewFile();
    SystemClock.sleep(11000);
    marker.delete();
    shot("recording-live");
    click("结束");
    waitStopped();
    RecordingStore store = new RecordingStore(app, id);
    assertTrue(store.size() > 9L * 32000);
    assertFalse("Real ASR result retained", store.text(false).isEmpty());
    assertEquals("草稿不动", value());
    assertEquals(store.text(false), app.histories.get(id).asrText);
    await("已保存", 3000);
    shot("recording-saved");
    JSONObject proof =
        new JSONObject()
            .put("bytes", store.size())
            .put("transcript", store.text(false))
            .put("draftPreserved", true);
    try (FileOutputStream out =
        new FileOutputStream(new File(app.getCacheDir(), "product-recording-live-proof.json"))) {
      out.write(proof.toString().getBytes("UTF-8"));
    }
  }
}
