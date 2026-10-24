package app.doudou.ime.voice;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import app.doudou.ime.design.Ui;

/** A curved ribbon and curved baseline, rather than a rotated rectangular button. */
final class ArcAction extends View {
  private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final boolean right;
  private final String label;
  private boolean chosen;

  ArcAction(Context context, String label, boolean right, Runnable action) {
    super(context);
    this.label = label; this.right = right;
    setContentDescription(label); setFocusable(true);
    setOnClickListener(v -> action.run());
    band.setStyle(Paint.Style.STROKE); band.setStrokeCap(Paint.Cap.ROUND);
    text.setTextSize(17 * getResources().getDisplayMetrics().scaledDensity);
    text.setTextAlign(Paint.Align.LEFT);
  }

  void select(boolean value) {
    chosen = value; setSelected(value); invalidate();
    animate().scaleX(value ? 1.025f : 1).scaleY(value ? 1.025f : 1).setDuration(90).start();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth(), h = getHeight();
    path.reset();
    if (right) {
      path.moveTo(w * .21f, h * .32f);
      path.quadTo(w * .57f, h * .34f, w * 1.16f, h * .78f);
    } else {
      path.moveTo(-w * .16f, h * .78f);
      path.quadTo(w * .43f, h * .34f, w * .79f, h * .32f);
    }
    band.setStrokeWidth(h * .63f);
    band.setColor(chosen ? Color.WHITE : 0x18ffffff);
    canvas.drawPath(path, band);
    text.setColor(chosen ? right ? Ui.BG : 0xffff5a67 : Ui.INK);
    float length = new PathMeasure(path, false).getLength();
    Paint.FontMetrics metrics = text.getFontMetrics();
    float start=length*(right ? .40f : .60f)-text.measureText(label)/2;
    canvas.drawTextOnPath(label, path, start, -(metrics.ascent + metrics.descent) / 2, text);
  }
}
