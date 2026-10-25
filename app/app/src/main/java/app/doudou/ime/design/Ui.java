package app.doudou.ime.design;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

public final class Ui {
  public static int bg() { return Skin.choose(0xff222222, 0xfff6f7fb); }
  public static int panel() { return Skin.choose(0xff2c2c2c, 0xffffffff); }
  public static int key() { return Skin.choose(0xff606060, 0xffffffff); }
  public static int ink() { return Skin.choose(0xfff4f4f4, 0xff22262e); }
  public static int sub() { return Skin.choose(0xffaaaaaa, 0xff606773); }
  public static int accent() { return Skin.choose(0xfff1f0ee, 0xff30343c); }
  public static int appAccent() { return Skin.choose(0xffa9c4ff, 0xff355fbc); }
  public static int appTint() { return Skin.choose(0xff30394b, 0xffe6edff); }
  public static int audioStart() { return Skin.choose(0xff14beca, 0xff007b8b); }
  public static int audioEnd() { return Skin.choose(0xff4c80ff, 0xff3866d6); }
  public static int surface() { return Skin.choose(0xff333333, 0xffe9ebf1); }
  public static int selected() { return Skin.choose(0xff464646, 0xffd8dfec); }
  public static int keyboard() { return Skin.choose(0xff222222, 0xffe3e5eb); }
  public static int specialKey() { return Skin.choose(0xff333333, 0xffcbd0da); }
  public static int hero() { return Skin.choose(0xff383838, 0xffebeff8); }
  public static int line() { return Skin.choose(0xff3a3a3a, 0xffd2d7e1); }
  public static int red() { return Skin.choose(0xffffb4ab, 0xffb3261e); }
  public static int voiceBackdrop() { return Skin.choose(0xff2a3843, 0xffd5e6f2); }
  public static int outline() { return Skin.choose(0xff535353, 0xffadb4c0); }
  public static int sheet() { return Skin.choose(0xff303030, 0xfffafbff); }
  public static int sheetOutline() { return Skin.choose(0xff505050, 0xffc6ccd7); }
  public static int withdrawalRed() { return Skin.choose(0xffff5a67, 0xffb32635); }

  public static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  public static GradientDrawable bg(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  public static TextView text(Context c, String s, int size, int color) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setIncludeFontPadding(false);
    return t;
  }

  public static LinearLayout col(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  public static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  public static void pad(Context c, View v, int x, int y) {
    v.setPadding(dp(c, x), dp(c, y), dp(c, x), dp(c, y));
  }

  public static void gap(Context c, LinearLayout p, int h) {
    p.addView(new View(c), new LinearLayout.LayoutParams(1, dp(c, h)));
  }

  public static TextView button(Context c, String s, Runnable click) {
    TextView t = text(c, s, 15, ink());
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    t.setGravity(Gravity.CENTER);
    pad(c, t, 14, 8);
    t.setMinHeight(dp(c, 44));
    t.setBackground(pressable(c, surface(), 12));
    t.setOnClickListener(v -> click.run());
    t.setFocusable(true);
    t.setContentDescription(s);
    return t;
  }

  public static TextView primary(Context c, String s, Runnable click) {
    TextView t = button(c, s, click);
    t.setTextColor(bg());
    t.setBackground(pressable(c, accent(), 14));
    return t;
  }

  public static EditText edit(Context c, String hint, String value) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(value);
    e.setTextColor(ink());
    e.setHintTextColor(sub());
    e.setTextSize(16);
    e.setMinHeight(dp(c, 52));
    e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
    e.setBackground(bg(c, panel(), 12));
    e.setSelectAllOnFocus(false);
    return e;
  }

  public static LinearLayout card(Context c) {
    LinearLayout l = col(c);
    pad(c, l, 16, 16);
    l.setBackground(bg(c, panel(), 18));
    return l;
  }

  public static void content(Activity a, View root) {
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

  public static void toast(Context c, String s) {
    Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
  }

  public static String when(long ms) {
    return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        .format(new java.util.Date(ms));
  }

  public static View icon(Context c, String name, Runnable click) {
    Glyph g = new Glyph(c, name);
    g.setContentDescription(name);
    g.setFocusable(true);
    g.setOnClickListener(v -> click.run());
    g.setMinimumWidth(dp(c, 44));
    g.setMinimumHeight(dp(c, 44));
    g.setBackground(pressable(c, android.graphics.Color.TRANSPARENT, 12));
    return g;
  }

  public static android.graphics.drawable.Drawable pressable(Context c, int color, int radius) {
    return new android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(Skin.choose(0x24ffffff, 0x16000000)),
        bg(c, color, radius),
        bg(c, android.graphics.Color.WHITE, radius));
  }

