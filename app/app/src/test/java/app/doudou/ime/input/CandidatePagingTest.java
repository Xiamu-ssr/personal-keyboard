package app.doudou.ime.input;

import static org.junit.Assert.*;
import app.doudou.ime.input.engine.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.*;

/** Real JNI dictionary, including later native pages and original-input consumption. */
public class CandidatePagingTest {
  private static PinyinEngine engine;

  @BeforeClass public static void open() throws Exception {
    engine = new PinyinEngine(new File("src/main/assets/dict_pinyin.dat").getAbsolutePath(),
        new File("build/paging-dictionary.user").getAbsolutePath(),
        InitialLexicon.read(new FileReader("src/main/assets/initials.tsv")));
  }

  @Test public void allPagesAreReachableWithoutDuplicatesOrRankChanges() {
    PinyinEngine.Query query = engine.query("shi", Collections.emptyMap());
    assertTrue("Native dictionary has later pages", query.nativeCount > 48);
    List<PinyinEngine.Word> pages = new ArrayList<>();
    for (int offset = 0; offset < query.size(); offset += 17) pages.addAll(query.page(offset, 17));
    assertEquals(query.size(), pages.size());
    Set<String> distinct = new HashSet<>();
    for (int i = 0; i < pages.size(); i++) {
      assertTrue(distinct.add(pages.get(i).text));
      assertEquals(query.page(0, Integer.MAX_VALUE).get(i).text, pages.get(i).text);
      assertTrue("shi".startsWith(pages.get(i).code));
    }
    assertTrue(query.page(query.size(), 48).isEmpty());
    assertTrue(query.page(Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
  }

  @Test public void nativeOffsetsReturnTheSameEntriesAsTheWholeSearch() throws Exception {
    Method method = PinyinEngine.class.getDeclaredMethod(
        "nativeCandidates", String.class, int.class, int.class);
    method.setAccessible(true);
    String[] full = (String[]) method.invoke(null, "shi", 0, Integer.MAX_VALUE);
    String[] later = (String[]) method.invoke(null, "shi", 48, 17);
    assertArrayEquals(Arrays.copyOfRange(full, 96, 130), later);
    assertEquals(0, ((String[]) method.invoke(null, "shi", -1, 48)).length);
  }

  @Test public void prefixSelectionStillLeavesTheUnmatchedSuffix() {
    PinyinEngine.Query query = engine.query("woxiangni", Collections.emptyMap());
    PinyinEngine.Word word = query.page(0, query.size()).stream()
        .filter(w -> w.text.equals("我想")).findFirst().get();
    assertEquals("woxiang", word.code);
    assertEquals("ni", "woxiangni".substring(word.code.length()));
  }
}
