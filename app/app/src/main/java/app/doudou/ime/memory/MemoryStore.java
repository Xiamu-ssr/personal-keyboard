package app.doudou.ime.memory;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import app.doudou.ime.sync.RevisionClock;
import java.util.*;
import org.json.*;

/** Shared, local-only data layer. Revisions and tombstones make edits/deletes reversible. */
public final class MemoryStore extends SQLiteOpenHelper {
  public static final class Item {
    public long id, owner, created, updated;
    public int learningOffset;
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
    super(c, name, null, 4);
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
    onUpgrade(d, 1, 4);
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
      v.put(
          "updated", Math.max(System.currentTimeMillis(), before == null ? 0 : before.updated + 1));
      v.put("modified_by", device(d));
      v.put(
          "clock",
          new JSONObject(
                  RevisionClock.advance(
                      before == null ? Collections.emptyMap() : clock(before.clock), device(d)))
              .toString());
      if (before == null) {
        v.put("global_id", UUID.randomUUID().toString());
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
      advanceWhere(
          d,
          "deleted=0 AND (id=? OR (kind='memory' AND owner=?))",
          new String[] {"" + id, "" + id});
      v.put("deleted", 1);
      long stamp = System.currentTimeMillis();
      try (Cursor c = d.rawQuery("SELECT MAX(updated) FROM items WHERE deleted=1", null)) {
        if (c.moveToFirst()) stamp = Math.max(stamp, c.getLong(0) + 1);
      }
      v.put("updated", stamp);
      v.put("modified_by", device(d));
      d.execSQL(
          "UPDATE items SET revision=revision+1 WHERE deleted=0 AND (id=? OR (kind='memory' AND"
              + " owner=?))",
          new Object[] {id, id});
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
      advanceWhere(
          d,
          "id=? OR (kind='memory' AND owner=? AND updated=?)",
          new String[] {"" + id, "" + id, "" + p.updated});
      long now = Math.max(System.currentTimeMillis(), p.updated + 1);
      if (p.kind.equals("person"))
        d.execSQL(
            "UPDATE items SET deleted=0,revision=revision+1,updated=?,modified_by=? WHERE"
                + " kind='memory' AND owner=? AND updated=?",
            new Object[] {now, device(d), id, p.updated});
      d.execSQL(
          "UPDATE items SET deleted=0,revision=revision+1,updated=?,modified_by=? WHERE id=?",
          new Object[] {now, device(d), id});
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
    SQLiteDatabase d = getWritableDatabase();
    advanceWhere(
        d,
        "kind IN ('history','feedback') AND deleted=0 AND created<?",
        new String[] {"" + (System.currentTimeMillis() - days * 86400000L)});
    d.execSQL(
        "UPDATE items SET deleted=1,body='',revision=revision+1,updated=?,modified_by=? WHERE kind"
            + " IN ('history','feedback') AND deleted=0 AND created<?",
        new Object[] {
          System.currentTimeMillis(), device(d), System.currentTimeMillis() - days * 86400000L
        });
  }

  public synchronized void clearAll() {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      advanceWhere(d, "1=1", null);
      d.delete("versions", null, null);
      d.execSQL(
          "UPDATE items SET"
              + " deleted=1,title='已清除',body='',aux='',revision=revision+1,updated=?,modified_by=?",
          new Object[] {System.currentTimeMillis(), device(d)});
      d.delete("learned_events", null, null);
      d.delete("learning_progress", null, null);
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
        .put("schema", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("items", a);
  }

  public synchronized int importData(JSONObject doc) throws JSONException {
    if (doc.optInt("schema") != 2) throw new IllegalArgumentException("不支持的备份格式");
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
          if (!Arrays.asList(
                  "word", "style", "person", "memory", "history", "feedback", "learned", "proposal")
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

  private String device(SQLiteDatabase d) {
    try (Cursor c = d.rawQuery("SELECT value FROM meta WHERE key='device'", null)) {
      c.moveToFirst();
      return c.getString(0);
    }
  }

  public Item byGlobal(String global) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT * FROM items WHERE global_id=?", new String[] {global})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public List<Item> unprocessed(int maximum, int budget) {
    List<Item> out = new ArrayList<>();
    int size = 0;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT i.*,CASE WHEN p.source_revision=i.revision THEN p.next_offset ELSE 0 END AS"
                    + " next_offset FROM items i LEFT JOIN learning_progress p ON"
                    + " p.global_id=i.global_id WHERE i.kind IN ('history','feedback') AND"
                    + " i.deleted=0 AND (p.global_id IS NULL OR p.source_revision!=i.revision OR"
                    + " p.next_offset<p.source_length) AND length(i.body)>0 ORDER BY i.id LIMIT ?",
                new String[] {"" + maximum})) {
      while (c.moveToNext()) {
        Item item = read(c);
        item.learningOffset = c.getInt(14);
        int n = Math.min(1600, item.body.length() - item.learningOffset);
        if (!out.isEmpty() && size + n > budget) break;
        out.add(item);
        size += n;
      }
    }
    return out;
  }

