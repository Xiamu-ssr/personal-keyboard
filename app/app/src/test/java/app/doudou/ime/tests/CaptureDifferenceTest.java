package app.doudou.ime.tests;

import static org.junit.Assert.*;

import app.doudou.ime.advisor.AdvisorSession;
import app.doudou.ime.capture.CaptureDifference;
import org.junit.Test;

public class CaptureDifferenceTest {
  @Test
  public void cursorBlinkIsNotAnExtraPageButNewContentIs() {
    int[] first = new int[96 * 128], second = new int[96 * 128];
    for (int i = 0; i < 8; i++) second[i] = 0xffffff;
    assertFalse(CaptureDifference.changed(first, second));
    for (int i = 0; i < 100; i++) second[i] = 0x999999;
    assertTrue(CaptureDifference.changed(first, second));
  }

  @Test
  public void earlierCapturesPrecedeLatestAndRemainBounded() {
    AdvisorSession session = new AdvisorSession();
    session.begin(1, "sample");
    session.image("current", false);
    session.image("earlier", true);
    assertArrayEquals(new String[] {"earlier", "current"}, session.images().toArray(new String[0]));
    try {
      session.image("fourth", true);
      fail("Unbounded screenshot input");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void retryKeepsTheFailedOperationInsteadOfSilentlyReusingAnOldPage() {
    AdvisorSession session = new AdvisorSession();
    session.begin(1, "sample");
    session.image("current", false);
    session.stage = AdvisorSession.Stage.CAPTURING;
    session.fail("capture denied");
    assertTrue(session.retryCapture);
    session.stage = AdvisorSession.Stage.GENERATING;
    session.fail("provider unavailable");
    assertFalse(session.retryCapture);
    assertEquals("current", session.images().get(0));
  }

  @Test
  public void allGeneratedPagesShareAlreadyFinalizedChronologicalImageReferences() {
    app.doudou.ime.advisor.ReplyHistory replies = new app.doudou.ime.advisor.ReplyHistory();
    java.util.List<app.doudou.ime.advisor.ReplyHistory.Choice> values =
        java.util.Arrays.asList(
            new app.doudou.ime.advisor.ReplyHistory.Choice("slot1", "自然", "a"),
            new app.doudou.ime.advisor.ReplyHistory.Choice("slot1", "自然", "b"),
            new app.doudou.ime.advisor.ReplyHistory.Choice("slot1", "自然", "c"));
    replies.accept(values, "[{\"image\":2}]");
    replies.accept(values, "[{\"image\":1},{\"image\":2}]");
    replies.back();
    assertEquals("[{\"image\":2}]", replies.messages());
    replies.forward();
    assertEquals("[{\"image\":1},{\"image\":2}]", replies.messages());
  }
}
