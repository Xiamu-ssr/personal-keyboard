package app.doudou.ime;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

public class DeviceTest {
  Context c;
  Store s;
  String db;

  @Before
  public void before() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    db = "test-" + System.nanoTime() + ".db";
    s = new Store(c, db);
  }

  @After
  public void after() {
    s.close();
    c.deleteDatabase(db);
  }

  @Test
  public void persistsAndRejectsStaleRevision() {
    long id = s.save(0, 0, "word", 0, "豆豆", "doudou", "", "manual");
    Store.Item old = s.get(id);
    s.save(id, old.revision, "word", 0, "豆豆输入法", "doudoushurufa", "", "manual");
    boolean rejected = false;
    try {
      s.save(id, old.revision, "word", 0, "旧值", "", "", "manual");
    } catch (IllegalStateException e) {
      rejected = true;
    }
    assertTrue(rejected);
    s.close();
    s = new Store(c, db);
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
    for (Store.Item person : s.list("person", 0))
      assertEquals(1, s.list("memory", person.id).size());
    assertEquals("本机资料", s.get(p).body);
  }

  @Test
  public void invalidBackupRollsBack() throws Exception {
    JSONObject doc =
        new JSONObject()
            .put("schema", 1)
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
