package app.doudou.ime.cloud;

import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/**
 * Polls the same durable Cloud task after transport interruptions. Cancellation releases its
 * reservation.
 */
public final class CloudTask {
  private final CloudClient client;
  private final String account;
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private volatile String job = "";

  public CloudTask(CloudClient c) {
    client = c;
    account = c.userId();
  }

  public void cancel() {
    cancelled.set(true);
    String id = job;
    if (!id.isEmpty())
      app.doudou.ime.app.KeyboardApp.instance.network.execute(
          () -> {
            try {
              client.request(account, "/v1/jobs/" + id + "/cancel", "POST", new JSONObject());
            } catch (Exception ignored) {
            }
          });
  }

  public JSONObject run(String kind, JSONObject input, String sourceJob) throws Exception {
    String fingerprint = kind + ":" + sha(input.toString() + sourceJob),
        requestId = client.requestId(fingerprint);
    JSONObject payload =
        new JSONObject().put("requestId", requestId).put("kind", kind).put("input", input);
    if (sourceJob != null && !sourceJob.isEmpty()) payload.put("sourceJob", sourceJob);
    JSONObject current = client.request(account, "/v1/jobs", "POST", payload);
    job = current.getString("id");
    long deadline = System.currentTimeMillis() + 85000;
    while (true) {
      if (cancelled.get()) {
        client.request(account, "/v1/jobs/" + job + "/cancel", "POST", new JSONObject());
        throw new java.io.IOException("任务已取消");
      }
      String status = current.getString("status");
      if (status.equals("succeeded")) {
        JSONObject result = current.optJSONObject("result");
        if (result == null) throw new java.io.IOException("恢复期已过，请查看本机历史");
        client.completeRequest(fingerprint);
        return result;
      }
      if (status.equals("failed") || status.equals("cancelled")) {
        JSONObject error = current.optJSONObject("error");
        throw new java.io.IOException(error == null ? "任务失败，预留已释放" : error.optString("message"));
      }
      if (System.currentTimeMillis() > deadline) throw new java.io.IOException("任务仍在处理，可重试恢复原任务");
      Thread.sleep(650);
      current = client.request(account, "/v1/jobs/" + job, "GET", null);
    }
  }

  private static String sha(String value) throws Exception {
    byte[] b = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
    StringBuilder s = new StringBuilder();
    for (byte c : b) s.append(String.format(java.util.Locale.ROOT, "%02x", c));
    return s.toString();
  }
}
