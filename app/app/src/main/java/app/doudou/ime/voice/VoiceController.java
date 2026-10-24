package app.doudou.ime.voice;

import android.view.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.model.TextModelClient;

/** Space starts capture; the dedicated dictation surface owns the held gesture. */
public final class VoiceController extends PanelController {
  private final VoiceInput voice;
  private final TextModelClient model;
  private boolean sessionActive, holding, stopping, working, newlineAfter, editing;
  private String retained;
  private long serial;
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

  public void render() {
    if (!editing) host.voiceFeedback(state, hint(), holding, target);
  }

  /** The first user edit owns the visible draft; ASR can still finish its raw history. */
  public void beforeEdit() {
    if (!sessionActive || !stopping || editing) return;
    editing = true;
    retained = host.retainVoice();
    host.voiceFeedback(VoiceState.IDLE, "", false, target);
  }

  public void cancel() {
    serial++;
    if (stopping) host.retainVoice(); else host.restoreVoice();
    sessionActive = holding = stopping = working = false;
    newlineAfter = editing = false; retained = null;
    state = VoiceState.IDLE;
    voice.cancel(); model.cancel();
    transcript = rawTranscript = historyId = "";
  }

  /** The tool card and accessibility action use this hands-free entry. */
  public void show() { begin(false); }
  public void hold() { begin(true); }

  private void begin(boolean held) {
    if (privateMode()) return;
    if (!app.cloud.loggedIn()) { message("登录后使用语音", "普通打字不需要登录。", "去登录", () -> settings("account")); return; }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      message("允许麦克风", "开启后，就能按住空格说话。", "允许麦克风", () -> settings("permissions")); return;
    }
    host.cancelAdvisor(); cancel(); commitComposing(); snapshot();
    host.typing();
    sessionActive = true; holding = held;
    target = VoiceGesture.Target.FINISH;
    present(VoiceState.CONNECTING, "");
    startVoice();
  }

  public boolean touch(MotionEvent e) {
    if (!holding) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_CANCEL || e.getPointerCount() > 1) { leave(); return true; }
    VoiceGesture.Target next = host.voiceTarget(e.getRawX(), e.getRawY());
    if (next != target) {
      target = next;
      host.vibrate();
      present(state, "");
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
      holding = false;
      if (target == VoiceGesture.Target.CANCEL) leave();
      else finishRecording(target == VoiceGesture.Target.NEWLINE);
    }
    return true;
  }

  private void present(VoiceState next, String message) {
    state = next;
    render();
  }

  private String hint() {
    return state == VoiceState.FINISHING ? "正在完成识别…"
        : state == VoiceState.REWRITING ? "按你的口吻整理…"
        : !holding ? "正在倾听"
        : target == VoiceGesture.Target.CANCEL ? "松手撤回"
        : target == VoiceGesture.Target.NEWLINE ? "松手换行"
        : "松手结束";
  }

  public void leave() { cancel(); typing(); }

  public void finishRecording(boolean newline) {
    if (!sessionActive || stopping) return;
    newlineAfter = newline;
    holding = false; stopping = working = true;
    if (newline && !editing) host.previewVoice(transcript.trim() + "\n");
    present(VoiceState.FINISHING, ""); voice.stop();
  }

  public void insertTranscript() {
    if (working || transcript.trim().isEmpty() && !newlineAfter) return;
    String value = transcript.trim() + (newlineAfter ? "\n" : "");
    voice.cancel(); model.cancel(); sessionActive = holding = false;
    if (editing || host.finishVoice(value)) {
      if (!rawTranscript.trim().isEmpty() && app.prefs().getBoolean("history", true)) {
        if (historyId.isEmpty()) historyId = app.histories.create("voice", rawTranscript);
        if (!editing || retained != null) {
          app.histories.inserted(historyId, editing ? retained : value, host.conversationRef());
          host.trackHistory(historyId);
        }
      }
      transcript = rawTranscript = historyId = "";
      typing();
    }
  }

  private void failed(String message) {
    boolean preserveEdit = editing;
    sessionActive = holding = stopping = working = false;
    voice.cancel(); host.restoreVoice();
    if (preserveEdit) { typing(); Ui.toast(this, "识别未完成，已保留你的修改"); }
    else host.voiceRecovery(message, !transcript.trim().isEmpty());
  }

  private void startVoice() {
    working = true; stopping = false;
    final long gen = host.generation(), attempt = serial;
    voice.start(new VoiceInput.Listener() {
      boolean active() { return sessionActive && attempt == serial && gen == host.generation() && host.mode().equals("typing"); }
      public void text(String value, boolean fin) {
        if (!active()) return;
        transcript = value;
        if (!editing && !value.isEmpty() && !host.previewVoice(value + (stopping && newlineAfter ? "\n" : ""))) {
          if (stopping) beforeEdit();
          else { failed("草稿已变化，识别文字已保留"); return; }
        }
        if (fin) {
          holding = working = false; rawTranscript = value;
          if (!value.trim().isEmpty() && app.prefs().getBoolean("history", true)) historyId = app.histories.create("voice", value);
          if (value.trim().isEmpty()) {
            if (newlineAfter) insertTranscript(); else failed("没听清，再按住说一次");
            return;
          }
          if (!editing && app.prefs().getBoolean("personalRewrite", false)) polishVoice(); else insertTranscript();
        }
      }
      public void level(float rmsDb) { if (active()) host.voiceLevel(rmsDb); }
      public void ready() { if (active() && !stopping) { working = false; present(VoiceState.RECORDING, ""); } }
      public void processing() { if (active()) { working = true; present(VoiceState.FINISHING, ""); } }
      public void status(String value) { }
      public void error(String code, String value) {
        if (!active()) return;
        // A newline is an explicit keyboard action even when no speech was detected.
        if (newlineAfter && code.equals("NO_SPEECH")) {
          working = false; transcript = rawTranscript = ""; insertTranscript();
        } else failed(value);
      }
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
