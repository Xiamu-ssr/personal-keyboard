package app.doudou.ime.capture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit screenshots and an explicit earlier-page gesture; never enumerates chat nodes. */
public final class CaptureService extends AccessibilityService {
  public static volatile CaptureService current;

  public interface Result {
    void complete(Bitmap image, String error);
  }

  public interface Scrolled { void complete(boolean success); }

  /** A downward finger stroke reveals earlier chat, wholly above the shrunken IME. */
  public static void earlier(int keyboardTop, Scrolled callback) {
    CaptureService service=current;
    if(service==null){callback.complete(false);return;}
    float width=service.getResources().getDisplayMetrics().widthPixels;
    Path path=new Path();path.moveTo(width*.5f,keyboardTop*.28f);path.lineTo(width*.5f,keyboardTop*.74f);
    AtomicBoolean done=new AtomicBoolean();
    android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    main.postDelayed(()->{if(done.compareAndSet(false,true))callback.complete(false);},1800);
    boolean accepted=service.dispatchGesture(new GestureDescription.Builder()
        .addStroke(new GestureDescription.StrokeDescription(path,0,320)).build(),
        new GestureResultCallback(){
          @Override public void onCompleted(GestureDescription gesture){if(done.compareAndSet(false,true))callback.complete(true);}
          @Override public void onCancelled(GestureDescription gesture){if(done.compareAndSet(false,true))callback.complete(false);}
        },main);
    if(!accepted && done.compareAndSet(false,true))callback.complete(false);
  }

  public static int[] signature(Bitmap bitmap) {
    int[] pixels=new int[96*128];
    for(int y=0;y<128;y++)for(int x=0;x<96;x++)
      pixels[y*96+x]=bitmap.getPixel(Math.min(bitmap.getWidth()-1,(2*x+1)*bitmap.getWidth()/192),
          Math.min(bitmap.getHeight()-1,(2*y+1)*bitmap.getHeight()/256));
    return pixels;
  }

  @Override
  protected void onServiceConnected() {
    current = this;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent e) {}

  @Override
  public void onInterrupt() {}

  @Override
  public void onDestroy() {
    if (current == this) current = null;
    super.onDestroy();
  }

  public static void capture(int keyboardTop, Result cb) {
    CaptureService service = current;
    if (service == null) {
      cb.complete(null, "请在设置中开启「主动截屏」权限");
      return;
    }
    AtomicBoolean done = new AtomicBoolean();
    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    h.postDelayed(
        () -> {
          if (done.compareAndSet(false, true)) cb.complete(null, "截屏超时，可以重新试一次");
        },
        6000);
    try {
      service.takeScreenshot(
          Display.DEFAULT_DISPLAY,
          service.getMainExecutor(),
          new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult result) {
              HardwareBuffer buffer = result.getHardwareBuffer();
              Bitmap hardware = null, copy = null, cropped = null;
              try {
                hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                if (hardware == null) throw new IllegalStateException("empty bitmap");
                copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                int statusId =
                    service.getResources().getIdentifier("status_bar_height", "dimen", "android");
                int top =
                    statusId == 0 ? 0 : service.getResources().getDimensionPixelSize(statusId);
                int bottom = Math.min(copy.getHeight(), keyboardTop);
                if (bottom - top < 100) throw new IllegalStateException("invalid editor bounds");
                cropped = Bitmap.createBitmap(copy, 0, top, copy.getWidth(), bottom - top);
                if (cropped == copy) copy = null;
                if (done.compareAndSet(false, true)) {
                  cb.complete(cropped, null);
                  cropped = null;
                }
              } catch (Exception e) {
                if (done.compareAndSet(false, true)) cb.complete(null, "当前窗口无法截屏；它可能受系统或应用保护");
              } finally {
                if (cropped != null) cropped.recycle();
                if (copy != null) copy.recycle();
                if (hardware != null) hardware.recycle();
                buffer.close();
              }
            }

            @Override
            public void onFailure(int error) {
              if (done.compareAndSet(false, true))
                cb.complete(
                    null,
                    error == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                        ? "截屏间隔太短，请稍后重试"
                        : "系统拒绝截屏（" + error + "），请返回或重试");
            }
          });
    } catch (Exception ex) {
      if (done.compareAndSet(false, true)) cb.complete(null, "截屏权限不可用，请在设置中检查");
    }
  }
}
