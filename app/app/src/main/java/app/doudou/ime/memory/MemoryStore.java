package app.doudou.ime.memory;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

/** Read-only projection of Cloud memory, plus local candidate frequencies. */
public final class MemoryStore extends SQLiteOpenHelper {
  public static final class Item {
    public long id, owner, created, updated;
    public int revision;
    public boolean deleted;
    public String kind, title, body, aux, source, globalId, modifiedBy, clock;

    public JSONObject json() throws JSONException {
      return new JSONObject()
          .put("id", id)
          .put("kind", kind)
          .put("owner", owner)
          .put("title", title)
          .put("body", body)
          .put("aux", aux)
          .put("source", source)
          .put("revision", revision)
          .put("created", created)
          .put("updated", updated)
          .put("deleted", deleted)
          .put("globalId", globalId)
          .put("modifiedBy", modifiedBy)
          .put("clock", new JSONObject(clock));
    }
  }

  public MemoryStore(Context c) {
    this(c, "doudou-keyboard.db");
  }

  public MemoryStore(Context c, String name) {
    super(c, name, null, 5);
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,owner INTEGER"
            + " NOT NULL DEFAULT 0,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',aux TEXT NOT"
            + " NULL DEFAULT '',source TEXT NOT NULL DEFAULT 'manual',revision INTEGER NOT NULL"
            + " DEFAULT 1,created INTEGER NOT NULL,updated INTEGER NOT NULL,deleted INTEGER NOT"
            + " NULL DEFAULT 0)");
    d.execSQL("CREATE INDEX items_scope ON items(kind,owner,deleted,updated)");
    d.execSQL(
        "CREATE TABLE versions(item_id INTEGER NOT NULL,revision INTEGER NOT NULL,snapshot TEXT NOT"
            + " NULL,PRIMARY KEY(item_id,revision))");
    onUpgrade(d, 1, 5);
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    if (old < 2) {
      d.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
      d.execSQL(
          "INSERT INTO meta(key,value) VALUES('device',?)",
          new Object[] {UUID.randomUUID().toString()});
      d.execSQL("ALTER TABLE items ADD COLUMN global_id TEXT NOT NULL DEFAULT ''");
      d.execSQL("ALTER TABLE items ADD COLUMN modified_by TEXT NOT NULL DEFAULT ''");
      try (Cursor c = d.rawQuery("SELECT id FROM items", null)) {
        while (c.moveToNext())
          d.execSQL(
              "UPDATE items SET global_id=?,modified_by=? WHERE id=?",
              new Object[] {UUID.randomUUID().toString(), device(d), c.getLong(0)});
      }
      d.execSQL("CREATE UNIQUE INDEX items_global ON items(global_id)");
      d.execSQL(
          "CREATE TABLE learned_events(global_id TEXT PRIMARY KEY,processed_at INTEGER NOT NULL)");
    }
    if (old < 3) {
      d.execSQL("ALTER TABLE items ADD COLUMN clock TEXT NOT NULL DEFAULT '{}'");
      try (Cursor c = d.rawQuery("SELECT id,modified_by FROM items", null)) {
        while (c.moveToNext()) {
          Map<String, Long> initial = new TreeMap<>();
          initial.put(c.getString(1), 1L);
          d.execSQL(
              "UPDATE items SET clock=? WHERE id=?",
              new Object[] {new JSONObject(initial).toString(), c.getLong(0)});
        }
      }
    }
    if (old < 4) {
      d.execSQL(
          "CREATE TABLE learning_progress(global_id TEXT PRIMARY KEY,next_offset INTEGER NOT"
              + " NULL,source_revision INTEGER NOT NULL,source_length INTEGER NOT NULL,processed_at"
              + " INTEGER NOT NULL)");
      d.execSQL(
          "INSERT INTO learning_progress SELECT"
              + " i.global_id,length(i.body),i.revision,length(i.body),e.processed_at FROM items i"
              + " JOIN learned_events e ON i.global_id=e.global_id");
    }
    if (old < 5) {
      d.execSQL("DROP TABLE IF EXISTS versions");
      d.execSQL("DROP TABLE IF EXISTS learned_events");
      d.execSQL("DROP TABLE IF EXISTS learning_progress");
    }
  }

  private Item read(Cursor c) {
    Item x = new Item();
    x.id = c.getLong(0);
    x.kind = c.getString(1);
    x.owner = c.getLong(2);
    x.title = c.getString(3);
    x.body = c.getString(4);
    x.aux = c.getString(5);
    x.source = c.getString(6);
    x.revision = c.getInt(7);
    x.created = c.getLong(8);
    x.updated = c.getLong(9);
    x.deleted = c.getInt(10) != 0;
    x.globalId = c.getString(11);
    x.modifiedBy = c.getString(12);
    x.clock = c.getString(13);
    return x;
  }

  public Item get(long id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public List<Item> list(String kind, long owner) {
    List<Item> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM items WHERE kind=? AND owner=? AND deleted=0 ORDER BY updated"
                    + " DESC,id DESC LIMIT 500",
                new String[] {kind, "" + owner})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }

  private synchronized void saveLearned(Item before, String word, String code, int frequency) {
    SQLiteDatabase d = getWritableDatabase();
    ContentValues v = new ContentValues();
    v.put("kind", "learned");
    v.put("title", word);
    v.put("body", code);
    v.put("aux", "" + frequency);
    v.put("source", "candidate");
    v.put("updated", System.currentTimeMillis());
    if (before == null) {
      v.put("global_id", UUID.randomUUID().toString());
      v.put("created", System.currentTimeMillis());
      d.insertOrThrow("items", null, v);
    } else {
      v.put("revision", before.revision + 1);
      d.update("items", v, "id=?", new String[] {"" + before.id});
    }
  }

  public synchronized void learn(String word, String code) {
    if (word.isEmpty() || word.length() > 40 || code.isEmpty()) return;
    for (Item i : list("learned", 0))
      if (i.title.equals(word)) {
        int n;
        try {
          n = Integer.parseInt(i.aux);
        } catch (Exception e) {
          n = 0;
        }
        saveLearned(i, word, code, Math.min(1000000, n + 1));
        return;
      }
    saveLearned(null, word, code, 1);
  }

  public Map<String, Integer> frequencies() {
    Map<String, Integer> m = new HashMap<>();
    for (Item i : list("learned", 0)) {
      try {
        m.put(i.title, Integer.parseInt(i.aux));
      } catch (Exception ignored) {
      }
    }
    for (Item i : list("word", 0)) m.put(i.title, 100);
    return m;
  }

  public JSONObject exportData() throws JSONException {
    JSONArray a = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items ORDER BY id", null)) {
      while (c.moveToNext()) a.put(read(c).json());
    }
    return new JSONObject()
        .put("schema", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("items", a);
  }

  private String device(SQLiteDatabase d) {
    try (Cursor c = d.rawQuery("SELECT value FROM meta WHERE key='device'", null)) {
      if (!c.moveToFirst()) throw new IllegalStateException("本机标识缺失");
      return c.getString(0);
    }
  }

  public synchronized void discardLegacy() {
    getWritableDatabase().delete("items", "kind IN ('history','feedback')", null);
  }

  public synchronized String meta(String key, String fallback) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[] {key})) {
      return c.moveToFirst() ? c.getString(0) : fallback;
    }
  }

  public synchronized void setMeta(String key, String value) {
    getWritableDatabase()
        .execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", new Object[] {key, value});
  }

  public synchronized void clearLocalLearning() {
    getWritableDatabase().delete("items", "kind='learned'", null);
  }

  public long cacheVersion(String account) {
    return Long.parseLong(meta("cache.version:" + account, "0"));
  }

  public JSONObject cache(String account) throws JSONException {
    return new JSONObject(
        meta(
            "cache.body:" + account,
            "{\"hotwords\":[],\"styles\":[],\"people\":[],\"candidates\":[]}"));
  }

  public synchronized void cache(String account, long version, JSONObject memory)
      throws JSONException {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      Map<String, Long> ids = new HashMap<>();
      try (Cursor c =
          d.rawQuery(
              "SELECT global_id,id FROM items WHERE kind IN"
                  + " ('word','style','person','memory','proposal')",
              null)) {
        while (c.moveToNext()) ids.put(c.getString(0), c.getLong(1));
      }
      Set<String> used = new HashSet<>();
      Map<String, Long> owners = new HashMap<>();
      JSONArray words = memory.getJSONArray("hotwords"),
          styles = memory.getJSONArray("styles"),
          people = memory.getJSONArray("people"),
          candidates = memory.optJSONArray("candidates");
      for (int i = 0; i < words.length(); i++) {
        JSONObject w = words.getJSONObject(i);
        String code = meta("word.code:" + w.getString("id"), "");
        if (code.isEmpty())
          code =
              com.github.promeg.pinyinhelper.Pinyin.toPinyin(w.getString("text"), "")
                  .toLowerCase(java.util.Locale.ROOT);
        cacheItem(
            d, ids, used, w.getString("id"), "word", 0, w.getString("text"), code, "", version);
      }
      for (int i = 0; i < styles.length(); i++) {
        JSONObject s = styles.getJSONObject(i);
        cacheItem(
            d,
            ids,
            used,
            s.getString("id"),
            "style",
            0,
            s.getString("scene"),
            join(s.getJSONArray("rules")),
            s.toString(),
            version);
      }
      for (int i = 0; i < people.length(); i++) {
        JSONObject p = people.getJSONObject(i);
        long id =
            cacheItem(
                d,
                ids,
                used,
                p.getString("id"),
                "person",
                0,
                p.getString("name"),
                join(p.getJSONArray("aliases")),
                p.toString(),
                version);
        owners.put(p.getString("id"), id);
      }
      for (int i = 0; i < people.length(); i++) {
        JSONObject p = people.getJSONObject(i);
        JSONArray facts = p.getJSONArray("facts"), relations = p.getJSONArray("relations");
        for (int j = 0; j < facts.length(); j++) {
          JSONObject f = facts.getJSONObject(j);
          cacheItem(
              d,
              ids,
              used,
              f.getString("id"),
              "memory",
              owners.get(p.getString("id")),
              f.getString("key"),
              f.getString("value"),
              f.getString("scope"),
              version);
        }
        for (int j = 0; j < relations.length(); j++) {
          JSONObject r = relations.getJSONObject(j);
          cacheItem(
              d,
              ids,
              used,
              r.getString("id"),
              "memory",
              owners.get(p.getString("id")),
              "关系",
              r.getString("type"),
              "relation:" + r.getString("targetId"),
              version);
        }
      }
      if (candidates != null)
        for (int i = 0; i < candidates.length(); i++) {
          JSONObject c = candidates.getJSONObject(i);
          cacheItem(
              d,
              ids,
              used,
              c.getString("id"),
              "proposal",
              0,
              c.optString("reason", "记忆更新待确认"),
              c.toString(),
              "cloud-candidate",
              version);
        }
      for (String id : ids.keySet())
        if (!used.contains(id)) d.delete("items", "global_id=?", new String[] {id});
      setMeta("cache.body:" + account, memory.toString());
      setMeta("cache.version:" + account, "" + version);
      setMeta("cache.active", account);
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  private long cacheItem(
      SQLiteDatabase d,
      Map<String, Long> ids,
      Set<String> used,
      String global,
      String kind,
      long owner,
      String title,
      String body,
      String aux,
      long version) {
    used.add(global);
    ContentValues v = new ContentValues();
    v.put("global_id", global);
    v.put("kind", kind);
    v.put("owner", owner);
    v.put("title", title);
    v.put("body", body);
    v.put("aux", aux);
    v.put("source", "cloud");
    v.put("revision", version);
    v.put("deleted", 0);
    v.put("updated", System.currentTimeMillis());
    v.put("modified_by", device(d));
    v.put("clock", "{}");
    Long old = ids.get(global);
    if (old != null) {
      d.update("items", v, "id=?", new String[] {"" + old});
      return old;
    }
    v.put("created", System.currentTimeMillis());
    return d.insertOrThrow("items", null, v);
  }

  private String join(JSONArray values) {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < values.length(); i++) list.add(values.optString(i));
    return String.join("\n", list);
  }
}
