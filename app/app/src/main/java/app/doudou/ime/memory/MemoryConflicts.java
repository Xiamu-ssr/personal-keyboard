package app.doudou.ime.memory;

import android.app.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import java.util.*;
import org.json.*;

/** Only real disagreements appear next to the record they would change. */
public final class MemoryConflicts {
  private final Activity activity;
  private final KeyboardApp app;
  private final Navigator nav;

  public MemoryConflicts(Activity activity, KeyboardApp app, Navigator nav) {
    this.activity = activity;
    this.app = app;
    this.nav = nav;
  }

  public void draw(LinearLayout parent, MemoryStore.Item item) {
    List<JSONObject> changes = new ArrayList<>();
    for (MemoryStore.Item proposal : app.store.list("proposal", 0))
      try {
        JSONObject candidate = new JSONObject(proposal.body),
            op = candidate.getJSONObject("operation");
        if (item.globalId.equals(op.optString("id"))
            || (item.kind.equals("person") && item.globalId.equals(op.optString("personId"))))
          changes.add(candidate);
      } catch (Exception ignored) {
      }
    if (changes.isEmpty()) return;
    TextView review = Ui.button(activity, "核对变化 · " + changes.size(), () -> show(item, changes, 0));
    review.setTextColor(Ui.appAccent());
    parent.addView(review);
  }

  private void show(MemoryStore.Item item, List<JSONObject> changes, int index) {
    if (index >= changes.size() || activity.isFinishing()) {
      nav.refresh();
      return;
    }
    JSONObject candidate = changes.get(index),
        op = candidate.optJSONObject("operation"),
        value = op == null ? null : op.optJSONObject("value");
    if (value == null) return;
    String text =
        value.optString(
            "text",
            value.optString("scene", value.optString("name", value.optString("key", "关系"))));
    if (value.has("value")) text += "：" + value.optString("value");
    if (value.has("rules")) {
      JSONArray rules = value.optJSONArray("rules");
      for (int i = 0; rules != null && i < rules.length(); i++)
        text += (i == 0 ? "\n" : "、") + rules.optString(i);
    }
    if (value.has("type")) text += "：" + value.optString("type");
    String reason = candidate.optString("reason");
    new AlertDialog.Builder(activity)
        .setTitle(item.title + "有了新资料")
        .setMessage(text + (reason.isEmpty() ? "" : "\n\n" + reason))
        .setPositiveButton(
            "更新", (d, w) -> resolve(candidate, true, () -> show(item, changes, index + 1)))
        .setNeutralButton(
            "保留原记录", (d, w) -> resolve(candidate, false, () -> show(item, changes, index + 1)))
        .setNegativeButton("稍后", null)
        .show();
  }

  private void resolve(JSONObject candidate, boolean accept, Runnable next) {
    app.network.execute(
        () -> {
          try {
            app.personalization.sync();
            app.personalization.candidate(candidate.getString("id"), accept);
            activity.runOnUiThread(next);
          } catch (Exception e) {
            activity.runOnUiThread(
                () -> {
                  Ui.toast(activity, e.getMessage());
                  nav.refresh();
                });
          }
        });
  }
}
