package app.doudou.ime.translation;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Skin;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.*;
import java.util.Map;

/** A language palette over the live editor, with one always-visible route back to the original. */
final class TranslationPanel {
  interface Actions {
    void language(String id);
    void original();
    void retry();
    void login();
    void copy();
    void scroll(int offset);
  }

  static void show(EditorHost host, TranslationDraft draft, Map<String, String> languages,
      String pending, String error, int scrollOffset, Actions actions) {
    Context c = host.context();
    LinearLayout root = host.openPanel("translation", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    root.addView(host.workspaceBar(WorkspaceBar.Mode.TRANSLATION), new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
    if (draft.local() && !draft.displayed().isEmpty()) {
      TextView preview = Ui.text(c, draft.displayed(), 16, Ui.ink());
      preview.setContentDescription("录音译文"); preview.setTextIsSelectable(true);
      Ui.pad(c, preview, 14, 8);
      ScrollView scroll = new ScrollView(c); scroll.addView(preview);
      root.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(c, 70)));
    }
    ScrollView scroll = new ScrollView(c);
    scroll.setFillViewport(false);
    LinearLayout grid = Ui.col(c); Ui.pad(c, grid, 6, 4);
    LinearLayout row = null; int index = 0;
    for (Map.Entry<String, String> language : languages.entrySet()) {
      if (index++ % 2 == 0) { row = Ui.row(c); grid.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
      String id = language.getKey();
      boolean selected = id.equals(pending.isEmpty() ? draft.language() : pending);
      TextView choice = Ui.button(c, language.getValue(), () -> actions.language(id));
      choice.setTextSize(17); choice.setTypeface(null, android.graphics.Typeface.BOLD);
      choice.setGravity(Gravity.CENTER);
      choice.setContentDescription("翻译成" + language.getValue());
      choice.setSelected(selected);
      choice.setStateDescription(id.equals(pending) ? "翻译中" : selected ? "当前显示" : null);
      choice.setBackground(Ui.pressable(c, selected ? Ui.appAccent() : Skin.choose(Ui.surface(), 0xffffffff), 28));
      choice.setTextColor(selected ? Skin.choose(Ui.bg(), 0xffffffff) : Ui.ink());
      choice.setElevation(Ui.dp(c, 1));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1);
      lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
      row.addView(choice, lp);
    }
    if (index % 2 != 0) row.addView(new View(c), new LinearLayout.LayoutParams(0, 0, 1));
    scroll.addView(grid); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> actions.scroll(y));
    scroll.post(() -> scroll.scrollTo(0, scrollOffset));

    LinearLayout footer = Ui.row(c); Ui.pad(c, footer, 10, 0);
    TextView original = Ui.button(c, "原文", actions::original);
    original.setContentDescription("还原原文");
    original.setEnabled(!draft.language().isEmpty() || !pending.isEmpty());
    original.setAlpha(original.isEnabled() ? 1 : .4f);
    footer.addView(original, new LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 48)));
    TextView state = Ui.text(c, !error.isEmpty() ? error : !pending.isEmpty() ? "翻译中…"
        : draft.original().isEmpty() ? "没有可翻译的文字" : "", 13, error.isEmpty() ? Ui.sub() : Ui.red());
    state.setContentDescription("翻译状态"); state.setMaxLines(2);
    state.setEllipsize(android.text.TextUtils.TruncateAt.END);
    Ui.pad(c, state, 10, 0);
    footer.addView(state, new LinearLayout.LayoutParams(0, -2, 1));
    if (!pending.isEmpty()) {
      ProgressBar progress = new ProgressBar(c);
      progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.audioEnd()));
      footer.addView(progress, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
    } else if (!error.isEmpty()) {
      footer.addView(Ui.button(c, host.services().cloud.loggedIn() ? "重试" : "登录",
          host.services().cloud.loggedIn() ? actions::retry : actions::login));
    } else if (draft.local() && !draft.displayed().isEmpty()) {
      footer.addView(Ui.button(c, "复制", actions::copy));
    }
    root.addView(footer, new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
  }
}
