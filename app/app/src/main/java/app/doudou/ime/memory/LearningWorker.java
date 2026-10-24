package app.doudou.ime.memory;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import app.doudou.ime.app.KeyboardApp;
import java.util.concurrent.TimeUnit;

/** A durable upload outbox; no long-lived background process is required. */
public final class LearningWorker extends Worker {
  public LearningWorker(@NonNull Context c, @NonNull WorkerParameters p) {
    super(c, p);
  }

  private static Constraints constraints() {
    return new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build();
  }

  public static void enqueue(Context c) {
    WorkManager.getInstance(c)
        .enqueueUniqueWork(
            "history-upload",
            ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(LearningWorker.class)
                .setInitialDelay(45, TimeUnit.SECONDS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build());
  }

  public static void schedule(Context c) {
    WorkManager work = WorkManager.getInstance(c);
    work.cancelUniqueWork("personal-history");
    work.enqueueUniquePeriodicWork(
        "memory-cache-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(LearningWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .build());
    enqueue(c);
  }

  @NonNull
  @Override
  public Result doWork() {
    KeyboardApp app = KeyboardApp.instance;
    if (!app.cloud.loggedIn()) return Result.success();
    try {
      new LearningBatch(app).run();
      if (app.cloud.preferences().getBoolean("syncEnabled", false)) app.sync.run();
      return app.personalization.pending() ? Result.retry() : Result.success();
    } catch (Exception e) {
      return Result.retry();
    }
  }
}
