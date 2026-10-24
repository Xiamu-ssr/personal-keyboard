package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import java.util.*;

/** Discoverable local symbol and emoji collections; no network and no unsupported modes. */
public final class KeyboardExtras {
  private final EditorHost host;
  private static final String[] EMOJI = {
    "😀 😄 😂 🤣 😊 🥹 🙂 🙃 😉 😍 🥰 😘 😎 🤔 🫡 🤗 🤭 😅 🥲 😭 😮 😳 😴 🙄 😤 😡 🥺 😱 😇 🤩",
    "👍 👎 👏 🙌 🤝 🙏 💪 🤞 ✌️ 👌 🫶 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💯 🔥 ✨ 🎉",
    "☀️ 🌙 ⭐ 🌈 🌸 🍀 🍎 🍊 🍉 ☕ 🍻 🥂 🍰 🎂 🍜 🍲 🧋 🥟 🐶 🐱 🐼 🐥 🐸 🦊",
    "✅ ❌ ❓ ❗ 💬 📞 💡 📌 📅 ⏰ 🎁 🎈 🧧 💰 🏠 🚗 🚄 ✈️ 🏃 🎵 🎬 📚 💻 📱"
  };
  private static final String[] SYMBOLS = {
    "， 。 ？ ！ 、 ： ； … — · ～ （ ） 【 】 《 》 「 」 『 』 〈 〉 \" ' / \\ _ @ # & * + - = % ¥ $ € £",
    "1 2 3 4 5 6 7 8 9 0 ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ",
    "＋ − × ÷ ＝ ≠ ≈ ≤ ≥ ± ∞ √ ∑ ∏ ∫ π ° ℃ ℉ ㎡ ㎥ ㎝ ㎜ ㎏ ㎎ ㏄ ％ ‰",
    "＠ ＃ ＆ ＊ ＿ ～ ＾ ｜ 〔 〕 〖 〗 ﹁ ﹂ ﹃ ﹄ ｛ ｝ ＜ ＞ § © ® ™ → ← ↑ ↓ ↔ ⇒ ✓ ★ ☆ ♡ ♥ ♪ ♫"
  };

  public KeyboardExtras(EditorHost host) {
    this.host = host;
  }

  public void emoji() {
    show(true, 0);
  }

  public void symbols() {
    show(false, 0);
  }

  private void show(boolean emoji, int selected) {
    host.cancelVoice();
    host.commitComposing();
    Context c = host.context();
    LinearLayout root = host.openPanel(emoji ? "emoji" : "symbols", KeyboardView.HEIGHT_DP);
    root.setBackgroundColor(Ui.KEYBOARD);
    LinearLayout header = Ui.row(c);
    header.addView(
        Ui.button(c, "ABC", host::letters),
        new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 48)));
    String[] names =
        emoji ? new String[] {"笑脸", "心意", "生活", "符号"} : new String[] {"常用", "数字", "数学", "更多"};
    for (int i = 0; i < names.length; i++) {
      final int page = i;
      TextView tab = Ui.button(c, names[i], () -> show(emoji, page));
      tab.setTextSize(13);
      tab.setTextColor(i == selected ? Ui.ACCENT : Ui.SUB);
      tab.setBackground(Ui.pressable(c, android.graphics.Color.TRANSPARENT, 12));
      header.addView(tab, new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1));
    }
    header.addView(
        Ui.icon(c, "退格", host::delete), new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    root.addView(header);
    ScrollView scroll = new ScrollView(c);
    LinearLayout content = Ui.col(c);
    Ui.pad(c, content, 4, 4);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    String[] values = (emoji ? EMOJI : SYMBOLS)[selected].split(" ");
    LinearLayout row = null;
    for (int i = 0; i < values.length; i++) {
      if (i % 6 == 0) {
        row = Ui.row(c);
        content.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));
      }
      String value = values[i];
      TextView key =
          Ui.button(
              c,
              value,
              () -> {
                host.insert(value);
                if (!host.privateMode() && emoji)
                  host.services().prefs().edit().putString("recentEmoji", value).apply();
              });
      key.setTextSize(emoji ? 25 : 21);
      key.setBackground(Ui.pressable(c, Ui.SURFACE, 8));
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
    root.setBackgroundColor(Ui.KEYBOARD);
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

  public static List<String> suggest(String pinyin) {
    switch (pinyin.replace("'", "")) {
      case "haha":
      case "hahaha":
      case "xiaosi":
        return Arrays.asList("😂", "🤣");
      case "xiexie":
      case "ganxie":
        return Arrays.asList("🙏", "🫶");
      case "kaixin":
      case "gaoxing":
        return Arrays.asList("😄", "🥰");
      case "liuliuliu":
      case "lll":
        return Arrays.asList("👍", "💯");
      case "aixin":
      case "aini":
        return Arrays.asList("❤️", "😘");
      case "shengri":
      case "shengrikuaile":
        return Arrays.asList("🎂", "🎉");
      case "wuyu":
        return Arrays.asList("🙄", "😅");
      default:
        return Collections.emptyList();
    }
  }
}
