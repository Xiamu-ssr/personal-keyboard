package app.doudou.ime.voice;

import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.model.TextModelClient;

/** Speak, release, insert. Composing previews can be cancelled without changing the prior draft. */
public final class VoiceController extends PanelController {
  private final VoiceInput voice;
  private final TextModelClient model;
  private TextView status, voiceText, keep;
  private VoiceButton microphone;
  private boolean recording, working, holding, stopping;
  private float downX, downY;
  private VoiceGesture.Target target = VoiceGesture.Target.FINISH;
  private String transcript = "", rawTranscript = "", historyId = "";

  public VoiceController(EditorHost host) {
    super(host);
    voice = new VoiceInput(this);
    model = new TextModelClient(this);
  }

  public boolean holding() { return holding; }

  public void cancel() {
    holding = recording = working = stopping = false;
    voice.cancel(); model.cancel(); host.restoreVoice();
    transcript = rawTranscript = historyId = "";
    if (microphone != null) microphone.setState(VoiceButton.State.IDLE);
  }

  public void show() { begin(false, 0, 0); }
  public void hold(float x, float y) { begin(true, x, y); }

  private void begin(boolean held, float x, float y) {
    if (privateMode()) { message("隐私输入", "此输入框不启用语音。", "", null); return; }
    if (!app.cloud.loggedIn()) { message("登录后使用语音", "普通打字不需要登录。", "去登录", () -> settings("account")); return; }
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      message("允许麦克风", "开启后，就能长按空格说话。", "允许麦克风", () -> settings("permissions")); return;
    }
    host.cancelAdvisor(); cancel(); commitComposing(); snapshot();
    holding = held; downX = x; downY = y; target = VoiceGesture.Target.FINISH;
    render(); startVoice();
  }

  public boolean touch(MotionEvent e) {
    if (!holding) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_CANCEL || e.getPointerCount() > 1) {
      leave(); return true;
    }
    VoiceGesture.Target next = VoiceGesture.target(e.getRawX() - downX, e.getRawY() - downY, Ui.dp(this, 64));
    if (next != target) {
      target = next;
      root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      showHint();
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
      holding = false;
      if (target == VoiceGesture.Target.CANCEL) leave();
      else if (target == VoiceGesture.Target.LOCK) { target = VoiceGesture.Target.FINISH; showHint(); }
      else finishRecording();
    }
    return true;
  }

  private void render() {
    reset("voice", 284); root.setBackgroundColor(Ui.KEYBOARD);
    LinearLayout bar = Ui.row(this);
    bar.addView(Ui.icon(this, "返回", this::leave), new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, "说话就好", 17, Ui.INK);
    bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    bar.addView(Ui.button(this, "取消", this::leave), new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 48)));
    root.addView(bar);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    voiceText = Ui.text(this, "", 19, Ui.INK);
    voiceText.setGravity(Gravity.CENTER);
    voiceText.setLineSpacing(Ui.dp(this, 4), 1);
    Ui.pad(this, voiceText, 24, 8);
    voiceText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    scroll.addView(voiceText);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    status = Ui.text(this, "", 13, Ui.SUB);
    status.setGravity(Gravity.CENTER); Ui.pad(this, status, 12, 6);
    status.setMinHeight(Ui.dp(this, 36));
    root.addView(status);
    LinearLayout actions = Ui.row(this); actions.setGravity(Gravity.CENTER);
    microphone = new VoiceButton(this, () -> { if (recording || working) finishRecording(); else begin(false, 0, 0); });
    actions.addView(microphone, new LinearLayout.LayoutParams(Ui.dp(this, 156), Ui.dp(this, 56)));
    root.addView(actions, new LinearLayout.LayoutParams(-1, Ui.dp(this, 64)));
    keep = Ui.button(this, "填入已有文字", this::insertTranscript);
    keep.setVisibility(View.GONE);
    root.addView(keep, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
    showHint();
  }

  private void showHint() {
    if (status == null) return;
    status.setTextColor(target == VoiceGesture.Target.CANCEL ? Ui.RED : Ui.SUB);
    status.setText(!holding ? "说完点停止，文字会自动填入" : target == VoiceGesture.Target.CANCEL ? "松手取消" : target == VoiceGesture.Target.LOCK ? "松手锁定，继续说话" : "松手填入 · 上滑锁定 · 左滑取消");
  }

  private void leave() { cancel(); typing(); }

  private void finishRecording() {
    if (stopping) return;
    holding = false; stopping = true; recording = false; working = true;
    microphone.setState(VoiceButton.State.TRANSCRIBING);
    status.setTextColor(Ui.SUB); status.setText("正在完成识别…"); voice.stop();
  }

  private void insertTranscript() {
    if (recording || working || transcript.trim().isEmpty()) return;
    String value = transcript.trim();
    voice.cancel(); model.cancel();
    if (host.finishVoice(value)) {
      if (app.prefs().getBoolean("history", true)) {
        if (historyId.isEmpty()) historyId = app.histories.create("voice", rawTranscript);
        app.histories.inserted(historyId, value, host.conversationRef()); host.trackHistory(historyId);
      }
      holding = false; transcript = rawTranscript = historyId = "";
      typing();
    }
  }

  private void startVoice() {
    working = true; stopping = false;
    microphone.setState(VoiceButton.State.CONNECTING);
    final long gen = host.generation();
    voice.start(new VoiceInput.Listener() {
      boolean active() { return gen == host.generation() && host.mode().equals("voice"); }
      public void text(String value, boolean fin) {
        if (!active()) return;
        transcript = value; voiceText.setText(value); host.previewVoice(value);
        if (fin) {
          holding = recording = working = false; rawTranscript = value;
          if (!value.trim().isEmpty() && app.prefs().getBoolean("history", true)) historyId = app.histories.create("voice", value);
          if (value.trim().isEmpty()) { host.restoreVoice(); microphone.setState(VoiceButton.State.IDLE); status.setText("没听清，再说一次"); return; }
          if (app.prefs().getBoolean("personalRewrite", false)) polishVoice(); else insertTranscript();
        }
      }
      public void level(float rmsDb) { if (active()) microphone.level(rmsDb); }
      public void ready() { if (active() && !stopping) { working = false; recording = true; microphone.setState(VoiceButton.State.LISTENING); showHint(); } }
      public void processing() { if (active()) { recording = false; working = true; microphone.setState(VoiceButton.State.TRANSCRIBING); status.setText("正在完成识别…"); } }
      public void status(String value) { /* Status belongs to the interaction, not transport implementation. */ }
      public void error(String value) {
        if (!active()) return;
        holding = recording = working = stopping = false; host.restoreVoice();
        microphone.setState(VoiceButton.State.IDLE);
        status.setTextColor(Ui.RED); status.setText(value);
        keep.setVisibility(transcript.trim().isEmpty() ? View.GONE : View.VISIBLE);
      }
    }, host.conversationRef());
  }

  private void polishVoice() {
    final long gen = host.generation(); final String original = transcript;
    working = true; microphone.setState(VoiceButton.State.REWRITING); status.setText("按你的口吻整理…");
    model.polish(original, voice.sourceJob(), host.conversationRef(), (text, error) -> {
      if (gen != host.generation() || !host.mode().equals("voice")) return;
      working = false;
      transcript = error == null ? text : original;
      if (error != null) Ui.toast(this, "整理未完成，已保留原文");
      insertTranscript();
    });
  }
}
