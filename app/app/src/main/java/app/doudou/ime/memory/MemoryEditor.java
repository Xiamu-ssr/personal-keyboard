package app.doudou.ime.memory;

import android.app.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import org.json.*;

/** Cloud acknowledgements precede edits to the local personalization cache. */
public final class MemoryEditor {
  private final Activity activity;
  private final KeyboardApp app;
  private final Navigator nav;

  public MemoryEditor(Activity activity, KeyboardApp app, Navigator nav) {
    this.activity = activity;
    this.app = app;
    this.nav = nav;
  }

  public void edit(MemoryStore.Item item, String kind, long owner) {
    edit(item, kind, owner, "", "");
  }

  public void edit(
      MemoryStore.Item item, String kind, long owner, String initial, String initialBody) {
    if (!app.cloud.loggedIn()) {
      nav.open("account", 0);
      return;
    }
    final long version = app.store.cacheVersion(app.cloud.userId());
    LinearLayout fields = Ui.col(activity);
    Ui.pad(activity, fields, 20, 14);
    EditText title =
        Ui.edit(
            activity,
            kind.equals("word")
                ? "热词"
                : kind.equals("style") ? "场景" : kind.equals("person") ? "称呼" : "资料名称",
            item == null ? initial : item.title);
    fields.addView(title);
    Ui.gap(activity, fields, 12);
    EditText body =
        Ui.edit(
            activity,
            kind.equals("person") ? "别名，一行一个" : kind.equals("style") ? "表达习惯，一行一条" : "内容",
            item == null ? initialBody : item.body);
    body.setMinLines(3);
    if (!kind.equals("word")) fields.addView(body);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(fields);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(item == null ? "添加" : "修订资料")
            .setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        JSONObject op =
                            app.personalization.operation(
                                item,
                                kind,
                                owner,
                                title.getText().toString(),
                                body.getText().toString());
                        dialog.getButton(-1).setEnabled(false);
                        app.network.execute(
                            () -> {
                              try {
                                app.personalization.mutate(version, new JSONArray().put(op));
                                app.cloud.completeRequest("draft:" + kind + ":" + owner);
                                activity.runOnUiThread(
                                    () -> {
                                      dialog.dismiss();
                                      nav.refresh();
                                    });
                              } catch (Exception e) {
                                activity.runOnUiThread(
                                    () -> {
                                      dialog.getButton(-1).setEnabled(true);
                                      Ui.toast(activity, e.getMessage());
                                    });
                              }
                            });
                      } catch (Exception e) {
                        Ui.toast(activity, e.getMessage());
                      }
                    }));
    dialog.show();
  }

  public void remove(MemoryStore.Item item) {
    app.network.execute(
        () -> {
          try {
            JSONObject restore =
                item.kind.equals("word") || item.kind.equals("style")
                    ? app.personalization.operation(
                        item, item.kind, item.owner, item.title, item.body)
                    : null;
            app.personalization.remove(item);
            activity.runOnUiThread(
                () -> {
                  nav.refresh();
                  if (restore == null) Ui.toast(activity, "已删除");
                  else
                    nav.notice(
                        "已删除",
                        () ->
                            app.network.execute(
                                () -> {
                                  try {
                                    app.personalization.mutate(
                                        app.store.cacheVersion(app.cloud.userId()),
                                        new JSONArray().put(restore));
                                    activity.runOnUiThread(nav::refresh);
                                  } catch (Exception e) {
                                    activity.runOnUiThread(
                                        () -> Ui.toast(activity, e.getMessage()));
                                  }
                                }));
                });
          } catch (Exception e) {
            activity.runOnUiThread(() -> Ui.toast(activity, e.getMessage()));
          }
        });
  }

  public static String sourceName(String source) {
    return source.equals("cloud") ? "个人记忆" : source.equals("manual") ? "手动添加" : "本机历史";
  }
}
