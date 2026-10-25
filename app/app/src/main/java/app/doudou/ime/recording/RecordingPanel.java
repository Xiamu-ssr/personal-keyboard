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
  private int renderSerial;
  private String recordId = "", account = "";
  private long requestedAt;
  private final Handler main = new Handler(Looper.getMainLooper());

  public RecordingPanel(EditorHost h) {
    host = h;
  }

  /** Navigation never opens the microphone; Start is an explicit action. */
  public void enter() {
    show();
  }

  private void start() {
    if (host.privateMode()) return;
    Context c = host.context();
    if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      host.message("允许录音", "", "允许麦克风", () -> host.settings("settings"));
      return;
    }
    RecordingService current = RecordingService.current;
    if (current != null) {
      if (current.belongsToCurrentAccount() && !current.active()) current.startNextRecording();
      show(); return;
    }
    if (requestedAt > 0 && SystemClock.uptimeMillis() - requestedAt < 5000) return;
    try {
      requestedAt = SystemClock.uptimeMillis(); recordId = "";
      c.startForegroundService(new Intent(c, RecordingService.class));
      show();
    } catch (Exception e) {
      requestedAt = 0;
      host.message("暂时无法录音", "", "重试", this::start);
    }
  }

  public String text() {
    RecordingService service = RecordingService.current;
    if (service != null && service.belongsToCurrentAccount()) return service.text();
    if (recordId.isEmpty()) return "";
    try {
      RecordingStore store = new RecordingStore(host.context(), recordId);
      return store.accountMatches() ? store.text(true) : "";
    } catch (Exception e) { return ""; }
  }

  private String duration(long bytes) {
    long seconds = bytes / 32000;
    return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
  }

  private long savedBytes() {
    if (recordId.isEmpty()) return 0;
    try {
      RecordingStore store = new RecordingStore(host.context(), recordId);
      return store.accountMatches() ? store.size() : 0;
    } catch (Exception e) { return 0; }
  }

  public void show() {
    Context c = host.context();
    String currentAccount = host.services().cloud.userId();
    if (!currentAccount.equals(account)) { account = currentAccount; recordId = ""; requestedAt = 0; }
    if (host.privateMode()) return;
    if (RecordingService.current != null && !RecordingService.current.belongsToCurrentAccount()) {
      host.message("另一账号的录音仍在进行", "请先结束这段录音", "返回键盘", host::typing);
      return;
    }
    host.cancelVoice();
    host.commitComposing();
    final boolean starting = requestedAt > 0 && SystemClock.uptimeMillis() - requestedAt < 5000;
    LinearLayout root = host.openPanel("recorder", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    root.addView(host.workspaceBar(WorkspaceBar.Mode.RECORDING), new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
    LinearLayout heading = Ui.row(c);
    TextView clock = Ui.text(c, duration(savedBytes()), 14, Ui.accent());
    clock.setContentDescription("录音时长");
    heading.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
    TextView open =
        Ui.button(
            c,
            "查看录音",
            () -> {
              if (!recordId.isEmpty())
                c.startActivity(
                    new Intent(c, RecordingActivity.class)
                        .putExtra("id", recordId)
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
    TextView text = Ui.text(c, text(), 17, Ui.ink());
    text.setTextIsSelectable(true);
    text.setLineSpacing(Ui.dp(c, 5), 1);
    content.addView(text);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = Ui.row(c);
    Ui.pad(c, footer, 16, 6);
    RecordingWaveform waveform = new RecordingWaveform(c);
    footer.addView(waveform, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 36)));
    TextView status = Ui.text(c, starting ? "正在开始…" : "未开始", 13, Ui.sub());
    footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
    RecordingService initial = RecordingService.current;
    boolean active = initial != null && initial.active();
    TextView stop =
        Ui.primary(
            c,
            active ? "结束" : starting ? "正在开始…" : "开始录音",
            () -> {
              RecordingService current = RecordingService.current;
              if (current != null && current.belongsToCurrentAccount() && current.active()) current.finish();
              else start();
            });
    stop.setMinWidth(Ui.dp(c, 88));
    stop.setEnabled(active || !starting);
    footer.addView(stop, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
    root.addView(footer);
    long generation = host.generation();
    final int serial = ++renderSerial;
    String[] last = {""};
    boolean[] saved = {false};
    long started = android.os.SystemClock.uptimeMillis();
    Runnable tick =
        new Runnable() {
          public void run() {
            if (serial != renderSerial || !root.isAttachedToWindow()
                || !host.mode().equals("recorder")
                || host.generation() != generation) return;
            RecordingService service = RecordingService.current;
            if (service != null) {
              requestedAt = 0;
              saved[0] = false;
              recordId = service.id();
              open.setVisibility(service.active() ? View.GONE : View.VISIBLE);
              waveform.level(service.active() ? service.level() : 0);
              String value = service.text();
              if (!value.equals(last[0])) {
                text.setText(value);
                last[0] = value;
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
              }
              clock.setText(duration(service.bytes()));
              status.setText(service.status());
              if (!service.active()) {
                stop.setText(service.startingNext() ? "正在开始…" : "开始录音");
                stop.setEnabled(!service.startingNext());
              } else {
                stop.setText("结束");
                stop.setEnabled(true);
              }
            } else if (starting && recordId.isEmpty()
                && android.os.SystemClock.uptimeMillis() - started < 5000) {
              status.setText("正在开始…");
              stop.setEnabled(false);
            } else {
              if (!saved[0]) { clock.setText(duration(savedBytes())); saved[0] = true; }
              waveform.level(0);
              open.setVisibility(recordId.isEmpty() ? View.GONE : View.VISIBLE);
              status.setText(recordId.isEmpty() ? "未开始" : "已保存");
              String value = text();
              if (!value.equals(last[0])) { text.setText(value); last[0] = value; }
              stop.setText("开始录音");
              stop.setEnabled(true);
            }
            main.postDelayed(this, 400);
          }
        };
    root.postDelayed(tick, 150);
  }
}
