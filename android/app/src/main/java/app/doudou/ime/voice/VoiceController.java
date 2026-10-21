package app.doudou.ime.voice;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.model.TextModelClient;
import java.util.*;

/** Owns recording, transcription and optional text cleanup. */
public final class VoiceController extends PanelController {
  private final VoiceInput voice;
  private final TextModelClient model;
  private TextView status, voiceToggle;
  private EditText voiceText;
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
    panelHeading(
        "语音输入",
        () -> {
          voice.cancel();
          model.cancel();
          typing();
        });
    LinearLayout body = Ui.col(this);
    Ui.pad(this, body, 12, 0);
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    status = Ui.text(this, transcript.isEmpty() ? "说完后可编辑，或按个人文风整理" : "可编辑、整理，再填入", 12, Ui.SUB);
    Ui.pad(this, status, 2, 5);
    body.addView(status);
    voiceText = Ui.edit(this, "转写会显示在这里，也可以手动编辑", transcript);
    voiceText.setTextSize(17);
    voiceText.setShowSoftInputOnFocus(false);
    voiceText.setGravity(Gravity.TOP | Gravity.START);
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
                show();
              });
        });
    body.addView(voiceText, new LinearLayout.LayoutParams(-1, 0, 1));
    Ui.gap(this, body, 10);
    LinearLayout recordingRow = Ui.row(this);
    recordingRow.setGravity(Gravity.CENTER);
    voiceToggle =
        Ui.primary(
            this,
            "开始说话",
            () -> {
              if (recording) {
                recording = false;
                voice.stop();
                voiceToggle.setText("开始说话");
              } else startVoice();
            });
    recordingRow.addView(
        voiceToggle, new LinearLayout.LayoutParams(Ui.dp(this, 184), Ui.dp(this, 52)));
    body.addView(recordingRow);
    Ui.gap(this, body, 8);
    LinearLayout actions = Ui.row(this);
    TextView service = Ui.text(this, "系统语音服务", 11, Ui.SUB);
    actions.addView(service, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    service.setGravity(Gravity.CENTER_VERTICAL);
    actions.addView(
        Ui.button(this, "整理", this::polishVoice),
        new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 40)));
    Space gap = new Space(this);
    actions.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
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
        new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 40)));
    body.addView(actions);
  }

  private void startVoice() {
    recording = true;
    voiceToggle.setText("停止录音");
    final long gen = host.generation();
    voice.start(
        app.prefs().getBoolean("onlineVoice", false),
        new VoiceInput.Listener() {
          public void text(String value, boolean fin) {
            if (gen != host.generation() || !host.mode().equals("voice")) return;
            transcript = value;
            voiceText.setText(value);
            voiceText.setSelection(value.length());
            if (fin) {
              recording = false;
              voiceToggle.setText("开始说话");
              status.setText("转写完成 · 可编辑、整理或填入");
            }
          }

          public void status(String s) {
            if (gen == host.generation() && host.mode().equals("voice")) status.setText(s);
          }

          public void error(String s) {
            recording = false;
            if (gen == host.generation() && host.mode().equals("voice")) {
              voiceToggle.setText("开始说话");
              status.setText(s);
            }
          }
        });
  }

  private void polishVoice() {
    voice.stop();
    recording = false;
    voiceToggle.setText("开始说话");
    String value = voiceText.getText().toString().trim();
    if (value.isEmpty()) return;
    String original = value;
    final long gen = host.generation();
    if (!model.configured()) {
      status.setText("文字模型尚未配置；原始转写可直接填入");
      return;
    }
    status.setText("按热词和文风整理中…");
    model.polish(
        value,
        (text, error) -> {
          if (gen != host.generation() || !host.mode().equals("voice")) return;
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
}
