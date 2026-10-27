package app.doudou.ime.input;

import java.util.*;

/** Offline Unicode collections. Every value has a reachable page; no truncated symbol grid. */
public final class SymbolCatalog {
  public static final int PAGE_SIZE = 26;
  public static final String[] NAMES = {"常用", "中文", "英文", "数学", "序号", "更多"};
  private static final String[] VALUES = {
    "_ — ； # % & ^ * + = - / : ~ ( ) … @ “ ” 。 ， 、 ？ ！ .",
    "， 。 ？ ！ 、 ： ； … — · ～ （ ） 【 】 《 》 「 」 『 』 〈 〉 〔 〕 〖 〗 ﹁ ﹂ ﹃ ﹄ ｛ ｝ ＜ ＞",
    ", . ? ! : ; ' \" / \\ _ @ # & * + - = % $ ( ) [ ] { } < > ~ ` | ^",
    "＋ − × ÷ ＝ ≠ ≈ ≤ ≥ ± ∞ √ ∑ ∏ ∫ π ° ℃ ℉ ㎡ ㎥ ㎝ ㎜ ㎏ ㎎ ㏄ ％ ‰",
    "壹 贰 叁 肆 伍 陆 柒 捌 玖 零 拾 佰 仟 万 亿 一 二 三 四 五 六 七 八 九 十 ① ② ③ ④ ⑤ ⑥ ⑦ ⑧ ⑨ ⑩ Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ",
    "¥ $ € £ ＠ ＃ ＆ ＊ ＿ ～ ＾ ｜ § © ® ™ → ← ↑ ↓ ↔ ⇒ ✓ ★ ☆ ♡ ♥ ♪ ♫"
  };
  private SymbolCatalog() {}
  public static List<String> values(int category) {
    if (category < 0 || category >= VALUES.length) throw new IllegalArgumentException("Symbol category");
    return Collections.unmodifiableList(Arrays.asList(VALUES[category].split(" ")));
  }
  public static int pages(int category) {
    return Math.max(1, (values(category).size() + PAGE_SIZE - 1) / PAGE_SIZE);
  }
  public static List<String> page(int category, int page) {
    List<String> all = values(category);
    if (page < 0 || page >= pages(category)) throw new IllegalArgumentException("Symbol page");
    int first = page * PAGE_SIZE;
    return all.subList(first, Math.min(first + PAGE_SIZE, all.size()));
  }
}
