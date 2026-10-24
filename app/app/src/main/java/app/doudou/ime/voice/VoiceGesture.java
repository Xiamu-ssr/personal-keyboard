package app.doudou.ime.voice;

/** Targets belong to the visible dictation surface, independently of the space key's width. */
public final class VoiceGesture {
  private VoiceGesture() {}

  public enum Target { FINISH, CANCEL, NEWLINE }

  public static Target target(float x, float y, float width, float height) {
    if (width <= 0 || height <= 0 || y >= height * .68f) return Target.FINISH;
    return x <= width / 2 ? Target.CANCEL : Target.NEWLINE;
  }
}
