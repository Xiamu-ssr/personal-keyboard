package app.doudou.ime.input;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.emoji2.widget.EmojiTextView;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.MainActivity;
import app.doudou.ime.design.Skin;
import app.doudou.ime.design.Ui;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** Height-only Android regressions; no service, editor, dictionary or provider calls. */
public final class KeyboardHeightTest {
  private static final String[] BOTTOM_KEYS = {
      "123", "，", "空格", "中", "换行", "大写", "退格"
  };
  private Instrumentation ins;
  private Activity activity;
  private Context context;
  private FrameLayout stage;
  private LinearLayout root;
  private KeyboardView keyboard;
  private boolean originalLight;
  private int menus, expands, selections, dismisses;

  @Before public void open() throws Exception {
    Assume.assumeTrue("Owned emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    activity = ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    originalLight = Skin.light();
    ins.runOnMainSync(() -> {
      stage = new FrameLayout(activity);
      activity.setContentView(stage);
    });
    settle();
  }

  @After public void close() {
    if (activity != null) ins.runOnMainSync(() -> {
      Skin.select(activity, originalLight);
      activity.finish();
    });
  }

  private KeyboardView createKeyboard() {
    KeyboardView.Actions actions = (KeyboardView.Actions) Proxy.newProxyInstance(
        KeyboardView.Actions.class.getClassLoader(), new Class<?>[] {KeyboardView.Actions.class},
        (proxy, method, args) -> {
          if (method.getName().equals("more")) menus++;
          if (method.getName().equals("expand")) expands++;
          if (method.getName().equals("dismissPredictions")) dismisses++;
          return null;
        });
    WorkspaceBar workspace = new WorkspaceBar(context, WorkspaceBar.Mode.INPUT, false,
        new WorkspaceBar.Actions() {
          public void select(WorkspaceBar.Mode mode) {}
          public void tools() { menus++; }
          public void hide() {}
        });
    return new KeyboardView(context, false, false, false, false, false, actions, workspace);
  }

  private void mount(int widthDp, int insetDp, float fontScale, boolean light) throws Exception {
    ins.runOnMainSync(() -> {
      Skin.select(activity, light);
      Configuration config = new Configuration(activity.getResources().getConfiguration());
      config.fontScale = fontScale;
      context = new ContextThemeWrapper(activity.createConfigurationContext(config), Skin.theme());
      stage.removeAllViews();
      // Match the service's fixed content height and bottom padding, including a synthetic inset.
      root = Ui.col(context);
      root.setBackgroundColor(Ui.keyboard());
      root.setPadding(0, 0, 0, Ui.dp(context, 4) + Ui.dp(context, insetDp));
      keyboard = createKeyboard();
      root.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1));
      FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(Ui.dp(context, widthDp),
          Ui.dp(context, KeyboardView.HEIGHT_DP) + Ui.dp(context, insetDp),
          Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      if (stage.getRootWindowInsets() != null)
        size.bottomMargin = stage.getRootWindowInsets()
            .getInsets(WindowInsets.Type.navigationBars()).bottom;
      stage.addView(root, size);
    });
    settle();
  }

