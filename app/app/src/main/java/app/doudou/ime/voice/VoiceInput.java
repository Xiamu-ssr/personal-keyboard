package app.doudou.ime.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import java.util.UUID;

/** Short dictation owns its microphone; recordings have their own durable capture service. */
public final class VoiceInput {
  public interface Listener {
    void text(String value, boolean fin);

    void status(String value);

    void error(String code, String value);

    void level(float rmsDb);

    void processing();

    void ready();
  }

  private final Context context;
  private final AsrStream stream;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile int epoch;
  private volatile boolean capturing;
  private volatile AudioRecord recorder;

  public VoiceInput(Context c) {
    context = c;
    stream = new AsrStream(c);
  }

  public String sourceJob() {
    return stream.sourceJob();
  }

  public void start(Listener next, String conversation, String editorContext) {
    cancel();
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      next.error("MICROPHONE_PERMISSION", "请允许麦克风");
      return;
    }
    if (!app.doudou.ime.app.KeyboardApp.instance.cloud.loggedIn()) {
      next.error("LOGIN_REQUIRED", "请先登录豆豆");
      return;
    }
    final int token = epoch;
    capturing = true;
    stream.start(
        new Listener() {
          public void text(String v, boolean fin) {
            if (token == epoch) {
              if (fin) stopCapture();
              next.text(v, fin);
            }
          }

          public void status(String v) {
            if (token == epoch) next.status(v);
          }

          public void error(String c, String v) {
            if (token == epoch) {
              stopCapture();
              next.error(c, v);
            }
          }

          public void level(float v) {}

          public void processing() {}

          public void ready() {}
        },
        conversation,
        UUID.randomUUID().toString(), editorContext);
    new Thread(() -> capture(token, next), "doudou-dictation").start();
  }

  @SuppressWarnings("MissingPermission")
  private void capture(int token, Listener listener) {
    AudioRecord audio = null;
    try {
      int minimum =
          AudioRecord.getMinBufferSize(
              16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
      audio =
          new AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              16000,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              Math.max(6400, minimum));
      if (audio.getState() != AudioRecord.STATE_INITIALIZED || !capturing || token != epoch) return;
      recorder = audio;
      audio.startRecording();
      main.post(
          () -> {
            if (token == epoch) {
              listener.ready();
              listener.status("正在听");
            }
          });
      byte[] buffer = new byte[3200];
      long started = System.currentTimeMillis();
      while (capturing && token == epoch && System.currentTimeMillis() - started < 90000) {
        int n = audio.read(buffer, 0, buffer.length);
        if (n <= 0) {
          if (!capturing) break;
          throw new java.io.IOException();
        }
        if (!stream.feed(buffer, n)) break;
        double sum = 0;
        for (int i = 0; i + 1 < n; i += 2) {
          short v = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
          sum += (double) v * v;
        }
        float level = (float) (20 * Math.log10(Math.sqrt(sum / Math.max(1, n / 2)) + 1) - 40);
        main.post(
            () -> {
              if (token == epoch) listener.level(level);
            });
      }
      if (token == epoch) {
        stream.finish();
        main.post(
            () -> {
              if (token == epoch) listener.processing();
            });
      }
    } catch (Exception e) {
      if (capturing && token == epoch)
        main.post(
            () -> {
              if (token == epoch) {
                stream.cancel();
                listener.error("CAPTURE_FAILED", "录音失败，请检查麦克风");
              }
            });
    } finally {
      if (audio != null) {
        try {
          audio.stop();
        } catch (Exception ignored) {
        }
        audio.release();
        if (recorder == audio) recorder = null;
      }
      if (token == epoch) capturing = false;
    }
  }

  private void stopCapture() {
    capturing = false;
    AudioRecord r = recorder;
    if (r != null)
      try {
        r.stop();
      } catch (Exception ignored) {
      }
  }

  public void stop() {
    stopCapture();
  }

  public void cancel() {
    epoch++;
    stopCapture();
    stream.cancel();
  }
}
