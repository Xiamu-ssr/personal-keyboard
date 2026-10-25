package app.doudou.ime.advisor;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.ThemedActivity;
import app.doudou.ime.design.Ui;
import java.util.*;
import org.json.*;

/** A slot has a name and a complete set of independent expression preferences. */
public final class PaletteActivity extends ThemedActivity {
  private KeyboardApp app;
  private PaletteStore store;
  private JSONObject snapshot, draft;
  private int index;
  private boolean dirty, busy;
  private String account;
  private LinearLayout root, body;
  private EditText name;
  private TextView save;
  private ScrollView scroll;
  private int restoreScroll;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    app = (KeyboardApp) getApplication(); store = new PaletteStore(app);
    account = app.cloud.userId(); index = getIntent().getIntExtra("slot", 0);
    if (!app.cloud.loggedIn() || index < 0 || index >= 6) { finish(); return; }
    if (state != null && account.equals(state.getString("account"))) {
      try {
        snapshot = new JSONObject(state.getString("snapshot"));
        draft = new JSONObject(state.getString("draft"));
        dirty = state.getBoolean("dirty"); restoreScroll = state.getInt("scroll");
        render(); return;
      } catch (Exception ignored) { }
    }
    load();
  }

  private void shell() {
    root = Ui.col(this); root.setBackgroundColor(Ui.bg());
    LinearLayout heading = Ui.row(this); Ui.pad(this, heading, 12, 8);
    heading.addView(Ui.icon(this, "返回", this::onBackPressed), new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, "编辑表达", 21, Ui.ink()); title.setTypeface(null, Typeface.BOLD);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    save = Ui.button(this, "保存", this::save);
    save.setTextColor(Ui.appAccent()); save.setBackground(Ui.pressable(this, Ui.appTint(), 14));
    saveState();
    heading.addView(save, new LinearLayout.LayoutParams(-2, Ui.dp(this, 48)));
    root.addView(heading); root.addView(Ui.divider(this));
    scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
    Ui.pad(this, scroll, 20, 18); body = Ui.col(this); scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); Ui.content(this, root);
  }

  private void load() {
    snapshot = null; shell();
    ProgressBar progress = new ProgressBar(this); progress.setContentDescription("正在加载表达");
    body.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
    app.network.execute(() -> {
      try {
        JSONObject value = store.sync();
        runOnUiThread(() -> {
          if (isFinishing() || !account.equals(app.cloud.userId())) return;
          try {
            snapshot = value; draft = new JSONObject(value.getJSONArray("slots").getJSONObject(index).toString());
            dirty = false; render();
          } catch (Exception e) { failedLoad(); }
        });
      } catch (Exception e) { runOnUiThread(this::failedLoad); }
    });
  }

  private void failedLoad() {
    if (isFinishing()) return;
    body.removeAllViews(); body.addView(Ui.text(this, "暂时无法加载表达", 17, Ui.ink()));
    Ui.gap(this, body, 16); body.addView(Ui.button(this, "重试", this::load));
  }

  private void render() {
    if (isFinishing()) return;
    shell();
    try {
      name = Ui.edit(this, "给这格取个名字", draft.getString("name"));
      name.setSingleLine(true); name.setTextSize(22);
      name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(16)});
      name.setContentDescription("表达名称");
      name.addTextChangedListener(new android.text.TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        public void onTextChanged(CharSequence s, int start, int before, int count) { changedName(); }
        public void afterTextChanged(android.text.Editable e) { }
      });
      body.addView(name);
      Ui.gap(this, body, 12);
      LinearLayout actions = Ui.row(this);
      actions.addView(Ui.button(this, "参考搭配", this::templates), new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
      TextView inherit = Ui.button(this, "全部随本人", () -> {
        try { draft.put("attributes", PaletteDefinition.inherit(this)); dirty = true; renderAtCurrentPosition(); }
        catch (Exception e) { Ui.toast(this, "表达配置无法读取"); }
      });
      LinearLayout.LayoutParams next = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1); next.leftMargin = Ui.dp(this, 8);
      actions.addView(inherit, next); body.addView(actions);
      JSONArray fields = PaletteDefinition.fields(this);
      Ui.gap(this, body, 18);
      LinearLayout bank = Ui.col(this); Ui.pad(this, bank, 10, 14);
      bank.setBackground(Ui.bg(this, Ui.panel(), 16)); body.addView(bank);
      String[] graduated = {"length", "directness", "warmth", "emoji", "emotion", "formality", "audience"};
      int columns = getResources().getConfiguration().fontScale > 1.15f ? 3 : 4;
      for (int start = 0; start < graduated.length; start += columns) {
        LinearLayout row = Ui.row(this); row.setGravity(Gravity.TOP); bank.addView(row);
        for (int j = start; j < Math.min(start + columns, graduated.length); j++)
          for (int i = 0; i < fields.length(); i++)
            if (graduated[j].equals(fields.getJSONObject(i).getString("id"))) graduatedField(fields.getJSONObject(i), row);
        for (int j = Math.min(start + columns, graduated.length); j < start + columns; j++)
          row.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (start + columns < graduated.length) Ui.gap(this, bank, 20);
      }
      for (String id : new String[] {"rhetoric", "structure"})
        for (int i = 0; i < fields.length(); i++) if (id.equals(fields.getJSONObject(i).getString("id"))) field(fields.getJSONObject(i));
      Ui.gap(this, body, 12);
      body.setFocusableInTouchMode(true); body.requestFocus();
      scroll.post(() -> scroll.scrollTo(0, restoreScroll));
    } catch (Exception e) { failedLoad(); }
  }

  private void changedName() {
    if (draft == null || name == null) return;
    try { draft.put("name", name.getText().toString()); dirty = true; saveState(); }
    catch (Exception ignored) { }
  }

  private void renderAtCurrentPosition() { restoreScroll = scroll.getScrollY(); render(); }

  private void field(JSONObject f) throws JSONException {
    String id = f.getString("id"), title = f.getString("name");
    boolean multi = f.getString("kind").equals("multi");
    Ui.gap(this, body, 10);
    LinearLayout header = Ui.row(this); header.setGravity(Gravity.CENTER_VERTICAL);
    header.setMinimumHeight(Ui.dp(this, 56)); header.setFocusable(true);
    header.setContentDescription(title + "设置");
    TextView label = Ui.text(this, title + (multi ? " · 可组合" : ""), 16, Ui.ink()); label.setTypeface(null, Typeface.BOLD);
    TextView summary = Ui.text(this, "", 14, Ui.sub()); summary.setGravity(Gravity.END);
    summary.setSingleLine(true); summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(label, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout.LayoutParams summarySize = new LinearLayout.LayoutParams(0, -2, 1); summarySize.leftMargin = Ui.dp(this, 10);
    header.addView(summary, summarySize);
    body.addView(header);
    LinearLayout choices = Ui.col(this); body.addView(choices);
    Runnable describe = () -> {
      try {
        String current = selectedNames(f);
        summary.setText(current); header.setStateDescription(current);
      } catch (Exception ignored) { }
    };
    JSONArray options = new JSONArray(f.getJSONArray("options").toString());
    if (multi) {
      JSONArray extended = new JSONArray(); extended.put(new JSONObject().put("id", "inherit").put("name", "随本人"));
      for (int i = 0; i < options.length(); i++) extended.put(options.get(i));
      options = extended;
    }
    final JSONArray values = options;
    int width = getResources().getConfiguration().screenWidthDp - 40;
    int columns = getResources().getConfiguration().fontScale > 1.15f || width < 320 ? 2 : 3;
    List<ToggleButton> buttons = new ArrayList<>();
    for (int start = 0; start < options.length(); start += columns) {
      LinearLayout row = Ui.row(this); row.setGravity(Gravity.TOP);
      for (int offset = 0; offset < columns; offset++) {
        int option = start + offset;
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
        if (offset > 0) size.leftMargin = Ui.dp(this, 8);
        if (option >= options.length()) { row.addView(new Space(this), size); continue; }
        JSONObject value = options.getJSONObject(option);
        ToggleButton button = new ToggleButton(this);
        button.setStateListAnimator(null); button.setElevation(0);
        button.setTextOn(value.getString("name")); button.setTextOff(value.getString("name"));
        button.setTextSize(14); button.setAllCaps(false); button.setMinWidth(0);
        button.setMinimumWidth(0); button.setMinimumHeight(Ui.dp(this, 48));
        button.setPadding(Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10));
        button.setContentDescription(title + "：" + value.getString("name"));
        buttons.add(button); row.addView(button, size);
        button.setOnClickListener(v -> {
          if (busy) return;
          try {
            JSONObject attributes = draft.getJSONObject("attributes"); String key = value.getString("id");
            if (!multi) attributes.put(id, key);
            else {
              JSONArray prior = attributes.getJSONArray(id), selected = new JSONArray();
              boolean had = PaletteDefinition.contains(prior, key);
              if (!key.equals("inherit")) {
                for (int j = 0; j < prior.length(); j++) {
                  String old = prior.getString(j);
                  if (!old.equals(key) && !key.equals("plain") && !old.equals("plain")) selected.put(old);
                }
                if (!had) selected.put(key);
                if (selected.length() > f.getInt("maxItems")) {
                  Ui.toast(this, "最多组合三种手法"); paintOptions(f, values, buttons); return;
                }
              }
              attributes.put(id, selected);
            }
            dirty = true; saveState(); paintOptions(f, values, buttons); describe.run();
          } catch (Exception e) { Ui.toast(this, "这个表达选项暂不可用"); }
        });
      }
      choices.addView(row); if (start + columns < options.length()) Ui.gap(this, choices, 8);
    }
    Ui.gap(this, choices, 6); paintOptions(f, values, buttons); describe.run();
  }

  /** Ordered preferences are actual discrete controls. Categories remain categories. */
  private void graduatedField(JSONObject f, LinearLayout row) throws JSONException {
    String id = f.getString("id"), title = f.getString("name");
    JSONArray all = f.getJSONArray("options"), levels = new JSONArray();
    for (int i = 0; i < all.length(); i++) if (!all.getJSONObject(i).getString("id").equals("inherit")) levels.put(all.getJSONObject(i));
    LinearLayout column = Ui.col(this); column.setGravity(Gravity.CENTER_HORIZONTAL);
    Ui.pad(this, column, 3, 0); row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    String shortTitle = id.equals("formality") ? "正式" : id.equals("directness") ? "直接"
        : id.equals("warmth") ? "亲近" : id.equals("emotion") ? "情绪" : id.equals("audience") ? "话题" : title;
    TextView label = Ui.text(this, shortTitle, 14, Ui.ink()); label.setGravity(Gravity.CENTER);
    label.setTypeface(null, Typeface.BOLD); column.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 28)));
    Ui.gap(this, column, 8);
    PreferenceSlider slider = new PreferenceSlider(this); slider.setMax(levels.length() - 1);
    String[] levelNames = new String[levels.length()];
    for(int i=0;i<levels.length();i++)levelNames[i]=levels.getJSONObject(i).getString("name");
    slider.setLevels(levelNames);
    slider.setTag("palette:" + id); slider.setContentDescription(title + "调节");
    slider.setPadding(0, 0, 0, 0);
    column.addView(slider, new LinearLayout.LayoutParams(-1, Ui.dp(this, 144)));
    TextView inherit = Ui.text(this, "", 12, Ui.sub()); inherit.setGravity(Gravity.CENTER);
    inherit.setMaxLines(2);
    column.addView(inherit, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
    Runnable paint = () -> {
      try {
        String value = draft.getJSONObject("attributes").getString(id);
        boolean inherited = value.equals("inherit"); int position = levels.length() / 2;
        for (int i = 0; i < levels.length(); i++) if (value.equals(levels.getJSONObject(i).getString("id"))) position = i;
        inherit.setText(inherited ? "随本人" : "恢复跟随");
        inherit.setContentDescription(title + (inherited ? "：随本人" : "：恢复随本人"));
        inherit.setClickable(!inherited); inherit.setFocusable(!inherited);
        inherit.setStateDescription(inherited ? "随本人" : null);
        inherit.setTextColor(inherited ? Ui.sub() : Ui.appAccent());
        inherit.setBackground(inherited ? null : Ui.pressable(this, android.graphics.Color.TRANSPARENT, 12));
        slider.inherited(inherited); slider.setProgress(position);
        String description = inherited ? "随本人" : levels.getJSONObject(position).getString("name");
        slider.setStateDescription(description);
      } catch (Exception ignored) { }
    };
    slider.setExplicitSelection(() -> {
      if (busy) return;
      try {
        String selected = levels.getJSONObject(slider.getProgress()).getString("id");
        if (!draft.getJSONObject("attributes").getString(id).equals(selected)) {
          draft.getJSONObject("attributes").put(id, selected);
          dirty = true; saveState(); paint.run();
        }
      } catch (Exception ignored) { }
    });
    inherit.setOnClickListener(v -> {
      if (busy) return;
      try { draft.getJSONObject("attributes").put(id, "inherit"); dirty = true; saveState(); paint.run(); }
      catch (Exception ignored) { }
    });
    paint.run();
  }

  private String selectedNames(JSONObject field) throws JSONException {
    JSONObject values = draft.getJSONObject("attributes"); String id = field.getString("id");
    List<String> names = new ArrayList<>(); JSONArray options = field.getJSONArray("options");
    for (int i = 0; i < options.length(); i++) {
      JSONObject option = options.getJSONObject(i);
      if (field.getString("kind").equals("multi") ? PaletteDefinition.contains(values.getJSONArray(id), option.getString("id"))
          : option.getString("id").equals(values.getString(id))) names.add(option.getString("name"));
    }
    return names.isEmpty() ? "随本人" : android.text.TextUtils.join(" · ", names);
  }

  private void paintOptions(JSONObject f, JSONArray options, List<ToggleButton> buttons) throws JSONException {
    JSONObject attributes = draft.getJSONObject("attributes"); String id = f.getString("id");
    for (int i = 0; i < buttons.size(); i++) {
      String value = options.getJSONObject(i).getString("id");
      boolean selected = f.getString("kind").equals("multi")
          ? value.equals("inherit") ? attributes.getJSONArray(id).length() == 0 : PaletteDefinition.contains(attributes.getJSONArray(id), value)
          : value.equals(attributes.getString(id));
      ToggleButton button = buttons.get(i); button.setChecked(selected);
      button.setTextColor(selected ? Ui.appAccent() : Ui.ink());
      button.setBackground(Ui.pressable(this, selected ? Ui.appTint() : Ui.surface(), 12));
    }
  }

  private void templates() {
    if (busy) return;
    try {
      JSONArray values = PaletteDefinition.templates(this);
      LinearLayout choices = Ui.col(this); Ui.pad(this, choices, 20, 12);
      AlertDialog dialog = new AlertDialog.Builder(this).setTitle("参考搭配").setView(choices).setNegativeButton("取消", null).create();
      for (int i = 0; i < values.length(); i += 2) {
        LinearLayout row = Ui.row(this);
        for (int j = i; j < Math.min(i + 2, values.length()); j++) {
          JSONObject value = values.getJSONObject(j);
          TextView button = Ui.button(this, value.getString("name"), () -> {
            try {
              draft.put("attributes", new JSONObject(value.getJSONObject("attributes").toString()));
              dirty = true; dialog.dismiss(); renderAtCurrentPosition();
            } catch (Exception e) { Ui.toast(this, "搭配无法读取"); }
          });
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
          if (j > i) lp.leftMargin = Ui.dp(this, 8);
          row.addView(button, lp);
        }
        if (row.getChildCount() == 1) {
          LinearLayout.LayoutParams empty = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
          empty.leftMargin = Ui.dp(this, 8); row.addView(new Space(this), empty);
        }
        choices.addView(row); Ui.gap(this, choices, 8);
      }
      dialog.show();
    } catch (Exception e) { Ui.toast(this, "搭配无法读取"); }
  }

  private void save() {
    if (busy || snapshot == null || !dirty) return;
    if (!account.equals(app.cloud.userId())) { finish(); return; }
    String value = name.getText().toString().trim();
    if (value.isEmpty()) { name.setError("取个名字"); return; }
    try {
      JSONObject attributes = PaletteDefinition.attributes(this, draft.getJSONObject("attributes"));
      busy = true; enabled(body, false); saveState(); save.setText("保存中…");
      app.network.execute(() -> {
        try {
          store.save(account, snapshot, index, value, attributes);
          runOnUiThread(() -> { dirty = false; finish(); });
        } catch (Exception e) { runOnUiThread(() -> { busy = false; enabled(body, true); save.setText("保存"); saveState(); Ui.toast(this, e.getMessage()); }); }
      });
    } catch (Exception e) { Ui.toast(this, "表达属性不完整"); }
  }

  private void enabled(View view, boolean value) {
    view.setEnabled(value);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) enabled(group.getChildAt(i), value);
    }
  }

  private void saveState() {
    boolean available = snapshot != null && dirty && !busy;
    save.setEnabled(available); save.setAlpha(available || busy ? 1f : .4f);
  }

  @Override protected void onResume() {
    super.onResume();
    if (account != null && !account.equals(app.cloud.userId())) finish();
  }

  @Override public void onBackPressed() {
    if (busy) return;
    if (!dirty) { super.onBackPressed(); return; }
    new AlertDialog.Builder(this).setTitle("保存这格表达？").setPositiveButton("保存", (d, w) -> save())
        .setNegativeButton("放弃修改", (d, w) -> finish()).setNeutralButton("继续编辑", null).show();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    if (snapshot != null && draft != null) {
      state.putString("snapshot", snapshot.toString()); state.putString("draft", draft.toString());
      state.putString("account", account); state.putBoolean("dirty", dirty);
      state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    }
    super.onSaveInstanceState(state);
  }
}
