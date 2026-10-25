package app.doudou.ime.input;

/** InputConnection deletion counts UTF-16 units; a visible emoji can contain many code points. */
public final class Graphemes {
  private Graphemes() {}
  public static int lastLength(CharSequence text) {
    if (text.length() == 0) return 0;
    android.icu.text.BreakIterator boundaries = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
    boundaries.setText(text.toString());
    return text.length() - boundaries.preceding(text.length());
  }
}
