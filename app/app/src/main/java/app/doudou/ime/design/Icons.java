package app.doudou.ime.design;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

/** Material Symbols Rounded. Source and Apache license are in app/licenses/. */
public final class Icons {
  private Icons() {}

  public static ImageView image(Context context, int resource, int color) {
    ImageView image = new ImageView(context);
    image.setImageResource(resource);
    image.setImageTintList(ColorStateList.valueOf(color));
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return image;
  }

  public static ImageView button(Context context, int resource, String name, Runnable action) {
    ImageView image = image(context, resource, Ui.ink());
    int padding = Ui.dp(context, 11);
    image.setPadding(padding, padding, padding, padding);
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    image.setContentDescription(name);
    image.setTooltipText(name);
    image.setFocusable(true);
    image.setBackground(Ui.pressable(context, Color.TRANSPARENT, 24));
    image.setOnClickListener(v -> action.run());
    return image;
  }
}
