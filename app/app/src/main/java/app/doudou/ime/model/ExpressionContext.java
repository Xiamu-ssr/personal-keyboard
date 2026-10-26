package app.doudou.ime.model;

/** Bounded text already in this editor, captured before dictation changes the draft. */
public final class ExpressionContext {
  private ExpressionContext() {}

  public static String tail(String text, int maximum) {
    if (text == null || maximum <= 0) return "";
    if (text.length() <= maximum) return text;
    int start = text.length() - maximum;
    if (Character.isLowSurrogate(text.charAt(start))) start++;
    return text.substring(start);
  }

  public static String recognition(String recent, String editor) {
    String value = (recent.isEmpty() ? "" : "近期表达：" + recent + "\n")
        + (editor.isEmpty() ? "" : "输入框原有文字：" + editor);
    return tail(value, 400);
  }
}
