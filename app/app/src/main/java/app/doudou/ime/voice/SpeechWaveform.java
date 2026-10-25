package app.doudou.ime.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import app.doudou.ime.design.Ui;

/** A small, smoothed microphone meter. Silence has a baseline, never invented speech peaks. */
final class SpeechWaveform extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float level, drawnLevel;
  private boolean listening;

  SpeechWaveform(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void listening(boolean value) {
    listening = value;
    if (!value) level = drawnLevel = 0;
    invalidate();
  }

  void level(float rmsDb) {
    if (!listening) return;
    level = Math.max(0, Math.min(1, (rmsDb + 5) / 18));
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    paint.setShader(
        new LinearGradient(0, 0, w, 0, Ui.audioStart(), Ui.audioEnd(), Shader.TileMode.CLAMP));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    drawnLevel += (level - drawnLevel) * .24f;
    float step = getWidth() / 20f, bar = Math.min(Ui.dp(getContext(), 3), step * .5f);
    for (int i = 0; i < 20; i++) {
      float envelope = .35f + .65f * (float) Math.sin(Math.PI * (i + .5f) / 20);
      float height =
          Ui.dp(getContext(), 4) + drawnLevel * envelope * (getHeight() - Ui.dp(getContext(), 5));
      float x = step * (i + .5f);
      canvas.drawRoundRect(
          x - bar / 2,
          (getHeight() - height) / 2,
          x + bar / 2,
          (getHeight() + height) / 2,
          bar / 2,
          bar / 2,
          paint);
    }
    if (isShown()
        && listening
        && ValueAnimator.areAnimatorsEnabled()
        && Math.abs(level - drawnLevel) > .005f) postInvalidateOnAnimation();
  }
}
