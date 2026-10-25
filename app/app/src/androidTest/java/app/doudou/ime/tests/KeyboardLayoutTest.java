package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Intent;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.MainActivity;
import app.doudou.ime.input.KeyboardView;
import app.doudou.ime.voice.VoiceGesture;
import app.doudou.ime.voice.VoiceState;
import org.junit.*;

/** Native rendering and routing, with no microphone, account or provider fixture. */
public final class KeyboardLayoutTest {
  private Instrumentation ins;
  private Activity activity;
  private KeyboardView keyboard;
  private int spaces, letters, finishes, starts;

  @Before
  public void open() {
    Assume.assumeTrue("Isolated emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    activity =
        ins.startActivitySync(
            new Intent(ins.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ins.runOnMainSync(
        () -> {
          keyboard =
              new KeyboardView(
                  activity,
                  false,
                  false,
                  false,
                  false,
                  false,
                  new KeyboardView.Actions() {
                    public void letter(char c) {
                      letters++;
                    }

                    public void space() {
                      spaces++;
                    }

                    public void enter() {}

                    public void delete() {}

                    public void shift() {}

                    public void symbols() {}

                    public void language() {}

                    public void cursor(int d) {}

                    public void more() {}

                    public void voice() {
                      starts++;
                    }

                    public void holdVoice() {}

                    public void recorder() {}

                    public void hide() {}

                    public void symbolPanel() {}

                    public void advisor() {}

                    public void finishVoice(boolean newline) {
                      finishes++;
                    }

                    public void cancelVoice() {}

                    public void recoverVoice() {}

                    public void selectAll() {}

                    public void finishEditing() {}

                    public void expand() {}

                    public void haptic(View v) {}
                  }, null);
          FrameLayout root = new FrameLayout(activity);
          int h =
              Math.round(
                  KeyboardView.HEIGHT_DP * activity.getResources().getDisplayMetrics().density);
          root.addView(keyboard, new FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM));
          activity.setContentView(root);
        });
    ins.waitForIdleSync();
    SystemClock.sleep(100);
  }

  @After
  public void close() {
    if (activity != null) ins.runOnMainSync(activity::finish);
  }

  private View find(View v, String name) {
    if (v.isShown()
        && name.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = find(((ViewGroup) v).getChildAt(i), name);
        if (found != null) return found;
      }
    return null;
  }

  private Rect bounds(View v) {
    Rect r = new Rect();
    assertTrue(v.getGlobalVisibleRect(r));
    return r;
  }

  private void tap(View v) {
    ins.runOnMainSync(
        () -> {
          long now = SystemClock.uptimeMillis();
          MotionEvent down =
              MotionEvent.obtain(
                  now, now, MotionEvent.ACTION_DOWN, v.getWidth() / 2f, v.getHeight() / 2f, 0);
          MotionEvent up =
              MotionEvent.obtain(
                  now, now + 30, MotionEvent.ACTION_UP, v.getWidth() / 2f, v.getHeight() / 2f, 0);
          v.dispatchTouchEvent(down);
          v.dispatchTouchEvent(up);
          down.recycle();
          up.recycle();
        });
  }

  @Test
  public void heldSpeechReplacesTheKeysAndReturnsToTheSameEditorGeometry() {
    View q = find(keyboard, "q"), space = find(keyboard, "空格");
    assertNotNull(q);
    assertNotNull(space);
    Rect keyBefore = bounds(q), spaceBefore = bounds(space), surfaceBefore = bounds(keyboard);
    tap(space);
    assertEquals(1, spaces);
    ins.runOnMainSync(
        () -> {
          android.view.accessibility.AccessibilityNodeInfo node =
              space.createAccessibilityNodeInfo();
          assertTrue(
              node.getActionList().stream()
                  .anyMatch(a -> "开始免按录音".contentEquals(a.getLabel() == null ? "" : a.getLabel())));
          keyboard.voice(VoiceState.RECORDING, "松手结束", true, VoiceGesture.Target.FINISH);
          space.performLongClick();
          assertEquals(0, starts);
        });
    ins.waitForIdleSync();
    SystemClock.sleep(150);
    assertFalse("Irrelevant keys leave the voice surface", q.isShown());
    assertFalse(space.isShown());
    assertTrue(find(keyboard, "撤回输入").isShown());
    assertTrue(find(keyboard, "换行").isShown());
    assertEquals(surfaceBefore, bounds(keyboard));
    Rect withdraw = bounds(find(keyboard, "撤回输入")), newline = bounds(find(keyboard, "换行"));
    assertEquals(
        VoiceGesture.Target.CANCEL, keyboard.voiceTarget(withdraw.centerX(), withdraw.centerY()));
    assertEquals(
        VoiceGesture.Target.NEWLINE, keyboard.voiceTarget(newline.centerX(), newline.centerY()));
    for (VoiceState state :
        new VoiceState[] {
          VoiceState.CONNECTING, VoiceState.RECORDING, VoiceState.FINISHING, VoiceState.REWRITING
        }) {
      ins.runOnMainSync(() -> keyboard.voice(state, "正在完成识别…", true, VoiceGesture.Target.FINISH));
      ins.waitForIdleSync();
      SystemClock.sleep(60);
      assertEquals(surfaceBefore, bounds(keyboard));
      if (state == VoiceState.FINISHING || state == VoiceState.REWRITING) {
        assertTrue("Release restores editing before the final model result", q.isShown());
        assertTrue(space.isShown());
      } else assertFalse(q.isShown());
      ins.runOnMainSync(
          () -> {
            space.performLongClick();
            assertEquals(0, starts);
          });
    }
    ins.runOnMainSync(() -> keyboard.voice(VoiceState.IDLE, "", false, VoiceGesture.Target.FINISH));
    ins.waitForIdleSync();
    assertTrue(q.isShown());
    tap(space);
    assertEquals(2, spaces);
    ins.runOnMainSync(() -> assertTrue(space.performLongClick()));
    assertEquals(1, starts);
    assertEquals(keyBefore, bounds(q));
    assertEquals(spaceBefore, bounds(space));
  }
}
