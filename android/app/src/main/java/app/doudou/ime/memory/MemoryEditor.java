package app.doudou.ime.memory;

import android.app.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import java.util.*;

/** All memory mutation goes through revision checks and reversible deletion. */
public final class MemoryEditor {
  private final Activity activity;
  private final KeyboardApp app;
  private final Navigator nav;

  public MemoryEditor(Activity a, KeyboardApp app, Navigator nav) {
    activity = a;
    this.app = app;
    this.nav = nav;
  }

  public void edit(MemoryStore.Item item, String kind, long owner) {
    edit(item, kind, owner, "", "");
  }

  public void edit(
      MemoryStore.Item item, String kind, long owner, String initial, String initialBody) {
    LinearLayout fields = Ui.col(activity);
    Ui.pad(activity, fields, 20, 14);
    EditText title =
        Ui.edit(
            activity,
            kind.equals("word") ? "热词" : "名称 / 记忆标题",
            item == null ? initial : item.title);
    fields.addView(title);
    Ui.gap(activity, fields, 12);
    EditText body =
        Ui.edit(
            activity,
            kind.equals("word") ? "拼音 / 快捷码（可选）" : "内容 / 备注",
            item == null ? initialBody : item.body);
    body.setMinLines(kind.equals("word") ? 1 : 3);
    fields.addView(body);
    Ui.gap(activity, fields, 12);
    EditText aux = Ui.edit(activity, "备注（可选）", item == null ? "" : item.aux);
    String[] kinds = {"fact", "event", "preference", "inference"};
    Spinner type = new Spinner(activity);
    ArrayAdapter<String> types =
        new ArrayAdapter<>(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"事实", "事件", "沟通偏好", "待确认的推断"});
    type.setAdapter(types);
    type.setSelection(Math.max(0, Arrays.asList(kinds).indexOf(item == null ? "fact" : item.aux)));
    if (kind.equals("memory")) fields.addView(type);
    else if (kind.equals("word")) fields.addView(aux);
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
                        String extra =
                            kind.equals("memory")
                                ? kinds[type.getSelectedItemPosition()]
                                : kind.equals("person")
                                    ? item == null ? "" : item.aux
                                    : kind.equals("word") ? aux.getText().toString().trim() : "";
                        app.store.save(
                            item == null ? 0 : item.id,
                            item == null ? 0 : item.revision,
                            kind,
                            owner,
                            title.getText().toString(),
                            body.getText().toString(),
                            extra,
                            item == null ? "manual" : item.source);
                        dialog.dismiss();
                        nav.refresh();
                      } catch (Exception e) {
                        Ui.toast(activity, e.getMessage());
                      }
                    }));
    dialog.show();
  }

  public void remove(MemoryStore.Item i) {
    app.store.delete(i.id);
    nav.refresh();
    undo(i.id);
  }

  private void undo(long id) {
    nav.notice(
        "已删除",
        () -> {
          app.store.restore(id);
          nav.refresh();
        });
  }

  public static String sourceName(String value) {
    if (value.startsWith("capture:")) return "读屏";
    if (value.startsWith("input:")) return "修改反馈";
    if (value.startsWith("voice")) return "语音";
    if (value.startsWith("import:")) return "导入";
    return value.equals("manual") ? "手动添加" : "本机";
  }
}
