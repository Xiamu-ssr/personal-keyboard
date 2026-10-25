package app.doudou.ime.people;

import android.content.Context;
import app.doudou.ime.memory.MemoryStore;
import java.util.*;
import org.json.*;

/** Optional profile rows share their names with the Cloud learning contract. */
public final class PersonFields {
  public final List<JSONObject> fields = new ArrayList<>();

  public PersonFields(Context context) {
    try (java.io.InputStream stream = context.getAssets().open("person.json")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[4096]; int size;
      while ((size = stream.read(buffer)) != -1) out.write(buffer, 0, size);
      JSONArray list = new JSONObject(out.toString("UTF-8")).getJSONArray("fields");
      for (int i = 0; i < list.length(); i++) fields.add(list.getJSONObject(i));
    } catch (Exception e) { throw new IllegalStateException("人物字段不可用", e); }
  }

  public boolean matches(JSONObject field, MemoryStore.Item item) {
    if (field.optString("kind").equals("relation")) return item.aux.equals("relation:self");
    if (field.optString("kind").equals("person"))
      return !item.aux.startsWith("relation:") && Arrays.asList("nickname", "昵称", "称呼", "name").contains(item.title);
    if (!field.optString("kind").equals("fact") || item.aux.startsWith("relation:")) return false;
    if (!field.optString("key").equals("工作经历")
        && !Arrays.asList("", "当前", "current").contains(item.aux)) return false;
    if (field.optString("key").equals(item.title)) return true;
    JSONArray aliases = field.optJSONArray("aliases");
    for (int i = 0; aliases != null && i < aliases.length(); i++)
      if (aliases.optString(i).equalsIgnoreCase(item.title)) return true;
    return false;
  }
}
