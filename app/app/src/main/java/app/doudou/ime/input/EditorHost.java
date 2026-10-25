package app.doudou.ime.input;

import android.content.Context;
import android.widget.LinearLayout;
import app.doudou.ime.app.KeyboardApp;
import java.util.function.Consumer;

/** Live-editor boundary; panels do not hold InputConnections or send messages. */
public interface EditorHost {
  Context context();

  KeyboardApp services();

  long generation();

  String editorPackage();

  String mode();

  int bottomInset();

  boolean inputViewVisible();

  void showInputView();

  boolean privateMode();

  LinearLayout openPanel(String mode, int heightDp);

  void typing();

  android.view.View workspaceBar(WorkspaceBar.Mode mode);

  void letters();

  void commitComposing();

  void snapshot();

  boolean insert(String text);

  boolean protectedInsert(String text);
  boolean protectedReplace(String original, String text);

  boolean previewVoice(String text);

  boolean finishVoice(String text);

  String retainVoice();

  void showVoice();

  void restoreVoice();

  android.widget.EditText editLocal(String title, String value, Consumer<String> done);

  void settings(String page);

  void trackHistory(String id);

  String conversationRef();

  void message(String title, String detail, String actionLabel, Runnable action);

  void voiceFeedback(
      app.doudou.ime.voice.VoiceState state,
      String hint,
      boolean held,
      app.doudou.ime.voice.VoiceGesture.Target target);

  void voiceLevel(float rmsDb);

  app.doudou.ime.voice.VoiceGesture.Target voiceTarget(float rawX, float rawY);

  void voiceRecovery(String hint, boolean hasText);

  void vibrate();

  void cancelVoice();

  void cancelAdvisor();

  void togglePrivacy();

  void moveCursor(int delta);

  void delete();

  void undoInsertion();

  boolean canUndoInsertion();

  String full();

  void selectAll();

  void editText();
}
