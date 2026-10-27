package app.doudou.ime.input.engine;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.BeforeClass;
import org.junit.Test;

/** Standalone JVM tests: no Android, JNI, editor, model, network or Gradle required. */
public class EmojiLexiconTest {
  private static EmojiLexicon real;
  private static Path asset;

  @BeforeClass public static void loadRealCldrAsset() throws Exception {
    String supplied = System.getProperty("emoji.lexicon.asset");
    if (supplied != null) asset = Paths.get(supplied);
    else {
      for (String path : Arrays.asList("src/main/assets/emoji-keywords.tsv",
          "app/src/main/assets/emoji-keywords.tsv", "app/app/src/main/assets/emoji-keywords.tsv")) {
        if (Files.isRegularFile(Paths.get(path))) { asset = Paths.get(path); break; }
      }
    }
    assertNotNull("Pass -Demoji.lexicon.asset=/absolute/path/to/emoji-keywords.tsv", asset);
    try (Reader reader = Files.newBufferedReader(asset, StandardCharsets.UTF_8)) {
      real = EmojiLexicon.read(reader);
    }
  }

  @Test public void realHighFrequencyChineseKeywordsUseOfficialSemantics() {
    assertEquals(Arrays.asList("☺️", "😊"), real.suggest("微笑", 2));
    assertEquals(Arrays.asList("😄", "😆"), real.suggest("开心", 2));
    assertEquals(Arrays.asList("😄", "😆"), real.suggest("笑", 2));
    assertEquals(Arrays.asList("😃", "😄"), real.suggest("哈哈", 2));
    assertEquals(Collections.singletonList("🙏"), real.suggest("谢谢", 2));
    assertEquals(Arrays.asList("🎂", "🥳"), real.suggest("生日", 2));
    assertEquals(Arrays.asList("😴", "🥱"), real.suggest("晚安", 2));
    assertEquals(Collections.singletonList("☕"), real.suggest("咖啡", 2));
    // These are the actual CLDR 爱心 mappings; red heart is tagged 红心/心/爱.
    assertEquals(Arrays.asList("🤎", "🤍"), real.suggest("爱心", 2));
    assertTrue(real.suggest("红心", 2).contains("❤️"));
    assertEquals(Collections.singletonList("🎂"), real.suggest("生日快乐", 2));
  }

  @Test public void realEnglishAndDerivedNamesAreIndexedToo() {
    assertEquals(Collections.singletonList("☕"), real.suggest("COFFEE", 2));
    assertEquals(Arrays.asList("😀", "😃"), real.suggest("smile", 2));
    assertEquals(Arrays.asList("🎂", "🥳"), real.suggest("birthday", 2));
    assertEquals(Arrays.asList("🙏", "🫂"), real.suggest("thanks", 2));
    assertEquals(Collections.singletonList("👩🏽‍💻"), real.suggest("女程序员: 中等肤色", 2));
    assertEquals(Collections.singletonList("🇨🇳"), real.suggest("旗: 中国", 2));
  }

  @Test public void realDataEvidenceForComposingAndPostCommit() {
    for (String keyword : Arrays.asList("微笑", "开心", "笑", "哈哈", "谢谢", "生日", "晚安", "咖啡", "爱心"))
      System.out.println("CLDR_PROBE\t" + keyword + "\tsuggest=" + real.suggest(keyword, 2)
          + "\tafter=" + real.after(keyword, 2));
    assertTrue(real.suggest("shengri", 2).isEmpty()); // Caller resolves pinyin to Chinese first.
    assertTrue(real.suggest("晚安啦啦啦", 2).isEmpty()); // No invented colloquial aliases.
  }

  @Test public void everyGeneratedRowRoundTripsThroughThePublicReader() throws Exception {
    int rows = 0, mappings = 0;
    for (String line : Files.readAllLines(asset, StandardCharsets.UTF_8)) {
      if (!line.contains("\t") || line.startsWith("# ")) continue;
      String[] fields = line.split("\t", -1);
      List<String> expected = Arrays.asList(fields[1].split(" "));
      assertEquals(fields[0], expected, real.suggest(fields[0], Integer.MAX_VALUE));
      rows++;
      mappings += expected.size();
    }
    assertTrue("Full semantic corpus, not a tiny phrase list", rows > 10000);
    System.out.println("CLDR_ROUND_TRIP\tkeywords=" + rows + "\tmappings=" + mappings);
  }

