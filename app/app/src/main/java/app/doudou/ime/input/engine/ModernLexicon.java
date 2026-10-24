package app.doudou.ime.input.engine;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;

/** A versioned, indexed lexical resource; never loads 400k words into Java memory. */
public final class ModernLexicon implements PinyinEngine.Lexicon {
  private final SQLiteDatabase db;

  public ModernLexicon(Context context) throws Exception {
    File file = new File(context.getFilesDir(), "lexicon-08.db");
    if (!file.exists()) {
      File temp = new File(context.getFilesDir(), "lexicon-08.tmp");
      try (InputStream in = context.getAssets().open("modern-lexicon.db");
          FileOutputStream out = new FileOutputStream(temp)) {
        byte[] bytes = new byte[32768];
        int n;
        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        out.getFD().sync();
      }
      if (!temp.renameTo(file)) throw new IOException("词库安装失败");
    }
    db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
  }

  @Override
  public List<PinyinEngine.Word> lookup(String raw, int limit) {
    String code = raw.replace("'", "");
    List<PinyinEngine.Word> out = new ArrayList<>();
    add(
        out,
        "SELECT text,weight FROM words WHERE code=? ORDER BY weight DESC LIMIT ?",
        new String[] {code, "" + limit},
        raw,
        315);
    if (code.length() >= 2 && code.matches("[b-df-hj-np-tv-z]+"))
      add(
          out,
          "SELECT text,weight FROM words WHERE initials=? ORDER BY weight DESC LIMIT ?",
          new String[] {code, "" + limit},
          raw,
          260);
    if (code.length() >= 4 && out.size() < limit)
      add(
          out,
          "SELECT text,weight FROM words WHERE code>? AND code<? ORDER BY weight DESC LIMIT ?",
          new String[] {code, code + "{", "" + limit},
          raw,
          190);
    return out;
  }

  private void add(
      List<PinyinEngine.Word> out, String sql, String[] args, String code, double base) {
    try (Cursor c = db.rawQuery(sql, args)) {
      int rank = 0;
      while (c.moveToNext())
        out.add(
            new PinyinEngine.Word(
                c.getString(0), code, base + Math.min(25, c.getInt(1) / 150.) - rank++));
    }
  }
}
