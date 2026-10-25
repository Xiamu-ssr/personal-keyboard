package app.doudou.ime.tests;

import static org.junit.Assert.*;
import app.doudou.ime.translation.TranslationDraft;
import org.junit.Test;

public final class TranslationOriginalTest {
  @Test public void repeatedLanguagesAlwaysUseTheCapturedOriginalAndCanReturnWithoutAnotherCall() {
    TranslationDraft draft = new TranslationDraft();
    draft.bind("user", 3, false, "明天见");
    assertTrue(draft.remember(draft.revision(), "en", "See you tomorrow"));
    draft.display("en");
    draft.bind("user", 3, false, "See you tomorrow");
    assertEquals("明天见", draft.original());
    assertTrue(draft.remember(draft.revision(), "ja", "また明日"));
    draft.display("ja");
    assertEquals("明天见", draft.original());
    assertEquals("See you tomorrow", draft.cached("en"));
    draft.restore();
    assertEquals("明天见", draft.displayed());
    assertEquals("", draft.language());
    draft.display("en");
    assertEquals("See you tomorrow", draft.displayed());
  }

  @Test public void restoringDuringAnInFlightRequestInvalidatesItsLateAnswer() {
    TranslationDraft draft = new TranslationDraft(); draft.bind("user", 3, false, "原文");
    long pending = draft.revision();
    draft.restore();
    assertFalse(draft.remember(pending, "en", "late"));
    assertEquals("原文", draft.displayed());
    assertNull(draft.cached("en"));
  }

  @Test public void manualEditRebasesTheOriginalAndDropsOldTranslations() {
    TranslationDraft draft = new TranslationDraft(); draft.bind("user", 3, false, "原文");
    draft.remember(draft.revision(), "en", "translated"); draft.display("en");
    assertFalse(draft.matches("user", 3, "新意思"));
    long pending = draft.revision();
    draft.bind("user", 3, false, "新意思");
    assertEquals("新意思", draft.original());
    assertFalse(draft.remember(pending, "ja", "late"));
    assertNull(draft.cached("en"));
  }

  @Test public void identicalTextInAnotherEditorOrAccountCannotReuseTheOriginal() {
    TranslationDraft draft = new TranslationDraft(); draft.bind("user", 3, false, "原文");
    draft.remember(draft.revision(), "en", "translation"); draft.display("en");
    assertFalse(draft.matches("user", 4, "translation"));
    assertFalse(draft.matches("other", 3, "translation"));
    draft.bind("user", 4, false, "translation");
    assertEquals("translation", draft.original()); assertNull(draft.cached("en"));
  }

  @Test public void recorderTranslationHasNoChatTargetAndNoPersistence() {
    TranslationDraft draft = new TranslationDraft(); draft.bind("user", 3, true, "录音原文");
    assertTrue(draft.local()); assertTrue(draft.matches("user", 3, "不相关的聊天"));
    draft.remember(draft.revision(), "en", "recording"); draft.display("en");
    assertEquals("录音原文", draft.original());
    draft.restore(); assertEquals("录音原文", draft.displayed());
    assertEquals("", new TranslationDraft().original());
  }

  @Test public void unreadableEditorKeepsOriginalButNeverAcrossAccounts() {
    TranslationDraft draft = new TranslationDraft(); draft.bind("user", 3, false, "原文");
    draft.remember(draft.revision(), "en", "translation"); draft.display("en");
    draft.bind("user", 3, false, null);
    assertEquals("原文", draft.original()); assertEquals("translation", draft.cached("en"));
    assertFalse(draft.matches("user", 3, null));
    draft.bind("other", 3, false, null);
    assertEquals("", draft.original()); assertNull(draft.cached("en"));
  }
}
