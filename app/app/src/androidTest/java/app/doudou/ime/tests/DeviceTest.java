package app.doudou.ime.tests;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.history.HistoryStore;
import app.doudou.ime.memory.MemoryStore;
import java.util.*;
import org.json.*;
import org.junit.*;

public class DeviceTest {
  Context c;
  MemoryStore s;
  HistoryStore h;
  String db, historyDb;

  @Before
  public void before() throws Exception {
    Assume.assumeTrue("Owned emulator only", "ranchu".equals(android.os.Build.HARDWARE));
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    app.doudou.ime.app.KeyboardApp app = (app.doudou.ime.app.KeyboardApp) c.getApplicationContext();
    if (app.cloud.loggedIn()) app.cloud.logout();
    app.personalization.activate();
    db = "test-" + System.nanoTime() + ".db";
    historyDb = db + "-history";
    s = new MemoryStore(c, db);
    h = new HistoryStore(c, historyDb);
  }

  @After
  public void after() {
    h.clear();
    h.close();
    s.close();
    c.deleteDatabase(db);
    c.deleteDatabase(historyDb);
  }

  private JSONObject memory() throws Exception {
    return new JSONObject("{\"hotwords\":[],\"styles\":[],\"people\":[],\"candidates\":[]}");
  }

  private JSONObject word(String id, String text) throws Exception {
    return new JSONObject().put("id", id).put("text", text).put("variants", new JSONArray());
  }

  @Test
  public void cloudCacheKeepsStableLocalIdsAndSeparatesAccounts() throws Exception {
    String id = UUID.randomUUID().toString();
    JSONObject m = memory();
    m.getJSONArray("hotwords").put(word(id, "豆豆输入法"));
    s.cache("a", 1, m);
    long localId = s.list("word", 0).get(0).id;
    assertEquals("doudoushurufa", s.get(localId).body);
    s.cache("a", 2, m);
    assertEquals(localId, s.list("word", 0).get(0).id);
    s.cache("b", 0, memory());
    assertTrue(s.list("word", 0).isEmpty());
    s.cache("a", s.cacheVersion("a"), s.cache("a"));
    assertEquals("豆豆输入法", s.list("word", 0).get(0).title);
  }

  @Test
  public void invalidCacheRollsBackAndPreservesPreviousVersion() throws Exception {
    JSONObject m = memory();
    m.getJSONArray("hotwords").put(word(UUID.randomUUID().toString(), "豆豆"));
    s.cache("a", 1, m);
    JSONObject bad = memory();
    bad.getJSONArray("people").put(new JSONObject().put("id", "missing-name"));
    try {
      s.cache("a", 2, bad);
      fail();
    } catch (JSONException expected) {
    }
    assertEquals(1, s.cacheVersion("a"));
    assertEquals("豆豆", s.list("word", 0).get(0).title);
  }

  @Test
  public void changedHistoryUsesOneRecordAndUploadAcknowledgementCannotEatLaterEdits()
      throws Exception {
    String id = h.create("voice", "豆包");
    h.inserted(id, "豆包", "chat:person");
    assertEquals("豆包", h.get(id).resultText);
    List<HistoryStore.Entry> batch = h.pending("account-a");
    h.observed(id, "豆豆");
    h.accepted("account-a", batch);
    assertEquals("豆包", h.get(id).asrText);
    assertEquals("豆包", h.get(id).insertedText);
    assertEquals("豆豆", h.get(id).resultText);
    assertEquals(1, h.pending("account-a").size());
    assertTrue(h.pending("account-b").isEmpty());
    h.accepted("account-a", h.pending("account-a"));
    assertTrue(h.pending("account-a").isEmpty());
    h.remove(id);
    assertTrue(h.get(id).deleted);
    assertEquals("", h.get(id).resultText);
    assertEquals(1, h.pending("account-a").size());
  }

