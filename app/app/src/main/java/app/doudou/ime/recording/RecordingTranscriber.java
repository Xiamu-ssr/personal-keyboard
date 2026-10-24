package app.doudou.ime.recording;

import android.content.Context;
import androidx.work.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.history.HistoryStore;
import app.doudou.ime.voice.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * Explicit resumable transcription: receipts resolve ambiguous disconnects before spending again.
 */
public final class RecordingTranscriber extends Worker {
  private volatile AsrStream active;

  public RecordingTranscriber(Context c, WorkerParameters p) {
    super(c, p);
  }

  // A user-requested retry tests our API directly; OS internet validation can lag behind Wi-Fi.
  public static void enqueue(Context c, String id) {
    OneTimeWorkRequest work =
        new OneTimeWorkRequest.Builder(RecordingTranscriber.class)
            .setInputData(new Data.Builder().putString("id", id).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build();
    WorkManager.getInstance(c).enqueueUniqueWork("recording:" + id, ExistingWorkPolicy.KEEP, work);
  }

  @Override
  public Result doWork() {
    KeyboardApp app = KeyboardApp.instance;
    RecordingStore saved = null;
    try {
      String id = getInputData().getString("id");
      HistoryStore.Entry entry = app.histories.get(id);
      if (entry == null || entry.deleted) return Result.failure();
      RecordingStore store = new RecordingStore(getApplicationContext(), id);
      saved = store;
      if (!app.cloud.loggedIn() || !store.claimGuest() || store.snapshot().optBoolean("active"))
        return Result.failure();
      store.transcription("转写中");
      String account = app.cloud.userId();
      JSONArray chunks = store.snapshot().getJSONArray("chunks");
      for (int i = 0; i < chunks.length() && !isStopped(); i++) {
        JSONObject chunk = store.chunk(i);
        if ("done".equals(chunk.optString("state"))) continue;
        File file = store.audio(i);
        if (file.length() == 0) {
          store.text(i, "", true);
          continue;
        }
        JSONObject receipt =
            app.cloud.request(
                account, "/v1/asr/requests/" + chunk.getString("requestId"), "GET", null);
        String state = receipt.optString("status");
        if ("succeeded".equals(state)) {
          JSONObject result = receipt.optJSONObject("result");
          if (result != null && result.has("text")) {
            store.text(i, result.getString("text"), true);
            continue;
          }
          // The old job has settled but its short-lived result was purged. Keep the
          // audio and record an explicit new attempt rather than reusing its receipt.
          store.request(i, UUID.randomUUID().toString());
        }
        if ("queued".equals(state) || "running".equals(state)) return Result.retry();
        if ("failed".equals(state) || "cancelled".equals(state))
          store.request(i, UUID.randomUUID().toString());
        final int index = i;
        CountDownLatch ready = new CountDownLatch(1), done = new CountDownLatch(1);
        String[] error = {null};
        active = new AsrStream(getApplicationContext());
        active.start(
            new VoiceInput.Listener() {
              public void text(String text, boolean fin) {
                try {
                  store.text(index, text, fin);
                } catch (Exception e) {
                  error[0] = "LOCAL_WRITE";
                }
                if (fin) {
                  ready.countDown();
                  done.countDown();
                }
              }

              public void error(String code, String value) {
                try {
                  if ("NO_SPEECH".equals(code)) store.text(index, "", true);
                  else {
                    store.error(index, code);
                    error[0] = code;
                  }
                } catch (Exception e) {
                  error[0] = "LOCAL_WRITE";
                }
                ready.countDown();
                done.countDown();
              }

              public void ready() {
                ready.countDown();
              }

              public void status(String s) {}

              public void level(float v) {}

              public void processing() {}
            },
            "",
            store.chunk(i).getString("requestId"));
        if (!ready.await(15, TimeUnit.SECONDS)) {
          active.cancel();
          return Result.failure();
        }
        if (done.getCount() > 0)
          try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[3200];
            int n;
            while (!isStopped()
                && account.equals(app.cloud.userId())
                && done.getCount() > 0
                && (n = in.read(buffer)) != -1) {
              if (!active.feed(buffer, n)) break;
              Thread.sleep(100);
            }
            active.finish();
            if (!done.await(30, TimeUnit.SECONDS)) error[0] = "ASR_TIMEOUT";
          }
        active.cancel();
        active = null;
        if (error[0] != null || !account.equals(app.cloud.userId())) {
          boolean retry =
              account.equals(app.cloud.userId())
                  && getRunAttemptCount() < 3
                  && java.util.Arrays.asList(
                          "ASR_NETWORK",
                          "ASR_CLOSED",
                          "CONNECTION_FAILED",
                          "CONNECTION_TIMEOUT",
                          "ASR_TIMEOUT")
                      .contains(error[0]);
          store.transcription(retry ? "等待连接" : "转写已暂停");
          return retry ? Result.retry() : Result.failure();
        }
      }
      store.transcription(isStopped() ? "转写已暂停" : "转写完成");
      return isStopped() ? Result.failure() : Result.success();
    } catch (Exception e) {
      boolean transientFailure = e instanceof IOException;
      if (e instanceof app.doudou.ime.cloud.CloudClient.CloudError) {
        int status = ((app.doudou.ime.cloud.CloudClient.CloudError) e).status;
        transientFailure = status >= 500 || status == 429;
      }
      boolean retry = transientFailure && getRunAttemptCount() < 3;
      android.util.Log.w(
          "RecordingTranscriber", "Stored audio retained: " + e.getClass().getSimpleName());
      if (saved != null)
        try {
          saved.transcription(retry ? "等待连接" : "转写已暂停");
        } catch (Exception ignored) {
        }
      return retry ? Result.retry() : Result.failure();
    } finally {
      if (active != null) active.cancel();
    }
  }

  @Override
  public void onStopped() {
    if (active != null) active.cancel();
    super.onStopped();
  }
}