  @Test public void afterIsOnlyALongestCompleteSuffixAndIsCappedAtTwo() throws Exception {
    EmojiLexicon lexicon = fixture("生日\t🎂 🥳 🎉\n生日快乐\t🎂 🎉 🥳\n快乐\t😄\n");
    assertEquals(Arrays.asList("🎂", "🎉"), lexicon.after("今天，生日快乐！", 100));
    assertEquals(Collections.singletonList("🎂"), lexicon.after("生日快乐", 1));
    assertTrue(lexicon.after("生日快乐明天见", 2).isEmpty());
    assertTrue(lexicon.after("今天生日", 2).isEmpty());
    assertTrue(lexicon.after("快乐生日派对", 2).isEmpty());
    assertEquals(Arrays.asList("🎂", "🥳"), lexicon.after("生日", 2));
  }

  @Test public void realAfterDoesNotGuessChineseWordBoundariesOrSentiment() {
    assertEquals(Arrays.asList("🎂", "🥳"), real.after("生日！", 2));
    assertEquals(Collections.singletonList("🙏"), real.after("好的，谢谢！", 2));
    assertTrue(real.after("今天很开心", 2).isEmpty());
    assertTrue(real.after("我要喝咖啡", 2).isEmpty());
    // 不开心 itself is in CLDR; it must use that complete sad keyword.
    assertEquals("🙁", real.after("不开心", 2).get(0));
    assertTrue(Collections.disjoint(real.after("不开心", 2), real.suggest("开心", 2)));
    assertTrue(real.after("生日以后再聊zzzz", 2).isEmpty());
  }

  @Test public void englishSuffixUsesWholeWordsIncludingMultiwordNames() throws Exception {
    EmojiLexicon lexicon = fixture("happy\t😄\ncoffee\t☕\ncake\t🍰\nbirthday cake\t🎂 🎉\n");
    assertEquals(Collections.singletonList("☕"), lexicon.after("I want coffee.", 2));
    assertEquals(Arrays.asList("🎂", "🎉"), lexicon.after("I brought birthday cake!", 2));
    for (String text : Arrays.asList("unhappy", "decafcoffee", "some_coffee", "我coffee",
        "https://coffee", "coffee later", "coffee🙂"))
      assertTrue(text, lexicon.after(text, 2).isEmpty());
  }

  @Test public void punctuationDoesNotSearchAcrossFinalNewlines() {
    assertEquals(Collections.singletonList("☕"), real.after("“ＣＯＦＦＥＥ” ！", 2));
    for (String text : Arrays.asList("谢谢\n", "谢谢\r\n", "谢谢\u2028", "谢谢\n ", "！？", ""))
      assertTrue(text, real.after(text, 2).isEmpty());
    assertEquals(Collections.singletonList("🙏"), real.after("前一句\n谢谢", 2));
  }

  @Test public void directLookupIsExactAndNormalizesCaseWidthAndWhitespace() {
    assertEquals(real.suggest("coffee", 2), real.suggest("　ＣＯＦＦＥＥ\u00a0", 2));
    assertEquals(real.suggest("birthday cake", 2), real.suggest("  BIRTHDAY\t  CAKE  ", 2));
    for (String key : Arrays.asList("今天生日", "coffee!", "生日啦啦", "微", "coff", ""))
      assertTrue(key, real.suggest(key, 2).isEmpty());
  }

