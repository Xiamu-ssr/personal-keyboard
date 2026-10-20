package app.doudou.ime;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

/** Shared, local-only data layer. Revisions and tombstones make edits/deletes reversible. */
public final class Store extends SQLiteOpenHelper {
  public static final class Item {
    public long id, owner, created, updated;
    public int revision;
    public boolean deleted;
    public String kind, title, body, aux, source;

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
          .put("deleted", deleted);
    }
  }

  public Store(Context c) {
    this(c, "doudou-keyboard.db");
  }

  Store(Context c, String name) {
    super(c, name, null, 1);
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
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    throw new IllegalStateException("Unsupported database migration");
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

  public synchronized long save(
      long id,
      int expected,
      String kind,
      long owner,
      String title,
      String body,
      String aux,
      String source) {
    if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("请填写名称");
    if (kind.equals("memory") && owner > 0) {
      Item p = get(owner);
      if (p == null || p.deleted || !p.kind.equals("person"))
        throw new IllegalArgumentException("人物不存在");
    }
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      Item before = id > 0 ? get(id) : null;
      if (id > 0 && (before == null || before.deleted || before.revision != expected))
        throw new IllegalStateException("资料已更新，请重新打开");
      if (before != null) {
        ContentValues snap = new ContentValues();
        snap.put("item_id", id);
        snap.put("revision", before.revision);
        try {
          snap.put("snapshot", before.json().toString());
        } catch (JSONException e) {
          throw new IllegalStateException(e);
        }
        d.insertOrThrow("versions", null, snap);
      }
      ContentValues v = new ContentValues();
      v.put("kind", kind);
      v.put("owner", owner);
      v.put("title", title.trim());
      v.put("body", body == null ? "" : body);
      v.put("aux", aux == null ? "" : aux);
      v.put("source", source == null ? "manual" : source);
      v.put("updated", System.currentTimeMillis());
      if (before == null) {
        v.put("created", System.currentTimeMillis());
        id = d.insertOrThrow("items", null, v);
      } else {
        v.put("revision", before.revision + 1);
        d.update("items", v, "id=?", new String[] {"" + id});
      }
      d.setTransactionSuccessful();
      return id;
    } finally {
      d.endTransaction();
    }
  }

  public synchronized void delete(long id) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("deleted", 1);
      long stamp = System.currentTimeMillis();
      try (Cursor c = d.rawQuery("SELECT MAX(updated) FROM items WHERE deleted=1", null)) {
        if (c.moveToFirst()) stamp = Math.max(stamp, c.getLong(0) + 1);
      }
      v.put("updated", stamp);
      d.update(
          "items",
          v,
          "deleted=0 AND (id=? OR (kind='memory' AND owner=?))",
          new String[] {"" + id, "" + id});
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public synchronized void restore(long id) {
    Item p = get(id);
    if (p == null) return;
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("deleted", 0);
      d.update("items", v, "id=?", new String[] {"" + id});
      if (p.kind.equals("person")) {
        d.execSQL(
            "UPDATE items SET deleted=0 WHERE kind='memory' AND owner=? AND updated=?",
            new Object[] {id, p.updated});
      }
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
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
        save(i.id, i.revision, "learned", 0, word, code, "" + (n + 1), "candidate");
        return;
      }
    save(0, 0, "learned", 0, word, code, "1", "candidate");
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

  public void pruneHistory(int days) {
    if (days <= 0) return;
    getWritableDatabase()
        .delete(
            "items",
            "kind IN ('history','feedback') AND created<?",
            new String[] {"" + (System.currentTimeMillis() - days * 86400000L)});
  }

  public synchronized void clearAll() {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      d.delete("versions", null, null);
      d.delete("items", null, null);
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public JSONObject exportData() throws JSONException {
    JSONArray a = new JSONArray();
    try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items ORDER BY id", null)) {
      while (c.moveToNext()) a.put(read(c).json());
    }
    return new JSONObject()
        .put("schema", 1)
        .put("exportedAt", System.currentTimeMillis())
        .put("items", a);
  }

  public synchronized int importData(JSONObject doc) throws JSONException {
    if (doc.optInt("schema") != 1) throw new IllegalArgumentException("不支持的备份格式");
    JSONArray a = doc.getJSONArray("items");
    if (a.length() > 10000) throw new IllegalArgumentException("备份过大");
    Map<Long, Long> map = new HashMap<>();
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      // Import as new local records: an older backup never overwrites current revisions.
      for (int phase = 0; phase < 2; phase++)
        for (int j = 0; j < a.length(); j++) {
          JSONObject x = a.getJSONObject(j);
          if (x.optBoolean("deleted")) continue;
          String kind = x.getString("kind");
          long owner = x.optLong("owner");
          if ((owner == 0) != (phase == 0)) continue;
          if (!Arrays.asList("word", "style", "person", "memory", "history", "feedback", "learned")
              .contains(kind)) continue;
          if (owner > 0 && !map.containsKey(owner)) continue;
          long id =
              save(
                  0,
                  0,
                  kind,
                  owner == 0 ? 0 : map.get(owner),
                  x.getString("title"),
                  x.optString("body"),
                  x.optString("aux"),
                  "import:" + x.optString("source"));
          map.put(x.getLong("id"), id);
        }
      d.setTransactionSuccessful();
      return map.size();
    } finally {
      d.endTransaction();
    }
  }
}
