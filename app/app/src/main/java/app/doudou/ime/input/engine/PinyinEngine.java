package app.doudou.ime.input.engine;

import java.util.*;

/** AOSP's offline sentence decoder plus a separately replaceable personal lexicon. */
public final class PinyinEngine {
  static {
    System.loadLibrary("doudou_decoder");
  }

  private static native boolean nativeOpen(String systemDictionary, String userDictionary);

  private static native String[] nativeCandidates(String input, int offset, int limit);

  private static native String[] nativePredictions(String history, int limit);

  private static native void nativeLearn(String input, String selected);

  private static native boolean nativeClear(String dictionary, String user);

  private final String dictionaryPath, userPath;
  private final InitialLexicon initials;

  public interface Lexicon {
    List<Word> lookup(String input, int limit);

    /** All matching rows, independent of the candidate strip's visible item count. */
    default List<Word> lookupAll(String input) { return lookup(input, Integer.MAX_VALUE); }

    default List<Word> corrections(String input) { return Collections.emptyList(); }

    /** Text completions return only the suffix to append, never the already committed prefix. */
    default List<Word> continuations(String history, int limit) { return Collections.emptyList(); }
  }

  private Lexicon lexicon;
  private EmojiLexicon emoji;
  private Query cached;
  private Map<String, Integer> cachedLearned = Collections.emptyMap();

  public synchronized void lexicon(Lexicon value) {
    lexicon = value;
    cached = null;
  }

  public synchronized void emoji(EmojiLexicon value) {
    emoji = value;
    cached = null;
  }

  public enum Kind { WORD, EMOJI, PUNCTUATION }

  public static final class Word {
    /** code always consumes the original input; spelling may describe a corrected lookup. */
    public final String text, code, spelling;
    public final double score, correctionCost;
    public final Kind kind;

    public Word(String t, String c, double s) {
      this(t, c, s, c, 0);
    }

    public Word(String t, String c, double s, String spelling, double cost) {
      this(t, c, s, spelling, cost, Kind.WORD);
    }

    private Word(String t, String c, double s, String spelling, double cost, Kind kind) {
      text = t;
      code = c;
      score = s;
      this.spelling = spelling;
      correctionCost = cost;
      this.kind = kind;
    }
  }

  /** Immutable query snapshot; paging cannot change ranks or lose a dictionary's later rows. */
  public static final class Query {
    public final String input;
    public final int nativeCount, lexiconCount, correctionCount;
    public final boolean continuation;
    private final List<Word> words;

    Query(String input, List<Word> words, int nativeCount, int lexiconCount, int correctionCount) {
      this(input, words, nativeCount, lexiconCount, correctionCount, false);
    }

    Query(String input, List<Word> words, int nativeCount, int lexiconCount, int correctionCount,
        boolean continuation) {
      this.input = input;
      this.words = Collections.unmodifiableList(new ArrayList<>(words));
      this.nativeCount = nativeCount;
      this.lexiconCount = lexiconCount;
      this.correctionCount = correctionCount;
      this.continuation = continuation;
    }

    public int size() { return words.size(); }

    public List<Word> page(int offset, int limit) {
      if (offset < 0 || limit < 0) throw new IllegalArgumentException("Negative candidate page");
      if (offset >= words.size() || limit == 0) return Collections.emptyList();
      int end = offset + Math.min(limit, words.size() - offset);
      return words.subList(offset, end);
    }
  }

  private final List<Word> personal = new ArrayList<>();

  public PinyinEngine(String dictionary, String userDictionary, InitialLexicon initials) {
    this.initials = initials;
    dictionaryPath = dictionary;
    userPath = userDictionary;
    if (!nativeOpen(dictionary, userDictionary)) throw new IllegalStateException("拼音词库无法打开");
  }

  public synchronized void personalWords(List<Word> words) {
    if (personal.size() == words.size()) {
      boolean equal = true;
      for (int i = 0; i < words.size(); i++)
        if (!personal.get(i).text.equals(words.get(i).text)
            || !personal.get(i).code.equals(words.get(i).code)) { equal = false; break; }
      if (equal) return;
    }
    personal.clear();
    personal.addAll(words);
    cached = null;
  }

