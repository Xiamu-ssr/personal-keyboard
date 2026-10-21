package app.doudou.ime.model;

import android.content.Context;
import android.os.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.cloud.CloudTask;
import app.doudou.ime.memory.MemoryStore;
import java.util.*;
import org.json.*;

/** Product model jobs; prompt assembly and supplier routing belong to Cloud. */
public final class TextModelClient {
  public interface Result {
    void done(List<String> replies, String error);
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

  private JSONObject memory() throws Exception {
    JSONObject input = new JSONObject();
    JSONArray words = new JSONArray(), styles = new JSONArray();
    for (MemoryStore.Item i : app.store.list("word", 0)) {
      if (words.length() == 50) break;
      words.put(i.title);
    }
    for (MemoryStore.Item i : app.store.list("style", 0)) {
      if (styles.length() == 12) break;
      styles.put(i.body.substring(0, Math.min(600, i.body.length())));
    }
    return input.put("words", words).put("styles", styles);
  }

  public void reply(
      String pkg,
      long person,
      String context,
      String intent,
      String refine,
      List<String> images,
      Result result) {
    final int token = ++epoch;
    CloudTask current = new CloudTask(app.cloud);
    task = current;
    app.network.execute(
        () -> {
          try {
            JSONObject input =
                memory().put("context", context).put("intent", intent).put("style", refine);
            MemoryStore.Item subject = app.store.get(person);
            input.put("subject", subject == null ? "" : subject.title);
            JSONArray memories = new JSONArray();
            for (MemoryStore.Item i : app.store.list("memory", person)) {
              if (memories.length() == 30) break;
              memories.put(i.body.substring(0, Math.min(600, i.body.length())));
            }
            input.put("contactMemories", memories);
            if (images != null && !images.isEmpty()) input.put("images", new JSONArray(images));
            JSONObject output = current.run("advisor", input, null);
            JSONArray replies = output.getJSONArray("replies");
            List<String> values = new ArrayList<>();
            for (int i = 0; i < replies.length(); i++)
              values.add(replies.getJSONObject(i).getString("text"));
            main.post(
                () -> {
                  if (token == epoch) result.done(values, null);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (token == epoch) result.done(null, message(e));
                });
          }
        });
  }

  public void polish(String text, String source, TextResult result) {
    final int token = ++epoch;
    CloudTask current = new CloudTask(app.cloud);
    task = current;
    app.network.execute(
        () -> {
          try {
            String value =
                current.run("rewrite", memory().put("transcript", text), source).getString("text");
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
