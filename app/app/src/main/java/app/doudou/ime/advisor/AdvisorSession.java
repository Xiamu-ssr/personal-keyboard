package app.doudou.ime.advisor;

import java.util.*;

/** One capture and reply session, scoped to the current editor. */
public final class AdvisorSession {
  public enum Stage {
    IDLE,
    RESIZING,
    CAPTURING,
    READING,
    READY,
    GENERATING,
    RESULTS,
    ERROR,
    CANCELLED
  }

  public static final int MAX_PAGES = 3;
  public Stage stage = Stage.IDLE;
  public boolean retryCapture;
  public long generation;
  public String packageName = "", personId = "", personName = "", historyId = "", error = "";
  private volatile int revision;
  private final List<String> images = new ArrayList<>();

  public int begin(long gen, String pkg) {
    generation = gen;
    packageName = pkg;
    personId = "";
    personName = "";
    historyId = "";
    images.clear();
    error = "";
    retryCapture=false;
    stage = Stage.RESIZING;
    return ++revision;
  }

  public boolean valid(int token, long gen) {
    return token == revision
        && gen == generation
        && stage != Stage.IDLE
        && stage != Stage.CANCELLED;
  }

  public int token() {
    return revision;
  }

  public int pages() {
    return images.size();
  }

  public void image(String jpeg, boolean earlier) {
    if (images.size() >= MAX_PAGES) throw new IllegalArgumentException("最多补充三屏");
    images.add(earlier ? 0 : images.size(), jpeg);
    error = "";
    stage = Stage.READY;
  }

  public List<String> images() {
    return new ArrayList<>(images);
  }

  public void identify(String id, String name) {
    personId = id;
    personName = name;
    revision++;
    stage = Stage.READY;
  }

  public String conversationRef() {
    return personId.isEmpty() ? "" : packageName + ":" + personId;
  }

  public boolean canGenerate() {
    return !images.isEmpty()
        && (stage == Stage.READY || stage == Stage.RESULTS || stage == Stage.ERROR);
  }

  public void fail(String why) {
    retryCapture=stage==Stage.RESIZING || stage==Stage.CAPTURING || stage==Stage.READING;
    error = why;
    stage = Stage.ERROR;
  }

  public void cancel() {
    revision++;
    stage = Stage.CANCELLED;
    images.clear();
  }
}
