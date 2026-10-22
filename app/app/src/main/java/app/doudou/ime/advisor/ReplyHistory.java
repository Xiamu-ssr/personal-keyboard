package app.doudou.ime.advisor;

import java.util.*;

/** Editor-local reply pages. Returning to a previous page never spends another model call. */
public final class ReplyHistory {
  public static final class Page {
    public final List<String> replies;
    public final String style;
    public final String messages;

    Page(List<String> values, String name, String evidence) {
      replies = Collections.unmodifiableList(new ArrayList<>(values));
      style = name;
      messages = evidence;
    }
  }

  private Page current;
  private final Deque<Page> older = new ArrayDeque<>();

  public List<String> replies() {
    return current == null ? Collections.emptyList() : current.replies;
  }

  public String style() {
    return current == null ? "" : current.style;
  }

  public void accept(List<String> values, String style, boolean retainPrevious, String messages) {
    if (values.size() != 3) throw new IllegalArgumentException("Three replies required");
    if (retainPrevious && current != null) {
      older.push(current);
      while (older.size() > 8) older.removeLast();
    } else older.clear();
    current = new Page(values, style, messages);
  }

  public String messages() {
    return current == null ? "[]" : current.messages;
  }

  public boolean canBack() {
    return !older.isEmpty();
  }

  public void back() {
    if (canBack()) current = older.pop();
  }

  public void clear() {
    current = null;
    older.clear();
  }
}
