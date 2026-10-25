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
import app.doudou.ime.BuildConfig;
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureDifference;
import app.doudou.ime.capture.CaptureService;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.*;

/**
 * Explicitly enabled live-provider acceptance, restricted to an isolated emulator and sample chat.
 */
public final class ProductFlowTest {
  private Instrumentation ins;
  private UiAutomation ui;
  private KeyboardApp app;
  private Activity activity;
  private EditText editor;

  @Before
  public void open() throws Exception {
    Assume.assumeTrue("Live fixture only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    Assume.assumeTrue(
        "Explicit paid-fixture opt-in",
        "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    app.histories
        .clear(); // Only the guarded disposable emulator; every sample conversation starts fresh.
    File credentials = new File(app.getCacheDir(), "product-qa.json");
    JSONObject qa =
        new JSONObject(new String(java.nio.file.Files.readAllBytes(credentials.toPath()), "UTF-8"));
    app.cloud.login(qa.getString("handle"), qa.getString("password"));
    app.cloud.account();
    app.personalization.activate();
    app.prefs().edit().putBoolean("setupComplete", true).apply();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |=
        android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
    try {
      android.provider.Settings.Secure.putString(
          app.getContentResolver(), "enabled_accessibility_services", "");
      SystemClock.sleep(400);
      android.provider.Settings.Secure.putString(
          app.getContentResolver(),
          "enabled_accessibility_services",
          "app.doudou.ime/.capture.CaptureService");
      android.provider.Settings.Secure.putInt(app.getContentResolver(), "accessibility_enabled", 1);
    } finally {
      ui.dropShellPermissionIdentity();
    }
    long until = SystemClock.uptimeMillis() + 8000;
    while (CaptureService.current == null && SystemClock.uptimeMillis() < until)
      SystemClock.sleep(80);
    assertNotNull("Authorized screenshot service bound", CaptureService.current);
    activity =
        ins.startActivitySync(
            new Intent(app, PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    editor = field(activity.getWindow().getDecorView());
    assertNotNull(editor);
    long keyboardUntil = SystemClock.uptimeMillis() + 8000;
    while (node("更多") == null && SystemClock.uptimeMillis() < keyboardUntil) {
      ins.runOnMainSync(
          () -> {
            editor.requestFocus();
            ((InputMethodManager)
                    app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
          });
      SystemClock.sleep(100);
    }
    await("更多", 8000);
  }

  @After
  public void close() throws Exception {
    if (activity != null) ins.runOnMainSync(activity::finish);
    if (app != null && app.cloud.loggedIn()) {
      app.cloud.logout();
      app.personalization.activate();
    }
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

  private void latestMessageVisible() {
    long until = SystemClock.uptimeMillis() + 4000;
    final boolean[] bottom = {false};
    final int[] distance = {0};
    do {
      ins.runOnMainSync(
          () -> {
            ScrollView scroll = chat(activity.getWindow().getDecorView());
            distance[0] =
                scroll.getChildAt(0).getHeight() - scroll.getScrollY() - scroll.getHeight();
            bottom[0] = distance[0] <= 8;
          });
      if (bottom[0]) return;
      SystemClock.sleep(60);
    } while (SystemClock.uptimeMillis() < until);
    assertTrue(
        "Latest fixture message remains visible after capture/resize; distance=" + distance[0],
        bottom[0]);
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

  private int awaitGenerationAfterInputViewHides() {
    long until = SystemClock.uptimeMillis() + 15000;
    int hiddenTransitions = 0;
    boolean wasVisible = true;
    while (SystemClock.uptimeMillis() < until) {
      boolean visible = false;
      for (AccessibilityWindowInfo window : ui.getWindows())
        if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) visible = true;
      if (!visible && wasVisible) hiddenTransitions++;
      wasVisible = visible;
      if (node("想想怎么回…") != null) return hiddenTransitions;
      SystemClock.sleep(30);
    }
    throw new AssertionError("The captured chat never returned to the advisor panel");
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

  private app.doudou.ime.history.HistoryStore.Entry latest() throws Exception {
    long until = SystemClock.uptimeMillis() + 90000;
    while (SystemClock.uptimeMillis() < until) {
      for (app.doudou.ime.history.HistoryStore.Entry e : app.histories.list(20))
        if (e.type.equals("advisor") && e.replyGroups.length() > 0) return e;
      SystemClock.sleep(120);
    }
    throw new AssertionError("No successful advisor history");
  }

  private void groupCount(String id, int count) throws Exception {
    long until = SystemClock.uptimeMillis() + 90000;
    while (SystemClock.uptimeMillis() < until) {
      if (app.histories.get(id).replyGroups.length() >= count) return;
      SystemClock.sleep(180);
    }
    throw new AssertionError("Missing reply group " + count);
  }

  private String first(String id, int group) throws Exception {
    return app.histories
        .get(id)
        .replyGroups
        .getJSONObject(group)
        .getJSONArray("replies")
        .getJSONObject(0)
        .getString("text");
  }

  @Test
  public void automaticTwoScreensSixExpressionsAndRestorableVariants() throws Exception {
    ins.runOnMainSync(
        () -> {
          editor.setText("已有草稿：");
          editor.setSelection(editor.length());
        });
    ins.waitForIdleSync();
    SystemClock.sleep(350);
    long start = SystemClock.elapsedRealtime();
    click("帮我回");
    await("正在看聊天", 4000);
    shot("advisor-capture");
    await("想想怎么回…", 15000);
    shot("advisor-loading");
    await("自然回复", 90000);
    latestMessageVisible();
    shot("advisor");
    app.doudou.ime.history.HistoryStore.Entry h = latest();
    assertEquals(
        "Six distinct configured expressions",
        6,
        h.replyGroups.getJSONObject(0).getJSONArray("replies").length());
    JSONArray answers = h.replyGroups.getJSONObject(0).getJSONArray("replies");
    for (int i = 0; i < answers.length(); i++) {
      String text = answers.getJSONObject(i).getString("text");
      for (String invented : new String[] {"有点事", "手头还有事", "不确定有没有空", "不确定有空", "这次先不"})
        assertFalse("No unprovided availability or refusal: " + text, text.contains(invented));
    }
    assertEquals("Automatic capture gets two different fixture screens", 2, h.screenshots.length());
    Bitmap a =
        BitmapFactory.decodeFile(
            app.histories.imageFile(h.screenshots.getString(0)).getAbsolutePath());
    Bitmap b =
        BitmapFactory.decodeFile(
            app.histories.imageFile(h.screenshots.getString(1)).getAbsolutePath());
    assertTrue(CaptureDifference.changed(CaptureService.signature(a), CaptureService.signature(b)));
    Bitmap display = ui.takeScreenshot();
    assertTrue(a.getHeight() > display.getHeight() * .75);
    a.recycle();
    b.recycle();
    display.recycle();
    assertEquals("已有草稿：", value());
    String original = first(h.id, 0);
    click("换一条自然");
    await("想想怎么回…", 4000);
    groupCount(h.id, 2);
    await("自然回复", 4000);
    shot("advisor-variants");
    JSONArray variants =
        app.histories.get(h.id).replyGroups.getJSONObject(1).getJSONArray("replies");
    assertEquals(3, variants.length());
    for (int i = 0; i < 3; i++)
      assertEquals("slot1", variants.getJSONObject(i).getString("slotId"));
    int requests = ownJobs().length();
    click("上一组");
    await("1 / 2", 3000);
    assertEquals(original, await(original, 4000).getText().toString());
    click("下一组");
    await("2 / 2", 3000);
    assertEquals("Restoring either direction makes no model request", requests, ownJobs().length());
    click("换一批");
    await("想想怎么回…", 4000);
    groupCount(h.id, 3);
    await("自然回复", 4000);
    assertEquals(
        6, app.histories.get(h.id).replyGroups.getJSONObject(2).getJSONArray("replies").length());
    click("上一组");
    click("上一组");
    await("1 / 3", 4000);
    click("自然回复");
    await("q", 4000);
    assertEquals("已有草稿：" + original, value());
    h = app.histories.get(h.id);
    assertEquals(original, h.insertedText);
    assertFalse(h.messages.toString().isEmpty());
    latestMessageVisible();
    shot("advisor-inserted");
    JSONObject proof =
        new JSONObject()
            .put("milliseconds", SystemClock.elapsedRealtime() - start)
            .put("pages", 2)
            .put("initialReplies", 6)
            .put("slotVariants", 3)
            .put("savedGroups", 3)
            .put("restorationRequests", 0)
            .put("latestMessageVisible", true)
            .put("draftPreserved", true);
    try (FileOutputStream out =
        new FileOutputStream(new File(app.getCacheDir(), "product-advisor-proof.json"))) {
      out.write(proof.toString().getBytes("UTF-8"));
    }
  }

  @Test
  public void cancellingCaptureLeavesNoFailedHistory() throws Exception {
    int before = app.histories.list(100).size();
    click("帮我回");
    click("取消");
    await("q", 4000);
    SystemClock.sleep(1200);
    assertEquals(before, app.histories.list(100).size());
    assertEquals("", value());
    shot("advisor-cancelled");
  }

  @Test
  public void captureRestoresBeforeGenerating() throws Exception {
    click("帮我回");
    await("想想怎么回…", 15000);
    try {
      latestMessageVisible();
    } finally {
      click("取消生成并返回键盘");
    }
  }

  @Test
  public void hostHidingKeyboardOnCaptureScrollRestoresAdvisorAndDraft() throws Exception {
    java.util.concurrent.atomic.AtomicInteger hides =
        new java.util.concurrent.atomic.AtomicInteger();
    ins.runOnMainSync(
        () -> {
          editor.setText("翻屏前的草稿");
          editor.setSelection(editor.length());
          chat(activity.getWindow().getDecorView())
              .setOnTouchListener(
                  (view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && hides.getAndIncrement() == 0)
                      ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
                          .hideSoftInputFromWindow(editor.getWindowToken(), 0);
                    return false;
                  });
          editor.requestFocus();
        });
    ins.waitForIdleSync();
    SystemClock.sleep(350);
    click("帮我回");
    assertTrue(
        "The IME window really disappeared before recovering",
        awaitGenerationAfterInputViewHides() > 0);
    assertTrue("The host really hid the IME during our automatic gesture", hides.get() > 0);
    latestMessageVisible();
    assertEquals("翻屏前的草稿", value());
    shot("advisor-host-hide-restored");
    click("取消生成并返回键盘");
    await("q", 4000);
    assertEquals("翻屏前的草稿", value());
  }

  @Test
  public void bothCaptureAndReturnGesturesCanHideTheKeyboard() throws Exception {
    java.util.concurrent.atomic.AtomicInteger hides =
        new java.util.concurrent.atomic.AtomicInteger();
    ins.runOnMainSync(
        () ->
            chat(activity.getWindow().getDecorView())
                .setOnTouchListener(
                    (view, event) -> {
                      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        hides.incrementAndGet();
                        ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(editor.getWindowToken(), 0);
                      }
                      return false;
                    }));
    click("帮我回");
    assertEquals(
        "Two actual IME visibility losses were recovered", 2, awaitGenerationAfterInputViewHides());
    assertEquals("Both the earlier-page and return gesture ran", 2, hides.get());
    latestMessageVisible();
    click("取消生成并返回键盘");
    await("q", 4000);
  }

  @Test
  public void leavingHostDuringCaptureDoesNotReopenTheKeyboard() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean touched =
        new java.util.concurrent.atomic.AtomicBoolean();
    ins.runOnMainSync(
        () ->
            chat(activity.getWindow().getDecorView())
                .setOnTouchListener(
                    (view, event) -> {
                      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touched.set(true);
                      return false;
                    }));
    click("帮我回");
    long until = SystemClock.uptimeMillis() + 6000;
    while (!touched.get() && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
    assertTrue("Leave after the owned gesture really started", touched.get());
    assertTrue(
        ui.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));
    SystemClock.sleep(3000);
    for (AccessibilityWindowInfo window : ui.getWindows())
      assertNotEquals(
          "Home must not receive the old chat's IME",
          AccessibilityWindowInfo.TYPE_INPUT_METHOD,
          window.getType());
    assertTrue("Abandoned capture makes no history", app.histories.list(50).isEmpty());
  }

  @Test
  public void switchingEditorsDuringCaptureDoesNotRestoreTheOldPanel() throws Exception {
    EditText[] other = {null};
    java.util.concurrent.atomic.AtomicBoolean switched =
        new java.util.concurrent.atomic.AtomicBoolean();
    ins.runOnMainSync(
        () -> {
          editor.setText("旧输入框的草稿");
          other[0] = new EditText(activity);
          other[0].setId(View.generateViewId());
          other[0].setText("新输入框的草稿");
          other[0].setFocusable(false);
          ((LinearLayout) editor.getParent())
              .addView(other[0], new LinearLayout.LayoutParams(120, 80));
          chat(activity.getWindow().getDecorView())
              .setOnTouchListener(
                  (view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && switched.compareAndSet(false, true)) {
                      other[0].setFocusableInTouchMode(true);
                      other[0].requestFocus();
                      ((InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE))
                          .showSoftInput(other[0], InputMethodManager.SHOW_IMPLICIT);
                    }
                    return false;
                  });
          editor.requestFocus();
        });
    ins.waitForIdleSync();
    SystemClock.sleep(350);
    click("帮我回");
    long until = SystemClock.uptimeMillis() + 6000;
    while (!switched.get() && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
    assertTrue("The host changed its editor during our gesture", switched.get());
    await("q", 5000);
    SystemClock.sleep(2500);
    assertNull(node("想想怎么回…"));
    assertEquals("旧输入框的草稿", value());
    ins.runOnMainSync(() -> assertEquals("新输入框的草稿", other[0].getText().toString()));
    assertTrue(app.histories.list(50).isEmpty());
  }

  @Test
  public void captureDragsHaveNoResidualScroll() throws Exception {
    Rect bounds = new Rect();
    for (AccessibilityWindowInfo window : ui.getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        window.getBoundsInScreen(bounds);
    assertTrue(bounds.top > 0);
    JSONArray proof = new JSONArray();
    for (int step = 0; step < 3; step++) {
      if (step > 0) {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        boolean[] ok = {false};
        if (step == 1)
          CaptureService.earlier(
              bounds.top,
              v -> {
                ok[0] = v;
                done.countDown();
              });
        else
          CaptureService.later(
              bounds.top,
              v -> {
                ok[0] = v;
                done.countDown();
              });
        assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS));
        System.out.println("gesture step=" + step + " completed=" + ok[0]);
        SystemClock.sleep(200);
      }
      final int[] values = new int[3];
      ins.runOnMainSync(
          () -> {
            ScrollView v = chat(activity.getWindow().getDecorView());
            values[0] = v.getScrollY();
            values[1] = v.getHeight();
            values[2] = v.getChildAt(0).getHeight();
          });
      proof.put(new JSONArray(values));
    }
    System.out.println("scroll positions " + proof.toString());
    latestMessageVisible();
  }

  @Test
  public void cancellingAfterAutomaticScrollRestoresTheLatestChat() throws Exception {
    ins.runOnMainSync(
        () -> {
          editor.setText("取消后保留草稿");
          editor.setSelection(editor.length());
        });
    latestMessageVisible();
    click("帮我回");
    await("正在看聊天", 4000);
    long until = SystemClock.uptimeMillis() + 7000;
    boolean moved = false;
    while (SystemClock.uptimeMillis() < until) {
      final int[] distance = {0};
      ins.runOnMainSync(
          () -> {
            ScrollView view = chat(activity.getWindow().getDecorView());
            distance[0] = view.getChildAt(0).getHeight() - view.getScrollY() - view.getHeight();
          });
      if (distance[0] > 100) {
        moved = true;
        break;
      }
      SystemClock.sleep(30);
    }
    assertTrue("Automatic earlier-page movement really began", moved);
    click("取消");
    await("q", 5000);
    latestMessageVisible();
    assertEquals("取消后保留草稿", value());
    SystemClock.sleep(1000);
    assertTrue(app.histories.list(50).isEmpty());
    shot("advisor-scroll-cancelled");
  }

  @Test
  public void editingDuringSpeechFinalizationKeepsUserDraft() throws Exception {
    ins.runOnMainSync(
        () -> {
          editor.setText("前文：");
          editor.setSelection(editor.length());
        });
    SystemClock.sleep(350);
    Rect key = new Rect();
    await("空格", 8000).getBoundsInScreen(key);
    long down = SystemClock.uptimeMillis();
    event(MotionEvent.ACTION_DOWN, key.centerX(), key.centerY(), down);
    await("松手结束", 4000);
    File ready = new File(app.getCacheDir(), "product-voice-ready");
    ready.createNewFile();
    SystemClock.sleep(11000);
    shot("voice");
    event(MotionEvent.ACTION_UP, key.centerX(), key.centerY(), down);
    ready.delete();
    await("q", 1000);
    click("，");
    String draft = value();
    long until = SystemClock.uptimeMillis() + 45000;
    boolean finalText = false;
    while (SystemClock.uptimeMillis() < until) {
      for (app.doudou.ime.history.HistoryStore.Entry h : app.histories.list(100))
        if (h.type.equals("voice") && !h.asrText.isEmpty()) finalText = true;
      if (finalText) break;
      SystemClock.sleep(200);
    }
    assertTrue("Real final ASR is retained", finalText);
    SystemClock.sleep(1200);
    assertEquals("Late model output never overwrites the user's edit", draft, value());
    shot("voice-edited-while-waiting");
  }

  private org.json.JSONArray ownJobs() throws Exception {
    okhttp3.Request request =
        new okhttp3.Request.Builder()
            .url(BuildConfig.API_BASE_URL + "/v1/jobs")
            .header("Authorization", "Bearer " + app.cloud.accessToken(app.cloud.userId()))
            .build();
    try (okhttp3.Response response = app.cloud.http.newCall(request).execute()) {
      assertTrue(response.isSuccessful());
      return new JSONArray(response.body().string());
    }
  }
}
