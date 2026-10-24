package app.doudou.ime.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.app.KeyboardApp;
import java.util.UUID;
import java.util.ArrayDeque;
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

    void error(String code, String value);

    void level(float rmsDb);

    void processing();

    void ready();
  }

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile int epoch;
  private volatile boolean recording, finished, stopRequested;
  private volatile AudioRecord recorder;
  private volatile WebSocket socket;
  private volatile Listener listener;
  private volatile String jobId = "";
  private final Object audioLock = new Object();
  private final ArrayDeque<byte[]> prebuffer = new ArrayDeque<>();
  private int bufferedBytes;
  private boolean transportReady, audioDone, finishSent;

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

  public void start(Listener next, String conversationRef) {
    cancel();
    listener = next;
    final int token = epoch;
    jobId = "";
    finished = false;
    stopRequested = false;
    synchronized (audioLock) {
      prebuffer.clear(); bufferedBytes = 0; transportReady = audioDone = finishSent = false;
    }
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      next.error("MICROPHONE_PERMISSION", "请先到豆豆设置允许麦克风");
      return;
    }
    KeyboardApp app = KeyboardApp.instance;
    if (!app.cloud.loggedIn()) {
      next.error("LOGIN_REQUIRED", "请先到设置登录豆豆账户");
      return;
    }
    final String account = app.cloud.userId();
    next.status("正在连接豆豆语音…");
    capture(token);
    main.postDelayed(() -> { if (token == epoch && !finished && !transportReady) fail(token, "CONNECTION_TIMEOUT", "连接较慢，请稍后重试"); }, 8000);
    app.network.execute(
        () -> {
          try {
            String access = app.cloud.accessToken(account);
            if (token != epoch || finished) return;
            JSONObject config =
                new JSONObject()
                    .put("type", "configure")
                    .put("requestId", UUID.randomUUID().toString())
                    .put("polish", app.prefs().getBoolean("asrPolish", true));
            config.put("context", app.histories.recent(conversationRef, 400));
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
                        if (token != epoch || !account.equals(app.cloud.userId())) {
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
                            synchronized (audioLock) {
                              socket = ws; transportReady = true;
                              while (!prebuffer.isEmpty()) ws.send(ByteString.of(prebuffer.removeFirst()));
                              bufferedBytes = 0;
                              finishWhenReady();
                            }
                          }
                          if (type.equals("partial")) {
                            String text = m.optString("text");
                            post(token, () -> listener.text(text, false));
                          }
                          if (type.equals("error")) {
                            JSONObject e = m.optJSONObject("error");
                            fail(token, e == null ? "ASR_FAILED" : e.optString("code", "ASR_FAILED"), e == null ? "语音连接失败" : e.optString("message"));
                          }
                          if (type.equals("job")) {
                            finished = true;
                            releaseRecorder();
                            JSONObject result = m.optJSONObject("result");
                            if (m.optString("status").equals("succeeded") && result != null) {
                              String text = result.getString("text");
                              jobId = m.getString("id");
                              post(token, () -> listener.text(text, true));
                              ws.close(1000, "Completed");
                            } else {
                              JSONObject e = m.optJSONObject("error");
                              fail(token, e == null ? "ASR_FAILED" : e.optString("code", "ASR_FAILED"), e == null ? "任务已结束，请重试" : e.optString("message"));
                            }
                          }
                        } catch (Exception e) {
                          fail(token, "MODEL_FORMAT", "语音返回格式不正确");
                        }
                      }

                      @Override
                      public void onFailure(WebSocket ws, Throwable error, Response response) {
                        if (!finished) fail(token, "ASR_NETWORK", "语音连接失败，请检查网络后重试");
                      }

                      @Override
                      public void onClosed(WebSocket ws, int code, String why) {
                        if (!finished) fail(token, "ASR_CLOSED", "语音连接已结束，可重试");
                      }
                    });
            if (token != epoch) created.cancel();
            else socket = created;
          } catch (Exception e) {
            fail(token, "CONNECTION_FAILED", e.getMessage() == null ? "连接失败" : e.getMessage());
          }
        });
  }

  @SuppressWarnings("MissingPermission")
  private void capture(int token) {
    if (token != epoch) return;
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
                if (token != epoch || finished || stopRequested) return;
                recorder = audio;
                audio.startRecording();
                post(token, () -> { listener.ready(); listener.status("正在听，说完点停止"); });
                byte[] buffer = new byte[3200];
                long started = System.currentTimeMillis();
                while (recording && !stopRequested
                    && token == epoch
                    && System.currentTimeMillis() - started < 90000) {
                  int n = audio.read(buffer, 0, buffer.length);
                  if (n <= 0) {
                    if (!recording) break;
                    throw new IllegalStateException();
                  }
                  synchronized (audioLock) {
                    if (token != epoch || finished) break;
                    if (transportReady && socket != null) {
                      if (socket.queueSize() > 128000 || !socket.send(ByteString.of(buffer, 0, n))) throw new java.io.IOException("网络较慢，请重试");
                    } else {
                      if (bufferedBytes + n > 128000) throw new java.io.IOException("网络较慢，请重试");
                      prebuffer.add(java.util.Arrays.copyOf(buffer, n)); bufferedBytes += n;
                    }
                  }
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
                  synchronized (audioLock) { audioDone = true; finishWhenReady(); }
                }
              } catch (Exception e) {
                if (token == epoch && !finished) {
                  fail(token, "CAPTURE_FAILED", e.getMessage() == null ? "录音失败，请检查麦克风" : e.getMessage());
                }
              } finally {
                if (token == epoch) {
                  recording = false;
                  synchronized (audioLock) { audioDone = true; finishWhenReady(); }
                }
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

  private void finishWhenReady() {
    if (transportReady && audioDone && !finishSent && !finished && socket != null) {
      finishSent = true; socket.send("{\"type\":\"finish\"}");
    }
  }

  private void fail(int token, String code, String value) {
    if (token != epoch) return;
    finished = true;
    releaseRecorder();
    synchronized (audioLock) { prebuffer.clear(); bufferedBytes = 0; }
    WebSocket current = socket;
    if (current != null) { current.send("{\"type\":\"cancel\"}"); current.close(1000, "Stopped"); }
    post(token, () -> listener.error(code, value));
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
    synchronized (audioLock) { socket = null; prebuffer.clear(); bufferedBytes = 0; transportReady = false; }
    if (ws != null) {
      ws.send("{\"type\":\"cancel\"}");
      ws.close(1000, "Cancelled");
    }
  }
}
