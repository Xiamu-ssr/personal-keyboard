package app.doudou.ime;

import android.content.*;
import android.graphics.*;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import app.doudou.ime.core.*;
import java.util.*;

public final class KeyboardService extends InputMethodService {
  private DoudouApp app;
  private LinearLayout root, keys, candidate;
  private TextView composingLabel, status;
  private EditText voiceText;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final StringBuilder composing = new StringBuilder();
  private final EditTracker edits = new EditTracker();
  private final AdvisorSession advisor = new AdvisorSession();
  private VoiceInput voice;
  private ModelClient model;
  private long generation;
  private int candidateSerial;
  private boolean english, shift, caps, symbols, incognito, sensitive, recording;
  private int compositionChange;
  private int bottomInset, contentHeight = 280;
  private boolean forcedLatin, normalEnglish;
  private long lastShift;
  private String pkg = "", mode = "typing", initialDraft = "", intent = "", lastFeedback = "";
  private int initialStart, initialEnd;
  private final ReplyHistory replyHistory = new ReplyHistory();
  private String[] styleNames = {"温和", "直接", "轻松"};
  private long chosenPerson;
  private String transcript = "";
  private String lastCandidateCode = "";
  private EditText localEditor;
  private Runnable finishLocal;
  private TextView voiceToggle;

  @Override
  public void onCreate() {
    super.onCreate();
    app = (DoudouApp) getApplication();
    voice = new VoiceInput(this);
    model = new ModelClient(this);
  }

