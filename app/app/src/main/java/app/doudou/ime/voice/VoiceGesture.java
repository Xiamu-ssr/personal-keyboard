package app.doudou.ime.voice;

/** Pure gesture decision; crossing a target previews it, release is the commitment. */
public final class VoiceGesture {
  public enum Target { FINISH, CANCEL, LOCK }
  public static Target target(float dx, float dy, float distance) {
    if (dx < -distance && -dx > -dy) return Target.CANCEL;
    if (dy < -distance) return Target.LOCK;
    return Target.FINISH;
  }
}
