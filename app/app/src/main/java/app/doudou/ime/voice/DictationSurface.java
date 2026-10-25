package app.doudou.ime.voice;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;

/** The keyboard-sized hold surface: withdraw on the left, finish with a newline on the right. */
public final class DictationSurface extends FrameLayout {
  public interface Actions {
    void cancel();
    void finish(boolean newline);
  }

  private final SpeechWaveform wave;
  private final ArcAction withdraw, newline;
  private final TextView leftHint, rightHint, release, status, finish;
  private final View close;
  private final Backdrop backdrop;
  private final ProgressBar progress;
  private VoiceState state = VoiceState.IDLE;
  private VoiceGesture.Target target = VoiceGesture.Target.FINISH;
  private boolean held;

  public DictationSurface(Context context, Actions actions) {
    super(context);
    setClipChildren(true);
    backdrop = new Backdrop();
    setBackground(backdrop);
    wave = new SpeechWaveform(context);
    addView(wave);
    withdraw = new ArcAction(context, "撤回输入", false, actions::cancel);
    newline = new ArcAction(context, "换行", true, () -> actions.finish(true));
    addView(withdraw); addView(newline);
    leftHint = label("松手撤回", 13, Ui.withdrawalRed()); leftHint.setRotation(-12);
    rightHint = label("松手换行", 13, Ui.sub()); rightHint.setRotation(12);
    release = label("松手结束", 13, Ui.sub());
    release.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    status = label("", 15, Ui.ink());
    status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    finish = action("完成", () -> actions.finish(false));
    finish.setTextColor(Ui.bg());
    finish.setBackground(Ui.pressable(context, Ui.accent(), 32));
    close = Icons.button(context, R.drawable.ic_close, "撤回本次语音", actions::cancel);
    addView(close);
    progress = new ProgressBar(context);
    progress.setIndeterminateTintList(ColorStateList.valueOf(Ui.sub()));
    progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(progress);
  }

  private TextView label(String title, int size, int color) {
    TextView view = Ui.text(getContext(), title, size, color);
    view.setGravity(Gravity.CENTER);
    addView(view);
    return view;
  }

  private TextView action(String title, Runnable action) {
    TextView view = label(title, 17, Ui.ink());
    view.setContentDescription(title);
    view.setFocusable(true);
    view.setOnClickListener(v -> action.run());
    return view;
  }

  public void present(VoiceState next, String hint, boolean holding, VoiceGesture.Target selected) {
    boolean waiting = next == VoiceState.FINISHING || next == VoiceState.REWRITING;
    state = next; held = holding; target = selected;
    withdraw.setVisibility(waiting || !held ? GONE : VISIBLE);
    newline.setVisibility(waiting || !held ? GONE : VISIBLE);
    release.setVisibility(waiting || !held ? GONE : VISIBLE);
    release.setText("松手结束");
    leftHint.setVisibility(held && target == VoiceGesture.Target.CANCEL ? VISIBLE : GONE);
    rightHint.setVisibility(held && target == VoiceGesture.Target.NEWLINE ? VISIBLE : GONE);
    status.setVisibility(waiting || !held || next == VoiceState.CONNECTING ? VISIBLE : GONE);
    status.setText(waiting ? hint : held ? "正在连接…" : "正在倾听");
    close.setVisibility(waiting || !held ? VISIBLE : GONE);
    finish.setVisibility(!waiting && !held ? VISIBLE : GONE);
    progress.setVisibility(waiting ? VISIBLE : GONE);
    wave.setVisibility(waiting ? GONE : VISIBLE);
    wave.listening(next == VoiceState.RECORDING);
    release.setContentDescription(hint);
    withdraw.select(target == VoiceGesture.Target.CANCEL);
    newline.select(target == VoiceGesture.Target.NEWLINE);
    backdrop.invalidateSelf();
    requestLayout();
  }

  public void level(float rmsDb) { wave.level(rmsDb); }

  public VoiceGesture.Target target(float rawX, float rawY) {
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    return VoiceGesture.target(rawX - origin[0], rawY - origin[1], getWidth(), getHeight());
  }

  private void place(View view, int x, int y, int width, int height) {
    LayoutParams params = (LayoutParams)view.getLayoutParams();
    if (params.width == width && params.height == height && params.leftMargin == x && params.topMargin == y) return;
    params.width = width; params.height = height;
    params.leftMargin = x; params.topMargin = y;
    view.setLayoutParams(params);
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
    int touch = Ui.dp(getContext(), 44);
    boolean waiting = state == VoiceState.FINISHING || state == VoiceState.REWRITING;
    place(wave, (int)(w * .33f), (int)(h * .13f), (int)(w * .34f), (int)(h * .10f));
    place(withdraw, 0, (int)(h * .37f), (int)(w * .53f), (int)(h * .27f));
    place(newline, (int)(w * .47f), (int)(h * .37f), (int)(w * .53f), (int)(h * .27f));
    place(leftHint, 0, (int)(h * .32f), (int)(w * .40f), (int)(h * .08f));
    place(rightHint, (int)(w * .60f), (int)(h * .32f), (int)(w * .40f), (int)(h * .08f));
    place(release, (int)(w * .30f), (int)(h * .74f), (int)(w * .40f), touch);
    place(status, (int)(w * .12f), (int)(h * (waiting ? .39f : .27f)), (int)(w * .76f), touch);
    place(finish, (int)(w * .25f), (int)(h * .72f), (int)(w * .50f), Ui.dp(getContext(), 52));
    place(close, 2, 0, touch, touch);
    int spinner = Ui.dp(getContext(), 24);
    place(progress, (w - spinner) / 2, (int)(h * .28f), spinner, spinner);
    super.onMeasure(widthSpec, heightSpec);
  }

  private final class Backdrop extends android.graphics.drawable.Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader glow;
    @Override protected void onBoundsChange(Rect bounds) {
      int w = bounds.width(), h = bounds.height();
      glow = new RadialGradient(w * .5f, h * .6f, Math.max(w, h) * .65f,
          new int[] { Ui.voiceBackdrop(), Ui.keyboard() }, null, Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas canvas) {
      float w = getBounds().width(), h = getBounds().height();
      paint.setColor(Color.WHITE);
      paint.setShader(glow);
      canvas.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
      if (held && state != VoiceState.FINISHING && state != VoiceState.REWRITING) {
        paint.setColor(target == VoiceGesture.Target.FINISH
            ? app.doudou.ime.design.Skin.choose(0x04ffffff, 0x04000000)
            : app.doudou.ime.design.Skin.choose(0x10ffffff, 0x10000000));
        canvas.drawOval(-w * .22f, h * .70f, w * 1.22f, h * 1.85f, paint);
      }
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
  }
}
