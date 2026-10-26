package app.doudou.ime.tests;

import static org.junit.Assert.*;
import app.doudou.ime.model.ExpressionContext;
import org.junit.Test;

public class ExpressionContextTest {
  @Test public void editorBeforeVoiceIsIncludedWithoutSplittingEmoji() {
    assertEquals("", ExpressionContext.tail(null, 10));
    assertEquals("尾字", ExpressionContext.tail("开头😀尾字", 3));
    String context = ExpressionContext.recognition("关于莫奈的画", "明天去美术馆");
    assertTrue(context.contains("莫奈"));
    assertTrue(context.contains("明天去美术馆"));
    assertTrue(ExpressionContext.recognition("长".repeat(1000), "尾部").length() <= 400);
  }
}
