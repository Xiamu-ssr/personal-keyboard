package app.doudou.ime.capture;

/** A coarse duplicate guard, not OCR or a claim that all newly visible text was understood. */
public final class CaptureDifference {
  private CaptureDifference() {}
  public static boolean changed(int[] before,int[] after) {
    if(before==null || after==null || before.length!=after.length)return true;
    int changes=0;
    for(int i=0;i<before.length;i++) {
      int a=before[i],b=after[i];
      int delta=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));
      if(delta>48)changes++;
    }
    return changes>before.length/200;
  }
}
