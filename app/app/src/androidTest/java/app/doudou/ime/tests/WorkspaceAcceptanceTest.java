package app.doudou.ime.tests;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.*;
import app.doudou.ime.recording.*;
import app.doudou.ime.capture.CaptureService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.*;
import org.junit.*;

/** Explicit live acceptance on the dedicated QA account and our disposable practice screen only.
 * Does not reset data, log in/out, change permissions, or send to a real chat/contact. */
public final class WorkspaceAcceptanceTest {
  private Instrumentation ins;
  private UiAutomation ui;
  private KeyboardApp app;
  private Activity activity;
  private Activity bill;
  private EditText editor;
  private String account, ownedRecording = "";
  private int accessibilityFlags;

  @Before public void ownPracticeOnly() throws Exception {
    assumeTrue("Explicit device and paid-model acceptance", "true".equals(InstrumentationRegistry.getArguments().getString("deviceAcceptance"))
        && "true".equals(InstrumentationRegistry.getArguments().getString("liveModels")));
    ins = InstrumentationRegistry.getInstrumentation();
    app = (KeyboardApp)ins.getTargetContext().getApplicationContext();
    JSONObject fixture = new JSONObject(new String(Files.readAllBytes(new File(app.getCacheDir(), "product-qa.json").toPath()), StandardCharsets.UTF_8));
    assertTrue("Keep the pre-existing dedicated account logged in", app.cloud.loggedIn());
    assertEquals("Dedicated ordinary QA account only", fixture.getString("handle"), app.cloud.handle());
    account = app.cloud.userId();
    assertNull("Do not disturb another recording", RecordingService.current);
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    android.accessibilityservice.AccessibilityServiceInfo flags = ui.getServiceInfo();
    accessibilityFlags = flags.flags; flags.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    ui.setServiceInfo(flags);
    activity = ins.startActivitySync(new Intent(app, PracticeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    editor = field(activity.getWindow().getDecorView()); assertNotNull(editor);
    onMain(() -> { editor.setText("明天下午三点见，记得带上豆豆的测试手机。"); editor.setSelection(editor.length()); editor.requestFocus(); });
    long ready = SystemClock.uptimeMillis() + 10000;
    while (node("更多") == null && SystemClock.uptimeMillis() < ready) { focus(); SystemClock.sleep(100); }
    await("更多", 1000);
  }

  @After public void finishOnlyOwnedWork() throws Exception {
    if (!ownedRecording.isEmpty() && RecordingService.current != null && ownedRecording.equals(RecordingService.current.id()))
      onMain(() -> RecordingService.current.finish());
    if (activity != null) onMain(activity::finish);
    if (bill != null) onMain(bill::finish);
    if (ui != null) {
      android.accessibilityservice.AccessibilityServiceInfo flags = ui.getServiceInfo(); flags.flags = accessibilityFlags; ui.setServiceInfo(flags);
    }
    if (account != null) { assertEquals(account, app.cloud.userId()); assertTrue(app.cloud.loggedIn()); }
  }

  @Test public void realTranslationIsExplicitEditableAndNeverFillsUntilAsked() throws Exception {
    String baseline = value();
    click("翻译"); await("翻译原文", 5000);
    // Exercise the real local editor even if this QA account already has a translation draft.
    click("翻译原文"); await("修改后返回", 5000);
    AccessibilityNodeInfo local = imeEditor();
    Bundle text = new Bundle(); text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, baseline);
    assertTrue(local.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
    click("返回"); await("开始翻译", 5000);
    // Choose a known target through the actual menu; keep automatic source detection.
    click("原文语言"); click("自动识别");
    click("目标语言"); click("英语");
    click("开始翻译");
    AccessibilityNodeInfo translated = await("译文", 90000);
    String result = translated.getText().toString(); assertFalse(result.isEmpty()); assertFalse(result.equals(baseline));
    assertEquals("No automatic editor replacement", baseline, value());
    assertTrue("Replace becomes available only for a complete result", await("替换原文", 3000).isEnabled());
    shot("translation-result");
    click("输入"); await("空格", 5000);
    assertEquals("Switching workspaces preserves the host draft", baseline, value());
    click("翻译"); assertEquals(result, await("译文", 5000).getText().toString());
    click("替换原文"); await("空格", 5000);
    assertEquals("Explicit translation replaces its original draft instead of duplicating it", result, value());
    shot("translation-filled");
  }

  @Test public void recordingContinuesAcrossTranslationInputAndKeyboardHide() throws Exception {
    String baseline = value();
    click("录音");
    long deadline = SystemClock.uptimeMillis() + 8000;
    while (RecordingService.current == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(80);
    assertNotNull(RecordingService.current); ownedRecording = RecordingService.current.id();
    while (RecordingService.current.bytes() < 16000 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
    long first = RecordingService.current.bytes(); assertTrue("Audio persisted before tools are switched", first > 0);
    click("翻译"); await("开始翻译", 5000); SystemClock.sleep(600);
    assertEquals("Recording remains visible while working elsewhere", "正在录音", await("录音", 3000).getStateDescription());
    assertEquals(ownedRecording, RecordingService.current.id()); assertTrue(RecordingService.current.active());
    assertTrue("Microphone continues in the other workspace", RecordingService.current.bytes() > first);
    assertEquals(baseline, value());
    click("输入"); await("空格", 5000); click("收起键盘");
    long hidden = RecordingService.current.bytes(); SystemClock.sleep(600);
    assertTrue(RecordingService.current.bytes() > hidden); assertEquals(baseline, value());
    focus(); await("录音", 5000); click("录音"); await("结束", 5000);
    shot("recorder-live"); click("结束");
    deadline = SystemClock.uptimeMillis() + 25000;
    while (RecordingService.current != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
    assertNull("Finish closes only this recording", RecordingService.current);
    RecordingStore saved = new RecordingStore(app, ownedRecording);
    JSONObject snapshot = saved.snapshot();
    assertTrue(snapshot.getJSONArray("chunks").length() > 0); assertFalse(snapshot.optBoolean("active", true));
    assertTrue("Recorded audio remains available after service stop", saved.audio(0).length() > 0);
    assertEquals(baseline, value()); await("新录音", 5000); shot("recorder-saved");
    String duration=await("录音时长",3000).getText().toString(); assertNotEquals("00:00",duration);
    click("翻译"); await("翻译原文",5000); click("录音"); await("新录音",5000);
    assertEquals("Saved recording duration survives workspace navigation",duration,await("录音时长",3000).getText().toString());
  }

  @Test public void emojiPickerCommitsWholeEmojiAndDeleteRemovesOneCluster() throws Exception {
    String before = value(); shot("typing"); click("更多"); shot("tools"); click("表情");
    await("ABC", 5000); SystemClock.sleep(1600); shot("emoji-picker");
    // The official picker exposes each cell as a clickable image with the Unicode sequence.
    AccessibilityNodeInfo emoji = emojiNode(); assertNotNull("Real emoji cells in the native picker", emoji);
    String sequence = emoji.getContentDescription().toString();
    assertTrue(emoji.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    SystemClock.sleep(200); String inserted = value();
    assertTrue("One complete emoji sequence was committed", inserted.startsWith(before) && inserted.length() > before.length());
    click("退格"); assertEquals("Backspace removes the whole selected sequence", before, value());
    click("ABC"); await("空格", 5000);
    // Owned text fixtures test complete-cluster deletion through the real IME, beyond one Picker cell.
    for (String cluster : new String[] {"👨‍👩‍👧‍👦", "👩🏽‍💻", "🇨🇳", "1️⃣", "e\u0301"}) {
      onMain(() -> { editor.setText(before + cluster); editor.setSelection(editor.length()); });
      ui.waitForIdle(100,5000); ins.waitForIdleSync(); await("空格",5000);
      click("退格"); assertEquals("Delete one visible cluster: " + cluster, before, value());
    }
    await("空格", 5000);
  }

  @Test public void holdSpaceKeepsTheVoiceSurfaceAndCancelPreservesDraft() throws Exception {
    String before = value();
    android.graphics.Rect space = new android.graphics.Rect(); await("空格", 5000).getBoundsInScreen(space);
    long time = SystemClock.uptimeMillis(); float x = space.exactCenterX(), y = space.exactCenterY();
    android.view.MotionEvent down = android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,x,y,0);
    assertTrue(ui.injectInputEvent(down,true)); down.recycle();
    try {
      await("撤回输入",5000); await("松手结束",5000);
      assertNull("Voice replaces the letter keys while the finger is held",node("q"));
      SystemClock.sleep(250); shot("voice-held");
    }
    finally {
      android.view.MotionEvent cancel = android.view.MotionEvent.obtain(time,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_CANCEL,x,y,0);
      assertTrue(ui.injectInputEvent(cancel,true)); cancel.recycle();
    }
    await("空格",5000); assertEquals("Cancelled voice input preserves the complete host draft",before,value());
  }

  @Test public void advisorRemainsARealWorkspaceAndKeepsRepliesAcrossNavigation() throws Exception {
    long bound=SystemClock.uptimeMillis()+10000;
    while(CaptureService.current==null&&SystemClock.uptimeMillis()<bound)SystemClock.sleep(100);
    assertNotNull("Owned-device screenshot service was provisioned outside this test", CaptureService.current);
    String before = value(); click("帮我回");
    await("翻译",20000); click("翻译"); await("翻译原文",5000);
    SystemClock.sleep(12000);
    assertNotNull("A late advisor callback must not replace the translation workspace",node("翻译原文"));
    click("帮我回");
    long ready=SystemClock.uptimeMillis()+90000; AccessibilityNodeInfo answer;
    while((answer=reply())==null&&SystemClock.uptimeMillis()<ready) {
      if (node("重试") != null || node("重新截图") != null) break;
      SystemClock.sleep(120);
    }
    if(answer==null) {
      shot("advisor-failure"); StringBuilder labels=new StringBuilder();
      for(AccessibilityWindowInfo window:ui.getWindows()) {
        AccessibilityNodeInfo n=window.getRoot();
        if(n!=null&&app.getPackageName().contentEquals(n.getPackageName()))labels(n,labels);
      }
      Files.write(new File(app.getCacheDir(),"qa-v091-advisor-failure.txt").toPath(),labels.toString().getBytes(StandardCharsets.UTF_8));
    }
    assertNotNull("A real screenshot request returns selectable replies",answer);
    String first=answer.getContentDescription()+":"+replyText(answer);
    assertEquals("Opening advisor does not insert a reply",before,value()); shot("advisor");
    click("输入"); await("空格",5000); click("帮我回");
    assertEquals("Returning to advisor retains the same first answer",first,reply().getContentDescription()+":"+replyText(reply()));
    assertTrue("Advisor is selected in the same navigation",await("帮我回",3000).isSelected());
    click("输入"); await("空格",5000); assertEquals(before,value());
  }

  @Test public void creditBillAndPublishedRatesReachTheNativeAccountScreen() throws Exception {
    bill = ins.startActivitySync(new Intent(app, app.doudou.ime.account.CreditUsageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    await("积分账单", 5000);
    long ready = SystemClock.uptimeMillis() + 15000;
    while (!await("计价", 3000).isEnabled() && SystemClock.uptimeMillis() < ready) SystemClock.sleep(100);
    assertTrue("Both bill and published tariff loaded", await("计价", 3000).isEnabled());
    shot("credits-bill");
    AccessibilityNodeInfo line = billLine(); assertNotNull("A real successful job can be inspected", line);
    assertTrue(line.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    await("实际用量", 5000); await("复制账单号", 3000); shot("credits-detail"); click("完成");
    click("计价");
    await("积分计价", 5000); await("按实际用量计分 · 1 Credit = 0.001 元", 3000);
    shot("credits-pricing"); click("完成");
  }

  private void onMain(Runnable task) { ins.runOnMainSync(task); ins.waitForIdleSync(); }
  private void focus() { onMain(() -> { editor.requestFocus(); ((InputMethodManager)app.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT); }); SystemClock.sleep(180); }
  private String value() { String[] s={""}; onMain(() -> s[0]=editor.getText().toString()); return s[0]; }
  private EditText field(View v) {
    if (v instanceof EditText) return (EditText)v;
    if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) { EditText e=field(((ViewGroup)v).getChildAt(i)); if(e!=null)return e; }
    return null;
  }
  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
    if(n==null)return null;
    if(n.isVisibleToUser() && (label.contentEquals(n.getText()==null?"":n.getText()) || label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription())))return n;
    for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=find(n.getChild(i),label);if(v!=null)return v;} return null;
  }
  private AccessibilityNodeInfo node(String label) {
    for(AccessibilityWindowInfo w:ui.getWindows()){ AccessibilityNodeInfo r=w.getRoot();if(r!=null && app.getPackageName().contentEquals(r.getPackageName())){AccessibilityNodeInfo n=find(r,label);if(n!=null)return n;} }return null;
  }
  private AccessibilityNodeInfo await(String label,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo n=node(label);if(n!=null)return n;SystemClock.sleep(80);}throw new AssertionError("Missing native control: "+label);}
  private void click(String label){long end=SystemClock.uptimeMillis()+8000;boolean done=false;do{AccessibilityNodeInfo n=await(label,3000);while(n!=null&&!n.isClickable())n=n.getParent();assertNotNull(label,n);done=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(!done)SystemClock.sleep(80);}while(!done&&SystemClock.uptimeMillis()<end);assertTrue(label,done);SystemClock.sleep(150);}
  private AccessibilityNodeInfo imeEditor(){for(AccessibilityWindowInfo w:ui.getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){AccessibilityNodeInfo e=editable(w.getRoot());if(e!=null)return e;}throw new AssertionError("Local keyboard editor");}
  private AccessibilityNodeInfo editable(AccessibilityNodeInfo n){if(n==null)return null;if(n.isEditable()&&n.isVisibleToUser())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo e=editable(n.getChild(i));if(e!=null)return e;}return null;}
  private AccessibilityNodeInfo emojiNode(){for(AccessibilityWindowInfo w:ui.getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){AccessibilityNodeInfo n=emoji(w.getRoot());if(n!=null)return n;}return null;}
  private AccessibilityNodeInfo billLine(){for(AccessibilityWindowInfo w:ui.getWindows()){AccessibilityNodeInfo n=billLine(w.getRoot());if(n!=null)return n;}return null;}
  private AccessibilityNodeInfo reply(){for(AccessibilityWindowInfo w:ui.getWindows())if(w.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){AccessibilityNodeInfo n=reply(w.getRoot());if(n!=null)return n;}return null;}
  private AccessibilityNodeInfo reply(AccessibilityNodeInfo n){if(n==null)return null;CharSequence d=n.getContentDescription();if(n.isVisibleToUser()&&n.isClickable()&&d!=null&&d.toString().endsWith("回复"))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=reply(n.getChild(i));if(v!=null)return v;}return null;}
  private String replyText(AccessibilityNodeInfo n){String value=n.getText()==null?"":n.getText().toString();for(int i=0;i<n.getChildCount();i++){String text=replyText(n.getChild(i));if(!text.isEmpty())value=text;}return value;}
  private void labels(AccessibilityNodeInfo n,StringBuilder out){if(n==null)return;if(n.isVisibleToUser()&&n.getText()!=null)out.append(n.getText()).append('\n');for(int i=0;i<n.getChildCount();i++)labels(n.getChild(i),out);}
  private AccessibilityNodeInfo billLine(AccessibilityNodeInfo n){if(n==null)return null;CharSequence d=n.getContentDescription();if(n.isVisibleToUser()&&n.isClickable()&&d!=null&&d.toString().startsWith("消费明细："))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=billLine(n.getChild(i));if(v!=null)return v;}return null;}
  private AccessibilityNodeInfo emoji(AccessibilityNodeInfo n){
    if(n==null)return null;CharSequence d=n.getContentDescription();
    if(n.isVisibleToUser()&&n.isClickable()&&d!=null && d.toString().codePoints().anyMatch(p->p>=0x1f300&&p<=0x1faff))return n;
    for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo e=emoji(n.getChild(i));if(e!=null)return e;}return null;
  }
  private void shot(String name)throws Exception{SystemClock.sleep(240);Bitmap b=ui.takeScreenshot();assertNotNull(b);try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),"qa-v091-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
