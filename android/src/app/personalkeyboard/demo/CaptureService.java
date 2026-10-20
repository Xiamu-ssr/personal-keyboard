package app.personalkeyboard.demo;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.Display;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;

public class CaptureService extends AccessibilityService {
    static volatile CaptureService current;
    interface Result {void complete(boolean ok,String message);}
    @Override protected void onServiceConnected(){current=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){clearCapture();if(current==this)current=null;super.onDestroy();}
    void clearCapture(){new File(getCacheDir(),"last-capture.png").delete();}
    void capture(int keyboardTop,Result callback){
        takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult result){
                try{
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                    if(hardware==null)throw new IllegalStateException();
                    Bitmap full=hardware.copy(Bitmap.Config.ARGB_8888,false);
                    hardware.recycle();result.getHardwareBuffer().close();
                    int statusId=getResources().getIdentifier("status_bar_height","dimen","android");
                    int top=statusId==0?0:getResources().getDimensionPixelSize(statusId);
                    int bottom=Math.min(full.getHeight(),keyboardTop);
                    if(bottom<=top){full.recycle();callback.complete(false,"聊天区域尚未稳定，请重试");return;}
                    Bitmap image=Bitmap.createBitmap(full,0,top,full.getWidth(),bottom-top);
                    if(image!=full)full.recycle();
                    File output=new File(getCacheDir(),"last-capture.png");
                    try(FileOutputStream stream=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,stream);}
                    String detail=image.getWidth()+" × "+image.getHeight();image.recycle();
                    callback.complete(true,detail);
                }catch(Exception e){callback.complete(false,"截屏处理失败");}
            }
            @Override public void onFailure(int code){callback.complete(false,"系统截屏失败（"+code+"）");}
        });
    }
}
