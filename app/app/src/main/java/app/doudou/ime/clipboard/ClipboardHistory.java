package app.doudou.ime.clipboard;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Device-local text clips. Never included in learning or Cloud backup. */
public final class ClipboardHistory extends SQLiteOpenHelper {
  public static final int LIMIT = 30;
  public static final class Clip {
    public final long id, time;
    public final String text;
    Clip(long id, String text, long time) { this.id=id; this.text=text; this.time=time; }
  }
  public ClipboardHistory(Context context) { this(context, "clipboard.db"); }
  public ClipboardHistory(Context context, String name) { super(context, name, null, 1); }
  @Override public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL UNIQUE, created INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  }
  @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {}

  public synchronized boolean capture(ClipData data) {
    if (data == null || data.getItemCount() == 0) return false;
    ClipDescription description = data.getDescription();
    if (description != null && description.getExtras() != null
        && description.getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) return false;
    CharSequence value = data.getItemAt(0).getText();
    if (value == null || value.toString().trim().isEmpty() || value.length() > 10000) return false;
    SQLiteDatabase db = getWritableDatabase();
    String fingerprint;
    try {
      byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((value.toString()+":"+description.getTimestamp()).getBytes("UTF-8"));
      fingerprint=android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP);
    }catch(Exception error){return false;}
    try(Cursor seen=db.rawQuery("SELECT value FROM meta WHERE key='lastClip'",null)) {
      if(seen.moveToFirst() && fingerprint.equals(seen.getString(0)))return false;
    }
    ContentValues seen=new ContentValues();seen.put("key","lastClip");seen.put("value",fingerprint);
    db.insertWithOnConflict("meta",null,seen,SQLiteDatabase.CONFLICT_REPLACE);
    ContentValues row = new ContentValues();
    row.put("text", value.toString()); row.put("created", System.currentTimeMillis());
    db.insertWithOnConflict("clips", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    prune(db);
    return true;
  }

  private void prune(SQLiteDatabase db) {
    db.delete("clips", "created<?", new String[] {""+(System.currentTimeMillis()-7*86400000L)});
    db.execSQL("DELETE FROM clips WHERE id NOT IN (SELECT id FROM clips ORDER BY created DESC,id DESC LIMIT "+LIMIT+")");
  }
  public synchronized List<Clip> list() {
    SQLiteDatabase db = getWritableDatabase(); prune(db);
    List<Clip> out = new ArrayList<>();
    try (Cursor c=db.rawQuery("SELECT id,text,created FROM clips ORDER BY created DESC,id DESC", null)) {
      while(c.moveToNext()) out.add(new Clip(c.getLong(0),c.getString(1),c.getLong(2)));
    }
    return out;
  }
  public synchronized void remove(long id) { getWritableDatabase().delete("clips", "id=?", new String[]{""+id}); }
  public synchronized void clear() { getWritableDatabase().delete("clips", null, null); }
  public synchronized void restore(List<Clip> clips) {
    SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
    try {
      for(Clip clip:clips){ContentValues row=new ContentValues();row.put("text",clip.text);row.put("created",clip.time);
        db.insertWithOnConflict("clips",null,row,SQLiteDatabase.CONFLICT_IGNORE);}
      prune(db);db.setTransactionSuccessful();
    }finally{db.endTransaction();}
  }
}
