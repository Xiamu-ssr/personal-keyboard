package app.doudou.ime.input;

import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.design.*;
import app.doudou.ime.input.engine.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;

/** Real Android measurement, picker and indexed dictionary. Only isolated, synthetic QA content. */
public class BaseKeyboardTest {
  private Instrumentation ins;
  private Activity activity;
  private Context context;
  private LinearLayout panel;
  private FrameLayout stage;
  private int returns, held;
  private final StringBuilder entered = new StringBuilder();
  private boolean originalLight;

  @Before public void open() {
    Assume.assumeTrue("Owned emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class)
        .putExtra("scene", "comments")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    originalLight = Skin.light();
    context = activity;
    ins.runOnMainSync(() -> {
      stage = new FrameLayout(activity);
      stage.setBackgroundColor(Ui.bg());
      activity.setContentView(stage);
    });
  }

  @After public void close() {
    if (activity != null) ins.runOnMainSync(() -> {
      Skin.select(activity, originalLight);
      activity.finish();
    });
  }

  private void mount(View view) {
    stage.removeAllViews();
    FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(Ui.dp(context, 360),
        Ui.dp(context, KeyboardView.HEIGHT_DP), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    if (stage.getRootWindowInsets() != null)
      size.bottomMargin = stage.getRootWindowInsets().getInsets(WindowInsets.Type.navigationBars()).bottom;
    stage.addView(view, size);
  }

  private EditorHost host() {
    return (EditorHost) Proxy.newProxyInstance(EditorHost.class.getClassLoader(),
        new Class<?>[] {EditorHost.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "context": return context;
            case "services": return (KeyboardApp) activity.getApplication();
            case "privateMode": return true;
            case "openPanel": panel = Ui.col(context); mount(panel); return panel;
            case "letters": returns++; return null;
            case "insert": entered.append((String) args[0]); return true;
            case "generation": return 1L;
            case "mode": return "typing";
          }
          if (method.getReturnType() == boolean.class) return false;
          if (method.getReturnType() == int.class) return 0;
          if (method.getReturnType() == long.class) return 0L;
          return null;
        });
  }

  private KeyboardView keyboard(boolean numbers) {
    return keyboard(numbers ? KeyboardPage.NUMBERS : KeyboardPage.LETTERS);
  }

  private KeyboardView keyboard(KeyboardPage page) {
    KeyboardView.Actions actions = (KeyboardView.Actions) Proxy.newProxyInstance(
        KeyboardView.Actions.class.getClassLoader(), new Class<?>[] {KeyboardView.Actions.class},
        (proxy, method, args) -> {
          if (method.getName().equals("letter")) {
            entered.append((Character) args[0]);
            if (page == KeyboardPage.SYMBOLS) mount(keyboard(KeyboardPage.LETTERS));
          }
          if (method.getName().equals("symbolPanel")) mount(keyboard(KeyboardPage.SYMBOLS));
          if (method.getName().equals("numberPad")) mount(keyboard(KeyboardPage.NUMBER_PAD));
          if (method.getName().equals("numbers")) mount(keyboard(KeyboardPage.NUMBERS));
          if (method.getName().equals("letters")) { returns++; mount(keyboard(KeyboardPage.LETTERS)); }
          if (method.getName().equals("holdVoice")) held++;
          return null;
        });
    return new KeyboardView(context, false, false, page, false, false, actions, null);
  }

  private View find(View view, String label) {
    if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
      return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View result = find(((ViewGroup) view).getChildAt(i), label);
        if (result != null) return result;
      }
    return null;
  }

  private void settle() {
    ins.waitForIdleSync();
    SystemClock.sleep(120);
  }