  private void settle() throws Exception {
    CountDownLatch frame = new CountDownLatch(1);
    ins.runOnMainSync(() -> {
      stage.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          stage.getViewTreeObserver().removeOnPreDrawListener(this);
          frame.countDown();
          return true;
        }
      });
      stage.requestLayout();
      stage.invalidate();
    });
    assertTrue("Android completed a layout frame", frame.await(5, TimeUnit.SECONDS));
    ins.waitForIdleSync();
  }

  private View find(View view, String label) {
    if (view.getVisibility() != View.VISIBLE) return null;
    if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
      return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private View visible(String label) {
    View result = find(keyboard, label);
    assertNotNull("Visible control: " + label, result);
    return result;
  }

  private Rect bounds(View view) {
    Rect actual = new Rect();
    assertTrue("Control is visible", view.getGlobalVisibleRect(actual));
    int[] location = new int[2];
    view.getLocationOnScreen(location);
    Rect full = new Rect(location[0], location[1],
        location[0] + view.getWidth(), location[1] + view.getHeight());
    assertEquals("Control is not clipped", full, actual);
    return actual;
  }

  private Map<String, Rect> geometry() {
    Map<String, Rect> result = new LinkedHashMap<>();
    ins.runOnMainSync(() -> {
      result.put("window content", bounds(root));
      result.put("keyboard", bounds(keyboard));
      for (char letter : "qwertyuiopasdfghjklzxcvbnm".toCharArray())
        result.put("" + letter, bounds(visible("" + letter)));
      for (String label : BOTTOM_KEYS) result.put(label, bounds(visible(label)));
      assertEquals("No extra spelling gap above the letter rows",
          bounds(keyboard).top + Ui.dp(context, 48) + Ui.dp(context, 3.5f),
          result.get("q").top);
      assertEquals(Ui.dp(context, KeyboardView.HEIGHT_DP) - Ui.dp(context, 4),
          keyboard.getHeight());
    });
    return result;
  }

  private void unchanged(Map<String, Rect> before, String state) throws Exception {
    settle();
    assertEquals(state + " preserves every absolute key bound", before, geometry());
  }

  private void candidates(String raw) {
    keyboard.clearCandidates();
    keyboard.setComposing(raw);
    keyboard.addCandidate("你好", () -> selections++);
    keyboard.addCandidate("你们", () -> selections++);
  }

  @Test public void passiveSuggestionsYieldToEditingAndRecoverableSpeech() throws Exception {
    mount(360, 24, 1f, false);
    ins.runOnMainSync(() -> {
      assertTrue(keyboard.acceptsSuggestions());
      keyboard.showPredictions();
      assertTrue(keyboard.acceptsSuggestions());
      keyboard.edit();
      assertFalse(keyboard.acceptsSuggestions());
      assertNotNull(visible("左移"));
      keyboard.showIdle();
      keyboard.recovery("转写中断", true);
      assertFalse(keyboard.acceptsSuggestions());
      assertNotNull(visible("填入文字"));
      keyboard.showIdle();
      assertTrue(keyboard.acceptsSuggestions());
    });
  }

  @Test public void pinyinPredictionAndIdleTransitionsKeepEveryKeyBound() throws Exception {
    mount(360, 24, 1f, false);
    Map<String, Rect> before = geometry();
    View firstKey = visible("q"), spaceKey = visible("空格");
    ins.runOnMainSync(() -> {
      keyboard.clearCandidates();
      keyboard.setComposing("n");
    });
    unchanged(before, "Loading first pinyin candidates");
    ins.runOnMainSync(() -> candidates("nihao"));
    unchanged(before, "Pinyin candidates");
    ins.runOnMainSync(() -> {
      assertNull("Pinyin strip has no prediction menu", find(keyboard, "更多"));
      keyboard.clearCandidates();
      keyboard.setComposing("nihaoma");
    });
    unchanged(before, "Asynchronous candidate refresh");
    ins.runOnMainSync(() -> {
      keyboard.addCandidate("你好吗", () -> selections++);
      keyboard.setComposing("");
    });
    unchanged(before, "Candidate strip without raw pinyin");
    ins.runOnMainSync(() -> {
      keyboard.showPredictions();
      keyboard.addCandidate("好的", () -> selections++);
      keyboard.addCandidate("👍🏽", () -> selections++);
    });
    unchanged(before, "Predictions after committing a word");
    ins.runOnMainSync(() -> {
      assertNull(find(keyboard, "正在输入拼音：nihaoma"));
      assertEquals(Ui.dp(context, 48), bounds(visible("更多")).height());
      visible("更多").performClick();
      visible("展开").performClick();
      assertEquals(Ui.dp(context, 48), bounds(visible("收起联想")).height());
      visible("收起联想").performClick();
      assertEquals(1, menus);
      assertEquals(1, expands);
      assertEquals(1, dismisses);
      keyboard.showIdle();
    });
    unchanged(before, "Returning to the workspace bar");
    ins.runOnMainSync(() -> {
      assertSame(firstKey, visible("q"));
      assertSame(spaceKey, visible("空格"));
      for (String label : new String[] {"输入", "录音", "帮我回", "翻译"})
        assertEquals(Ui.dp(context, 48), bounds(visible(label)).height());
      assertNull(find(keyboard, "展开"));
      assertNull(find(keyboard, "好的"));
      candidates("shi");
    });
    unchanged(before, "Typing resumes after idle");
    ins.runOnMainSync(keyboard::showPredictions);
    unchanged(before, "Predictions clear the raw pinyin");
    ins.runOnMainSync(() -> {
      assertNull(find(keyboard, "正在输入拼音：shi"));
      assertNull(find(keyboard, "你好"));
      keyboard.showIdle();
      keyboard.showIdle();
    });
    unchanged(before, "Repeated idle updates");
  }

  @Test public void headerChangesAndKeyboardRecreationKeepGeometryAtLargeType() throws Exception {
    for (boolean light : new boolean[] {false, true}) {
      mount(320, 0, 1.3f, light);
      Map<String, Rect> before = geometry();
      ins.runOnMainSync(() -> candidates("zhongguoshurufa"));
      unchanged(before, "Large-type pinyin, light=" + light);
      ins.runOnMainSync(() -> {
        TextView candidate = (TextView) visible("你好");
        assertEquals(Ui.dp(context, 48), bounds(candidate).height());
        assertTrue("Candidate text fits below the spelling overlay",
            candidate.getLineHeight() <= candidate.getHeight()
                - candidate.getPaddingTop() - candidate.getPaddingBottom());
        TextView spelling = (TextView) visible("正在输入拼音：zhongguoshurufa");
        assertTrue("Spelling precedes candidate text",
            bounds(spelling).bottom <= bounds(candidate).top + candidate.getPaddingTop());
        assertTrue("Spelling fits at large font scale", spelling.getLineHeight() <= spelling.getHeight());
        keyboard.showPredictions();
        keyboard.addCandidate("👨‍👩‍👧‍👦", () -> selections++);
      });
      unchanged(before, "Large-type predictions, light=" + light);
      ins.runOnMainSync(() -> {
        assertTrue(visible("👨‍👩‍👧‍👦") instanceof EmojiTextView);
        root.removeView(keyboard);
        keyboard = createKeyboard();
        root.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1));
      });
      unchanged(before, "Service-style keyboard recreation, light=" + light);
    }
  }

  private void tapInKeyboard(View target, boolean lowerHalf) {
    int[] targetLocation = new int[2], keyboardLocation = new int[2];
    target.getLocationOnScreen(targetLocation);
    keyboard.getLocationOnScreen(keyboardLocation);
    float x = targetLocation[0] - keyboardLocation[0] + target.getWidth() / 2f;
    float y = targetLocation[1] - keyboardLocation[1]
        + target.getHeight() * (lowerHalf ? .75f : .25f);
    long now = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0);
    try {
      assertTrue(keyboard.dispatchTouchEvent(down));
      assertTrue(keyboard.dispatchTouchEvent(up));
    } finally {
      down.recycle();
      up.recycle();
    }
  }

  @Test public void spellingOverlayPreservesCandidateTargetsAndEmojiText() throws Exception {
    mount(360, 24, 1f, false);
    Map<String, Rect> before = geometry();
    String raw = "nihaozhongguoshurufanihaozhongguoshurufanihaozhongguoshurufa";
    String emoji = "👨‍👩‍👧‍👦";
    ins.runOnMainSync(() -> {
      keyboard.clearCandidates();
      keyboard.setComposing(raw);
      keyboard.addCandidate(emoji, () -> selections++);
      for (int i = 0; i < 16; i++) keyboard.addCandidate("候选" + i, () -> {});
    });
    unchanged(before, "Long pinyin and overflowing candidates");
    ins.runOnMainSync(() -> {
      TextView candidate = (TextView) visible(emoji);
      assertTrue("Candidates use the existing EmojiCompat widget", candidate instanceof EmojiTextView);
      assertEquals(emoji, candidate.getText().toString());
      assertEquals(Ui.dp(context, 48), bounds(candidate).height());
      TextView spelling = (TextView) visible("正在输入拼音：" + raw);
      HorizontalScrollView spellingScroll = (HorizontalScrollView) spelling.getParent();
      assertTrue("Long spelling follows its trailing characters", spellingScroll.getScrollX() > 0);
      Rect candidateBounds = bounds(candidate), keyBounds = bounds(visible("q"));
      assertTrue("The header stays above the letter keys", candidateBounds.bottom <= keyBounds.top);
      tapInKeyboard(candidate, true);
    });
    settle();
    assertEquals("The passive spelling overlay keeps the lower candidate target tappable", 1, selections);
    ins.runOnMainSync(() -> {
      TextView candidate = (TextView) visible(emoji);
      HorizontalScrollView scroll = (HorizontalScrollView) candidate.getParent().getParent();
      scroll.fullScroll(View.FOCUS_RIGHT);
    });
    unchanged(before, "Horizontal candidate scrolling");
    ins.runOnMainSync(keyboard::showIdle);
    unchanged(before, "Clearing long pinyin and predictions");
  }
}
