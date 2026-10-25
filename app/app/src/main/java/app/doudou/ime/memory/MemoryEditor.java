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
    edit(item, kind, owner, initial, initialBody, false);
  }

  public void property(MemoryStore.Item item, long owner, String label, boolean relationship) {
    edit(item, relationship ? "relationship" : "memory", owner, label, "", true);
  }

  private void edit(MemoryStore.Item item, String kind, long owner,
      String initial, String initialBody, boolean fixed) {
    final String account = app.cloud.userId();
    if (!app.cloud.loggedIn()) {
      nav.open("account", 0);
      return;
    }
    app.network.execute(
        () -> {
          try {
            synchronized (app.personalization) {
              if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
              app.personalization.sync();
              MemoryStore.Item fresh = item == null ? null : app.store.get(item.id);
              if (item != null && (fresh == null || fresh.deleted || !fresh.globalId.equals(item.globalId)))
                throw new IllegalStateException("这项资料已变化，请刷新");
              long version = app.store.cacheVersion(account);
              activity.runOnUiThread(() -> showEdit(fresh, kind, owner, initial, initialBody, fixed, account, version));
            }
          } catch (Exception e) {
            activity.runOnUiThread(() -> Ui.toast(activity, "请联网同步后再修改"));
          }
        });
  }

  private void showEdit(
      MemoryStore.Item item, String kind, long owner, String initial, String initialBody, boolean fixed, String account, long version) {
    if (activity.isFinishing() || !account.equals(app.cloud.userId())) return;
    LinearLayout fields = Ui.col(activity);
    Ui.pad(activity, fields, 20, 14);
    EditText title =
        Ui.edit(
            activity,
            kind.equals("word")
                ? "热词"
                : kind.equals("style") ? "场景" : kind.equals("person") ? "称呼" : "资料名称",
            item == null ? initial : item.title);
    if (!fixed) { fields.addView(title); Ui.gap(activity, fields, 12); }
    EditText body =
        Ui.edit(
            activity,
            fixed ? initial : kind.equals("person") ? "别名，一行一个" : kind.equals("style") ? "表达习惯，一行一条" : "内容",
            item == null ? initialBody : item.body);
    body.setMinLines(fixed && !initial.equals("工作经历") ? 1 : 3);
    if (!kind.equals("word")) fields.addView(body);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(fields);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(fixed ? initial : item == null ? "添加" : "修订资料")
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
                        String changedTitle = fixed ? initial : title.getText().toString();
                        String changedBody = body.getText().toString();
                        dialog.getButton(-1).setEnabled(false);
                        app.network.execute(
                            () -> {
                              try {
                                synchronized (app.personalization) {
                                  app.personalization.expect(account, version);
                                  JSONObject op = fixed && item != null && changedBody.trim().isEmpty()
                                      ? app.personalization.deletion(item)
                                      : app.personalization.operation(item, kind, owner, changedTitle, changedBody);
                                  app.personalization.mutate(account, version, new JSONArray().put(op));
                                  app.cloud.completeRequest("draft:" + kind + ":" + owner);
                                }
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
                    }));
    dialog.show();
  }

  public void remove(MemoryStore.Item item) {
    final String account = app.cloud.userId();
    app.network.execute(
        () -> {
          try {
            if (!account.equals(app.cloud.userId())) throw new IllegalStateException("账户已变化");
            CloudMemory.Removal removed = app.personalization.remove(account, item);
            activity.runOnUiThread(
                () -> {
                  nav.refresh();
                  if (removed.restore == null) Ui.toast(activity, "已删除");
                  else
                    nav.notice(
                        "已删除",
                        () ->
                            app.network.execute(
                                () -> {
                                  try {
                                    app.personalization.mutate(
                                        account, removed.version,
                                        new JSONArray().put(removed.restore));
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

  public void source(MemoryStore.Item item) {
    try {
      MemoryStore.Item fresh = app.store.get(item.id);
      if (fresh == null || !fresh.globalId.equals(item.globalId)) throw new IllegalStateException("资料已变化");
      String kind = item.kind.equals("word") ? "hotword" : item.kind.equals("memory")
          ? item.aux.startsWith("relation:") ? "relation" : "fact" : item.kind;
      String key = kind + ":" + (item.owner > 0 ? app.store.get(item.owner).globalId + ":" : "") + item.globalId;
      JSONObject cache = app.store.cache(app.cloud.userId());
      JSONObject origins = cache.optJSONObject("origins");
      JSONObject origin = origins == null ? null : origins.optJSONObject(key);
      app.doudou.ime.history.HistoryStore.Entry history = origin == null ? null
          : app.histories.get(origin.optString("historyId"));
      if (history != null && history.account.equals(app.cloud.userId())) {
        if (history.revision != origin.optInt("revision")) Ui.toast(activity, "这条来源历史后来有修改");
        new app.doudou.ime.history.HistoryView(activity, app, nav).open(history);
        return;
      }
      JSONArray protectedItems = cache.optJSONArray("protected");
      boolean manual = false;
      for (int i = 0; protectedItems != null && i < protectedItems.length(); i++)
        if (key.equals(protectedItems.optString(i))) manual = true;
      Ui.toast(activity, manual ? "你手动保存的资料" : "来源历史不在这台手机");
    } catch (Exception e) { Ui.toast(activity, "暂时无法查看来源"); }
  }

  public static String sourceName(String source) {
    return source.equals("cloud") ? "个人记忆" : source.equals("manual") ? "手动添加" : "本机历史";
  }
}
