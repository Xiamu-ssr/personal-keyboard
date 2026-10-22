package app.doudou.ime.sync;

import android.util.Base64;
import app.doudou.ime.app.KeyboardApp;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

/**
 * Account-bound encrypted snapshots; optimistic cloud revisions and record UUIDs prevent blind
 * overwrites.
 */
public final class ManagedSync {
  private final KeyboardApp app;

  public ManagedSync(KeyboardApp a) {
    app = a;
  }

  private byte[] key(String user) throws Exception {
    String name = "sync:" + user, value = app.cloud.secrets().get(name);
    if (value.isEmpty()) {
      value = app.cloud.request(user, "/v1/sync/key", "POST", new JSONObject()).getString("key");
      app.cloud.secrets().set(name, value);
    }
    return Base64.decode(value, Base64.NO_WRAP);
  }

  private byte[] encrypt(byte[] key, byte[] input, String user) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
    c.updateAAD(user.getBytes("UTF-8"));
    byte[] body = c.doFinal(input), iv = c.getIV(), out = new byte[iv.length + body.length];
    System.arraycopy(iv, 0, out, 0, iv.length);
    System.arraycopy(body, 0, out, iv.length, body.length);
    return out;
  }

  private byte[] decrypt(byte[] key, byte[] input, String user) throws Exception {
    if (input.length < 28) throw new IllegalArgumentException("云备份不完整");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOfRange(input, 0, 12)));
    c.updateAAD(user.getBytes("UTF-8"));
    return c.doFinal(Arrays.copyOfRange(input, 12, input.length));
  }

  public synchronized void run() throws Exception {
    if (!app.cloud.loggedIn()) throw new IllegalStateException("请先登录豆豆账户");
    String user = app.cloud.userId();
    byte[] key = key(user);
    for (int attempt = 0; attempt < 3; attempt++) {
      JSONObject remote = app.cloud.request(user, "/v1/sync", "GET", null);
      if (!remote.isNull("blob")) {
        byte[] clear = decrypt(key, Base64.decode(remote.getString("blob"), Base64.NO_WRAP), user);
        JSONObject document = new JSONObject(new String(clear, "UTF-8"));
        if (document.optInt("schema") == 2) {
          app.histories.migrateLegacy(document, user);
          app.store.setMeta("profile.migration", document.toString());
          app.store.setMeta("profile.migration.owner", user);
          app.personalization.sync();
        } else app.histories.restore(document, user);
      }
      if (!app.cloud.userId().equals(user)) throw new IllegalStateException("账号已切换，同步停止");
      long quota =
          app.cloud.cachedAccount().optJSONObject("entitlements") == null
              ? 0
              : app.cloud.cachedAccount().getJSONObject("entitlements").optLong("syncMaxBytes");
      if (quota <= 1024) throw new IllegalStateException("账户尚未开通历史备份空间");
      byte[] encrypted =
          encrypt(key, app.histories.backup(quota - 1024, user).toString().getBytes("UTF-8"), user);
      if (encrypted.length > quota) throw new IllegalStateException("云备份空间不足，原资料保留在手机");
      try {
        app.cloud.request(
            user,
            "/v1/sync",
            "PUT",
            new JSONObject()
                .put("baseRevision", remote.getLong("revision"))
                .put("blob", Base64.encodeToString(encrypted, Base64.NO_WRAP)));
        app.cloud.preferences().edit().putLong("lastSync", System.currentTimeMillis()).apply();
        return;
      } catch (app.doudou.ime.cloud.CloudClient.CloudError e) {
        if (!e.code.equals("SYNC_CONFLICT")) throw e;
      }
    }
    throw new IllegalStateException("另一台设备正在更新，请稍后再同步");
  }
}
