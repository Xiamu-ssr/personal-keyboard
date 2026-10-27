package app.doudou.ime.input;

import static org.junit.Assert.*;
import app.doudou.ime.input.engine.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.*;

/** Real decoder predictions, Unicode boundaries, full-query emoji and continuation contracts. */
public class ContinuationTest {
  private PinyinEngine engine;

  @Before public void open() throws Exception {
    engine = new PinyinEngine(new File("src/main/assets/dict_pinyin.dat").getAbsolutePath(),
        new File("build/continuation-dictionary.user").getAbsolutePath(),
        InitialLexicon.read(new FileReader("src/main/assets/initials.tsv")));
    engine.emoji(EmojiLexicon.read(new InputStreamReader(
        new FileInputStream("src/main/assets/emoji-keywords.tsv"), "UTF-8")));
  }

  @Test public void selectingACharacterOffersSuffixesAndPunctuation() {
    PinyinEngine.Query query = engine.predict("你", Collections.emptyMap());
    assertTrue(query.continuation);
    assertEquals("你", query.input);
    assertTrue(query.nativeCount > 0);
    assertTrue(query.page(0, query.size()).stream().anyMatch(w -> w.text.equals("好")));
    assertTrue(query.page(0, query.size()).stream().anyMatch(w -> w.text.equals("，")));
    assertTrue(query.page(0, query.size()).stream().allMatch(w -> w.code.isEmpty()));
    assertTrue(engine.predict("你好", Collections.emptyMap()).size() > 0);
    assertTrue(engine.predict("你好，", Collections.emptyMap()).size() == 0);
  }

  @Test public void anEmojiConsumesPinyinWithoutReplacingItsTextCandidate() {
    PinyinEngine.Query query = engine.query("shengri", Collections.emptyMap());
    List<PinyinEngine.Word> all = query.page(0, query.size());
    assertTrue(all.stream().anyMatch(w -> w.kind == PinyinEngine.Kind.WORD && w.text.equals("生日")));
    PinyinEngine.Word emoji = all.stream().filter(w -> w.kind == PinyinEngine.Kind.EMOJI)
        .findFirst().orElseThrow(AssertionError::new);
    assertEquals("shengri", emoji.code);
    assertTrue("Emoji appears in the visible strip, not only a separately patched panel",
        all.indexOf(emoji) < 3);
    assertTrue(engine.predict("生日", Collections.emptyMap()).page(0, 8).stream()
        .anyMatch(w -> w.kind == PinyinEngine.Kind.EMOJI));
  }

  @Test public void predictionsUseOnlyTheCurrentHanziSuffixAndNeverSplitSurrogates() {
    assertEquals("你好", PinyinEngine.predictionSuffix("hello😀你好"));
    assertEquals("", PinyinEngine.predictionSuffix("你好😀"));
    assertEquals("", PinyinEngine.predictionSuffix("hello"));
    assertEquals("", PinyinEngine.predictionSuffix("123"));
    assertEquals("", PinyinEngine.predictionSuffix(null));
    assertEquals(7, PinyinEngine.predictionSuffix("一二三四五六七八九十").length());
    for (String before : Arrays.asList("", " ", "hello", "123", "你好😀", "你好。"))
      assertEquals(before, 0, engine.predict(before, Collections.emptyMap()).size());
  }

  @Test public void nativeBoundaryRejectsUnboundedOrNonHanziBuffers() throws Exception {
    Method nativeMethod = PinyinEngine.class.getDeclaredMethod("nativePredictions", String.class, int.class);
    nativeMethod.setAccessible(true);
    for (String text : Arrays.asList(null, "", "你好\u0000hello", "😀", "一二三四五六七八九十", "hello"))
      assertEquals(0, ((String[]) nativeMethod.invoke(null, text, 128)).length);
    assertEquals(0, ((String[]) nativeMethod.invoke(null, "你", -1)).length);
  }

  @Test public void theLexiconAndPersonalWordsAppendOnlyTheirUncommittedSuffix() {
    engine.lexicon(new PinyinEngine.Lexicon() {
      public List<PinyinEngine.Word> lookup(String input, int limit) { return Collections.emptyList(); }
      public List<PinyinEngine.Word> continuations(String history, int limit) {
        return Arrays.asList(new PinyinEngine.Word("们", "", 400));
      }
    });
    engine.personalWords(Arrays.asList(new PinyinEngine.Word("豆豆输入法", "doudoushurufa", 26)));
    List<PinyinEngine.Word> query = engine.predict("豆豆", Collections.emptyMap()).page(0, 200);
    assertTrue(query.stream().anyMatch(w -> w.text.equals("输入法")));
    assertFalse(query.stream().anyMatch(w -> w.text.equals("豆豆输入法")));
    assertEquals(1, query.stream().filter(w -> w.text.equals("们")).count());
  }
}
