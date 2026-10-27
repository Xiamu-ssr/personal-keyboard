package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import java.util.*;

/** Discoverable local symbol and emoji collections; no network and no unsupported modes. */
public final class KeyboardExtras {
  private final EditorHost host;
  private static final String[] SYMBOLS = {
    "， 。 ？ ！ 、 ： ； … — · ～ （ ） 【 】 《 》 「 」 『 』 〈 〉 \" ' / \\ _ @ # & * + - = % ¥ $ € £",
    "1 2 3 4 5 6 7 8 9 0 壹 贰 叁 肆 伍 陆 柒 捌 玖 零 拾 佰 仟 万 亿 一 二 三 四 五 六 七 八 九 十 ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ",
    "＋ − × ÷ ＝ ≠ ≈ ≤ ≥ ± ∞ √ ∑ ∏ ∫ π ° ℃ ℉ ㎡ ㎥ ㎝ ㎜ ㎏ ㎎ ㏄ ％ ‰",
    "＠ ＃ ＆ ＊ ＿ ～ ＾ ｜ 〔 〕 〖 〗 ﹁ ﹂ ﹃ ﹄ ｛ ｝ ＜ ＞ § © ® ™ → ← ↑ ↓ ↔ ⇒ ✓ ★ ☆ ♡ ♥ ♪ ♫"
  };

  public KeyboardExtras(EditorHost host) {
    this.host = host;
  }

  public void emoji() {
    new EmojiPanel(host).show();
  }

  public void symbols() {
    showSymbols(0);
  }

  private void showSymbols(int selected) {
    host.cancelVoice();
    host.commitComposing();
    Context c = host.context();
    LinearLayout root = host.openPanel("symbols", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout header = Ui.row(c);
    TextView back = Ui.button(c, "ABC", host::letters);
    back.setPadding(0, 0, 0, 0);
    header.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 48)));
    String[] names = {"常用", "数字", "数学", "更多"};
    for (int i = 0; i < names.length; i++) {
      final int page = i;
      TextView tab = Ui.button(c, names[i], () -> showSymbols(page));
      tab.setTextSize(13);
      tab.setPadding(0, 0, 0, 0);
      tab.setTextColor(i == selected ? Ui.accent() : Ui.sub());
      tab.setBackground(Ui.pressable(c, android.graphics.Color.TRANSPARENT, 12));
      header.addView(tab, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1));
    }
    header.addView(
        Ui.icon(c, "退格", host::delete), new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    root.addView(header, new LinearLayout.LayoutParams(-1, Ui.dp(c, 48)));
    ScrollView scroll = new ScrollView(c);
    LinearLayout content = Ui.col(c);
    Ui.pad(c, content, 4, 4);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    String[] values = SYMBOLS[selected].split(" ");
    LinearLayout row = null;
    for (int i = 0; i < values.length; i++) {
      if (i % 6 == 0) {
        row = Ui.row(c);
        content.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(c, 56)));
      }
      String value = values[i];
      TextView key =
          Ui.button(
              c,
              value,
              () -> {
                host.insert(value);

              });
      key.setTextSize(21);
      key.setPadding(0, 0, 0, 0);
      key.setEllipsize(null);
      key.setBackground(Ui.pressable(c, Ui.surface(), 8));
      LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
      size.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
      row.addView(key, size);
      if (i == values.length - 1)
        while (row.getChildCount() < 6)
          row.addView(new Space(c), new LinearLayout.LayoutParams(0, 1, 1));
    }
  }

  public void keyboards() {
    Context c = host.context();
    LinearLayout root = host.openPanel("keyboards", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.keyboard());
    root.addView(Ui.button(c, "返回键盘", host::typing));
    root.addView(
        Ui.cell(
            c,
            "工具",
            "中文 26 键",
            "",
            "",
            () -> {
              host.services().prefs().edit().putBoolean("preferredEnglish", false).apply();
              host.typing();
            }));
    root.addView(
        Ui.cell(
            c,
            "工具",
            "English QWERTY",
            "",
            "",
            () -> {
              host.services().prefs().edit().putBoolean("preferredEnglish", true).apply();
              host.typing();
            }));
  }

}
