package app.doudou.ime.cloud;

import okhttp3.Call;

/** Cancels only the task's current result wait, never its submission or settlement. */
public final class RequestCancellation {
  private boolean cancelled;
  private Call current;

  public synchronized void attach(Call call) {
    current = call;
    if (cancelled) call.cancel();
  }

  public synchronized void detach(Call call) {
    if (current == call) current = null;
  }

  public synchronized void cancel() {
    cancelled = true;
    if (current != null) current.cancel();
  }
}
