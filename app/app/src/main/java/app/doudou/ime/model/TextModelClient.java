package app.doudou.ime.model;

import android.content.Context;
import android.os.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.cloud.CloudTask;
import java.util.*;
import org.json.*;

/** Phone requests contain current evidence and references; Cloud assembles personal memory. */
public final class TextModelClient {
  public interface Result {
    void done(JSONObject result, String error);
  }

  public interface TextResult {
    void done(String text, String error);
  }

  private final KeyboardApp app = KeyboardApp.instance;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile CloudTask task;
  private int epoch;

  public TextModelClient(Context c) {}

  public boolean configured() {
    return app.cloud.loggedIn();
  }

  public void cancel() {
    epoch++;
    CloudTask old = task;
    if (old != null) old.cancel();
    task = null;
  }

  public void reply(
      String person,
      String conversation,
      String intent,
      String style,
      List<String> images,
      Result result) {
    final int token = ++epoch;
    CloudTask current = new CloudTask(app.cloud);
    task = current;
    app.network.execute(
        () -> {
          try {
            JSONObject input =
                new JSONObject()
                    .put("personId", person)
                    .put("conversationRef", conversation)
                    .put("recentContext", app.histories.recent(conversation, 12000))
                    .put("intent", intent)
                    .put("style", style)
                    .put("images", new JSONArray(images));
            JSONObject output = current.run("advisor", input, null);
            main.post(
                () -> {
                  if (token == epoch) result.done(output, null);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (token == epoch) result.done(null, message(e));
                });
          }
        });
  }

  public void polish(String text, String source, String conversation, TextResult result) {
    final int token = ++epoch;
    CloudTask current = new CloudTask(app.cloud);
    task = current;
    app.network.execute(
        () -> {
          try {
            JSONObject input =
                new JSONObject()
                    .put("transcript", text)
                    .put("conversationRef", conversation)
                    .put("recentContext", app.histories.recent(conversation, 12000));
            String value = current.run("rewrite", input, source).getString("text");
            main.post(
                () -> {
                  if (token == epoch) result.done(value, null);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (token == epoch) result.done(null, message(e));
                });
          }
        });
  }

  public static String message(Exception e) {
    return e.getMessage() == null ? "连接失败，请稍后重试" : e.getMessage();
  }
}