  public String learningFragment(Item item) {
    int end = Math.min(item.body.length(), item.learningOffset + 1600);
    if (end < item.body.length()
        && end > item.learningOffset
        && Character.isHighSurrogate(item.body.charAt(end - 1))) end--;
    return item.body.substring(item.learningOffset, end);
  }

  public synchronized void markProcessed(List<Item> batch) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      for (Item i : batch) {
        Item current = get(i.id);
        if (current == null || current.deleted || current.revision != i.revision) continue;
        int next = i.learningOffset + learningFragment(i).length();
        d.execSQL(
            "INSERT OR REPLACE INTO"
                + " learning_progress(global_id,next_offset,source_revision,source_length,processed_at)"
                + " VALUES(?,?,?,?,?)",
            new Object[] {
              i.globalId, next, i.revision, i.body.length(), System.currentTimeMillis()
            });
      }
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public synchronized JSONObject syncSnapshot() throws JSONException {
    JSONObject doc = exportData();
    JSONArray items = doc.getJSONArray("items");
    JSONArray processed = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT global_id,next_offset,source_revision FROM learning_progress LIMIT 10000",
                null)) {
      while (c.moveToNext())
        processed.put(
            new JSONObject()
                .put("globalId", c.getString(0))
                .put("nextOffset", c.getInt(1))
                .put("revision", c.getInt(2))
                .put("sourceHash", learningHash(byGlobal(c.getString(0)))));
    }
    doc.put("processed", processed);
    for (int i = 0; i < items.length(); i++) {
      JSONObject x = items.getJSONObject(i);
      Item owner = get(x.optLong("owner"));
      x.put("ownerGlobal", owner == null ? "" : owner.globalId);
    }
    return doc;
  }

  public synchronized int mergeSync(JSONObject doc) throws JSONException {
    if (doc.optInt("schema") != 2) throw new IllegalArgumentException("同步格式不支持");
    JSONArray rows = doc.getJSONArray("items");
    if (rows.length() > 10000) throw new IllegalArgumentException("同步包过大");
    Set<String>
        allowed =
            new HashSet<>(
                Arrays.asList(
                    "word",
                    "style",
                    "person",
                    "memory",
                    "history",
                    "feedback",
                    "learned",
                    "proposal")),
        seen = new HashSet<>();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject x = rows.getJSONObject(i);
      UUID.fromString(x.getString("globalId"));
      UUID.fromString(x.getString("modifiedBy"));
      clock(x.getJSONObject("clock").toString());
      if (!seen.add(x.getString("globalId"))
          || !allowed.contains(x.getString("kind"))
          || x.getString("title").length() > 1000
          || x.optString("body").length() > 50000
          || x.getInt("revision") < 1
          || x.getLong("updated") < 0) throw new IllegalArgumentException("同步资料格式错误");
    }
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    int count = 0;
    try {
      for (int phase = 0; phase < 2; phase++)
        for (int i = 0; i < rows.length(); i++) {
          JSONObject x = rows.getJSONObject(i);
          String ownerGlobal = x.optString("ownerGlobal");
          if (ownerGlobal.isEmpty() != (phase == 0)) continue;
          Item owner = ownerGlobal.isEmpty() ? null : byGlobal(ownerGlobal);
          if (!ownerGlobal.isEmpty() && (owner == null || !owner.kind.equals("person"))) continue;
          Item old = byGlobal(x.getString("globalId"));
          boolean incomingDeleted = x.optBoolean("deleted");
          Map<String, Long> incoming = clock(x.getJSONObject("clock").toString()),
              merged = incoming;
          if (old != null) {
            RevisionClock.Order order = RevisionClock.compare(clock(old.clock), incoming);
            if (order == RevisionClock.Order.AFTER || order == RevisionClock.Order.EQUAL) continue;
            merged = RevisionClock.merge(clock(old.clock), incoming);
            if (order == RevisionClock.Order.CONCURRENT) {
              boolean differs =
                  !old.body.equals(x.optString("body")) || !old.title.equals(x.optString("title"));
              boolean preferLocal =
                  (old.deleted && !incomingDeleted)
                      || (!incomingDeleted
                          && old.source.equals("manual")
                          && !x.optString("source").equals("manual"))
                      || (!incomingDeleted && old.updated > x.getLong("updated"))
                      || (!incomingDeleted
                          && old.updated == x.getLong("updated")
                          && old.modifiedBy.compareTo(x.getString("modifiedBy")) >= 0);
              if (differs && !old.deleted && !incomingDeleted) {
                String alternate = preferLocal ? x.optString("body") : old.body;
                String note = "同步时保留的另一版资料：" + x.optString("title") + "\n" + alternate;
                if (note.length() > 4000) note = note.substring(0, 4000);
                boolean duplicate = false;
                for (Item f : list("feedback", 0))
                  if (f.source.equals("sync-conflict:" + old.globalId) && f.body.equals(note))
                    duplicate = true;
                if (!duplicate)
                  save(0, 0, "feedback", 0, "同步差异待确认", note, "", "sync-conflict:" + old.globalId);
              }
              if (preferLocal) {
                ContentValues union = new ContentValues();
                union.put("clock", new JSONObject(merged).toString());
                d.update("items", union, "id=?", new String[] {"" + old.id});
                continue;
              }
            }
          }
          ContentValues v = new ContentValues();
          v.put("global_id", x.getString("globalId"));
          v.put("modified_by", x.getString("modifiedBy"));
          v.put("clock", new JSONObject(merged).toString());
          v.put("kind", x.getString("kind"));
          v.put("owner", owner == null ? 0 : owner.id);
          v.put("title", x.getString("title"));
          v.put("body", x.optString("body"));
          v.put("aux", x.optString("aux"));
          v.put("source", x.optString("source"));
          v.put(
              "revision",
              old == null
                  ? x.getInt("revision")
                  : Math.max(old.revision, x.getInt("revision")) + 1);
          v.put("created", x.getLong("created"));
          v.put("updated", x.getLong("updated"));
          v.put("deleted", incomingDeleted ? 1 : 0);
          if (old == null) d.insertOrThrow("items", null, v);
          else d.update("items", v, "id=?", new String[] {"" + old.id});
          count++;
        }
      JSONArray processed = doc.optJSONArray("processed");
      if (processed != null) {
        if (processed.length() > 10000) throw new IllegalArgumentException("已整理来源过多");
        for (int j = 0; j < processed.length(); j++) {
          JSONObject p = processed.getJSONObject(j);
          String id = p.getString("globalId");
          UUID.fromString(id);
          Item source = byGlobal(id);
          int revision = p.getInt("revision"), offset = p.getInt("nextOffset");
          if (source == null
              || !learningHash(source).equals(p.optString("sourceHash"))
              || offset < 0
              || offset > source.body.length()) continue;
          d.execSQL(
              "INSERT INTO"
                  + " learning_progress(global_id,next_offset,source_revision,source_length,processed_at)"
                  + " VALUES(?,?,?,?,?) ON CONFLICT(global_id) DO UPDATE SET next_offset=CASE WHEN"
                  + " learning_progress.source_revision=excluded.source_revision THEN"
                  + " MAX(learning_progress.next_offset,excluded.next_offset) ELSE"
                  + " excluded.next_offset"
                  + " END,source_revision=excluded.source_revision,source_length=excluded.source_length",
              new Object[] {
                id, offset, source.revision, source.body.length(), System.currentTimeMillis()
              });
        }
      }
      d.setTransactionSuccessful();
      return count;
    } finally {
      d.endTransaction();
    }
  }

  private String learningHash(Item item) {
    if (item == null) return "";
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(
                  (item.kind + "|" + item.source + "|" + item.aux + "|" + item.body)
                      .getBytes("UTF-8"));
      StringBuilder s = new StringBuilder();
      for (byte b : hash) s.append(String.format(java.util.Locale.ROOT, "%02x", b));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Long> clock(String value) {
    try {
      JSONObject json = new JSONObject(value);
      if (json.length() > 40) throw new IllegalArgumentException("同步设备过多");
      Map<String, Long> result = new TreeMap<>();
      Iterator<String> keys = json.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        UUID.fromString(k);
        long n = json.getLong(k);
        if (n < 1 || n > 1000000000) throw new IllegalArgumentException("修订计数无效");
        result.put(k, n);
      }
      return result;
    } catch (JSONException e) {
      throw new IllegalArgumentException("修订信息错误", e);
    }
  }

  private void advanceWhere(SQLiteDatabase d, String where, String[] args) {
    try (Cursor c = d.rawQuery("SELECT id,clock FROM items WHERE " + where, args)) {
      while (c.moveToNext())
        d.execSQL(
            "UPDATE items SET clock=? WHERE id=?",
            new Object[] {
              new JSONObject(RevisionClock.advance(clock(c.getString(1)), device(d))).toString(),
              c.getLong(0)
            });
    }
  }

  public synchronized void acceptProposal(Item item) throws JSONException {
    if (item == null || item.deleted || !item.kind.equals("proposal"))
      throw new IllegalArgumentException("候选已更新");
    JSONObject p = new JSONObject(item.body);
    if (p.optInt("baseRevision", -1) != 0) throw new IllegalStateException("候选不是新增资料，请重新整理");
    JSONArray ids = p.getJSONArray("sourceIds");
    for (int i = 0; i < ids.length(); i++) {
      Item source = byGlobal(ids.getString(i));
      if (source == null || source.deleted) throw new IllegalStateException("来源已删除，请重新核对");
    }
    JSONArray evidence = p.optJSONArray("evidence");
    if (evidence != null)
      for (int i = 0; i < evidence.length(); i++) {
        JSONObject e = evidence.getJSONObject(i);
        Item source = byGlobal(e.getString("id"));
        if (source == null || source.revision != e.getInt("revision"))
          throw new IllegalStateException("来源已修订，请重新核对");
      }
    String category = p.getString("category"),
        scope = p.getString("scope"),
        content = p.getString("content");
    long owner = 0;
    String kind =
        category.equals("vocabulary_correction")
            ? "word"
            : category.equals("personal_style") ? "style" : "memory";
    if (scope.equals("contact")) {
      Item person = byGlobal(p.getString("subject"));
      if (person == null || person.deleted || !person.kind.equals("person"))
        throw new IllegalStateException("人物已变化");
      owner = person.id;
      kind = "memory";
    }
    String title = content.substring(0, Math.min(kind.equals("word") ? 40 : 30, content.length()));
    for (Item existing : list(kind, owner))
      if (existing.title.equals(title) || existing.body.equals(content)) {
        delete(item.id);
        return;
      }
    for (Item existing : allDeleted(kind, owner))
      if (existing.title.equals(title) || existing.body.equals(content))
        throw new IllegalStateException("你已删除过相同记忆，请手动添加以恢复");
    save(
        0,
        0,
        kind,
        owner,
        title,
        kind.equals("word") ? "" : content,
        category,
        "confirmed-learning");
    delete(item.id);
  }

  private List<Item> allDeleted(String kind, long owner) {
    List<Item> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM items WHERE kind=? AND owner=? AND deleted=1",
                new String[] {kind, "" + owner})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }
}
