package app.doudou.ime.history;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Bitmap;
import app.doudou.ime.app.KeyboardApp;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local evidence. A result is revised in place; there is no keystroke snapshot table. */
public final class HistoryStore extends SQLiteOpenHelper {
  private final Context context;

  public static final class Entry {
    public String id, type, asrText, insertedText, resultText, conversationRef, account;
    public JSONArray messages, screenshots, replyGroups;
    public JSONObject identity;
    public String audioRef;
    public int revision, uploaded;
    public boolean deleted, practice;
    public long created, updated;

    public JSONObject increment() throws JSONException {
      return new JSONObject()
          .put("id", id)
          .put("revision", revision)
          .put("type", type)
          .put("asrText", asrText)
          .put("insertedText", insertedText)
          .put("resultText", resultText)
          .put("conversationRef", conversationRef)
          .put("messages", messages)
          .put("identity", identity)
          .put("practice", practice)
          .put("deleted", deleted);
    }

    public JSONObject json() throws JSONException {
      return increment()
          .put("screenshots", screenshots)
          .put("replyGroups", replyGroups)
          .put("audioRef", audioRef)
          .put("account", account)
          .put("created", created)
          .put("updated", updated);
    }
  }

  public HistoryStore(Context c) {
    this(c, "history.db");
  }

  public HistoryStore(Context c, String name) {
    super(c, name, null, 3);
    context = c.getApplicationContext();
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE history(id TEXT PRIMARY KEY,type TEXT NOT NULL,asr_text TEXT NOT NULL DEFAULT"
            + " '',inserted_text TEXT NOT NULL DEFAULT '',result_text TEXT NOT NULL DEFAULT"
            + " '',conversation_ref TEXT NOT NULL DEFAULT '',messages TEXT NOT NULL DEFAULT"
            + " '[]',screenshots TEXT NOT NULL DEFAULT '[]',revision INTEGER NOT NULL DEFAULT"
            + " 1,uploaded INTEGER NOT NULL DEFAULT 0,account TEXT NOT NULL DEFAULT '',created"
            + " INTEGER NOT NULL,updated INTEGER NOT NULL,deleted INTEGER NOT NULL DEFAULT 0)");
    d.execSQL("ALTER TABLE history ADD COLUMN identity TEXT NOT NULL DEFAULT '{}'");
    d.execSQL("ALTER TABLE history ADD COLUMN reply_groups TEXT NOT NULL DEFAULT '[]'");
    d.execSQL("ALTER TABLE history ADD COLUMN audio_ref TEXT NOT NULL DEFAULT ''");
    d.execSQL("ALTER TABLE history ADD COLUMN practice INTEGER NOT NULL DEFAULT 0");
    d.execSQL("CREATE INDEX history_pending ON history(account,uploaded,revision)");
    d.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    if (old < 2) {
      d.execSQL("ALTER TABLE history ADD COLUMN identity TEXT NOT NULL DEFAULT '{}'");
      d.execSQL("ALTER TABLE history ADD COLUMN reply_groups TEXT NOT NULL DEFAULT '[]'");
      d.execSQL("ALTER TABLE history ADD COLUMN audio_ref TEXT NOT NULL DEFAULT ''");
      d.execSQL("UPDATE history SET revision=revision+1 WHERE deleted=0");
      // Re-submit retained evidence through the new automatic-learning policy. Remove only
      // failed/empty historical attempts. Existing successful evidence remains.
      d.execSQL(
          "UPDATE history SET deleted=1,revision=revision+1 WHERE type='advisor' AND messages='[]'"
              + " AND inserted_text='' AND result_text=''");
    }
    if (old < 3) d.execSQL("ALTER TABLE history ADD COLUMN practice INTEGER NOT NULL DEFAULT 0");
  }

  private Entry read(Cursor c) {
    Entry e = new Entry();
    e.id = c.getString(0);
    e.type = c.getString(1);
    e.asrText = c.getString(2);
    e.insertedText = c.getString(3);
    e.resultText = c.getString(4);
    e.conversationRef = c.getString(5);
    try {
      e.messages = new JSONArray(c.getString(6));
      e.screenshots = new JSONArray(c.getString(7));
      e.identity = new JSONObject(c.getString(14));
      e.replyGroups = new JSONArray(c.getString(15));
      e.audioRef = c.getString(16);
    } catch (JSONException ex) {
      throw new IllegalStateException(ex);
    }
    e.revision = c.getInt(8);
    e.uploaded = c.getInt(9);
    e.account = c.getString(10);
    e.created = c.getLong(11);
    e.updated = c.getLong(12);
    e.deleted = c.getInt(13) != 0;
    e.practice = c.getInt(17) != 0;
    return e;
  }

