package app.doudou.ime.memory;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class VocabularyProjectionTest {
  @Test public void oldWordsRemainManageableWithoutInfluencingRecognition() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="vocabulary-qa-"+UUID.randomUUID()+".db";
    MemoryStore store=new MemoryStore(c,name);
    try {
      JSONObject legacy=new JSONObject().put("id",UUID.randomUUID()).put("text","蓝鲸引擎").put("variants",new JSONArray());
      JSONObject manual=new JSONObject().put("id",UUID.randomUUID()).put("text","自定义叫法").put("variants",new JSONArray());
      JSONObject qualified=new JSONObject().put("id",UUID.randomUUID()).put("text","羲和引擎").put("variants",new JSONArray()).put("lexical",new JSONObject().put("kind","name").put("meaning","语音项目名称"));
      JSONObject memory=new JSONObject().put("hotwords",new JSONArray().put(legacy).put(manual).put(qualified)).put("styles",new JSONArray()).put("people",new JSONArray()).put("candidates",new JSONArray()).put("protected",new JSONArray().put("hotword:"+manual.getString("id")));
      store.cache("synthetic-v094",1,memory);
      assertEquals("Old terms remain accessible for edit/delete",3,store.list("word",0).size());
      assertFalse(store.frequencies().containsKey("蓝鲸引擎"));
      assertTrue(store.frequencies().containsKey("羲和引擎"));
      assertTrue(store.frequencies().containsKey("自定义叫法"));
    } finally { store.close(); c.deleteDatabase(name); }
  }
}
