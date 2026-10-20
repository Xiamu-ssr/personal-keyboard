package app.doudou.ime.core;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.junit.*;

public class CoreTest {
  private static PinyinEngine engine;

  @BeforeClass
  public static void dictionary() throws Exception {
    engine =
        new PinyinEngine(
            new File("src/main/assets/dict_pinyin.dat").getAbsolutePath(),
            new File("build/test-dictionary.user").getAbsolutePath());
  }

  @Before
  public void reset() {
    engine.personalWords(Collections.emptyList());
  }

  @Test
  public void commonChinese() {
    assertEquals("你好", engine.candidates("nihao", 8, Collections.emptyMap()).get(0).text);
    assertTrue(
        engine.candidates("zaijian", 8, Collections.emptyMap()).stream()
            .anyMatch(x -> x.text.equals("再见")));
  }

  @Test
  public void composesAcrossWords() {
    List<PinyinEngine.Word> w = engine.candidates("woxiangxiacizaijian", 8, Collections.emptyMap());
    assertFalse(w.isEmpty());
    assertTrue(w.stream().anyMatch(x -> x.text.contains("我") && x.text.contains("再见")));
  }

  @Test
  public void personalWordCanBeRemoved() {
    engine.personalWords(Arrays.asList(new PinyinEngine.Word("豆豆输入法", "doudoushurufa", 26)));
    assertEquals(
        "豆豆输入法", engine.candidates("doudoushurufa", 3, Collections.emptyMap()).get(0).text);
    engine.personalWords(Collections.emptyList());
    assertFalse(
        engine.candidates("doudoushurufa", 12, Collections.emptyMap()).stream()
            .anyMatch(x -> x.text.equals("豆豆输入法")));
  }

  @Test
  public void learningChangesHomophoneRank() {
    engine.personalWords(
        Arrays.asList(
            new PinyinEngine.Word("林然", "linran", 26), new PinyinEngine.Word("琳然", "linran", 26)));
    Map<String, Integer> freq = new HashMap<>();
    freq.put("琳然", 50);
    assertEquals("琳然", engine.candidates("linran", 5, freq).get(0).text);
  }

  @Test
  public void umlautAndUnknown() {
    assertFalse(engine.candidates("lü", 4, Collections.emptyMap()).isEmpty());
    for (PinyinEngine.Word w :
        engine.candidates("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", 4, Collections.emptyMap()))
      assertTrue("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".startsWith(w.code));
  }

  @Test
  public void arbitraryCompositionNeverConsumesUnmatchedSuffix() {
    Random random = new Random(12345);
    for (int i = 0; i < 200; i++) {
      StringBuilder raw = new StringBuilder();
      int length = 1 + random.nextInt(39);
      for (int j = 0; j < length; j++) raw.append((char) ('a' + random.nextInt(26)));
      for (PinyinEngine.Word w : engine.candidates(raw.toString(), 6, Collections.emptyMap())) {
        assertFalse(w.code.isEmpty());
        assertTrue(raw.toString().startsWith(w.code));
      }
    }
  }

  @Test
  public void sentenceAndPartialCandidateKeepRemainingSpelling() {
    List<PinyinEngine.Word> w = engine.candidates("woxiangni", 8, Collections.emptyMap());
    assertEquals("我想你", w.get(0).text);
    PinyinEngine.Word prefix = w.stream().filter(x -> x.text.equals("我想")).findFirst().get();
    assertEquals("woxiang", prefix.code);
    assertEquals("ni", "woxiangni".substring(prefix.code.length()));
  }

  @Test
  public void repeatedInitialsAndLongSpellingAreBounded() {
    for (char letter = 'a'; letter <= 'z'; letter++) {
      for (int length = 1; length <= 39; length++) {
        char[] chars = new char[length];
        Arrays.fill(chars, letter);
        String raw = new String(chars);
        for (PinyinEngine.Word w : engine.candidates(raw, 6, Collections.emptyMap()))
          assertTrue(raw.startsWith(w.code));
      }
    }
  }

  @Test
  public void emptyAndBounded() {
    assertTrue(engine.candidates("", 5, Collections.emptyMap()).isEmpty());
    assertTrue(engine.candidates("shi", 3, Collections.emptyMap()).size() <= 3);
  }

