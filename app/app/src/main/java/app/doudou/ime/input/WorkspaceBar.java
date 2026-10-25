package app.doudou.ime.input;

import android.content.Context;
import android.graphics.drawable.InsetDrawable;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;

/** Four named workspaces share one capsule; the selected item also unfolds its icon. */
public final class WorkspaceBar extends LinearLayout {
  private NavigationTab recordingTab;
  private boolean recordingActive;
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable recordingStatus = new Runnable() {
    public void run() {
      app.doudou.ime.recording.RecordingService service = app.doudou.ime.recording.RecordingService.current;
      boolean active = service != null && service.active() && service.belongsToCurrentAccount();
      if (recordingTab != null && active != recordingActive) {
        recordingActive = active;
        recordingTab.recording = active;
        recordingTab.invalidate();
        recordingTab.setStateDescription(active ? "正在录音" : null);
      }
      if (isAttachedToWindow()) main.postDelayed(this, 400);
    }
  };
  public enum Mode { INPUT, RECORDING, ADVISOR, TRANSLATION }

  public interface Actions {
    void select(Mode mode);
    void tools();
    void hide();
  }

  public WorkspaceBar(Context c, Mode selected, boolean privateMode, Actions actions) {
    super(c);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0);
    addView(Icons.button(c, R.drawable.ic_grid_view, "更多", actions::tools),
        new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    LinearLayout tabs = Ui.row(c);
    tabs.setBackground(new InsetDrawable(Ui.bg(c, Ui.specialKey(), 24), 0, Ui.dp(c, 4), 0, Ui.dp(c, 4)));
    // Visual insets must not become layout padding and clip the 48dp radio targets.
    tabs.setPadding(0, 0, 0, 0);
    String[] labels = {"输入", "录音", "帮我回", "翻译"};
    int[] symbols = {R.drawable.ic_workspace_keyboard, R.drawable.ic_workspace_record,
        R.drawable.ic_workspace_reply, R.drawable.ic_workspace_translate};
    Mode[] modes = Mode.values();
    for (int i = 0; i < modes.length; i++) {
      Mode mode = modes[i];
      NavigationTab tab = new NavigationTab(c);
      tab.setText(labels[i]);
      tab.setTextSize(14); tab.setTextColor(selected == mode ? Ui.ink() : Ui.sub());
      tab.setTypeface(null, selected == mode ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
      tab.setIncludeFontPadding(false);
      android.graphics.drawable.Drawable symbol = c.getDrawable(symbols[i]).mutate();
      symbol.setTint(selected == mode ? Ui.ink() : Ui.sub());
      symbol.setBounds(0, 0, Ui.dp(c, 22), Ui.dp(c, 22));
      tab.setCompoundDrawables(selected == mode ? symbol : null, null, null, null);
      tab.setCompoundDrawablePadding(selected == mode ? Ui.dp(c, 6) : 0);
      if (mode == Mode.RECORDING) recordingTab = tab;
      tab.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      tab.setSingleLine();
      tab.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0);
      tab.setContentDescription(labels[i]);
      tab.setSelected(selected == mode);
      tab.setFocusable(true);
      tab.setBackground(new InsetDrawable(Ui.pressable(c,
          selected == mode ? app.doudou.ime.design.Skin.choose(Ui.selected(), 0xffffffff) : android.graphics.Color.TRANSPARENT, 22),
          Ui.dp(c, 2), Ui.dp(c, 5), Ui.dp(c, 2), Ui.dp(c, 5)));
      tab.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0);
      if (selected == mode) tab.setElevation(Ui.dp(c, 1));
      tab.setTooltipText(labels[i]);
      tab.setAccessibilityDelegate(new View.AccessibilityDelegate() {
        @Override public void onInitializeAccessibilityNodeInfo(View v, android.view.accessibility.AccessibilityNodeInfo info) {
          super.onInitializeAccessibilityNodeInfo(v, info);
          info.setClassName("android.widget.RadioButton");
          info.setCheckable(true);
          info.setChecked(v.isSelected());
        }
      });
      tab.setEnabled(mode == Mode.INPUT || !privateMode);
      tab.setAlpha(tab.isEnabled() ? 1 : .4f);
      tab.setOnClickListener(v -> {
        if (mode != selected) actions.select(mode);
      });
      int labelWidth = (int)Math.ceil(tab.getPaint().measureText(labels[i])) + Ui.dp(c, 8);
      int selectedWidth = labelWidth + Ui.dp(c, 40);
      tabs.addView(tab, selected == mode ? new LayoutParams(Math.max(Ui.dp(c, 48), selectedWidth), Ui.dp(c, 48))
          : new LayoutParams(Math.max(Ui.dp(c, 48), labelWidth), Ui.dp(c, 48)));
    }
    HorizontalScrollView viewport = new HorizontalScrollView(c);
    viewport.setHorizontalScrollBarEnabled(false);
    viewport.setFillViewport(true);
    LinearLayout centered = Ui.row(c); centered.setGravity(Gravity.CENTER);
    centered.addView(tabs, new LayoutParams(-2, Ui.dp(c, 48)));
    viewport.addView(centered, new HorizontalScrollView.LayoutParams(-2, Ui.dp(c, 48)));
    addView(viewport, new LayoutParams(0, Ui.dp(c, 48), 1));
    View hide = Ui.icon(c, "收起", actions::hide);
    hide.setContentDescription("收起键盘");
    addView(hide, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
  }

  @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); main.post(recordingStatus); }
  @Override protected void onDetachedFromWindow() { main.removeCallbacks(recordingStatus); super.onDetachedFromWindow(); }

  private static final class NavigationTab extends androidx.emoji2.widget.EmojiTextView {
    boolean recording;
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    NavigationTab(Context context) { super(context); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w,h,oldw,oldh);
      android.graphics.drawable.Drawable icon = getCompoundDrawables()[0];
      int content = icon == null ? 0 : icon.getBounds().width();
      if (getText().length() > 0) content += getCompoundDrawablePadding() + (int)Math.ceil(getPaint().measureText(getText().toString()));
      int padding = Math.max(Ui.dp(getContext(),2),(w-content)/2);
      setPadding(padding,0,padding,0);
    }
    @Override protected void onDraw(android.graphics.Canvas canvas) {
      super.onDraw(canvas);
      if (recording) {
        paint.setColor(Ui.audioStart());
        boolean animate = android.animation.ValueAnimator.areAnimatorsEnabled() && isShown();
        paint.setAlpha(animate ? 150 + (int)(105 * (.5 + .5 * Math.sin(android.os.SystemClock.uptimeMillis() * Math.PI / 800))) : 255);
        canvas.drawCircle(getScrollX() + getWidth() - Ui.dp(getContext(), 9), Ui.dp(getContext(), 10), Ui.dp(getContext(), 3), paint);
        if (animate) postInvalidateOnAnimation();
      }
    }
  }
}
