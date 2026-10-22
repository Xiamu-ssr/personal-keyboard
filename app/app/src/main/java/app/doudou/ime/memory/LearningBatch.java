package app.doudou.ime.memory;

import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.history.HistoryStore;
import java.util.*;
import org.json.*;

/** Upload receipts and cache synchronization only. Cloud owns inference and commits. */
public final class LearningBatch {
  private final KeyboardApp app;

  public LearningBatch(KeyboardApp a) {
    app = a;
  }

  public String run() throws Exception {
    if (!app.cloud.loggedIn()) return "登录后同步个人记忆；历史保留在本机";
    String account = app.cloud.userId();
    int rounds = 0;
    List<HistoryStore.Entry> batch;
    while (!(batch = app.histories.pending(account)).isEmpty() && rounds++ < 8) {
      JSONArray events = new JSONArray();
      for (HistoryStore.Entry e : batch) events.put(e.increment());
      JSONObject response =
          app.cloud.request(
              account, "/v1/memory/history", "POST", new JSONObject().put("events", events));
      if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
      JSONArray accepted = response.getJSONArray("accepted");
      Set<String> receipts = new HashSet<>();
      for (int i = 0; i < accepted.length(); i++)
        receipts.add(
            accepted.getJSONObject(i).getString("id")
                + ":"
                + accepted.getJSONObject(i).getInt("revision"));
      for (HistoryStore.Entry e : batch)
        if (!receipts.contains(e.id + ":" + e.revision))
          throw new IllegalStateException("云端尚未确认这批历史");
      app.histories.accepted(account, batch);
    }
    app.personalization.sync();
    if (!app.histories.pending(account).isEmpty()) throw new IllegalStateException("历史增量仍在等待上传");
    return "新增历史已交给云端整理，个人记忆缓存已同步";
  }
}
