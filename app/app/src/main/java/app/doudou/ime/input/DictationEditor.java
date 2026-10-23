package app.doudou.ime.input;

import android.view.inputmethod.*;

/** Owns only this dictation's composing span. Never replaces a changed or different editor. */
public final class DictationEditor {
  private InputConnection connection;
  private String before = "", preview = "";
  private int start, end;
  private boolean active, changed;

  public boolean active() { return active; }

  public boolean begin(InputConnection ic, String text, int a, int b) {
    if (ic == null || text == null || a < 0 || b < 0 || Math.max(a, b) > text.length()) return false;
    connection = ic; before = text; start = Math.min(a, b); end = Math.max(a, b);
    preview = ""; active = true; changed = false;
    return true;
  }

  private boolean unchanged() {
    ExtractedText e = connection.getExtractedText(new ExtractedTextRequest(), 0);
    String expected = before.substring(0, start) + preview + before.substring(end);
    return e != null && e.startOffset == 0 && e.text != null && expected.contentEquals(e.text)
        && e.selectionStart == start + preview.length() && e.selectionEnd == e.selectionStart;
  }

  public boolean update(String value) {
    if (!active) return false;
    // First replacement can include an existing selection; later ones must still be ours.
    ExtractedText current = connection.getExtractedText(new ExtractedTextRequest(), 0);
    boolean first = !changed && current != null && before.contentEquals(current.text == null ? "" : current.text)
        && Math.min(current.selectionStart, current.selectionEnd) == start
        && Math.max(current.selectionStart, current.selectionEnd) == end;
    if (!first && !unchanged()) return false;
    connection.beginBatchEdit();
    try {
      if (first) connection.setSelection(start, end);
      else connection.setComposingRegion(start, start + preview.length());
      if (!connection.setComposingText(value, 1)) return false;
      preview = value; changed = true;
      return true;
    } finally { connection.endBatchEdit(); }
  }

  public boolean restore() {
    if (!active) return true;
    active = false;
    if (!changed) { connection.finishComposingText(); return true; }
    if (!unchanged()) { connection.finishComposingText(); return false; }
    connection.beginBatchEdit();
    try {
      connection.setComposingRegion(start, start + preview.length());
      connection.commitText(before.substring(start, end), 1);
      connection.finishComposingText();
      connection.setSelection(start, end);
      return true;
    } finally { connection.endBatchEdit(); connection = null; }
  }
}
