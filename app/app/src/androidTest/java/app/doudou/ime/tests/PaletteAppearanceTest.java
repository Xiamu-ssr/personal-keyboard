package app.doudou.ime.tests;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import app.doudou.ime.advisor.PaletteActivity;
import app.doudou.ime.advisor.PaletteDefinition;
import app.doudou.ime.advisor.PaletteStore;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.app.MainActivity;
import app.doudou.ime.app.PracticeActivity;
import app.doudou.ime.design.Skin;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Native UI acceptance for an already running, isolated ranchu emulator. The three offline tests
 * require a guest session. The optional live test requires an already logged-in QA account and
 * -e liveModels true; it uses only palette GET/POST, never login/logout or a model endpoint.
 * No test clears app data, changes permissions/IME, or starts an emulator.
 */
@RunWith(AndroidJUnit4.class)
public final class PaletteAppearanceTest {
  private static final String OWNER = "paletteAppearanceTestOwner";
  private static final long TIMEOUT = 10000;
  private final String owner = UUID.randomUUID().toString();
  private final Set<Activity> owned = Collections.newSetFromMap(new IdentityHashMap<>());
  private Instrumentation ins;
  private KeyboardApp app;
  private UiAutomation ui;
  private String originalSkin, originalAccount;
  private boolean hadSkin, hadSetup, originalSetup, originalLogin, prepared;
  private int originalAccessibilityFlags;

  @Before
  public void isolatedEmulatorOnly() {
    assumeTrue("Use an isolated ranchu emulator; never a physical device", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    app = (KeyboardApp) ins.getTargetContext().getApplicationContext();
    SharedPreferences prefs = app.prefs();
    hadSkin = prefs.contains("skin");
    originalSkin = prefs.getString("skin", "dark");
    hadSetup = prefs.contains("setupComplete");
    originalSetup = prefs.getBoolean("setupComplete", false);
    originalAccount = app.cloud.userId();
    originalLogin = app.cloud.loggedIn();
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    AccessibilityServiceInfo service = ui.getServiceInfo();
    originalAccessibilityFlags = service.flags;
    service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(service);
    prepared = true;
  }

  @After
  public void restoreOnlyThisTestsState() throws Exception {
    if (!prepared) return;
    try {
      finishOwned();
    } finally {
      SharedPreferences.Editor restore = app.prefs().edit();
      if (hadSkin) restore.putString("skin", originalSkin); else restore.remove("skin");
      if (hadSetup) restore.putBoolean("setupComplete", originalSetup); else restore.remove("setupComplete");
      assertTrue("Restore appearance/setup preferences", restore.commit());
      onMain(() -> { Skin.load(app); return null; });
      AccessibilityServiceInfo service = ui.getServiceInfo();
      service.flags = originalAccessibilityFlags;
      ui.setServiceInfo(service);
    }
    assertTrue("The test must not change accounts", originalAccount.equals(app.cloud.userId()));
    assertEquals("The test must not log in or out", originalLogin, app.cloud.loggedIn());
  }

  private void guestOnly() {
    assumeFalse("Offline acceptance requires a guest fixture; do not log out an existing account", app.cloud.loggedIn());
    assumeTrue("Offline acceptance requires no cached account identity", app.cloud.userId().isEmpty());
    assertTrue(app.prefs().edit().putBoolean("setupComplete", true).commit());
  }

  @Test
  public void nineAttributeContractAndLegacyCacheMigrationReachNativeHome() throws Exception {
    guestOnly();
    JSONArray fields = PaletteDefinition.fields(app);
    assertEquals(2, PaletteDefinition.contract(app).getInt("schemaVersion"));
    assertEquals(9, fields.length());
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < fields.length(); i++) assertTrue(ids.add(fields.getJSONObject(i).getString("id")));
    assertEquals(new HashSet<>(Arrays.asList("length", "formality", "directness", "warmth",
        "emotion", "audience", "rhetoric", "emoji", "structure")), ids);
    JSONObject inherited = PaletteDefinition.inherit(app);
    assertEquals(0, inherited.getJSONArray("rhetoric").length());
    JSONObject explicitPlain = new JSONObject(inherited.toString()).put("rhetoric", new JSONArray().put("plain"));
    assertEquals("plain", PaletteDefinition.attributes(app, explicitPlain).getJSONArray("rhetoric").getString(0));
    JSONObject romantic = new JSONObject(inherited.toString()).put("rhetoric", new JSONArray().put("flirt").put("coquettish"));
    assertEquals(2, PaletteDefinition.attributes(app, romantic).getJSONArray("rhetoric").length());
    try {
      PaletteDefinition.attributes(app, new JSONObject(inherited.toString())
          .put("rhetoric", new JSONArray().put("plain").put("flirt")));
      fail("Explicit plain must not silently coexist with flirt");
    } catch (JSONException expected) { /* contradictory configuration must be rejected */ }

    // Deliberately permute legacy expressions: migration must map expression, not slot index.
    String[] oldTypes = {"witty", "natural", "playful", "direct", "considered", "caring"};
    String[] names = {"夜聊", "工作直说", "粉红泡泡", "拒绝邀约", "一针见血", "安慰"};
    JSONArray legacySlots = new JSONArray();
    for (int i = 0; i < 6; i++) legacySlots.put(new JSONObject()
        .put("id", "slot" + (i + 1)).put("name", names[i]).put("expression", oldTypes[i]));
    String key = "palette:" + app.cloud.userId();
    String previous = app.store.meta(key, null);
    try {
      app.store.setMeta(key, new JSONObject().put("version", 37).put("slots", legacySlots).toString());
      MainActivity first = launch(MainActivity.class, null);
      for (String name : names) reveal("编辑" + name);
      shot("migration-home");
      JSONObject migrated = new JSONObject(app.store.meta(key, ""));
      assertEquals(2, migrated.getInt("schemaVersion"));
      assertEquals("Local migration must not invent a Cloud revision", 37, migrated.getInt("version"));
      JSONArray slots = migrated.getJSONArray("slots");
      assertEquals(6, slots.length());
      for (int i = 0; i < 6; i++) {
        JSONObject slot = slots.getJSONObject(i);
        assertEquals("slot" + (i + 1), slot.getString("id"));
        assertEquals(names[i], slot.getString("name"));
        assertFalse(slot.has("expression"));
        assertEquals(9, slot.getJSONObject("attributes").length());
        jsonEquals("legacy " + oldTypes[i], template(oldTypes[i]).getJSONObject("attributes"), slot.getJSONObject("attributes"));
      }
      String once = app.store.meta(key, "");
      onMain(() -> { first.recreate(); return null; });
      MainActivity recreated = resumed(MainActivity.class, first);
      destroyed(first);
      for (String name : names) reveal("编辑" + name);
      assertEquals("Reopening migrated cache must be idempotent", once, app.store.meta(key, ""));
      assertNotSame(first, recreated);
    } finally {
      finishOwned();
      if (previous == null) app.store.getWritableDatabase().delete("meta", "key=?", new String[] {key});
      else app.store.setMeta(key, previous);
    }
  }

