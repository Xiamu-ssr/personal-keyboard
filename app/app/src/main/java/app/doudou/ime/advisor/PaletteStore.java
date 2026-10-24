package app.doudou.ime.advisor;

import app.doudou.ime.app.KeyboardApp;
import java.util.UUID;
import org.json.*;

/** User-configured expressions are distinct from automatically learned scene styles. */
public final class PaletteStore {
  public static final String[] IDS = {
    "natural", "caring", "direct", "playful", "considered", "witty"
  };
  public static final String[] NAMES = {"自然", "体贴", "直说", "俏皮", "周全", "机智"};
  public static final int[] COLORS = {
    0xffa9c4ff, 0xffe6b5a6, 0xffa7bce3, 0xffe3c77d, 0xffbaaada, 0xff99c5d1
  };
  private final KeyboardApp app;

  public PaletteStore(KeyboardApp a) {
    app = a;
  }

  public JSONObject cached() {
    try {
      return new JSONObject(app.store.meta("palette:" + app.cloud.userId(), ""));
    } catch (Exception e) {
      try {
        JSONArray slots = new JSONArray();
        for (int i = 0; i < 6; i++)
          slots.put(
              new JSONObject()
                  .put("id", "slot" + (i + 1))
                  .put("name", NAMES[i])
                  .put("expression", IDS[i]));
        return new JSONObject().put("version", 0).put("slots", slots);
      } catch (Exception impossible) {
        throw new IllegalStateException(impossible);
      }
    }
  }

  public synchronized JSONObject sync() throws Exception {
    String account = app.cloud.userId();
    if (!app.cloud.loggedIn()) return cached();
    JSONObject value = app.cloud.request(account, "/v1/advisor/palette", "GET", null);
    if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
    app.store.setMeta("palette:" + account, value.toString());
    return value;
  }

  public synchronized void save(JSONObject old, int index, String name, String expression)
      throws Exception {
    String account = app.cloud.userId();
    if (!app.cloud.loggedIn()) throw new IllegalStateException("请先登录");
    JSONObject value = new JSONObject(old.toString());
    value
        .getJSONArray("slots")
        .getJSONObject(index)
        .put("name", name)
        .put("expression", expression);
    JSONObject body =
        new JSONObject()
            .put("baseVersion", value.getLong("version"))
            .put("slots", value.getJSONArray("slots"))
            .put("requestId", UUID.randomUUID().toString());
    JSONObject result = app.cloud.request(account, "/v1/advisor/palette", "POST", body);
    if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
    app.store.setMeta("palette:" + account, result.toString());
  }
}
