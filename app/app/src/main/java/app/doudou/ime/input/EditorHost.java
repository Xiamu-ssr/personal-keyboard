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

  boolean privateMode();

  LinearLayout openPanel(String mode, int heightDp);

  void typing();

  void commitComposing();

  void snapshot();

  boolean insert(String text);

  boolean protectedInsert(String text);

  boolean previewVoice(String text);

  boolean finishVoice(String text);

  void restoreVoice();

  void editLocal(String title, String value, Consumer<String> done);

  void settings(String page);

  void trackHistory(String id);

  String conversationRef();

  void message(String title, String detail, String actionLabel, Runnable action);

  void openVoice();

  void openAdvisor();

  void cancelVoice();

  void cancelAdvisor();

  void togglePrivacy();

  void moveCursor(int delta);

  void delete();

  void undoInsertion();

  String full();

  void selectAll();
}
