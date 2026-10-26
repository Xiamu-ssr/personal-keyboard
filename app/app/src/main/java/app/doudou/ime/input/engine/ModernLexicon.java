package app.doudou.ime.input.engine;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;

/** A versioned, indexed lexical resource; never loads 400k words into Java memory. */
public final class ModernLexicon implements PinyinEngine.Lexicon {
  private final SQLiteDatabase db;
  private final SpellingTrie spellings;

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
    // Build once, on KeyboardApp's dictionary worker. Primitive arrays keep the 359k-code
    // resource around 16 MB instead of allocating a Java object for each of its 1.15M edges.
    spellings = new SpellingTrie();
    try (Cursor cursor = db.rawQuery(
        "SELECT code,MAX(weight) FROM words GROUP BY code ORDER BY code", null)) {
      while (cursor.moveToNext()) spellings.add(cursor.getString(0), cursor.getInt(1));
    }
    spellings.seal();
  }

  @Override
  public List<PinyinEngine.Word> lookup(String raw, int limit) {
    if (limit <= 0) return Collections.emptyList();
    List<PinyinEngine.Word> all = lookupAll(raw);
    return all.subList(0, Math.min(limit, all.size()));
  }

  @Override
  public List<PinyinEngine.Word> lookupAll(String raw) {
    String code = raw.replace("'", "");
    List<PinyinEngine.Word> out = new ArrayList<>();
    add(
        out,
        "SELECT text,weight FROM words WHERE code=? ORDER BY weight DESC,text",
        new String[] {code},
        raw,
        315);
    if (code.length() >= 2 && code.matches("[b-df-hj-np-tv-z]+"))
      add(
          out,
          "SELECT text,weight FROM words WHERE initials=? ORDER BY weight DESC,text",
          new String[] {code},
          raw,
          260);
    if (code.length() >= 4)
      add(
          out,
          "SELECT text,weight FROM words WHERE code>? AND code<? ORDER BY weight DESC,text",
          new String[] {code, code + "{"},
          raw,
          190);
    return out;
  }

  @Override
  public List<PinyinEngine.Word> corrections(String raw) {
    List<PinyinEngine.Word> out = new ArrayList<>();
    // The hypothesis beam bounds alternative spellings, never the pages of exact candidates.
    for (SpellingTrie.Match match : spellings.search(raw, 16)) {
      try (Cursor c = db.rawQuery(
          "SELECT text,weight FROM words WHERE code=? ORDER BY weight DESC,text",
          new String[] {match.spelling})) {
        while (c.moveToNext())
          out.add(new PinyinEngine.Word(c.getString(0), raw,
              325 + Math.min(25, 2 * Math.log1p(Math.max(0, c.getInt(1)))) - 45 * match.cost,
              match.spelling, match.cost));
      }
    }
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
