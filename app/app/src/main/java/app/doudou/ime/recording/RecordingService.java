package app.doudou.ime.recording;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import app.doudou.ime.R;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.voice.*;
import java.io.*;
import java.util.*;

/** One local microphone, independent from the optional live ASR transports. */
public final class RecordingService extends Service {
  public static volatile RecordingService current;
  private volatile boolean recording, online = true;
  private volatile AudioRecord microphone;
  private volatile String status = "正在录音";
  private volatile float level;

  public float level() {
    return level;
  }

  private RecordingStore store;
  private final List<AsrStream> streams = Collections.synchronizedList(new ArrayList<>());
  private final Handler main = new Handler(Looper.getMainLooper());

  public String id() {
    return store == null ? "" : store.id;
  }

  public String status() {
    return status;
  }

  public boolean belongsToCurrentAccount() {
    return store != null && store.accountMatches();
  }

  public String text() {
    return store == null ? "" : store.text(true);
  }

  public long bytes() {
    return store == null ? 0 : store.size();
  }

  public void finish() {
    status = "正在保存";
    recording = false;
    AudioRecord r = microphone;
    if (r != null)
      try {
        r.stop();
      } catch (Exception ignored) {
      }
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "stop".equals(intent.getAction())) {
      finish();
      return START_NOT_STICKY;
    }
    if (current != null && current != this) return START_NOT_STICKY;
    if (recording || (current == this && store != null)) return START_NOT_STICKY;
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("recording", "录音笔", NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, RecordingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, RecordingService.class).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification =
        new Notification.Builder(this, "recording")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("豆豆 · 正在录音")
            .setContentText("音频正在保存到手机")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "结束", stop).build())
            .build();
    try {
      startForeground(811, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
      String id = KeyboardApp.instance.histories.create("recording", "");
      store = new RecordingStore(this, id);
      store.publish();
      current = this;
      recording = true;
      new Thread(this::capture, "doudou-recorder").start();
    } catch (Exception e) {
      status = "无法开始录音，请检查麦克风权限";
      app.doudou.ime.design.Ui.toast(this, status);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  private AsrStream stream(int index) {
    if (!online || !KeyboardApp.instance.cloud.loggedIn() || !store.accountMatches()) return null;
    AsrStream stream = new AsrStream(this);
    streams.add(stream);
    try {
      stream.start(
          new VoiceInput.Listener() {
            public void text(String text, boolean fin) {
              try {
                store.text(index, text, fin);
                if (fin) {
                  streams.remove(stream);
                  if (!recording && streams.isEmpty()) stopSelf();
                }
              } catch (Exception e) {
                status = "文字保存失败，音频仍保留";
              }
            }

            public void error(String code, String message) {
              try {
                if ("NO_SPEECH".equals(code)) store.text(index, "", true);
                else {
                  store.error(index, code);
                  online = false;
                  status = recording ? "录音中 · 转写已暂停" : "音频已保存 · 转写已暂停";
                }
              } catch (Exception ignored) {
              }
              streams.remove(stream);
            }

            public void status(String v) {}

            public void level(float v) {}

            public void processing() {}

            public void ready() {}
          },
          "",
          store.chunk(index).getString("requestId"));
      return stream;
    } catch (Exception e) {
      streams.remove(stream);
      return null;
    }
  }

  @SuppressWarnings("MissingPermission")
  private void capture() {
    AudioRecord audio = null;
    FileOutputStream file = null;
    AsrStream live = null;
    int index = -1;
    long chunkBytes = 0, lastFlush = 0;
    String reason = "已保存";
    try {
      int min =
          AudioRecord.getMinBufferSize(
              16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
      audio =
          new AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              16000,
              AudioFormat.CHANNEL_IN_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
              Math.max(6400, min));
      if (audio.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("麦克风不可用");
      microphone = audio;
      audio.startRecording();
      byte[] buffer = new byte[3200];
      index = store.beginChunk();
      file = new FileOutputStream(store.audio(index));
      live = stream(index);
      long total = 0;
      while (recording) {
        if (!store.accountMatches()) {
          reason = "账户已退出，录音已保存";
          break;
        }
        int n = audio.read(buffer, 0, buffer.length);
        if (n <= 0) {
          if (!recording) break;
          throw new IOException("麦克风已中断");
        }
        file.write(buffer, 0, n);
        chunkBytes += n;
        total += n;
        double square = 0;
        for (int p = 0; p + 1 < n; p += 2) {
          short sample = (short) ((buffer[p] & 255) | (buffer[p + 1] << 8));
          square += (double) sample * sample;
        }
        level = (float) Math.min(1, Math.sqrt(square / Math.max(1, n / 2)) / 7000.);
        if (live != null && !live.feed(buffer, n)) live = null;
        if (SystemClock.elapsedRealtime() - lastFlush > 1000) {
          file.flush();
          file.getFD().sync();
          store.bytes(index, chunkBytes);
          lastFlush = SystemClock.elapsedRealtime();
        }
        if (getFilesDir().getUsableSpace() < 16L * 1024 * 1024 || total >= 460800000L) {
          reason = "录音已保存，请腾出空间或开始下一段";
          break;
        }
        if (chunkBytes >= 45 * 32000) {
          file.getFD().sync();
          file.close();
          file = null;
          store.bytes(index, chunkBytes);
          if (live != null) live.finish();
          index = store.beginChunk();
          file = new FileOutputStream(store.audio(index));
          chunkBytes = 0;
          live = stream(index);
        }
      }
    } catch (Exception e) {
      reason = e.getMessage() == null ? "录音中断，已有音频保留" : e.getMessage();
    } finally {
      recording = false;
      if (file != null)
        try {
          file.getFD().sync();
          file.close();
          store.bytes(index, chunkBytes);
        } catch (Exception ignored) {
        }
      if (live != null) live.finish();
      if (audio != null) {
        try {
          audio.stop();
        } catch (Exception ignored) {
        }
        audio.release();
      }
      microphone = null;
      try {
        store.stopped(reason);
        if (store.size() == 0) KeyboardApp.instance.histories.remove(store.id);
      } catch (Exception ignored) {
      }
      status = reason;
      // Give final ASR up to 20 seconds; audio capture is already safely closed.
      main.postDelayed(
          () -> {
            synchronized (streams) {
              for (AsrStream s : streams) s.cancel();
              streams.clear();
            }
            stopSelf();
          },
          streams.isEmpty() ? 0 : 20000);
    }
  }

  public boolean active() {
    return recording;
  }

  @Override
  public void onDestroy() {
    finish();
    synchronized (streams) {
      for (AsrStream s : streams) s.cancel();
      streams.clear();
    }
    if (current == this) current = null;
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
