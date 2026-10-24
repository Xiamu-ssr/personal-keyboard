package app.doudou.ime.memory;

import android.os.*;
import app.doudou.ime.advisor.PaletteStore;
import app.doudou.ime.app.KeyboardApp;
import java.util.concurrent.atomic.AtomicBoolean;

/** A bounded foreground refresh, backed by WorkManager when Android suspends the process. */
public final class MemoryRefresh {
  private static final AtomicBoolean running = new AtomicBoolean();
  private static final Handler main = new Handler(Looper.getMainLooper());

  public static void start(KeyboardApp app) {
    if (!app.cloud.loggedIn() || !running.compareAndSet(false, true)) return;
    String account = app.cloud.userId();
    long deadline = SystemClock.elapsedRealtime() + 120000;
    app.network.execute(
        () -> {
          try {
            new LearningBatch(app).run();
            new PaletteStore(app).sync();
            app.cloud.account();
            for (Runnable observer : app.personalization.observers) main.post(observer);
          } catch (Exception ignored) {
          }
          poll(app, account, deadline);
        });
  }

  private static void poll(KeyboardApp app, String account, long deadline) {
    if (!account.equals(app.cloud.userId())) {
      running.set(false);
      return;
    }
    try {
      app.personalization.sync();
    } catch (Exception e) {
      running.set(false);
      LearningWorker.enqueue(app);
      return;
    }
    if (app.personalization.pending() && SystemClock.elapsedRealtime() < deadline) {
      main.postDelayed(() -> app.network.execute(() -> poll(app, account, deadline)), 4000);
    } else {
      running.set(false);
      if (app.personalization.pending()) LearningWorker.enqueue(app);
    }
  }
}
