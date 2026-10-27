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

    void numbers();
    void numberPad();
    void letters();

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

    void dismissPredictions();

    void haptic(View key);
  }

  private final Actions actions;
  private final LinearLayout candidateRow, idle, busy, editing, keys, keyRows;
  private final FrameLayout candidateArea;
  private final DictationSurface dictation;
  private final HorizontalScrollView candidateScroll;
  private final View predictionMenu, predictionClose, expand, recover, voiceClose;
  private final ProgressBar pending;
  private final TextView voiceHint;
  private final TextView composing;
  private final HorizontalScrollView composingScroll;
  private final LinearLayout composingRow;
  private VoiceSpaceKey spaceKey;
  private VoiceState voiceState = VoiceState.IDLE;
  private final Handler repeats = new Handler(Looper.getMainLooper());
  private final boolean english;
  private final KeyboardPage page;
  private int symbolCategory, symbolPage;

  public KeyboardView(
      Context c,
      boolean english,
      boolean capital,
      KeyboardPage page,
      boolean privacy,
      boolean nested,
      Actions a,
      View workspace) {
    super(c);
    this.english = english;
    this.page = page;
    actions = a;
    setOrientation(VERTICAL);
    FrameLayout surfaces = new FrameLayout(c);
    keys = Ui.col(c);
    keys.setPadding(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
    composingRow = Ui.row(c);
    composingRow.setVisibility(GONE);
    composing = Ui.text(c, "", 13, Ui.sub());
    composing.setSingleLine(true);
    composing.setHorizontallyScrolling(true);
    composing.setAutoSizeTextTypeUniformWithConfiguration(11, 13, 1,
        android.util.TypedValue.COMPLEX_UNIT_SP);
    composing.setGravity(Gravity.CENTER_VERTICAL);
    composing.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
    composing.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    composingScroll = new HorizontalScrollView(c) {
      @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Follow the newly measured tail, including text received before the first layout.
        if (getChildCount() > 0) scrollTo(getChildAt(0).getRight() - getWidth(), 0);
      }

      @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // The spelling follows the cursor; candidate targets keep the full 48dp header.
        return false;
      }
    };
    composingScroll.setHorizontalScrollBarEnabled(false);
    composingScroll.setFillViewport(true);
    composingScroll.addView(composing, new HorizontalScrollView.LayoutParams(-2, -1));
    composingRow.addView(composingScroll, new LayoutParams(-1, -1));
    surfaces.addView(keys, new FrameLayout.LayoutParams(-1, -1));
    addView(surfaces, new LayoutParams(-1, 0, 1));
    LinearLayout bar = Ui.row(c);
    bar.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
    idle = Ui.row(c);
    if (workspace != null && page == KeyboardPage.LETTERS)
      idle.addView(workspace, new LayoutParams(-1, -1));
    bar.addView(idle, new LayoutParams(-1, -1));
    predictionMenu = Icons.button(c, R.drawable.ic_grid_view, "更多", a::more);
    predictionMenu.setVisibility(GONE);
    bar.addView(predictionMenu, new LayoutParams(Ui.dp(c, 48), -1));
    candidateRow = Ui.row(c);
    candidateScroll = new HorizontalScrollView(c);
    candidateScroll.setHorizontalScrollBarEnabled(false);
    candidateScroll.addView(candidateRow, new HorizontalScrollView.LayoutParams(-2, -1));
    // Idle, predictions and spelling share one header. Spelling never takes key-row space.
    candidateArea = new FrameLayout(c);
    candidateArea.setVisibility(GONE);
    candidateArea.addView(candidateScroll, new FrameLayout.LayoutParams(-1, -1));
    candidateArea.addView(composingRow,
        new FrameLayout.LayoutParams(-1, Ui.dp(c, 18), Gravity.TOP));
    bar.addView(candidateArea, new LayoutParams(0, -1, 1));
    expand = Ui.icon(c, "展开", a::expand);
    expand.setVisibility(GONE);
    bar.addView(expand, new LayoutParams(Ui.dp(c, 36), -1));
    predictionClose = Icons.button(c, R.drawable.ic_close, "收起联想", a::dismissPredictions);
    predictionClose.setVisibility(GONE);
    bar.addView(predictionClose, new LayoutParams(Ui.dp(c, 48), -1));
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
    keyRows = Ui.col(c);
    keys.addView(keyRows, new LayoutParams(-1, 0, 1));
    if (page == KeyboardPage.NUMBERS) {
      numbers(!privacy && !nested);
    } else if (page == KeyboardPage.NUMBER_PAD) {
      numberPad();
    } else if (page == KeyboardPage.SYMBOLS) {
      symbols();
    } else {
      String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
      for (int n = 0; n < rows.length; n++) {
        LinearLayout row = row();
        LinearLayout letters = row;
        if (n == 1) row.addView(new Space(c), new LayoutParams(0, 1, .5f));
        if (n == 2) {
          icon(row, "大写", 1.35f, a::shift);
          View shiftKey = row.getChildAt(row.getChildCount() - 1);
          ((Ui.Glyph) shiftKey).color = Ui.ink();
          shiftKey.setBackground(Ui.pressable(c, capital ? Ui.selected() : Ui.specialKey(), 7));
          shiftKey.setSelected(capital);
          shiftKey.setStateDescription(capital ? "大写已开启" : "小写输入");
          letters = centerKeys(row);
        }
        for (char ch : rows[n].toCharArray()) {
          key(
              letters,
              ("" + ch).toUpperCase(java.util.Locale.ROOT),
              1,
              () -> a.letter(ch),
              false);
          letters.getChildAt(letters.getChildCount() - 1).setContentDescription(
              english && capital ? ("" + ch).toUpperCase(java.util.Locale.ROOT) : "" + ch);
        }
        if (n == 1) row.addView(new Space(c), new LayoutParams(0, 1, .5f));
        if (n == 2) icon(row, "退格", 1.35f, a::delete);
      }
      LinearLayout row = row();
      key(row, "123", 1.8f, a::numbers, true);
      key(row, english ? "," : "，", .9f, () -> a.letter(english ? ',' : '，'), false);
      space(row, 3.6f, !privacy && !nested);
      key(row, english ? "英" : "中", .95f, a::language, true);
      icon(row, "换行", 1.85f, a::enter);
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

  private void numbers(boolean speech) {
    keyRows.removeAllViews();
    auxiliaryHeader();
    for (String[] chars : new String[][] {
        {"1","2","3","4","5","6","7","8","9","0"},
        {"-","/",":","~","(",")","…","@","“","”"}}) {
      LinearLayout row = row();
      for (String value : chars) character(row, value, 1);
    }
    punctuationRow("符号", actions::symbolPanel,
        new String[] {"。", "，", "、", "?", "!", "."});
    LinearLayout row = row();
    back(row, 1.8f);
    key(row, "12\n34", 1.1f, actions::numberPad, false);
    row.getChildAt(row.getChildCount() - 1).setContentDescription("数字");
    space(row, 4.1f, speech);
    icon(row, "换行", 1.85f, actions::enter);
  }

  private void numberPad() {
    keyRows.removeAllViews();
    auxiliaryHeader();
    LinearLayout columns = Ui.row(getContext());
    keyRows.addView(columns, new LayoutParams(-1, -1));
    LinearLayout left = Ui.col(getContext()), middle = Ui.col(getContext()), right = Ui.col(getContext());
    columns.addView(left, new LayoutParams(0, -1, 1.15f));
    columns.addView(middle, new LayoutParams(0, -1, 4.2f));
    columns.addView(right, new LayoutParams(0, -1, 1.15f));
    for (char symbol : "%+-*".toCharArray()) {
      LinearLayout line = columnRow(left, .75f);
      key(line, "" + symbol, 1, () -> actions.letter(symbol), true);
    }
    back(columnRow(left, 1), 1);
    for (String digits : new String[] {"123", "456", "789"}) {
      LinearLayout line = columnRow(middle, 1);
      for (char digit : digits.toCharArray())
        key(line, "" + digit, 1, () -> actions.letter(digit), false);
    }
    LinearLayout last = columnRow(middle, 1);
    key(last, "符号", 1, actions::symbolPanel, false);
    character(last, "0", 1); character(last, ".", 1);
    icon(columnRow(right, 1), "退格", 1, actions::delete);
    key(columnRow(right, 1), "空格", 1, actions::space, true);
    icon(columnRow(right, 2), "换行", 1, actions::enter);
  }

  private LinearLayout columnRow(LinearLayout column, float weight) {
    LinearLayout row = Ui.row(getContext());
    column.addView(row, new LayoutParams(-1, 0, weight));
    return row;
  }

  private void character(LinearLayout row, String value, float weight) {
    key(row, value, weight, () -> {
      for (char ch : value.toCharArray()) actions.letter(ch);
    }, false);
  }

  private void punctuationRow(String label, Runnable switchPage, String[] values) {
    LinearLayout row = row();
    key(row, label, 1.35f, switchPage, true);
    LinearLayout middle = centerKeys(row);
    for (String value : values) {
      if (value.isEmpty()) middle.addView(new Space(getContext()), geometry(1));
      else character(middle, value, 1);
    }
    icon(row, "退格", 1.35f, actions::delete);
  }

  private LinearLayout centerKeys(LinearLayout row) {
    LinearLayout middle = Ui.row(getContext());
    row.addView(middle, new LayoutParams(0, -1, 7));
    return middle;
  }

  private void back(LinearLayout row, float weight) {
    View key = Icons.button(getContext(), R.drawable.ic_arrow_back, "返回键盘", actions::letters);
    key.setBackground(Ui.pressable(getContext(), Ui.specialKey(), 7));
    key.setElevation(Ui.dp(getContext(), .8f));
    row.addView(key, geometry(weight));
  }

  private void auxiliaryHeader() {
    Context c = getContext();
    idle.removeAllViews();
    HorizontalScrollView scroll = new HorizontalScrollView(c);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout quick = Ui.row(c);
    for (String value : new String[] {"#", "%", "&", "+", "…", "《", "》"}) {
      TextView item = Ui.text(c, value, 20, Ui.ink());
      item.setGravity(Gravity.CENTER); item.setContentDescription(value);
      item.setBackground(Ui.pressable(c, Color.TRANSPARENT, 7));
      item.setOnClickListener(v -> { for (char ch : value.toCharArray()) actions.letter(ch); });
      quick.addView(item, new LayoutParams(Ui.dp(c, 48), -1));
    }
    scroll.addView(quick, new HorizontalScrollView.LayoutParams(-2, -1));
    idle.addView(scroll, new LayoutParams(0, -1, 1));
    if (page == KeyboardPage.SYMBOLS && SymbolCatalog.pages(symbolCategory) > 1) {
      View previous = Icons.button(c, R.drawable.ic_arrow_back, "上一页符号",
          () -> { symbolPage--; symbols(); });
      previous.setEnabled(symbolPage > 0);
      previous.setAlpha(previous.isEnabled() ? 1 : .35f);
      idle.addView(previous, new LayoutParams(Ui.dp(c, 40), -1));
      TextView number = Ui.text(c, (symbolPage + 1) + "/" + SymbolCatalog.pages(symbolCategory), 13, Ui.sub());
      number.setGravity(Gravity.CENTER); idle.addView(number, new LayoutParams(Ui.dp(c, 36), -1));
      View next = Icons.button(c, R.drawable.ic_arrow_back, "下一页符号",
          () -> { symbolPage++; symbols(); });
      next.setRotation(180); next.setEnabled(symbolPage + 1 < SymbolCatalog.pages(symbolCategory));
      next.setAlpha(next.isEnabled() ? 1 : .35f);
      idle.addView(next, new LayoutParams(Ui.dp(c, 40), -1));
    }
    idle.addView(Icons.button(c, R.drawable.ic_close, "返回键盘", actions::letters),
        new LayoutParams(Ui.dp(c, 48), -1));
  }

  private void symbols() {
    keyRows.removeAllViews();
    auxiliaryHeader();
    java.util.List<String> values = SymbolCatalog.page(symbolCategory, symbolPage);
    for (int n = 0; n < 2; n++) {
      LinearLayout row = row();
      for (int i = n * 10; i < (n + 1) * 10; i++) {
        if (i < values.size()) character(row, values.get(i), 1);
        else row.addView(new Space(getContext()), geometry(1));
      }
    }
    String[] last = new String[6];
    for (int i = 0; i < 6; i++) last[i] = i + 20 < values.size() ? values.get(i + 20) : "";
    punctuationRow("123", actions::numbers, last);
    LinearLayout footer = row();
    back(footer, 1.35f);
    HorizontalScrollView viewport = new HorizontalScrollView(getContext());
    viewport.setHorizontalScrollBarEnabled(false);
    LinearLayout tabs = Ui.row(getContext());
    for (int i = 0; i < SymbolCatalog.NAMES.length; i++) {
      final int category = i;
      TextView tab = Ui.text(getContext(), SymbolCatalog.NAMES[i], 14,
          i == symbolCategory ? Ui.ink() : Ui.sub());
      tab.setGravity(Gravity.CENTER); tab.setSingleLine(); tab.setContentDescription(SymbolCatalog.NAMES[i]);
      tab.setBackground(Ui.pressable(getContext(), i == symbolCategory ? Ui.selected() : Color.TRANSPARENT, 12));
      tab.setSelected(i == symbolCategory); tab.setOnClickListener(v -> { symbolCategory = category; symbolPage = 0; symbols(); });
      tabs.addView(tab, new LayoutParams(Ui.dp(getContext(), 52), -1));
    }
    viewport.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
    footer.addView(viewport, new LayoutParams(0, -1, 8.35f));
  }

  private LinearLayout row() {
    LinearLayout r = Ui.row(getContext());
    keyRows.addView(r, new LayoutParams(-1, 0, 1));
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
    key.setSingleLine(!label.contains("\n"));
    key.setHorizontallyScrolling(false);
    if (label.contains("\n")) key.setMaxLines(2);
    if (special && label.length() > 1)
      key.setAutoSizeTextTypeUniformWithConfiguration(12, label.contains("\n") ? 14 : 16,
          1, android.util.TypedValue.COMPLEX_UNIT_SP);
    key.setGravity(Gravity.CENTER);
    key.setFontFeatureSettings("kern");
    key.setBackground(Ui.pressable(c, special ? Ui.specialKey() : Ui.key(), 7));
    key.setElevation(Ui.dp(c, .8f));
    key.setContentDescription(label.matches("[A-Z]")
        ? label.toLowerCase(java.util.Locale.ROOT) : label);
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

  private void icon(LinearLayout row, String name, float weight, Runnable click) {
    Context c = getContext();
    View key =
        Ui.icon(
            c,
            name,
            () -> {
              actions.haptic(this);
              click.run();
            });
    ((Ui.Glyph) key).color = Ui.ink();
    key.setBackground(Ui.pressable(c, Ui.specialKey(), 7));
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
    predictionMenu.setVisibility(GONE);
    predictionClose.setVisibility(GONE);
    candidateArea.setVisibility(VISIBLE);
    expand.setVisibility(VISIBLE);
    candidateRow.removeAllViews();
    candidateScroll.scrollTo(0, 0);
  }

  /** Starts a fresh prediction strip, keeping tools and the candidate grid reachable. */
  public void showPredictions() {
    clearCandidates();
    setComposing("");
    predictionMenu.setVisibility(VISIBLE);
    predictionClose.setVisibility(VISIBLE);
  }

  /** Restores the workspace bar without rebuilding or resizing any typing keys. */
  public void showIdle() {
    setComposing("");
    predictionMenu.setVisibility(GONE);
    predictionClose.setVisibility(GONE);
    candidateArea.setVisibility(GONE);
    expand.setVisibility(GONE);
    busy.setVisibility(GONE);
    editing.setVisibility(GONE);
    idle.setVisibility(VISIBLE);
  }

  /** Passive suggestions own only an ordinary typing header. Explicit edit controls and
   * recoverable speech must remain until their own action releases that surface. */
  public boolean acceptsSuggestions() {
    return keys.getVisibility() == VISIBLE && voiceState == VoiceState.IDLE
        && busy.getVisibility() != VISIBLE && editing.getVisibility() != VISIBLE;
  }

  /** Independent of the host editor's composing decoration; never grows the keyboard window. */
  public void setComposing(String raw) {
    String text = raw == null ? "" : raw;
    composing.setText(text);
    composing.setContentDescription("正在输入拼音：" + text);
    composingRow.setVisibility(text.isEmpty() ? GONE : VISIBLE);
    for (int i = 0; i < candidateRow.getChildCount(); i++)
      candidatePadding(candidateRow.getChildAt(i));
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
      predictionMenu.setVisibility(GONE);
      predictionClose.setVisibility(GONE);
      candidateArea.setVisibility(GONE);
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
    predictionMenu.setVisibility(GONE);
    predictionClose.setVisibility(GONE);
    candidateArea.setVisibility(GONE);
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
        Ui.text(getContext(), text, 20, candidateRow.getChildCount() == 0 ? Ui.accent() : Ui.ink());
    t.setSingleLine(true);
    t.setAutoSizeTextTypeUniformWithConfiguration(14, 20, 1,
        android.util.TypedValue.COMPLEX_UNIT_SP);
    t.setGravity(Gravity.CENTER);
    candidatePadding(t);
    t.setMinWidth(Ui.dp(getContext(), 48));
    t.setContentDescription(text);
    t.setBackground(Ui.pressable(getContext(), Color.TRANSPARENT, 8));
    t.setOnClickListener(v -> click.run());
    candidateRow.addView(t, new LayoutParams(-2, -1));
  }

  private void candidatePadding(View candidate) {
    int side = Ui.dp(getContext(), 14);
    // Spelling sits above candidates; the shared 48dp hit target never resizes the keys.
    candidate.setPadding(side,
        composingRow.getVisibility() == VISIBLE ? Ui.dp(getContext(), 18) : 0, side, 0);
  }

  @Override
  protected void onDetachedFromWindow() {
    repeats.removeCallbacksAndMessages(null);
    super.onDetachedFromWindow();
  }
}
