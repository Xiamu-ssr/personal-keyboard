package app.doudou.ime.cloud;

import android.content.*;
import app.doudou.ime.BuildConfig;
import app.doudou.ime.security.SecretStore;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** The only HTTP boundary in the product App. Supplier credentials never reach Android. */
public final class CloudClient {
  public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  public final OkHttpClient http =
      new OkHttpClient.Builder()
          .connectTimeout(12, TimeUnit.SECONDS)
          .readTimeout(75, TimeUnit.SECONDS)
          .callTimeout(80, TimeUnit.SECONDS)
          .build();
  private final SecretStore vault;
  private final SharedPreferences prefs;
  private volatile JSONObject session = new JSONObject();

  public CloudClient(Context context) {
    vault = new SecretStore(context);
    prefs = context.getSharedPreferences("cloud", 0);
    try {
      String s = vault.get("session");
      if (!s.isEmpty()) session = new JSONObject(s);
    } catch (Exception e) {
      prefs.edit().clear().apply();
    }
  }

  public boolean loggedIn() {
    return session.has("refreshToken");
  }

  public String userId() {
    return session.optJSONObject("user") == null
        ? ""
        : session.optJSONObject("user").optString("id");
  }

  public String phoneHint() {
    return session.optJSONObject("user") == null
        ? ""
        : session.optJSONObject("user").optString("phoneHint");
  }

  public static final class CloudError extends IOException {
    public final String code;
    public final int status;

    public CloudError(String code, String message, int status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  public JSONObject publicRequest(String route, JSONObject body) throws Exception {
    return execute(route, body == null ? "GET" : "POST", body, null);
  }

  private JSONObject execute(String route, String method, JSONObject body, String token)
      throws Exception {
    Request.Builder builder =
        new Request.Builder()
            .url(BuildConfig.API_BASE_URL + route)
            .header("Accept", "application/json");
    if (token != null) builder.header("Authorization", "Bearer " + token);
    builder.method(
        method,
        method.equals("GET")
            ? null
            : RequestBody.create(body == null ? "{}" : body.toString(), JSON));
    try (Response response = http.newCall(builder.build()).execute()) {
      if (response.body() == null) throw new IOException("云端没有返回结果");
      String content = response.body().string();
      if (content.length() > 7500000) throw new IOException("云端返回过大");
      JSONObject result;
      try {
        result = new JSONObject(content);
      } catch (JSONException e) {
        throw new IOException("云服务返回格式不正确");
      }
      if (!response.isSuccessful()) {
        JSONObject error = result.optJSONObject("error");
        throw new CloudError(
            error == null ? "NETWORK" : error.optString("code"),
            error == null ? "服务暂不可用" : error.optString("message", "服务暂不可用"),
            response.code());
      }
      return result;
    }
  }

  public synchronized void login(String challenge, String code) throws Exception {
    JSONObject next =
        publicRequest(
            "/v1/auth/verify", new JSONObject().put("challengeId", challenge).put("code", code));
    if (!userId().equals(next.getJSONObject("user").getString("id")))
      prefs.edit().remove("syncEnabled").remove("account").apply();
    vault.set("session", next.toString());
    session = next;
  }

  public synchronized String accessToken() throws Exception {
    if (!loggedIn()) throw new CloudError("LOGIN_REQUIRED", "请先到设置登录豆豆账户", 401);
    if (session.optLong("accessExpiresAt") > System.currentTimeMillis() + 60000)
      return session.getString("accessToken");
    try {
      JSONObject next =
          publicRequest(
              "/v1/auth/refresh",
              new JSONObject().put("refreshToken", session.getString("refreshToken")));
      vault.set("session", next.toString());
      session = next;
      return next.getString("accessToken");
    } catch (CloudError e) {
      if (e.status == 401) clearSession();
      throw e;
    }
  }

  public JSONObject request(String route, String method, JSONObject body) throws Exception {
    return execute(route, method, body, accessToken());
  }

  public JSONObject account() throws Exception {
    JSONObject result = request("/v1/account", "GET", null);
    prefs.edit().putString("account", result.toString()).apply();
    return result;
  }

  public JSONObject cachedAccount() {
    try {
      return new JSONObject(prefs.getString("account", "{}"));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public synchronized void clearSession() throws Exception {
    String id = userId();
    vault.set("session", "");
    if (!id.isEmpty()) vault.set("sync:" + id, "");
    session = new JSONObject();
    prefs.edit().remove("account").putBoolean("syncEnabled", false).apply();
  }

  public void logout() throws Exception {
    try {
      request("/v1/auth/logout", "POST", new JSONObject());
    } finally {
      clearSession();
    }
  }

  public SharedPreferences preferences() {
    return prefs;
  }

  public SecretStore secrets() {
    return vault;
  }

  public synchronized String requestId(String key) {
    String value = prefs.getString("pending:" + key, "");
    if (value.isEmpty()) {
      value = UUID.randomUUID().toString();
      prefs.edit().putString("pending:" + key, value).commit();
    }
    return value;
  }

  public void completeRequest(String key) {
    prefs.edit().remove("pending:" + key).apply();
  }
}
