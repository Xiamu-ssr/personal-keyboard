package app.doudou.ime.advisor;

import java.util.*;

/** Every generated page remains navigable in both directions during this editor session. */
public final class ReplyHistory {
  public static final class Choice {
    public final String slotId, name, text;

    public Choice(String slotId, String name, String text) {
      this.slotId = slotId;
      this.name = name;
      this.text = text;
    }
  }

  public static final class Page {
    public final List<Choice> choices;
    public final String messages;

    Page(List<Choice> choices, String messages) {
      this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
      this.messages = messages;
    }
  }

  private final List<Page> pages = new ArrayList<>();
  private int cursor = -1;

  public List<Choice> choices() {
    return cursor < 0 ? Collections.emptyList() : pages.get(cursor).choices;
  }

  public void accept(List<Choice> values, String messages) {
    if (values.size() != 3 && values.size() != 6)
      throw new IllegalArgumentException("Three variants or six expressions required");
    pages.add(new Page(values, messages));
    cursor = pages.size() - 1;
  }

  public String messages() {
    return cursor < 0 ? "[]" : pages.get(cursor).messages;
  }

  public boolean canBack() {
    return cursor > 0;
  }

  public boolean canForward() {
    return cursor >= 0 && cursor < pages.size() - 1;
  }

  public void back() {
    if (canBack()) cursor--;
  }

  public void forward() {
    if (canForward()) cursor++;
  }

  public String position() {
    return (cursor + 1) + " / " + pages.size();
  }

  public List<String> recentTexts() {
    List<String> out = new ArrayList<>();
    for (int i = pages.size() - 1; i >= 0 && out.size() < 24; i--)
      for (Choice c : pages.get(i).choices) if (out.size() < 24) out.add(c.text);
    return out;
  }

  public void clear() {
    pages.clear();
    cursor = -1;
  }
}
