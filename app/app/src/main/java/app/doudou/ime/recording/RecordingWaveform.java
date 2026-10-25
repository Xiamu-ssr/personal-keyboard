package app.doudou.ime.recording;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import app.doudou.ime.design.Ui;

/** Each bar is a measured microphone amplitude, including flat silence. */
final class RecordingWaveform extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] values = new float[13];

  RecordingWaveform(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void level(float value) {
    System.arraycopy(values, 1, values, 0, values.length - 1);
    values[values.length - 1] = Math.max(0, Math.min(1, value));
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    paint.setShader(
        new LinearGradient(0, 0, w, 0, Ui.audioStart(), Ui.audioEnd(), Shader.TileMode.CLAMP));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeWidth(Ui.dp(getContext(), 2));
    float gap = getWidth() / 15f, center = getHeight() / 2f;
    for (int i = 0; i < values.length; i++) {
      float x = (i + 1.5f) * gap, half = Ui.dp(getContext(), 1) + values[i] * getHeight() * .33f;
      canvas.drawLine(x, center - half, x, center + half, paint);
    }
  }
}