  @Test
  public void keyboardHistoryFiltersUnadoptedRowsBeforeItsLimit() {
    String used = h.create("voice", "原文");
    h.inserted(used, "更早的有效结果", "");
    for (int i = 0; i < 60; i++) {
      String id = h.create("voice", "未采用 " + i);
      if (i % 2 == 0) {
        h.inserted(id, "撤回 " + i, "");
        h.withdraw(id);
      }
    }
    assertEquals(50, h.list(50).size());
    assertEquals(1, h.reusable(50).size());
    assertEquals(used, h.reusable(50).get(0).id);
    assertEquals("更早的有效结果", h.reusable(50).get(0).resultText);
  }

  @Test
  public void clearDoesNotMeanSentAndUndoRemovesTheInsertedResult() {
    String id = h.create("voice", "你好");
    h.inserted(id, "你好", "");
    h.observed(id, "");
    assertEquals("你好", h.get(id).resultText);
    h.withdraw(id);
    assertEquals("", h.get(id).resultText);
    assertEquals("你好", h.get(id).asrText);
  }

  @Test
  public void screenshotsStayLocalAndBackupIsIdempotent() throws Exception {
    String id = h.create("advisor", "");
    Bitmap b = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
    String ref;
    try {
      ref = h.screenshot(id, b, false);
    } finally {
      b.recycle();
    }
    h.inserted(id, "下次见", "chat:person");
    assertFalse(h.get(id).increment().has("screenshots"));
    JSONObject backup = h.backup(100000, "");
    String otherDb = db + "-restore";
    HistoryStore other = new HistoryStore(c, otherDb);
    try {
      other.restore(backup, "");
      other.observed(id, "周末见");
      other.restore(backup, "");
      assertEquals("周末见", other.get(id).resultText);
      assertTrue(other.imageFile(ref).exists());
      try {
        h.backup(8, "");
        fail();
      } catch (java.io.IOException expected) {
      }
    } finally {
      other.close();
      c.deleteDatabase(otherDb);
    }
  }

