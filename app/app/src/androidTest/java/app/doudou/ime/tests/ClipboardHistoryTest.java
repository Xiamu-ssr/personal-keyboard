package app.doudou.ime.tests;

import android.content.*;
import android.os.PersistableBundle;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.clipboard.ClipboardHistory;
import org.junit.*;
import static org.junit.Assert.*;

public class ClipboardHistoryTest {
  private Context context;private ClipboardHistory clips;
  @Before public void open(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("test-clips.db");clips=new ClipboardHistory(context,"test-clips.db");}
  @After public void close(){clips.close();context.deleteDatabase("test-clips.db");}
  @Test public void sensitiveAndNontextClipsAreNotRetained(){
    ClipData secret=ClipData.newPlainText("password","private fixture");
    PersistableBundle extras=new PersistableBundle();extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true);secret.getDescription().setExtras(extras);
    assertFalse(clips.capture(secret));
    assertFalse(clips.capture(ClipData.newRawUri("uri",android.net.Uri.parse("content://sample/1"))));
    assertTrue(clips.list().isEmpty());
  }
  @Test public void historyIsBoundedAndDeletedCurrentClipDoesNotReappear(){
    for(int i=0;i<40;i++)assertTrue(clips.capture(ClipData.newPlainText("sample","sample "+i)));
    assertEquals(30,clips.list().size());assertEquals("sample 39",clips.list().get(0).text);
    ClipData current=ClipData.newPlainText("sample","current");assertTrue(clips.capture(current));
    clips.remove(clips.list().get(0).id);assertFalse(clips.capture(current));
    java.util.List<ClipboardHistory.Clip> saved=clips.list();
    clips.clear();assertTrue(clips.list().isEmpty());assertFalse(clips.capture(current));
    clips.restore(saved);assertEquals(saved.size(),clips.list().size());
  }
}
