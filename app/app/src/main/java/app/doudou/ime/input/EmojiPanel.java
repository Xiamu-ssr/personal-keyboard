package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.emoji2.emojipicker.EmojiPickerView;
import androidx.emoji2.emojipicker.RecentEmojiProvider;
import app.doudou.ime.design.Ui;
import java.util.*;
import org.json.JSONArray;

/** Offline Unicode picker with native categories, long-press variants and account-scoped recents. */
public final class EmojiPanel {
  private final EditorHost host;
  public EmojiPanel(EditorHost host) { this.host = host; }

  public void show() {
    host.cancelVoice(); host.commitComposing();
    Context c = host.context();
    LinearLayout root = host.openPanel("emoji", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout header = Ui.row(c);
    TextView back = Ui.button(c, "返回键盘", host::letters);
    back.setMinWidth(Ui.dp(c, 100));
    header.addView(back, new LinearLayout.LayoutParams(-2, -1));
    TextView title = Ui.text(c, "表情", 15, Ui.sub()); Ui.pad(c, title, 10, 0);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(Ui.icon(c, "退格", host::delete), new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    // The header is an unweighted sibling, never part of the picker's scrolling area. Picker
    // measurements, category changes and font scaling cannot consume its return target.
    root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
    android.content.res.Configuration locale = new android.content.res.Configuration(c.getResources().getConfiguration());
    locale.setLocales(new android.os.LocaleList(java.util.Locale.SIMPLIFIED_CHINESE));
    Context pickerContext = new ContextThemeWrapper(c.createConfigurationContext(locale), app.doudou.ime.design.Skin.theme());
    EmojiPickerView picker = new EmojiPickerView(pickerContext);
    picker.setEmojiGridColumns(Math.max(5, Math.min(7, c.getResources().getConfiguration().screenWidthDp / 52)));
    picker.setEmojiGridRows(2.5f);
    picker.setRecentEmojiProvider(new RecentEmojiProvider() {
      private String key() { return "emoji.recent:" + host.services().cloud.userId(); }
      public void recordSelection(String emoji) {
        if (host.privateMode()) return;
        List<String> recent = list(); recent.remove(emoji); recent.add(0, emoji);
        while (recent.size() > 40) recent.remove(recent.size() - 1);
        host.services().store.setMeta(key(), new JSONArray(recent).toString());
      }
      private List<String> list() {
        List<String> recent = new ArrayList<>();
        if (host.privateMode()) return recent;
        try {
          JSONArray saved = new JSONArray(host.services().store.meta(key(), "[]"));
          for (int i = 0; i < Math.min(40, saved.length()); i++) recent.add(saved.getString(i));
        } catch (Exception ignored) { }
        return recent;
      }
      public Object getRecentEmojiList(kotlin.coroutines.Continuation<? super List<String>> continuation) { return list(); }
    });
    picker.setOnEmojiPickedListener(item -> host.insert(item.getEmoji()));
    FrameLayout viewport = new FrameLayout(c);
    viewport.setClipChildren(true);
    viewport.addView(picker, new FrameLayout.LayoutParams(-1, -1));
    root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
  }
}
