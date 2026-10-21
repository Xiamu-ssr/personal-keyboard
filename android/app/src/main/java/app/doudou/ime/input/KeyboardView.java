package app.doudou.ime.input;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** Key geometry and candidate/action row. No dictionary, editor or model work lives here. */
public final class KeyboardView extends LinearLayout {
  public interface Actions {
    void letter(char ch);

    void space();

    void enter();

    void delete();

    void shift();

    void symbols();

    void language();

    void cursor(int delta);

    void tools();

    void voice();

    void advisor();

    void hide();

    void expand();

    void haptic(View key);
  }

  private final Actions actions;
  private final LinearLayout candidateRow, idle;
  private final HorizontalScrollView candidateScroll;
  private final View expand;
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
    LinearLayout bar = Ui.row(c);
    bar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
    idle = Ui.row(c);
    idle.addView(Ui.icon(c, "工具", a::tools), new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 44)));
    LinearLayout voice = Ui.row(c);
    voice.setGravity(Gravity.CENTER);
    voice.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 12), 0);
    voice.setBackground(Ui.pressable(c, 0xff343735, 22));
    voice.addView(
        Ui.icon(c, privacy ? "隐私" : "语音", a::voice), new LayoutParams(Ui.dp(c, 30), Ui.dp(c, 32)));
    TextView prompt = Ui.text(c, privacy ? "隐私输入" : "点击说话", 14, Ui.SUB);
    voice.addView(prompt);
    voice.setContentDescription(privacy ? "隐私输入" : "语音");
    voice.setOnClickListener(v -> a.voice());
    LayoutParams voiceLp = new LayoutParams(-2, Ui.dp(c, 34));
    voiceLp.setMargins(Ui.dp(c, 8), 0, 0, 0);
    idle.addView(voice, voiceLp);
    idle.addView(new Space(c), new LayoutParams(0, 1, 1));
    if (!nested)
      idle.addView(Ui.icon(c, "军师", a::advisor), new LayoutParams(Ui.dp(c, 42), Ui.dp(c, 44)));
    idle.addView(Ui.icon(c, "收起", a::hide), new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 44)));
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
    addView(bar, new LayoutParams(-1, Ui.dp(c, 44)));
    if (symbols) {
      for (String chars : new String[] {"1234567890", "@#¥%&*+-=", "，。？！：；（）"}) {
        LinearLayout row = row();
        for (char ch : chars.toCharArray()) key(row, "" + ch, 1, () -> a.letter(ch), false);
      }
      LinearLayout row = row();
      key(row, "ABC", 1.4f, a::symbols, true);
      key(row, "空格", 4, a::space, false);
      icon(row, "退格", 1.4f, a::delete, false);
      icon(row, "换行", 1.6f, a::enter, true);
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
      key(row, "123", 1.55f, a::symbols, true);
      key(row, english ? "," : "，", .8f, () -> a.letter(english ? ',' : '，'), false);
      key(row, "空格", 3.6f, a::space, false);
      key(row, english ? "EN" : "中", .85f, a::language, true);
      icon(row, "换行", 1.7f, a::enter, true);
    }
  }

  private LinearLayout row() {
    LinearLayout r = Ui.row(getContext());
    addView(r, new LayoutParams(-1, 0, 1));
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
    TextView key = Ui.text(c, label, special ? 16 : label.length() > 1 ? 14 : 22, Ui.INK);
    key.setGravity(Gravity.CENTER);
    key.setFontFeatureSettings("kern");
    key.setBackground(Ui.pressable(c, special ? 0xff363938 : 0xff525654, 7));
    key.setElevation(Ui.dp(c, .8f));
    key.setContentDescription(label);
    key.setFocusable(true);
    key.setOnClickListener(
        v -> {
          actions.haptic(v);
          click.run();
        });
    row.addView(key, geometry(weight));
    if (label.equals("空格")) {
      final float[] down = {0};
      key.setOnTouchListener(
          (v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) down[0] = e.getX();
            if (e.getAction() == MotionEvent.ACTION_UP
                && Math.abs(e.getX() - down[0]) > Ui.dp(c, 28)) {
              actions.cursor(e.getX() > down[0] ? 1 : -1);
              return true;
            }
            return false;
          });
    }
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
    key.setBackground(Ui.pressable(c, primary ? 0xff596d61 : 0xff363938, 7));
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
    candidateScroll.setVisibility(VISIBLE);
    expand.setVisibility(VISIBLE);
    candidateRow.removeAllViews();
  }

  public void addCandidate(String text, Runnable click) {
    TextView t =
        Ui.text(getContext(), text, 18, candidateRow.getChildCount() == 0 ? Ui.ACCENT : Ui.INK);
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
