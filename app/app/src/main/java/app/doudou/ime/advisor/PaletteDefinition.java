package app.doudou.ime.advisor;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

/** Native editor and Cloud share the same versioned attribute contract. */
public final class PaletteDefinition {
  private static JSONObject definition;

  public static synchronized JSONObject contract(Context context) {
    if (definition == null) {
      try (InputStream input = context.getAssets().open("palette.json")) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; int n;
        while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
        definition = new JSONObject(bytes.toString("UTF-8"));
      } catch (Exception e) { throw new IllegalStateException("表达配置无法读取", e); }
    }
    return definition;
  }

  public static JSONArray fields(Context c) throws JSONException { return contract(c).getJSONArray("attributes"); }
  public static JSONArray templates(Context c) throws JSONException { return contract(c).getJSONArray("templates"); }

  public static JSONObject defaults(Context c) throws JSONException {
    JSONArray slots = new JSONArray(), templates = templates(c);
    for (int i = 0; i < 6; i++) {
      JSONObject p = templates.getJSONObject(i);
      slots.put(new JSONObject().put("id", "slot" + (i + 1)).put("name", p.getString("name"))
          .put("attributes", new JSONObject(p.getJSONObject("attributes").toString())));
    }
    return new JSONObject().put("schemaVersion", 2).put("version", 0).put("slots", slots);
  }

  public static JSONObject inherit(Context c) throws JSONException {
    JSONObject result = new JSONObject(); JSONArray fields = fields(c);
    for (int i = 0; i < fields.length(); i++) {
      JSONObject field = fields.getJSONObject(i);
      result.put(field.getString("id"), field.getString("kind").equals("multi") ? new JSONArray() : "inherit");
    }
    return result;
  }

  public static JSONObject attributes(Context c, JSONObject raw) throws JSONException {
    JSONArray fields = fields(c);
    if (raw.length() != fields.length()) throw new JSONException("表达属性不完整");
    JSONObject result = new JSONObject();
    for (int i = 0; i < fields.length(); i++) {
      JSONObject f = fields.getJSONObject(i); String id = f.getString("id");
      JSONArray options = f.getJSONArray("options"); Set<String> valid = new HashSet<>();
      for (int j = 0; j < options.length(); j++) valid.add(options.getJSONObject(j).getString("id"));
      if (f.getString("kind").equals("multi")) {
        JSONArray selected = raw.getJSONArray(id); Set<String> values = new HashSet<>();
        for (int j = 0; j < selected.length(); j++) {
          String value = selected.getString(j);
          if (!valid.contains(value) || !values.add(value)) throw new JSONException("表达手法无效");
        }
        if (selected.length() > f.getInt("maxItems") || (values.contains("plain") && values.size() != 1)) throw new JSONException("表达手法组合无效");
        JSONArray ordered = new JSONArray();
        for (int j = 0; j < options.length(); j++) {
          String value = options.getJSONObject(j).getString("id");
          if (values.contains(value)) ordered.put(value);
        }
        result.put(id, ordered);
      } else {
        String value = raw.getString(id);
        if (!valid.contains(value)) throw new JSONException("表达选项无效");
        result.put(id, value);
      }
    }
    return result;
  }

  public static void validate(Context c, JSONObject palette) throws JSONException {
    if (palette.getInt("schemaVersion") != 2) throw new JSONException("请更新豆豆后再编辑表达");
    JSONArray slots = palette.getJSONArray("slots");
    if (slots.length() != 6) throw new JSONException("调色盘需要六个槽位");
    for (int i = 0; i < slots.length(); i++) {
      JSONObject slot = slots.getJSONObject(i);
      if (!slot.getString("id").equals("slot" + (i + 1)) || slot.getString("name").trim().isEmpty()) throw new JSONException("表达槽位无效");
      attributes(c, slot.getJSONObject("attributes"));
    }
  }

  /** One-time local cache migration; fresh canonical data is fetched before editing. */
  public static JSONObject migrateCache(Context c, JSONObject old) throws JSONException {
    if (old.optInt("schemaVersion") == 2) { validate(c, old); return old; }
    JSONArray slots = old.getJSONArray("slots"), templates = templates(c);
    if (slots.length() != 6) throw new JSONException("原调色盘需要重新同步");
    for (int i = 0; i < slots.length(); i++) {
      JSONObject slot = slots.getJSONObject(i); JSONObject preset = null;
      for (int j = 0; j < 6; j++) if (templates.getJSONObject(j).getString("id").equals(slot.getString("expression"))) preset = templates.getJSONObject(j);
      if (preset == null) throw new JSONException("原表达类型无法迁移");
      slot.remove("expression");
      slot.put("attributes", new JSONObject(preset.getJSONObject("attributes").toString()));
    }
    old.put("schemaVersion", 2); validate(c, old); return old;
  }

  public static String summary(Context c, JSONObject value) {
    List<String> chosen = new ArrayList<>();
    try {
      JSONArray fields = fields(c);
      // Show the differentiating technique first, then distance and length.
      for (String key : new String[] {"rhetoric", "warmth", "directness", "length", "formality"}) {
        for (int i = 0; i < fields.length(); i++) {
          JSONObject f = fields.getJSONObject(i);
          if (!f.getString("id").equals(key)) continue;
          JSONArray options = f.getJSONArray("options");
          for (int j = 0; j < options.length(); j++) {
            JSONObject o = options.getJSONObject(j); String id = o.getString("id");
            boolean selected = f.getString("kind").equals("multi") ? contains(value.optJSONArray(key), id) : id.equals(value.optString(key));
            if (selected && !id.equals("inherit") && chosen.size() < 3) chosen.add(o.getString("name"));
          }
        }
      }
    } catch (Exception ignored) { }
    return chosen.isEmpty() ? "随本人文风" : android.text.TextUtils.join(" · ", chosen);
  }

  public static boolean contains(JSONArray values, String id) {
    if (values != null) for (int i = 0; i < values.length(); i++) if (id.equals(values.optString(i))) return true;
    return false;
  }
}