  public static String normalize(String s) {
    return s.toLowerCase(Locale.ROOT)
        .replace("ü", "v")
        .replace("u:", "v")
        .replaceAll("[^a-z']", "");
  }

  public synchronized List<Word> candidates(String input, int limit, Map<String, Integer> learned) {
    if (limit <= 0) return Collections.emptyList();
    return query(input, learned).page(0, limit);
  }

  /** Run on the dictionary worker, then pass the snapshot to the UI. Native search supports 39
   * letters (and at most 1,450 lemma entries internally); the modern word index is not page-capped. */
  public synchronized Query query(String input, Map<String, Integer> learned) {
    String code = normalize(input);
    if (cached != null && cached.input.equals(code) && cachedLearned.equals(learned)) return cached;
    if (code.isEmpty() || code.length() > 64)
      return new Query(code, Collections.emptyList(), 0, 0, 0);
    LinkedHashMap<String, Word> found = new LinkedHashMap<>();
    String[] nativeResult = nativeCandidates(code, 0, Integer.MAX_VALUE);
    for (int i = 0; i + 1 < nativeResult.length; i += 2) {
      String text = nativeResult[i], consumed = nativeResult[i + 1];
      found.put(
          text,
          new Word(
              text,
              consumed,
              (consumed.equals(code) ? 300 - i : 145 - i)
                  + learnedBoost(learned.getOrDefault(text, 0))));
    }
    int lexicalCount = 0, correctionCount = 0;
    if (lexicon != null) {
      List<Word> lexical = lexicon.lookupAll(code);
      lexicalCount = lexical.size();
      for (Word w : lexical) {
        Word candidate =
            new Word(w.text, w.code, w.score + learnedBoost(learned.getOrDefault(w.text, 0)));
        Word old = found.get(w.text);
        if (old == null || old.score < candidate.score) found.put(w.text, candidate);
      }
      for (Word w : lexicon.corrections(code)) {
        correctionCount++;
        Word candidate = new Word(w.text, code,
            w.score + learnedBoost(learned.getOrDefault(w.text, 0)), w.spelling, w.correctionCost);
        Word old = found.get(w.text);
        if (old == null || old.score < candidate.score) found.put(w.text, candidate);
      }
    }
    int initialRank = 0;
    for (InitialLexicon.Entry entry : initials.lookup(code)) {
      double score =
          (code.matches("[b-df-hj-np-tv-z]{2,8}") ? 285 : 210)
              - initialRank++
              + learnedBoost(learned.getOrDefault(entry.text, 0));
      Word previous = found.get(entry.text);
      if (previous == null || previous.score < score)
        found.put(entry.text, new Word(entry.text, code, score));
    }
    for (Word w : personal) {
      String key = normalize(w.code);
      if (!key.startsWith(code)) continue;
      boolean exact = key.equals(code);
      found.put(
          w.text,
          new Word(
              w.text, code, (exact ? 400 : 180) + learnedBoost(learned.getOrDefault(w.text, 0))));
    }
    List<Word> out = new ArrayList<>(found.values());
    out.sort((a, b) -> Double.compare(b.score, a.score));
    if (emoji != null) {
      LinkedHashSet<String> pictographs = new LinkedHashSet<>();
      // Match the meanings of whole candidates, including corrected spellings. This uses the
      // same dictionary as the full grid; an emoji tap consumes the current pinyin once.
      for (int i = 0; i < Math.min(3, out.size()); i++) {
        Word word = out.get(i);
        if (word.code.equals(code)) pictographs.addAll(emoji.suggest(word.text, 2));
      }
      int index = Math.min(1, out.size()), count = 0;
      for (String text : pictographs) {
        if (found.containsKey(text)) continue;
        out.add(index++, new Word(text, code, 0, code, 0, Kind.EMOJI));
        if (++count == 2) break;
      }
    }
    cached = new Query(code, out, nativeResult.length / 2, lexicalCount, correctionCount);
    cachedLearned = new HashMap<>(learned);
    return cached;
  }

