package app.doudou.ime.voice;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import app.doudou.ime.R;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;

/** Tap is a space. Holding this same key opens the dictation surface. */
public final class VoiceSpaceKey extends FrameLayout {
  public VoiceSpaceKey(Context context, boolean speech, Runnable action) {
    super(context);
    setBackground(Ui.pressable(context, Ui.key(), 7));
    setElevation(Ui.dp(context, .8f));
    setContentDescription("空格");
    setFocusable(true);
    setOnClickListener(v -> action.run());
    if (speech) {
      View mark = Icons.image(context, R.drawable.ic_graphic_eq, Ui.sub());
      addView(mark, new LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26), Gravity.CENTER));
      setTooltipText("按住说话，松手结束");
    } else {
      TextView label = Ui.text(context, "空格", 15, Ui.ink());
      label.setGravity(Gravity.CENTER);
      label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(label, new LayoutParams(-1, -1));
    }
  }
}
