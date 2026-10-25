package app.doudou.ime.account;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.Ui;
import org.json.JSONObject;

/** One account identity and its available allowance, shared by My and Account. */
public final class AccountSummary {
  public static LinearLayout create(Context c, KeyboardApp app, Runnable open) {
    LinearLayout card = Ui.card(c);
    Ui.pad(c, card, 20, 20);
    LinearLayout identity = Ui.row(c);
    ImageView avatar = new ImageView(c);
    avatar.setImageResource(R.drawable.brand_logo);
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    avatar.setBackground(Ui.bg(c, c.getColor(R.color.brand_background), 16));
    avatar.setClipToOutline(true);
    avatar.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    identity.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
    LinearLayout labels = Ui.col(c);
    Ui.pad(c, labels, 14, 0);
    TextView name = Ui.text(c, app.cloud.loggedIn() ? app.cloud.handle() : "登录豆豆", 20, Ui.ink());
    name.setTypeface(null, Typeface.BOLD);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    labels.addView(name);
    Ui.gap(c, labels, 6);
    if (!app.cloud.loggedIn()) labels.addView(Ui.text(c, "语音与帮我回", 13, Ui.sub()));
    identity.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (open != null) {
      TextView arrow = Ui.text(c, "›", 26, Ui.sub());
      arrow.setGravity(Gravity.CENTER);
      identity.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 48)));
      identity.setBackground(Ui.pressable(c, android.graphics.Color.TRANSPARENT, 12));
      identity.setOnClickListener(v -> open.run());
      identity.setContentDescription(app.cloud.loggedIn() ? "账户与权益" : "登录豆豆账户");
    }
    card.addView(identity);
    JSONObject ent = app.cloud.cachedAccount().optJSONObject("entitlements");
    if (app.cloud.loggedIn() && ent != null) {
      Ui.gap(c, card, 24);
      LinearLayout usage = Ui.row(c);
      metric(c, usage, ent.optJSONObject("asr"), "转写字符可用");
      ViewSeparator.add(c, usage);
      metric(c, usage, ent.optJSONObject("advisor"), "军师次数可用");
      card.addView(usage);
    }
    return card;
  }

  private static void metric(Context c, LinearLayout row, JSONObject item, String label) {
    LinearLayout column = Ui.col(c);
    String number =
        item == null
            ? "—"
            : String.format(java.util.Locale.CHINA, "%,d", item.optLong("available"));
    TextView value = Ui.text(c, number, 26, Ui.ink());
    value.setTypeface(null, Typeface.BOLD);
    value.setSingleLine(true);
    value.setAutoSizeTextTypeUniformWithConfiguration(
        18, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    column.addView(value);
    Ui.gap(c, column, 7);
    column.addView(Ui.text(c, label, 12, Ui.sub()));
    row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private static final class ViewSeparator {
    static void add(Context c, LinearLayout row) {
      android.view.View line = new android.view.View(c);
      line.setBackgroundColor(Ui.line());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 1), Ui.dp(c, 36));
      lp.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 20), 0);
      row.addView(line, lp);
    }
  }
}
