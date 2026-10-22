package app.doudou.ime.history;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import java.util.*;

public final class HistoryView {
  private final Activity activity;
  private final KeyboardApp app;
  private final Navigator nav;

  public HistoryView(Activity activity, KeyboardApp app, Navigator nav) {
    this.activity = activity;
    this.app = app;
    this.nav = nav;
  }

  public void draw(LinearLayout parent, String query) {
    List<HistoryStore.Entry> entries = app.histories.list(500);
    int count = 0;
    for (HistoryStore.Entry e : entries) {
      String value = e.resultText.isEmpty() ? e.asrText : e.resultText;
      if (!query.isEmpty()
          && !value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      count++;
      LinearLayout card = Ui.card(activity);
      String preview = value.isEmpty() ? "聊天截图 · " + e.screenshots.length() + " 张" : value;
      card.addView(
          Ui.cell(
              activity,
              e.type.equals("voice") ? "语音" : "军师",
              preview,
              Ui.when(e.updated),
              "",
              () -> open(e)));
      parent.addView(card);
      Ui.gap(activity, parent, 10);
    }
    if (count == 0) Ui.empty(activity, parent, "还没有历史", "语音结果和军师截图会保存在这里。");
  }

  public void open(HistoryStore.Entry e) {
    LinearLayout content = Ui.col(activity);
    Ui.pad(activity, content, 18, 12);
    if (!e.resultText.isEmpty()) field(content, "结果稿", e.resultText);
    if (!e.asrText.isEmpty() && !e.asrText.equals(e.resultText))
      field(content, "ASR 原文", e.asrText);
    if (!e.insertedText.isEmpty() && !e.insertedText.equals(e.resultText))
      field(content, "实际填入稿", e.insertedText);
    if (e.messages.length() > 0) {
      StringBuilder messages = new StringBuilder();
      for (int i = 0; i < e.messages.length(); i++) {
        org.json.JSONObject m = e.messages.optJSONObject(i);
        if (m != null)
          messages
              .append(
                  m.optString("speaker").equals("self")
                      ? "本人："
                      : m.optString("speaker").equals("other") ? "对方：" : "未确认：")
              .append(m.optString("text"))
              .append('\n');
      }
      field(content, "截图文字依据", messages.toString().trim());
    }
    for (int i = 0; i < e.screenshots.length(); i++) {
      String ref = e.screenshots.optString(i);
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 3;
      Bitmap image =
          BitmapFactory.decodeFile(app.histories.imageFile(ref).getAbsolutePath(), options);
      if (image != null) {
        ImageView preview = new ImageView(activity);
        preview.setImageBitmap(image);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        content.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 260)));
        Ui.gap(activity, content, 10);
      }
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    new AlertDialog.Builder(activity)
        .setTitle(e.type.equals("voice") ? "语音历史" : "军师历史")
        .setView(scroll)
        .setPositiveButton(
            "复制结果",
            (d, w) ->
                ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(
                        ClipData.newPlainText(
                            "豆豆历史", e.resultText.isEmpty() ? e.asrText : e.resultText)))
        .setNeutralButton(
            "删除",
            (d, w) ->
                new AlertDialog.Builder(activity)
                    .setTitle("删除这条历史和截图？")
                    .setPositiveButton(
                        "删除",
                        (x, y) -> {
                          app.histories.remove(e.id);
                          nav.refresh();
                        })
                    .setNegativeButton("取消", null)
                    .show())
        .setNegativeButton("返回", null)
        .show();
  }

  private void field(LinearLayout content, String title, String text) {
    content.addView(Ui.text(activity, title, 12, Ui.SUB));
    Ui.gap(activity, content, 5);
    content.addView(Ui.text(activity, text, 16, Ui.INK));
    Ui.gap(activity, content, 16);
  }
}
