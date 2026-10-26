package app.doudou.ime.voice;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.input.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;

/** Provider auto-final and microphone timeout must hand the real composing span to typing. */
public class VoiceOwnershipTest {
  private EditText find(View v) {
    if (v instanceof EditText) return (EditText)v;
    if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
      EditText e=find(((ViewGroup)v).getChildAt(i)); if(e!=null)return e;
    }
    return null;
  }
  private void state(VoiceController voice,String name,Object value) throws Exception {
    Field f=VoiceController.class.getDeclaredField(name);f.setAccessible(true);f.set(voice,value);
  }
  @Test public void automaticFinalAllowsFirstPinyinEditDuringCorrection() throws Exception { check(VoiceState.REWRITING); }
  @Test public void delayedMicrophoneProcessingAlsoAllowsFirstEdit() throws Exception { check(VoiceState.FINISHING); }
  private void check(VoiceState phase) throws Exception {
    Assume.assumeTrue("Own emulator only", "ranchu".equals(Build.HARDWARE));
    Instrumentation ins=InstrumentationRegistry.getInstrumentation();
    KeyboardApp app=(KeyboardApp)ins.getTargetContext().getApplicationContext();
    Activity activity=ins.startActivitySync(new Intent(app,PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    List<String> histories=new ArrayList<>();
    try {
      ins.runOnMainSync(()->{
        EditText edit=find(activity.getWindow().getDecorView());edit.setText("前文：");edit.requestFocus();edit.setSelection(3);
        InputConnection ic=edit.onCreateInputConnection(new EditorInfo());DictationEditor draft=new DictationEditor();
        assertTrue(draft.begin(ic,"前文：",3,3));assertTrue(draft.update("已识别的语音"));
        EditorHost host=(EditorHost)Proxy.newProxyInstance(EditorHost.class.getClassLoader(),new Class<?>[]{EditorHost.class},(p,m,a)->{
          switch(m.getName()) {
            case "context": return activity;
            case "services": return app;
            case "retainVoice": return draft.release();
            case "trackHistory": histories.add((String)a[0]); return null;
            case "practiceMode": return true;
            case "mode": return "typing";
            case "conversationRef": return "synthetic-v094-ownership";
          }
          if(m.getReturnType()==boolean.class)return false;
          if(m.getReturnType()==long.class)return 1L;
          if(m.getReturnType()==int.class)return 0;
          return null;
        });
        VoiceController voice=new VoiceController(host);
        try {
          state(voice,"sessionActive",true);state(voice,"holding",true);state(voice,"stopping",false);
          state(voice,"working",true);state(voice,"state",phase);
          state(voice,"transcript","已识别的语音");state(voice,"rawTranscript","已识别的语音");
        } catch(Exception e){throw new AssertionError(e);}
        voice.beginFinalization();voice.beforeEdit();
        assertFalse("A first edit never waits for a late model callback",voice.active());
        ic.setComposingText("n",1);
        assertEquals("前文：已识别的语音n",edit.getText().toString());
        assertFalse(draft.update("迟到的校对"));
        assertEquals("前文：已识别的语音n",edit.getText().toString());
      });
    } finally { for(String id:histories)app.histories.remove(id);ins.runOnMainSync(activity::finish); }
  }
}