  @Override
  public View onCreateInputView() {
    root = Ui.col(this);
    root.setBackgroundColor(Ui.PANEL);
    root.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          int bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
          if (bottom != bottomInset) {
            bottomInset = bottom;
            sizeInputView();
          }
          return insets;
        });
    typing();
    root.requestApplyInsets();
    return root;
  }

  @Override
  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    cancelJobs();
    generation++;
    candidateSerial++;
    composing.setLength(0);
    edits.clear();
    lastFeedback = "";
    pkg = info.packageName == null ? "" : info.packageName;
    int variation = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
    int clazz = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
    boolean password =
        (clazz == android.text.InputType.TYPE_CLASS_TEXT
                && (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            || (clazz == android.text.InputType.TYPE_CLASS_NUMBER
                && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    sensitive = password || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    if (forcedLatin) english = normalEnglish;
    forcedLatin = password;
    if (password) {
      normalEnglish = english;
      english = true;
    }
    symbols =
        clazz == android.text.InputType.TYPE_CLASS_NUMBER
            || clazz == android.text.InputType.TYPE_CLASS_PHONE;
    shift = false;
    caps = false;
    chosenPerson = 0;
    if (root != null) typing();
  }

  @Override
  public void onFinishInput() {
    cancelJobs();
    generation++;
    candidateSerial++;
    edits.clear();
    composing.setLength(0);
    super.onFinishInput();
  }

  @Override
  public void onFinishInputView(boolean finishing) {
    cancelJobs();
    super.onFinishInputView(finishing);
  }

  @Override
  public void onDestroy() {
    cancelJobs();
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  private void cancelJobs() {
    if (voice != null) voice.cancel();
    if (model != null) model.cancel();
    advisor.cancel();
    recording = false;
    replyHistory.clear();
    transcript = "";
    localEditor = null;
    finishLocal = null;
  }

  private boolean privateMode() {
    return sensitive || incognito;
  }

  private void reset(String next, int height) {
    mode = next;
    contentHeight = height;
    root.removeAllViews();
    sizeInputView();
  }

  private void sizeInputView() {
    int total = Ui.dp(this, contentHeight) + bottomInset;
    root.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4) + bottomInset);
    root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, total));
    root.setMinimumHeight(total);
    root.requestLayout();
  }

  private InputConnection input() {
    if (localEditor != null) return localEditor.onCreateInputConnection(new EditorInfo());
    return getCurrentInputConnection();
  }

  private void addKey(LinearLayout row, String text, float weight, Runnable action) {
    TextView b = Ui.text(this, text, text.length() > 2 ? 16 : 24, Ui.INK);
    b.setGravity(Gravity.CENTER);
    b.setBackground(Ui.bg(this, Ui.KEY, 9));
    b.setContentDescription(text);
    b.setFocusable(true);
    b.setOnClickListener(
        v -> {
          haptic(v);
          action.run();
        });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
    lp.setMargins(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 3), Ui.dp(this, 4));
    row.addView(b, lp);
    if (text.equals("空格")) {
      final float[] x = {0};
      b.setOnTouchListener(
          (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) x[0] = event.getX();
            if (event.getAction() == MotionEvent.ACTION_UP
                && Math.abs(event.getX() - x[0]) > Ui.dp(this, 28)) {
              int delta = event.getX() > x[0] ? 1 : -1;
              moveCursor(delta);
              return true;
            }
            return false;
          });
    }
  }

  private void addIcon(LinearLayout row, String name, float weight, Runnable action) {
    View b =
        Ui.icon(
            this,
            name,
            () -> {
              haptic(root);
              action.run();
            });
    b.setBackground(Ui.bg(this, Ui.LINE, 9));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
    lp.setMargins(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 3), Ui.dp(this, 4));
    row.addView(b, lp);
    if (name.equals("退格")) repeatDelete(b);
  }

  private void haptic(View v) {
    if (app.prefs().getBoolean("haptic", true))
      v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
  }

  private LinearLayout keyrow() {
    LinearLayout r = Ui.row(this);
    root.addView(r, new LinearLayout.LayoutParams(-1, 0, 1));
    return r;
  }

  private void typing() {
    if (root == null) return;
    mode = "typing";
    reset("typing", localEditor == null ? 280 : 390);
    if (localEditor != null) {
      panelHeading(
          "修改后返回",
          () -> {
            commitComposing();
            Runnable done = finishLocal;
            localEditor = null;
            finishLocal = null;
            if (done != null) done.run();
          });
      if (localEditor.getParent() != null)
        ((android.view.ViewGroup) localEditor.getParent()).removeView(localEditor);
      root.addView(localEditor, new LinearLayout.LayoutParams(-1, Ui.dp(this, 66)));
    }
    LinearLayout toolbar = Ui.row(this);
    View tools =
        Ui.icon(
            this,
            "工具",
            () -> {
              if (localEditor == null) tools();
            });
    toolbar.addView(tools, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 46)));
    composingLabel =
        Ui.text(this, privateMode() ? "隐私输入" : "豆豆", 13, privateMode() ? Ui.ACCENT : Ui.SUB);
    composingLabel.setGravity(Gravity.CENTER_VERTICAL);
    candidate = Ui.row(this);
    HorizontalScrollView candidates = new HorizontalScrollView(this);
    candidates.setHorizontalScrollBarEnabled(false);
    candidates.addView(candidate, new HorizontalScrollView.LayoutParams(-2, -1));
    candidate.addView(composingLabel, new LinearLayout.LayoutParams(-2, -1));
    toolbar.addView(candidates, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
    if (localEditor == null) {
      toolbar.addView(
          Ui.icon(this, "语音", this::showVoice),
          new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 46)));
      toolbar.addView(
          Ui.icon(this, "军师", () -> startAdvisor(false)),
          new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 46)));
    }
    root.addView(toolbar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
    if (symbols) {
      String[] rows = {"1234567890", "@#¥%&*+-=", "，。？！：；（）"};
      for (String letters : rows) {
        LinearLayout r = keyrow();
        for (int i = 0; i < letters.length(); i++) {
          final String ch = letters.substring(i, i + 1);
          addKey(r, ch, 1, () -> literal(ch));
        }
      }
      LinearLayout bottom = keyrow();
      addKey(
          bottom,
          "ABC",
          1.4f,
          () -> {
            symbols = false;
            typing();
          });
      addKey(bottom, "空格", 4, this::space);
      addIcon(bottom, "退格", 1.4f, this::delete);
      addIcon(bottom, "换行", 1.6f, this::enter);
    } else {
      String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
      for (int index = 0; index < rows.length; index++) {
        LinearLayout r = keyrow();
        if (index == 1) {
          Space s = new Space(this);
          r.addView(s, new LinearLayout.LayoutParams(0, 1, .5f));
        }
        if (index == 2)
          addIcon(
              r,
              "大写",
              1.4f,
              () -> {
                long now = SystemClock.uptimeMillis();
                caps = now - lastShift < 350 && !caps;
                shift = caps || !shift;
                lastShift = now;
                typing();
              });
        for (char letter : rows[index].toCharArray()) {
          final char ch = letter;
          addKey(
              r, (shift || caps ? "" + Character.toUpperCase(ch) : "" + ch), 1, () -> letter(ch));
        }
        if (index == 1) {
          r.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, .5f));
        }
        if (index == 2) addIcon(r, "退格", 1.4f, this::delete);
      }
      LinearLayout bottom = keyrow();
      addKey(
          bottom,
          "123",
          1.3f,
          () -> {
            commitComposing();
            symbols = true;
            typing();
          });
      addKey(bottom, "，", .8f, () -> literal(english ? "," : "，"));
      addKey(bottom, "空格", 3.8f, this::space);
      addKey(
          bottom,
          english ? "EN" : "中",
          .9f,
          () -> {
            commitComposing();
            english = !english;
            typing();
          });
      addIcon(bottom, "换行", 1.7f, this::enter);
    }
    if (composing.length() > 0) refreshCandidates();
  }

  private void letter(char ch) {
    if (english) {
      literal(shift || caps ? "" + Character.toUpperCase(ch) : "" + ch);
      if (shift && !caps) {
        shift = false;
        typing();
      }
      return;
    }
    composing.append(ch);
    InputConnection ic = input();
    if (ic != null) ic.setComposingText(composing, 1);
    refreshCandidates();
  }

  private void refreshCandidates() {
    if (composingLabel == null) return;
    composingLabel.setText(composing.toString());
    candidate.removeAllViews();
    String code = composing.toString();
    final long gen = generation;
    final int request = ++candidateSerial;
    addCandidate(code, () -> choose(code, code));
    if (app.pinyin == null) {
      addCandidate(app.dictionaryError.isEmpty() ? "词库载入中…" : app.dictionaryError, () -> {});
      main.postDelayed(
          () -> {
            if (generation == gen && mode.equals("typing") && request == candidateSerial)
              refreshCandidates();
          },
          300);
      return;
    }
    app.worker.execute(
        () -> {
          List<PinyinEngine.Word> personal = new ArrayList<>();
          if (!privateMode())
            for (Store.Item word : app.store.list("word", 0))
              if (!word.body.isEmpty())
                personal.add(new PinyinEngine.Word(word.title, word.body, 26));
          app.pinyin.personalWords(personal);
          List<PinyinEngine.Word> list =
              app.pinyin.candidates(
                  code, 12, privateMode() ? Collections.emptyMap() : app.store.frequencies());
          main.post(
              () -> {
                if (request != candidateSerial || gen != generation || !mode.equals("typing"))
                  return;
                candidate.removeAllViews();
                for (PinyinEngine.Word w : list) addCandidate(w.text, () -> choose(w.text, w.code));
                if (list.isEmpty()) addCandidate(code, () -> choose(code, code));
              });
        });
  }

  private void addCandidate(String text, Runnable click) {
    TextView b = Ui.text(this, text, 18, Ui.INK);
    b.setGravity(Gravity.CENTER);
    Ui.pad(this, b, 14, 0);
    b.setMinWidth(Ui.dp(this, 48));
    b.setOnClickListener(v -> click.run());
    candidate.addView(b, new LinearLayout.LayoutParams(-2, -1));
    if (candidate.getParent() instanceof HorizontalScrollView) return;
    // Horizontal scrolling keeps touch targets large even with many Chinese candidates.
    int at = root.indexOfChild(candidate);
    if (at >= 0) {
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) candidate.getLayoutParams();
      root.removeView(candidate);
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      scroll.addView(candidate, new HorizontalScrollView.LayoutParams(-2, -1));
      root.addView(scroll, at, lp);
    }
  }

  private void choose(String text, String code) {
    InputConnection ic = input();
    if (ic == null) return;
    String raw = composing.toString();
    String remaining = raw.startsWith(code) ? raw.substring(code.length()) : "";
    compositionChange++;
    ic.beginBatchEdit();
    try {
      ic.commitText(text, 1);
      ic.finishComposingText();
      composing.setLength(0);
      composing.append(remaining);
      if (!remaining.isEmpty()) ic.setComposingText(remaining, 1);
    } finally {
      ic.endBatchEdit();
    }
    candidateSerial++;
    if (!privateMode() && app.prefs().getBoolean("learning", true))
      app.worker.execute(
          () -> {
            app.store.learn(text, code);
            if (app.pinyin != null) app.pinyin.accept(code, text);
          });
    typing();
  }

  private void commitComposing() {
    if (composing.length() > 0) {
      InputConnection ic = input();
      if (ic != null) {
        ic.commitText(composing, 1);
        ic.finishComposingText();
      }
      composing.setLength(0);
      candidateSerial++;
    }
  }

  private void literal(String s) {
    if (composing.length() > 0 && !english) space();
    commitComposing();
    InputConnection ic = input();
    if (ic != null) ic.commitText(s, 1);
  }

  private void space() {
    if (composing.length() > 0) {
      final String code = composing.toString();
      if (app.pinyin != null) {
        List<PinyinEngine.Word> ws =
            app.pinyin.candidates(
                code, 1, privateMode() ? Collections.emptyMap() : app.store.frequencies());
        if (!ws.isEmpty()) {
          choose(ws.get(0).text, ws.get(0).code);
          return;
        }
      }
      choose(code, code);
    } else literal(" ");
  }

  private void delete() {
    InputConnection ic = input();
    if (ic == null) return;
    if (composing.length() > 0) {
      composing.deleteCharAt(composing.length() - 1);
      ic.setComposingText(composing, 1);
      if (composing.length() == 0) {
        ic.finishComposingText();
        typing();
      } else refreshCandidates();
    } else {
      CharSequence selected = ic.getSelectedText(0);
      if (selected != null && selected.length() > 0) ic.commitText("", 1);
      else ic.deleteSurroundingTextInCodePoints(1, 0);
    }
  }

  private void repeatDelete(View v) {
    final Runnable[] task = {null};
    v.setOnTouchListener(
        (view, e) -> {
          if (e.getAction() == MotionEvent.ACTION_DOWN) {
            task[0] =
                new Runnable() {
                  public void run() {
                    delete();
                    main.postDelayed(this, 65);
                  }
                };
            main.postDelayed(task[0], 380);
          } else if (e.getAction() == MotionEvent.ACTION_UP
              || e.getAction() == MotionEvent.ACTION_CANCEL) {
            if (task[0] != null) main.removeCallbacks(task[0]);
          }
          return false;
        });
  }

  private void enter() {
    if (composing.length() > 0 && !english) space();
    commitComposing();
    InputConnection ic = input();
    EditorInfo info = getCurrentInputEditorInfo();
    if (ic == null) return;
    int action =
        info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
    if (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)
      action = EditorInfo.IME_ACTION_NONE;
    // Even an app's SEND action is intentionally not invoked by this keyboard.
    if (action == EditorInfo.IME_ACTION_NEXT
        || action == EditorInfo.IME_ACTION_SEARCH
        || action == EditorInfo.IME_ACTION_GO
        || action == EditorInfo.IME_ACTION_DONE) ic.performEditorAction(action);
    else ic.commitText("\n", 1);
  }

  private String full() {
    InputConnection ic = input();
    if (ic == null) return null;
    ExtractedTextRequest req = new ExtractedTextRequest();
    req.hintMaxChars = 12001;
    ExtractedText x = ic.getExtractedText(req, 0);
    if (x == null || x.startOffset != 0 || x.text == null || x.text.length() > 12000) return null;
    return x.text.toString();
  }

  private int[] selection() {
    InputConnection ic = input();
    if (ic == null) return null;
    ExtractedText x = ic.getExtractedText(new ExtractedTextRequest(), 0);
    if (x == null || x.startOffset != 0 || x.selectionStart < 0 || x.selectionEnd < 0) return null;
    return new int[] {x.selectionStart, x.selectionEnd};
  }

  private void snapshot() {
    initialDraft = full();
    int[] s = selection();
    initialStart = s == null ? -1 : s[0];
    initialEnd = s == null ? -1 : s[1];
  }

  private boolean insert(String text) {
    InputConnection ic = input();
    if (ic == null) return false;
    String now = full();
    int[] s = selection();
    if (text.isEmpty()) return false;
    edits.clear();
    if (now != null && s != null)
      edits.begin(now, Math.min(s[0], s[1]), Math.max(s[0], s[1]), text, generation);
    ic.beginBatchEdit();
    try {
      ic.commitText(text, 1);
    } finally {
      ic.endBatchEdit();
    }
    lastFeedback = "";
    return true;
  }

  private void undoInsertion() {
    int cursor = edits.originalEnd();
    String old = edits.undo(full(), generation);
    if (old == null) {
      Ui.toast(this, "文字已被修改，未覆盖你的新输入");
      return;
    }
    InputConnection ic = input();
    if (ic == null) return;
    String now = full();
    if (now == null) return;
    ic.beginBatchEdit();
    try {
      ic.setSelection(0, now.length());
      ic.commitText(old, 1);
      ic.setSelection(cursor, cursor);
    } finally {
      ic.endBatchEdit();
    }
    typing();
  }

  @Override
  public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
    super.onUpdateSelection(os, oe, ns, ne, cs, ce);
    if (localEditor != null) return;
    if (cs < 0 && ce < 0 && composing.length() > 0) {
      final int change = compositionChange;
      final long gen = generation;
      main.post(
          () -> {
            if (change != compositionChange || gen != generation || composing.length() == 0) return;
            InputConnection ic = input();
            CharSequence before = ic == null ? null : ic.getTextBeforeCursor(composing.length(), 0);
            if (before != null && before.toString().endsWith(composing.toString())) return;
            composing.setLength(0);
            candidateSerial++;
            if (root != null && mode.equals("typing")) typing();
          });
    }
    if (!privateMode() && edits.active() && app.prefs().getBoolean("learning", true)) {
      final long gen = generation;
      main.postDelayed(
          () -> {
            if (gen != generation || privateMode() || localEditor != null) return;
            EditTracker.Change c = edits.observe(full(), gen);
            if (c != null
                && !c.before.isEmpty()
                && !c.after.isEmpty()
                && c.before.length() <= 64
                && c.after.length() <= 64
                && !lastFeedback.equals(c.before + "→" + c.after)) {
              lastFeedback = c.before + "→" + c.after;
              app.store.save(
                  0,
                  0,
                  "feedback",
                  0,
                  "一次表达修改",
                  c.before + " → " + c.after,
                  c.after,
                  "input:" + pkg);
            }
          },
          1200);
    }
  }

  private void tools() {
    commitComposing();
    reset("tools", 280);
    LinearLayout top = Ui.row(this);
    top.addView(
        Ui.icon(this, "返回", this::typing),
        new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    top.addView(Ui.text(this, "随手工具", 16, Ui.SUB));
    root.addView(top);
    String[] labels = {
      "语音输入", "原地军师", "热词短语", "剪贴板", "光标移动", "撤销上屏", privateMode() ? "退出隐私" : "隐私输入", "设置"
    };
    Runnable[] actions = {
      this::showVoice,
      () -> startAdvisor(false),
      this::phrases,
      this::clipboard,
      this::cursor,
      this::undoInsertion,
      () -> {
        incognito = !incognito;
        edits.clear();
        typing();
      },
      () -> settings("")
    };
    for (int row = 0; row < 2; row++) {
      LinearLayout r = keyrow();
      for (int j = 0; j < 4; j++) {
        int i = row * 4 + j;
        TextView b = Ui.button(this, labels[i], actions[i]);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        r.addView(b, p);
      }
    }
  }

  private void settings(String page) {
    cancelJobs();
    startActivity(
        new Intent(this, MainActivity.class)
            .putExtra("page", page.isEmpty() ? "settings" : page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private void message(String title, String detail, Runnable retry) {
    reset("error", 280);
    panelHeading(title, this::typing);
    TextView t = Ui.text(this, detail, 15, Ui.SUB);
    Ui.pad(this, t, 16, 16);
    root.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
    if (retry != null) root.addView(Ui.button(this, "重试", retry));
    root.addView(Ui.button(this, "打开设置", () -> settings("settings")));
  }

  private void panelHeading(String title, Runnable back) {
    LinearLayout h = Ui.row(this);
    h.addView(
        Ui.icon(this, "返回", back), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    TextView label = Ui.text(this, title, 17, Ui.INK);
    h.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    label.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(h);
  }

  private void phrases() {
    reset("phrases", 280);
    panelHeading("热词与常用表达", this::typing);
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.col(this);
    scroll.addView(list);
    List<Store.Item> words = app.store.list("word", 0);
    for (Store.Item i : words)
      list.addView(
          Ui.button(
              this,
              i.title,
              () -> {
                insert(i.title);
                typing();
              }));
    if (words.isEmpty()) list.addView(Ui.button(this, "添加你的第一个热词", () -> settings("memory")));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void clipboard() {
    if (privateMode()) {
      message("隐私输入", "隐私模式不读取剪贴板。", null);
      return;
    }
    android.content.ClipboardManager manager =
        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    ClipData clip = manager.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      message("剪贴板是空的", "没有读取或保存任何历史剪贴板。", null);
      return;
    }
    CharSequence text = clip.getItemAt(0).getText();
    if (text == null || text.length() > 10000) {
      message("无法粘贴", "只支持不超过一万字的纯文字剪贴板。", null);
      return;
    }
    String value = text.toString();
    reset("clipboard", 280);
    panelHeading("本次剪贴板", this::typing);
    ScrollView s = new ScrollView(this);
    TextView t = Ui.text(this, value, 16, Ui.INK);
    Ui.pad(this, t, 16, 8);
    s.addView(t);
    root.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(
        Ui.primary(
            this,
            "填入文字",
            () -> {
              insert(value);
              typing();
            }));
  }

  private void cursor() {
    reset("cursor", 280);
    panelHeading("移动光标", this::typing);
    LinearLayout r = keyrow();
    for (String label : new String[] {"←", "→", "全选"})
      addKey(
          r,
          label,
          1,
          () -> {
            if (label.equals("全选")) {
              String f = full();
              if (f != null) input().setSelection(0, f.length());
            } else moveCursor(label.equals("←") ? -1 : 1);
          });
    LinearLayout b = keyrow();
    addIcon(b, "退格", 1, this::delete);
    addKey(b, "返回键盘", 3, this::typing);
  }

  private void moveCursor(int delta) {
    commitComposing();
    InputConnection ic = input();
    int[] s = selection();
    String f = full();
    if (ic == null || s == null || f == null) return;
    int pos = delta < 0 ? Math.min(s[0], s[1]) : Math.max(s[0], s[1]);
    if (delta < 0 && pos > 0) pos = f.offsetByCodePoints(pos, -1);
    else if (delta > 0 && pos < f.length()) pos = f.offsetByCodePoints(pos, 1);
    ic.setSelection(pos, pos);
  }

  private void showVoice() {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用语音、读屏、历史与学习。", null);
      return;
    }
    commitComposing();
    voice.cancel();
    model.cancel();
    snapshot();
    reset("voice", 280);
    panelHeading(
        "说完，再按自己的习惯整理",
        () -> {
          voice.cancel();
          model.cancel();
          typing();
        });
    status = Ui.text(this, "点击开始说话", 13, Ui.SUB);
    Ui.pad(this, status, 14, 6);
    root.addView(status);
    voiceText = Ui.edit(this, "转写会显示在这里，也可以手动编辑", transcript);
    voiceText.setTextSize(17);
    voiceText.setShowSoftInputOnFocus(false);
    voiceText.setMinLines(2);
    voiceText.setFocusable(false);
    voiceText.setOnClickListener(
        v -> {
          voice.cancel();
          recording = false;
          String value = voiceText.getText().toString();
          editLocal(
              "修正语音文字",
              value,
              edited -> {
                transcript = edited;
                showVoice();
              });
        });
    root.addView(voiceText, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = Ui.row(this);
    voiceToggle =
        Ui.button(
            this,
            recording ? "停止" : "开始",
            () -> {
              if (recording) {
                recording = false;
                voice.stop();
                voiceToggle.setText("开始");
              } else startVoice();
            });
    actions.addView(voiceToggle, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    actions.addView(
        Ui.button(this, "整理", this::polishVoice),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    actions.addView(
        Ui.primary(
            this,
            "填入",
            () -> {
              String value = voiceText.getText().toString().trim();
              if (value.isEmpty()) return;
              voice.cancel();
              model.cancel();
              if (protectedInsert(value)) {
                history("语音输入", value, "voice");
                transcript = "";
                typing();
              }
            }),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    root.addView(actions);
  }

  private void startVoice() {
    recording = true;
    voiceToggle.setText("停止");
    final long gen = generation;
    voice.start(
        app.prefs().getBoolean("onlineVoice", false),
        new VoiceInput.Listener() {
          public void text(String value, boolean fin) {
            if (gen != generation || !mode.equals("voice")) return;
            transcript = value;
            voiceText.setText(value);
            voiceText.setSelection(value.length());
            if (fin) {
              recording = false;
              voiceToggle.setText("开始");
              status.setText("转写完成 · 可编辑、整理或填入");
            }
          }

          public void status(String s) {
            if (gen == generation && mode.equals("voice")) status.setText(s);
          }

          public void error(String s) {
            recording = false;
            if (gen == generation && mode.equals("voice")) {
              voiceToggle.setText("开始");
              status.setText(s);
            }
          }
        });
  }

  private void polishVoice() {
    voice.stop();
    recording = false;
    String value = voiceText.getText().toString().trim();
    if (value.isEmpty()) return;
    String original = value;
    final long gen = generation;
    if (!model.configured()) {
      status.setText("文字模型尚未配置；原始转写可直接填入");
      return;
    }
    status.setText("按热词和文风整理中…");
    model.polish(
        value,
        (text, error) -> {
          if (gen != generation || !mode.equals("voice")) return;
          if (error != null) {
            status.setText(error);
            return;
          }
          if (!voiceText.getText().toString().equals(original)) {
            status.setText("你已修改转写，保留当前文字；可重新整理");
            return;
          }
          transcript = text;
          voiceText.setText(text);
          status.setText("整理完成 · 请确认意思后填入");
        });
  }

  private void startAdvisor(boolean supplement) {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用军师读屏。", null);
      return;
    }
    if (CaptureService.current == null) {
      message("军师需要主动截屏权限", "只在你点击时截屏并在本机识字。请到设置开启，返回后再试。", () -> startAdvisor(false));
      return;
    }
    if (supplement && advisor.pages() >= AdvisorSession.MAX_PAGES) {
      Ui.toast(this, "已补读六屏，可修正当前文字或重新开始");
      return;
    }
    commitComposing();
    voice.cancel();
    model.cancel();
    if (!supplement) {
      snapshot();
      advisor.begin(generation, pkg);
      replyHistory.clear();
      intent = "";
    } else {
      advisor.stage = AdvisorSession.Stage.RESIZING;
      replyHistory.clear();
    }
    final int token = advisor.token();
    final long gen = generation;
    reset("capturing", 76);
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.text(this, "正在读取当前屏幕…", 14, Ui.SUB),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1));
    bar.addView(
        Ui.button(
            this,
            "取消",
            () -> {
              advisor.cancel();
              typing();
            }));
    root.addView(bar);
    awaitLayout(token, gen, 0, -1, 0);
  }

  private void awaitLayout(int token, long gen, int count, int last, int stable) {
    if (!advisor.valid(token, gen) || !mode.equals("capturing")) return;
    int[] loc = new int[2];
    root.getLocationOnScreen(loc);
    int top = loc[1];
    boolean measured =
        root.isLaidOut() && root.getHeight() <= Ui.dp(this, 90) + bottomInset && top > 100;
    int next = measured && top == last ? stable + 1 : 0;
    if (next >= 3) {
      advisor.stage = AdvisorSession.Stage.CAPTURING;
      CaptureService.capture(
          top,
          (bitmap, error) -> {
            if (!advisor.valid(token, generation) || gen != generation) {
              if (bitmap != null) bitmap.recycle();
              return;
            }
            if (error != null) {
              advisor.fail(error);
              advisorPanel();
              return;
            }
            advisor.stage = AdvisorSession.Stage.READING;
            LocalOcr.read(
                bitmap,
                (text, why) -> {
                  if (!advisor.valid(token, generation) || gen != generation) return;
                  if (why != null || text == null || text.trim().isEmpty()) {
                    advisor.fail(why == null ? "当前屏没有识别到文字，可以补充或重新截屏" : why);
                    advisorPanel();
                    return;
                  }
                  try {
                    advisor.append(text);
                  } catch (IllegalArgumentException limit) {
                    advisor.fail(limit.getMessage());
                  }
                  advisorPanel();
                });
          });
      return;
    }
    if (count > 40) {
      advisor.fail("宿主布局没有稳定，未截屏；可以重试");
      advisorPanel();
      return;
    }
    main.postDelayed(() -> awaitLayout(token, gen, count + 1, top, next), 50);
  }

  private void advisorPanel() {
    if (!advisor.valid(advisor.token(), generation)) return;
    reset("advisor", 300);
    panelHeading(
        "军师 · " + (advisor.person > 0 ? personName(advisor.person) : "先确认聊天对象"),
        () -> {
          advisor.cancel();
          model.cancel();
          typing();
        });
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = Ui.col(this);
    Ui.pad(this, content, 10, 4);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (advisor.stage == AdvisorSession.Stage.ERROR) {
      TextView e = Ui.text(this, advisor.error, 14, Ui.RED);
      content.addView(e);
      content.addView(Ui.button(this, "重新读当前屏", () -> startAdvisor(false)));
    }
    if (!advisor.context().isEmpty()) {
      if (advisor.person == 0) {
        TextView help = Ui.text(this, "识字完成。OCR 无法保证说话人身份，请确认对象；不会自动归档为人物事实。", 13, Ui.SUB);
        content.addView(help);
        for (Store.Item p : app.store.list("person", 0))
          if (p.aux.isEmpty() || p.aux.equals(pkg)) {
            content.addView(
                Ui.button(
                    this,
                    p.title,
                    () -> {
                      advisor.identify(p.id);
                      chosenPerson = p.id;
                      advisorPanel();
                    }));
          }
        content.addView(
            Ui.button(
                this,
                "创建这个聊天对象",
                () ->
                    editLocal(
                        "给聊天对象起个名字",
                        "",
                        name -> {
                          if (name.trim().isEmpty()) {
                            advisorPanel();
                            return;
                          }
                          long id =
                              app.store.save(0, 0, "person", 0, name.trim(), "", pkg, "manual");
                          advisor.identify(id);
                          chosenPerson = id;
                          advisorPanel();
                        })));
      } else if (replyHistory.replies().isEmpty()
          || advisor.stage == AdvisorSession.Stage.GENERATING) {
        TextView info =
            Ui.text(
                this,
                model.configured() ? "写下你想表达的立场，再生成三种说法。" : "文字模型尚未连接；本地读屏可用，配置后就能生成。",
                14,
                Ui.SUB);
        content.addView(info);
        content.addView(
            Ui.button(
                this,
                intent.isEmpty() ? "＋ 写下我想表达的意思" : intent,
                () ->
                    editLocal(
                        "想表达的意思",
                        intent,
                        value -> {
                          intent = value;
                          advisorPanel();
                        })));
        if (advisor.stage == AdvisorSession.Stage.GENERATING)
          content.addView(Ui.text(this, "正在组织表达…", 14, Ui.ACCENT));
        else
          content.addView(
              Ui.primary(
                  this,
                  model.configured() ? "生成三条回复" : "去配置文字模型",
                  () -> {
                    if (model.configured()) generate("");
                    else settings("settings");
                  }));
      } else {
        for (int index = 0; index < replyHistory.replies().size(); index++) {
          final int at = index;
          LinearLayout card = Ui.card(this);
          TextView label =
              Ui.text(
                  this,
                  !replyHistory.style().isEmpty()
                      ? replyHistory.style() + " · " + (index + 1)
                      : styleNames[index],
                  12,
                  Ui.ACCENT);
          card.addView(label);
          Ui.gap(this, card, 8);
          card.addView(Ui.text(this, replyHistory.replies().get(index), 16, Ui.INK));
          LinearLayout actions = Ui.row(this);
          actions.addView(
              Ui.button(this, "填入", () -> applyReply(replyHistory.replies().get(at))),
              new LinearLayout.LayoutParams(0, -2, 1));
          actions.addView(
              Ui.button(
                  this,
                  "更多",
                  () ->
                      generate(
                          replyHistory.style().isEmpty() ? styleNames[at] : replyHistory.style())),
              new LinearLayout.LayoutParams(0, -2, 1));
          card.addView(actions);
          content.addView(card);
          Ui.gap(this, content, 8);
        }
        if (replyHistory.canBack())
          content.addView(
              Ui.button(
                  this,
                  "返回上一组",
                  () -> {
                    replyHistory.back();
                    advisor.stage = AdvisorSession.Stage.RESULTS;
                    advisorPanel();
                  }));
      }
      Ui.gap(this, content, 6);
      content.addView(Ui.button(this, "查看 / 修正本次识字", this::editContext));
      Ui.gap(this, content, 6);
      content.addView(Ui.text(this, "上滑聊天，再点“补当前屏”。", 12, Ui.SUB));
    }
    LinearLayout bottom = Ui.row(this);
    bottom.addView(
        Ui.button(this, "补当前屏", () -> startAdvisor(true)),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    bottom.addView(
        Ui.button(
            this,
            "存文字",
            () -> {
              if (!advisor.context().isEmpty()) {
                history("主动读屏", advisor.context(), "capture:" + pkg);
                Ui.toast(this, "已存到本机历史，不是人物事实");
              }
            }),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    bottom.addView(
        Ui.button(
            this,
            "取消",
            () -> {
              model.cancel();
              advisor.cancel();
              typing();
            }),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    root.addView(bottom);
  }

  private void editContext() {
    editLocal(
        "识字文字 · 请核对说话人",
        advisor.context(),
        updated -> {
          long person = advisor.person;
          advisor.begin(generation, pkg);
          advisor.append(updated);
          if (person > 0) advisor.identify(person);
          replyHistory.clear();
          advisorPanel();
        });
  }

  private interface Edited {
    void done(String value);
  }

  private void editLocal(String title, String value, Edited callback) {
    commitComposing();
    EditText field = Ui.edit(this, title, value);
    field.setShowSoftInputOnFocus(false);
    field.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    field.setSelectAllOnFocus(false);
    localEditor = field;
    finishLocal = () -> callback.done(field.getText().toString());
    typing();
    field.requestFocus();
    field.setSelection(field.getText().length());
  }

  private String personName(long id) {
    Store.Item p = app.store.get(id);
    return p == null ? "未确认人物" : p.title;
  }

  private void generate(String style) {
    if (!advisor.canGenerate() || advisor.stage == AdvisorSession.Stage.GENERATING) return;
    Store.Item person = app.store.get(advisor.person);
    if (person == null || person.deleted || (!person.aux.isEmpty() && !person.aux.equals(pkg))) {
      advisor.fail("人物或应用范围已变化，请重新确认");
      advisorPanel();
      return;
    }
    final int token = advisor.token();
    final long gen = generation;
    String goal = intent;
    advisor.stage = AdvisorSession.Stage.GENERATING;

    advisorPanel();
    model.reply(
        pkg,
        advisor.person,
        advisor.context(),
        goal,
        style,
        (out, error) -> {
          if (!advisor.valid(token, generation) || gen != generation) return;
          if (error != null) {
            if (!replyHistory.replies().isEmpty()) {
              advisor.stage = AdvisorSession.Stage.RESULTS;
              Ui.toast(this, error);
            } else advisor.fail(error);
            advisorPanel();
            return;
          }
          replyHistory.accept(out, style, !style.isEmpty());
          advisor.stage = AdvisorSession.Stage.RESULTS;
          advisorPanel();
        });
  }

  private void applyReply(String value) {
    Store.Item p = app.store.get(advisor.person);
    if (p == null || p.deleted || (!p.aux.isEmpty() && !p.aux.equals(pkg))) {
      advisor.fail("人物范围已变化，请重新确认");
      advisorPanel();
      return;
    }
    if (protectedInsert(value)) {
      history("采纳的军师草稿", value, "advisor");
      advisor.cancel();
      model.cancel();
      typing();
    }
  }

  private boolean protectedInsert(String value) {
    String current = full();
    int[] s = selection();
    boolean changed =
        initialDraft == null
            || current == null
            || s == null
            || !initialDraft.equals(current)
            || s[0] != initialStart
            || s[1] != initialEnd;
    if (changed) {
      message("你已修改输入框", "为保护新草稿，没有自动填入。可以复制这条建议后自行粘贴。", null);
      root.addView(
          Ui.button(
              this,
              "复制建议",
              () -> {
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("军师建议", value));
                Ui.toast(this, "已复制");
              }));
      return false;
    }
    return insert(value);
  }

  private void history(String title, String text, String source) {
    if (privateMode() || !app.prefs().getBoolean("history", true)) return;
    app.store.save(0, 0, "history", 0, title, text, "draft", source);
  }
}
