package app.doudou.ime.app;

import android.content.*;
import android.graphics.*;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.engine.PinyinEngine;
import app.doudou.ime.input.learning.EditTracker;
import app.doudou.ime.memory.MemoryStore;
import java.util.*;

public final class KeyboardService extends InputMethodService
    implements app.doudou.ime.input.EditorHost {
  private KeyboardApp app;
  private LinearLayout root;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final StringBuilder composing = new StringBuilder();
  private final EditTracker edits = new EditTracker();
  private final EditTracker historyEdits = new EditTracker();
  private String trackedHistory = "", activeConversation = "", insertionBefore, insertionText;
  private int insertionStart, insertionEnd;
  private long insertionSerial, trackedInsertionSerial;
  private app.doudou.ime.voice.VoiceController voice;
  private app.doudou.ime.advisor.AdvisorController advisorController;
  private app.doudou.ime.input.ToolsPanel toolkit;
  private long generation;
  private int candidateSerial;
  private boolean english, shift, caps, symbols, incognito, sensitive;
  private int compositionChange;
  private int bottomInset, contentHeight = 284;
  private boolean forcedLatin, normalEnglish;
  private long lastShift;
  private String pkg = "", mode = "typing", initialDraft = "", lastFeedback = "";
  private int initialStart, initialEnd;
  private EditText localEditor;
  private Runnable finishLocal;
  private final app.doudou.ime.input.DictationEditor dictation = new app.doudou.ime.input.DictationEditor();

  private final class InputSurface extends LinearLayout {
    InputSurface() { super(KeyboardService.this); setOrientation(VERTICAL); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
      if (voice != null && voice.holding()) return voice.touch(event);
      return super.dispatchTouchEvent(event);
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    app = (KeyboardApp) getApplication();
    voice = new app.doudou.ime.voice.VoiceController(this);
    advisorController = new app.doudou.ime.advisor.AdvisorController(this);
    toolkit = new app.doudou.ime.input.ToolsPanel(this);
  }

  @Override
  public View onCreateInputView() {
    Window window = getWindow().getWindow();
    window.setDecorFitsSystemWindows(false);
    window.setNavigationBarColor(Ui.KEYBOARD);
    window.setNavigationBarContrastEnforced(false);
    WindowInsetsController bars = window.getInsetsController();
    if (bars != null) bars.setSystemBarsAppearance(
        0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    root = new InputSurface();
    root.setBackgroundColor(Ui.PANEL);
    root.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          // Android 15 and some OEM IMEs report their switcher as a caption bar.
          // It belongs to the system. Reserve it once; never draw controls underneath it.
          int bottom = insets.getInsets(
              WindowInsets.Type.navigationBars() | WindowInsets.Type.captionBar()).bottom;
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
  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  @Override
  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    cancelJobs();
    generation++;
    candidateSerial++;
    composing.setLength(0);
    edits.clear();
    historyEdits.clear();
    trackedHistory = "";
    activeConversation = "";
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
    if (root != null) typing();
  }

  @Override
  public void onFinishInput() {
    observeHistory();
    cancelJobs();
    generation++;
    candidateSerial++;
    edits.clear();
    historyEdits.clear();
    trackedHistory = "";
    activeConversation = "";
    composing.setLength(0);
    super.onFinishInput();
  }

  @Override
  public void onFinishInputView(boolean finishing) {
    observeHistory();
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
    if (advisorController != null) advisorController.cancel();
    localEditor = null;
    finishLocal = null;
  }

  public boolean privateMode() {
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
    root.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), Ui.dp(this, 4) + bottomInset);
    root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, total));
    root.setMinimumHeight(total);
    root.requestLayout();
  }

  private InputConnection input() {
    if (localEditor != null) return localEditor.onCreateInputConnection(new EditorInfo());
    return getCurrentInputConnection();
  }

  private app.doudou.ime.input.KeyboardView keyboard;
  private List<PinyinEngine.Word> visibleCandidates = new ArrayList<>();

  private void haptic(View v) {
    if (app.prefs().getBoolean("haptic", true))
      v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
  }

  public void typing() {
    if (root == null) return;
    reset("typing", localEditor == null ? 284 : 394);
    root.setBackgroundColor(Ui.KEYBOARD);
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
        ((ViewGroup) localEditor.getParent()).removeView(localEditor);
      root.addView(localEditor, new LinearLayout.LayoutParams(-1, Ui.dp(this, 66)));
    }
    keyboard =
        new app.doudou.ime.input.KeyboardView(
            this,
            english,
            shift || caps,
            symbols,
            privateMode(),
            localEditor != null,
            new app.doudou.ime.input.KeyboardView.Actions() {
              public void letter(char ch) {
                if (Character.isLetter(ch)) KeyboardService.this.letter(ch);
                else literal("" + ch);
              }

              public void space() {
                KeyboardService.this.space();
              }

              public void enter() {
                KeyboardService.this.enter();
              }

              public void delete() {
                KeyboardService.this.delete();
              }

              public void shift() {
                long now = SystemClock.uptimeMillis();
                caps = now - lastShift < 350 && !caps;
                shift = caps || !shift;
                lastShift = now;
                typing();
              }

              public void symbols() {
                commitComposing();
                symbols = !symbols;
                typing();
              }

              public void language() {
                commitComposing();
                english = !english;
                typing();
              }

              public void cursor(int d) {
                moveCursor(d);
              }

              public void tools() {
                if (localEditor == null) toolkit.show();
              }

              public void voice() {
                if (localEditor == null) showVoice();
              }

              public void holdVoice(float x, float y) {
                if (localEditor == null) voice.hold(x, y);
              }

              public void advisor() {
                if (localEditor == null) startAdvisor(false);
              }

              public void hide() {
                requestHideSelf(0);
              }

              public void expand() {
                showCandidates();
              }

              public void haptic(View key) {
                KeyboardService.this.haptic(key);
              }
            });
    root.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1));
    if (composing.length() > 0) refreshCandidates();
  }

  private void showCandidates() {
    if (composing.length() == 0) return;
    final List<PinyinEngine.Word> options = new ArrayList<>(visibleCandidates);
    reset("candidates", 284);
    panelHeading(composing.toString(), this::typing);
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.col(this);
    Ui.pad(this, list, 12, 0);
    scroll.addView(list);
    for (PinyinEngine.Word word : options)
      list.addView(Ui.cell(this, "", word.text, "", "", () -> choose(word.text, word.code)));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
    if (keyboard == null) return;
    keyboard.clearCandidates();
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
            for (MemoryStore.Item word : app.store.list("word", 0))
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
                keyboard.clearCandidates();
                visibleCandidates = new ArrayList<>(list);
                for (PinyinEngine.Word w : list) addCandidate(w.text, () -> choose(w.text, w.code));
                if (list.isEmpty()) addCandidate(code, () -> choose(code, code));
              });
        });
  }

  private void addCandidate(String text, Runnable click) {
    keyboard.addCandidate(text, click);
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

  public void commitComposing() {
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

  public void delete() {
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

  public String full() {
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

  public void snapshot() {
    initialDraft = full();
    int[] s = selection();
    initialStart = s == null ? -1 : s[0];
    initialEnd = s == null ? -1 : s[1];
  }

  public boolean insert(String text) {
    InputConnection ic = input();
    if (ic == null) return false;
    String now = full();
    int[] s = selection();
    if (text.isEmpty()) return false;
    insertionSerial++;
    edits.clear();
    if (now != null && s != null)
      edits.begin(now, Math.min(s[0], s[1]), Math.max(s[0], s[1]), text, generation);
    insertionBefore = now;
    insertionText = text;
    insertionStart = s == null ? -1 : Math.min(s[0], s[1]);
    insertionEnd = s == null ? -1 : Math.max(s[0], s[1]);
    ic.beginBatchEdit();
    try {
      ic.commitText(text, 1);
    } finally {
      ic.endBatchEdit();
    }
    lastFeedback = "";
    return true;
  }

  public void undoInsertion() {
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
    if (!trackedHistory.isEmpty() && trackedInsertionSerial == insertionSerial) {
      app.histories.withdraw(trackedHistory);
      trackedHistory = "";
      historyEdits.clear();
    } else observeHistory();
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
    if (!trackedHistory.isEmpty() && "".equals(full())) {
      historyEdits.clear();
      trackedHistory = "";
      activeConversation = "";
    }
    if (!privateMode() && historyEdits.active()) {
      final long gen = generation;
      main.postDelayed(
          () -> {
            if (gen != generation || privateMode() || localEditor != null) return;
            observeHistory();
          },
          1200);
    }
  }

  public void settings(String page) {
    cancelJobs();
    startActivity(
        new Intent(this, MainActivity.class)
            .putExtra("page", page.isEmpty() ? "settings" : page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  public void message(String title, String detail, String actionLabel, Runnable action) {
    reset("error", 284);
    panelHeading(title, this::typing);
    TextView t = Ui.text(this, detail, 15, Ui.SUB);
    Ui.pad(this, t, 16, 16);
    root.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
    if (action != null) root.addView(Ui.primary(this, actionLabel, action));
    root.addView(Ui.button(this, "返回键盘", this::typing));
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

  public void moveCursor(int delta) {
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

  public void editLocal(String title, String value, java.util.function.Consumer<String> callback) {
    commitComposing();
    EditText field = Ui.edit(this, title, value);
    field.setShowSoftInputOnFocus(false);
    field.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    field.setSelectAllOnFocus(false);
    localEditor = field;
    finishLocal = () -> callback.accept(field.getText().toString());
    typing();
    field.requestFocus();
    field.setSelection(field.getText().length());
  }

  public boolean protectedInsert(String value) {
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
      message("你已修改输入框", "为保护新草稿，没有自动填入。可以复制这条建议后自行粘贴。", "", null);
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

  public void trackHistory(String id) {
    if (privateMode() || !app.prefs().getBoolean("history", true)) return;
    trackedHistory = id;
    trackedInsertionSerial = insertionSerial;
    historyEdits.begin(insertionBefore, insertionStart, insertionEnd, insertionText, generation);
    app.doudou.ime.history.HistoryStore.Entry entry = app.histories.get(id);
    if (entry != null) activeConversation = entry.conversationRef;
  }

  public String conversationRef() {
    return activeConversation;
  }

  private void observeHistory() {
    if (app == null || privateMode() || localEditor != null || trackedHistory.isEmpty() || dictation.active()) return;
    String value = historyEdits.observedText(full(), generation);
    if (value != null) app.histories.observed(trackedHistory, value);
  }

  public android.content.Context context() {
    return this;
  }

  public KeyboardApp services() {
    return app;
  }

  public long generation() {
    return generation;
  }

  public String editorPackage() {
    return pkg;
  }

  public String mode() {
    return mode;
  }

  public int bottomInset() {
    return bottomInset;
  }

  public LinearLayout openPanel(String next, int height) {
    reset(next, height);
    return root;
  }

  public void openVoice() {
    showVoice();
  }

  public void openAdvisor() {
    startAdvisor(false);
  }

  public void cancelVoice() {
    voice.cancel();
  }

  public void cancelAdvisor() {
    advisorController.cancel();
  }

  public void togglePrivacy() {
    observeHistory();
    incognito = !incognito;
    historyEdits.clear();
    trackedHistory = "";
    activeConversation = "";
    edits.clear();
    typing();
  }

  public void selectAll() {
    String f = full();
    if (f != null && input() != null) input().setSelection(0, f.length());
  }

  private void showVoice() {
    voice.show();
  }

  public boolean previewVoice(String text) {
    if (!dictation.active()) {
      String current = full(); int[] selected = selection();
      if (current == null || selected == null || initialDraft == null || !initialDraft.equals(current)
          || selected[0] != initialStart || selected[1] != initialEnd) return false;
      if (!dictation.begin(input(), current, selected[0], selected[1])) return false;
    }
    return dictation.update(text);
  }

  public boolean finishVoice(String text) {
    dictation.restore();
    return protectedInsert(text);
  }

  public void restoreVoice() { dictation.restore(); }

  private void startAdvisor(boolean supplement) {
    advisorController.start(supplement);
  }
}
