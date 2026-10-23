package app.doudou.ime.tests;

import static org.junit.Assert.*;
import app.doudou.ime.voice.VoiceGesture;
import org.junit.Test;

public class VoiceGestureTest {
  @Test public void releaseCancelsOnlyAfterEnteringCancelTarget() {
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(-18, -12, 64));
    assertEquals(VoiceGesture.Target.CANCEL, VoiceGesture.target(-90, -15, 64));
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(0, 0, 64));
  }
  @Test public void lockTargetWinsWhenMovingMostlyUp() {
    assertEquals(VoiceGesture.Target.LOCK, VoiceGesture.target(-70, -110, 64));
    assertEquals(VoiceGesture.Target.CANCEL, VoiceGesture.target(-110, -70, 64));
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(80, 4, 64));
  }
}
