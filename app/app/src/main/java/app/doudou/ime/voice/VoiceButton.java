package app.doudou.ime.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import app.doudou.ime.design.Ui;

/** Microphone, measured speech level, and recognition progress share one stable touch target. */
public final class VoiceButton extends View {
  public enum State {
    IDLE,
    CONNECTING,
    LISTENING,
    TRANSCRIBING,
    REWRITING
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] levels = new float[11];
  private State state = State.IDLE;
  private ValueAnimator progress;
  private float phase;

  public VoiceButton(Context context, Runnable action) {
    super(context);
    setFocusable(true);
    setBackground(Ui.pressable(context, Ui.ACCENT, 40));
    setOnClickListener(v -> action.run());
    setState(State.IDLE);
  }

  public void setState(State next) {
    state = next;
    stopProgress();
    java.util.Arrays.fill(levels, 0);
    setContentDescription(next == State.IDLE ? "开始说话" : next == State.LISTENING ? "停止录音" : next == State.CONNECTING ? "正在连接语音" : next == State.REWRITING ? "正在按文风整理" : "正在完成识别");
    setEnabled(next == State.IDLE || next == State.LISTENING);
    if (next != State.IDLE && next != State.LISTENING && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()) {
      progress = ValueAnimator.ofFloat(0, 360);
      progress.setDuration(900);
      progress.setRepeatCount(ValueAnimator.INFINITE);
      progress.setInterpolator(new android.view.animation.LinearInterpolator());
      progress.addUpdateListener(
          a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
          });
      progress.start();
    }
    invalidate();
  }

  public void level(float rmsDb) {
    if (state != State.LISTENING) return;
    System.arraycopy(levels, 1, levels, 0, levels.length - 1);
    levels[levels.length - 1] = Math.max(0, Math.min(1, (rmsDb + 2) / 12));
    invalidate();
  }

  private void stopProgress() {
    if (progress != null) {
      progress.cancel();
      progress = null;
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    stopProgress();
    super.onDetachedFromWindow();
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    setState(state);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    canvas.translate(getWidth() / 2f, getHeight() / 2f);
    float d = getResources().getDisplayMetrics().density;
    canvas.scale(d, d);
    paint.setColor(Ui.BG);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(2.4f);
    if (state == State.REWRITING) {
      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 3; i++) {
        float bounce = ValueAnimator.areAnimatorsEnabled() ? (float) Math.sin(Math.toRadians(phase - i * 65)) * 3 : 0;
        canvas.drawCircle((i - 1) * 13, bounce, 3.5f, paint);
      }
    } else if (state == State.CONNECTING || state == State.TRANSCRIBING) {
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawArc(-12, -12, 12, 12, phase - 90, 255, false, paint);
    } else if (state == State.LISTENING) {
      paint.setStrokeWidth(3.2f);
      for (int i = 0; i < levels.length; i++) {
        float h = 2 + 14 * levels[i];
        float x = (i - 5) * 6f;
        canvas.drawLine(x, -h, x, h, paint);
      }
      // A stop mark makes the tap action clear even in silence or without RMS callbacks.
      paint.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(47, -4, 55, 4, 1.5f, 1.5f, paint);
    } else {
      paint.setStyle(Paint.Style.FILL);
      canvas.drawRoundRect(-5, -14, 5, 5, 5, 5, paint);
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawArc(-10, -7, 10, 11, 0, 180, false, paint);
      canvas.drawLine(0, 11, 0, 17, paint);
    }
    canvas.restore();
  }
}
