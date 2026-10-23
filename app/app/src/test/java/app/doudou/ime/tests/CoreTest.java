package app.doudou.ime.tests;

import static org.junit.Assert.*;

import app.doudou.ime.advisor.AdvisorSession;
import app.doudou.ime.advisor.ReplyHistory;
import app.doudou.ime.input.engine.InitialLexicon;
import app.doudou.ime.input.engine.PinyinEngine;
import app.doudou.ime.input.learning.EditTracker;
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
            new File("build/test-dictionary.user").getAbsolutePath(),
            InitialLexicon.read(new FileReader("src/main/assets/initials.tsv")));
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
  public void undoAvailabilityCanBeCheckedWithoutConsumingTheInsertion() {
    EditTracker t = new EditTracker();
    t.begin("前文", 2, 2, "回复", 3);
    assertTrue(t.canUndo("前文回复", 3));
    assertTrue(t.canUndo("前文回复", 3));
    assertFalse(t.canUndo("前文回复！", 3));
    assertFalse(t.canUndo("前文回复", 4));
    assertFalse(t.canUndo(null, 3));
    assertEquals("前文", t.undo("前文回复", 3));
    assertFalse(t.canUndo("前文回复", 3));
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
  public void screenshotCanGenerateWithoutInventingAPerson() {
    AdvisorSession s = new AdvisorSession();
    s.begin(1, "chat");
    assertFalse(s.canGenerate());
    s.image("jpeg-one");
    assertTrue(s.canGenerate());
    assertEquals("", s.conversationRef());
    s.identify("person-id", "小林");
    assertEquals("chat:person-id", s.conversationRef());
  }

  @Test
  public void selectionInvalidatesPendingInference() {
    AdvisorSession s = new AdvisorSession();
    int old = s.begin(1, "chat");
    s.image("jpeg");
    s.identify("person-id", "小林");
    assertFalse(s.valid(old, 1));
    assertTrue(s.valid(s.token(), 1));
  }

  @Test
  public void newJobCannotKeepOldPersonOrScreenshot() {
    AdvisorSession s = new AdvisorSession();
    s.begin(1, "a");
    s.image("jpeg");
    s.identify("id", "name");
    s.begin(2, "b");
    assertEquals("", s.personId);
    assertEquals(0, s.pages());
    assertFalse(s.canGenerate());
  }

  @Test
  public void backRestoresExactReplyStyleWithoutMutation() {
    ReplyHistory h = new ReplyHistory();
    h.accept(Arrays.asList("a", "b", "c"), "", false, "first-evidence");
    h.accept(Arrays.asList("d", "e", "f"), "温和", true, "next-evidence");
    h.accept(Arrays.asList("g", "h", "i"), "温和", true, "next-evidence");
    h.back();
    assertEquals("d", h.replies().get(0));
    assertEquals("温和", h.style());
    h.back();
    assertEquals("a", h.replies().get(0));
    assertEquals("first-evidence", h.messages());
    assertEquals("", h.style());
    assertFalse(h.canBack());
  }

  @Test
  public void screenshotLimitDoesNotDiscardEarlierScreens() {
    AdvisorSession a = new AdvisorSession();
    a.begin(1, "chat");
    a.image("one");
    a.image("two");
    a.image("three");
    try {
      a.image("four");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(Arrays.asList("one", "two", "three"), a.images());
  }

  @Test
  public void completeResultObservationIncludesEditsButNeverClearOrAnotherEditor() {
    EditTracker t = new EditTracker();
    t.begin("前文，后文", 3, 3, "豆包", 7);
    assertEquals("豆豆啊", t.observedText("前文，豆豆啊后文", 7));
    assertNull(t.observedText("前文，后文", 7));
    assertNull(t.observedText("前文，豆豆后文", 8));
  }

  @Test
  public void initialsRecallModernExpressionWithoutGlobalReplacement() {
    List<PinyinEngine.Word> list = engine.candidates("lll", 12, Collections.emptyMap());
    assertTrue(list.stream().anyMatch(w -> w.text.equals("六六六") && w.code.equals("lll")));
    assertTrue(list.stream().anyMatch(w -> !w.text.equals("六六六")));
    assertTrue(
        engine.candidates("nh", 12, Collections.emptyMap()).stream()
            .anyMatch(w -> w.text.equals("你好")));
  }

  @Test
  public void aPersonsSelectionCanPromoteANativeAlternative() {
    List<PinyinEngine.Word> base = engine.candidates("shi", 12, Collections.emptyMap());
    String alternate = base.get(1).text;
    Map<String, Integer> learned = new HashMap<>();
    learned.put(alternate, 20);
    assertEquals(alternate, engine.candidates("shi", 12, learned).get(0).text);
    assertNotEquals(alternate, engine.candidates("shi", 12, Collections.emptyMap()).get(0).text);
  }

  @Test
  public void personalShortcutCanBeAddedAndRemoved() {
    engine.personalWords(Arrays.asList(new PinyinEngine.Word("我自己的表达", "lll", 1)));
    assertEquals("我自己的表达", engine.candidates("lll", 12, Collections.emptyMap()).get(0).text);
    engine.personalWords(Collections.emptyList());
    assertFalse(
        engine.candidates("lll", 12, Collections.emptyMap()).stream()
            .anyMatch(w -> w.text.equals("我自己的表达")));
  }
}
