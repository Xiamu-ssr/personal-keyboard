package app.doudou.ime.input.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, offline keyword lookup. It neither reads editor state nor changes/learns text. */
public final class EmojiLexicon {
  private static final int MAX_KEYWORD_CHARS = 160;
  private static final int MAX_ROWS = 50000;
  private static final int MAX_INPUT_CHARS = 4 * 1024 * 1024;
  private static final int MAX_AFTER_RESULTS = 2;
  private static final String TERMINAL_PUNCTUATION = ",，、。.!！?？;；…\"”’')）]】」』》";
  private static final String BOUNDARY_PUNCTUATION =
      TERMINAL_PUNCTUATION + ":：“‘(（[【「『《";

  private final Map<String, List<String>> entries;
  private final int maxKeywordLength;

  private EmojiLexicon(Map<String, List<String>> entries, int maxKeywordLength) {
    this.entries = Collections.unmodifiableMap(entries);
    this.maxKeywordLength = maxKeywordLength;
  }

  /**
   * Reads UTF-8-decoded TSV: keyword TAB space-separated complete emoji sequences.
   * Blank lines and # comments are ignored; duplicate normalized keys/values merge
   * in file order. Malformed rows throw IOException. The caller owns the Reader.
   */
  public static EmojiLexicon read(Reader source) throws IOException {
    if (source == null) throw new NullPointerException("source");
    BufferedReader reader = source instanceof BufferedReader
        ? (BufferedReader) source : new BufferedReader(source);
    Map<String, LinkedHashSet<String>> values = new HashMap<>();
    int lineNumber = 0, totalChars = 0;
    String line;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      totalChars += line.length() + 1;
      if (lineNumber > MAX_ROWS || totalChars > MAX_INPUT_CHARS)
        throw new IOException("Emoji lexicon exceeds size limit");
      if (lineNumber == 1 && line.startsWith("\ufeff")) line = line.substring(1);
      if (line.trim().isEmpty() || (line.startsWith("#") && line.indexOf('\t') < 0)) continue;
      int tab = line.indexOf('\t');
      if (tab <= 0 || tab != line.lastIndexOf('\t')) throw malformed(lineNumber);
      String key = normalize(line.substring(0, tab));
      String payload = line.substring(tab + 1);
      if (!wellFormed(payload)) throw malformed(lineNumber);
      payload = payload.trim();
      if (key.isEmpty() || key.length() > MAX_KEYWORD_CHARS || payload.isEmpty()
          || !wellFormed(key)) throw malformed(lineNumber);
      LinkedHashSet<String> row = values.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
      for (String emoji : payload.split(" +")) {
        if (!wellFormed(emoji)) throw malformed(lineNumber);
        for (int at = 0; at < emoji.length(); ) {
          int cp = emoji.codePointAt(at);
          if (isSpace(cp) || Character.isISOControl(cp)) throw malformed(lineNumber);
          at += Character.charCount(cp);
        }
        row.add(emoji); // Do not split ZWJ, skin modifiers, flags, tags or VS16.
      }
    }
    Map<String, List<String>> entries = new HashMap<>();
    int longest = 0;
    for (Map.Entry<String, LinkedHashSet<String>> entry : values.entrySet()) {
      entries.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
      longest = Math.max(longest, entry.getKey().length());
    }
    return new EmojiLexicon(entries, longest);
  }

  /** Exact keyword lookup after NFKC, Locale.ROOT lowercase and whitespace normalization. */
  public List<String> suggest(String chineseOrEnglishKeyword, int limit) {
    if (limit <= 0 || chineseOrEnglishKeyword == null) return Collections.emptyList();
    String key = normalize(chineseOrEnglishKeyword);
    if (key.length() > maxKeywordLength) return Collections.emptyList();
    return take(entries.get(key), limit);
  }

  /**
   * Returns at most two optional suggestions for the longest complete keyword at
   * the end of recentText. Terminal punctuation/spaces may follow it, but a final
   * line break ends the context. No scanning for keywords earlier in the sentence.
   *
   * A match must start at a text/space/punctuation boundary: continuous Chinese is
   * not guessed/segmented (不开心 cannot trigger 开心). Passive hints require at
   * least two letters including Han, or three letters without Han; one-character
   * Chinese keywords and short English abbreviations remain available via suggest.
   */
  public List<String> after(String recentText, int limit) {
    if (recentText == null || limit <= 0 || maxKeywordLength == 0)
      return Collections.emptyList();
    int end = recentText.length();
    while (end > 0) {
      int cp = recentText.codePointBefore(end);
      if (cp == '\n' || cp == '\r' || cp == 0x2028 || cp == 0x2029)
        return Collections.emptyList();
      if (!isSpace(cp) && TERMINAL_PUNCTUATION.indexOf(cp) < 0) break;
      end -= Character.charCount(cp);
    }
    if (end == 0) return Collections.emptyList();
    // Read only a bounded tail. Never treat the artificial cut as a word boundary.
    int cut = Math.max(0, end - (MAX_KEYWORD_CHARS * 4 + 2));
    if (cut > 0 && Character.isLowSurrogate(recentText.charAt(cut))) cut++;
    String tail = normalize(recentText.substring(cut, end));
    for (int start = Math.max(0, tail.length() - maxKeywordLength); start < tail.length(); start++) {
      if (Character.isLowSurrogate(tail.charAt(start))) continue;
      if (start == 0 ? cut != 0 : !isBoundary(tail.codePointBefore(start))) continue;
      String keyword = tail.substring(start);
      List<String> result = entries.get(keyword);
      if (result != null && meaningful(keyword))
        return take(result, Math.min(limit, MAX_AFTER_RESULTS));
    }
    return Collections.emptyList();
  }

  private static List<String> take(List<String> values, int limit) {
    if (values == null || values.isEmpty()) return Collections.emptyList();
    return values.size() <= limit ? values : values.subList(0, limit);
  }

  private static String normalize(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder(normalized.length());
    boolean space = false;
    for (int at = 0; at < normalized.length(); ) {
      int cp = normalized.codePointAt(at);
      at += Character.charCount(cp);
      if (isSpace(cp)) {
        space = result.length() > 0;
      } else {
        if (space) result.append(' ');
        result.appendCodePoint(cp);
        space = false;
      }
    }
    return result.toString();
  }

  private static boolean meaningful(String keyword) {
    int letters = 0;
    boolean han = false;
    for (int at = 0; at < keyword.length(); ) {
      int cp = keyword.codePointAt(at);
      at += Character.charCount(cp);
      if (Character.isLetter(cp)) letters++;
      if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) han = true;
    }
    return letters >= (han ? 2 : 3);
  }

  private static boolean isSpace(int cp) {
    return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
  }

  private static boolean isBoundary(int cp) {
    return isSpace(cp) || BOUNDARY_PUNCTUATION.indexOf(cp) >= 0;
  }

  private static boolean wellFormed(String value) {
    for (int at = 0; at < value.length(); at++) {
      char ch = value.charAt(at);
      if (Character.isHighSurrogate(ch)) {
        if (++at == value.length() || !Character.isLowSurrogate(value.charAt(at))) return false;
      } else if (Character.isLowSurrogate(ch) || Character.isISOControl(ch)) {
        return false;
      }
    }
    return true;
  }

  private static IOException malformed(int line) {
    return new IOException("Malformed emoji lexicon row at line " + line);
  }
}
