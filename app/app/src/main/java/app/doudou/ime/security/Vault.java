package app.doudou.ime.security;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Secrets stay encrypted on device; exports never include this store. */
public final class Vault {
  private final Context context;

  public Vault(Context c) {
    context = c;
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore s = KeyStore.getInstance("AndroidKeyStore");
    s.load(null);
    if (!s.containsAlias("doudou-model-key")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "doudou-model-key", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) s.getKey("doudou-model-key", null);
  }

  public void set(String secret) throws Exception {
    if (secret.isEmpty()) {
      context.getSharedPreferences("vault", 0).edit().clear().apply();
      return;
    }
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    String value =
        Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(c.doFinal(secret.getBytes("UTF-8")), Base64.NO_WRAP);
    context.getSharedPreferences("vault", 0).edit().putString("model", value).apply();
  }

  public String get() throws Exception {
    String v = context.getSharedPreferences("vault", 0).getString("model", "");
    if (v.isEmpty()) return "";
    String[] a = v.split(":");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(a[0], 0)));
    return new String(c.doFinal(Base64.decode(a[1], 0)), "UTF-8");
  }

  public static String hint(String value) {
    return value.length() < 10
        ? "已保存（短密钥）"
        : value.substring(0, 3) + "••••••" + value.substring(value.length() - 3);
  }
}
