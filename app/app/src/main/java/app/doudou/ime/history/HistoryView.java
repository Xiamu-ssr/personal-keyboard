package app.doudou.ime.history;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.recording.RecordingActivity;
import java.util.*;
import org.json.*;

public final class HistoryView {
  private final Activity activity;
  private final KeyboardApp app;
  private final Navigator nav;

  public HistoryView(Activity a, KeyboardApp app, Navigator nav) {
    activity = a;
    this.app = app;
    this.nav = nav;
  }

  public void draw(LinearLayout parent, String query) {
    int count = 0;
    for (HistoryStore.Entry e : app.histories.list(500)) {
      String value = e.resultText.isEmpty() ? e.asrText : e.resultText;
      if (!query.isEmpty()
          && !value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      String kind = e.type.equals("recording") ? "录音笔" : e.type.equals("advisor") ? "帮我回" : "语音";
      String preview = value.isEmpty() ? (e.type.equals("recording") ? "录音" : "回复建议") : value;
      LinearLayout card = Ui.card(activity);
      TextView text = Ui.text(activity, preview, 17, Ui.ink());
      text.setMaxLines(3);
      text.setEllipsize(android.text.TextUtils.TruncateAt.END);
      text.setLineSpacing(Ui.dp(activity, 3), 1);
      card.addView(text);
      Ui.gap(activity, card, 12);
      card.addView(Ui.text(activity, kind + " · " + Ui.when(e.created), 12, Ui.sub()));
      card.setFocusable(true);
      card.setContentDescription(kind + "历史");
      card.setOnClickListener(v -> open(e));
      parent.addView(card);
      Ui.gap(activity, parent, 10);
      count++;
    }
    if (count == 0) Ui.empty(activity, parent, "还没有历史", "");
  }

  public void open(HistoryStore.Entry e) {
    if (e.type.equals("recording")) {
      activity.startActivity(new Intent(activity, RecordingActivity.class).putExtra("id", e.id));
      return;
    }
    LinearLayout content = Ui.col(activity);
    Ui.pad(activity, content, 18, 12);
    String value = e.resultText.isEmpty() ? e.asrText : e.resultText;
    if (!value.isEmpty()) {
      TextView text = Ui.text(activity, value, 19, Ui.ink());
      text.setLineSpacing(Ui.dp(activity, 6), 1);
      text.setTextIsSelectable(true);
      content.addView(text);
      Ui.gap(activity, content, 14);
      content.addView(Ui.button(activity, "编辑", () -> edit(e, value)));
    }
    if (!e.asrText.isEmpty() && !e.asrText.equals(value)) {
      LinearLayout original = Ui.col(activity);
      original.setVisibility(View.GONE);
      TextView raw = Ui.text(activity, e.asrText, 16, Ui.sub());
      raw.setTextIsSelectable(true);
      Ui.pad(activity, raw, 0, 12);
      original.addView(raw);
      content.addView(
          Ui.button(
              activity,
              "识别原文",
              () ->
                  original.setVisibility(
                      original.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
      content.addView(original);
    }
    if (e.screenshots.length() > 0) {
      Ui.gap(activity, content, 18);
      HorizontalScrollView gallery = new HorizontalScrollView(activity);
      LinearLayout images = Ui.row(activity);
      gallery.addView(images);
      content.addView(gallery);
      for (int i = 0; i < e.screenshots.length(); i++) {
        String ref = e.screenshots.optString(i);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 3;
        Bitmap bitmap =
            BitmapFactory.decodeFile(app.histories.imageFile(ref).getAbsolutePath(), opts);
        if (bitmap == null) continue;
        ImageView image = new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("聊天截图" + (i + 1));
        image.setOnClickListener(v -> showImage(ref));
        LinearLayout.LayoutParams size =
            new LinearLayout.LayoutParams(Ui.dp(activity, 132), Ui.dp(activity, 230));
        size.rightMargin = Ui.dp(activity, 8);
        images.addView(image, size);
      }
    }
    if (e.replyGroups.length() > 0) {
      Ui.gap(activity, content, 16);
      LinearLayout pages = Ui.col(activity);
      content.addView(pages);
      showGroup(pages, e, e.replyGroups.length() - 1);
    }
    if (e.messages.length() > 0) {
      LinearLayout transcript = Ui.col(activity);
      transcript.setVisibility(View.GONE);
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < e.messages.length(); i++) {
        JSONObject m = e.messages.optJSONObject(i);
        if (m != null)
          text.append(
                  m.optString("speaker").equals("self")
                      ? "我："
                      : m.optString("speaker").equals("other") ? "对方：" : "未知：")
              .append(m.optString("text"))
              .append('\n');
      }
      TextView lines = Ui.text(activity, text.toString().trim(), 15, Ui.sub());
      lines.setLineSpacing(Ui.dp(activity, 4), 1);
      Ui.pad(activity, lines, 0, 12);
      transcript.addView(lines);
      Ui.gap(activity, content, 12);
      content.addView(
          Ui.button(
              activity,
              "聊天摘录",
              () ->
                  transcript.setVisibility(
                      transcript.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
      content.addView(transcript);
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(e.type.equals("voice") ? "语音" : "帮我回")
            .setView(scroll)
            .setPositiveButton(
                value.isEmpty() ? "完成" : "复制",
                (d, w) -> {
                  if (!value.isEmpty()) copy(value);
                })
            .setNeutralButton(
                "删除",
                (d, w) ->
                    new AlertDialog.Builder(activity)
                        .setTitle("删除这条历史？")
                        .setPositiveButton(
                            "删除",
                            (x, y) -> {
                              app.histories.remove(e.id);
                              nav.refresh();
                            })
                        .setNegativeButton("取消", null)
                        .show())
            .setNegativeButton("返回", null)
            .create();
    dialog.show();
  }

  private void showGroup(LinearLayout target, HistoryStore.Entry e, int index) {
    target.removeAllViews();
    LinearLayout header = Ui.row(activity);
    TextView back = Ui.button(activity, "‹", () -> showGroup(target, e, index - 1));
    back.setEnabled(index > 0);
    header.addView(back);
    TextView count = Ui.text(activity, (index + 1) + " / " + e.replyGroups.length(), 13, Ui.sub());
    count.setGravity(Gravity.CENTER);
    header.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
    TextView next = Ui.button(activity, "›", () -> showGroup(target, e, index + 1));
    next.setEnabled(index < e.replyGroups.length() - 1);
    header.addView(next);
    target.addView(header);
    try {
      JSONArray replies = e.replyGroups.getJSONObject(index).getJSONArray("replies");
      for (int i = 0; i < replies.length(); i++) {
        JSONObject reply = replies.getJSONObject(i);
        TextView line =
            Ui.text(
                activity, reply.getString("style") + "\n" + reply.getString("text"), 16, Ui.ink());
        line.setLineSpacing(Ui.dp(activity, 4), 1);
        Ui.pad(activity, line, 10, 12);
        line.setBackground(Ui.pressable(activity, Ui.surface(), 12));
        String text = reply.getString("text");
        line.setOnClickListener(
            v -> {
              copy(text);
              Ui.toast(activity, "已复制");
            });
        target.addView(line);
        Ui.gap(activity, target, 8);
      }
    } catch (Exception ignored) {
    }
  }

  private void edit(HistoryStore.Entry e, String text) {
    EditText field = Ui.edit(activity, "文字", text);
    field.setMinLines(4);
    new AlertDialog.Builder(activity)
        .setTitle("编辑历史")
        .setView(field)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存",
            (d, w) -> {
              String value = field.getText().toString().trim();
              if (!value.isEmpty()) {
                app.histories.observed(e.id, value);
                nav.refresh();
                Ui.toast(activity, "已保存");
              }
            })
        .show();
  }

  private void showImage(String ref) {
    ImageView image = new ImageView(activity);
    image.setImageBitmap(BitmapFactory.decodeFile(app.histories.imageFile(ref).getAbsolutePath()));
    image.setAdjustViewBounds(true);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(image);
    new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("关闭", null).show();
  }

  private void copy(String text) {
    ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText("豆豆历史", text));
  }
}
