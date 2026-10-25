package app.doudou.ime.input;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;
import app.doudou.ime.voice.DictationSurface;
import app.doudou.ime.voice.VoiceGesture;
import app.doudou.ime.voice.VoiceSpaceKey;
import app.doudou.ime.voice.VoiceState;

/** Key geometry and candidate/action row. No dictionary, editor or model work lives here. */
public final class KeyboardView extends LinearLayout {
  public static final int HEIGHT_DP = 286;

  public interface Actions {
    void letter(char ch);

    void space();

    void enter();

    void delete();

    void shift();

    void symbols();

    void language();

    void cursor(int delta);

    void more();

    void voice();

    void holdVoice();

    void advisor();

    void recorder();

    void hide();

    void symbolPanel();

    void finishVoice(boolean newline);

    void cancelVoice();

    void recoverVoice();

    void selectAll();

    void finishEditing();

    void expand();

    void haptic(View key);
  }

  private final Actions actions;
  private final LinearLayout candidateRow, idle, busy, editing, keys;
  private final DictationSurface dictation;
  private final HorizontalScrollView candidateScroll;
  private final View expand, recover, voiceClose;
  private final ProgressBar pending;
  private final TextView voiceHint;
  private VoiceSpaceKey spaceKey;
  private VoiceState voiceState = VoiceState.IDLE;
  private final Handler repeats = new Handler(Looper.getMainLooper());
  private final boolean english;

