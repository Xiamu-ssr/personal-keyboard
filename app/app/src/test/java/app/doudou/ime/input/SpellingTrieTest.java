package app.doudou.ime.input;

import static org.junit.Assert.*;
import app.doudou.ime.input.engine.SpellingTrie;
import java.util.*;
import org.junit.Test;

public class SpellingTrieTest {
  @Test public void recallsUnseenDictionaryKeysAcrossAllFourEditOperations() {
    Random random = new Random(72831);
    List<String> keys = new ArrayList<>();
    SpellingTrie trie = new SpellingTrie();
    for (int n = 0; n < 120; n++) {
      StringBuilder key = new StringBuilder("a");
      while (key.length() < 6 + n % 7) key.append((char) ('a' + random.nextInt(26)));
      keys.add(key.toString());
      trie.add(key.toString(), n + 1);
    }
    trie.seal();
    for (String key : keys) {
      int at = 2;
      List<String> errors = Arrays.asList(
          key.substring(0, at) + key.substring(at + 1),
          key.substring(0, at) + 'z' + key.substring(at),
          key.substring(0, at) + (key.charAt(at) == 'z' ? 'x' : 'z') + key.substring(at + 1),
          key.substring(0, at) + key.charAt(at + 1) + key.charAt(at) + key.substring(at + 2));
      for (String error : errors) {
        if (error.equals(key)) continue;
        assertTrue(key + " missing for " + error,
            trie.search(error, 1000).stream().anyMatch(m -> m.spelling.equals(key)));
      }
    }
  }

  @Test public void explicitBoundariesInitialsAndShortInputRemainLiteral() {
    SpellingTrie trie = new SpellingTrie();
    trie.add("nihao", 100);
    trie.add("xian", 100);
    trie.seal();
    for (String raw : Arrays.asList("nh", "ni", "xi'an", "zzzzzz", "a"))
      assertTrue(trie.search(raw, 16).isEmpty());
    assertTrue(trie.search("nihao", 16).stream().noneMatch(m -> m.spelling.equals("nihao")));
  }

  @Test public void weightingRanksTranspositionAheadOfUnrelatedSubstitution() {
    SpellingTrie trie = new SpellingTrie();
    trie.add("abcdefgh", 100);
    trie.add("abcdefgi", 100);
    trie.seal();
    SpellingTrie.Match transpose = trie.search("abcdfegh", 16).get(0);
    assertEquals("abcdefgh", transpose.spelling);
    assertEquals(.65, transpose.cost, .001);
    assertEquals(1, trie.search("abcdefgz", 16).get(0).cost, .001);
  }

  @Test public void cheapTranspositionKeepsAnAdmissibleSubtreeBound() {
    SpellingTrie trie = new SpellingTrie();
    trie.add("abcdefpxijk", 100);
    trie.add("abpqcdefxy", 100);
    trie.seal();
    // An adjacent-key substitution followed by a non-adjacent transposition costs 1.35.
    // The intermediate DP row costs 1.70; pruning on that row alone loses a valid match.
    assertEquals(1.35, trie.search("avcdefxpijk", 16).get(0).cost, .001);
    assertEquals(1.30, trie.search("abqpcdefyx", 16).get(0).cost, .001);
  }
}
