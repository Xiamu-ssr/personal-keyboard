package app.doudou.ime.app;

import app.doudou.ime.design.Skin;

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
  public app.doudou.ime.history.HistoryStore histories;
  public app.doudou.ime.clipboard.ClipboardHistory clips;
  public app.doudou.ime.memory.CloudMemory personalization;
  public final ExecutorService worker = Executors.newSingleThreadExecutor();
  public final ExecutorService network = Executors.newFixedThreadPool(2);
  public volatile PinyinEngine pinyin;
  public volatile String dictionaryError = "";

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    androidx.emoji2.text.EmojiCompat.init(new androidx.emoji2.bundled.BundledEmojiCompatConfig(this)
        .setReplaceAll(true).setUseAfterUpdatableSystemFonts(true));
    Skin.load(this);
    setTheme(Skin.theme());
    cloud = new app.doudou.ime.cloud.CloudClient(this);
    store = new MemoryStore(this);
    histories = new app.doudou.ime.history.HistoryStore(this);
    clips = new app.doudou.ime.clipboard.ClipboardHistory(this);
    personalization = new app.doudou.ime.memory.CloudMemory(this);
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
    if (prefs().getInt("productMigration", 0) < 5) {
      try {
        org.json.JSONObject legacy = store.exportData();
        org.json.JSONArray profiles = new org.json.JSONArray(),
            items = legacy.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
          org.json.JSONObject item = items.getJSONObject(i);
          if (java.util.Arrays.asList("word", "style", "person", "memory")
              .contains(item.optString("kind"))) profiles.put(item);
        }
        store.setMeta(
            "profile.migration", new org.json.JSONObject().put("items", profiles).toString());
        store.setMeta("profile.migration.owner", cloud.userId());
        histories.migrateLegacy(legacy, cloud.userId());
        store.discardLegacy();
        prefs()
            .edit()
            .remove("cloudLearning")
            .remove("advisorVision")
            .putInt("productMigration", 5)
            .apply();
      } catch (Exception e) {
        throw new IllegalStateException("本机历史迁移未完成，原数据库保留", e);
      }
    }
    if (prefs().getInt("productMigration", 0) < 8) {
      prefs()
          .edit()
          .remove("personalRewrite")
          .remove("history")
          .remove("learning")
          .putInt("productMigration", 8)
          .apply();
    }
    try {
      personalization.activate();
    } catch (Exception e) {
      throw new IllegalStateException("个人记忆缓存损坏，请同步恢复", e);
    }
    app.doudou.ime.memory.LearningWorker.schedule(this);
    app.doudou.ime.memory.LearningWorker.enqueue(this);
    worker.execute(this::openDecoder);
    worker.execute(() -> app.doudou.ime.recording.RecordingStore.recover(this));
    worker.execute(
        () -> {
          histories.prune(prefs().getInt("retentionDays", 30));
          histories.collectImages();
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
      pinyin.lexicon(new app.doudou.ime.input.engine.ModernLexicon(this));
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
