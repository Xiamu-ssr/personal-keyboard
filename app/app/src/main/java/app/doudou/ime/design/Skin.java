package app.doudou.ime.design;

import android.app.Activity;
import android.content.Context;
import android.view.WindowInsetsController;
import app.doudou.ime.R;

/** Device appearance is separate from account memory and expression preferences. */
public final class Skin {
  private static volatile boolean light;

  public static boolean light() { return light; }

  public static void load(Context context) {
    light = "light".equals(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("skin", "dark"));
  }

  public static int theme() { return light ? R.style.AppTheme_Light : R.style.AppTheme; }

  public static void select(Context context, boolean value) {
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("skin", value ? "light" : "dark").commit();
    load(context.getApplicationContext());
    context.getApplicationContext().setTheme(theme());
  }

  public static void systemBars(Activity activity) {
    activity.getWindow().setStatusBarColor(Ui.bg());
    activity.getWindow().setNavigationBarColor(Ui.bg());
    activity.getWindow().setNavigationBarContrastEnforced(false);
    WindowInsetsController controller = activity.getWindow().getDecorView().getWindowInsetsController();
    if (controller != null) {
      int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
      controller.setSystemBarsAppearance(light ? flags : 0, flags);
    }
  }

  public static int choose(int dark, int bright) { return light ? bright : dark; }
}
