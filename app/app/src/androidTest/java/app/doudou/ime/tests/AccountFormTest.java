package app.doudou.ime.tests;
import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import org.junit.Test;
import java.util.*;

public final class AccountFormTest {
  private void fields(View v,List<EditText> out) {
    if(v instanceof EditText) out.add((EditText)v);
    if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) fields(((ViewGroup)v).getChildAt(i),out);
  }
  private View text(View v,String label) {
    if(v instanceof TextView && label.contentEquals(((TextView)v).getText())) return v;
    if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {View n=text(((ViewGroup)v).getChildAt(i),label);if(n!=null)return n;}
    return null;
  }
  @Test public void rejectedRegistrationKeepsInMemoryFormForCorrection() throws Exception {
    assertEquals("ranchu",Build.HARDWARE);
    Instrumentation ins=InstrumentationRegistry.getInstrumentation();
    MainActivity a=(MainActivity)ins.startActivitySync(new Intent(ins.getTargetContext(),MainActivity.class).putExtra("page","account").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      ins.runOnMainSync(() -> text(a.getWindow().getDecorView(),"受邀注册").performClick());
      final List<EditText> f=new ArrayList<>();
      ins.runOnMainSync(() -> {
        fields(a.getWindow().getDecorView(),f);assertEquals(4,f.size());
        String[] values={"qa_form_fixture","Only-a-test-password-2026","Only-a-test-password-2026","invalid-fixture-invitation"};
        for(int i=0;i<4;i++)f.get(i).setText(values[i]);
        text(a.getWindow().getDecorView(),"创建账户").performClick();
      });
      long until=SystemClock.uptimeMillis()+15000;
      while(SystemClock.uptimeMillis()<until) {
        final boolean[] done={false};
        ins.runOnMainSync(() -> done[0]=text(a.getWindow().getDecorView(),"请输入有效的邀请码")!=null);
        if(done[0])break;SystemClock.sleep(80);
      }
      ins.runOnMainSync(() -> {
        assertNotNull(text(a.getWindow().getDecorView(),"请输入有效的邀请码"));
        List<EditText> retained=new ArrayList<>();fields(a.getWindow().getDecorView(),retained);
        assertEquals("qa_form_fixture",retained.get(0).getText().toString());
        assertEquals("Only-a-test-password-2026",retained.get(1).getText().toString());
        assertEquals("Only-a-test-password-2026",retained.get(2).getText().toString());
        assertEquals("invalid-fixture-invitation",retained.get(3).getText().toString());
      });
    } finally {ins.runOnMainSync(a::finish);}
  }
}
