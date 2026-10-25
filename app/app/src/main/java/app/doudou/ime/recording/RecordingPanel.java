package app.doudou.ime.recording;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.*;

/** Recording stays in the keyboard footprint, with no text-editor side effects. */
public final class RecordingPanel {
  private final EditorHost host;
  private final Handler main = new Handler(Looper.getMainLooper());

  public RecordingPanel(EditorHost h) {
    host = h;
  }

  public void show() {
    Context c = host.context();
    if (host.privateMode()) return;
    if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      host.message("允许录音", "", "允许麦克风", () -> host.settings("settings"));
      return;
    }
    host.cancelVoice();
    host.cancelAdvisor();
    host.commitComposing();
    if (RecordingService.current == null)
      try {
        c.startForegroundService(new Intent(c, RecordingService.class));
      } catch (Exception e) {
        host.message(
            "暂时无法录音",
            "请打开录音笔重试",
            "打开录音笔",
            () ->
                c.startActivity(
                    new Intent(c, RecordingActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        return;
      }
    LinearLayout root = host.openPanel("recorder", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout heading = Ui.row(c);
    heading.addView(
        Ui.icon(c, "返回", host::typing), new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    TextView title = Ui.text(c, "录音笔", 17, Ui.ink());
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView clock = Ui.text(c, "00:00", 14, Ui.accent());
    heading.addView(clock);
    final String[] recordId = {""};
    TextView open =
        Ui.button(
            c,
            "查看录音",
            () -> {
              if (!recordId[0].isEmpty())
                c.startActivity(
                    new Intent(c, RecordingActivity.class)
                        .putExtra("id", recordId[0])
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
    open.setVisibility(View.GONE);
    heading.addView(open, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
    Ui.pad(c, heading, 8, 0);
    root.addView(heading);
    ScrollView scroll = new ScrollView(c);
    LinearLayout content = Ui.col(c);
    Ui.pad(c, content, 18, 12);
    scroll.addView(content);
    TextView text = Ui.text(c, "", 17, Ui.ink());
    text.setLineSpacing(Ui.dp(c, 5), 1);
    content.addView(text);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = Ui.row(c);
    Ui.pad(c, footer, 16, 6);
    RecordingWaveform waveform = new RecordingWaveform(c);
    footer.addView(waveform, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 36)));
    TextView status = Ui.text(c, "正在开始…", 13, Ui.sub());
    footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    TextView stop =
        Ui.primary(
            c,
            "结束",
            () -> {
              if (RecordingService.current != null) RecordingService.current.finish();
            });
    stop.setMinWidth(Ui.dp(c, 88));
    footer.addView(stop, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
    root.addView(footer);
    long generation = host.generation();
    String[] last = {""};
    Runnable tick =
        new Runnable() {
          public void run() {
            if (!root.isAttachedToWindow()
                || !host.mode().equals("recorder")
                || host.generation() != generation) return;
            RecordingService service = RecordingService.current;
            if (service != null) {
              recordId[0] = service.id();
              open.setVisibility(service.active() ? View.GONE : View.VISIBLE);
              waveform.level(service.active() ? service.level() : 0);
              String value = service.text();
              if (!value.equals(last[0])) {
                text.setText(value);
                last[0] = value;
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
              }
              long seconds = service.bytes() / 32000;
              clock.setText(
                  String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
              status.setText(service.status());
              if (!service.active()) {
                stop.setText("返回键盘");
                stop.setOnClickListener(v -> host.typing());
              }
            } else {
              waveform.level(0);
              open.setVisibility(recordId[0].isEmpty() ? View.GONE : View.VISIBLE);
              status.setText("已保存");
              stop.setText("返回键盘");
              stop.setOnClickListener(v -> host.typing());
            }
            main.postDelayed(this, 400);
          }
        };
    root.postDelayed(tick, 150);
  }
}
