package app.doudou.ime.translation;

import java.util.HashMap;
import java.util.Map;

/** Temporary original and translations, scoped to one account and live editor. Never persisted. */
public final class TranslationDraft {
  private String account = "", original = "", displayed = "", language = "";
  private long editor = -1, revision;
  private boolean local;
  private final Map<String, String> translations = new HashMap<>();

  public void bind(String user, long generation, boolean recording, String text) {
    // A temporarily unreadable InputConnection must never erase the captured original.
    if (text == null) {
      if (account.equals(user) && editor == generation && local == recording) return;
      text = "";
    }
    if (account.equals(user) && editor == generation && local == recording
        && (recording ? original : displayed).equals(text)) return;
    account = user; editor = generation; local = recording;
    original = displayed = text; language = ""; translations.clear(); revision++;
  }

  public boolean matches(String user, long generation, String text) {
    return account.equals(user) && editor == generation && (local || displayed.equals(text));
  }

  public String original() { return original; }
  public String displayed() { return displayed; }
  public String language() { return language; }
  public boolean local() { return local; }
  public long revision() { return revision; }
  public String cached(String target) { return translations.get(target); }

  public boolean remember(long requested, String target, String result) {
    if (requested != revision || result == null || result.trim().isEmpty()) return false;
    translations.put(target, result); return true;
  }

  public void display(String target) {
    String value = translations.get(target);
    if (value == null) throw new IllegalStateException("译文尚未完成");
    displayed = value; language = target;
  }

  public void restore() { displayed = original; language = ""; revision++; }
}
