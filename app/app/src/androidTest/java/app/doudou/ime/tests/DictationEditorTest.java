package app.doudou.ime.tests;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.PracticeActivity;
import app.doudou.ime.input.DictationEditor;
import org.junit.Test;

/** A real Android editor: no model fixture, account, history, or settings mutations. */
public final class DictationEditorTest {
  private EditText field(View view) {
    if (view instanceof EditText) return (EditText) view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
      EditText found = field(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
    }
    return null;
  }

  @Test public void previewsReplaceOnlyOwnSpanAndCancelRestoresSelection() {
    Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    Activity activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      ins.runOnMainSync(() -> {
        EditText edit = field(activity.getWindow().getDecorView());
        edit.setText("前面旧字后面"); edit.requestFocus(); edit.setSelection(2, 4);
        InputConnection ic = edit.onCreateInputConnection(new EditorInfo());
        DictationEditor span = new DictationEditor();
        assertTrue(span.begin(ic, edit.getText().toString(), 2, 4));
        assertTrue(span.update("说")); assertTrue(span.update("说出来的话"));
        assertEquals("前面说出来的话后面", edit.getText().toString());
        assertTrue(span.restore());
        assertEquals("前面旧字后面", edit.getText().toString());
        assertEquals(2, edit.getSelectionStart()); assertEquals(4, edit.getSelectionEnd());
        assertTrue(span.begin(ic, edit.getText().toString(), 2, 4));
        assertTrue(span.update(""));
        assertTrue(span.restore());
        assertEquals("前面旧字后面", edit.getText().toString());
      });
    } finally { ins.runOnMainSync(activity::finish); }
  }

  @Test public void cancellingNeverOverwritesAnExternalEdit() {
    Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    Activity activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      ins.runOnMainSync(() -> {
        EditText edit = field(activity.getWindow().getDecorView());
        edit.setText("原草稿"); edit.requestFocus(); edit.setSelection(3);
        DictationEditor span = new DictationEditor();
        assertTrue(span.begin(edit.onCreateInputConnection(new EditorInfo()), "原草稿", 3, 3));
        assertTrue(span.update("临时转写"));
        edit.setText("用户另外写的新草稿"); edit.setSelection(edit.length());
        assertFalse(span.update("迟到的识别"));
        assertFalse(span.restore());
        assertEquals("用户另外写的新草稿", edit.getText().toString());
      });
    } finally { ins.runOnMainSync(activity::finish); }
  }

  @Test public void releasingPreviewLetsTypingOwnTheDraftAndRejectsLateReplacement() {
    Instrumentation ins = InstrumentationRegistry.getInstrumentation();
    Activity activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      ins.runOnMainSync(() -> {
        EditText edit = field(activity.getWindow().getDecorView());
        edit.setText("前文："); edit.requestFocus(); edit.setSelection(edit.length());
        InputConnection ic = edit.onCreateInputConnection(new EditorInfo());
        DictationEditor span = new DictationEditor();
        assertTrue(span.begin(ic, "前文：", 3, 3));
        assertTrue(span.update("今晚见。"));
        assertEquals("今晚见。", span.release());
        ic.deleteSurroundingText(1, 0); ic.commitText("，七点。", 1);
        assertFalse(span.update("晚到的整理结果"));
        assertTrue(span.restore());
        assertEquals("前文：今晚见，七点。", edit.getText().toString());
      });
    } finally { ins.runOnMainSync(activity::finish); }
  }
}