  /** Read only the Hanzi immediately before the caret. Punctuation, Latin and emoji end a
   * phrase; text after the caret is never treated as committed context. No history is saved. */
  public static String predictionSuffix(String before) {
    if (before == null) return "";
    int end = before.length(), start = end;
    while (start > 0 && end - start < 7) {
      char ch = before.charAt(start - 1);
      if (ch < 0x3400 || ch > 0x9fff) break;
      start--;
    }
    return before.substring(start, end);
  }

  /** Offline continuation, emoji and punctuation share one immutable candidate query. The
   * request input is the editor snapshot, used by the host to reject stale selections. */
  public synchronized Query predict(String before, Map<String, Integer> learned) {
    String tail = predictionSuffix(before);
    if (tail.isEmpty()) return new Query(before, Collections.emptyList(), 0, 0, 0, true);
    LinkedHashMap<String, Word> found = new LinkedHashMap<>();
    String[] nativeResult = nativePredictions(tail, 128);
    for (int i = 0; i < nativeResult.length; i++) {
      String text = nativeResult[i];
      if (!text.isEmpty()) found.put(text,
          new Word(text, "", 260 - i + learnedBoost(learned.getOrDefault(text, 0))));
    }
    List<Word> lexical = lexicon == null ? Collections.emptyList()
        : lexicon.continuations(tail, 128);
    for (Word word : lexical) {
      Word previous = found.get(word.text);
      Word next = new Word(word.text, "", word.score
          + learnedBoost(learned.getOrDefault(word.text, 0)));
      if (previous == null || previous.score < next.score) found.put(next.text, next);
    }
    for (Word word : personal) {
      for (int size = Math.min(tail.length(), word.text.length() - 1); size > 0; size--) {
        String prefix = tail.substring(tail.length() - size);
        if (!word.text.startsWith(prefix)) continue;
        String text = word.text.substring(size);
        Word next = new Word(text, "", 300 + 20 * size
            + learnedBoost(learned.getOrDefault(word.text, 0)));
        Word previous = found.get(text);
        if (previous == null || previous.score < next.score) found.put(text, next);
        break;
      }
    }
    List<Word> ranked = new ArrayList<>(found.values());
    ranked.sort((a, b) -> Double.compare(b.score, a.score));
    List<Word> out = new ArrayList<>();
    int first = Math.min(3, ranked.size());
    out.addAll(ranked.subList(0, first));
    List<String> pictographs = emoji == null ? Collections.emptyList() : emoji.after(tail, 2);
    // Three frequent continuations must stay ahead of symbol categories. Keep only one
    // pictograph in this leading group; the complete query still contains every suggestion.
    if (!pictographs.isEmpty())
      out.add(new Word(pictographs.get(0), "", 0, "", 0, Kind.EMOJI));
    for (String text : Arrays.asList("，", "。"))
      out.add(new Word(text, "", 0, "", 0, Kind.PUNCTUATION));
    for (int i = 1; i < pictographs.size(); i++)
      out.add(new Word(pictographs.get(i), "", 0, "", 0, Kind.EMOJI));
    out.addAll(ranked.subList(first, Math.min(128, ranked.size())));
    for (String text : Arrays.asList("？", "！", "、", "…"))
      out.add(new Word(text, "", 0, "", 0, Kind.PUNCTUATION));
    return new Query(before, out, nativeResult.length, lexical.size(), 0, true);
  }

  private static double learnedBoost(int count) {
    return Math.min(280, 45 * Math.log1p(Math.max(0, count)));
  }

  public synchronized void accept(String code, String word) {
    cached = null;
    if (word.codePoints().allMatch(c -> c >= 0x3400 && c <= 0x9fff)) nativeLearn(code, word);
  }

  public synchronized void clearLearning() {
    cached = null;
    personal.clear();
    if (!nativeClear(dictionaryPath, userPath)) throw new IllegalStateException("拼音词库重置失败");
  }
}
