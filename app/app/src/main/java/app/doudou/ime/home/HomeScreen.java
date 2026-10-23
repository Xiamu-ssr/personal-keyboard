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
    LinearLayout hero = Ui.col(activity);
    Ui.pad(activity, hero, 4, 22);
    TextView title = Ui.text(activity, "开口，\n就写好了。", 34, Ui.INK);
    title.setTypeface(null, Typeface.BOLD);
    title.setLineSpacing(Ui.dp(activity, 6), 1);
    hero.addView(title);
    Ui.gap(activity, hero, 16);
    hero.addView(Ui.text(activity, "长按空格说话，松手填入。", 16, Ui.SUB));
    Ui.gap(activity, hero, 28);
    hero.addView(Ui.primary(activity, "试试豆豆", () -> activity.startActivity(new Intent(activity, PracticeActivity.class))));
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
            active ? "到任意输入框，开始自己的表达。" : "本地拼音无需登录，语音和军师使用豆豆账户。",
            active ? "正在使用" : "",
            () -> {
              if (enabled())
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker();
              else activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }));
    add(setup);
    LinearLayout quick = Ui.card(activity);
    quick.addView(Ui.cell(activity, "历史", "我的历史", "文字与截图，留在手机", "", () -> nav.open("history", 0)));
    quick.addView(Ui.divider(activity));
    quick.addView(Ui.cell(activity, "词典", "我的表达习惯", "热词、文风，让豆豆更懂你", "", () -> nav.open("memory", 0)));
    add(quick);
    caption("点军师，看看怎么回。选好后填入，发送由你决定。");
  }
}