  private void screenshot(String name) throws Exception {
    settle();
    Bitmap bitmap = ins.getUiAutomation().takeScreenshot();
    File file = new File(activity.getExternalFilesDir(null), "base-" + name + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  @Test public void uppercaseKeycapsEmitLowercaseAndSpellingSurvivesCandidateRefresh() throws Exception {
    final KeyboardView[] view = new KeyboardView[1];
    ins.runOnMainSync(() -> {
      view[0] = keyboard(false); mount(view[0]);
      assertEquals("Q", ((TextView) find(view[0], "q")).getText().toString());
      find(view[0], "q").performClick();
      view[0].clearCandidates();
      view[0].setComposing("nihsao");
      view[0].addCandidate("你好", () -> {});
      view[0].clearCandidates();
      view[0].addCandidate("你稍", () -> {});
    });
    settle();
    assertEquals("q", entered.toString());
    View spelling = find(view[0], "正在输入拼音：nihsao");
    Rect visible = new Rect();
    assertTrue(spelling.getGlobalVisibleRect(visible));
    assertTrue(visible.height() > 0);
    assertEquals(Ui.dp(context, KeyboardView.HEIGHT_DP), view[0].getHeight());
    screenshot("spelling-dark");
    View space = find(view[0], "空格");
    ins.runOnMainSync(() -> {
      long now = SystemClock.uptimeMillis();
      MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
          space.getWidth() / 2f, space.getHeight() / 2f, 0);
      space.dispatchTouchEvent(down); down.recycle();
    });
    SystemClock.sleep(350);
    ins.runOnMainSync(() -> {
      long now = SystemClock.uptimeMillis();
      MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
          space.getWidth() / 2f, space.getHeight() / 2f, 0);
      space.dispatchTouchEvent(up); up.recycle();
    });
    assertEquals(1, held);
  }

  @Test public void emojiReturnStaysOutsideScrollablePickerAtLargeType() throws Exception {
    Configuration config = new Configuration(context.getResources().getConfiguration());
    config.fontScale = 1.3f;
    context = new ContextThemeWrapper(activity.createConfigurationContext(config), Skin.theme());
    ins.runOnMainSync(() -> new EmojiPanel(host()).show());
    settle();
    View back = find(panel, "返回键盘");
    Rect before = new Rect();
    assertTrue(back.getGlobalVisibleRect(before));
    assertEquals(Ui.dp(context, 48), before.height());
    ins.runOnMainSync(() -> scrollPicker(panel.getChildAt(1)));
    settle();
    Rect after = new Rect();
    assertTrue(back.getGlobalVisibleRect(after));
    assertEquals(before, after);
    screenshot("emoji-large");
    ins.runOnMainSync(back::performClick);
    assertEquals(1, returns);
  }

  @Test public void mainDigitsAndChineseFinancialDigitsAreReachable() throws Exception {
    ins.runOnMainSync(() -> mount(keyboard(true)));
    settle();
    for (String digit : Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) {
      View key = find(stage, digit);
      Rect visible = new Rect(); assertTrue(key.getGlobalVisibleRect(visible));
      assertTrue(visible.width() >= Ui.dp(context, 24));
      ins.runOnMainSync(key::performClick);
    }
    assertEquals("1234567890", entered.toString());
    screenshot("numbers-dark");
    ins.runOnMainSync(() -> find(stage, "数字").performClick());
    settle();
    screenshot("number-pad");
    for (String digit : Arrays.asList("1", "2", "3", "0")) {
      Rect visible = new Rect(); assertTrue("Number pad digit is visible: " + digit, find(stage, digit).getGlobalVisibleRect(visible));
      assertTrue(visible.width() >= Ui.dp(context, 48));
    }
    screenshot("number-pad");
    for (String digit : Arrays.asList("壹", "贰", "叁")) {
      ins.runOnMainSync(() -> mount(keyboard(KeyboardPage.SYMBOLS)));
      ins.runOnMainSync(() -> find(stage, "序号").performClick());
      settle();
      ins.runOnMainSync(() -> find(stage, digit).performClick());
    }
    assertEquals("1234567890壹贰叁", entered.toString());
    screenshot("financial-digits");
  }

