package app.doudou.ime.memory;

import app.doudou.ime.app.KeyboardApp;
import java.util.*;
import org.json.*;

/** Canonical edits are acknowledged by Cloud before the local cache is replaced. */
public final class CloudMemory {
  private final KeyboardApp app;
  public final java.util.concurrent.CopyOnWriteArraySet<Runnable> observers =
      new java.util.concurrent.CopyOnWriteArraySet<>();

  public boolean pending() {
    try {
      String state =
          new JSONObject(app.store.meta("memory.status:" + app.cloud.userId(), "{}"))
              .optString("state");
      return java.util.Arrays.asList("queued", "running", "waiting").contains(state);
    } catch (Exception e) {
      return false;
    }
  }

  public CloudMemory(KeyboardApp app) {
    this.app = app;
  }

  public synchronized void activate() throws Exception {
    String id = app.cloud.userId();
    app.store.cache(id, app.store.cacheVersion(id), app.store.cache(id));
  }

  private void sameAccount(String id) {
    if (!id.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化，请重试");
  }

  public synchronized void sync() throws Exception {
    if (!app.cloud.loggedIn()) return;
    String account = app.cloud.userId();
    JSONObject response =
        app.cloud.request(
            account, "/v1/memory?after=" + app.store.cacheVersion(account), "GET", null);
    sameAccount(account);
    apply(account, response);
    importLegacy(account);
  }

  private void apply(String account, JSONObject response) throws Exception {
    JSONObject memory;
    if (response.optBoolean("reset")) memory = response.getJSONObject("memory");
    else {
      memory = app.store.cache(account);
      JSONArray changes = response.getJSONArray("changes");
      for (int i = 0; i < changes.length(); i++) {
        JSONObject change = changes.getJSONObject(i);
        String group = change.getString("group");
        if (!Arrays.asList("hotwords", "styles", "people").contains(group))
          throw new JSONException("未知记忆类别");
        JSONArray old = memory.getJSONArray(group), next = new JSONArray();
        String id =
            change.optBoolean("deleted")
                ? change.getString("id")
                : change.getJSONObject("record").getString("id");
        for (int j = 0; j < old.length(); j++)
          if (!old.getJSONObject(j).getString("id").equals(id)) next.put(old.getJSONObject(j));
        if (!change.optBoolean("deleted")) next.put(change.getJSONObject("record"));
        memory.put(group, next);
      }
      memory.put("candidates", response.getJSONArray("candidates"));
      memory.put("origins", response.getJSONObject("origins"));
      memory.put("protected", response.getJSONArray("protected"));
    }
    sameAccount(account);
    long oldVersion = app.store.cacheVersion(account);
    app.store.cache(account, response.getLong("version"), memory);
    if (oldVersion != response.getLong("version"))
      for (Runnable observer : observers)
        new android.os.Handler(android.os.Looper.getMainLooper()).post(observer);
    JSONObject status = response.optJSONObject("status");
    if (status != null) app.store.setMeta("memory.status:" + account, status.toString());
  }

  public synchronized void expect(String account, long version) {
    sameAccount(account);
    if (app.store.cacheVersion(account) != version)
      throw new IllegalStateException("资料已更新，请刷新后再修改");
  }

  public synchronized void mutate(String account, long baseVersion, JSONArray operations) throws Exception {
    expect(account, baseVersion);
    change(account, new JSONObject().put("baseVersion", baseVersion).put("operations", operations));
  }

  public synchronized void candidate(String account, long version, String id, boolean accept) throws Exception {
    expect(account, version);
    change(account,
        new JSONObject()
            .put("baseVersion", version)
            .put("candidateId", id)
            .put("accept", accept));
  }

  private void change(String account, JSONObject input) throws Exception {
    sameAccount(account);
    if (!app.cloud.loggedIn()) throw new IllegalStateException("登录后可修改云端个人记忆");
    String key = "memory:" + account + ":" + digest(input.toString());
    input.put("requestId", app.cloud.requestId(key));
    JSONObject response = app.cloud.request(account, "/v1/memory", "POST", input);
    sameAccount(account);
    apply(account, response);
    app.cloud.completeRequest(key);
  }

  public JSONObject operation(
      MemoryStore.Item item, String kind, long owner, String title, String body) throws Exception {
    String type =
        kind.equals("word")
            ? "hotword"
            : kind.equals("memory")
                ? (item != null && item.aux.startsWith("relation:") ? "relation" : "fact")
            : kind.equals("relationship") ? "relation" : kind;
    JSONObject value = new JSONObject();
    if (type.equals("hotword")) value.put("text", title.trim()).put("variants", new JSONArray());
    if (type.equals("style"))
      value.put("scene", title.trim()).put("rules", lines(body)).put("examples", new JSONArray());
    if (type.equals("person")) value.put("name", title.trim()).put("aliases", lines(body));
    if (type.equals("fact"))
      value
          .put("key", title.trim())
          .put("value", body.trim())
          .put("scope", item == null || item.aux.isEmpty() ? "当前" : item.aux);
    if (type.equals("relation"))
      value.put("targetId", item == null ? "self" : item.aux.substring("relation:".length())).put("type", body.trim());
    JSONObject op =
        new JSONObject()
            .put("kind", type)
            .put("action", "upsert")
            .put(
                "id",
                item == null
                    ? "new:" + app.cloud.requestId("draft:" + kind + ":" + owner)
                    : item.globalId)
            .put("value", value);
    if (type.equals("fact") || type.equals("relation")) {
      MemoryStore.Item person = app.store.get(owner);
      if (person == null) throw new IllegalStateException("人物不存在");
      op.put("personId", person.globalId);
    }
    // Editing a record preserves its fields that are not exposed by this small editor.
    if (item != null) {
      JSONObject doc = app.store.cache(app.cloud.userId());
      JSONArray rows =
          doc.getJSONArray(
              type.equals("hotword") ? "hotwords" : type.equals("style") ? "styles" : "people");
      if (type.equals("hotword") || type.equals("style") || type.equals("person"))
        for (int i = 0; i < rows.length(); i++)
          if (rows.getJSONObject(i).getString("id").equals(item.globalId)) {
            JSONObject original = rows.getJSONObject(i);
            if (type.equals("hotword")) {
              value.put("variants", original.getJSONArray("variants"));
              if (original.optJSONObject("lexical") != null)
                value.put("lexical", original.getJSONObject("lexical"));
            }
            else if (type.equals("style")) value.put("examples", original.getJSONArray("examples"));
            else
              value.put(
                  "identities",
                  original.optJSONArray("identities") == null
                      ? new JSONArray()
                      : original.getJSONArray("identities"));
          }
    }
    return op;
  }

  public JSONObject deletion(MemoryStore.Item item) throws Exception {
    String type =
        item.kind.equals("word")
            ? "hotword"
            : item.kind.equals("memory")
                ? (item.aux.startsWith("relation:") ? "relation" : "fact")
                : item.kind;
    JSONObject op =
        new JSONObject().put("kind", type).put("action", "delete").put("id", item.globalId);
    if (type.equals("fact") || type.equals("relation"))
      op.put("personId", app.store.get(item.owner).globalId);
    return op;
  }

  public static final class Removal {
    public final long version;
    public final JSONObject restore;
    private Removal(long version, JSONObject restore) { this.version = version; this.restore = restore; }
  }

  public synchronized Removal remove(String account, MemoryStore.Item item) throws Exception {
    sameAccount(account); sync(); sameAccount(account);
    MemoryStore.Item fresh = app.store.get(item.id);
    if (fresh == null || fresh.deleted || !fresh.globalId.equals(item.globalId)
        || !fresh.title.equals(item.title) || !fresh.body.equals(item.body) || !fresh.aux.equals(item.aux))
      throw new IllegalStateException("资料已更新，请重新查看后再删除");
    JSONObject restore = fresh.kind.equals("word") || fresh.kind.equals("style")
        ? operation(fresh, fresh.kind, fresh.owner, fresh.title, fresh.body) : null;
    mutate(account, app.store.cacheVersion(account), new JSONArray().put(deletion(fresh)));
    return new Removal(app.store.cacheVersion(account), restore);
  }

  private JSONArray lines(String text) {
    JSONArray out = new JSONArray();
    for (String line : text.split("\n")) if (!line.trim().isEmpty()) out.put(line.trim());
    return out;
  }

  private String digest(String value) throws Exception {
    byte[] bytes =
        java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
    return out.toString();
  }

  private void importLegacy(String account) throws Exception {
    String saved = app.store.meta("profile.migration", ""),
        owner = app.store.meta("profile.migration.owner", "");
    if (saved.isEmpty() || (!owner.isEmpty() && !owner.equals(account))) return;
    app.store.setMeta("profile.migration.owner", account);
    JSONArray old = new JSONObject(saved).getJSONArray("items"), ops = new JSONArray();
    Map<Long, String> people = new HashMap<>();
    JSONObject current = app.store.cache(account);
    Set<String> existing = new HashSet<>();
    for (String group : new String[] {"hotwords", "styles", "people"})
      for (int i = 0; i < current.getJSONArray(group).length(); i++)
        existing.add(current.getJSONArray(group).getJSONObject(i).getString("id"));
    for (int i = 0; i < current.getJSONArray("people").length(); i++)
      for (String group : new String[] {"facts", "relations"}) {
        JSONArray nested = current.getJSONArray("people").getJSONObject(i).getJSONArray(group);
        for (int j = 0; j < nested.length(); j++)
          existing.add(nested.getJSONObject(j).getString("id"));
      }
    for (int i = 0; i < old.length(); i++) {
      JSONObject item = old.getJSONObject(i);
      if (item.optString("kind").equals("person") && !item.optBoolean("deleted"))
        people.put(item.getLong("id"), item.getString("globalId"));
    }
    for (String kind : new String[] {"person", "word", "style", "memory"})
      for (int i = 0; i < old.length(); i++) {
        JSONObject item = old.getJSONObject(i);
        if (!item.optString("kind").equals(kind)
            || item.optBoolean("deleted")
            || existing.contains(item.getString("globalId"))) continue;
        JSONObject value = new JSONObject(),
            op = new JSONObject().put("id", item.getString("globalId")).put("action", "upsert");
        if (kind.equals("person"))
          value.put("name", item.getString("title")).put("aliases", new JSONArray());
        if (kind.equals("word")) {
          value.put("text", item.getString("title")).put("variants", new JSONArray());
          app.store.setMeta("word.code:" + item.getString("globalId"), item.optString("body"));
        }
        if (kind.equals("style"))
          value
              .put("scene", item.getString("title"))
              .put("rules", lines(item.optString("body")))
              .put("examples", new JSONArray());
        if (kind.equals("memory")) {
          String person = people.get(item.optLong("owner"));
          if (person == null) continue;
          op.put("personId", person);
          value
              .put("key", item.getString("title"))
              .put("value", item.optString("body"))
              .put("scope", "导入");
        }
        op.put("kind", kind.equals("word") ? "hotword" : kind.equals("memory") ? "fact" : kind)
            .put("value", value);
        ops.put(op);
        if (ops.length() == 40) {
          mutate(account, app.store.cacheVersion(account), ops);
          ops = new JSONArray();
        }
      }
    if (ops.length() > 0) mutate(account, app.store.cacheVersion(account), ops);
    // v0.4 used the person body for notes; preserve it as an explicit fact, never aliases.
    ops = new JSONArray();
    for (int i = 0; i < old.length(); i++) {
      JSONObject item = old.getJSONObject(i);
      if (!item.optString("kind").equals("person")
          || item.optBoolean("deleted")
          || item.optString("body").isEmpty()) continue;
      String noteId =
          UUID.nameUUIDFromBytes(
                  ("v04-person-note:" + item.getString("globalId")).getBytes("UTF-8"))
              .toString();
      if (existing.contains(noteId)) continue;
      ops.put(
          new JSONObject()
              .put("kind", "fact")
              .put("action", "upsert")
              .put("id", noteId)
              .put("personId", item.getString("globalId"))
              .put(
                  "value",
                  new JSONObject()
                      .put("key", "备注")
                      .put("value", item.getString("body"))
                      .put("scope", "导入")));
      if (ops.length() == 40) {
        mutate(account, app.store.cacheVersion(account), ops);
        ops = new JSONArray();
      }
    }
    if (ops.length() > 0) mutate(account, app.store.cacheVersion(account), ops);
    app.store.setMeta("profile.migration", "");
  }
}
