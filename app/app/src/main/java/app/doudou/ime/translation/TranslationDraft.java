package app.doudou.ime.translation;

import app.doudou.ime.app.KeyboardApp;
import org.json.JSONObject;

/** One account-scoped local tool draft; translations are never evidence of the user's writing style. */
public final class TranslationDraft {
  public String source = "", target = "en", language = "auto", result = "", detected = "";
  public long revision;
  private final String account;

  public TranslationDraft(KeyboardApp app) {
    account = app.cloud.userId();
    try {
      JSONObject saved = new JSONObject(app.store.meta("translation:" + account, "{}"));
      source = saved.optString("source");
      target = saved.optString("target", "en");
      language = saved.optString("language", "auto");
      result = saved.optString("result");
      detected = saved.optString("detected");
    } catch (Exception ignored) { /* An absent tool draft has no effect on history or recordings. */ }
  }

  public boolean belongsTo(KeyboardApp app) { return account.equals(app.cloud.userId()); }

  public void change(String text, String from, String to) {
    if (source.equals(text) && language.equals(from) && target.equals(to)) return;
    source = text; language = from; target = to;
    result = ""; detected = ""; revision++;
  }

  public boolean accept(long requested, String text, String detectedLanguage) {
    if (revision != requested) return false;
    result = text; detected = detectedLanguage; return true;
  }

  public void save(KeyboardApp app) {
    if (!belongsTo(app)) return;
    try {
      app.store.setMeta("translation:" + account, new JSONObject()
          .put("source", source).put("language", language).put("target", target)
          .put("result", result).put("detected", detected).toString());
    } catch (Exception e) {
      throw new IllegalStateException("翻译草稿未能保存", e);
    }
  }
}
