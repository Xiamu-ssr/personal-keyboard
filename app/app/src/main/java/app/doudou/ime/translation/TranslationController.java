package app.doudou.ime.translation;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.cloud.CloudTask;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.*;
import java.util.*;
import org.json.*;

/** Translation is a local workspace. Only an explicit Fill action crosses the live-editor boundary. */
public final class TranslationController {
  private final EditorHost host;
  private final Handler main = new Handler(Looper.getMainLooper());
  private TranslationDraft draft;
  private final LinkedHashMap<String, String> languages = new LinkedHashMap<>();
  private int limit, epoch;
  private CloudTask task;
  private boolean busy;
  private String error = "", offeredRecording = "";

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

  private TranslationDraft draft() {
    if (draft == null || !draft.belongsTo(host.services())) {
      cancel();
      draft = new TranslationDraft(host.services());
      if (!languages.containsKey(draft.target)) draft.target = "en";
      if (!draft.language.equals("auto") && !languages.containsKey(draft.language)) draft.language = "auto";
    }
    return draft;
  }

  public void show(String recording) {
    if (host.privateMode()) return;
    host.cancelVoice();
    host.commitComposing();
    TranslationDraft d = draft();
    offeredRecording = recording != null && !recording.isEmpty() && !d.source.isEmpty() && !recording.equals(d.source) ? recording : "";
    String text = d.source.isEmpty() ? recording != null ? recording : host.full() : null;
    if (text != null && !text.equals(d.source)) change(text, d.language, d.target);
    host.snapshot();
    render();
  }

  private void change(String text, String from, String to) {
    cancel();
    draft.change(text, from, to);
    draft.save(host.services());
    error = "";
  }

  public void cancel() {
    epoch++;
    if (task != null) task.cancel();
    task = null; busy = false;
  }

