package app.doudou.ime.tests;

import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import android.view.inputmethod.InputMethodManager;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
import org.json.*;
import org.junit.*;
import java.io.*;
import java.util.*;

/** Explicitly enabled live-provider acceptance, restricted to an isolated emulator and sample chat. */
public final class ProductFlowTest {
  private Instrumentation ins;
  private UiAutomation ui;
  private KeyboardApp app;
  private Activity activity;
  private EditText editor;

  @Before public void open() throws Exception {
    Assume.assumeTrue("Live fixture only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    Assume.assumeTrue("Explicit paid-fixture opt-in", "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    app.histories.clear(); // Only the guarded disposable emulator; every sample conversation starts fresh.
    File credentials = new File(app.getCacheDir(), "product-qa.json");
    JSONObject qa = new JSONObject(new String(java.nio.file.Files.readAllBytes(credentials.toPath()), "UTF-8"));
    app.cloud.login(qa.getString("handle"), qa.getString("password"));
    app.cloud.account(); app.personalization.activate();
    app.prefs().edit().putBoolean("personalRewrite", false).apply();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo info = ui.getServiceInfo();
    info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(info);
    ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
    try {
      android.provider.Settings.Secure.putString(app.getContentResolver(), "enabled_accessibility_services", "");
      SystemClock.sleep(400);
      android.provider.Settings.Secure.putString(app.getContentResolver(), "enabled_accessibility_services", "app.doudou.ime/.capture.CaptureService");
      android.provider.Settings.Secure.putInt(app.getContentResolver(), "accessibility_enabled", 1);
    } finally { ui.dropShellPermissionIdentity(); }
    long until = SystemClock.uptimeMillis() + 8000;
    while (CaptureService.current == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(80);
    assertNotNull("Authorized screenshot service bound", CaptureService.current);
    activity = ins.startActivitySync(new Intent(app, PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    editor = field(activity.getWindow().getDecorView()); assertNotNull(editor);
    long keyboardUntil=SystemClock.uptimeMillis()+8000;
    while(node("更多")==null && SystemClock.uptimeMillis()<keyboardUntil) {
      ins.runOnMainSync(()->{editor.requestFocus();((InputMethodManager)app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);});
      SystemClock.sleep(100);
    }
    await("更多", 8000);
  }

  @After public void close() throws Exception {
    if (activity != null) ins.runOnMainSync(activity::finish);
    if (app != null && app.cloud.loggedIn()) { app.cloud.logout(); app.personalization.activate(); }
  }

  private EditText field(View view) {
    if (view instanceof EditText) return (EditText)view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
      EditText found = field(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
    }
    return null;
  }
  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String text) {
    if (n == null) return null;
    if (n.isVisibleToUser() && (text.contentEquals(n.getText() == null ? "" : n.getText()) || text.contentEquals(n.getContentDescription() == null ? "" : n.getContentDescription()))) return n;
    for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo found = find(n.getChild(i), text); if (found != null) return found; }
    return null;
  }
  private AccessibilityNodeInfo node(String text) {
    for (AccessibilityWindowInfo w : ui.getWindows()) {
      AccessibilityNodeInfo n = w.getRoot();
      if (n != null && "app.doudou.ime".contentEquals(n.getPackageName())) { AccessibilityNodeInfo found = find(n, text); if (found != null) return found; }
    }
    return null;
  }
  private AccessibilityNodeInfo await(String text, long timeout) {
    long until = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < until) { AccessibilityNodeInfo n = node(text); if (n != null) return n; SystemClock.sleep(80); }
    throw new AssertionError("Missing product control: " + text);
  }
  private void click(String text) {
    AccessibilityNodeInfo n = await(text, 8000); while (n != null && !n.isClickable()) n = n.getParent();
    assertNotNull(text,n); assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); SystemClock.sleep(120);
  }
  private void shot(String name) throws Exception {
    SystemClock.sleep(180); Bitmap image = ui.takeScreenshot(); assertNotNull(image);
    try (FileOutputStream out = new FileOutputStream(new File(app.getCacheDir(), "test-native-"+name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
    image.recycle();
    if("true".equals(InstrumentationRegistry.getArguments().getString("tour"))) SystemClock.sleep(1200);
  }
  private String value() { final String[] text={""}; ins.runOnMainSync(() -> text[0]=editor.getText().toString()); return text[0]; }
  private void event(int action, float x, float y, long down) {
    MotionEvent e = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
    assertTrue(ui.injectInputEvent(e,true)); e.recycle();
  }

  @Test public void oneTapAdvisorGeneratesAndChosenReplyIsInserted() throws Exception {
    ins.runOnMainSync(() -> { editor.setText("已有草稿："); editor.setSelection(editor.length()); });
    ins.waitForIdleSync(); SystemClock.sleep(350);
    long started = SystemClock.elapsedRealtime();
    click("帮我回");
    await("看懂聊天，想想怎么回…", 10000);
    shot("advisor-loading");
    await("温和回复", 85000);
    long elapsed = SystemClock.elapsedRealtime() - started;
    assertNull("No preparation form", node("生成三条回复"));
    assertEquals("已有草稿：", value());
    shot("advisor");
    String originalReply=await("温和回复",4000).getChild(1).getText().toString();
    click("换一组 · 1次");
    await("看懂聊天，想想怎么回…",5000);
    await("温和回复",85000);
    click("更多");click("上一组");
    assertEquals("Returning to prior replies needs no new request",originalReply,await("温和回复",4000).getChild(1).getText().toString());
    click("温和回复"); await("更多",8000);
    assertTrue(value().startsWith("已有草稿：")); assertTrue(value().length()>6);
    assertFalse("Never sends the practice message", value().isEmpty());
    shot("advisor-inserted");
    JSONObject proof = new JSONObject().put("advisorMilliseconds",elapsed).put("oneTap",true).put("insertedWithoutSending",true);
    try (FileOutputStream out = new FileOutputStream(new File(app.getCacheDir(),"product-advisor-proof.json"))) { out.write(proof.toString().getBytes("UTF-8")); }
  }

  @Test public void spokenAudioStreamsIntoEditorAndReleaseInsertsWithoutSending() throws Exception {
    Assume.assumeTrue("Requires a verified SDK virtual PCM input; unsupported on this host", "true".equals(InstrumentationRegistry.getArguments().getString("virtualPcm")));
    ins.runOnMainSync(() -> { editor.setText("保留前文："); editor.setSelection(editor.length()); });
    ins.waitForIdleSync(); SystemClock.sleep(350);
    Rect key = new Rect(); await("空格",8000).getBoundsInScreen(key);
    long down = SystemClock.uptimeMillis();
    event(MotionEvent.ACTION_DOWN,key.centerX(),key.centerY(),down);
    await("松手填入",8000);
    new File(app.getCacheDir(),"product-voice-ready").createNewFile();
    long until = SystemClock.uptimeMillis()+10000;
    while (value().equals("保留前文：") && SystemClock.uptimeMillis()<until) SystemClock.sleep(100);
    assertTrue("ASR interim appears in real editor while finger is held",value().length()>5);
    shot("voice-live");
    until=SystemClock.uptimeMillis()+12000;
    while (!new File(app.getCacheDir(),"product-voice-audio-done").exists() && SystemClock.uptimeMillis()<until) SystemClock.sleep(100);
    assertTrue("Fixture audio reached emulator microphone",new File(app.getCacheDir(),"product-voice-audio-done").exists());
    event(MotionEvent.ACTION_UP,key.centerX(),key.centerY(),down);
    await("更多",30000);
    shot("voice-inserted");
    try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),"product-voice-proof.json"))) {out.write(new JSONObject().put("result",value()).toString().getBytes("UTF-8"));}
    assertTrue(value().startsWith("保留前文："));
    assertTrue("Not a canned transcript",value().contains("离谱"));
    assertTrue("The end of the spoken sentence is retained",value().contains("解决"));
    assertTrue("Ordinary profanity is preserved",value().contains("他妈"));
    click("撤销"); await("更多",4000);
    assertEquals("保留前文：",value());
  }

  @Test public void holdSpaceSlideUpLocksAndLeftSlideCancels() throws Exception {
    ins.runOnMainSync(() -> { editor.setText("原草稿"); editor.setSelection(editor.length()); });
    ins.waitForIdleSync(); SystemClock.sleep(350);
    Rect key = new Rect(); await("空格",8000).getBoundsInScreen(key);
    long down = SystemClock.uptimeMillis();
    event(MotionEvent.ACTION_DOWN,key.centerX(),key.centerY(),down);
    await("松手填入 · 上滑免按 · 左滑取消",4000);
    event(MotionEvent.ACTION_MOVE,key.centerX()-240,key.centerY(),down);
    await("松手取消",2000); shot("voice-cancel");
    event(MotionEvent.ACTION_UP,key.centerX()-240,key.centerY(),down);
    await("更多",4000); assertEquals("原草稿",value());
    await("空格",4000).getBoundsInScreen(key); down=SystemClock.uptimeMillis();
    event(MotionEvent.ACTION_DOWN,key.centerX(),key.centerY(),down);
    await("松手填入 · 上滑免按 · 左滑取消",4000);
    event(MotionEvent.ACTION_MOVE,key.centerX(),key.centerY()-240,down);
    await("松手后免按说话",2000);
    event(MotionEvent.ACTION_UP,key.centerX(),key.centerY()-240,down);
    await("免按说话 · 点空格完成",3000); shot("voice");
    ins.runOnMainSync(() -> ((InputMethodManager)app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0));
    SystemClock.sleep(250);
    ins.runOnMainSync(() -> { editor.requestFocus(); ((InputMethodManager)app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT); });
    await("更多",8000); assertTrue(await("q",4000).isEnabled()); assertEquals("原草稿",value());
    shot("voice-restored");
  }
}
