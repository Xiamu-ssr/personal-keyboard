package app.doudou.ime.tests;

import static org.junit.Assert.*;
import app.doudou.ime.cloud.RequestCancellation;
import okhttp3.*;
import org.junit.Test;

public class RequestCancellationTest {
  private Call call() {
    return new OkHttpClient().newCall(new Request.Builder().url("http://127.0.0.1/").build());
  }

  @Test public void cancellingWaitReleasesItsCallAndAlsoCatchesTheAttachRace() {
    RequestCancellation wait = new RequestCancellation();
    Call first = call(); wait.attach(first); wait.cancel();
    assertTrue(first.isCanceled());
    Call raced = call(); wait.attach(raced);
    assertTrue(raced.isCanceled());
  }

  @Test public void completedCallIsDetachedSoCancellationDoesNotTouchOtherRequests() {
    RequestCancellation wait = new RequestCancellation();
    Call completed = call(); wait.attach(completed); wait.detach(completed);
    wait.cancel();
    assertFalse(completed.isCanceled());
  }
}