  private void edit() {
    cancel();
    EditText field = host.editLocal("原文", draft.source, value -> {
      change(value, draft.language, draft.target);
      host.snapshot();
      render();
    });
    field.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      public void afterTextChanged(android.text.Editable value) {
        if (!draft.source.equals(value.toString())) change(value.toString(), draft.language, draft.target);
      }
    });
  }

  private void language(View anchor, boolean source) {
    PopupMenu menu = Ui.menu(host.context(), anchor);
    List<String> ids = new ArrayList<>();
    if (source) { ids.add("auto"); menu.getMenu().add(0, 0, 0, "自动识别"); }
    for (Map.Entry<String, String> entry : languages.entrySet()) {
      ids.add(entry.getKey());
      menu.getMenu().add(0, ids.size() - 1, ids.size() - 1, entry.getValue());
    }
    menu.setOnMenuItemClickListener(item -> {
      String id = ids.get(item.getItemId());
      change(draft.source, source ? id : draft.language, source ? draft.target : id);
      render(); return true;
    });
    menu.show();
  }

  private void swap() {
    String language = draft.language.equals("auto") ? draft.detected : draft.language;
    if (!languages.containsKey(language) || draft.result.isEmpty()) return;
    change(draft.result, draft.target, language);
    render();
  }

  private void translate() {
    if (busy || draft.source.trim().isEmpty()) return;
    if (!host.services().cloud.loggedIn()) {
      error = "登录后可翻译"; render(); return;
    }
    if (draft.source.codePointCount(0, draft.source.length()) > limit) {
      error = "每次最多翻译 " + limit + " 字，原文已保留"; render(); return;
    }
    final TranslationDraft requested = draft;
    final long revision = requested.revision;
    final int token = ++epoch;
    final String source = requested.source, from = requested.language, to = requested.target;
    task = new CloudTask(host.services().cloud);
    final CloudTask current = task;
    busy = true; error = ""; render();
    host.services().network.execute(() -> {
      try {
        JSONObject input = new JSONObject().put("text", source).put("sourceLanguage", from).put("targetLanguage", to);
        JSONObject result = current.run("translation", input, null);
        main.post(() -> {
          if (epoch != token || !requested.belongsTo(host.services())) return;
          task = null; busy = false;
          if (requested.accept(revision, result.optString("text"), result.optString("detectedLanguage")))
            requested.save(host.services());
          if (host.mode().equals("translation")) render();
        });
      } catch (Exception e) {
        main.post(() -> {
          if (epoch != token || !requested.belongsTo(host.services())) return;
          task = null; busy = false;
          error = app.doudou.ime.model.TextModelClient.message(e);
          if (host.mode().equals("translation")) render();
        });
      }
    });
  }

  private void paste() {
    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
        host.context().getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) { Ui.toast(host.context(), "剪贴板没有文字"); return; }
    CharSequence text = clip.getItemAt(0).coerceToText(host.context());
    if (text == null || text.length() == 0) return;
    change(text.toString(), draft.language, draft.target); render();
  }

  private void fill() {
    if (busy || draft.result.isEmpty()) return;
    boolean replaced = draft.source.equals(host.full()) && !draft.source.isEmpty();
    if (replaced ? host.protectedReplace(draft.source, draft.result) : host.protectedInsert(draft.result)) host.typing();
  }

  public void render() {
    Context c = host.context();
    LinearLayout root = host.openPanel("translation", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    root.addView(host.workspaceBar(WorkspaceBar.Mode.TRANSLATION), new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
    LinearLayout languagesRow = Ui.row(c);
    Ui.pad(c, languagesRow, 8, 0);
    TextView from = Ui.button(c, draft.language.equals("auto") ? "自动识别" : languages.get(draft.language), () -> {});
    from.setTextSize(14); from.setContentDescription("原文语言"); from.setOnClickListener(v -> language(v, true));
    languagesRow.addView(from, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1));
    View swap = Icons.button(c, R.drawable.ic_swap_horiz, "交换语言", this::swap);
    swap.setEnabled(!draft.result.isEmpty() && (languages.containsKey(draft.detected) || !draft.language.equals("auto")));
    swap.setAlpha(swap.isEnabled() ? 1 : .35f);
    languagesRow.addView(swap, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    TextView to = Ui.button(c, languages.get(draft.target), () -> {});
    to.setTextSize(14); to.setContentDescription("目标语言"); to.setOnClickListener(v -> language(v, false));
    languagesRow.addView(to, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1));
    languagesRow.addView(Icons.button(c, R.drawable.ic_content_paste, "粘贴原文", this::paste),
        new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    root.addView(languagesRow);
    ScrollView scroll = new ScrollView(c);
    LinearLayout content = Ui.col(c); Ui.pad(c, content, 10, 6);
    if (!offeredRecording.isEmpty()) content.addView(Ui.button(c, "换成这段录音稿", () -> {
      String text = offeredRecording; offeredRecording = ""; change(text, draft.language, draft.target); render();
    }));
    TextView source = Ui.text(c, draft.source.isEmpty() ? "输入原文" : draft.source, 16, draft.source.isEmpty() ? Ui.sub() : Ui.ink());
    source.setContentDescription("翻译原文"); source.setMinimumHeight(Ui.dp(c, 52));
    source.setGravity(Gravity.CENTER_VERTICAL); source.setMaxLines(3); source.setEllipsize(android.text.TextUtils.TruncateAt.END);
    Ui.pad(c, source, 12, 10); source.setBackground(Ui.pressable(c, Ui.surface(), 12));
    source.setFocusable(true); source.setOnClickListener(v -> edit());
    content.addView(source, new LinearLayout.LayoutParams(-1, -2)); Ui.gap(c, content, 6);
    if (busy) {
      LinearLayout progress = Ui.row(c); Ui.pad(c, progress, 12, 12);
      ProgressBar spinner = new ProgressBar(c);
      spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.audioEnd()));
      progress.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
      TextView label = Ui.text(c, "翻译中…", 15, Ui.sub()); Ui.pad(c, label, 10, 0); progress.addView(label);
      content.addView(progress);
    } else if (!error.isEmpty()) {
      TextView label = Ui.text(c, error, 14, Ui.red()); Ui.pad(c, label, 12, 10);
      content.addView(label);
      if (!host.services().cloud.loggedIn()) content.addView(Ui.button(c, "登录", () -> host.settings("account")));
    } else if (!draft.result.isEmpty()) {
      TextView result = Ui.text(c, draft.result, 17, Ui.ink());
      result.setContentDescription("译文"); result.setTextIsSelectable(true); result.setLineSpacing(Ui.dp(c, 3), 1);
      Ui.pad(c, result, 12, 10); content.addView(result);
    }
    scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = Ui.row(c); Ui.pad(c, footer, 10, 0);
    boolean complete = !busy && error.isEmpty() && !draft.result.isEmpty();
    String fillLabel = !draft.source.isEmpty() && draft.source.equals(host.full()) ? "替换原文" : "填入";
    TextView primary = Ui.primary(c, complete ? fillLabel : busy ? "翻译中" : error.isEmpty() ? "翻译" : "重试", complete ? this::fill : this::translate);
    primary.setContentDescription(complete ? fillLabel : "开始翻译");
    primary.setEnabled(!busy && !draft.source.trim().isEmpty()); primary.setAlpha(primary.isEnabled() ? 1 : .4f);
    footer.addView(primary, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1));
    TextView copy = Ui.button(c, "复制", () -> {
      ((android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE))
          .setPrimaryClip(ClipData.newPlainText("译文", draft.result)); Ui.toast(c, "已复制");
    });
    TextView retry = Ui.button(c, "重译", this::translate); retry.setContentDescription("开始翻译");
    for (TextView button : new TextView[] {copy, retry}) {
      button.setVisibility(complete ? View.VISIBLE : View.GONE);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 48));
      lp.setMargins(Ui.dp(c, 6), 0, 0, 0); footer.addView(button, lp);
    }
    root.addView(footer);
  }
}
