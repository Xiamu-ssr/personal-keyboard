package app.doudou.ime.setup;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import app.doudou.ime.app.KeyboardApp;

/** A denied permission must offer a usable recovery destination. */
public final class PermissionActions {
  private PermissionActions(){}
  public static boolean microphoneNeedsSettings(Activity activity,KeyboardApp app){
    return app.prefs().getBoolean("microphoneRequested",false)
        && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
  }
  public static void microphone(Activity activity,KeyboardApp app){
    if(microphoneNeedsSettings(activity,app)){
      activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));
    }else{
      app.prefs().edit().putBoolean("microphoneRequested",true).apply();
      activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},20);
    }
  }
}
