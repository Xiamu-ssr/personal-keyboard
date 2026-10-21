package app.doudou.ime.home;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;

public final class HomeScreen extends Screen {
  public HomeScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  @Override
  protected void draw() {
    LinearLayout hero = Ui.card(activity);
    Ui.pad(activity, hero, 22, 24);
    GradientDrawable color =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xff34453c, 0xff232c27});
    color.setCornerRadius(Ui.dp(activity, 20));
    hero.setBackground(color);
    TextView title = Ui.text(activity, "说出来，\n还是你的语气。", 27, Ui.INK);
    title.setTypeface(null, Typeface.BOLD);
    title.setLineSpacing(Ui.dp(activity, 5), 1);
    hero.addView(title);
    Ui.gap(activity, hero, 12);
    hero.addView(Ui.text(activity, "日常打字、语音整理，和原地军师。", 13, 0xffb7c2bb));
    Ui.gap(activity, hero, 22);
    hero.addView(
        Ui.primary(
            activity,
            "试试豆豆",
            () -> activity.startActivity(new Intent(activity, PracticeActivity.class))));
    add(hero);
    String selected =
        Settings.Secure.getString(
            activity.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    boolean active = selected != null && selected.startsWith(activity.getPackageName() + "/");
    LinearLayout setup = Ui.card(activity);
    setup.addView(
        Ui.cell(
            activity,
            active ? "闪光" : "工具",
            active ? "键盘已就绪" : enabled() ? "切换到豆豆输入法" : "开启豆豆输入法",
            active ? "到任意输入框，开始自己的表达。" : "离线拼音先用起来，模型可以稍后再接。",
            active ? "正在使用" : "",
            () -> {
              if (enabled())
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker();
              else activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }));
    add(setup);
    add(Ui.eyebrow(activity, "属于你的表达"));
    LinearLayout stats = Ui.row(activity);
    stat(stats, "热词", app.store.list("word", 0).size(), "把专有词记准确", () -> nav.open("words", 0));
    stat(stats, "文风", app.store.list("style", 0).size(), "把习惯留下来", () -> nav.open("style", 0));
    add(stats);
    LinearLayout people = Ui.card(activity);
    people.addView(
        Ui.cell(
            activity,
            "人物",
            "人物与记忆",
            app.store.list("person", 0).size() + " 位人物 · 资料留在本机",
            "",
            () -> nav.open("people", 0)));
    add(people);
    caption("军师只在点按时读屏；回复填入输入框后，由你决定发送。");
  }

  private void stat(LinearLayout row, String label, int value, String hint, Runnable action) {
    LinearLayout card = Ui.card(activity);
    card.setBackground(Ui.pressable(activity, Ui.PANEL, 18));
    card.setOnClickListener(v -> action.run());
    card.addView(Ui.text(activity, label, 12, Ui.SUB));
    Ui.gap(activity, card, 10);
    TextView count = Ui.text(activity, "" + value, 30, Ui.INK);
    count.setTypeface(null, Typeface.BOLD);
    card.addView(count);
    Ui.gap(activity, card, 8);
    card.addView(Ui.text(activity, hint, 11, Ui.SUB));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    if (row.getChildCount() == 0) lp.rightMargin = Ui.dp(activity, 10);
    row.addView(card, lp);
  }
}
