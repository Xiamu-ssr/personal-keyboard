package app.doudou.ime.input.engine;

import java.util.*;

/** AOSP's offline sentence decoder plus a separately replaceable personal lexicon. */
public final class PinyinEngine {
  static {
    System.loadLibrary("doudou_decoder");
  }

  private static native boolean nativeOpen(String systemDictionary, String userDictionary);

  private static native String[] nativeCandidates(String input, int limit);

  private static native void nativeLearn(String input, String selected);

  private static native boolean nativeClear(String dictionary, String user);

  private final String dictionaryPath, userPath;
  private final InitialLexicon initials;

  public interface Lexicon {
    List<Word> lookup(String input, int limit);
  }

  private Lexicon lexicon;

  public synchronized void lexicon(Lexicon value) {
    lexicon = value;
  }

  public static final class Word {
    public final String text, code;
    public final double score;

    public Word(String t, String c, double s) {
      text = t;
      code = c;
      score = s;
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
    personal.clear();
    personal.addAll(words);
  }

  public static String normalize(String s) {
    return s.toLowerCase(Locale.ROOT)
        .replace("ü", "v")
        .replace("u:", "v")
        .replaceAll("[^a-z']", "");
  }

  public synchronized List<Word> candidates(String input, int limit, Map<String, Integer> learned) {
    String code = normalize(input);
    if (code.isEmpty() || code.length() > 64) return Collections.emptyList();
    LinkedHashMap<String, Word> found = new LinkedHashMap<>();
    String[] nativeResult = nativeCandidates(code, Math.max(limit, 20));
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
    if (lexicon != null)
      for (Word w : lexicon.lookup(code, limit)) {
        Word candidate =
            new Word(w.text, w.code, w.score + learnedBoost(learned.getOrDefault(w.text, 0)));
        Word old = found.get(w.text);
        if (old == null || old.score < candidate.score) found.put(w.text, candidate);
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
    if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
    return out;
  }

  private static double learnedBoost(int count) {
    return Math.min(280, 45 * Math.log1p(Math.max(0, count)));
  }

  public synchronized void accept(String code, String word) {
    if (word.codePoints().allMatch(c -> c >= 0x3400 && c <= 0x9fff)) nativeLearn(code, word);
  }

  public synchronized void clearLearning() {
    personal.clear();
    if (!nativeClear(dictionaryPath, userPath)) throw new IllegalStateException("拼音词库重置失败");
  }
}
