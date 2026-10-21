package app.doudou.ime.memory;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import app.doudou.ime.app.KeyboardApp;
import java.util.concurrent.TimeUnit;

public final class LearningWorker extends Worker {
  public LearningWorker(@NonNull Context c, @NonNull WorkerParameters p) {
    super(c, p);
  }

  public static void schedule(Context c) {
    Constraints constraints =
        new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build();
    PeriodicWorkRequest request =
        new PeriodicWorkRequest.Builder(LearningWorker.class, 5, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();
    WorkManager.getInstance(c)
        .enqueueUniquePeriodicWork("personal-history", ExistingPeriodicWorkPolicy.KEEP, request);
  }

  @NonNull
  @Override
  public Result doWork() {
    KeyboardApp app = KeyboardApp.instance;
    if (!app.cloud.loggedIn()) return Result.success();
    try {
      if (app.prefs().getBoolean("cloudLearning", false)) new LearningBatch(app).run();
      if (app.cloud.preferences().getBoolean("syncEnabled", false)) app.sync.run();
      return Result.success();
    } catch (Exception e) {
      return Result.retry();
    }
  }
}
