package app.doudou.ime.recording;

import android.content.Context;
import android.util.AtomicFile;
import app.doudou.ime.app.KeyboardApp;
import java.io.*;
import java.util.*;
import org.json.*;

/** Durable 16 kHz mono PCM chunks. A failed model call cannot own or delete this audio. */
public final class RecordingStore {
  private static final Set<String> erased = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Context context;
  public final String id;
  private final File directory;
  private final AtomicFile manifest;
  private JSONObject data;

  public RecordingStore(Context c, String id) throws Exception {
    UUID.fromString(id);
    context = c.getApplicationContext();
    this.id = id;
    directory = new File(c.getFilesDir(), "recordings/" + id);
    directory.mkdirs();
    manifest = new AtomicFile(new File(directory, "session.json"));
    if (manifest.getBaseFile().exists())
      data = new JSONObject(new String(manifest.readFully(), "UTF-8"));
    else {
      data =
          new JSONObject()
              .put("id", id)
              .put("account", KeyboardApp.instance.cloud.userId())
              .put("started", System.currentTimeMillis())
              .put("active", true)
              .put("chunks", new JSONArray());
      save();
    }
  }

  private void save() throws Exception {
    if (erased.contains(id)) throw new IOException("录音已删除");
    FileOutputStream out = null;
    try {
      out = manifest.startWrite();
      out.write(data.toString().getBytes("UTF-8"));
      manifest.finishWrite(out);
    } catch (Exception e) {
      if (out != null) manifest.failWrite(out);
      throw e;
    }
  }

  public synchronized JSONObject snapshot() throws Exception {
    return new JSONObject(data.toString());
  }

  public synchronized int beginChunk() throws Exception {
    JSONArray chunks = data.getJSONArray("chunks");
    int index = chunks.length();
    chunks.put(
        new JSONObject()
            .put("file", index + ".pcm")
            .put("requestId", UUID.randomUUID().toString())
            .put("state", "pending")
            .put("text", "")
            .put("partial", "")
            .put("bytes", 0));
    save();
    return index;
  }

  public File audio(int index) {
    if (index < 0 || index > 10000) throw new IllegalArgumentException();
    return new File(directory, index + ".pcm");
  }

  public synchronized JSONObject chunk(int i) throws Exception {
    return new JSONObject(data.getJSONArray("chunks").getJSONObject(i).toString());
  }

  public synchronized void bytes(int i, long bytes) throws Exception {
    data.getJSONArray("chunks").getJSONObject(i).put("bytes", bytes);
    save();
  }

  public synchronized void text(int i, String text, boolean fin) throws Exception {
    JSONObject chunk = data.getJSONArray("chunks").getJSONObject(i);
    chunk.put(fin ? "text" : "partial", text);
    if (fin) chunk.put("state", "done").put("partial", "");
    save();
    if (fin) publish();
  }

  public synchronized void transcription(String status) throws Exception {
    data.put("transcription", status);
    save();
  }

  public synchronized void error(int i, String code) throws Exception {
    data.getJSONArray("chunks").getJSONObject(i).put("error", code);
    save();
  }

  public synchronized void request(int i, String requestId) throws Exception {
    data.getJSONArray("chunks").getJSONObject(i).put("requestId", requestId);
    save();
  }

  public synchronized String text(boolean partial) {
    StringBuilder text = new StringBuilder();
    JSONArray chunks = data.optJSONArray("chunks");
    for (int i = 0; i < chunks.length(); i++) {
      JSONObject ch = chunks.optJSONObject(i);
      String value = ch.optString("text");
      if (value.isEmpty() && partial) value = ch.optString("partial");
      if (!value.isEmpty()) {
        if (text.length() > 0) text.append('\n');
        text.append(value);
      }
    }
    return text.toString();
  }

  public synchronized void stopped(String reason) throws Exception {
    data.put("active", false).put("ended", System.currentTimeMillis()).put("reason", reason);
    save();
    publish();
  }

  public synchronized void publish() {
    KeyboardApp.instance.histories.recording(id, id, text(false));
  }

  public synchronized boolean accountMatches() {
    return data.optString("account").equals(KeyboardApp.instance.cloud.userId());
  }

  /** Guest recordings may be claimed once, together with their local history. */
  public synchronized boolean claimGuest() throws Exception {
    String account = KeyboardApp.instance.cloud.userId();
    if (data.optString("account").equals(account)) return true;
    app.doudou.ime.history.HistoryStore.Entry history = KeyboardApp.instance.histories.get(id);
    if (!data.optString("account").isEmpty()
        || account.isEmpty()
        || history == null
        || history.deleted
        || (!history.account.isEmpty() && !history.account.equals(account))) return false;
    data.put("account", account);
    save();
    return true;
  }

  public long size() {
    long total = 0;
    File[] files = directory.listFiles();
    if (files != null) for (File f : files) if (f.getName().endsWith(".pcm")) total += f.length();
    return total;
  }

  public static void recover(Context c) {
    File root = new File(c.getFilesDir(), "recordings");
    File[] folders = root.listFiles();
    if (folders == null) return;
    for (File f : folders)
      try {
        RecordingStore store = new RecordingStore(c, f.getName());
        if (store.snapshot().optBoolean("active")
            && (RecordingService.current == null
                || !RecordingService.current.id().equals(store.id))) store.stopped("录音已中断，音频已保留");
      } catch (Exception ignored) {
      }
  }

  public static void erase(Context c, String id) {
    UUID.fromString(id);
    erased.add(id);
    androidx.work.WorkManager.getInstance(c).cancelUniqueWork("recording:" + id);
    if (RecordingService.current != null && id.equals(RecordingService.current.id()))
      RecordingService.current.finish();
    File dir = new File(c.getFilesDir(), "recordings/" + id);
    File[] files = dir.listFiles();
    if (files != null) for (File f : files) f.delete();
    dir.delete();
  }
}
