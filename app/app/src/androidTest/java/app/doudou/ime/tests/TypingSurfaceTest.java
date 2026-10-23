package app.doudou.ime.tests;

import static org.junit.Assert.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.PracticeActivity;
import org.junit.Test;

/** Uses only our practice field. No account, permission, private-app or fixture-data changes. */
public final class TypingSurfaceTest {
  private UiAutomation ui;
  private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text) {
    if(n==null)return null;
    if(n.isVisibleToUser() && (text.contentEquals(n.getText()==null?"":n.getText()) || text.contentEquals(n.getContentDescription()==null?"":n.getContentDescription())))return n;
    for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=find(n.getChild(i),text);if(f!=null)return f;}
    return null;
  }
  private AccessibilityNodeInfo node(String text) {
    for(AccessibilityWindowInfo w:ui.getWindows()) {
      AccessibilityNodeInfo root=w.getRoot();
      if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD && root!=null && "app.doudou.ime".contentEquals(root.getPackageName())) {
        AccessibilityNodeInfo f=find(root,text);if(f!=null)return f;
      }
    }
    return null;
  }
  private AccessibilityNodeInfo await(String text) {
    long until=SystemClock.uptimeMillis()+6000;
    while(SystemClock.uptimeMillis()<until){AccessibilityNodeInfo n=node(text);if(n!=null)return n;SystemClock.sleep(60);}
    throw new AssertionError("Own IME control missing: "+text);
  }
  private void click(String text){assertTrue(text,await(text).performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(60);}
  private EditText field(View v) {
    if(v instanceof EditText)return (EditText)v;
    if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=field(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}
    return null;
  }
  @Test public void decimalEmailAndUrlCanBeTypedWithVisibleKeys() throws Exception {
    Instrumentation ins=InstrumentationRegistry.getInstrumentation();
    ui=ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);
    Activity activity=ins.startActivitySync(new Intent(ins.getTargetContext(),PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      EditText edit=field(activity.getWindow().getDecorView());assertNotNull(edit);
      ins.runOnMainSync(()->{edit.setText("");edit.requestFocus();((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);});
      await("工具");
      boolean originalEnglish=node("EN")!=null;
      if(node("中")!=null)click("中");
      String expected="3.14 a_b@example.com https://a.cn/x";
      for(char c:expected.toCharArray()) {
        if(c==' '){click("空格");continue;}
        boolean letter=c>='a'&&c<='z';
        if(letter && node("ABC")!=null)click("ABC");
        if(!letter && node(String.valueOf(c))==null && node("123")!=null)click("123");
        click(String.valueOf(c));
      }
      ins.runOnMainSync(()->assertEquals(expected,edit.getText().toString()));
      if(node("ABC")!=null)click("ABC");
      if(!originalEnglish) {
        click("EN");click("。");
        ins.runOnMainSync(()->assertEquals(expected+"。",edit.getText().toString()));
      }
    } finally {ins.runOnMainSync(activity::finish);}
  }
}
