package app.doudou.ime.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.memory.MemoryStore;
import java.util.UUID;
import okhttp3.*;
import okio.ByteString;
import org.json.*;

/**
 * PCM capture only; ASR is proxied and metered by the product Cloud. No system recognizer fallback.
 */
public final class VoiceInput {
  public interface Listener {
    void text(String value, boolean fin);

    void status(String value);

    void error(String value);

    void level(float rmsDb);

    void processing();
  }

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile int epoch;
  private volatile boolean recording, finished, stopRequested;
  private volatile AudioRecord recorder;
  private volatile WebSocket socket;
  private volatile Listener listener;
  private volatile String jobId = "";

  public VoiceInput(Context c) {
    context = c;
  }

  public String sourceJob() {
    return jobId;
  }

  private void post(int token, Runnable r) {
    main.post(
        () -> {
          if (token == epoch && listener != null) r.run();
        });
  }

  public void start(Listener next) {
    cancel();
    listener = next;
    final int token = epoch;
    jobId = "";
    finished = false;
    stopRequested = false;
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      next.error("请先到豆豆设置允许麦克风");
      return;
    }
    KeyboardApp app = KeyboardApp.instance;
    if (!app.cloud.loggedIn()) {
      next.error("请先到设置登录豆豆账户");
      return;
    }
    next.status("正在连接豆豆语音…");
    app.network.execute(
        () -> {
          try {
            String access = app.cloud.accessToken();
            JSONObject config =
                new JSONObject()
                    .put("type", "configure")
                    .put("requestId", UUID.randomUUID().toString())
                    .put("polish", app.prefs().getBoolean("asrPolish", true));
            JSONArray words = new JSONArray();
            for (MemoryStore.Item i : app.store.list("word", 0)) {
              if (words.length() == 50) break;
              if (i.title.length() <= 60) words.put(i.title);
            }
            config.put("words", words);
            StringBuilder recent = new StringBuilder();
            for (MemoryStore.Item i : app.store.list("history", 0)) {
              if (!app.prefs().getBoolean("history", true)) break;
              if (!(i.source.startsWith("asr:") || i.source.equals("voice"))) continue;
              if (recent.length() >= 350) break;
              String s = i.body.substring(0, Math.min(i.body.length(), 250));
              recent.append(s).append('\n');
            }
            config.put("context", recent.substring(0, Math.min(400, recent.length())));
            Request request =
                new Request.Builder()
                    .url(BuildConfig.API_BASE_URL.replace("https:", "wss:") + "/v1/asr")
                    .header("Authorization", "Bearer " + access)
                    .build();
            WebSocket created =
                app.cloud.http.newWebSocket(
                    request,
                    new WebSocketListener() {
                      @Override
                      public void onOpen(WebSocket ws, Response response) {
                        if (token != epoch) {
                          ws.cancel();
                          return;
                        }
                        ws.send(config.toString());
                      }

                      @Override
                      public void onMessage(WebSocket ws, String data) {
                        if (token != epoch) return;
                        try {
                          JSONObject m = new JSONObject(data);
                          String type = m.optString("type");
                          if (type.equals("accepted")) jobId = m.getString("jobId");
                          if (type.equals("ready")) {
                            post(token, () -> listener.status("正在听，说完点停止"));
                            capture(ws, token);
                          }
                          if (type.equals("partial")) {
                            String text = m.optString("text");
                            post(token, () -> listener.text(text, false));
                          }
                          if (type.equals("error")) {
                            JSONObject e = m.optJSONObject("error");
                            fail(token, e == null ? "语音连接失败" : e.optString("message"));
                          }
                          if (type.equals("job")) {
                            finished = true;
                            releaseRecorder();
                            JSONObject result = m.optJSONObject("result");
                            if (m.optString("status").equals("succeeded") && result != null) {
                              String text = result.getString("text");
                              jobId = m.getString("id");
                              if (app.prefs().getBoolean("history", true))
                                app.store.save(
                                    0, 0, "history", 0, "语音转写", text, "transcript", "asr:" + jobId);
                              post(token, () -> listener.text(text, true));
                            } else {
                              JSONObject e = m.optJSONObject("error");
                              fail(token, e == null ? "任务已结束，请重试" : e.optString("message"));
                            }
                          }
                        } catch (Exception e) {
                          fail(token, "语音返回格式不正确");
                        }
                      }

                      @Override
                      public void onFailure(WebSocket ws, Throwable error, Response response) {
                        if (!finished) fail(token, "语音连接失败，请检查网络后重试");
                      }

                      @Override
                      public void onClosed(WebSocket ws, int code, String why) {
                        if (!finished) fail(token, "语音连接已结束，可重试");
                      }
                    });
            if (token != epoch) created.cancel();
            else socket = created;
          } catch (Exception e) {
            fail(token, e.getMessage() == null ? "连接失败" : e.getMessage());
          }
        });
  }

  @SuppressWarnings("MissingPermission")
  private void capture(WebSocket ws, int token) {
    if (token != epoch) return;
    if (stopRequested) {
      ws.send("{\"type\":\"cancel\"}");
      fail(token, "已停止，本次未开始录音");
      return;
    }
    recording = true;
    new Thread(
            () -> {
              AudioRecord audio = null;
              try {
                int min =
                    AudioRecord.getMinBufferSize(
                        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) throw new IllegalStateException();
                audio =
                    new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min, 6400));
                if (audio.getState() != AudioRecord.STATE_INITIALIZED)
                  throw new IllegalStateException();
                if (token != epoch) return;
                recorder = audio;
                audio.startRecording();
                byte[] buffer = new byte[3200];
                long started = System.currentTimeMillis();
                while (recording
                    && token == epoch
                    && System.currentTimeMillis() - started < 90000) {
                  int n = audio.read(buffer, 0, buffer.length);
                  if (n <= 0) {
                    if (!recording) break;
                    throw new IllegalStateException();
                  }
                  if (ws.queueSize() > 128000) throw new java.io.IOException("网络较慢，请缩短语音");
                  if (!ws.send(ByteString.of(buffer, 0, n))) throw new java.io.IOException("连接中断");
                  double sum = 0;
                  for (int i = 0; i + 1 < n; i += 2) {
                    short sample = (short) ((buffer[i] & 255) | (buffer[i + 1] << 8));
                    sum += (double) sample * sample;
                  }
                  float level =
                      (float) (20 * Math.log10(Math.sqrt(sum / Math.max(1, n / 2)) + 1) - 40);
                  post(token, () -> listener.level(level));
                }
                if (token == epoch && !finished) {
                  post(
                      token,
                      () -> {
                        listener.status("正在完成识别…");
                        listener.processing();
                      });
                  ws.send("{\"type\":\"finish\"}");
                }
              } catch (Exception e) {
                if (token == epoch && !finished) {
                  ws.send("{\"type\":\"cancel\"}");
                  fail(token, e.getMessage() == null ? "录音失败，请检查麦克风" : e.getMessage());
                }
              } finally {
                if (token == epoch) recording = false;
                if (audio != null) {
                  try {
                    audio.stop();
                  } catch (Exception ignored) {
                  }
                  audio.release();
                  if (recorder == audio) recorder = null;
                }
              }
            },
            "doudou-audio")
        .start();
  }

  private void fail(int token, String value) {
    if (token != epoch) return;
    finished = true;
    releaseRecorder();
    post(token, () -> listener.error(value));
  }

  private void releaseRecorder() {
    recording = false;
    AudioRecord r = recorder;
    if (r != null)
      try {
        r.stop();
      } catch (Exception ignored) {
      }
  }

  public void stop() {
    stopRequested = true;
    releaseRecorder();
  }

  public void cancel() {
    epoch++;
    listener = null;
    finished = true;
    releaseRecorder();
    WebSocket ws = socket;
    socket = null;
    if (ws != null) {
      ws.send("{\"type\":\"cancel\"}");
      ws.close(1000, "Cancelled");
    }
  }
}