  @Test
  public void nativeSkinChoicesRecreateAndPersistAcrossActivityRelaunch() throws Exception {
    guestOnly();
    MainActivity main = launch(MainActivity.class, null);
    click("我的");
    await("皮肤");
    boolean initialLight = "light".equals(app.prefs().getString("skin", "dark"));
    main = changeSkin(main, !initialLight);
    assertSettingsContrast(main);
    checkHomeThenReturnToSettings(main);
    main = changeSkin(main, initialLight);
    assertSettingsContrast(main);
    checkHomeThenReturnToSettings(main);
    finishOwned();
    MainActivity reopened = launch(MainActivity.class, null);
    assertSurface(reopened, initialLight);
    assertHomeContrast(reopened);
    click("我的");
    assertSkinChoice(initialLight);
    shot("skin-persisted-" + (initialLight ? "light" : "dark"));
  }

  @Test
  public void practiceDraftSelectionAndLocalMessagesSurviveSkinRecreation() throws Exception {
    guestOnly();
    for (String scene : new String[] {"chat", "comments"}) {
      PracticeActivity before = launch(PracticeActivity.class, new BundleBuilder().string("scene", scene).value);
      await(scene.equals("comments") ? "评论练习" : "和小林聊天");
      String sent = "换肤前已发送的本机练习消息：" + scene;
      setEditor(sent);
      click("发送"); // PracticeActivity appends locally; no person/model is contacted.
      await(sent);
      assertPracticeContrast(before, true);
      String draft = "这段草稿还没发送，换肤后继续写：" + scene;
      setEditor(draft);
      Bundle selection = new Bundle();
      selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 2);
      selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 7);
      assertTrue(editor().performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection));
      assertEditor(draft, 2, 7);
      shot("practice-" + scene + "-before");
      boolean nextLight = !"light".equals(app.prefs().getString("skin", "dark"));
      // Open real settings above the existing practice instance, without resetting the scene.
      onMain(() -> { before.startActivity(intent(MainActivity.class, null, false)); return null; });
      MainActivity settings = resumed(MainActivity.class, null);
      click("我的");
      settings = changeSkin(settings, nextLight);
      assertSettingsContrast(settings);
      back();
      await("试试豆豆");
      back();
      PracticeActivity after = resumed(PracticeActivity.class, before);
      destroyed(before);
      assertSurface(after, nextLight);
      assertEditor(draft, 2, 7);
      await(sent);
      onMain(() -> {
        assertEquals("A saved draft must not become a sent bubble", 0, nonEditorTextCount(content(after), draft));
        return null;
      });
      assertPracticeContrast(after, false);
      shot("practice-" + scene + "-after");
      finishOwned();
    }
  }

  @Test
  public void livePaletteTemplateAndManualAttributeSurviveSaveAndReopen() throws Exception {
    assumeTrue("Explicit Cloud palette acceptance only", "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    assertTrue("Pre-login the ordinary QA account outside this test; this test never logs in", app.cloud.loggedIn());
    File fixture = new File(app.getCacheDir(), "product-qa.json");
    assertTrue("Missing pre-provisioned ordinary QA fixture", fixture.isFile());
    JSONObject qa = new JSONObject(new String(Files.readAllBytes(fixture.toPath()), StandardCharsets.UTF_8));
    assertTrue("The current account must match the ordinary QA fixture", qa.getString("handle").equals(app.cloud.handle()));
    String account = app.cloud.userId();
    PaletteStore store = new PaletteStore(app);
    JSONObject original = new JSONObject(store.sync().toString());
    boolean attemptedSave = false;
    try {
      launch(PaletteActivity.class, new BundleBuilder().integer("slot", 0).value);
      await("参考搭配");
      assertFalse("Save must be disabled after loading an unchanged slot", await("保存").isEnabled());
      setText(await("表达名称"), "夜聊验收");
      click("参考搭配");
      click("暧昧");
      assertEquals("Choosing a reference must preserve the custom slot name",
          "夜聊验收", await("表达名称").getText().toString());
      click("Emoji设置");
      click("Emoji：不用"); // A manual change beyond choosing a template (template uses 'few').
      assertTrue(reveal("Emoji：不用").isChecked());
      shot("live-editor-before-save");
      attemptedSave = true; // Set before the click: a timeout can still follow a committed save.
      click("保存");
      waitForNoResumed(PaletteActivity.class);
      assertTrue("Account changed during save", account.equals(app.cloud.userId()));
      JSONObject changed = store.sync();
      JSONObject first = changed.getJSONArray("slots").getJSONObject(0);
      assertEquals("slot1", first.getString("id"));
      assertEquals("夜聊验收", first.getString("name"));
      JSONObject expected = new JSONObject(template("flirt").getJSONObject("attributes").toString()).put("emoji", "none");
      jsonEquals("saved template plus manual override", expected, first.getJSONObject("attributes"));
      for (int i = 1; i < 6; i++) jsonEquals("untouched slot " + i,
          original.getJSONArray("slots").getJSONObject(i), changed.getJSONArray("slots").getJSONObject(i));
      launch(PaletteActivity.class, new BundleBuilder().integer("slot", 0).value);
      assertEquals("夜聊验收", await("表达名称").getText().toString());
      assertFalse("Reopened saved slot must start unchanged", await("保存").isEnabled());
      assertTrue(reveal("表达手法：暧昧试探").isChecked());
      click("Emoji设置");
      assertTrue(reveal("Emoji：不用").isChecked());
      assertFalse(reveal("Emoji：少量").isChecked());
      assertFalse("Expanding an attribute must not enable Save", await("保存").isEnabled());
      shot("live-editor-reopened");
    } finally {
      finishOwned();
      if (attemptedSave) {
        assertTrue("Restore only the original QA account", account.equals(app.cloud.userId()));
        JSONObject current = store.sync(), first = original.getJSONArray("slots").getJSONObject(0);
        store.save(account, current, 0, first.getString("name"), first.getJSONObject("attributes"));
        jsonEquals("QA slot restored", first, store.sync().getJSONArray("slots").getJSONObject(0));
      }
    }
  }

  private MainActivity changeSkin(MainActivity old, boolean light) throws Exception {
    click(light ? "亮色" : "暗色");
    MainActivity next = resumed(MainActivity.class, old);
    destroyed(old);
    assertSkinChoice(light);
    assertSurface(next, light);
    shot("skin-" + (light ? "light" : "dark"));
    return next;
  }

  private void assertSkinChoice(boolean light) {
    await("皮肤");
    assertEquals(light ? "light" : "dark", app.prefs().getString("skin", "dark"));
    AccessibilityNodeInfo selected = reveal(light ? "亮色" : "暗色");
    assertTrue("Selected skin must be exposed by the native control", selected.isSelected());
    assertTrue(String.valueOf(selected.getContentDescription()).contains("已选择"));
    assertFalse(reveal(light ? "暗色" : "亮色").isSelected());
  }

  private void assertSurface(Activity activity, boolean light) throws Exception {
    onMain(() -> {
      double value = luminance(background(content(activity)));
      assertTrue("The rebuilt native surface must actually change luminance", light ? value > 0.7 : value < 0.1);
      return null;
    });
  }

  private void assertSettingsContrast(MainActivity activity) throws Exception {
    onMain(() -> {
      for (String label : new String[] {"皮肤", "亮色", "暗色", "语音与帮我回"}) contrast(textView(content(activity), label), false);
      return null;
    });
  }

  private void assertHomeContrast(MainActivity activity) throws Exception {
    onMain(() -> {
      // HomeScreen.practiceAction includes the inline chevron in the actual TextView text.
      for (String label : new String[] {"试试豆豆", "聊天  ›", "评论  ›", "调色盘"}) {
        TextView sample = textView(content(activity), label);
        assertNotNull("Missing native Home contrast sample: " + label, sample);
        contrast(sample, false);
      }
      return null;
    });
  }

  private void checkHomeThenReturnToSettings(MainActivity activity) throws Exception {
    click("输入");
    await("试试豆豆");
    assertHomeContrast(activity);
    shot("home-" + app.prefs().getString("skin", "dark"));
    click("我的");
    await("皮肤");
  }

  private void assertPracticeContrast(PracticeActivity activity, boolean empty) throws Exception {
    onMain(() -> {
      contrast(textView(content(activity), "本机练习"), false);
      contrast(textView(content(activity), "发送"), false);
      EditText edit = editView(content(activity));
      assertNotNull("Native practice editor", edit);
      contrast(edit, empty);
      return null;
    });
  }

  /** Read actual views/drawables, including ancestor backgrounds; never compare Ui color getters. */
  private void contrast(TextView view, boolean hint) {
    assertNotNull("Missing native contrast sample", view);
    assertTrue("Contrast sample must be visible", view.isShown() && view.getGlobalVisibleRect(new Rect()));
    if (hint) assertEquals("Hint sample must really be displayed", 0, view.length());
    int bg = background(view), fg = composite(hint ? view.getCurrentHintTextColor() : view.getCurrentTextColor(), bg);
    double a = luminance(fg), b = luminance(bg), ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    assertTrue("Native text/background contrast below 4.5:1 for " + (hint ? view.getHint() : view.getText()) + ": " + ratio, ratio >= 4.5);
  }

  private int background(View view) {
    int result = Color.TRANSPARENT;
    for (View current = view; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null) {
      result = composite(result, drawableColor(current.getBackground(), current.getDrawableState()));
      if (Color.alpha(result) == 255) return result;
    }
    throw new AssertionError("No opaque native background for contrast sample");
  }

  private int drawableColor(Drawable drawable, int[] state) {
    if (drawable == null) return Color.TRANSPARENT;
    if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
    if (drawable instanceof GradientDrawable) {
      android.content.res.ColorStateList colors = ((GradientDrawable) drawable).getColor();
      assertNotNull("Gradient backgrounds need a separate pixel contrast check", colors);
      return colors.getColorForState(state, colors.getDefaultColor());
    }
    if (drawable instanceof LayerDrawable) {
      LayerDrawable layers = (LayerDrawable) drawable;
      int result = Color.TRANSPARENT;
      for (int i = 0; i < layers.getNumberOfLayers(); i++) {
        if (layers.getId(i) != android.R.id.mask) result = composite(drawableColor(layers.getDrawable(i), state), result);
      }
      return result; // Ripple mask is geometry, not the visible button fill.
    }
    Drawable current = drawable.getCurrent();
    if (current != drawable) return drawableColor(current, state);
    throw new AssertionError("Unsupported native contrast background: " + drawable.getClass().getSimpleName());
  }

  private static int composite(int fg, int bg) {
    double fa = Color.alpha(fg) / 255.0, ba = Color.alpha(bg) / 255.0, alpha = fa + ba * (1 - fa);
    if (alpha == 0) return Color.TRANSPARENT;
    return Color.argb((int) Math.round(alpha * 255),
        (int) Math.round((Color.red(fg) * fa + Color.red(bg) * ba * (1 - fa)) / alpha),
        (int) Math.round((Color.green(fg) * fa + Color.green(bg) * ba * (1 - fa)) / alpha),
        (int) Math.round((Color.blue(fg) * fa + Color.blue(bg) * ba * (1 - fa)) / alpha));
  }

  private static double luminance(int color) {
    return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color));
  }

  private static double linear(int channel) {
    double value = channel / 255.0;
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  private View content(Activity activity) { return ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0); }

  private TextView textView(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
      TextView found = textView(((ViewGroup) view).getChildAt(i), text);
      if (found != null) return found;
    }
    return null;
  }

  private EditText editView(View view) {
    if (view instanceof EditText) return (EditText) view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
      EditText found = editView(((ViewGroup) view).getChildAt(i));
      if (found != null) return found;
    }
    return null;
  }

  private int nonEditorTextCount(View view, String text) {
    int count = view instanceof TextView && !(view instanceof EditText) && text.contentEquals(((TextView) view).getText()) ? 1 : 0;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) count += nonEditorTextCount(((ViewGroup) view).getChildAt(i), text);
    return count;
  }

  private Intent intent(Class<? extends Activity> type, Bundle extras, boolean newTask) {
    Intent value = new Intent(app, type).putExtra(OWNER, owner);
    if (extras != null) value.putExtras(extras);
    if (newTask) value.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    return value;
  }

  private <T extends Activity> T launch(Class<T> type, Bundle extras) throws Exception {
    T activity = type.cast(ins.startActivitySync(intent(type, extras, true)));
    owned.add(activity);
    return resumed(type, null);
  }

  private <T extends Activity> T resumed(Class<T> type, Activity previous) throws Exception {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      T found = onMain(() -> {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
          if (type.isInstance(activity) && activity != previous && owner.equals(activity.getIntent().getStringExtra(OWNER)) && !activity.isFinishing()) {
            owned.add(activity);
            return type.cast(activity);
          }
        }
        return null;
      });
      if (found != null) { ins.waitForIdleSync(); return found; }
      SystemClock.sleep(80);
    }
    throw new AssertionError("No new resumed " + type.getSimpleName());
  }

  private void destroyed(Activity old) throws Exception {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      if (onMain(old::isDestroyed)) return;
      SystemClock.sleep(80);
    }
    fail("Skin change/recreate must destroy the previous Activity instance");
  }

  private void waitForNoResumed(Class<? extends Activity> type) throws Exception {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      boolean present = onMain(() -> {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
          if (type.isInstance(activity) && owner.equals(activity.getIntent().getStringExtra(OWNER))) return true;
        return false;
      });
      if (!present) return;
      SystemClock.sleep(80);
    }
    fail("Save did not close the palette editor");
  }

  private void finishOwned() throws Exception {
    onMain(() -> {
      // Also discover a recreated instance if an assertion failed before resumed() recorded it.
      for (Stage stage : new Stage[] {Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED}) {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
          if (owner.equals(activity.getIntent().getStringExtra(OWNER))) owned.add(activity);
      }
      for (Activity activity : new ArrayList<>(owned)) if (!activity.isDestroyed() && !activity.isFinishing()) activity.finish();
      return null;
    });
    ins.waitForIdleSync();
  }

  /** Bubble UI-thread failures back to JUnit instead of crashing the app's main Looper. */
  private <T> T onMain(Callable<T> action) throws Exception {
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    ins.runOnMainSync(() -> { try { value.set(action.call()); } catch (Throwable e) { error.set(e); } });
    if (error.get() instanceof Error) throw (Error) error.get();
    if (error.get() instanceof Exception) throw (Exception) error.get();
    return value.get();
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label, boolean editable) {
    if (node == null) return null;
    if (node.isVisibleToUser() && (editable ? node.isEditable() :
        label.contentEquals(node.getText() == null ? "" : node.getText()) ||
        label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))) return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo found = find(node.getChild(i), label, editable);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String label, boolean editable) {
    for (AccessibilityWindowInfo window : ui.getWindows()) {
      if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
      AccessibilityNodeInfo root = window.getRoot();
      if (root == null || !app.getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) continue;
      AccessibilityNodeInfo found = find(root, label, editable);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo await(String label) {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo found = node(label, false);
      if (found != null) return found;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Missing native control: " + label);
  }

  private AccessibilityNodeInfo reveal(String label) {
    for (int i = 0; i < 12; i++) {
      AccessibilityNodeInfo found = node(label, false);
      if (found != null) return found;
      boolean moved = false;
      for (AccessibilityWindowInfo window : ui.getWindows()) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
        AccessibilityNodeInfo root = window.getRoot();
        if (root != null && app.getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) moved |= scroll(root);
      }
      if (!moved) break;
      ins.waitForIdleSync();
      SystemClock.sleep(150);
    }
    return await(label);
  }

  private boolean scroll(AccessibilityNodeInfo node) {
    if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child != null && scroll(child)) return true;
    }
    return false;
  }

  private void click(String label) {
    AccessibilityNodeInfo target = reveal(label);
    while (target != null && !target.isClickable()) target = target.getParent();
    assertNotNull("Clickable native control: " + label, target);
    assertTrue("Native click: " + label, target.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    ins.waitForIdleSync();
  }

  private AccessibilityNodeInfo editor() {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo found = node("", true);
      if (found != null) return found;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Missing native editor");
  }

  private void setText(AccessibilityNodeInfo target, String value) {
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    assertTrue("Set native editor text", target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
    ins.waitForIdleSync();
  }

  private void setEditor(String value) { setText(editor(), value); }

  private void assertEditor(String text, int start, int end) {
    AccessibilityNodeInfo edit = editor();
    assertEquals(text, String.valueOf(edit.getText()));
    assertEquals("Draft selection start", start, edit.getTextSelectionStart());
    assertEquals("Draft selection end", end, edit.getTextSelectionEnd());
  }

  private void back() {
    assertTrue(ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
    ins.waitForIdleSync();
  }

  private void shot(String name) throws Exception {
    File file = new File(app.getCacheDir(), "test-native-palette-" + name + ".png");
    Files.deleteIfExists(file.toPath()); // A failed capture must not leave a previous run's evidence.
    ScreenshotWindow window = screenshotWindow();
    awaitCommittedFrame(window.decor);
    ScreenshotProbe probe = onMain(() -> {
      int[] position = new int[2];
      window.root.getLocationOnScreen(position);
      // Both page margins are outside the header controls and expose the actual root background.
      int y = position[1] + window.root.getPaddingTop()
          + Math.max(2, Math.round(12 * window.root.getResources().getDisplayMetrics().density));
      assertTrue("Screenshot probe must be inside the native content", y < position[1] + window.root.getHeight());
      return new ScreenshotProbe(background(window.root), position[0] + 1,
          position[0] + window.root.getWidth() - 2, y);
    });
    Bitmap previous = null;
    boolean saved = false;
    int stable = 0;
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    try {
      while (SystemClock.uptimeMillis() < deadline) {
        assertTrue("Screenshot window changed before capture: " + name,
            onMain(() -> window.root.isAttachedToWindow() && window.decor.hasWindowFocus()));
        Bitmap current = ui.takeScreenshot();
        assertNotNull("Native screenshot: " + name, current);
        boolean matches = probe.matches(current);
        if (!matches) stable = 0;
        else if (previous != null && previous.sameAs(current)) stable++;
        else stable = 1;
        if (previous != null) previous.recycle();
        previous = current;
        if (stable >= 3) {
          try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue("Save native screenshot", current.compress(Bitmap.CompressFormat.PNG, 100, output));
          }
          saved = true;
          return;
        }
        SystemClock.sleep(100);
      }
      fail("No three stable native frames matching the current window background: " + name);
    } finally {
      if (previous != null) previous.recycle();
      if (!saved) Files.deleteIfExists(file.toPath());
    }
  }

  private ScreenshotWindow screenshotWindow() throws Exception {
    long deadline = SystemClock.uptimeMillis() + TIMEOUT;
    while (SystemClock.uptimeMillis() < deadline) {
      ScreenshotWindow window = onMain(() -> {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
          if (!owner.equals(activity.getIntent().getStringExtra(OWNER)) || activity.isFinishing()) continue;
          View decor = activity.getWindow().getDecorView(), root = content(activity);
          if (decor.hasWindowFocus() && root.isAttachedToWindow() && root.isLaidOut()
              && root.getWidth() > 3 && root.getHeight() > 0) return new ScreenshotWindow(decor, root);
        }
        return null;
      });
      if (window != null) return window;
      SystemClock.sleep(80);
    }
    throw new AssertionError("No focused, laid-out test window for native screenshot");
  }

  private void awaitCommittedFrame(View decor) throws Exception {
    CountDownLatch committed = new CountDownLatch(1);
    Runnable callback = committed::countDown;
    ViewTreeObserver observer = onMain(() -> {
      assertTrue("Frame commit capture requires the Activities' hardware renderer", decor.isHardwareAccelerated());
      ViewTreeObserver tree = decor.getViewTreeObserver();
      tree.registerFrameCommitCallback(callback);
      decor.postInvalidateOnAnimation();
      return tree;
    });
    try {
      assertTrue("Native window did not commit a newly drawn frame", committed.await(TIMEOUT, TimeUnit.MILLISECONDS));
    } finally {
      onMain(() -> { if (observer.isAlive()) observer.unregisterFrameCommitCallback(callback); return null; });
    }
    // A committed frame can still be waiting for display composition; shot() checks native pixels.
  }

  private static final class ScreenshotWindow {
    final View decor, root;
    ScreenshotWindow(View decor, View root) { this.decor = decor; this.root = root; }
  }

  private static final class ScreenshotProbe {
    final int background, left, right, y;
    ScreenshotProbe(int background, int left, int right, int y) {
      this.background = background; this.left = left; this.right = right; this.y = y;
    }
    boolean matches(Bitmap bitmap) {
      return left >= 0 && right < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()
          && near(bitmap.getPixel(left, y)) && near(bitmap.getPixel(right, y));
    }
    private boolean near(int color) {
      return Math.abs(Color.red(color) - Color.red(background)) <= 2
          && Math.abs(Color.green(color) - Color.green(background)) <= 2
          && Math.abs(Color.blue(color) - Color.blue(background)) <= 2;
    }
  }

  private JSONObject template(String id) throws JSONException {
    JSONArray templates = PaletteDefinition.templates(app);
    for (int i = 0; i < templates.length(); i++) if (id.equals(templates.getJSONObject(i).getString("id"))) return templates.getJSONObject(i);
    throw new AssertionError("Missing contract template: " + id);
  }

  private void jsonEquals(String path, Object expected, Object actual) throws JSONException {
    if (expected instanceof JSONObject && actual instanceof JSONObject) {
      JSONObject a = (JSONObject) expected, b = (JSONObject) actual;
      assertEquals(path + " key count", a.length(), b.length());
      for (Iterator<String> keys = a.keys(); keys.hasNext();) {
        String key = keys.next();
        assertTrue(path + " missing key " + key, b.has(key));
        jsonEquals(path + "." + key, a.get(key), b.get(key));
      }
    } else if (expected instanceof JSONArray && actual instanceof JSONArray) {
      JSONArray a = (JSONArray) expected, b = (JSONArray) actual;
      assertEquals(path + " array length", a.length(), b.length());
      for (int i = 0; i < a.length(); i++) jsonEquals(path + "[" + i + "]", a.get(i), b.get(i));
    } else assertEquals(path, expected, actual);
  }

  private static final class BundleBuilder {
    final Bundle value = new Bundle();
    BundleBuilder string(String key, String text) { value.putString(key, text); return this; }
    BundleBuilder integer(String key, int number) { value.putInt(key, number); return this; }
  }
}