  private String jpeg() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    try {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
    } finally {
      bitmap.recycle();
    }
    return "data:image/jpeg;base64,"
        + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
  }

  @Test
  public void advisorSaveFailureLeavesNeitherEmptyHistoryNorPartialScreenshots() throws Exception {
    java.io.File folder = new java.io.File(c.getFilesDir(), "history-screenshots");
    Set<String> before =
        new HashSet<>(Arrays.asList(folder.list() == null ? new String[0] : folder.list()));
    try {
      h.saveAdvisor(
          "", new JSONObject().put("replies", new JSONArray()), Arrays.asList(jpeg()), "account-a");
      fail("Missing messages must fail before publishing a history");
    } catch (JSONException expected) {
    }
    assertTrue(h.list(50).isEmpty());
    assertEquals(
        before,
        new HashSet<>(Arrays.asList(folder.list() == null ? new String[0] : folder.list())));
  }

  @Test
  public void advisorSaveKeepsOriginalScreenshotsAndEveryAnswerGroup() throws Exception {
    JSONObject output =
        new JSONObject()
            .put("messages", new JSONArray())
            .put("replies", new JSONArray().put(new JSONObject().put("text", "周末见")));
    String id = h.saveAdvisor("", output, Arrays.asList(jpeg(), jpeg()), "account-a");
    assertNotNull(h.get(id));
    JSONArray refs = h.get(id).screenshots;
    assertEquals(2, refs.length());
    assertTrue(h.imageFile(refs.getString(0)).isFile());
    assertEquals(id, h.saveAdvisor(id, output, Arrays.asList(jpeg()), "account-a"));
    assertEquals(refs.toString(), h.get(id).screenshots.toString());
    assertEquals(2, h.get(id).replyGroups.length());
    try {
      h.saveAdvisor(id, output, Collections.emptyList(), "account-b");
      fail();
    } catch (java.io.IOException expected) {
    }
    assertEquals(2, h.get(id).replyGroups.length());
  }

  @Test
  public void recentContextUsesOnlyOwnLastResultInTheSameConversation() {
    String a = h.create("voice", "错误原文");
    h.inserted(a, "初稿", "same");
    h.observed(a, "修改后的结果");
    String b = h.create("voice", "别人的上下文");
    h.inserted(b, "其他聊天", "different");
    assertEquals("修改后的结果", h.recent("same", 400));
    assertEquals("", h.recent("", 400));
  }

  @Test
  public void migratesV1BeforeRemovingTheOldLearningPath() throws Exception {
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
        "INSERT INTO items(kind,title,body,source,created,updated)"
            + " VALUES('history','语音','保留原资料','asr:legacy',1,1)");
    old.setVersion(1);
    old.close();
    MemoryStore upgraded = new MemoryStore(c, name);
    try {
      JSONObject doc = upgraded.exportData();
      h.migrateLegacy(doc, "");
      h.migrateLegacy(doc, "");
      assertEquals("保留原资料", h.get(upgraded.get(1).globalId).asrText);
      upgraded.discardLegacy();
      assertTrue(upgraded.list("history", 0).isEmpty());
    } finally {
      upgraded.close();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void wrongAccountRequestStopsBeforeNetworkOrCredentials() throws Exception {
    app.doudou.ime.cloud.CloudClient client = new app.doudou.ime.cloud.CloudClient(c);
    try {
      client.request("different-account", "/v1/memory/history", "POST", new JSONObject());
      fail("Cross-account request accepted");
    } catch (app.doudou.ime.cloud.CloudClient.CloudError error) {
      assertEquals("ACCOUNT_CHANGED", error.code);
    }
  }

  @Test
  public void configuredIpUsesNormalCertificateValidation() throws Exception {
    JSONObject health = new app.doudou.ime.cloud.CloudClient(c).publicRequest("/healthz", null);
    assertEquals("ok", health.getString("status"));
  }

  @Test
  public void invitedAccountUsesRealCloudAndDoesNotStoreRecoveryCode() throws Exception {
    java.io.File input = new java.io.File(c.getCacheDir(), "device-account-test.json");
    Assume.assumeTrue("Explicit disposable test invitation required", input.isFile());
    assertTrue(
        android.os.Build.HARDWARE.equals("ranchu") || android.os.Build.HARDWARE.equals("goldfish"));
    JSONObject data;
    try (java.io.FileInputStream stream = new java.io.FileInputStream(input)) {
      byte[] bytes = new byte[(int) input.length()];
      assertEquals(bytes.length, stream.read(bytes));
      data = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
    input.delete();
    app.doudou.ime.cloud.CloudClient client = new app.doudou.ime.cloud.CloudClient(c);
    assertFalse("Never replace a real user's session", client.loggedIn());
    String name = data.getString("handle"), password = data.getString("password");
    try {
      String recovery = client.register(name, password, data.getString("invitation"));
      String id = client.userId();
      assertTrue(recovery.startsWith("DDR-"));
      assertFalse(client.secrets().get("session").contains(recovery));
      assertEquals(
          10000,
          client.account().getJSONObject("entitlements").getJSONObject("asr").getLong("available"));
      client.logout();
      client.login(name, password);
      assertEquals(id, client.userId());
      assertEquals(
          20,
          client
              .account()
              .getJSONObject("entitlements")
              .getJSONObject("advisor")
              .getInt("available"));
      client.changePassword(password, password + "-changed");
      client.logout();
      String rotated = client.recover(name, password, recovery);
      assertNotEquals(recovery, rotated);
      assertEquals(id, client.userId());
      assertFalse(client.secrets().get("session").contains(rotated));
      client.reauthenticate(password);
    } finally {
      if (client.loggedIn())
        client.request(
            client.userId(), "/v1/account/delete", "POST", new JSONObject().put("confirm", true));
      client.clearSession();
    }
  }
}
