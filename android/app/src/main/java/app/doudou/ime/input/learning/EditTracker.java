package app.doudou.ime.input.learning;

/**
 * Tracks ONLY text inserted by this IME. Unknown/truncated/cleared editors never teach deletion.
 */
public final class EditTracker {
  public static final class Change {
    public final String before, after;
    public final int start;

    Change(String b, String a, int s) {
      before = b;
      after = a;
      start = s;
    }
  }

  private String original, expected, left, right;
  private long generation;
  private int start;

  public void begin(String full, int selectionStart, int selectionEnd, String inserted, long gen) {
    clear();
    if (full == null
        || selectionStart < 0
        || selectionEnd < selectionStart
        || selectionEnd > full.length()
        || full.length() > 12000) return;
    original = full.substring(selectionStart, selectionEnd);
    start = selectionStart;
    generation = gen;
    left = full.substring(0, start);
    right = full.substring(selectionEnd);
    expected = inserted;
  }

  public String undo(String full, long gen) {
    if (expected == null
        || generation != gen
        || full == null
        || !full.equals(left + expected + right)) return null;
    String out = left + original + right;
    clear();
    return out;
  }

  public Change observe(String full, long gen) {
    if (expected == null
        || generation != gen
        || full == null
        || full.isEmpty()
        || full.length() > 12000) return null;
    if (!full.startsWith(left)
        || !full.endsWith(right)
        || full.length() < left.length() + right.length()) return null;
    String actual = full.substring(left.length(), full.length() - right.length());
    if (actual.isEmpty()
        || actual.equals(expected)
        || actual.length() > Math.max(256, expected.length() * 3)) return null;
    Change delta = diff(expected, actual);
    if (delta.before.isEmpty() || delta.after.isEmpty()) return null;
    return new Change(expected, actual, delta.start);
  }

  public void clear() {
    original = null;
    expected = null;
    left = null;
    right = null;
  }

  public boolean active() {
    return expected != null;
  }

  public int start() {
    return start;
  }

  public int originalEnd() {
    return start + (original == null ? 0 : original.length());
  }

  public int insertedLength() {
    return expected == null ? 0 : expected.length();
  }

  public static Change diff(String before, String after) {
    int[] a = before.codePoints().toArray(), b = after.codePoints().toArray();
    int p = 0;
    while (p < a.length && p < b.length && a[p] == b[p]) p++;
    int ai = a.length, bi = b.length;
    while (ai > p && bi > p && a[ai - 1] == b[bi - 1]) {
      ai--;
      bi--;
    }
    return new Change(new String(a, p, ai - p), new String(b, p, bi - p), p);
  }
}
