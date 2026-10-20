package app.doudou.ime.core;

import java.util.*;

/** One editor-scoped job. Identity is explicit; OCR never claims who said a line. */
public final class AdvisorSession {
  public enum Stage {
    IDLE,
    RESIZING,
    CAPTURING,
    READING,
    IDENTITY,
    READY,
    GENERATING,
    RESULTS,
    ERROR,
    CANCELLED
  }

  public Stage stage = Stage.IDLE;
  public long generation, person;
  public String packageName = "", error = "";
  public static final int MAX_PAGES = 6, MAX_CHARS = 16000;
  private int revision, pages;
  private final LinkedHashSet<String> lines = new LinkedHashSet<>();

  public int begin(long gen, String pkg) {
    generation = gen;
    packageName = pkg;
    person = 0;
    lines.clear();
    pages = 0;
    error = "";
    stage = Stage.RESIZING;
    return ++revision;
  }

  public boolean valid(int id, long gen) {
    return id == revision && gen == generation && stage != Stage.CANCELLED && stage != Stage.IDLE;
  }

  public int token() {
    return revision;
  }

  public void cancel() {
    revision++;
    stage = Stage.CANCELLED;
    lines.clear();
    person = 0;
  }

  public void append(String text) {
    if (pages >= MAX_PAGES) throw new IllegalArgumentException("已达到六屏上限");
    LinkedHashSet<String> next = new LinkedHashSet<>(lines);
    for (String l : text.split("\n")) {
      l = l.trim();
      if (!l.isEmpty()) next.add(l);
    }
    if (String.join("\n", next).length() > MAX_CHARS)
      throw new IllegalArgumentException("识字内容过长，请精简后再生成");
    lines.clear();
    lines.addAll(next);
    pages++;
    stage = person > 0 ? Stage.READY : Stage.IDENTITY;
  }

  public void identify(long id) {
    if (id <= 0) throw new IllegalArgumentException("Identity required");
    person = id;
    stage = Stage.READY;
  }

  public int pages() {
    return pages;
  }

  public String context() {
    return String.join("\n", lines);
  }

  public void fail(String why) {
    error = why;
    stage = Stage.ERROR;
  }

  public boolean canGenerate() {
    return person > 0
        && !lines.isEmpty()
        && (stage == Stage.READY || stage == Stage.RESULTS || stage == Stage.ERROR);
  }
}
