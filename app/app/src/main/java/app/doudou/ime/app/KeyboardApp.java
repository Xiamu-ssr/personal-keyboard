package app.doudou.ime.app;

import android.app.Application;
import android.content.*;
import app.doudou.ime.input.engine.InitialLexicon;
import app.doudou.ime.input.engine.PinyinEngine;
import app.doudou.ime.memory.MemoryStore;
import java.io.*;
import java.util.concurrent.*;

public final class KeyboardApp extends Application {
  public static KeyboardApp instance;
  public MemoryStore store;
  public app.doudou.ime.cloud.CloudClient cloud;
  public app.doudou.ime.sync.ManagedSync sync;
  public final ExecutorService worker = Executors.newSingleThreadExecutor();
  public final ExecutorService network = Executors.newFixedThreadPool(2);
  public volatile PinyinEngine pinyin;
  public volatile String dictionaryError = "";

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    cloud = new app.doudou.ime.cloud.CloudClient(this);
    store = new MemoryStore(this);
    sync = new app.doudou.ime.sync.ManagedSync(this);
    if (prefs().getInt("productMigration", 0) < 4) {
      getSharedPreferences("vault", MODE_PRIVATE).edit().clear().apply();
      prefs()
          .edit()
          .remove("modelBase")
          .remove("modelId")
          .remove("modelAnthropic")
          .remove("onlineVoice")
          .putInt("productMigration", 4)
          .apply();
    }
    app.doudou.ime.memory.LearningWorker.schedule(this);
    worker.execute(this::openDecoder);
    worker.execute(
        () -> {
          store.pruneHistory(prefs().getInt("retentionDays", 30));
          for (File f : getCacheDir().listFiles())
            if (f.getName().startsWith("capture-") || f.getName().startsWith("voice-")) f.delete();
        });
  }

  public SharedPreferences prefs() {
    return getSharedPreferences("settings", MODE_PRIVATE);
  }

  private void openDecoder() {
    try {
      File dictionary = new File(getFilesDir(), "dict_pinyin.dat");
      try (InputStream in = getAssets().open("dict_pinyin.dat");
          OutputStream out = new FileOutputStream(dictionary)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
      pinyin =
          new PinyinEngine(
              dictionary.getAbsolutePath(),
              new File(getFilesDir(), "dict.user").getAbsolutePath(),
              InitialLexicon.read(
                  new java.io.InputStreamReader(getAssets().open("initials.tsv"), "UTF-8")));
    } catch (Exception | LinkageError ex) {
      dictionaryError = "词库读取失败，请重新安装";
    }
  }

  public void clearDecoderLearning() {
    worker.execute(
        () -> {
          if (pinyin != null) pinyin.clearLearning();
        });
  }
}
