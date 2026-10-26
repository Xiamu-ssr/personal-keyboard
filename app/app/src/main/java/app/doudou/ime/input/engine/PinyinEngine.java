package app.doudou.ime.input.engine;

import java.util.*;

/** AOSP's offline sentence decoder plus a separately replaceable personal lexicon. */
public final class PinyinEngine {
  static {
    System.loadLibrary("doudou_decoder");
  }

  private static native boolean nativeOpen(String systemDictionary, String userDictionary);

  private static native String[] nativeCandidates(String input, int offset, int limit);

  private static native void nativeLearn(String input, String selected);

  private static native boolean nativeClear(String dictionary, String user);

  private final String dictionaryPath, userPath;
  private final InitialLexicon initials;

  public interface Lexicon {
    List<Word> lookup(String input, int limit);

    /** All matching rows, independent of the candidate strip's visible item count. */
    default List<Word> lookupAll(String input) { return lookup(input, Integer.MAX_VALUE); }

    default List<Word> corrections(String input) { return Collections.emptyList(); }
  }

  private Lexicon lexicon;
  private Query cached;
  private Map<String, Integer> cachedLearned = Collections.emptyMap();

  public synchronized void lexicon(Lexicon value) {
    lexicon = value;
    cached = null;
  }

  public static final class Word {
    /** code always consumes the original input; spelling may describe a corrected lookup. */
    public final String text, code, spelling;
    public final double score, correctionCost;

    public Word(String t, String c, double s) {
      this(t, c, s, c, 0);
    }

    public Word(String t, String c, double s, String spelling, double cost) {
      text = t;
      code = c;
      score = s;
      this.spelling = spelling;
      correctionCost = cost;
    }
  }

  /** Immutable query snapshot; paging cannot change ranks or lose a dictionary's later rows. */
  public static final class Query {
    public final String input;
    public final int nativeCount, lexiconCount, correctionCount;
    private final List<Word> words;

    Query(String input, List<Word> words, int nativeCount, int lexiconCount, int correctionCount) {
      this.input = input;
      this.words = Collections.unmodifiableList(new ArrayList<>(words));
      this.nativeCount = nativeCount;
      this.lexiconCount = lexiconCount;
      this.correctionCount = correctionCount;
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
    cached = new Query(code, out, nativeResult.length / 2, lexicalCount, correctionCount);
    cachedLearned = new HashMap<>(learned);
    return cached;
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
