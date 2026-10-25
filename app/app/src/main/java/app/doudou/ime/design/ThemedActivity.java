package app.doudou.ime.design;

import android.app.Activity;
import android.os.Bundle;

/** Native dialogs and system bars follow the same skin as the programmatic views. */
public abstract class ThemedActivity extends Activity {
  private boolean createdLight;

  @Override public void onCreate(Bundle state) {
    Skin.load(this);
    setTheme(Skin.theme());
    createdLight = Skin.light();
    super.onCreate(state);
    Skin.systemBars(this);
  }

  @Override protected void onResume() {
    super.onResume();
    if (createdLight != Skin.light()) recreate();
  }
}
