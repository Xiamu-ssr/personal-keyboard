package app.doudou.ime.advisor;

import app.doudou.ime.app.KeyboardApp;
import java.util.UUID;
import org.json.*;

/** User-configured expressions are distinct from automatically learned scene styles. */
public final class PaletteStore {
  public static final int[] COLORS = {
    0xffa9c4ff, 0xffe6b5a6, 0xffa7bce3, 0xffe3c77d, 0xffbaaada, 0xff99c5d1
  };
  private final KeyboardApp app;

  public PaletteStore(KeyboardApp a) {
    app = a;
  }

  public JSONObject cached() {
    try {
      String key = "palette:" + app.cloud.userId(), raw = app.store.meta(key, "");
      if (raw.isEmpty()) return PaletteDefinition.defaults(app);
      JSONObject value = new JSONObject(raw);
      if (value.optInt("schemaVersion") != 2) {
        value = PaletteDefinition.migrateCache(app, value);
        app.store.setMeta(key, value.toString());
      }
      PaletteDefinition.validate(app, value);
      return value;
    } catch (Exception e) {
      throw new IllegalStateException("调色盘需要重新同步", e);
    }
  }

  public synchronized JSONObject sync() throws Exception {
    String account = app.cloud.userId();
    if (!app.cloud.loggedIn()) return cached();
    JSONObject value = app.cloud.request(account, "/v1/advisor/palette", "GET", null);
    if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
    PaletteDefinition.validate(app, value);
    app.store.setMeta("palette:" + account, value.toString());
    return value;
  }

  public synchronized void save(String account, JSONObject old, int index, String name, JSONObject attributes)
      throws Exception {
    if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
    if (!app.cloud.loggedIn()) throw new IllegalStateException("请先登录");
    JSONObject value = new JSONObject(old.toString());
    value
        .getJSONArray("slots")
        .getJSONObject(index)
        .put("name", name)
        .put("attributes", PaletteDefinition.attributes(app, attributes));
    JSONObject body =
        new JSONObject()
            .put("baseVersion", value.getLong("version"))
            .put("slots", value.getJSONArray("slots"))
            .put("requestId", UUID.randomUUID().toString());
    JSONObject result = app.cloud.request(account, "/v1/advisor/palette", "POST", body);
    if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
    PaletteDefinition.validate(app, result);
    app.store.setMeta("palette:" + account, result.toString());
  }
}
