package app.doudou.ime.tests;

import app.doudou.ime.capture.CaptureDifference;
import app.doudou.ime.advisor.AdvisorSession;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureDifferenceTest {
  @Test public void cursorBlinkIsNotAnExtraPageButNewContentIs(){
    int[] first=new int[96*128],second=new int[96*128];
    for(int i=0;i<8;i++)second[i]=0xffffff;
    assertFalse(CaptureDifference.changed(first,second));
    for(int i=0;i<100;i++)second[i]=0x999999;
    assertTrue(CaptureDifference.changed(first,second));
  }
  @Test public void earlierCapturesPrecedeLatestAndRemainBounded(){
    AdvisorSession session=new AdvisorSession();session.begin(1,"sample");
    session.image("current",false);session.image("earlier",true);session.image("oldest",true);
    assertArrayEquals(new String[]{"oldest","earlier","current"},session.images().toArray(new String[0]));
    try{session.image("fourth",true);fail("Unbounded screenshot input");}catch(IllegalArgumentException expected){}
  }
  @Test public void retryKeepsTheFailedOperationInsteadOfSilentlyReusingAnOldPage(){
    AdvisorSession session=new AdvisorSession();session.begin(1,"sample");session.image("current",false);
    session.stage=AdvisorSession.Stage.CAPTURING;session.fail("capture denied");assertTrue(session.retryCapture);
    session.stage=AdvisorSession.Stage.GENERATING;session.fail("provider unavailable");assertFalse(session.retryCapture);
    assertEquals("current",session.images().get(0));
  }
  @Test public void oldReplyKeepsItsImageReferenceAfterEarlierPagesAreAdded(){
    app.doudou.ime.advisor.ReplyHistory replies=new app.doudou.ime.advisor.ReplyHistory();
    java.util.List<String> values=java.util.Arrays.asList("a","b","c");
    replies.accept(values,"",false,"[{\"image\":1}]");replies.prependImage();
    replies.accept(values,"",true,"[{\"image\":1},{\"image\":2}]");assertEquals(0,replies.imageOffset());
    replies.back();assertEquals(1,replies.imageOffset());assertEquals("[{\"image\":1}]",replies.messages());
    replies.prependImage();assertEquals(2,replies.imageOffset());
  }
}
