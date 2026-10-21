package app.doudou.ime.input.engine;

import java.io.*;
import java.util.*;

/** Indexed phrase recall for initials; ranking and learning remain outside the lexicon. */
public final class InitialLexicon {
  public static final class Entry {
    public final String text, spelling;

    Entry(String text, String spelling) {
      this.text = text;
      this.spelling = spelling;
    }
  }

  private final Map<String, List<Entry>> entries;

  private InitialLexicon(Map<String, List<Entry>> values) {
    entries = values;
  }

  public static InitialLexicon read(Reader source) throws IOException {
    Map<String, List<Entry>> values = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(source)) {
      String line;
      int count = 0;
      while ((line = reader.readLine()) != null) {
        if (++count > 80000) throw new IOException("首字母词库超过上限");
        String[] fields = line.split("\t", -1);
        if (fields.length != 3 || fields[0].length() < 2 || fields[0].length() > 8) continue;
        values
            .computeIfAbsent(fields[0], key -> new ArrayList<>())
            .add(new Entry(fields[1], fields[2]));
      }
    }
    return new InitialLexicon(values);
  }

  public List<Entry> lookup(String code) {
    return entries.getOrDefault(code, Collections.emptyList());
  }
}
