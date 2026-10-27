package app.doudou.ime.app;

import app.doudou.ime.design.Skin;

import android.content.*;
import android.content.ClipboardManager;
import android.graphics.*;
import android.inputmethodservice.InputMethodService;
import app.doudou.ime.input.Graphemes;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.KeyboardExtras;
import app.doudou.ime.input.KeyboardView;
import app.doudou.ime.input.KeyboardPage;
import app.doudou.ime.input.WorkspaceBar;
import app.doudou.ime.recording.RecordingPanel;
import app.doudou.ime.translation.TranslationController;
import app.doudou.ime.input.engine.PinyinEngine;
import app.doudou.ime.input.learning.EditTracker;
import app.doudou.ime.memory.MemoryRefresh;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.voice.VoiceGesture;
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
  private app.doudou.ime.input.KeyboardMenu keyboardMenu;
  private RecordingPanel recordingPanel;
  private TranslationController translation;
  private ClipboardManager clipboard;
  private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
      () -> rememberClipboard(true);
  private long generation;
  private volatile int candidateSerial;
  private String pendingPrediction;
  private String dismissedPrediction = "";
  private boolean english, shift, caps, incognito, sensitive;
  private KeyboardPage inputPage = KeyboardPage.LETTERS;
  private int compositionChange;
  private int bottomInset, contentHeight = KeyboardView.HEIGHT_DP;
  private boolean forcedLatin, normalEnglish;
  private long lastShift;
  private String pkg = "", mode = "typing", initialDraft = "", lastFeedback = "";
  private final Runnable selectionSuggestions = () -> {
    if (root != null && mode.equals("typing") && composing.length() == 0
        && (voice == null || !voice.active())) refreshPredictions();
  };
  private int initialStart, initialEnd;
  private EditText localEditor;
  private Runnable finishLocal;
  private final app.doudou.ime.input.DictationEditor dictation =
      new app.doudou.ime.input.DictationEditor();

  private final class InputSurface extends LinearLayout {
    InputSurface() {
      super(KeyboardService.this);
      setOrientation(VERTICAL);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
      if (voice != null && voice.holding()) return voice.touch(event);
      return super.dispatchTouchEvent(event);
    }
  }

  @Override
  public void onCreate() {
    Skin.load(this);
    setTheme(Skin.theme());
    super.onCreate();
    app = (KeyboardApp) getApplication();
    voice = new app.doudou.ime.voice.VoiceController(this);
    advisorController = new app.doudou.ime.advisor.AdvisorController(this);
    keyboardMenu = new app.doudou.ime.input.KeyboardMenu(this);
    recordingPanel = new RecordingPanel(this);
    translation = new TranslationController(this);
    clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    clipboard.addPrimaryClipChangedListener(clipListener);
  }

  private void rememberClipboard(boolean changed) {
    String selected =
        android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
    if (selected == null || !selected.startsWith(getPackageName() + "/")) return;
    if (privateMode()
        || ((android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE)).isDeviceLocked()) {
      app.prefs().edit().putBoolean("clipboardAwaitNewCopy", true).apply();
      return;
    }
    // A private copy must not be imported later merely because the keyboard reopens.
    if (!changed && app.prefs().getBoolean("clipboardAwaitNewCopy", false)) return;
    if (changed) app.prefs().edit().remove("clipboardAwaitNewCopy").apply();
    app.clips.capture(clipboard.getPrimaryClip());
  }

  @Override
  public View onCreateInputView() {
    Window window = getWindow().getWindow();
    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    window.setDecorFitsSystemWindows(false);
    skinWindow();
    root = new InputSurface();
    root.setBackgroundColor(Ui.panel());
    root.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          // Android 15 and some OEM IMEs report their switcher as a caption bar.
          // It belongs to the system. Reserve it once; never draw controls underneath it.
          int bottom =
              insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.captionBar())
                  .bottom;
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
    dismissedPrediction = "";
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
    inputPage = clazz == android.text.InputType.TYPE_CLASS_NUMBER
            || clazz == android.text.InputType.TYPE_CLASS_PHONE ? KeyboardPage.NUMBER_PAD : KeyboardPage.LETTERS;
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
  public void onStartInputView(EditorInfo info, boolean restarting) {
    super.onStartInputView(info, restarting);
    Skin.load(this);
    getTheme().applyStyle(Skin.theme(), true);
    skinWindow();
    MemoryRefresh.start(app);
    // Hiding cancels dictation. Reopening the same editor must not retain a disabled key surface.
    if (root != null && !advisorController.retainsInputView()) typing();
    rememberClipboard(false);
  }

  private void skinWindow() {
    Window window = getWindow().getWindow();
    window.setNavigationBarColor(Ui.keyboard());
    window.setNavigationBarContrastEnforced(false);
    WindowInsetsController bars = window.getInsetsController();
    if (bars != null) bars.setSystemBarsAppearance(
        Skin.light() ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
  }

  @Override
  public void onFinishInputView(boolean finishing) {
    observeHistory();
    if (advisorController.retainDuringGesture(finishing)) {
      // A chat can hide the IME on touch-down without ending the editor session.
      // Dictation still stops; the capture gesture owns its temporary visibility change.
      voice.cancel();
      localEditor = null;
      finishLocal = null;
    } else cancelJobs();
    super.onFinishInputView(finishing);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
      if (localEditor != null) {
        finishLocalEdit();
        return true;
      }
      if (voice != null && voice.active()) {
        voice.leave();
        return true;
      }
      if (mode.equals("typing") && inputPage != KeyboardPage.LETTERS) {
        letters();
        return true;
      }
      if (mode.equals("library")) {
        keyboardMenu.show();
        return true;
      }
      if (mode.equals("tools")
          || mode.equals("emoji")
          || mode.equals("candidates")
          || mode.equals("keyboards")
          || mode.equals("recorder")) {
        typing();
        return true;
      }
      if (mode.equals("translation")) {
        typing();
        return true;
      }
      if (mode.equals("advisor") || mode.equals("capturing")) {
        advisorController.leave();
        return true;
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void onDestroy() {
    if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
    cancelJobs();
    if (translation != null) translation.cancel();
    generation++;
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  private void cancelJobs() {
    if (voice != null) voice.cancel();
    if (advisorController != null) advisorController.cancel();
    if (translation != null) translation.cancel();
    localEditor = null;
    finishLocal = null;
  }

  public boolean privateMode() {
    return sensitive || incognito;
  }

  private void reset(String next, int height) {
    if (translation != null && !next.equals("translation")) translation.cancel();
    mode = next;
    candidateSerial++;
    candidateQuery = null;
    pendingPrediction = null;
    contentHeight = height;
    root.removeAllViews();
    root.setClipToOutline(false);
    sizeInputView();
  }

  private void sizeInputView() {
    int total = Ui.dp(this, contentHeight) + bottomInset;
    int sides =
        mode.equals("typing") || mode.equals("advisor") || mode.equals("capturing")
            ? 0
            : Ui.dp(this, 3);
    root.setPadding(sides, 0, sides, Ui.dp(this, 4) + bottomInset);
    root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, total));
    root.setMinimumHeight(total);
    root.requestLayout();
  }

  private InputConnection input() {
    if (localEditor != null) return localEditor.onCreateInputConnection(new EditorInfo());
    return getCurrentInputConnection();
  }

  private app.doudou.ime.input.KeyboardView keyboard;
  private PinyinEngine.Query candidateQuery;

  private void haptic(View v) {
    if (app.prefs().getBoolean("haptic", true))
      v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
  }

  public View workspaceBar(WorkspaceBar.Mode selected) {
    return new WorkspaceBar(this, selected, privateMode(), new WorkspaceBar.Actions() {
      public void select(WorkspaceBar.Mode next) {
        if (next == WorkspaceBar.Mode.RECORDING) recordingPanel.enter();
        else if (next == WorkspaceBar.Mode.ADVISOR) startAdvisor(false);
        else if (next == WorkspaceBar.Mode.TRANSLATION)
          translation.show(mode.equals("recorder") ? recordingPanel.text() : null);
        else typing();
      }
      public void tools() { keyboardMenu.show(); }
      public void hide() { requestHideSelf(0); }
    });
  }

  private void finishLocalEdit() {
    commitComposing();
    Runnable done = finishLocal;
    localEditor = null;
    finishLocal = null;
    if (done != null) done.run();
  }

  public void letters() {
    inputPage = KeyboardPage.LETTERS;
    typing();
  }

  private void inputPage(KeyboardPage next) {
    voice.beforeEdit();
    // Switching layout adopts the visible first candidate; Enter remains raw-pinyin commit.
    if (composing.length() > 0) space();
    inputPage = next;
    typing();
  }

  public void symbols() { inputPage(KeyboardPage.SYMBOLS); }

  public void typing() {
    if (root == null) return;
    if (app.prefs().contains("preferredEnglish")) {
      english = forcedLatin || app.prefs().getBoolean("preferredEnglish", false);
      inputPage = KeyboardPage.LETTERS;
      app.prefs().edit().remove("preferredEnglish").apply();
    }
    reset("typing", KeyboardView.HEIGHT_DP + (localEditor == null ? 0 : 110));
    root.setBackgroundColor(Ui.keyboard());
    if (localEditor != null) {
      panelHeading(
          "修改后返回",
          this::finishLocalEdit);
      if (localEditor.getParent() != null)
        ((ViewGroup) localEditor.getParent()).removeView(localEditor);
      root.addView(localEditor, new LinearLayout.LayoutParams(-1, Ui.dp(this, 66)));
    }
    keyboard =
        new app.doudou.ime.input.KeyboardView(
            this,
            english,
            shift || caps,
            inputPage,
            privateMode(),
            localEditor != null,
            new app.doudou.ime.input.KeyboardView.Actions() {
              public void letter(char ch) {
                voice.beforeEdit();
                if (inputPage == KeyboardPage.LETTERS && Character.isLetter(ch)) KeyboardService.this.letter(ch);
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

              public void numbers() { inputPage(KeyboardPage.NUMBERS); }
              public void numberPad() { inputPage(KeyboardPage.NUMBER_PAD); }
              public void letters() { KeyboardService.this.letters(); }

              public void language() {
                commitComposing();
                english = !english;
                typing();
              }

              public void cursor(int d) {
                moveCursor(d);
              }

              public void more() {
                if (localEditor == null) keyboardMenu.show();
              }

              public void voice() {
                if (localEditor == null) {
                  if (composing.length() > 0) space();
                  showVoice();
                }
              }

              public void holdVoice() {
                if (localEditor == null) {
                  if (composing.length() > 0) space();
                  voice.hold();
                }
              }

              public void recorder() {
                if (localEditor == null) recordingPanel.enter();
              }

              public void hide() {
                requestHideSelf(0);
              }

              public void symbolPanel() {
                KeyboardService.this.symbols();
              }

              public void advisor() {
                if (localEditor == null) startAdvisor(false);
              }

              public void finishVoice(boolean newline) {
                voice.finishRecording(newline);
              }

              public void cancelVoice() {
                voice.leave();
              }

              public void recoverVoice() {
                voice.insertTranscript();
              }

              public void selectAll() {
                KeyboardService.this.selectAll();
              }

              public void finishEditing() {
                typing();
              }

              public void expand() {
                showCandidates();
              }

              public void dismissPredictions() {
                dismissedPrediction = predictionBefore();
                candidateSerial++;
                candidateQuery = null;
                pendingPrediction = null;
                keyboard.showIdle();
              }

              public void haptic(View key) {
                KeyboardService.this.haptic(key);
              }
            }, localEditor == null ? workspaceBar(WorkspaceBar.Mode.INPUT) : null);
    root.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1));
    if (composing.length() > 0) refreshCandidates();
    else if (!voice.active()) refreshPredictions();
    if (voice.active()) voice.render();
  }

  private void showCandidates() {
    if (candidateQuery == null) return;
    final PinyinEngine.Query query = candidateQuery;
    if (query.continuation) {
      if (!predictionMatches(query.input)) return;
    } else if (composing.length() == 0
        || !query.input.equals(PinyinEngine.normalize(composing.toString()))) return;
    reset("candidates", KeyboardView.HEIGHT_DP);
    root.addView(new app.doudou.ime.input.CandidatePanel(this,
        query.continuation ? "联想" : composing.toString(), query, this::typing,
        word -> { if (query.continuation) choosePrediction(word, query.input); else choose(word); }),
        new LinearLayout.LayoutParams(-1, 0, 1));
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
    composing.append(shift || caps ? Character.toUpperCase(ch) : ch);
    InputConnection ic = input();
    if (ic != null) ic.setComposingText(composing, 1);
    if (shift && !caps) {
      shift = false;
      typing();
    } else refreshCandidates();
  }

  private void refreshCandidates() {
    if (keyboard == null) return;
    keyboard.clearCandidates();
    String code = composing.toString();
    keyboard.setComposing(code);
    candidateQuery = null;
    pendingPrediction = null;
    final long gen = generation;
    final int request = ++candidateSerial;
    final boolean privateInput = privateMode();
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
          if (request != candidateSerial) return;
          List<PinyinEngine.Word> personal = new ArrayList<>();
          if (!privateInput)
            for (MemoryStore.Item word : app.store.list("word", 0))
              if (word.usableWord() && !word.body.isEmpty())
                personal.add(new PinyinEngine.Word(word.title, word.body, 26));
          app.pinyin.personalWords(personal);
          PinyinEngine.Query query =
              app.pinyin.query(code, privateInput ? Collections.emptyMap() : app.store.frequencies());
          List<PinyinEngine.Word> list = query.page(0, 12);
          main.post(
              () -> {
                if (request != candidateSerial || gen != generation || !mode.equals("typing"))
                  return;
                keyboard.clearCandidates();
                keyboard.setComposing(code);
                candidateQuery = query;
                for (PinyinEngine.Word w : list)
                  addCandidate(w.text, () -> choose(w));
                if (list.isEmpty()) addCandidate(code, () -> choose(code, code));
              });
        });
  }

  private void addCandidate(String text, Runnable click) {
    keyboard.addCandidate(text, click);
  }

  private void choose(String text, String code) {
    choose(text, code, code);
  }

  private void choose(PinyinEngine.Word word) {
    choose(word.text, word.code, word.kind == PinyinEngine.Kind.WORD ? word.spelling : "");
  }

  /** One bounded editor snapshot, not an accumulation of typed text or a new history. */
  private String predictionBefore() {
    if (english || inputPage != KeyboardPage.LETTERS || sensitive
        || composing.length() != 0 || voice.active()) return "";
    InputConnection ic = input();
    if (ic == null) return "";
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) return "";
    CharSequence before = ic.getTextBeforeCursor(64, 0);
    return before == null ? "" : before.toString();
  }

  private boolean predictionMatches(String before) {
    return (mode.equals("candidates") || (mode.equals("typing") && keyboard != null
        && keyboard.acceptsSuggestions())) && !before.isEmpty() && before.equals(predictionBefore());
  }

  private void refreshPredictions() {
    if (keyboard == null || !mode.equals("typing")) return;
    if (!keyboard.acceptsSuggestions()) {
      candidateSerial++;
      pendingPrediction = null;
      candidateQuery = null;
      return;
    }
    String before = predictionBefore();
    if (PinyinEngine.predictionSuffix(before).isEmpty() || before.equals(dismissedPrediction)) {
      candidateSerial++;
      pendingPrediction = null;
      candidateQuery = null;
      keyboard.showIdle();
      return;
    }
    if (candidateQuery != null && candidateQuery.continuation
        && candidateQuery.input.equals(before)) return;
    if (before.equals(pendingPrediction)) return;
    if (app.pinyin == null) return;
    pendingPrediction = before;
    candidateQuery = null;
    final long gen = generation;
    final int request = ++candidateSerial;
    final boolean privateInput = privateMode();
    app.worker.execute(() -> {
      if (request != candidateSerial) return;
      List<PinyinEngine.Word> personal = new ArrayList<>();
      if (!privateInput)
        for (MemoryStore.Item word : app.store.list("word", 0))
          if (word.usableWord() && !word.body.isEmpty())
            personal.add(new PinyinEngine.Word(word.title, word.body, 26));
      app.pinyin.personalWords(personal);
      PinyinEngine.Query query = app.pinyin.predict(before,
          privateInput ? Collections.emptyMap() : app.store.frequencies());
      main.post(() -> {
        if (request != candidateSerial || gen != generation || !mode.equals("typing")
            || !keyboard.acceptsSuggestions()) return;
        pendingPrediction = null;
        if (!predictionMatches(before)) { refreshPredictions(); return; }
        candidateQuery = query;
        if (query.size() == 0) { keyboard.showIdle(); return; }
        keyboard.showPredictions();
        for (PinyinEngine.Word word : query.page(0, 16))
          addCandidate(word.text, () -> choosePrediction(word, before));
      });
    });
  }

  private void choosePrediction(PinyinEngine.Word word, String before) {
    // Re-check the caret and draft on the tap, not just when a worker result arrived. App
    // edits, selection changes and deleted drafts can never append a stale prediction.
    if (!predictionMatches(before)) {
      if (mode.equals("candidates")) typing(); else refreshPredictions();
      return;
    }
    InputConnection ic = input();
    if (ic == null) return;
    voice.beforeEdit();
    ic.commitText(word.text, 1);
    candidateSerial++;
    typing();
  }

  private void choose(String text, String code, String spelling) {
    InputConnection ic = input();
    if (ic == null) return;
    String raw = composing.toString();
    String remaining =
        raw.toLowerCase(java.util.Locale.ROOT).startsWith(code.toLowerCase(java.util.Locale.ROOT))
            ? raw.substring(code.length())
            : "";
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
    if (!privateMode() && !spelling.isEmpty())
      app.worker.execute(
          () -> {
            app.store.learn(text, spelling);
            if (app.pinyin != null) app.pinyin.accept(spelling, text);
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
    if (composing.length() > 0) space();
    InputConnection ic = input();
    if (ic != null) ic.commitText(s, 1);
    if (inputPage == KeyboardPage.SYMBOLS) letters();
    else refreshPredictions();
  }

  private void space() {
    voice.beforeEdit();
    if (composing.length() > 0) {
      final String code = composing.toString();
      if (app.pinyin != null) {
        List<PinyinEngine.Word> ws =
            app.pinyin.candidates(
                code, 1, privateMode() ? Collections.emptyMap() : app.store.frequencies());
        if (!ws.isEmpty()) {
          choose(ws.get(0));
          return;
        }
      }
      choose(code, code);
    } else literal(" ");
  }

  public void delete() {
    voice.beforeEdit();
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
      else {
        CharSequence before = ic.getTextBeforeCursor(1024, 0);
        if (before == null) {
          ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
          ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        } else if (before.length() > 0) ic.deleteSurroundingText(Graphemes.lastLength(before), 0);
      }
      refreshPredictions();
    }
  }

  private void enter() {
    voice.beforeEdit();
    if (composing.length() > 0) {
      commitComposing();
      typing();
      return;
    }
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
    // The largest supported translation is 16k code points (up to 32k UTF-16 units).
    // Keep a bounded complete snapshot large enough to restore it, including supplementary text.
    req.hintMaxChars = 65537;
    ExtractedText x = ic.getExtractedText(req, 0);
    if (x == null || x.startOffset != 0 || x.text == null || x.text.length() > 65536) return null;
    return x.text.toString();
  }

  public boolean practiceMode() {
    EditorInfo info = getCurrentInputEditorInfo();
    return getPackageName().equals(pkg) && info != null && "doudou:practice".equals(info.privateImeOptions);
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
    observeHistory();
    historyEdits.clear();
    trackedHistory = "";
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

  public boolean canUndoInsertion() {
    return !privateMode() && localEditor == null && edits.canUndo(full(), generation);
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
    if (composing.length() == 0) {
      main.removeCallbacks(selectionSuggestions);
      main.post(selectionSuggestions);
    }
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
    reset("error", KeyboardView.HEIGHT_DP);
    TextView heading = Ui.text(this, title, 17, Ui.ink());
    Ui.pad(this, heading, 16, 14);
    root.addView(heading);
    TextView t = Ui.text(this, detail, 15, Ui.sub());
    Ui.pad(this, t, 16, 16);
    root.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
    if (action != null) root.addView(Ui.primary(this, actionLabel, action));
    root.addView(Ui.button(this, "返回键盘", this::typing));
  }

  private void panelHeading(String title, Runnable back) {
    LinearLayout h = Ui.row(this);
    h.addView(
        Ui.icon(this, "返回", back), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    TextView label = Ui.text(this, title, 17, Ui.ink());
    h.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    label.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(h);
  }

  public void moveCursor(int delta) {
    voice.beforeEdit();
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

  public EditText editLocal(String title, String value, java.util.function.Consumer<String> callback) {
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
    return field;
  }

  private boolean snapshotUnchanged() {
    String current = full();
    int[] s = selection();
    return initialDraft != null && current != null && s != null && initialDraft.equals(current)
        && s[0] == initialStart && s[1] == initialEnd;
  }

  private boolean protectDraft(String value) {
      message("已保留新草稿", "", "", null);
      root.addView(
          Ui.button(
              this,
              "复制内容",
              () -> {
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("文字", value));
                Ui.toast(this, "已复制");
              }));
      return false;
  }

  public boolean protectedInsert(String value) {
    if (!snapshotUnchanged()) return protectDraft(value);
    return insert(value);
  }

  public boolean protectedReplace(String original, String value) {
    if (!snapshotUnchanged() || !original.equals(full())) return protectDraft(value);
    InputConnection ic = input();
    if (ic == null) return false;
    ic.beginBatchEdit();
    try {
      if (!ic.setSelection(0, original.length())) return protectDraft(value);
      return insert(value);
    } finally { ic.endBatchEdit(); }
  }

  public void trackHistory(String id) {
    if (privateMode()) return;
    trackedHistory = id;
    trackedInsertionSerial = insertionSerial;
    historyEdits.begin(insertionBefore, insertionStart, insertionEnd, insertionText, generation);
    app.doudou.ime.history.HistoryStore.Entry entry = app.histories.get(id);
    if (entry != null) activeConversation = entry.conversationRef;
    observeHistory();
  }

  public String conversationRef() {
    return activeConversation;
  }

  private void observeHistory() {
    if (app == null
        || privateMode()
        || localEditor != null
        || trackedHistory.isEmpty()
        || dictation.active()) return;
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

  public boolean inputViewVisible() {
    return isInputViewShown() && root != null && root.isShown();
  }

  public void showInputView() {
    requestShowSelf(0);
  }

  public LinearLayout openPanel(String next, int height) {
    reset(next, height);
    return root;
  }

  public void voiceFeedback(
      app.doudou.ime.voice.VoiceState state,
      String hint,
      boolean held,
      app.doudou.ime.voice.VoiceGesture.Target target) {
    if (keyboard != null && mode.equals("typing")) keyboard.voice(state, hint, held, target);
  }

  public void voiceLevel(float rmsDb) {
    if (keyboard != null) keyboard.voiceLevel(rmsDb);
  }

  public app.doudou.ime.voice.VoiceGesture.Target voiceTarget(float x, float y) {
    return keyboard == null ? VoiceGesture.Target.FINISH : keyboard.voiceTarget(x, y);
  }

  public void voiceRecovery(String hint, boolean hasText) {
    if (keyboard != null && mode.equals("typing")) keyboard.recovery(hint, hasText);
  }

  public void vibrate() {
    if (root != null) haptic(root);
  }

  public void cancelVoice() {
    voice.cancel();
  }

  public void cancelAdvisor() {
    advisorController.cancel();
  }

  public void togglePrivacy() {
    if (sensitive) {
      Ui.toast(this, "此输入框已自动开启隐私保护");
      return;
    }
    observeHistory();
    incognito = !incognito;
    historyEdits.clear();
    trackedHistory = "";
    activeConversation = "";
    edits.clear();
    typing();
  }

  public void showVoice() {
    voice.show();
  }

  public void selectAll() {
    voice.beforeEdit();
    commitComposing();
    InputConnection ic = input();
    if (ic != null && !ic.performContextMenuAction(android.R.id.selectAll))
      Ui.toast(this, "长按输入框选择文字");
  }

  public void editText() {
    commitComposing();
    typing();
    if (keyboard != null) keyboard.edit();
  }

  public boolean previewVoice(String text) {
    if (!dictation.active()) {
      String current = full();
      int[] selected = selection();
      if (current == null
          || selected == null
          || initialDraft == null
          || !initialDraft.equals(current)
          || selected[0] != initialStart
          || selected[1] != initialEnd) return false;
      if (!dictation.begin(input(), current, selected[0], selected[1])) return false;
    }
    return dictation.update(text);
  }

  public boolean finishVoice(String text) {
    dictation.restore();
    return protectedInsert(text);
  }

  public String retainVoice() {
    String value = dictation.release();
    if (value != null) {
      insertionBefore = initialDraft;
      insertionStart = Math.min(initialStart, initialEnd);
      insertionEnd = Math.max(initialStart, initialEnd);
      insertionText = value;
      insertionSerial++;
      edits.begin(insertionBefore, insertionStart, insertionEnd, value, generation);
    }
    return value;
  }

  public void restoreVoice() {
    dictation.restore();
  }

  private void startAdvisor(boolean supplement) {
    advisorController.start(supplement);
  }
}