  @Test public void largeLightKeyboardKeepsNumberEntryReadable() throws Exception {
    Configuration config = new Configuration(context.getResources().getConfiguration());
    config.fontScale = 1.3f;
    context = new ContextThemeWrapper(activity.createConfigurationContext(config), Skin.theme());
    ins.runOnMainSync(() -> {
      Skin.select(activity, true);
      Skin.systemBars(activity);
      stage.setBackgroundColor(Ui.bg());
      mount(keyboard(true));
    });
    settle();
    TextView toggle = (TextView) find(stage, "数字");
    assertEquals("12\n34", toggle.getText().toString());
    assertTrue(toggle.getLineCount() <= 2);
    screenshot("numbers-light-large");
  }

  private boolean scrollPicker(View view) {
    if (view.canScrollVertically(1) || view.canScrollVertically(-1)) {
      view.scrollBy(0, Ui.dp(context, 500));
      return true;
    }
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        if (scrollPicker(((ViewGroup) view).getChildAt(i))) return true;
    return false;
  }

  @Test public void realDictionaryCorrectionsAndEveryCandidatePage() throws Exception {
    KeyboardApp app = (KeyboardApp) activity.getApplication();
    PinyinEngine engine = app.worker.submit(() -> app.pinyin).get(30, TimeUnit.SECONDS);
    assertNotNull(app.dictionaryError, engine);
    String[][] cases = {{"nihso", "你好"}, {"zhongugo", "中国"}, {"shurfua", "输入法"}};
    for (String[] item : cases) {
      long began = SystemClock.elapsedRealtime();
      PinyinEngine.Query query = app.worker.submit(
          () -> engine.query(item[0], Collections.emptyMap())).get(30, TimeUnit.SECONDS);
      PinyinEngine.Word corrected = query.page(0, query.size()).stream()
          .filter(w -> w.text.equals(item[1])).findFirst().orElse(null);
      assertNotNull(item[0] + " recall", corrected);
      assertEquals(item[0], corrected.code);
      assertTrue(corrected.correctionCost > 0);
      android.util.Log.i("BaseKeyboardQA", item[0] + " -> " + corrected.text + " / "
          + corrected.spelling + " cost=" + corrected.correctionCost + " rank="
          + query.page(0, query.size()).indexOf(corrected) + " ms="
          + (SystemClock.elapsedRealtime() - began));
    }
    PinyinEngine.Query query = app.worker.submit(
        () -> engine.query("shi", Collections.emptyMap())).get(30, TimeUnit.SECONDS);
    assertTrue(query.size() > CandidatePanel.PAGE_SIZE);
    final CandidatePanel[] view = new CandidatePanel[1];
    ins.runOnMainSync(() -> {
      view[0] = new CandidatePanel(context, "shi", query, () -> returns++, w -> entered.append(w.text));
      mount(view[0]);
    });
    settle();
    screenshot("candidate-first");
    int pages = (query.size() + CandidatePanel.PAGE_SIZE - 1) / CandidatePanel.PAGE_SIZE;
    for (int i = 1; i < pages; i++)
      ins.runOnMainSync(() -> find(view[0], "下一页").performClick());
    settle();
    assertFalse(find(view[0], "下一页").isEnabled());
    PinyinEngine.Word lastPageFirst = query.page((pages - 1) * CandidatePanel.PAGE_SIZE, 1).get(0);
    ins.runOnMainSync(() -> find(view[0], lastPageFirst.text).performClick());
    assertEquals(lastPageFirst.text, entered.toString());
    screenshot("candidate-last");
    for (int i = 1; i < pages; i++)
      ins.runOnMainSync(() -> find(view[0], "上一页").performClick());
    assertFalse(find(view[0], "上一页").isEnabled());
    ins.runOnMainSync(() -> find(view[0], "返回键盘").performClick());
    assertEquals(1, returns);
    android.util.Log.i("BaseKeyboardQA", "shi native=" + query.nativeCount + " lexical="
        + query.lexiconCount + " total=" + query.size() + " pages=" + pages);
  }
}
