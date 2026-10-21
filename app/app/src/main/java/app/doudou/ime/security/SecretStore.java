package app.doudou.ime.security;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Account credentials and sync keys are Keystore encrypted and excluded from exports. */
public final class SecretStore {
  private final Context context;

  public SecretStore(Context c) {
    context = c.getApplicationContext();
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias("doudou-product-secrets")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "doudou-product-secrets",
                  KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) store.getKey("doudou-product-secrets", null);
  }

  public synchronized void set(String name, String value) throws Exception {
    if (value.isEmpty()) {
      context.getSharedPreferences("product-secrets", 0).edit().remove(name).commit();
      return;
    }
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    c.updateAAD(name.getBytes("UTF-8"));
    String encrypted =
        Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")), Base64.NO_WRAP);
    if (!context
        .getSharedPreferences("product-secrets", 0)
        .edit()
        .putString(name, encrypted)
        .commit()) throw new java.io.IOException("无法保存登录凭据");
  }

  public synchronized String get(String name) throws Exception {
    String value = context.getSharedPreferences("product-secrets", 0).getString(name, "");
    if (value.isEmpty()) return "";
    String[] a = value.split(":");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(a[0], 0)));
    c.updateAAD(name.getBytes("UTF-8"));
    return new String(c.doFinal(Base64.decode(a[1], 0)), "UTF-8");
  }
}
