package app.doudou.ime.input.engine;

import java.util.*;

/**
 * Weighted restricted Damerau-Levenshtein (optimal string alignment) over dictionary spellings.
 * Each trie edge advances one DP row; an admissible bound prunes the entire subtree. No generated misspelling table or
 * alphabet-wide edit enumeration is used. Weights are hundredths of an edit: nearby QWERTY keys
 * 70, transposition 65, insertion/deletion/other substitution 100.
 */
public final class SpellingTrie {
  private static final int[][] SUBSTITUTIONS = substitutions();
  private int[] child = new int[16384], sibling = new int[16384], frequency = new int[16384];
  private char[] edge = new char[16384];
  private int size = 1;
  private boolean sealed;

  public static final class Match {
    public final String spelling;
    public final int frequency;
    public final double cost;

    Match(String spelling, int frequency, int cost) {
      this.spelling = spelling;
      this.frequency = frequency;
      this.cost = cost / 100.;
    }

    double rank() {
      return Math.min(25, 2 * Math.log1p(frequency)) - 45 * cost;
    }
  }

  /** Called once while loading the indexed resource on the dictionary worker. */
  public void add(String spelling, int weight) {
    if (sealed) throw new IllegalStateException("Spelling trie is sealed");
    if (!letters(spelling) || spelling.length() > 39) return;
    int node = 0;
    for (int i = 0; i < spelling.length(); i++) {
      char letter = spelling.charAt(i);
      int next = child[node];
      while (next != 0 && edge[next] != letter) next = sibling[next];
      if (next == 0) {
        grow();
        next = size++;
        edge[next] = letter;
        sibling[next] = child[node];
        child[node] = next;
      }
      node = next;
    }
    frequency[node] = Math.max(frequency[node], Math.max(0, weight) + 1);
  }

  public void seal() {
    if (sealed) return;
    child = Arrays.copyOf(child, size);
    sibling = Arrays.copyOf(sibling, size);
    frequency = Arrays.copyOf(frequency, size);
    edge = Arrays.copyOf(edge, size);
    sealed = true;
  }

  public int nodeCount() { return size; }

  private void grow() {
    if (size < child.length) return;
    int capacity = child.length * 3 / 2;
    child = Arrays.copyOf(child, capacity);
    sibling = Arrays.copyOf(sibling, capacity);
    frequency = Arrays.copyOf(frequency, capacity);
    edge = Arrays.copyOf(edge, capacity);
  }

  public List<Match> search(String raw, int limit) {
    // Short input and consonant initials are deliberately literal. Explicit apostrophes fix
    // syllable boundaries, so correction must not silently reinterpret them.
    if (limit <= 0 || raw.length() < 4 || raw.length() > 39
        || !letters(raw) || !raw.matches(".*[aeiouv].*"))
      return Collections.emptyList();
    int budget = raw.length() < 8 ? 105 : 145;
    int[] row = new int[raw.length() + 1];
    for (int i = 0; i < row.length; i++) row[i] = i * 100;
    PriorityQueue<Match> best = new PriorityQueue<>(Comparator.comparingDouble(Match::rank));
    walk(0, '\0', raw, row, null, new StringBuilder(), budget, limit, best);
    List<Match> out = new ArrayList<>(best);
    out.sort(Comparator.comparingDouble(Match::rank).reversed()
        .thenComparing(m -> m.spelling));
    return out;
  }

  private void walk(int parent, char previousLetter, String raw, int[] previous, int[] older,
      StringBuilder path, int budget, int limit, PriorityQueue<Match> best) {
    int parentBound = Integer.MAX_VALUE;
    for (int value : previous) parentBound = Math.min(parentBound, value);
    for (int node = child[parent]; node != 0; node = sibling[node]) {
      char letter = edge[node];
      int[] current = new int[previous.length];
      current[0] = previous[0] + 100;
      int lowerBound = current[0];
      for (int i = 1; i < current.length; i++) {
        current[i] = Math.min(previous[i] + 100,
            Math.min(current[i - 1] + 100,
                previous[i - 1] + SUBSTITUTIONS[letter - 'a'][raw.charAt(i - 1) - 'a']));
        if (older != null && i > 1 && letter == raw.charAt(i - 2)
            && previousLetter == raw.charAt(i - 1))
          current[i] = Math.min(current[i], older[i - 2] + 65);
        lowerBound = Math.min(lowerBound, current[i]);
      }
      // A transposition crossing the next edge can be cheaper than this intermediate row.
      // Include that optimistic continuation before pruning (especially with a .65 weight).
      if (Math.min(lowerBound, parentBound + 65) > budget) continue;
      path.append(letter);
      int cost = current[raw.length()];
      if (frequency[node] > 0 && cost > 0 && cost <= budget) {
        Match match = new Match(path.toString(), frequency[node] - 1, cost);
        best.add(match);
        if (best.size() > limit) best.remove();
      }
      if (path.length() < raw.length() + 1)
        walk(node, letter, raw, current, previous, path, budget, limit, best);
      path.setLength(path.length() - 1);
    }
  }

  private static boolean letters(String input) {
    if (input.isEmpty()) return false;
    for (int i = 0; i < input.length(); i++)
      if (input.charAt(i) < 'a' || input.charAt(i) > 'z') return false;
    return true;
  }

  private static int[][] substitutions() {
    String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    double[] x = new double[26], y = new double[26];
    for (int r = 0; r < rows.length; r++)
      for (int i = 0; i < rows[r].length(); i++) {
        int letter = rows[r].charAt(i) - 'a';
        x[letter] = i + (r == 0 ? 0 : r == 1 ? .25 : .75);
        y[letter] = r;
      }
    int[][] cost = new int[26][26];
    for (int a = 0; a < 26; a++)
      for (int b = 0; b < 26; b++)
        cost[a][b] = a == b ? 0 : Math.abs(y[a] - y[b]) <= 1
            && Math.abs(x[a] - x[b]) <= 1.05 ? 70 : 100;
    return cost;
  }
}
