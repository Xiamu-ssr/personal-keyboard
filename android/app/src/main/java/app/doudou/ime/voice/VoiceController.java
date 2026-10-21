package app.doudou.ime.voice;

import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.model.TextModelClient;

/** Owns recording, transcription and optional text cleanup; never sends a chat message. */
public final class VoiceController extends PanelController {
  private final VoiceInput voice;
  private final TextModelClient model;
  private TextView status, voiceText, insertAction;
  private VoiceButton microphone;
  private boolean recording;
  private String transcript = "";

  public VoiceController(EditorHost host) {
    super(host);
    voice = new VoiceInput(this);
    model = new TextModelClient(this);
  }

  public void cancel() {
    voice.cancel();
    model.cancel();
    recording = false;
    transcript = "";
    if (microphone != null) microphone.setState(VoiceButton.State.IDLE);
  }

  public void show() {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用语音、读屏、历史与学习。", null);
      return;
    }
    host.cancelAdvisor();
    commitComposing();
    voice.cancel();
    model.cancel();
    recording = false;
    snapshot();
    reset("voice", 284);
    root.setBackgroundColor(Ui.KEYBOARD);
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.icon(this, "返回", this::leave),
        new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
    TextView brand = Ui.text(this, "豆豆", 18, Ui.INK);
    brand.setTypeface(null, Typeface.BOLD);
    bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView keyboard = Ui.button(this, "键盘", this::leave);
    keyboard.setMinHeight(Ui.dp(this, 34));
    LinearLayout.LayoutParams keyLp =
        new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 34));
    keyLp.rightMargin = Ui.dp(this, 12);
    bar.addView(keyboard, keyLp);
    root.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));

    LinearLayout center = Ui.col(this);
    center.setGravity(Gravity.CENTER);
    Ui.pad(this, center, 22, 0);
    root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));
    voiceText = Ui.text(this, transcript, 17, Ui.INK);
    voiceText.setGravity(Gravity.CENTER);
    voiceText.setMaxLines(2);
    voiceText.setEllipsize(android.text.TextUtils.TruncateAt.END);
    voiceText.setLineSpacing(Ui.dp(this, 3), 1);
    voiceText.setContentDescription("修正语音文字");
    voiceText.setOnClickListener(v -> editTranscript());
    center.addView(voiceText, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
    status = Ui.text(this, transcript.isEmpty() ? "点击说话" : "点文字可修正", 14, Ui.SUB);
    status.setGravity(Gravity.CENTER);
    status.setMaxLines(2);
    center.addView(status, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
    microphone =
        new VoiceButton(
            this,
            () -> {
              if (recording) {
                recording = false;
                microphone.setState(VoiceButton.State.PROCESSING);
                status.setText("正在识别…");
                voice.stop();
              } else startVoice();
            });
    center.addView(microphone, new LinearLayout.LayoutParams(Ui.dp(this, 156), Ui.dp(this, 58)));
    Ui.gap(this, center, 8);
    LinearLayout foot = Ui.row(this);
    Ui.pad(this, foot, 16, 4);
    foot.addView(
        round("整理", this::polishVoice),
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    foot.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
    insertAction = Ui.button(this, transcript.isEmpty() ? "换行" : "填入", this::insertTranscript);
    insertAction.setTextSize(18);
    insertAction.setBackground(Ui.pressable(this, Ui.SELECTED, 26));
    foot.addView(insertAction, new LinearLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 48)));
    foot.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
    foot.addView(
        round(
            "退格",
            () -> {
              if (recording) return;
              voice.cancel();
              model.cancel();
              microphone.setState(VoiceButton.State.IDLE);
              if (transcript.isEmpty()) host.delete();
              else {
                transcript =
                    transcript.substring(0, transcript.offsetByCodePoints(transcript.length(), -1));
                renderTranscript();
              }
            }),
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    root.addView(foot, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
  }

  private View round(String name, Runnable action) {
    View v = Ui.icon(this, name, action);
    v.setBackground(Ui.pressable(this, Ui.SPECIAL_KEY, 28));
    return v;
  }

  private void leave() {
    voice.cancel();
    model.cancel();
    recording = false;
    microphone.setState(VoiceButton.State.IDLE);
    typing();
  }

  private void editTranscript() {
    if (transcript.isEmpty()) return;
    voice.cancel();
    model.cancel();
    recording = false;
    microphone.setState(VoiceButton.State.IDLE);
    editLocal(
        "修正语音文字",
        transcript,
        edited -> {
          transcript = edited;
          show();
        });
  }

  private void renderTranscript() {
    voiceText.setText(transcript);
    insertAction.setText(transcript.isEmpty() ? "换行" : "填入");
  }

  private void insertTranscript() {
    if (recording) return;
    if (transcript.trim().isEmpty()) {
      host.insert("\n");
      return;
    }
    voice.cancel();
    model.cancel();
    if (protectedInsert(transcript.trim())) {
      history("语音输入", transcript.trim(), "voice");
      transcript = "";
      typing();
    }
  }

  private void startVoice() {
    model.cancel();
    recording = true;
    microphone.setState(VoiceButton.State.LISTENING);
    final long gen = host.generation();
    final String prefix = transcript.isEmpty() ? "" : transcript + " ";
    voice.start(
        app.prefs().getBoolean("onlineVoice", false),
        new VoiceInput.Listener() {
          boolean active() {
            return gen == host.generation() && host.mode().equals("voice");
          }

          public void text(String value, boolean fin) {
            if (!active()) return;
            transcript = prefix + value;
            renderTranscript();
            if (fin) {
              recording = false;
              microphone.setState(VoiceButton.State.IDLE);
              status.setText("说完了 · 可修正、整理或填入");
            }
          }

          public void level(float rmsDb) {
            if (active()) microphone.level(rmsDb);
          }

          public void processing() {
            if (active()) {
              recording = false;
              microphone.setState(VoiceButton.State.PROCESSING);
            }
          }

          public void status(String value) {
            if (active()) status.setText(value);
          }

          public void error(String value) {
            if (!active()) return;
            recording = false;
            microphone.setState(VoiceButton.State.IDLE);
            status.setText(value);
          }
        });
  }

  private void polishVoice() {
    voice.cancel();
    recording = false;
    microphone.setState(VoiceButton.State.IDLE);
    String original = transcript.trim();
    if (original.isEmpty()) {
      status.setText("先说一段话，再按个人文风整理");
      return;
    }
    if (!model.configured()) {
      status.setText("文字模型尚未配置；原始转写可直接填入");
      return;
    }
    final long gen = host.generation();
    microphone.setState(VoiceButton.State.PROCESSING);
    status.setText("按热词和文风整理中…");
    model.polish(
        original,
        (text, error) -> {
          if (gen != host.generation() || !host.mode().equals("voice")) return;
          microphone.setState(VoiceButton.State.IDLE);
          if (error != null) {
            status.setText(error);
            return;
          }
          if (!transcript.trim().equals(original)) {
            status.setText("保留你修改的文字，可重新整理");
            return;
          }
          transcript = text;
          renderTranscript();
          status.setText("整理完成 · 确认意思后填入");
        });
  }
}
