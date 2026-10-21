package app.doudou.ime.tests;

import static org.junit.Assert.*;

import app.doudou.ime.sync.RevisionClock;
import java.util.*;
import org.junit.Test;

public final class SyncClockTest {
  @Test
  public void distinguishesConcurrentChangesFromLaterRestore() {
    Map<String, Long> a = RevisionClock.advance(Collections.emptyMap(), "a"),
        b = RevisionClock.advance(a, "b"),
        concurrent = RevisionClock.advance(a, "a");
    assertEquals(RevisionClock.Order.CONCURRENT, RevisionClock.compare(b, concurrent));
    Map<String, Long> observed = RevisionClock.merge(b, concurrent);
    assertEquals(RevisionClock.Order.AFTER, RevisionClock.compare(observed, b));
    Map<String, Long> restore = RevisionClock.advance(observed, "b");
    assertEquals(RevisionClock.Order.AFTER, RevisionClock.compare(restore, observed));
    assertEquals(RevisionClock.Order.EQUAL, RevisionClock.compare(restore, restore));
  }
}
