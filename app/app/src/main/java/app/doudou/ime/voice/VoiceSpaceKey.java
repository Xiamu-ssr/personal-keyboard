package app.doudou.ime.voice;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** One stable target for a space, held dictation, and finishing hands-free dictation. */
public final class VoiceSpaceKey extends FrameLayout {
  private final TextView label, hint;
  private final ProgressBar activity, level;
  private final boolean speech;
  private VoiceState state = VoiceState.IDLE;

  public VoiceSpaceKey(Context c, boolean speech, Runnable action) {
    super(c);
    this.speech = speech;
    setBackground(Ui.pressable(c, Ui.KEY, 7));
    setElevation(Ui.dp(c, .8f));
    setFocusable(true);
    setOnClickListener(v -> action.run());
    LinearLayout words = Ui.col(c);
    words.setGravity(Gravity.CENTER);
    label = Ui.text(c, "空格", 14, Ui.INK);
    hint = Ui.text(c, "按住说话", 10, Ui.INK);
    label.setGravity(Gravity.CENTER);
    hint.setGravity(Gravity.CENTER);
    hint.setPadding(0, Ui.dp(c, 3), 0, 0);
    words.addView(label); words.addView(hint);
    words.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    addView(words, new LayoutParams(-1, -1));
    activity = new ProgressBar(c);
    activity.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK));
    LayoutParams spin = new LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    spin.topMargin = Ui.dp(c, 5); addView(activity, spin);
    level = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
    level.setMax(100);
    level.setProgressTintList(ColorStateList.valueOf(Ui.INK));
    level.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.KEY));
    LayoutParams meter = new LayoutParams(-1, Ui.dp(c, 3), Gravity.BOTTOM);
    meter.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 4)); addView(level, meter);
    activity.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    level.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    present(VoiceState.IDLE, false, VoiceGesture.Target.FINISH);
  }

  public VoiceState state() { return state; }

  public void present(VoiceState next, boolean held, VoiceGesture.Target target) {
    boolean cancelling = target == VoiceGesture.Target.CANCEL;
    state = next;
    boolean waiting = next == VoiceState.CONNECTING || next == VoiceState.FINISHING || next == VoiceState.REWRITING;
    activity.setVisibility(waiting ? VISIBLE : GONE);
    level.setVisibility(next == VoiceState.RECORDING ? VISIBLE : GONE);
    if (next != VoiceState.RECORDING) level.setProgress(0);
    String title = next == VoiceState.IDLE ? "空格"
        : next == VoiceState.FINISHING ? "识别中"
        : next == VoiceState.REWRITING ? "整理中"
        : held ? (cancelling ? "松手取消" : target == VoiceGesture.Target.LOCK ? "松手免按" : "松手填入") : "完成";
    label.setText(title);
    label.setTextColor(cancelling ? Ui.RED : Ui.INK);
    label.setTranslationY(waiting ? Ui.dp(getContext(), 7) : 0);
    hint.setText(next == VoiceState.IDLE ? "按住说话" : "正在录音");
    hint.setVisibility(next == VoiceState.IDLE && speech || next == VoiceState.RECORDING && !held ? VISIBLE : GONE);
    setContentDescription(next == VoiceState.IDLE ? "空格" : next == VoiceState.RECORDING && !held ? "完成录音" : title);
    setTooltipText(next == VoiceState.IDLE && speech ? "按住说话 · 松手填入 · 上滑免按 · 左滑取消" : title);
    setLongClickable(speech && next == VoiceState.IDLE);
    setPressed(false);
  }

  public void level(float rmsDb) {
    if (state == VoiceState.RECORDING)
      level.setProgress(Math.round(Math.max(0, Math.min(1, (rmsDb + 2) / 12)) * 100), true);
  }
}
