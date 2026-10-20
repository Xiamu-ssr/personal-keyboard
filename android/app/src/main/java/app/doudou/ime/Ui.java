package app.doudou.ime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int BG = 0xff191c1b,
      PANEL = 0xff252927,
      KEY = 0xff3b403d,
      INK = 0xffedf3ee,
      SUB = 0xffa1ada5,
      ACCENT = 0xffb7e3c9,
      LINE = 0xff353c37,
      RED = 0xffffb4ab;

  static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable bg(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static TextView text(Context c, String s, int size, int color) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setIncludeFontPadding(false);
    return t;
  }

  static LinearLayout col(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static void pad(Context c, View v, int x, int y) {
    v.setPadding(dp(c, x), dp(c, y), dp(c, x), dp(c, y));
  }

  static void gap(Context c, LinearLayout p, int h) {
    p.addView(new View(c), new LinearLayout.LayoutParams(1, dp(c, h)));
  }

  static TextView button(Context c, String s, Runnable click) {
    TextView t = text(c, s, 15, INK);
    t.setGravity(Gravity.CENTER);
    pad(c, t, 14, 12);
    t.setMinHeight(dp(c, 48));
    t.setBackground(bg(c, KEY, 16));
    t.setOnClickListener(v -> click.run());
    t.setFocusable(true);
    t.setContentDescription(s);
    return t;
  }

  static TextView primary(Context c, String s, Runnable click) {
    TextView t = button(c, s, click);
    t.setTextColor(BG);
    t.setBackground(bg(c, ACCENT, 18));
    return t;
  }

  static EditText edit(Context c, String hint, String value) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(value);
    e.setTextColor(INK);
    e.setHintTextColor(SUB);
    e.setTextSize(16);
    e.setMinHeight(dp(c, 52));
    e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
    e.setBackground(bg(c, KEY, 14));
    e.setSelectAllOnFocus(false);
    return e;
  }

  static LinearLayout card(Context c) {
    LinearLayout l = col(c);
    pad(c, l, 18, 16);
    l.setBackground(bg(c, PANEL, 22));
    return l;
  }

  static void content(Activity a, View root) {
    a.getWindow().setDecorFitsSystemWindows(false);
    int l = root.getPaddingLeft(),
        t = root.getPaddingTop(),
        r = root.getPaddingRight(),
        b = root.getPaddingBottom();
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          Insets bars = i.getInsets(WindowInsets.Type.systemBars()),
              ime = i.getInsets(WindowInsets.Type.ime());
          v.setPadding(
              l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
          return i;
        });
    a.setContentView(root);
    root.requestApplyInsets();
  }

  static void toast(Context c, String s) {
    Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
  }

  static String when(long ms) {
    return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        .format(new java.util.Date(ms));
  }

  static View icon(Context c, String name, Runnable click) {
    Glyph g = new Glyph(c, name);
    g.setContentDescription(name);
    g.setFocusable(true);
    g.setOnClickListener(v -> click.run());
    g.setMinimumWidth(dp(c, 44));
    g.setMinimumHeight(dp(c, 44));
    return g;
  }

  static final class Glyph extends View {
    final String name;
    final Paint p = new Paint(3);

    Glyph(Context c, String n) {
      super(c);
      name = n;
    }

    @Override
    protected void onDraw(Canvas source) {
      super.onDraw(source);
      Canvas c = source;
      c.save();
      float scale = dp(getContext(), 24);
      c.translate((getWidth() - scale) / 2, (getHeight() - scale) / 2);
      c.scale(scale / 24, scale / 24);
      p.setColor(INK);
      p.setStrokeWidth(1.8f);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      if (name.equals("语音")) {
        c.drawRoundRect(9, 2, 15, 15, 3, 3, p);
        c.drawArc(5, 6, 19, 19, 0, 180, false, p);
        c.drawLine(12, 19, 12, 23, p);
      } else if (name.equals("军师")) {
        c.drawRoundRect(3, 3, 21, 18, 4, 4, p);
        c.drawLine(7, 18, 7, 22, p);
        c.drawLine(7, 22, 12, 18, p);
        c.drawCircle(8, 10, 1, p);
        c.drawCircle(16, 10, 1, p);
      } else if (name.equals("工具")) {
        for (int y = 4; y <= 14; y += 10)
          for (int x = 4; x <= 14; x += 10) c.drawRoundRect(x, y, x + 6, y + 6, 2, 2, p);
      } else if (name.equals("退格")) {
        Path q = new Path();
        q.moveTo(2, 12);
        q.lineTo(8, 5);
        q.lineTo(22, 5);
        q.lineTo(22, 19);
        q.lineTo(8, 19);
        q.close();
        c.drawPath(q, p);
        c.drawLine(12, 9, 18, 15, p);
        c.drawLine(18, 9, 12, 15, p);
      } else if (name.equals("大写")) {
        Path q = new Path();
        q.moveTo(3, 12);
        q.lineTo(12, 3);
        q.lineTo(21, 12);
        q.lineTo(16, 12);
        q.lineTo(16, 21);
        q.lineTo(8, 21);
        q.lineTo(8, 12);
        q.close();
        c.drawPath(q, p);
      } else if (name.equals("换行")) {
        c.drawLine(20, 4, 20, 13, p);
        c.drawLine(20, 13, 4, 13, p);
        c.drawLine(4, 13, 9, 8, p);
        c.drawLine(4, 13, 9, 18, p);
      } else if (name.equals("切换输入法")) {
        c.drawCircle(12, 12, 9, p);
        c.drawOval(8, 3, 16, 21, p);
        c.drawLine(3, 12, 21, 12, p);
      } else if (name.equals("返回")) {
        c.drawLine(16, 4, 8, 12, p);
        c.drawLine(8, 12, 16, 20, p);
      } else if (name.equals("关闭")) {
        c.drawLine(6, 6, 18, 18, p);
        c.drawLine(18, 6, 6, 18, p);
      } else {
        c.drawCircle(12, 12, 8, p);
        c.drawLine(12, 7, 12, 17, p);
        c.drawLine(7, 12, 17, 12, p);
      }
      c.restore();
    }
  }
}