  public synchronized Entry get(String id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM history WHERE id=?", new String[] {id})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public synchronized String create(String type, String asr) {
    return create(type, asr, false);
  }

  public synchronized String create(String type, String asr, boolean practice) {
    if (!type.equals("voice") && !type.equals("advisor") && !type.equals("recording"))
      throw new IllegalArgumentException("未知历史类型");
    String id = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    ContentValues v = new ContentValues();
    v.put("id", id);
    v.put("type", type);
    v.put("asr_text", asr);
    v.put("practice", practice ? 1 : 0);
    v.put("created", now);
    v.put("updated", now);
    v.put("account", KeyboardApp.instance == null ? "" : KeyboardApp.instance.cloud.userId());
    getWritableDatabase().insertOrThrow("history", null, v);
    changed();
    return id;
  }

  private void update(String id, ContentValues values) {
    Entry old = get(id);
    if (old == null || old.deleted) return;
    values.put("revision", old.revision + 1);
    values.put("updated", Math.max(System.currentTimeMillis(), old.updated + 1));
    getWritableDatabase().update("history", values, "id=?", new String[] {id});
    changed();
  }

  public synchronized void transcript(String id, String value) {
    ContentValues v = new ContentValues();
    v.put("asr_text", value);
    update(id, v);
  }

  public synchronized void inserted(String id, String value, String conversation) {
    ContentValues v = new ContentValues();
    v.put("inserted_text", value);
    v.put("result_text", value);
    v.put("conversation_ref", conversation);
    update(id, v);
  }

  public synchronized void withdraw(String id) {
    ContentValues v = new ContentValues();
    v.put("inserted_text", "");
    v.put("result_text", "");
    update(id, v);
  }

  public synchronized void observed(String id, String value) {
    Entry old = get(id);
    if (old == null
        || old.deleted
        || value == null
        || value.isEmpty()
        || value.equals(old.resultText)) return;
    ContentValues v = new ContentValues();
    v.put("result_text", value);
    update(id, v);
  }

  public synchronized void advisorResult(String id, JSONObject result) throws Exception {
    Entry e = get(id);
    if (e == null || e.deleted) return;
    JSONArray groups = e.replyGroups;
    groups.put(
        new JSONObject()
            .put("created", System.currentTimeMillis())
            .put("replies", result.getJSONArray("replies")));
    ContentValues v = new ContentValues();
    v.put("reply_groups", groups.toString());
    v.put("messages", result.getJSONArray("messages").toString());
    v.put(
        "identity",
        result.optJSONObject("identity") == null
            ? "{}"
            : result.getJSONObject("identity").toString());
    update(id, v);
  }

  /** A successful answer and its screenshots become one visible history atomically. */
  public synchronized String saveAdvisor(
      String existing, JSONObject result, List<String> images, String account) throws Exception {
    return saveAdvisor(existing, result, images, account, false);
  }

