package app.doudou.ime.recording;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.Ui;
import app.doudou.ime.history.HistoryStore;
import java.io.*;
import org.json.*;

public final class RecordingActivity extends app.doudou.ime.design.ThemedActivity {
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean playing;
  private String id = "";

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    id = b == null ? getIntent().getStringExtra("id") : b.getString("recordingId", "");
    if (id == null) id = "";
    render();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    state.putString("recordingId", id);
    super.onSaveInstanceState(state);
  }

  private void render() {
    main.removeCallbacksAndMessages(null);
    KeyboardApp app = (KeyboardApp) getApplication();
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.bg());
    Ui.pad(this, root, 20, 12);
    LinearLayout heading = Ui.row(this);
    heading.addView(
        Ui.icon(this, "返回", this::finish),
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    heading.addView(Ui.text(this, "录音笔", 24, Ui.ink()), new LinearLayout.LayoutParams(0, -2, 1));
    if (!id.isEmpty()) {
      View options = Ui.icon(this, "更多", () -> {});
      options.setOnClickListener(v -> options(v));
      heading.addView(options, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    }
    root.addView(heading);
    if (id.isEmpty() && RecordingService.current != null) id = RecordingService.current.id();
    if (id.isEmpty()) {
      for (HistoryStore.Entry e : app.histories.list(500))
        if (e.type.equals("recording")) {
          root.addView(
              Ui.cell(
                  this,
                  "语音",
                  Ui.when(e.created),
                  "",
                  "",
                  () -> {
                    id = e.id;
                    render();
                  }));
        }
      root.addView(
          Ui.primary(
              this,
              "开始录音",
              () -> {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                  requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, 91);
                  return;
                }
                startForegroundService(new Intent(this, RecordingService.class));
                main.postDelayed(
                    () -> {
                      if (RecordingService.current != null) id = RecordingService.current.id();
                      render();
                    },
                    500);
              }));
      Ui.content(this, root);
      return;
    }
    HistoryStore.Entry entry = app.histories.get(id);
    if (entry == null
        || entry.deleted
        || (!entry.account.isEmpty() && !entry.account.equals(app.cloud.userId()))) {
      finish();
      return;
    }
    ScrollView scroll = new ScrollView(this);
    TextView transcript =
        Ui.text(this, entry.resultText.isEmpty() ? entry.asrText : entry.resultText, 18, Ui.ink());
    transcript.setTextIsSelectable(true);
    transcript.setLineSpacing(Ui.dp(this, 6), 1);
    Ui.pad(this, transcript, 0, 20);
    scroll.addView(transcript);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView state = Ui.text(this, "", 14, Ui.sub());
    root.addView(state);
    Ui.gap(this, root, 16);
    LinearLayout controls = Ui.row(this);
    TextView play = Ui.primary(this, "播放", () -> play(id));
    controls.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    TextView transcribe =
        Ui.button(
            this,
            "继续转写",
            () -> {
              if (!app.cloud.loggedIn()) {
                Ui.toast(this, "请先登录豆豆");
                return;
              }
              RecordingTranscriber.enqueue(this, id);
              Ui.toast(this, "已开始转写");
            });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
    lp.leftMargin = Ui.dp(this, 12);
    controls.addView(transcribe, lp);
    root.addView(controls);
    Ui.content(this, root);
    String recordId = id;
    Runnable tick =
        new Runnable() {
          public void run() {
            if (isFinishing() || !id.equals(recordId)) return;
            try {
              RecordingStore store = new RecordingStore(RecordingActivity.this, id);
              boolean active = store.snapshot().optBoolean("active");
              HistoryStore.Entry latest = app.histories.get(id);
              if (latest == null || latest.deleted) {
                finish();
                return;
              }
              transcript.setText(
                  active
                      ? store.text(true)
                      : latest.resultText.isEmpty() ? store.text(true) : latest.resultText);
              long seconds = store.size() / 32000;
              state.setText(
                  String.format(
                          java.util.Locale.ROOT,
                          "%02d:%02d · %.1f MB",
                          seconds / 60,
                          seconds % 60,
                          store.size() / 1048576.)
                      + (active ? "" : " · " + store.snapshot().optString("transcription", "已保存")));
              play.setText(active ? "结束" : playing ? "停止播放" : "播放");
              play.setOnClickListener(
                  v -> {
                    if (active) {
                      if (RecordingService.current != null) RecordingService.current.finish();
                    } else play(id);
                  });
              transcribe.setEnabled(!active);
            } catch (Exception e) {
              state.setText("录音文件暂不可读");
            }
            main.postDelayed(this, 800);
          }
        };
    main.post(tick);
  }

  private void play(String id) {
    if (playing) {
      playing = false;
      return;
    }
    playing = true;
    new Thread(
            () -> {
              AudioTrack track = null;
              try {
                RecordingStore store = new RecordingStore(this, id);
                JSONArray chunks = store.snapshot().getJSONArray("chunks");
                track =
                    new AudioTrack.Builder()
                        .setAudioAttributes(
                            new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(
                            new AudioFormat.Builder()
                                .setSampleRate(16000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build())
                        .setBufferSizeInBytes(16000)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                track.play();
                byte[] b = new byte[3200];
                for (int i = 0; i < chunks.length() && playing; i++)
                  try (InputStream in = new FileInputStream(store.audio(i))) {
                    int n;
                    while (playing && (n = in.read(b)) != -1) track.write(b, 0, n);
                  }
              } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, "无法播放这段录音"));
              } finally {
                if (track != null) {
                  try {
                    track.stop();
                  } catch (Exception ignored) {
                  }
                  track.release();
                }
                playing = false;
              }
            },
            "doudou-playback")
        .start();
  }

  private void options(View anchor) {
    KeyboardApp app = (KeyboardApp) getApplication();
    HistoryStore.Entry entry = app.histories.get(id);
    if (entry == null || entry.deleted) return;
    PopupMenu menu = new PopupMenu(this, anchor);
    if (!entry.resultText.isEmpty()) {
      menu.getMenu()
          .add("复制文字")
          .setOnMenuItemClickListener(
              i -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("录音转写", entry.resultText));
                Ui.toast(this, "已复制");
                return true;
              });
      if (RecordingService.current == null || !id.equals(RecordingService.current.id()))
        menu.getMenu()
            .add("编辑文字")
            .setOnMenuItemClickListener(
                i -> {
                  EditText field = Ui.edit(this, "文字", entry.resultText);
                  field.setMinLines(5);
                  new AlertDialog.Builder(this)
                      .setTitle("编辑文字")
                      .setView(field)
                      .setNegativeButton("取消", null)
                      .setPositiveButton(
                          "保存",
                          (d, w) -> {
                            String value = field.getText().toString().trim();
                            if (!value.isEmpty()) app.histories.observed(id, value);
                          })
                      .show();
                  return true;
                });
    }
    menu.getMenu()
        .add("删除录音")
        .setOnMenuItemClickListener(
            i -> {
              new AlertDialog.Builder(this)
                  .setTitle("删除这段录音？")
                  .setPositiveButton(
                      "删除",
                      (d, w) -> {
                        app.histories.remove(id);
                        finish();
                      })
                  .setNegativeButton("取消", null)
                  .show();
              return true;
            });
    menu.show();
  }

  @Override
  protected void onDestroy() {
    playing = false;
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
