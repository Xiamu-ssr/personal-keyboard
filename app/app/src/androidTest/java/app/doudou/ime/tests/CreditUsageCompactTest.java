package app.doudou.ime.tests;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.account.CreditUsageActivity;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.design.Ui;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Read-only live billing checks. Requires an existing QA login on an isolated emulator.
 * Run at the normal size and again at 360dp / fontScale 1.3; this class changes neither.
 * No account fixtures, model requests, settings changes, or shared keyboard test setup.
 */
public final class CreditUsageCompactTest {
  private Instrumentation ins;
  private UiAutomation ui;
  private CreditUsageActivity activity;
  private JSONObject tariff;

  @Before public void openExistingQaBill() throws Exception {
    assumeTrue("Use an isolated QA emulator", "ranchu".equals(Build.HARDWARE)
        || "goldfish".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    KeyboardApp app = (KeyboardApp)ins.getTargetContext().getApplicationContext();
    assumeTrue("Sign into the QA account before running; this test does not log in", app.cloud.loggedIn());
    ui = ins.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    activity = (CreditUsageActivity)ins.startActivitySync(
        new Intent(ins.getTargetContext(), CreditUsageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Field pricing = CreditUsageActivity.class.getDeclaredField("pricing"); pricing.setAccessible(true);
    long end = SystemClock.uptimeMillis() + 20000;
    do {
      tariff = onMain(() -> (JSONObject)pricing.get(activity));
      if (tariff != null && onMain(() -> text(activity.getWindow().getDecorView(), "用量示例")) != null) {
        ins.waitForIdleSync(); return;
      }
      if (onMain(() -> text(activity.getWindow().getDecorView(), "暂时无法加载账单")) != null)
        fail("The real bill failed to load; no fixture substitutes for it");
      SystemClock.sleep(80);
    } while (SystemClock.uptimeMillis() < end);
    fail("The live Cloud bill and its pricing did not load");
  }

  @After public void closeOwnActivity() {
    if (activity != null) ins.runOnMainSync(activity::finish);
  }

  @Test public void liveExamplesStayCompactWithoutClippingOrLosingSmallAmounts() throws Exception {
    onMain(() -> {
      ViewGroup group = examplesGroup();
      JSONArray examples = tariff.getJSONArray("examples");
      int creditLabels = 0;
      for (TextView label : labels(group)) {
        if (label.getText().toString().contains("Credit")) creditLabels++;
        assertFits(label);
      }
      assertEquals("One common unit instead of five repeated suffixes", 1, creditLabels);
      float fontScale = activity.getResources().getConfiguration().fontScale;
      assertTrue("The example group must leave room for recent usage",
          group.getHeight() <= Ui.dp(activity, 320 * Math.max(1, fontScale)));
      for (int i = 0; i < examples.length(); i++) {
        JSONObject example = examples.getJSONObject(i);
        ViewGroup row = exampleRow(example.getString("title"));
        assertTrue("Each example keeps its native touch target", row.getHeight() >= Ui.dp(activity, 48));
        assertTrue(row.isClickable()); assertTrue(row.isFocusable());
        List<TextView> values = labels(row);
        assertEquals(2, values.size());
        assertEquals(example.getString("title"), values.get(0).getText().toString());
        TextView price = values.get(1);
        assertEquals(Ui.appAccent(), price.getCurrentTextColor());
        double amount = example.getDouble("credits");
        String value = price.getText().toString();
        if (amount > 0 && amount < .1) assertEquals("<0.1", value);
        else {
          double shown = new DecimalFormat().parse(value).doubleValue();
          assertEquals("The compact value stays within half a tenth of the live example", amount, shown, .0500001);
          if (amount == 0) assertEquals(0, shown, 0);
          else assertTrue("A paid operation must not look free", shown > 0);
        }
        assertTrue("Accessible rows must keep the unit", row.getContentDescription().toString().contains("Credit"));
      }
      return null;
    });
    capture("qa-v096-credits-bill");
  }

  @Test public void eachLiveExampleOpensItsExactTokensAndMatchingRate() throws Exception {
    JSONArray examples = tariff.getJSONArray("examples");
    JSONObject definition = tariff.getJSONObject("definition");
    DecimalFormat f = new DecimalFormat("#,##0.###");
    for (int i = 0; i < examples.length(); i++) {
      JSONObject example = examples.getJSONObject(i);
      onMain(() -> { exampleRow(example.getString("title")).performClick(); return null; });
      String detail = awaitMessage("recording".equals(example.getString("id")) ? "保存、播放录音不扣积分。" : "本例 ");
      if ("recording".equals(example.getString("id"))) {
        assertTrue(detail.contains("实时转写按实际用量计分"));
      } else {
        assertTrue(detail.contains(f.format(example.getDouble("credits")) + " Credit"));
        assertTrue(detail.contains("输入 " + f.format(example.getLong("input"))));
        assertTrue(detail.contains("输出 " + f.format(example.getLong("output"))));
        assertTrue("The tokens must be labeled as examples", detail.contains("示例 Token"));
        assertTrue("The estimate must not imply a flat operation price", detail.contains("实际随内容与上下文变化"));
        String modelId = example.getString("model");
        JSONArray models = definition.getJSONArray("models");
        JSONObject rate = null;
        for (int m = 0; m < models.length(); m++)
          if (modelId.equals(models.getJSONObject(m).getString("id"))) rate = models.getJSONObject(m);
        assertNotNull("Each live example must identify its pricing source", rate);
        double markup = definition.getDouble("markupBasisPoints") / 10000;
        assertTrue(detail.contains("每千 Token：输入 "
            + f.format(rate.getDouble("inputMicroCnyPerMillion") * markup / 1000000)
            + " · 输出 " + f.format(rate.getDouble("outputMicroCnyPerMillion") * markup / 1000000) + " Credit"));
        assertTrue(detail.contains(modelId));
        assertTrue(detail.contains("计价 v" + tariff.getInt("version")));
      }
      AccessibilityNodeInfo done = node(ui.getRootInActiveWindow(), "完成", false);
      assertNotNull("Each detail can return to the bill", done);
      assertTrue(done.performAction(AccessibilityNodeInfo.ACTION_CLICK));
      ins.waitForIdleSync();
    }
    AccessibilityNodeInfo rates = node(ui.getRootInActiveWindow(), "计价", false);
    assertNotNull(rates); assertTrue(rates.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    awaitMessage("按实际输入、输出用量计分");
    capture("qa-v096-credits-pricing");
    AccessibilityNodeInfo done = node(ui.getRootInActiveWindow(), "完成", false);
    assertNotNull(done); assertTrue(done.performAction(AccessibilityNodeInfo.ACTION_CLICK));
  }

  private void capture(String name) throws Exception {
    ins.waitForIdleSync();
    android.graphics.Bitmap image = ui.takeScreenshot(); assertNotNull(image);
    try (java.io.FileOutputStream out = new java.io.FileOutputStream(
        new java.io.File(activity.getCacheDir(), name + ".png"))) {
      assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
    } finally { image.recycle(); }
  }

  private ViewGroup examplesGroup() {
    TextView heading = text(activity.getWindow().getDecorView(), "用量示例");
    assertNotNull(heading);
    return (ViewGroup)heading.getParent().getParent();
  }

  private ViewGroup exampleRow(String title) {
    TextView label = text(examplesGroup(), title); assertNotNull(label);
    return (ViewGroup)label.getParent();
  }

  private TextView text(View view, String value) {
    if (view instanceof TextView && value.contentEquals(((TextView)view).getText())) return (TextView)view;
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
      TextView found = text(((ViewGroup)view).getChildAt(i), value); if (found != null) return found;
    }
    return null;
  }

  private List<TextView> labels(View view) {
    List<TextView> result = new ArrayList<>();
    if (view instanceof TextView) result.add((TextView)view);
    if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++)
      result.addAll(labels(((ViewGroup)view).getChildAt(i)));
    return result;
  }

  private void assertFits(TextView label) {
    assertNotNull("Native text must be laid out", label.getLayout());
    int available = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
    for (int line = 0; line < label.getLineCount(); line++) {
      assertEquals("No example quantity or amount may be ellipsized", 0, label.getLayout().getEllipsisCount(line));
      assertTrue("No horizontal clipping: " + label.getText(), label.getLayout().getLineWidth(line) <= available + 1);
    }
    assertTrue("No vertical clipping: " + label.getText(), label.getLayout().getHeight()
        <= label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom() + 1);
  }

  private String awaitMessage(String prefix) {
    long end = SystemClock.uptimeMillis() + 5000;
    do {
      AccessibilityNodeInfo message = node(ui.getRootInActiveWindow(), prefix, true);
      if (message != null) return message.getText().toString();
      SystemClock.sleep(60);
    } while (SystemClock.uptimeMillis() < end);
    throw new AssertionError("Native example detail missing: " + prefix);
  }

  private AccessibilityNodeInfo node(AccessibilityNodeInfo root, String value, boolean prefix) {
    if (root == null) return null;
    String text = root.getText() == null ? "" : root.getText().toString();
    if (root.isVisibleToUser() && (prefix ? text.startsWith(value) : text.equals(value))) return root;
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo found = node(root.getChild(i), value, prefix); if (found != null) return found;
    }
    return null;
  }

  private <T> T onMain(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action); ins.runOnMainSync(task); return task.get();
  }
}