  public static View divider(Context c) {
    View v = new View(c);
    v.setBackgroundColor(line());
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(c, .5f)));
    return v;
  }

  public static PopupMenu menu(Context c, View anchor) {
    return new PopupMenu(new ContextThemeWrapper(c, Skin.light() ? android.R.style.Theme_Material_Light : android.R.style.Theme_Material), anchor);
  }

  public static LinearLayout cell(
      Context c, String icon, String title, String detail, String value, Runnable click) {
    LinearLayout row = row(c);
    pad(c, row, 0, 10);
    row.setMinimumHeight(dp(c, 58));
    if (!icon.isEmpty()) {
      row.addView(
          icon(
              c,
              icon,
              () -> {
                if (click != null) click.run();
              }),
          new LinearLayout.LayoutParams(dp(c, 36), dp(c, 40)));
      Space gap = new Space(c);
      row.addView(gap, new LinearLayout.LayoutParams(dp(c, 8), 1));
    }
    LinearLayout labels = col(c);
    labels.addView(text(c, title, 15, ink()));
    if (!detail.isEmpty()) {
      gap(c, labels, 5);
      TextView sub = text(c, detail, 12, sub());
      sub.setLineSpacing(dp(c, 3), 1);
      labels.addView(sub);
    }
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (!value.isEmpty()) {
      TextView trail = text(c, value, 12, sub());
      pad(c, trail, 6, 0);
      row.addView(trail);
    }
    if (click != null) {
      TextView arrow = text(c, "›", 23, sub());
      arrow.setGravity(Gravity.CENTER);
      row.addView(arrow, new LinearLayout.LayoutParams(dp(c, 20), dp(c, 32)));
      row.setBackground(pressable(c, android.graphics.Color.TRANSPARENT, 10));
      row.setOnClickListener(v -> click.run());
      row.setFocusable(true);
      row.setContentDescription(title + (value.isEmpty() ? "" : "，" + value));
    }
    return row;
  }

  public static View nav(Context c, String name, String icon, boolean selected, Runnable click) {
    LinearLayout item = col(c);
    item.setGravity(Gravity.CENTER);
    LinearLayout pill = row(c);
    pill.setGravity(Gravity.CENTER);
    pill.setBackground(bg(c, selected ? selected() : android.graphics.Color.TRANSPARENT, 20));
    Glyph glyph = new Glyph(c, icon);
    glyph.color = selected ? accent() : sub();
    pill.addView(glyph, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
    item.addView(pill, new LinearLayout.LayoutParams(dp(c, 60), dp(c, 32)));
    gap(c, item, 5);
    TextView caption = text(c, name, 11, selected ? ink() : sub());
    caption.setGravity(Gravity.CENTER);
    item.addView(caption);
    item.setContentDescription(name);
    item.setFocusable(true);
    item.setOnClickListener(v -> click.run());
    item.setBackground(pressable(c, android.graphics.Color.TRANSPARENT, 18));
    return item;
  }

  public static View tile(Context c, String icon, String name, Runnable click) {
    LinearLayout tile = col(c);
    tile.setGravity(Gravity.CENTER);
    pad(c, tile, 2, 10);
    tile.setBackground(pressable(c, surface(), 20));
    Glyph glyph = new Glyph(c, icon);
    tile.addView(glyph, new LinearLayout.LayoutParams(dp(c, 28), dp(c, 30)));
    gap(c, tile, 9);
    TextView label = text(c, name, 12, ink());
    label.setGravity(Gravity.CENTER);
    tile.addView(label);
    tile.setFocusable(true);
    tile.setContentDescription(name);
    tile.setOnClickListener(v -> click.run());
    return tile;
  }

  public static TextView eyebrow(Context c, String label) {
    TextView t = text(c, label, 12, sub());
    t.setLetterSpacing(.04f);
    pad(c, t, 2, 10);
    return t;
  }

  public static void empty(Context c, LinearLayout target, String title, String subtitle) {
    LinearLayout box = col(c);
    box.setGravity(Gravity.CENTER);
    pad(c, box, 16, 36);
    Glyph mark = new Glyph(c, "闪光");
    mark.color = accent();
    box.addView(mark, new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)));
    gap(c, box, 16);
    box.addView(text(c, title, 18, ink()));
    gap(c, box, 8);
    TextView desc = text(c, subtitle, 13, sub());
    desc.setGravity(Gravity.CENTER);
    desc.setLineSpacing(dp(c, 4), 1);
    box.addView(desc);
    target.addView(box, new LinearLayout.LayoutParams(-1, -2));
  }

  public static final class Glyph extends View {
    final String name;
    final Paint p = new Paint(3);
    public int color = ink();

    public Glyph(Context c, String n) {
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
      p.setColor(color);
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
      } else if (name.equals("换一条")) {
        c.drawArc(4, 4, 20, 20, 40, 285, false, p);
        c.drawLine(19, 4, 19, 10, p);
        c.drawLine(19, 10, 13, 9, p);
      } else if (name.equals("更多")) {
        p.setStyle(Paint.Style.FILL);
        for (int x = 5; x <= 19; x += 7) c.drawCircle(x, 12, 1.5f, p);
      } else if (name.equals("关闭")) {
        c.drawLine(6, 6, 18, 18, p);
        c.drawLine(18, 6, 6, 18, p);
      } else if (name.equals("清空练习区")) {
        c.drawLine(4, 6, 20, 6, p);
        c.drawLine(9, 3, 15, 3, p);
        c.drawRoundRect(6, 6, 18, 22, 2, 2, p);
        c.drawLine(10, 10, 10, 18, p);
        c.drawLine(14, 10, 14, 18, p);
      } else if (name.equals("收起") || name.equals("展开")) {
        c.drawLine(5, 9, 12, 16, p);
        c.drawLine(12, 16, 19, 9, p);
      } else if (name.equals("首页")) {
        Path q = new Path();
        q.moveTo(3, 11);
        q.lineTo(12, 3);
        q.lineTo(21, 11);
        q.lineTo(21, 21);
        q.lineTo(15, 21);
        q.lineTo(15, 14);
        q.lineTo(9, 14);
        q.lineTo(9, 21);
        q.lineTo(3, 21);
        q.close();
        c.drawPath(q, p);
      } else if (name.equals("词典") || name.equals("历史")) {
        c.drawRoundRect(5, 3, 20, 21, 2, 2, p);
        c.drawLine(9, 3, 9, 21, p);
        c.drawLine(12, 8, 17, 8, p);
        c.drawLine(12, 12, 17, 12, p);
      } else if (name.equals("人物")) {
        c.drawCircle(10, 7, 4, p);
        c.drawArc(3, 13, 17, 25, 180, 180, false, p);
        c.drawLine(18, 8, 22, 8, p);
        c.drawLine(18, 12, 22, 12, p);
      } else if (name.equals("设置")) {
        c.drawCircle(12, 12, 4, p);
        c.drawCircle(12, 12, 9, p);
        for (int a = 0; a < 6; a++) {
          double v = a * Math.PI / 3;
          c.drawLine(
              12 + (float) Math.cos(v) * 8,
              12 + (float) Math.sin(v) * 8,
              12 + (float) Math.cos(v) * 11,
              12 + (float) Math.sin(v) * 11,
              p);
        }
      } else if (name.equals("剪贴板")) {
        c.drawRoundRect(5, 5, 19, 22, 2, 2, p);
        c.drawRoundRect(9, 2, 15, 7, 1, 1, p);
        c.drawLine(9, 12, 15, 12, p);
        c.drawLine(9, 16, 15, 16, p);
      } else if (name.equals("隐私")) {
        c.drawRoundRect(5, 10, 19, 22, 2, 2, p);
        c.drawArc(8, 2, 16, 16, 180, 180, false, p);
        c.drawCircle(12, 15, 1, p);
      } else if (name.equals("闪光") || name.equals("整理")) {
        Path q = new Path();
        q.moveTo(12, 2);
        q.lineTo(15, 9);
        q.lineTo(22, 12);
        q.lineTo(15, 15);
        q.lineTo(12, 22);
        q.lineTo(9, 15);
        q.lineTo(2, 12);
        q.lineTo(9, 9);
        q.close();
        c.drawPath(q, p);
      } else if (name.equals("撤销")) {
        c.drawArc(5, 6, 21, 21, 200, 260, false, p);
        c.drawLine(3, 4, 3, 11, p);
        c.drawLine(3, 11, 10, 11, p);
      } else if (name.equals("光标")) {
        c.drawLine(12, 3, 12, 21, p);
        c.drawLine(2, 12, 8, 12, p);
        c.drawLine(2, 12, 5, 9, p);
        c.drawLine(2, 12, 5, 15, p);
        c.drawLine(22, 12, 16, 12, p);
        c.drawLine(22, 12, 19, 9, p);
        c.drawLine(22, 12, 19, 15, p);
      } else if (name.equals("停止")) {
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(6, 6, 18, 18, 2, 2, p);
      } else {
        c.drawCircle(12, 12, 8, p);
        c.drawLine(12, 7, 12, 17, p);
        c.drawLine(7, 12, 17, 12, p);
      }
      c.restore();
    }
  }
}
