package app.doudou.ime.sync;

import java.util.*;

/** Per-record vector clocks distinguish a newer edit from two independent device edits. */
public final class RevisionClock {
  public enum Order {
    EQUAL,
    BEFORE,
    AFTER,
    CONCURRENT
  }

  public static Order compare(Map<String, Long> a, Map<String, Long> b) {
    boolean more = false, less = false;
    Set<String> keys = new HashSet<>(a.keySet());
    keys.addAll(b.keySet());
    for (String k : keys) {
      long x = a.getOrDefault(k, 0L), y = b.getOrDefault(k, 0L);
      more |= x > y;
      less |= x < y;
    }
    return more && less ? Order.CONCURRENT : more ? Order.AFTER : less ? Order.BEFORE : Order.EQUAL;
  }

  public static Map<String, Long> merge(Map<String, Long> a, Map<String, Long> b) {
    Map<String, Long> result = new TreeMap<>(a);
    for (String k : b.keySet()) result.put(k, Math.max(result.getOrDefault(k, 0L), b.get(k)));
    return result;
  }

  public static Map<String, Long> advance(Map<String, Long> a, String device) {
    Map<String, Long> result = new TreeMap<>(a);
    result.put(device, result.getOrDefault(device, 0L) + 1);
    return result;
  }

  private RevisionClock() {}
}