  public KeyboardView(
      Context c,
      boolean english,
      boolean capital,
      boolean symbols,
      boolean privacy,
      boolean nested,
      Actions a) {
    super(c);
    this.english = english;
    actions = a;
    setOrientation(VERTICAL);
    FrameLayout surfaces = new FrameLayout(c);
    keys = Ui.col(c);
    keys.setPadding(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
    surfaces.addView(keys, new FrameLayout.LayoutParams(-1, -1));
    addView(surfaces, new LayoutParams(-1, 0, 1));
    LinearLayout bar = Ui.row(c);
    bar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
    idle = Ui.row(c);
    if (!nested) {
      View more = Icons.button(c, R.drawable.ic_grid_view, "更多", a::more);
      more.setTooltipText("表情、剪贴板与键盘工具");
      idle.addView(more, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    }
    if (privacy) {
      TextView privateLabel = Ui.text(c, "隐私输入", 13, Ui.sub());
      privateLabel.setContentDescription("隐私输入已开启");
      idle.addView(privateLabel);
    }
    if (!nested && !privacy) {
      TextView record =
          Ui.button(
              c,
              app.doudou.ime.recording.RecordingService.current == null ? "点击录音" : "● 录音中",
              a::recorder);
      record.setTextColor(Ui.sub());
      record.setBackground(Ui.pressable(c, Ui.surface(), 24));
      android.graphics.drawable.Drawable mic = c.getDrawable(R.drawable.ic_mic);
      mic.setTint(Ui.sub());
      mic.setBounds(0, 0, Ui.dp(c, 20), Ui.dp(c, 20));
      record.setCompoundDrawables(mic, null, null, null);
      record.setCompoundDrawablePadding(Ui.dp(c, 6));
      idle.addView(record, new LayoutParams(-2, Ui.dp(c, 40)));
    }
    idle.addView(new Space(c), new LayoutParams(0, 1, 1));
    if (!nested && !privacy) {
      FrameLayout advisor = new FrameLayout(c);
      advisor.setContentDescription("帮我回");
      advisor.setFocusable(true);
      advisor.setOnClickListener(v -> a.advisor());
      advisor.setTooltipText("看聊天，帮你回");
      TextView pill = Ui.text(c, "帮我回", 14, Ui.ink());
      pill.setGravity(Gravity.CENTER);
      pill.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      android.graphics.drawable.GradientDrawable surface = Ui.bg(c, Ui.specialKey(), 17);
      surface.setStroke(Ui.dp(c, 1), Ui.outline());
      pill.setBackground(surface);
      advisor.addView(pill, new FrameLayout.LayoutParams(-1, Ui.dp(c, 34), Gravity.CENTER));
      LayoutParams advisorSize = new LayoutParams(Ui.dp(c, 72), Ui.dp(c, 48));
      advisorSize.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 2), 0);
      idle.addView(advisor, advisorSize);
    }
    if (!nested) {
      View hide = Ui.icon(c, "收起", a::hide);
      hide.setContentDescription("收起键盘");
      idle.addView(hide, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    }
    bar.addView(idle, new LayoutParams(-1, -1));
    candidateRow = Ui.row(c);
    candidateScroll = new HorizontalScrollView(c);
    candidateScroll.setHorizontalScrollBarEnabled(false);
    candidateScroll.addView(candidateRow, new HorizontalScrollView.LayoutParams(-2, -1));
    candidateScroll.setVisibility(GONE);
    bar.addView(candidateScroll, new LayoutParams(0, -1, 1));
    expand = Ui.icon(c, "展开", a::expand);
    expand.setVisibility(GONE);
    bar.addView(expand, new LayoutParams(Ui.dp(c, 36), -1));
    busy = Ui.row(c);
    busy.setVisibility(GONE);
    voiceClose = Icons.button(c, R.drawable.ic_close, "关闭提示", a::cancelVoice);
    busy.addView(voiceClose, new LayoutParams(Ui.dp(c, 44), -1));
    pending = new ProgressBar(c);
    pending.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.sub()));
    pending.setVisibility(GONE);
    LayoutParams progressSize = new LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
    progressSize.setMargins(Ui.dp(c, 12), 0, 0, 0);
    busy.addView(pending, progressSize);
    voiceHint = Ui.text(c, "", 13, Ui.ink());
    voiceHint.setGravity(Gravity.CENTER);
    voiceHint.setMaxLines(2);
    voiceHint.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    busy.addView(voiceHint, new LayoutParams(0, -1, 1));
    recover = Ui.button(c, "填入文字", a::recoverVoice);
    recover.setMinimumWidth(Ui.dp(c, 84));
    recover.setVisibility(GONE);
    busy.addView(recover, new LayoutParams(-2, -1));
    bar.addView(busy, new LayoutParams(-1, -1));
    editing = Ui.row(c);
    editing.setVisibility(GONE);
    editButton(editing, "左移", () -> a.cursor(-1), true);
    editButton(editing, "全选", a::selectAll, false);
    editButton(editing, "右移", () -> a.cursor(1), true);
    editButton(editing, "完成", a::finishEditing, false);
    bar.addView(editing, new LayoutParams(-1, -1));
    keys.addView(bar, new LayoutParams(-1, Ui.dp(c, 48)));
    if (symbols) {
      for (String chars : new String[] {"1234567890", "@#¥%&*+-=_", ".,/?!:;()'"}) {
        LinearLayout row = row();
        for (char ch : chars.toCharArray()) key(row, "" + ch, 1, () -> a.letter(ch), false);
      }
      LinearLayout row = row();
      key(row, "ABC", 1.4f, a::symbols, true);
      key(row, "#+=", .9f, a::symbolPanel, true);
      space(row, 3.2f, !privacy && !nested);
      icon(row, "退格", 1.4f, a::delete, false);
      icon(row, "换行", 1.85f, a::enter, true);
    } else {
      String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
      for (int n = 0; n < rows.length; n++) {
        LinearLayout row = row();
        if (n == 1) row.addView(new Space(c), new LayoutParams(0, 1, .5f));
        if (n == 2) icon(row, "大写", 1.35f, a::shift, false);
        for (char ch : rows[n].toCharArray())
          key(
              row,
              capital ? ("" + ch).toUpperCase(java.util.Locale.ROOT) : "" + ch,
              1,
              () -> a.letter(ch),
              false);
        if (n == 1) row.addView(new Space(c), new LayoutParams(0, 1, .5f));
        if (n == 2) icon(row, "退格", 1.35f, a::delete, false);
      }
      LinearLayout row = row();
      key(row, "123", 1.8f, a::symbols, true);
      key(row, english ? "," : "，", .9f, () -> a.letter(english ? ',' : '，'), false);
      space(row, 3.6f, !privacy && !nested);
      key(row, english ? "英" : "中", .95f, a::language, true);
      icon(row, "换行", 1.85f, a::enter, true);
    }
    dictation =
        new DictationSurface(
            c,
            new DictationSurface.Actions() {
              public void cancel() {
                a.cancelVoice();
              }

              public void finish(boolean newline) {
                a.finishVoice(newline);
              }
            });
    dictation.setVisibility(GONE);
    surfaces.addView(dictation, new FrameLayout.LayoutParams(-1, -1));
  }

  private LinearLayout row() {
    LinearLayout r = Ui.row(getContext());
    keys.addView(r, new LayoutParams(-1, 0, 1));
    return r;
  }

  private LayoutParams geometry(float weight) {
    LayoutParams lp = new LayoutParams(0, -1, weight);
    lp.setMargins(
        Ui.dp(getContext(), 2.5f),
        Ui.dp(getContext(), 3.5f),
        Ui.dp(getContext(), 2.5f),
        Ui.dp(getContext(), 3.5f));
    return lp;
  }

  private void key(LinearLayout row, String label, float weight, Runnable click, boolean special) {
    Context c = getContext();
    TextView key = Ui.text(c, label, special ? 16 : label.length() > 1 ? 14 : 22, Ui.ink());
    key.setGravity(Gravity.CENTER);
    key.setFontFeatureSettings("kern");
    key.setBackground(Ui.pressable(c, special ? Ui.specialKey() : Ui.key(), 7));
    key.setElevation(Ui.dp(c, .8f));
    key.setContentDescription(label);
    key.setFocusable(true);
    key.setOnClickListener(
        v -> {
          actions.haptic(v);
          click.run();
        });
    row.addView(key, geometry(weight));
    if (label.equals("，") || label.equals(",")) {
      key.setTooltipText("长按选择标点");
      key.setOnLongClickListener(
          v -> {
            actions.symbolPanel();
            return true;
          });
    }
  }

  private void space(LinearLayout row, float weight, boolean speech) {
    Context c = getContext();
    spaceKey =
        new VoiceSpaceKey(
            c,
            speech,
            () -> {
              if (voiceState != VoiceState.RECORDING && voiceState != VoiceState.CONNECTING) {
                actions.haptic(this);
                actions.space();
              }
            });
    row.addView(spaceKey, geometry(weight));
    if (speech) {
      spaceKey.setOnLongClickListener(
          v -> {
            if (voiceState != VoiceState.IDLE) return false;
            actions.voice();
            return true;
          });
      spaceKey.setAccessibilityDelegate(
          new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, android.view.accessibility.AccessibilityNodeInfo info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              info.removeAction(
                  android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
                      .ACTION_LONG_CLICK);
              if (voiceState == VoiceState.IDLE)
                info.addAction(
                    new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        "开始免按录音"));
            }
          });
    }
    final float[] down = {0, 0};
    final boolean[] moved = {false}, held = {false};
    Runnable hold =
        () -> {
          held[0] = true;
          spaceKey.setPressed(false);
          actions.haptic(spaceKey);
          actions.holdVoice();
        };
    spaceKey.setOnTouchListener(
        (v, e) -> {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
              down[0] = e.getRawX();
              down[1] = e.getRawY();
              moved[0] = held[0] = false;
              v.setPressed(true);
              if (speech && voiceState == VoiceState.IDLE) repeats.postDelayed(hold, 280);
              break;
            case MotionEvent.ACTION_MOVE:
              if (!held[0] && Math.abs(e.getRawX() - down[0]) > Ui.dp(c, 20)) {
                moved[0] = true;
                repeats.removeCallbacks(hold);
              }
              break;
            case MotionEvent.ACTION_UP:
              repeats.removeCallbacks(hold);
              v.setPressed(false);
              if (!held[0]) {
                if (moved[0] && voiceState == VoiceState.IDLE)
                  actions.cursor(e.getRawX() > down[0] ? 1 : -1);
                else if (!moved[0]) v.performClick();
              }
              break;
            case MotionEvent.ACTION_CANCEL:
              repeats.removeCallbacks(hold);
              v.setPressed(false);
              break;
          }
          return true;
        });
  }

  private void icon(LinearLayout row, String name, float weight, Runnable click, boolean primary) {
    Context c = getContext();
    View key =
        Ui.icon(
            c,
            name,
            () -> {
              actions.haptic(this);
              click.run();
            });
    ((Ui.Glyph) key).color = primary ? Ui.bg() : Ui.ink();
    key.setBackground(Ui.pressable(c, primary ? Ui.ink() : Ui.specialKey(), 7));
    key.setElevation(Ui.dp(c, .8f));
    row.addView(key, geometry(weight));
    if (name.equals("退格")) {
      Runnable repeat =
          new Runnable() {
            public void run() {
              click.run();
              repeats.postDelayed(this, 65);
            }
          };
      key.setOnTouchListener(
          (v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) repeats.postDelayed(repeat, 350);
            if (e.getAction() == MotionEvent.ACTION_UP
                || e.getAction() == MotionEvent.ACTION_CANCEL) repeats.removeCallbacks(repeat);
            return false;
          });
    }
  }

  public void clearCandidates() {
    idle.setVisibility(GONE);
    busy.setVisibility(GONE);
    editing.setVisibility(GONE);
    candidateScroll.setVisibility(VISIBLE);
    expand.setVisibility(VISIBLE);
    candidateRow.removeAllViews();
  }

  public void voice(VoiceState state, String hint, boolean held, VoiceGesture.Target target) {
    boolean beginning = voiceState == VoiceState.IDLE && state != VoiceState.IDLE;
    voiceState = state;
    boolean recording = state == VoiceState.CONNECTING || state == VoiceState.RECORDING;
    keys.setVisibility(recording ? GONE : VISIBLE);
    dictation.setVisibility(recording ? VISIBLE : GONE);
    dictation.present(state, hint, held, target);
    if (!recording) {
      boolean waiting = state == VoiceState.FINISHING || state == VoiceState.REWRITING;
      idle.setVisibility(waiting ? GONE : VISIBLE);
      busy.setVisibility(waiting ? VISIBLE : GONE);
      candidateScroll.setVisibility(GONE);
      expand.setVisibility(GONE);
      pending.setVisibility(waiting ? VISIBLE : GONE);
      voiceClose.setVisibility(waiting ? GONE : VISIBLE);
      recover.setVisibility(GONE);
      voiceHint.setText(hint);
    }
    if (beginning && android.animation.ValueAnimator.areAnimatorsEnabled()) {
      dictation.setAlpha(0);
      dictation.animate().alpha(1).setDuration(110).start();
    }
  }

  public VoiceGesture.Target voiceTarget(float x, float y) {
    return dictation.target(x, y);
  }

  public void voiceLevel(float rmsDb) {
    dictation.level(rmsDb);
  }

  public void recovery(String hint, boolean hasText) {
    voice(VoiceState.IDLE, "", false, VoiceGesture.Target.FINISH);
    idle.setVisibility(GONE);
    busy.setVisibility(VISIBLE);
    voiceClose.setVisibility(VISIBLE);
    pending.setVisibility(GONE);
    voiceHint.setText(hint);
    recover.setVisibility(hasText ? VISIBLE : GONE);
  }

  public void edit() {
    idle.setVisibility(GONE);
    busy.setVisibility(GONE);
    candidateScroll.setVisibility(GONE);
    expand.setVisibility(GONE);
    editing.setVisibility(VISIBLE);
  }

  private void editButton(LinearLayout parent, String name, Runnable action, boolean repeat) {
    TextView button = Ui.button(getContext(), name, action);
    button.setBackground(Ui.pressable(getContext(), Color.TRANSPARENT, 10));
    parent.addView(button, new LayoutParams(0, -1, 1));
    if (!repeat) return;
    Runnable again =
        new Runnable() {
          public void run() {
            action.run();
            repeats.postDelayed(this, 80);
          }
        };
    button.setOnTouchListener(
        (v, e) -> {
          if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            v.setPressed(true);
            v.performClick();
            repeats.postDelayed(again, 350);
          }
          if (e.getActionMasked() == MotionEvent.ACTION_UP
              || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            repeats.removeCallbacks(again);
            v.setPressed(false);
          }
          return true;
        });
  }

  public void addCandidate(String text, Runnable click) {
    TextView t =
        Ui.text(getContext(), text, 18, candidateRow.getChildCount() == 0 ? Ui.accent() : Ui.ink());
    t.setGravity(Gravity.CENTER);
    Ui.pad(getContext(), t, 14, 0);
    t.setMinWidth(Ui.dp(getContext(), 48));
    t.setContentDescription(text);
    t.setBackground(Ui.pressable(getContext(), Color.TRANSPARENT, 8));
    t.setOnClickListener(v -> click.run());
    candidateRow.addView(t, new LayoutParams(-2, -1));
  }

  @Override
  protected void onDetachedFromWindow() {
    repeats.removeCallbacksAndMessages(null);
    super.onDetachedFromWindow();
  }
}
