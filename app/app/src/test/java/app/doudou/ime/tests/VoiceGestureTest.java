package app.doudou.ime.tests;

import static org.junit.Assert.*;
import app.doudou.ime.voice.VoiceGesture;
import org.junit.Test;

public class VoiceGestureTest {
  @Test public void bottomAreaFinishesAndCanBeReenteredAfterChoosingAnAction() {
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(180, 260, 360, 284));
    assertEquals(VoiceGesture.Target.CANCEL, VoiceGesture.target(70, 140, 360, 284));
    assertEquals(VoiceGesture.Target.NEWLINE, VoiceGesture.target(290, 140, 360, 284));
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(70, 260, 360, 284));
  }
  @Test public void targetsScaleWithTheSurfaceRatherThanTheSpaceKey() {
    assertEquals(VoiceGesture.Target.CANCEL, VoiceGesture.target(140, 280, 720, 568));
    assertEquals(VoiceGesture.Target.NEWLINE, VoiceGesture.target(580, 280, 720, 568));
    assertEquals(VoiceGesture.Target.FINISH, VoiceGesture.target(290, 140, 0, 0));
  }
}