  @Test public void normalizationDoesNotDependOnDeviceLocale() throws Exception {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      EmojiLexicon lexicon = fixture("SMILING\t😊\n");
      assertEquals(Collections.singletonList("😊"), lexicon.suggest("smiling", 2));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test public void shortKeysRemainExplicitOnlyAndLimitsHandleNullOrNegative() throws Exception {
    EmojiLexicon lexicon = fixture("笑\t😄\nok\t👌\n12\t🕛\n");
    assertFalse(lexicon.suggest("笑", 2).isEmpty());
    for (String text : Arrays.asList("笑", "ok", "12")) assertTrue(lexicon.after(text, 2).isEmpty());
    assertTrue(real.after(null, 2).isEmpty());
    assertTrue(real.suggest(null, 2).isEmpty());
    for (int limit : new int[] {0, -1, Integer.MIN_VALUE}) {
      assertTrue(real.suggest("生日", limit).isEmpty());
      assertTrue(real.after("生日", limit).isEmpty());
    }
  }

  @Test public void duplicateRowsMergeWithoutBreakingUnicodeEmojiSequences() throws Exception {
    EmojiLexicon lexicon = fixture("\ufeff# header\r\n\r\nCOFFEE\t☕ ☕\r\ncoffee\t☕ 👩🏽‍💻 ❤️ 🇨🇳 1️⃣\r\n#\t#️⃣\n");
    assertEquals(Arrays.asList("☕", "👩🏽‍💻", "❤️", "🇨🇳", "1️⃣"), lexicon.suggest("coffee", 20));
    assertEquals(Collections.singletonList("#️⃣"), lexicon.suggest("#", 2));
  }

  @Test public void returnedListsCannotMutateTheSharedDictionary() {
    List<String> first = real.suggest("生日", 1);
    try { first.add("☕"); fail("Expected immutable list"); }
    catch (UnsupportedOperationException expected) { /* immutable snapshot */ }
    try { real.after("生日", 2).set(0, "☕"); fail("Expected immutable list"); }
    catch (UnsupportedOperationException expected) { /* immutable snapshot */ }
    assertEquals("🎂", real.suggest("生日", 1).get(0));
    assertEquals(5, real.suggest("生日", 100).size());
  }

  @Test public void readerOwnershipAndIoErrorsStayWithTheCaller() throws Exception {
    final boolean[] closed = {false};
    StringReader reader = new StringReader("生日\t🎂\n") {
      @Override public void close() { closed[0] = true; super.close(); }
    };
    EmojiLexicon.read(reader);
    assertFalse(closed[0]);
    reader.close();
    assertTrue(closed[0]);
    Reader broken = new Reader() {
      @Override public int read(char[] target, int offset, int length) throws IOException {
        throw new IOException("fixture read failure");
      }
      @Override public void close() { fail("Caller owns Reader even on failure"); }
    };
    try { EmojiLexicon.read(broken); fail("Expected propagated IOException"); }
    catch (IOException expected) { assertEquals("fixture read failure", expected.getMessage()); }
  }

  @Test public void malformedRowsAndUnpairedSurrogatesFailWithLineOnly() throws Exception {
    for (String row : Arrays.asList("生日", "\t🎂", "生日\t", "生日\t🎂\textra",
        "生日\t\ud83d", "\ud83d\t🎂", "生日\t🎂\u0000")) {
      try { fixture("# fixture\n" + row + "\n"); fail("Expected malformed row"); }
      catch (IOException expected) { assertEquals("Malformed emoji lexicon row at line 2", expected.getMessage()); }
    }
  }

  @Test public void longContextsDoNotCreateABoundaryAtTheArtificialCut() {
    char[] prefix = new char[2000];
    Arrays.fill(prefix, 'x');
    assertTrue(real.after(new String(prefix) + "coffee", 2).isEmpty());
    assertEquals(Collections.singletonList("☕"), real.after(new String(prefix) + " coffee", 2));
    assertTrue(real.after(new String(prefix) + "开心", 2).isEmpty());
  }

  @Test public void emptyLexiconsRemainUsableAndIndependent() throws Exception {
    EmojiLexicon empty = fixture("# no keywords\n\n");
    assertTrue(empty.suggest("生日", 2).isEmpty());
    assertTrue(empty.after("生日", 2).isEmpty());
    assertFalse(real.suggest("生日", 2).isEmpty());
  }

  private static EmojiLexicon fixture(String text) throws IOException {
    return EmojiLexicon.read(new StringReader(text));
  }
}
