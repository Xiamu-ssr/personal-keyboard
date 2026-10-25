package app.doudou.ime.account;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.ThemedActivity;
import app.doudou.ime.design.Ui;
import org.json.*;

/** Financial metadata only. A bill does not turn temporary Cloud jobs into a chat-history store. */
public final class CreditUsageActivity extends ThemedActivity {
  private LinearLayout body;
  private String account;
  private JSONObject pricing;
  private TextView rates;
  @Override protected void onResume() {
    super.onResume();
    if (account != null && !account.equals(((KeyboardApp)getApplication()).cloud.userId())) {
      if (body != null) body.removeAllViews();
      finish();
    }
  }
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    KeyboardApp app = (KeyboardApp)getApplication(); account = app.cloud.userId();
    LinearLayout root = Ui.col(this); root.setBackgroundColor(Ui.bg()); Ui.pad(this, root, 16, 8);
    LinearLayout header = Ui.row(this);
    header.addView(Ui.icon(this, "返回", this::finish), new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    header.addView(Ui.text(this, "积分账单", 23, Ui.ink()), new LinearLayout.LayoutParams(0, -2, 1));
    rates = Ui.button(this, "计价", this::showRates); rates.setEnabled(false);
    header.addView(rates, new LinearLayout.LayoutParams(-2, Ui.dp(this, 48))); root.addView(header);
    ScrollView scroll = new ScrollView(this); body = Ui.col(this); scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); Ui.content(this, root); load();
  }
  private String when(String iso) { return when(iso, "MM-dd HH:mm:ss"); }
  private String when(String iso, String pattern) {
    try {
      java.time.Instant instant = java.time.Instant.parse(iso);
      return java.time.format.DateTimeFormatter.ofPattern(pattern)
          .withZone(java.time.ZoneId.systemDefault()).format(instant);
    } catch (Exception ignored) { return ""; }
  }
  private void load() {
    body.removeAllViews(); body.addView(new ProgressBar(this));
    KeyboardApp app = (KeyboardApp)getApplication();
    app.network.execute(() -> {
      try {
        JSONArray jobs = app.cloud.request(account, "/v1/credit-usage", "GET", null).getJSONArray("jobs");
        JSONObject tariff = app.cloud.request(account, "/v1/credit-pricing", "GET", null);
        runOnUiThread(() -> {
          if (isFinishing() || !account.equals(app.cloud.userId())) return;
          body.removeAllViews();
          pricing = tariff; rates.setEnabled(true);
          examples(tariff.optJSONArray("examples"));
          TextView recent = Ui.text(this, "最近消费", 19, Ui.ink());
          recent.setTypeface(null, android.graphics.Typeface.BOLD);
          Ui.pad(this, recent, 6, 18); body.addView(recent);
          int shown = 0;
          for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.optJSONObject(i);
            if (job == null || !"succeeded".equals(job.optString("status"))) continue;
            String kind = job.optString("kind");
            String title = title(kind);
            if (kind.equals("advisor") && job.optInt("replyCount") > 0)
              title += job.optInt("replyCount") == 6 ? " · 六条" : " · 三条";
            String amount = new java.text.DecimalFormat("#,##0.###").format(job.optDouble("credits"));
            LinearLayout row = Ui.cell(this, "", title, when(job.optString("createdAt")),
                "−" + amount + " Credit", () -> showBill(job));
            row.setContentDescription("消费明细：" + title + "，" + when(job.optString("createdAt")));
            body.addView(row); body.addView(Ui.divider(this)); shown++;
          }
          if (shown == 0) { TextView empty = Ui.text(this, "还没有积分支出", 17, Ui.sub()); Ui.pad(this, empty, 8, 28); body.addView(empty); }
        });
      } catch (Exception e) {
        runOnUiThread(() -> {
          if (isFinishing() || !account.equals(app.cloud.userId())) return;
          body.removeAllViews(); body.addView(Ui.text(this, "暂时无法加载账单", 17, Ui.ink()));
          Ui.gap(this, body, 16); body.addView(Ui.button(this, "重试", this::load));
        });
      }
    });
  }

  private void examples(JSONArray examples) {
    if (examples == null || examples.length() == 0) return;
    LinearLayout list = Ui.card(this); Ui.pad(this, list, 14, 10);
    TextView title = Ui.text(this, "用量示例", 20, Ui.ink()); title.setTypeface(null, android.graphics.Typeface.BOLD);
    Ui.pad(this, title, 0, 8); list.addView(title);
    java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.###");
    for (int i = 0; i < examples.length(); i++) {
      JSONObject example = examples.optJSONObject(i); if (example == null) continue;
      double amount = example.optDouble("credits");
      String value = amount == 0 ? "0 Credit" : "约 " + format.format(amount) + " Credit";
      LinearLayout row = Ui.row(this); row.setGravity(Gravity.CENTER_VERTICAL);
      row.setMinimumHeight(Ui.dp(this, 52)); Ui.pad(this, row, 0, 10);
      row.addView(Ui.text(this, example.optString("title"), 16, Ui.ink()), new LinearLayout.LayoutParams(0, -2, 1));
      TextView price = Ui.text(this, value, 16, Ui.appAccent()); price.setTypeface(null, android.graphics.Typeface.BOLD);
      row.addView(price); row.setFocusable(true);
      row.setContentDescription(example.optString("title") + "用量示例：" + value);
      row.setBackground(Ui.pressable(this, android.graphics.Color.TRANSPARENT, 8));
      row.setOnClickListener(v -> {
        String detail = "recording".equals(example.optString("id")) ? "仅保存、播放音频不扣积分；实时转写按实际用量扣分。"
            : "按输入 " + example.optInt("input") + "、输出 " + example.optInt("output")
              + " Token 演算。这是用量示例，不是固定收费或实测均价。";
        new android.app.AlertDialog.Builder(this).setTitle(example.optString("title")).setMessage(detail)
            .setPositiveButton("完成", null).show();
      });
      list.addView(row);
      if (i + 1 < examples.length()) list.addView(Ui.divider(this));
    }
    Ui.gap(this, list, 10);
    list.addView(Ui.text(this, "用量示例，实际随内容与上下文变化。1 Credit = 0.001 元。", 13, Ui.sub()));
    body.addView(list);
  }

  private String title(String kind) {
    return kind.equals("asr") ? "语音转写" : kind.equals("advisor") ? "帮我回"
        : kind.equals("translation") ? "翻译" : "语音润色";
  }

  private void showBill(JSONObject job) {
    LinearLayout list = Ui.col(this); Ui.pad(this, list, 22, 12);
    java.text.DecimalFormat f = new java.text.DecimalFormat("#,##0.###");
    list.addView(Ui.text(this, "−" + f.format(job.optDouble("credits")) + " Credit", 28, Ui.ink()));
    Ui.gap(this, list, 16);
    list.addView(Ui.text(this, when(job.optString("createdAt"), "yyyy-MM-dd HH:mm:ss"), 15, Ui.sub()));
    JSONObject bill = job.optJSONObject("billing");
    if (bill != null && !bill.isNull("inputTokens") && !bill.isNull("outputTokens")) {
      Ui.gap(this, list, 20);
      list.addView(Ui.text(this, "实际用量", 16, Ui.ink()));
      Ui.gap(this, list, 8);
      list.addView(Ui.text(this, "输入 " + f.format(bill.optLong("inputTokens")) + " Token  ×  "
          + f.format(bill.optDouble("inputCreditsPerThousand")) + " Credit / 千 Token", 15, Ui.sub()));
      Ui.gap(this, list, 8);
      list.addView(Ui.text(this, "输出 " + f.format(bill.optLong("outputTokens")) + " Token  ×  "
          + f.format(bill.optDouble("outputCreditsPerThousand")) + " Credit / 千 Token", 15, Ui.sub()));
      Ui.gap(this, list, 16);
      list.addView(Ui.text(this, "合计向上取整至 0.001 Credit；减免已计入实扣", 13, Ui.sub()));
    }
    Ui.gap(this, list, 16);
    list.addView(Ui.text(this, "本次计价 v" + job.optInt("pricingVersion"), 14, Ui.sub()));
    ScrollView scroll = new ScrollView(this); scroll.addView(list);
    new android.app.AlertDialog.Builder(this).setTitle(title(job.optString("kind")) + " · 消费明细")
        .setView(scroll).setPositiveButton("完成", null).setNeutralButton("复制账单号", (d, w) -> {
          android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
          clipboard.setPrimaryClip(android.content.ClipData.newPlainText("豆豆账单", job.optString("id")));
          Ui.toast(this, "已复制账单号");
        }).show();
  }

  private void showRates() {
    if (pricing == null) return;
    try {
      JSONObject definition = pricing.getJSONObject("definition");
      double markup = definition.getDouble("markupBasisPoints") / 10000;
      LinearLayout list = Ui.col(this); Ui.pad(this, list, 22, 12);
      list.addView(Ui.text(this, "按实际输入、输出用量计分", 16, Ui.ink()));
      Ui.gap(this, list, 8);
      list.addView(Ui.text(this, "单价：每 1,000 Token · Credit", 14, Ui.sub()));
      JSONArray models = definition.getJSONArray("models");
      java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.###");
      for (int i = 0; i < models.length(); i++) {
        JSONObject model = models.getJSONObject(i); Ui.gap(this, list, 18);
        String feature = model.getString("id").equals("qwen-audio-3.1-asr-flash-message")
            ? "语音转写" : "帮我回 · 语音润色 · 翻译";
        list.addView(Ui.text(this, feature, 17, Ui.ink())); Ui.gap(this, list, 8);
        list.addView(Ui.text(this, "输入 " + format.format(model.getDouble("inputMicroCnyPerMillion") * markup / 1000000)
            + "  ／  输出 " + format.format(model.getDouble("outputMicroCnyPerMillion") * markup / 1000000), 16, Ui.ink()));
      }
      Ui.gap(this, list, 22); list.addView(Ui.text(this, "按实际用量计分 · 1 Credit = 0.001 元", 14, Ui.sub()));
      ScrollView scroll = new ScrollView(this); scroll.addView(list);
      new android.app.AlertDialog.Builder(this).setTitle("积分计价").setView(scroll).setPositiveButton("完成", null).show();
    } catch (JSONException e) { Ui.toast(this, "计价暂不可用"); }
  }
}
