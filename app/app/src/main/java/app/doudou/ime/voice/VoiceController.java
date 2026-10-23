package app.doudou.ime.voice;

import android.view.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.model.TextModelClient;

/** The space key owns dictation. Feedback stays in the candidate row and host editor. */
public final class VoiceController extends PanelController {
  private final VoiceInput voice;
  private final TextModelClient model;
  private boolean sessionActive, holding, stopping, working;
  private long serial;
  private float downX, downY;
  private VoiceState state = VoiceState.IDLE;
  private VoiceGesture.Target target = VoiceGesture.Target.FINISH;
  private String transcript = "", rawTranscript = "", historyId = "";

  public VoiceController(EditorHost host) {
    super(host);
    voice = new VoiceInput(this);
    model = new TextModelClient(this);
  }

  public boolean holding() { return holding; }
  public boolean active() { return sessionActive; }

  public void cancel() {
    serial++;
    sessionActive = holding = stopping = working = false;
    state = VoiceState.IDLE;
    voice.cancel(); model.cancel(); host.restoreVoice();
    transcript = rawTranscript = historyId = "";
  }

  /** Accessibility long-click uses the same space key, already in hands-free mode. */
  public void show() { begin(false, 0, 0); }
  public void hold(float x, float y) { begin(true, x, y); }

  private void begin(boolean held, float x, float y) {
    if (privateMode()) return;
    if (!app.cloud.loggedIn()) { message("登录后使用语音", "普通打字不需要登录。", "去登录", () -> settings("account")); return; }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      message("允许麦克风", "开启后，就能按住空格说话。", "允许麦克风", () -> settings("permissions")); return;
    }
    host.cancelAdvisor(); cancel(); commitComposing(); snapshot();
    host.typing();
    sessionActive = true; holding = held; downX = x; downY = y;
    target = VoiceGesture.Target.FINISH;
    present(VoiceState.CONNECTING, "");
    startVoice();
  }

  public boolean touch(MotionEvent e) {
    if (!holding) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_CANCEL || e.getPointerCount() > 1) { leave(); return true; }
    VoiceGesture.Target next = VoiceGesture.target(e.getRawX() - downX, e.getRawY() - downY, Ui.dp(this, 64));
    if (next != target) {
      target = next;
      host.vibrate();
      present(state, "");
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
      holding = false;
      if (target == VoiceGesture.Target.CANCEL) leave();
      else if (target == VoiceGesture.Target.LOCK) { target = VoiceGesture.Target.FINISH; present(state, ""); }
      else finishRecording();
    }
    return true;
  }

  private void present(VoiceState next, String message) {
    state = next;
    String hint = !message.isEmpty() ? message
        : next == VoiceState.FINISHING ? "正在完成识别…"
        : next == VoiceState.REWRITING ? "按你的口吻整理…"
        : !holding ? "免按说话 · 点空格完成"
        : target == VoiceGesture.Target.CANCEL ? "松手取消"
        : target == VoiceGesture.Target.LOCK ? "松手后免按说话"
        : "松手填入 · 上滑免按 · 左滑取消";
    host.voiceFeedback(state, hint, holding, target);
  }

  public void leave() { cancel(); typing(); }

  public void finishRecording() {
    if (!sessionActive || stopping) return;
    holding = false; stopping = working = true;
    present(VoiceState.FINISHING, ""); voice.stop();
  }

  public void insertTranscript() {
    if (working || transcript.trim().isEmpty()) return;
    String value = transcript.trim();
    voice.cancel(); model.cancel(); sessionActive = holding = false;
    if (host.finishVoice(value)) {
      if (app.prefs().getBoolean("history", true)) {
        if (historyId.isEmpty()) historyId = app.histories.create("voice", rawTranscript);
        app.histories.inserted(historyId, value, host.conversationRef()); host.trackHistory(historyId);
      }
      transcript = rawTranscript = historyId = "";
      typing();
    }
  }

  private void failed(String message) {
    sessionActive = holding = stopping = working = false;
    voice.cancel(); host.restoreVoice();
    host.voiceRecovery(message, !transcript.trim().isEmpty());
  }

  private void startVoice() {
    working = true; stopping = false;
    final long gen = host.generation(), attempt = serial;
    voice.start(new VoiceInput.Listener() {
      boolean active() { return sessionActive && attempt == serial && gen == host.generation() && host.mode().equals("typing"); }
      public void text(String value, boolean fin) {
        if (!active()) return;
        transcript = value;
        if (!value.isEmpty() && !host.previewVoice(value)) { failed("草稿已变化，识别文字已保留"); return; }
        if (fin) {
          holding = working = false; rawTranscript = value;
          if (!value.trim().isEmpty() && app.prefs().getBoolean("history", true)) historyId = app.histories.create("voice", value);
          if (value.trim().isEmpty()) { failed("没听清，再按住说一次"); return; }
          if (app.prefs().getBoolean("personalRewrite", false)) polishVoice(); else insertTranscript();
        }
      }
      public void level(float rmsDb) { if (active()) host.voiceLevel(rmsDb); }
      public void ready() { if (active() && !stopping) { working = false; present(VoiceState.RECORDING, ""); } }
      public void processing() { if (active()) { working = true; present(VoiceState.FINISHING, ""); } }
      public void status(String value) { }
      public void error(String value) { if (active()) failed(value); }
    }, host.conversationRef());
  }

  private void polishVoice() {
    final long gen = host.generation(), attempt = serial; final String original = transcript;
    working = true; present(VoiceState.REWRITING, "");
    model.polish(original, voice.sourceJob(), host.conversationRef(), (text, error) -> {
      if (!sessionActive || attempt != serial || gen != host.generation() || !host.mode().equals("typing")) return;
      working = false;
      transcript = error == null ? text : original;
      if (error != null) Ui.toast(this, "整理未完成，已保留原文");
      insertTranscript();
    });
  }
}
