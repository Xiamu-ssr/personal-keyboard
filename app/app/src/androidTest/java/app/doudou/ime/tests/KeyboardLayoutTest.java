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
import app.doudou.ime.voice.VoiceState;
import app.doudou.ime.voice.VoiceGesture;
import org.junit.*;

/** Native rendering and routing, with no microphone, account or provider fixture. */
public final class KeyboardLayoutTest {
  private Instrumentation ins;
  private Activity activity;
  private KeyboardView keyboard;
  private int spaces, letters, finishes, starts;

  @Before public void open() {
    Assume.assumeTrue("Isolated emulator only", "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    activity = ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ins.runOnMainSync(() -> {
      keyboard = new KeyboardView(activity, false, false, false, false, false, true, new KeyboardView.Actions() {
        public void letter(char c) { letters++; }
        public void space() { spaces++; }
        public void enter() { }
        public void delete() { }
        public void shift() { }
        public void symbols() { }
        public void language() { }
        public void cursor(int d) { }
        public void more(View a) { }
        public void undo() { }
        public void voice() { starts++; }
        public void holdVoice(float x, float y) { }
        public void advisor() { }
        public void finishVoice() { finishes++; }
        public void cancelVoice() { }
        public void recoverVoice() { }
        public void selectAll() { }
        public void finishEditing() { }
        public void expand() { }
        public void haptic(View v) { }
      });
      FrameLayout root = new FrameLayout(activity);
      int h = Math.round(284 * activity.getResources().getDisplayMetrics().density);
      root.addView(keyboard, new FrameLayout.LayoutParams(-1, h, Gravity.BOTTOM));
      activity.setContentView(root);
    });
    ins.waitForIdleSync(); SystemClock.sleep(100);
  }

  @After public void close() { if (activity != null) ins.runOnMainSync(activity::finish); }

  private View find(View v, String name) {
    if (name.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
    if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
      View found=find(((ViewGroup)v).getChildAt(i),name); if(found!=null)return found;
    }
    return null;
  }
  private Rect bounds(View v) { Rect r=new Rect(); assertTrue(v.getGlobalVisibleRect(r)); return r; }
  private void tap(View v) {
    ins.runOnMainSync(() -> {
      long now=SystemClock.uptimeMillis();
      MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,v.getWidth()/2f,v.getHeight()/2f,0);
      MotionEvent up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,v.getWidth()/2f,v.getHeight()/2f,0);
      v.dispatchTouchEvent(down);v.dispatchTouchEvent(up);down.recycle();up.recycle();
    });
  }

  @Test public void recordingKeepsKeyPositionsAndSpaceFinishesHandsFree() {
    View q=find(keyboard,"q"), space=find(keyboard,"空格");
    assertNotNull(q);assertNotNull(space);
    Rect keyBefore=bounds(q), spaceBefore=bounds(space);
    ins.runOnMainSync(() -> {
      android.view.accessibility.AccessibilityNodeInfo node=space.createAccessibilityNodeInfo();
      assertTrue(node.getActionList().stream().anyMatch(a -> "开始免按录音".contentEquals(a.getLabel()==null?"":a.getLabel())));
      keyboard.voice(VoiceState.RECORDING,"松手后免按说话",true,VoiceGesture.Target.LOCK);
      assertEquals("松手免按",space.getContentDescription());
      space.performLongClick(); assertEquals(0, starts);
    });
    for(VoiceState state:new VoiceState[]{VoiceState.CONNECTING,VoiceState.RECORDING,VoiceState.FINISHING,VoiceState.REWRITING}) {
      ins.runOnMainSync(() -> keyboard.voice(state,"免按说话 · 点空格完成",false,VoiceGesture.Target.FINISH));
      ins.waitForIdleSync();SystemClock.sleep(60);
      assertEquals(keyBefore,bounds(q));assertEquals(spaceBefore,bounds(space));
      assertFalse(q.isEnabled());tap(q);assertEquals(0,letters);
      tap(space);
      assertEquals(state==VoiceState.CONNECTING?1:2,finishes);
      ins.runOnMainSync(() -> { space.performLongClick(); assertEquals(0, starts); });
    }
    ins.runOnMainSync(() -> keyboard.voice(VoiceState.IDLE,"",false,VoiceGesture.Target.FINISH));
    assertTrue(q.isEnabled());tap(space);assertEquals(1,spaces);
    ins.runOnMainSync(() -> assertTrue(space.performLongClick()));assertEquals(1,starts);
    assertEquals(keyBefore,bounds(q));
  }
}