  @Test
  public void undoRestoresSelectedText() {
    EditTracker t = new EditTracker();
    t.begin("原有草稿", 2, 4, "文字", 11);
    assertEquals("原有草稿", t.undo("原有文字", 11));
    assertFalse(t.active());
  }

  @Test
  public void undoCannotOverwriteEditsOrOtherEditor() {
    EditTracker t = new EditTracker();
    t.begin("之前", 2, 2, "你好", 1);
    assertNull(t.undo("之前你好啊", 1));
    assertNull(t.undo("之前你好", 2));
    assertEquals("之前", t.undo("之前你好", 1));
  }

  @Test
  public void clearOnSendIsUnknown() {
    EditTracker t = new EditTracker();
    t.begin("", 0, 0, "今天见", 1);
    assertNull(t.observe("", 1));
    assertNull(t.observe(null, 1));
    assertNull(t.observe("今天见", 2));
  }

  @Test
  public void correctionAnchoredToOwnInsertion() {
    EditTracker t = new EditTracker();
    t.begin("前文，后文", 3, 3, "豆包", 7);
    EditTracker.Change c = t.observe("前文，豆豆后文", 7);
    assertNotNull(c);
    assertEquals("豆包", c.before);
    assertEquals("豆豆", c.after);
    assertNull(t.observe("改变前文豆豆后文", 7));
  }

  @Test
  public void unicodeDiffKeepsCodePoints() {
    EditTracker.Change c = EditTracker.diff("你好😀呀", "你好🙂呀");
    assertEquals("😀", c.before);
    assertEquals("🙂", c.after);
  }

  @Test
  public void appendedTextIsNotAReplacement() {
    EditTracker t = new EditTracker();
    t.begin("", 0, 0, "你好", 1);
    EditTracker.Change c = t.observe("你好啊", 1);
    assertNull(c);
  }

  @Test
  public void cancellationDiscardsLateResults() {
    AdvisorSession s = new AdvisorSession();
    int id = s.begin(4, "chat.a");
    assertTrue(s.valid(id, 4));
    s.cancel();
    assertFalse(s.valid(id, 4));
    int next = s.begin(5, "chat.b");
    assertFalse(s.valid(id, 5));
    assertFalse(s.valid(next, 4));
  }

  @Test
  public void identityRequiredAndContextDeduplicated() {
    AdvisorSession s = new AdvisorSession();
    s.begin(1, "chat");
    s.append("今天几点\n你好");
    s.append("你好\n下次再说");
    assertEquals("今天几点\n你好\n下次再说", s.context());
    assertFalse(s.canGenerate());
    s.identify(17);
    assertTrue(s.canGenerate());
  }

  @Test(expected = IllegalArgumentException.class)
  public void cannotGuessPerson() {
    new AdvisorSession().identify(0);
  }

  @Test
  public void newJobCannotKeepOldPerson() {
    AdvisorSession s = new AdvisorSession();
    s.begin(1, "a");
    s.append("消息");
    s.identify(2);
    s.begin(2, "b");
    assertEquals(0, s.person);
    assertEquals("", s.context());
  }

  @Test
  public void backRestoresExactReplyStyleWithoutMutation() {
    ReplyHistory h = new ReplyHistory();
    h.accept(Arrays.asList("a", "b", "c"), "", false);
    h.accept(Arrays.asList("d", "e", "f"), "温和", true);
    h.accept(Arrays.asList("g", "h", "i"), "温和", true);
    h.back();
    assertEquals("d", h.replies().get(0));
    assertEquals("温和", h.style());
    h.back();
    assertEquals("a", h.replies().get(0));
    assertEquals("", h.style());
    assertFalse(h.canBack());
  }

  @Test
  public void oversizedContextDoesNotOverwriteExistingCapture() {
    AdvisorSession a = new AdvisorSession();
    a.begin(1, "chat");
    a.append("原文");
    char[] chars = new char[16001];
    Arrays.fill(chars, '字');
    try {
      a.append(new String(chars));
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals("原文", a.context());
    assertEquals(1, a.pages());
  }
}
