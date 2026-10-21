package app.doudou.ime.memory;

import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.cloud.CloudTask;
import java.util.*;
import org.json.*;

/**
 * Incremental, bounded extraction. Suggestions are evidence-linked additions, never directory
 * replacements.
 */
public final class LearningBatch {
  private final KeyboardApp app;

  public LearningBatch(KeyboardApp a) {
    app = a;
  }

  public String run() throws Exception {
    if (!app.cloud.loggedIn()) return "登录后才能整理；本机历史保留";
    List<MemoryStore.Item> batch = app.store.unprocessed(12, 8000);
    if (batch.isEmpty()) return "没有新的历史需要整理";
    JSONArray events = new JSONArray();
    for (MemoryStore.Item i : batch) {
      String subject = "", state = i.aux.isEmpty() ? "unconfirmed" : i.aux;
      if (i.source.startsWith("advisor-context:"))
        subject = i.source.substring("advisor-context:".length());
      events.put(
          new JSONObject()
              .put("id", i.globalId)
              .put("text", app.store.learningFragment(i))
              .put("kind", i.kind)
              .put("subject", subject)
              .put("state", state));
    }
    JSONArray memories = new JSONArray();
    int length = 0;
    for (String kind : new String[] {"word", "style"})
      for (MemoryStore.Item m : app.store.list(kind, 0)) {
        if (memories.length() >= 15 || length > 4000) break;
        String value = m.title + " " + m.body;
        value = value.substring(0, Math.min(250, value.length()));
        length += value.length();
        memories.put(
            new JSONObject()
                .put("id", m.globalId)
                .put("content", value)
                .put("revision", m.revision)
                .put("scope", "self"));
      }
    JSONObject input = new JSONObject().put("events", events).put("memories", memories);
    JSONObject result = new CloudTask(app.cloud).run("memory", input, null);
    JSONArray proposals = result.getJSONArray("proposals");
    Set<String> allowed = new HashSet<>();
    for (MemoryStore.Item i : batch) allowed.add(i.globalId);
    synchronized (app.store) {
      for (int i = 0; i < proposals.length(); i++) {
        JSONObject p = proposals.getJSONObject(i);
        if (p.optString("category").equals("ignore")) continue;
        boolean valid = p.optInt("baseRevision", -1) == 0;
        JSONArray ids = p.getJSONArray("sourceIds");
        for (int j = 0; j < ids.length(); j++) {
          MemoryStore.Item source = app.store.byGlobal(ids.getString(j));
          if (!allowed.contains(ids.getString(j)) || source == null || source.deleted)
            valid = false;
        }
        if (!valid) continue;
        JSONArray evidence = new JSONArray();
        for (MemoryStore.Item original : batch)
          for (int k = 0; k < ids.length(); k++)
            if (original.globalId.equals(ids.getString(k)))
              evidence.put(
                  new JSONObject()
                      .put("id", original.globalId)
                      .put("revision", original.revision)
                      .put("start", original.learningOffset)
                      .put(
                          "end",
                          original.learningOffset + app.store.learningFragment(original).length()));
        p.put("evidence", evidence);
        String title = p.getString("content");
        app.store.save(
            0,
            0,
            "proposal",
            0,
            title.substring(0, Math.min(30, title.length())),
            p.toString(),
            p.getString("category"),
            "cloud-learning");
      }
      app.store.markProcessed(batch);
    }
    return "新增历史已整理，候选可在记忆 → 待学习确认";
  }
}
