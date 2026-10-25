package app.doudou.ime.translation;

import android.content.*;
import android.os.*;
import app.doudou.ime.cloud.CloudTask;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import java.util.LinkedHashMap;
import org.json.*;

/** Language choices transform one captured original; they never translate a previous translation. */
public final class TranslationController {
  private final EditorHost host;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final TranslationDraft editorDraft = new TranslationDraft();
  private final TranslationDraft recordingDraft = new TranslationDraft();
  private TranslationDraft draft = editorDraft;
  private final LinkedHashMap<String, String> languages = new LinkedHashMap<>();
  private int limit, epoch, scrollOffset;
  private CloudTask task;
  private String pending = "", lastTarget = "", error = "";

  public TranslationController(EditorHost host) {
    this.host = host;
    try (java.io.InputStream stream = host.context().getAssets().open("translation.json")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] bytes = new byte[4096]; int n;
      while ((n = stream.read(bytes)) != -1) out.write(bytes, 0, n);
      JSONObject contract = new JSONObject(out.toString("UTF-8"));
      limit = contract.getInt("maxSourceCharacters");
      JSONArray list = contract.getJSONArray("languages");
      for (int i = 0; i < list.length(); i++) {
        JSONObject language = list.getJSONObject(i);
        languages.put(language.getString("id"), language.getString("name"));
      }
    } catch (Exception e) { throw new IllegalStateException("翻译语言配置不可用", e); }
  }

  public void show(String recording) {
    if (host.privateMode()) return;
    host.cancelVoice(); host.commitComposing();
    cancel();
    draft = recording != null ? recordingDraft : editorDraft;
    String text = recording != null ? recording : host.full();
    draft.bind(host.services().cloud.userId(), host.generation(), recording != null, text);
    error = "";
    if (text == null) error = "当前输入框暂时无法读取";
    render();
  }

  public void cancel() {
    epoch++;
    if (task != null) task.cancel();
    task = null; pending = "";
  }

  private boolean bound() {
    return !host.privateMode() && draft.matches(host.services().cloud.userId(), host.generation(), host.full());
  }

  private void choose(String target) {
    if (host.privateMode() || !languages.containsKey(target)) return;
    cancel(); error = ""; lastTarget = target;
    if (!bound()) {
      String text = host.full();
      if (draft.local() || text == null) {
        error = "当前输入框暂时无法读取"; render(); return;
      }
      draft.bind(host.services().cloud.userId(), host.generation(), false, text);
    }
    if (draft.original().trim().isEmpty()) { error = "没有可翻译的文字"; render(); return; }
    if (draft.cached(target) != null) { apply(target); return; }
    if (!host.services().cloud.loggedIn()) { error = "登录后可翻译"; render(); return; }
    if (draft.original().codePointCount(0, draft.original().length()) > limit) {
      error = "每次最多翻译 " + limit + " 字"; render(); return;
    }
    final long revision = draft.revision();
    final int token = ++epoch;
    final String original = draft.original();
    task = new CloudTask(host.services().cloud);
    final CloudTask current = task;
    pending = target; render();
    host.services().network.execute(() -> {
      try {
        JSONObject input = new JSONObject().put("text", original)
            .put("sourceLanguage", "auto").put("targetLanguage", target);
        JSONObject result = current.run("translation", input, null);
        main.post(() -> {
          if (epoch != token) return;
          task = null; pending = "";
          if (!bound()) {
            error = "文字已修改，请重新选择语言";
            if (host.mode().equals("translation")) render();
            return;
          }
          if (draft.remember(revision, target, result.optString("text"))) apply(target);
        });
      } catch (Exception e) {
        main.post(() -> {
          if (epoch != token) return;
          task = null; pending = "";
          error = app.doudou.ime.model.TextModelClient.message(e);
          if (host.mode().equals("translation")) render();
        });
      }
    });
  }

  private boolean write(String value) {
    if (!bound()) { error = "文字已修改，请重新选择语言"; return false; }
    if (draft.local() || value.equals(draft.displayed())) return true;
    host.snapshot();
    if (host.protectedReplace(draft.displayed(), value)) return true;
    error = "当前输入框暂时无法替换"; return false;
  }

  private void apply(String target) {
    if (write(draft.cached(target))) { draft.display(target); error = ""; }
    if (host.mode().equals("translation")) render();
  }

  private void original() {
    cancel();
    if (write(draft.original())) { draft.restore(); error = ""; }
    if (host.mode().equals("translation")) render();
  }

  public void render() {
    TranslationPanel.show(host, draft, languages, pending, error, scrollOffset, new TranslationPanel.Actions() {
      public void language(String id) { choose(id); }
      public void original() { TranslationController.this.original(); }
      public void retry() { choose(lastTarget); }
      public void login() { host.settings("account"); }
      public void scroll(int offset) { scrollOffset = offset; }
      public void copy() {
        ((ClipboardManager) host.context().getSystemService(Context.CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("翻译", draft.displayed()));
        Ui.toast(host.context(), "已复制");
      }
    });
  }
}
