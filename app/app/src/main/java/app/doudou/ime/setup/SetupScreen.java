package app.doudou.ime.setup;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.design.Ui;

/** First-use guidance follows the three useful outcomes, without a permission wall. */
public final class SetupScreen extends Screen {
  public SetupScreen(Activity activity,KeyboardApp app,Navigator nav){super(activity,app,nav);}
  @Override protected void draw(){
    TextView lead=Ui.text(activity,"让豆豆准备好。",30,Ui.ink());
    lead.setTypeface(null,android.graphics.Typeface.BOLD);add(lead);
    caption("打字随时可用。说话、帮你回，按需开启。设置后回来，这里会自动更新。");
    String selected=Settings.Secure.getString(activity.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
    boolean active=selected!=null && selected.startsWith(activity.getPackageName()+"/");
    LinearLayout keyboard=section("01  换上豆豆键盘",active ? "已就绪 · 空格短按打字，长按说话。" : "先开启，再切换到豆豆。系统会显示输入法通用提示。");
    if(!active){Ui.gap(activity,keyboard,12);keyboard.addView(Ui.primary(activity,enabled() ? "切换到豆豆" : "开启豆豆键盘",()->{
      if(enabled())((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker();
      else activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
    }));}add(keyboard);
    boolean microphone=activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    LinearLayout voice=section("02  开口说话",app.cloud.loggedIn() && microphone ? "已就绪 · 按住空格，松手填入。" : "语音需要账户和麦克风。也可以稍后再开。");
    if(!app.cloud.loggedIn()){Ui.gap(activity,voice,10);voice.addView(Ui.primary(activity,"登录或注册",()->nav.open("account",0)));}
    if(!microphone){Ui.gap(activity,voice,6);voice.addView(Ui.button(activity,PermissionActions.microphoneNeedsSettings(activity,app) ? "去系统设置允许麦克风" : "允许麦克风",()->PermissionActions.microphone(activity,app)));}
    add(voice);
    LinearLayout advisor=section("03  帮你回消息",CaptureService.current!=null
        ? app.cloud.loggedIn() ? "已就绪 · 在键盘点「帮我回」。" : "读屏已允许，完成上面的登录即可使用。"
        : "你主动点按时才截屏，上传给模型生成回复；不会代发。");
    if(CaptureService.current==null){Ui.gap(activity,advisor,8);advisor.addView(Ui.button(activity,"开启帮我回",()->new AlertDialog.Builder(activity)
        .setTitle("允许豆豆看当前聊天")
        .setMessage("点「帮我回」时截图；点「多看一屏」时翻到较早聊天再截图。图片上传用于生成回复，开启历史后保存在手机。")
        .setPositiveButton("去开启",(d,w)->activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
        .setNegativeButton("稍后",null).show()));}add(advisor);
    caption("复制的文字仅在本机保留最近 30 条、7 天。隐私输入和系统标记的敏感内容不记录。");
    add(Ui.primary(activity,"开始使用",()->{app.prefs().edit().putBoolean("setupComplete",true).apply();nav.open("home",0);}));
  }
}
