package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.capture.LocalOcr;
import app.doudou.ime.memory.MemoryStore;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

public class DeviceTest {
  Context c;
  MemoryStore s;
  String db;

  @Before
  public void before() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    db = "test-" + System.nanoTime() + ".db";
    s = new MemoryStore(c, db);
  }

  @After
  public void after() {
    s.close();
    c.deleteDatabase(db);
  }

  @Test
  public void persistsAndRejectsStaleRevision() {
    long id = s.save(0, 0, "word", 0, "豆豆", "doudou", "", "manual");
    MemoryStore.Item old = s.get(id);
    s.save(id, old.revision, "word", 0, "豆豆输入法", "doudoushurufa", "", "manual");
    boolean rejected = false;
    try {
      s.save(id, old.revision, "word", 0, "旧值", "", "", "manual");
    } catch (IllegalStateException e) {
      rejected = true;
    }
    assertTrue(rejected);
    s.close();
    s = new MemoryStore(c, db);
    assertEquals("豆豆输入法", s.get(id).title);
  }

  @Test
  public void deleteRestoresOnlyItsOwnMemory() {
    long p = s.save(0, 0, "person", 0, "测试人物", "", "chat.a", "manual"),
        q = s.save(0, 0, "person", 0, "另一个人", "", "chat.b", "manual");
    long a = s.save(0, 0, "memory", p, "事件", "约下次见", "event", "manual"),
        b = s.save(0, 0, "memory", q, "偏好", "不喝咖啡", "preference", "manual");
    s.delete(p);
    assertTrue(s.get(a).deleted);
    assertFalse(s.get(b).deleted);
    s.restore(p);
    assertFalse(s.get(a).deleted);
    assertEquals(1, s.list("memory", p).size());
  }

  @Test
  public void backupRemapsPeopleAndDoesNotOverwrite() throws Exception {
    long p = s.save(0, 0, "person", 0, "测试人物", "本机资料", "chat.a", "manual");
    s.save(0, 0, "memory", p, "事实", "只是一条测试资料", "fact", "manual");
    JSONObject backup = s.exportData();
    int imported = s.importData(backup);
    assertEquals(2, imported);
    assertEquals(2, s.list("person", 0).size());
    for (MemoryStore.Item person : s.list("person", 0))
      assertEquals(1, s.list("memory", person.id).size());
    assertEquals("本机资料", s.get(p).body);
  }

  @Test
  public void invalidBackupRollsBack() throws Exception {
    JSONObject doc =
        new JSONObject()
            .put("schema", 2)
            .put(
                "items",
                new JSONArray()
                    .put(new JSONObject().put("id", 1).put("kind", "person").put("title", "新人物"))
                    .put(new JSONObject().put("id", 2).put("kind", "word")));
    try {
      s.importData(doc);
      fail("invalid input accepted");
    } catch (JSONException expected) {
    }
    assertTrue(s.list("person", 0).isEmpty());
  }

  @Test
  public void historyRetentionPreservesMemories() {
    long word = s.save(0, 0, "word", 0, "热词", "reci", "", "manual");
    s.save(0, 0, "history", 0, "历史", "过去记录", "", "voice");
    s.getWritableDatabase().execSQL("UPDATE items SET created=1 WHERE kind='history'");
    s.pruneHistory(7);
    assertTrue(s.list("history", 0).isEmpty());
    assertNotNull(s.get(word));
  }

  @Test
  public void syncRemapsGlobalPeoplePreservesManualEditsAndTombstones() throws Exception {
    String otherName = "sync-" + System.nanoTime() + ".db";
    MemoryStore other = new MemoryStore(c, otherName);
    try {
      long person = s.save(0, 0, "person", 0, "示例联系人", "", "", "manual");
      long memory = s.save(0, 0, "memory", person, "偏好", "茶", "fact", "manual");
      assertEquals(2, other.mergeSync(s.syncSnapshot()));
      MemoryStore.Item p2 = other.byGlobal(s.get(person).globalId),
          m2 = other.byGlobal(s.get(memory).globalId);
      assertNotNull(p2);
      assertEquals(p2.id, m2.owner);
      other.save(m2.id, m2.revision, "memory", p2.id, "偏好", "本机手动改成咖啡", "fact", "manual");
      s.save(
          memory,
          s.get(memory).revision,
          "memory",
          person,
          "偏好",
          "模型改成汽水",
          "fact",
          "cloud-learning");
      other.mergeSync(s.syncSnapshot());
      assertEquals("本机手动改成咖啡", other.get(m2.id).body);
      other.delete(m2.id);
      other.mergeSync(s.syncSnapshot());
      assertTrue(other.get(m2.id).deleted);
      assertEquals(0, other.mergeSync(other.syncSnapshot()));
      s.mergeSync(other.syncSnapshot());
      assertTrue(s.get(memory).deleted);
      other.restore(m2.id);
      s.mergeSync(other.syncSnapshot());
      assertFalse(s.get(memory).deleted);
    } finally {
      other.close();
      c.deleteDatabase(otherName);
    }
  }

  @Test
  public void incrementalHistoryIsProcessedOnceAndEvidenceCanBeRevoked() throws Exception {
    long h = s.save(0, 0, "history", 0, "语音", "豆豆", "transcript", "asr:test");
    java.util.List<MemoryStore.Item> batch = s.unprocessed(12, 8000);
    assertEquals(1, batch.size());
    s.markProcessed(batch);
    assertTrue(s.unprocessed(12, 8000).isEmpty());
    JSONObject p =
        new JSONObject()
            .put("category", "vocabulary_correction")
            .put("scope", "self")
            .put("subject", "")
            .put("content", "豆豆")
            .put("baseRevision", 0)
            .put("sourceIds", new JSONArray().put(s.get(h).globalId));
    long id = s.save(0, 0, "proposal", 0, "词汇候选", p.toString(), "", "cloud-learning");
    s.delete(h);
    try {
      s.acceptProposal(s.get(id));
      fail("deleted evidence was learned");
    } catch (IllegalStateException expected) {
    }
    assertTrue(s.list("word", 0).isEmpty());
  }

  @Test
  public void longHistoryResumesItsNextFragmentIncludingEmoji() {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 1900; i++) text.append("😀");
    long id = s.save(0, 0, "history", 0, "长历史", text.toString(), "transcript", "asr:test");
    int total = 0, parts = 0;
    while (true) {
      java.util.List<MemoryStore.Item> batch = s.unprocessed(12, 8000);
      if (batch.isEmpty()) break;
      MemoryStore.Item item = batch.get(0);
      assertEquals(id, item.id);
      assertEquals(total, item.learningOffset);
      String piece = s.learningFragment(item);
      assertFalse(Character.isHighSurrogate(piece.charAt(piece.length() - 1)));
      total += piece.length();
      parts++;
      s.markProcessed(batch);
    }
    assertEquals(text.length(), total);
    assertEquals(3, parts);
  }

  @Test
  public void migratesExistingV1RecordsWithoutLosingTheirIdentityOrText() throws Exception {
    String name = "migration-" + System.nanoTime() + ".db";
    android.database.sqlite.SQLiteDatabase old = c.openOrCreateDatabase(name, 0, null);
    old.execSQL(
        "CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,owner INTEGER"
            + " NOT NULL DEFAULT 0,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',aux TEXT NOT"
            + " NULL DEFAULT '',source TEXT NOT NULL DEFAULT 'manual',revision INTEGER NOT NULL"
            + " DEFAULT 1,created INTEGER NOT NULL,updated INTEGER NOT NULL,deleted INTEGER NOT"
            + " NULL DEFAULT 0)");
    old.execSQL(
        "CREATE TABLE versions(item_id INTEGER NOT NULL,revision INTEGER NOT NULL,snapshot TEXT NOT"
            + " NULL,PRIMARY KEY(item_id,revision))");
    old.execSQL(
        "INSERT INTO items(kind,title,body,created,updated) VALUES('word','迁移测试','保留原资料',1,1)");
    old.setVersion(1);
    old.close();
    MemoryStore upgraded = new MemoryStore(c, name);
    try {
      MemoryStore.Item item = upgraded.get(1);
      assertEquals("保留原资料", item.body);
      assertNotNull(item.globalId);
      assertTrue(item.clock.length() > 10);
      upgraded.save(1, item.revision, "word", 0, item.title, "继续编辑", "", "manual");
    } finally {
      upgraded.close();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void configuredIpUsesNormalCertificateValidation() throws Exception {
    app.doudou.ime.cloud.CloudClient cloud = new app.doudou.ime.cloud.CloudClient(c);
    JSONObject health = cloud.publicRequest("/healthz", null);
    assertEquals("ok", health.getString("status"));
  }

  @Test
  public void bundledChineseOcrRunsLocally() throws Exception {
    Bitmap b = Bitmap.createBitmap(1000, 260, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(b);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(3);
    paint.setColor(Color.BLACK);
    paint.setTextSize(70);
    canvas.drawText("你好，豆豆输入法", 40, 130, paint);
    CountDownLatch done = new CountDownLatch(1);
    String[] result = new String[2];
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                LocalOcr.read(
                    b,
                    (text, error) -> {
                      result[0] = text;
                      result[1] = error;
                      done.countDown();
                    }));
    assertTrue(done.await(25, TimeUnit.SECONDS));
    assertNull(result[1]);
    assertTrue(result[0], result[0].contains("豆豆"));
    assertTrue(b.isRecycled());
  }
}