  public synchronized String saveAdvisor(
      String existing, JSONObject result, List<String> images, String account, boolean practice) throws Exception {
    if (!existing.isEmpty()) {
      Entry entry = get(existing);
      if (entry == null || entry.deleted || !entry.account.equals(account))
        throw new IOException("历史已变化");
      advisorResult(existing, result);
      return existing;
    }
    JSONArray refs = new JSONArray();
    List<File> created = new ArrayList<>();
    boolean committed = false;
    try {
      for (String image : images) {
        byte[] bytes =
            android.util.Base64.decode(
                image.substring(image.indexOf(',') + 1), android.util.Base64.DEFAULT);
        if (bytes.length < 2
            || bytes.length > 600000
            || bytes[0] != (byte) 255
            || bytes[1] != (byte) 216) throw new IOException("截图格式错误");
        String ref = UUID.randomUUID() + ".jpg";
        File file = imageFile(ref);
        created.add(file);
        try (FileOutputStream out = new FileOutputStream(file)) {
          out.write(bytes);
          out.getFD().sync();
        }
        refs.put(ref);
      }
      String id = UUID.randomUUID().toString();
      long now = System.currentTimeMillis();
      ContentValues row = new ContentValues();
      row.put("id", id);
      row.put("type", "advisor");
      row.put("account", account);
      row.put("practice", practice ? 1 : 0);
      row.put("created", now);
      row.put("updated", now);
      row.put("screenshots", refs.toString());
      row.put("messages", result.getJSONArray("messages").toString());
      row.put(
          "identity",
          result.optJSONObject("identity") == null
              ? "{}"
              : result.getJSONObject("identity").toString());
      row.put(
          "reply_groups",
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("created", now)
                      .put("replies", result.getJSONArray("replies")))
              .toString());
      getWritableDatabase().insertOrThrow("history", null, row);
      committed = true;
      changed();
      return id;
    } finally {
      if (!committed) for (File file : created) file.delete();
    }
  }

  public synchronized void collectImages() {
    Set<String> retained = new HashSet<>();
    try (Cursor cursor =
        getReadableDatabase().rawQuery("SELECT screenshots FROM history WHERE deleted=0", null)) {
      while (cursor.moveToNext())
        try {
          JSONArray images = new JSONArray(cursor.getString(0));
          for (int i = 0; i < images.length(); i++) retained.add(images.getString(i));
        } catch (JSONException ignored) {
        }
    }
    File directory = new File(context.getFilesDir(), "history-screenshots");
    File[] files = directory.listFiles();
    if (files != null)
      for (File file : files)
        if (file.getName().matches("[a-f0-9-]{36}\\.jpg") && !retained.contains(file.getName()))
          file.delete();
  }

  public synchronized void recording(String id, String audioRef, String text) {
    ContentValues v = new ContentValues();
    v.put("audio_ref", audioRef);
    v.put("asr_text", text);
    Entry e = get(id);
    if (e != null && (e.resultText.isEmpty() || e.resultText.equals(e.insertedText))) {
      v.put("result_text", text);
      v.put("inserted_text", text);
    }
    update(id, v);
  }

  public synchronized void interpreted(String id, JSONArray messages) {
    ContentValues v = new ContentValues();
    v.put("messages", messages.toString());
    update(id, v);
  }

  public synchronized String screenshot(String id, Bitmap image, boolean earlier) throws Exception {
    Entry e = get(id);
    if (e == null) throw new IOException("历史不存在");
    String ref = UUID.randomUUID().toString() + ".jpg";
    try (FileOutputStream out = new FileOutputStream(imageFile(ref))) {
      if (!image.compress(Bitmap.CompressFormat.JPEG, 82, out)) throw new IOException("截图保存失败");
    }
    JSONArray refs = new JSONArray();
    if (earlier) refs.put(ref);
    for (int i = 0; i < e.screenshots.length(); i++) refs.put(e.screenshots.get(i));
    if (!earlier) refs.put(ref);
    ContentValues v = new ContentValues();
    v.put("screenshots", refs.toString());
    if (earlier) {
      for (int i = 0; i < e.messages.length(); i++) {
        JSONObject message = e.messages.getJSONObject(i);
        if (message.has("image")) message.put("image", message.getInt("image") + 1);
      }
      v.put("messages", e.messages.toString());
    }
    update(id, v);
    return ref;
  }

  public File imageFile(String ref) {
    if (!ref.matches("[a-f0-9-]{36}\\.jpg")) throw new IllegalArgumentException("截图引用错误");
    File dir = new File(context.getFilesDir(), "history-screenshots");
    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("截图目录不可用");
    return new File(dir, ref);
  }

  public synchronized List<Entry> list(int limit) {
    return entries(
        limit, KeyboardApp.instance == null ? "" : KeyboardApp.instance.cloud.userId(), false);
  }

  public synchronized List<Entry> reusable(int limit) {
    return entries(
        limit, KeyboardApp.instance == null ? "" : KeyboardApp.instance.cloud.userId(), true);
  }

  private List<Entry> entries(int limit, String account, boolean adoptedOnly) {
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM history WHERE deleted=0 AND (account=? OR account='')"
                    + (adoptedOnly
                        ? " AND inserted_text<>'' AND result_text<>''"
                        : " AND (asr_text<>'' OR result_text<>'' OR reply_groups<>'[]' OR"
                            + " audio_ref<>'')")
                    + " ORDER BY updated DESC LIMIT ?",
                new String[] {account, "" + limit})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }

  public synchronized List<Entry> pending(String account) {
    if (account.isEmpty()) return Collections.emptyList();
    // Guest histories are bound once; another signed-in account never receives them.
    ContentValues bind = new ContentValues();
    bind.put("account", account);
    getWritableDatabase().update("history", bind, "account=''", null);
    List<Entry> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM history WHERE account=? AND practice=0 AND uploaded<revision AND (deleted=1 OR"
                    + " asr_text!='' OR result_text!='' OR messages!='[]') ORDER BY updated LIMIT"
                    + " 12",
                new String[] {account})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }

  public synchronized void accepted(String account, List<Entry> batch) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      for (Entry e : batch)
        d.execSQL(
            "UPDATE history SET uploaded=MAX(uploaded,?) WHERE id=? AND account=? AND revision>=?",
            new Object[] {e.revision, e.id, account, e.revision});
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public synchronized String advisorContext(String platform, int maxChars) {
    JSONArray out = new JSONArray();
    int size = 0;
    for (Entry e : list(50)) {
      if (e.practice || e.resultText.isEmpty()
          || e.insertedText.isEmpty()
          || !e.type.equals("advisor")
          || (!e.identity.optString("application").equals(platform)
              && !e.conversationRef.startsWith(platform + ":"))) continue;
      try {
        JSONObject row =
            new JSONObject()
                .put("identity", e.identity)
                .put("conversationRef", e.conversationRef)
                .put("text", e.resultText);
        int length = row.toString().length();
        if (size + length > maxChars) break;
        out.put(row);
        size += length;
      } catch (Exception ignored) {
      }
    }
    return out.toString();
  }

  public synchronized String recent(String conversation, int maxChars) {
    if (conversation.isEmpty()) return "";
    String account = KeyboardApp.instance == null ? "" : KeyboardApp.instance.cloud.userId(),
        result = "";
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT result_text FROM history WHERE deleted=0 AND practice=0 AND conversation_ref=? AND"
                    + " account=? AND inserted_text!='' AND result_text!='' ORDER BY updated DESC"
                    + " LIMIT 8",
                new String[] {conversation, account})) {
      while (c.moveToNext()) {
        String value = c.getString(0);
        int remaining = maxChars - result.length() - 1;
        if (remaining <= 0) break;
        if (value.length() > remaining) {
          int start = value.length() - remaining;
          if (start > 0 && Character.isLowSurrogate(value.charAt(start))) start++;
          value = value.substring(start);
        }
        result = value + "\n" + result;
      }
    }
    return result.trim();
  }

  public synchronized void remove(String id) {
    Entry old = get(id);
    if (old == null || old.deleted) return;
    ContentValues v = new ContentValues();
    v.put("deleted", 1);
    v.put("asr_text", "");
    v.put("inserted_text", "");
    v.put("result_text", "");
    v.put("conversation_ref", "");
    v.put("messages", "[]");
    v.put("screenshots", "[]");
    v.put("reply_groups", "[]");
    v.put("identity", "{}");
    v.put("audio_ref", "");
    update(id, v);
    if (!old.audioRef.isEmpty())
      app.doudou.ime.recording.RecordingStore.erase(context, old.audioRef);
    for (int i = 0; i < old.screenshots.length(); i++)
      imageFile(old.screenshots.optString(i)).delete();
  }

  public synchronized void prune(int days) {
    if (days <= 0) return;
    List<String> ids = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id FROM history WHERE deleted=0 AND type<>'recording' AND created<?",
                new String[] {"" + (System.currentTimeMillis() - days * 86400000L)})) {
      while (c.moveToNext()) ids.add(c.getString(0));
    }
    for (String id : ids) remove(id);
  }

  public synchronized void clear() {
    List<String> ids = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT id FROM history WHERE deleted=0", null)) {
      while (c.moveToNext()) ids.add(c.getString(0));
    }
    for (String id : ids) remove(id);
  }

  public synchronized JSONObject backup(long maxBytes, String account) throws Exception {
    JSONArray entries = new JSONArray();
    JSONObject images = new JSONObject();
    long size = 0;
    for (Entry e : entries(10000, account, false)) {
      JSONObject row = e.json();
      size += row.toString().getBytes("UTF-8").length;
      entries.put(row);
      for (int i = 0; i < e.screenshots.length(); i++) {
        String ref = e.screenshots.getString(i);
        File f = imageFile(ref);
        size += f.length() * 4 / 3;
        if (size > maxBytes) throw new IOException("历史与截图超过云备份空间，原资料保留在本机");
        try (FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
          images.put(
              ref,
              android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP));
        }
      }
      if (size > maxBytes) throw new IOException("历史超过云备份空间，原资料保留在本机");
    }
    return new JSONObject().put("schema", 3).put("entries", entries).put("images", images);
  }

  public synchronized void restore(JSONObject backup, String account) throws Exception {
    if (backup.getInt("schema") != 3) throw new IOException("备份格式不支持");
    JSONArray entries = backup.getJSONArray("entries");
    JSONObject images = backup.getJSONObject("images");
    if (entries.length() > 10000) throw new IOException("备份条目过多");
    List<File> created = new ArrayList<>();
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      for (int i = 0; i < entries.length(); i++) {
        JSONObject raw = entries.getJSONObject(i);
        String id = raw.getString("id");
        UUID.fromString(id);
        if (get(id) != null) continue;
        String type = raw.getString("type");
        if (!type.equals("voice") && !type.equals("advisor") && !type.equals("recording"))
          throw new IOException("历史类型无效");
        JSONArray refs = raw.getJSONArray("screenshots");
        for (int j = 0; j < refs.length(); j++) {
          String ref = refs.getString(j);
          File f = imageFile(ref);
          if (!f.exists()) {
            byte[] b =
                android.util.Base64.decode(images.getString(ref), android.util.Base64.DEFAULT);
            if (b.length > 3000000 || b.length < 2 || b[0] != (byte) 255 || b[1] != (byte) 216)
              throw new IOException("截图格式错误");
            try (FileOutputStream out = new FileOutputStream(f)) {
              out.write(b);
            }
            created.add(f);
          }
        }
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("type", type);
        v.put("practice", raw.optBoolean("practice") ? 1 : 0);
        v.put("asr_text", raw.optString("asrText"));
        v.put("inserted_text", raw.optString("insertedText"));
        v.put("result_text", raw.optString("resultText"));
        v.put("conversation_ref", raw.optString("conversationRef"));
        v.put("messages", raw.getJSONArray("messages").toString());
        v.put(
            "identity",
            raw.optJSONObject("identity") == null
                ? "{}"
                : raw.getJSONObject("identity").toString());
        v.put(
            "reply_groups",
            raw.optJSONArray("replyGroups") == null
                ? "[]"
                : raw.getJSONArray("replyGroups").toString());
        v.put("screenshots", refs.toString());
        v.put("revision", raw.getInt("revision"));
        v.put("account", account);
        v.put("created", raw.getLong("created"));
        v.put("updated", raw.getLong("updated"));
        d.insertOrThrow("history", null, v);
      }
      d.setTransactionSuccessful();
    } catch (Exception ex) {
      for (File f : created) f.delete();
      throw ex;
    } finally {
      d.endTransaction();
    }
    changed();
  }

  private void changed() {
    if (KeyboardApp.instance != null) app.doudou.ime.memory.LearningWorker.enqueue(context);
  }

  public synchronized void migrateLegacy(JSONObject old, String account) throws Exception {
    SQLiteDatabase d = getWritableDatabase();
    JSONArray rows = old.getJSONArray("items");
    d.beginTransaction();
    try {
      for (int i = 0; i < rows.length(); i++) {
        JSONObject x = rows.getJSONObject(i);
        if ((!x.optString("kind").equals("history") && !x.optString("kind").equals("feedback"))
            || x.optBoolean("deleted")) continue;
        String id = x.getString("globalId"),
            body = x.optString("body"),
            source = x.optString("source");
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put(
            "type",
            source.startsWith("advisor") || source.startsWith("capture:") ? "advisor" : "voice");
        if (x.optString("kind").equals("feedback") && body.contains(" → ")) {
          String[] halves = body.split(" → ", 2);
          v.put("inserted_text", halves[0]);
          v.put("result_text", halves[1]);
        } else if (source.startsWith("asr:")) v.put("asr_text", body);
        else if (source.equals("voice") || source.equals("advisor")) {
          v.put("inserted_text", body);
          v.put("result_text", body);
        } else if (source.startsWith("advisor-context:")) {
          try {
            JSONObject record = new JSONObject(body);
            JSONArray messages = new JSONArray();
            if (!record.optString("ocr").isEmpty())
              messages.put(
                  new JSONObject().put("speaker", "unknown").put("text", record.optString("ocr")));
            v.put("messages", messages.toString());
          } catch (JSONException ignored) {
            v.put("messages", "[]");
          }
        } else v.put("asr_text", body);
        v.put("created", x.getLong("created"));
        v.put("updated", x.getLong("updated"));
        v.put("account", account);
        d.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      }
      d.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES('migration.v04','done')");
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }
}
