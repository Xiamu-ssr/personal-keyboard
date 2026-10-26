package app.doudou.ime.voice;

import android.content.Context;
import android.media.*;
import android.os.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.model.ExpressionContext;
import java.util.ArrayDeque;
import okhttp3.*;
import okio.ByteString;
import org.json.*;

/** A bounded PCM transport. It never owns the microphone or an editor. */
public final class AsrStream {

  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile int epoch;
  private volatile boolean finished;
  private volatile WebSocket socket;
  private volatile VoiceInput.Listener listener;
  private volatile String jobId = "";
  private final Object audioLock = new Object();
  private final ArrayDeque<byte[]> prebuffer = new ArrayDeque<>();
  private int bufferedBytes;
  private boolean transportReady, audioDone, finishSent;

  public AsrStream(Context c) {
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

  public void start(VoiceInput.Listener next, String conversationRef, String requestId, String editorContext) {
    cancel();
    listener = next;
    final int token = epoch;
    jobId = "";
    finished = false;
    synchronized (audioLock) {
      prebuffer.clear();
      bufferedBytes = 0;
      transportReady = audioDone = finishSent = false;
    }
    KeyboardApp app = KeyboardApp.instance;
    if (!app.cloud.loggedIn()) {
      next.error("LOGIN_REQUIRED", "请先到设置登录豆豆账户");
      return;
    }
    final String account = app.cloud.userId();
    next.status("正在连接豆豆语音…");
    main.postDelayed(
        () -> {
          if (token == epoch && !finished && !transportReady)
            fail(token, "CONNECTION_TIMEOUT", "连接较慢，请稍后重试");
        },
        8000);
    app.network.execute(
        () -> {
          try {
            String access = app.cloud.accessToken(account);
            if (token != epoch || finished) return;
            JSONObject config =
                new JSONObject()
                    .put("type", "configure")
                    .put("requestId", requestId)
                    .put("polish", app.prefs().getBoolean("asrPolish", true));
            config.put("context", ExpressionContext.recognition(app.histories.recent(conversationRef, 400), editorContext));
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
                              socket = ws;
                              transportReady = true;
                              while (!prebuffer.isEmpty())
                                ws.send(ByteString.of(prebuffer.removeFirst()));
                              bufferedBytes = 0;
                              finishWhenReady();
                              post(token, () -> listener.ready());
                            }
                          }
                          if (type.equals("partial")) {
                            String text = m.optString("text");
                            post(token, () -> listener.text(text, false));
                          }
                          if (type.equals("error")) {
                            JSONObject e = m.optJSONObject("error");
                            fail(
                                token,
                                e == null ? "ASR_FAILED" : e.optString("code", "ASR_FAILED"),
                                e == null ? "语音连接失败" : e.optString("message"));
                          }
                          if (type.equals("job")) {
                            finished = true;
                            JSONObject result = m.optJSONObject("result");
                            if (m.optString("status").equals("succeeded") && result != null) {
                              String text = result.getString("text");
                              jobId = m.getString("id");
                              post(token, () -> listener.text(text, true));
                              ws.close(1000, "Completed");
                            } else {
                              JSONObject e = m.optJSONObject("error");
                              fail(
                                  token,
                                  e == null ? "ASR_FAILED" : e.optString("code", "ASR_FAILED"),
                                  e == null ? "任务已结束，请重试" : e.optString("message"));
                            }
                          }
                        } catch (Exception e) {
                          fail(token, "MODEL_FORMAT", "语音返回格式不正确");
                        }
                      }

                      @Override
                      public void onFailure(WebSocket ws, Throwable error, Response response) {
                        if (!finished) {
                          android.util.Log.w(
                              "DoudouAudio",
                              "WebSocket "
                                  + error.getClass().getSimpleName()
                                  + " HTTP "
                                  + (response == null ? 0 : response.code()));
                          fail(token, "ASR_NETWORK", "语音连接失败，请检查网络后重试");
                        }
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

  public boolean feed(byte[] buffer, int n) {
    synchronized (audioLock) {
      if (finished || audioDone) return false;
      if (transportReady && socket != null) {
        if (socket.queueSize() > 128000 || !socket.send(ByteString.of(buffer, 0, n))) {
          fail(epoch, "ASR_NETWORK", "网络较慢，音频仍保存在本机");
          return false;
        }
      } else {
        if (bufferedBytes + n > 128000) {
          fail(epoch, "ASR_NETWORK", "连接尚未就绪");
          return false;
        }
        prebuffer.add(java.util.Arrays.copyOf(buffer, n));
        bufferedBytes += n;
      }
      return true;
    }
  }

  public void finish() {
    synchronized (audioLock) {
      audioDone = true;
      finishWhenReady();
    }
  }

  private void finishWhenReady() {
    if (transportReady && audioDone && !finishSent && !finished && socket != null) {
      finishSent = true;
      socket.send("{\"type\":\"finish\"}");
    }
  }

  private void fail(int token, String code, String value) {
    if (token != epoch) return;
    android.util.Log.w("DoudouAudio", "Stream stopped: " + code);
    finished = true;
    synchronized (audioLock) {
      prebuffer.clear();
      bufferedBytes = 0;
    }
    WebSocket current = socket;
    if (current != null) {
      current.send("{\"type\":\"cancel\"}");
      current.close(1000, "Stopped");
    }
    post(token, () -> listener.error(code, value));
  }

  public void cancel() {
    epoch++;
    listener = null;
    finished = true;
    WebSocket ws = socket;
    synchronized (audioLock) {
      socket = null;
      prebuffer.clear();
      bufferedBytes = 0;
      transportReady = false;
    }
    if (ws != null) {
      ws.send("{\"type\":\"cancel\"}");
      ws.close(1000, "Cancelled");
    }
  }
}
