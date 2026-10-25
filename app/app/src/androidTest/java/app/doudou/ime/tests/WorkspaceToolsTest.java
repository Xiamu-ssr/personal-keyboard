package app.doudou.ime.tests;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.doudou.ime.app.KeyboardApp;
import app.doudou.ime.app.PracticeActivity;
import app.doudou.ime.cloud.CloudClient;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.KeyboardView;
import app.doudou.ime.input.WorkspaceBar;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.translation.TranslationController;
import app.doudou.ime.translation.TranslationDraft;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Offline native-component regressions, restricted to an already isolated ranchu emulator.
 * Uses a UUID-owned cache database and an unauthenticated synthetic identity. Selection and label
 * bounds use an owned practice window after pre-draw; controller fixtures remain detached.
 * Never starts a microphone, model request, login/logout, or app-data reset. These checks do not
 * establish IME insets, physical touch dispatch, real InputConnections, translation quality, or
 * visual acceptance.
 */
@RunWith(AndroidJUnit4.class)
public final class WorkspaceToolsTest {
  private Instrumentation ins;
  private Context fixtureContext;
  private KeyboardApp services;
  private File fixtureDirectory, databaseFile;
  private boolean ownsDirectory;
  private Activity activity;

  @Before
  public void isolatedLocalServices() throws Exception {
    assumeTrue("Isolated ranchu emulator only; never a physical device",
        "ranchu".equals(Build.HARDWARE));
    ins = InstrumentationRegistry.getInstrumentation();
    Context target = ins.getTargetContext();
    String owner = "workspace-tools-" + UUID.randomUUID();
    // Instrumentation runs as the target UID, not the test APK's UID. Its APK context is not a
    // writable data sandbox. Use only this new owned directory, never the normal app database.
    fixtureDirectory = new File(target.getCacheDir(), owner);
    ownsDirectory = fixtureDirectory.mkdir();
    assertTrue("Create this test's private directory", ownsDirectory);
    databaseFile = new File(fixtureDirectory, "draft.db");
    assertEquals(target.getCacheDir().getCanonicalFile(),
        fixtureDirectory.getCanonicalFile().getParentFile());

    fixtureContext = new ContextWrapper(target) {
      @Override public Context getApplicationContext() { return this; }
      @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return target.getSharedPreferences(owner + "-" + name, mode);
      }
    };
    // Instantiation attaches the fixture context; deliberately do not call KeyboardApp.onCreate().
    services = (KeyboardApp) Instrumentation.newApplication(KeyboardApp.class, fixtureContext);
    services.worker.shutdownNow();
    services.network.shutdownNow(); // An accidental translation submission must fail locally.
    services.cloud = new CloudClient(fixtureContext);
    services.store = new MemoryStore(fixtureContext, databaseFile.getAbsolutePath());
    assertEquals(databaseFile.getCanonicalPath(),
        new File(services.store.getWritableDatabase().getPath()).getCanonicalPath());
    Field session = CloudClient.class.getDeclaredField("session");
    session.setAccessible(true);
    session.set(services.cloud,
        new JSONObject().put("user", new JSONObject().put("id", owner)));
    assertFalse("Synthetic identity has no login or tokens", services.cloud.loggedIn());
  }

  @After
  public void closeOnlyOwnedFixtures() {
    if (activity != null) ins.runOnMainSync(activity::finishAndRemoveTask);
    if (services != null) {
      if (services.store != null) services.store.close();
      if (services.cloud != null) {
        services.cloud.http.connectionPool().evictAll();
        services.cloud.http.dispatcher().executorService().shutdownNow();
      }
      services.worker.shutdownNow();
      services.network.shutdownNow();
    }
    // Only files under this UUID directory; never clear the app's data or other test fixtures.
    if (ownsDirectory && databaseFile != null) SQLiteDatabase.deleteDatabase(databaseFile);
    if (ownsDirectory && fixtureDirectory.exists())
      assertTrue("Remove only this test's empty directory", fixtureDirectory.delete());
  }

  @Test
  public void eachModeHasOneRealSelectionAndRoutesOnlyItsOwnCallbacks() throws Exception {
    assertArrayEquals("Four peer modes keep the requested order",
        new WorkspaceBar.Mode[] {WorkspaceBar.Mode.INPUT, WorkspaceBar.Mode.RECORDING,
            WorkspaceBar.Mode.ADVISOR, WorkspaceBar.Mode.TRANSLATION}, WorkspaceBar.Mode.values());
    openWindow();
    for (WorkspaceBar.Mode selected : WorkspaceBar.Mode.values()) {
      Routes calls = new Routes();
      WorkspaceBar bar = attachBar(1f, selected, calls);
      ins.runOnMainSync(() -> {
        assertTrue("Read semantics from an attached view", bar.isAttachedToWindow());
        namedTabsAndSelectedIcon(bar, selected);
        for (WorkspaceBar.Mode mode : WorkspaceBar.Mode.values()) {
          View tab = require(bar, label(mode));
          assertEquals(mode == selected, tab.isSelected());
          assertTrue(tab.isEnabled());
          AccessibilityNodeInfo info = tab.createAccessibilityNodeInfo();
          try {
            assertTrue("The node is visible in the owned window", info.isVisibleToUser());
            assertTrue(info.isCheckable());
            assertEquals(mode == selected, info.isChecked());
            assertEquals(mode == selected, info.isSelected());
            assertEquals("android.widget.RadioButton", info.getClassName().toString());
          } finally { info.recycle(); }

          calls.selected.clear();
          assertTrue(tab.performClick());
          boolean routes = mode != selected || mode == WorkspaceBar.Mode.RECORDING;
          assertEquals(routes ? 1 : 0, calls.selected.size());
          if (routes) assertEquals(mode, calls.selected.get(0));
          assertEquals("Only rebuilding with a different mode changes selection",
              mode == selected, tab.isSelected());
        }
        int selections = calls.selected.size();
        require(bar, "更多").performClick();
        require(bar, "收起键盘").performClick();
        assertEquals(1, calls.tools);
        assertEquals(1, calls.hide);
        assertEquals("Tools/hide must not select another workspace", selections, calls.selected.size());
      });
    }
  }

  @Test
  public void privateModeDisablesRecordingTranslationAndAdvisor() {
    ins.runOnMainSync(() -> {
      Routes calls = new Routes();
      WorkspaceBar bar = new WorkspaceBar(uiContext(1f), WorkspaceBar.Mode.INPUT, true, calls);
      measure(bar, 360, 48);
      assertTrue(require(bar, "输入").isEnabled());
      assertTrue(require(bar, "输入").isSelected());
      for (String name : new String[] {"录音", "翻译", "帮我回"}) {
        View disabled = require(bar, name);
        assertFalse(name, disabled.isEnabled());
        // performClick bypasses enabled-state dispatch; use native touch handling for disabled views.
        touchDisabled(disabled);
      }
      assertTrue(calls.selected.isEmpty());
      assertEquals(0, calls.tools);
      assertEquals(0, calls.hide);
    });
  }

  @Test
  public void labelsAndTouchTargetsFit360dpAtNormalAndLargeFont() throws Exception {
    openWindow();
    float[][] normalSizes = new float[WorkspaceBar.Mode.values().length][WorkspaceBar.Mode.values().length];
    for (float scale : new float[] {1f, 1.3f}) {
      for (WorkspaceBar.Mode mode : WorkspaceBar.Mode.values()) {
        WorkspaceBar bar = attachBar(scale, mode, new Routes());
        ins.runOnMainSync(() -> {
          Context context = bar.getContext();
          assertEquals(scale, context.getResources().getConfiguration().fontScale, .001f);
          assertEquals(Ui.dp(context, 360), bar.getWidth());
          assertEquals(Ui.dp(context, 48), bar.getHeight());
          Rect visibleBar = new Rect();
          assertTrue("Fixture toolbar is visible: " + geometry(bar),
              bar.getGlobalVisibleRect(visibleBar));
          assertEquals("Fixture window preserves the full toolbar width: " + geometry(bar),
              bar.getWidth(), visibleBar.width());
          assertEquals("Fixture window preserves the full toolbar height: " + geometry(bar),
              bar.getHeight(), visibleBar.height());
          List<TextView> labels = namedTabsAndSelectedIcon(bar, mode);
          for (int i = 0; i < labels.size(); i++) {
            TextView text = labels.get(i);
            if (scale == 1f) normalSizes[mode.ordinal()][i] = text.getTextSize();
            else assertTrue("Large-font resources really increase every peer label: " + text.getText(),
                text.getTextSize() > normalSizes[mode.ordinal()][i]);
          }

          List<View> targets = new ArrayList<>();
          collectTargets(bar, targets);
          assertEquals("Four peer tabs plus tools and hide", 6, targets.size());
          List<Rect> rectangles = new ArrayList<>();
          for (View target : targets) {
            Rect visible = new Rect();
            String details = "mode=" + mode + " fontScale=" + scale + " " + geometry(target);
            assertTrue("Target is actually visible: " + details, target.getGlobalVisibleRect(visible));
            assertEquals("Target width is not clipped by ancestors: " + details,
                target.getWidth(), visible.width());
            assertEquals("Target height is not clipped by ancestors: " + details,
                target.getHeight(), visible.height());
            Rect bounds = inRoot(bar, target);
            assertTrue("Touch target width: " + target.getContentDescription(),
                bounds.width() >= Ui.dp(context, 48));
            assertTrue("Touch target height: " + target.getContentDescription(),
                bounds.height() >= Ui.dp(context, 48));
            assertTrue("Target remains inside toolbar",
                new Rect(0, 0, bar.getWidth(), bar.getHeight()).contains(bounds));
            for (Rect previous : rectangles)
              assertFalse("Adjacent touch targets must not overlap", Rect.intersects(previous, bounds));
            rectangles.add(bounds);
            assertRenderedContentsFit(target);
          }
        });
      }
    }
  }

  @Test
  public void translationOpensAVisibleLanguageGridWithoutAnotherEditorOrFillStep() {
    ins.runOnMainSync(() -> {
      Host fixture = new Host(uiContext(1.3f));
      new TranslationController(fixture.proxy).show(null);
      measure(fixture.panel, 360, KeyboardView.HEIGHT_DP);
      for (String name : new String[] {"中文", "英语", "日语", "韩语", "法语", "德语", "西班牙语", "俄语", "葡萄牙语"})
        assertTrue(require(fixture.panel, "翻译成" + name).isEnabled());
      assertFalse(require(fixture.panel, "还原原文").isEnabled());
      assertNull(find(fixture.panel, "翻译原文"));
      assertNull(find(fixture.panel, "开始翻译"));
      assertNull(find(fixture.panel, "填入"));
      assertTrue(fixture.inserted.isEmpty());
      assertEquals("宿主聊天草稿", fixture.editor);
    });
  }

  @Test
  public void eachRecordingImportUsesThatRecordingWithoutWritingIntoChat() {
    ins.runOnMainSync(() -> {
      Host fixture = new Host(uiContext(1f));
      TranslationController controller = new TranslationController(fixture.proxy);
      controller.show("录音原文 A");
      assertText(fixture.panel, "录音译文", "录音原文 A");
      controller.show("录音原文 B");
      assertText(fixture.panel, "录音译文", "录音原文 B");
      assertTrue(fixture.inserted.isEmpty());
      assertEquals("宿主聊天草稿", fixture.editor);
      controller.show(null);
      assertNull(find(fixture.panel, "录音译文"));
      assertFalse(require(fixture.panel, "还原原文").isEnabled());
    });
  }

  @Test
  public void recordingTranslationCannotEraseTheChatOriginalOrCachedLanguages() throws Exception {
    ins.runOnMainSync(() -> {
      try {
        Host fixture = new Host(uiContext(1f));
        TranslationController controller = new TranslationController(fixture.proxy);
        controller.show(null);
        Field field = TranslationController.class.getDeclaredField("draft");
        field.setAccessible(true);
        TranslationDraft chat = (TranslationDraft) field.get(controller);
        chat.remember(chat.revision(), "en", "Chat translation"); chat.display("en");
        fixture.editor = "Chat translation";
        controller.show("录音原文");
        assertNotSame(chat, field.get(controller));
        controller.show(null);
        assertSame(chat, field.get(controller));
        assertEquals("宿主聊天草稿", chat.original());
        assertEquals("Chat translation", chat.cached("en"));
        require(fixture.panel, "还原原文").performClick();
        assertEquals("宿主聊天草稿", fixture.editor);
      } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    });
  }

  private void openWindow() {
    activity = ins.startActivitySync(new Intent(ins.getTargetContext(), PracticeActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    ins.runOnMainSync(() -> {
      activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      activity.getWindow().setDecorFitsSystemWindows(true);
      // Detach the practice editor before its delayed showSoftInput callback; it has no window token.
      activity.setContentView(new FrameLayout(activity));
    });
  }

  private WorkspaceBar attachBar(float scale, WorkspaceBar.Mode selected, Routes calls)
      throws InterruptedException {
    WorkspaceBar[] result = new WorkspaceBar[1];
    CountDownLatch drawn = new CountDownLatch(1);
    ins.runOnMainSync(() -> {
      Context context = uiContext(scale);
      WorkspaceBar bar = new WorkspaceBar(context, selected, false, calls);
      result[0] = bar;
      FrameLayout root = new FrameLayout(activity);
      root.addView(bar, new FrameLayout.LayoutParams(Ui.dp(context, 360), Ui.dp(context, 48), Gravity.CENTER));
      root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override public boolean onPreDraw() {
          root.getViewTreeObserver().removeOnPreDrawListener(this);
          // All TextView pre-draw listeners must finish their single-line scroll positioning first.
          root.post(drawn::countDown);
          return true;
        }
      });
      activity.setContentView(root);
    });
    assertTrue("The owned native window reaches pre-draw", drawn.await(5, TimeUnit.SECONDS));
    ins.waitForIdleSync();
    return result[0];
  }

  private Context uiContext(float scale) {
    Context target = ins.getTargetContext();
    Configuration configuration = new Configuration(target.getResources().getConfiguration());
    configuration.fontScale = scale;
    configuration.screenWidthDp = 360;
    return new ContextThemeWrapper(target.createConfigurationContext(configuration),
        android.R.style.Theme_Material_Light_NoActionBar);
  }

  private static String label(WorkspaceBar.Mode mode) {
    switch (mode) {
      case INPUT: return "输入";
      case RECORDING: return "录音";
      case ADVISOR: return "帮我回";
      case TRANSLATION: return "翻译";
      default: throw new AssertionError("Missing mode label: " + mode);
    }
  }

  private static List<TextView> namedTabsAndSelectedIcon(WorkspaceBar bar, WorkspaceBar.Mode selected) {
    List<TextView> peerLabels = new ArrayList<>();
    for (WorkspaceBar.Mode mode : WorkspaceBar.Mode.values()) {
      View tab = require(bar, label(mode));
      assertEquals("Only the selected peer tab keeps a visible icon: " + mode,
          mode == selected, hasVisibleIcon(tab));
      List<TextView> labels = new ArrayList<>();
      collectVisibleLabels(tab, labels);
      assertEquals("Every peer tab always exposes one visible label: " + mode, 1, labels.size());
      TextView text = labels.get(0);
      assertEquals(label(mode), text.getText().toString());
      assertReadable(text);
      peerLabels.add(text);
    }
    assertEquals("All four workspace names remain visible", 4, peerLabels.size());
    return peerLabels;
  }

  private static void collectVisibleLabels(View view, List<TextView> labels) {
    if (view.getVisibility() != View.VISIBLE) return;
    if (view instanceof TextView && ((TextView) view).getText().length() > 0)
      labels.add((TextView) view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) collectVisibleLabels(group.getChildAt(i), labels);
    }
  }

  private static boolean hasVisibleIcon(View view) {
    if (view.getVisibility() != View.VISIBLE) return false;
    if (view instanceof ImageView) {
      Drawable icon = ((ImageView) view).getDrawable();
      if (icon != null && !icon.getBounds().isEmpty()) return true;
    }
    if (view instanceof TextView) {
      for (Drawable icon : ((TextView) view).getCompoundDrawables())
        if (icon != null && !icon.getBounds().isEmpty()) return true;
      for (Drawable icon : ((TextView) view).getCompoundDrawablesRelative())
        if (icon != null && !icon.getBounds().isEmpty()) return true;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (hasVisibleIcon(group.getChildAt(i))) return true;
    }
    return false;
  }

  private static void assertRenderedContentsFit(View view) {
    if (view.getVisibility() != View.VISIBLE) return;
    Rect visible = new Rect();
    assertTrue("Rendered content remains visible: " + geometry(view), view.getGlobalVisibleRect(visible));
    assertEquals("Rendered content width is not clipped: " + geometry(view), view.getWidth(), visible.width());
    assertEquals("Rendered content height is not clipped: " + geometry(view), view.getHeight(), visible.height());
    if (view instanceof TextView && ((TextView) view).getText().length() > 0)
      assertReadable((TextView) view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) assertRenderedContentsFit(group.getChildAt(i));
    }
  }

  private static void measure(View view, int widthDp, int heightDp) {
    int width = Ui.dp(view.getContext(), widthDp), height = Ui.dp(view.getContext(), heightDp);
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
  }

  private static View find(View view, String description) {
    if (view.getVisibility() != View.VISIBLE) return null;
    if (description.contentEquals(view.getContentDescription() == null
        ? "" : view.getContentDescription())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static View require(View root, String description) {
    View found = find(root, description);
    assertNotNull("Native control not found: " + description, found);
    return found;
  }

  private static void assertText(View root, String description, String expected) {
    assertEquals(expected, ((TextView) require(root, description)).getText().toString());
  }

  private static void collectTargets(View view, List<View> targets) {
    if (view.getVisibility() != View.VISIBLE) return;
    if (view.isClickable()) targets.add(view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) collectTargets(group.getChildAt(i), targets);
    }
  }

  private static Rect inRoot(ViewGroup root, View target) {
    // This API converts content coordinates and subtracts the descendant's own scroll offset.
    // Start with its drawing viewport, not (0,0), so single-line TextView scroll does not move
    // an otherwise correct touch rectangle thousands of pixels out of the toolbar.
    Rect bounds = new Rect();
    target.getDrawingRect(bounds);
    root.offsetDescendantRectToMyCoords(target, bounds);
    return bounds;
  }

  private static String geometry(View target) {
    StringBuilder details = new StringBuilder();
    for (View current = target; current != null;
        current = current.getParent() instanceof View ? (View) current.getParent() : null) {
      Rect visible = new Rect();
      current.getGlobalVisibleRect(visible);
      details.append(current.getClass().getSimpleName()).append('[')
          .append(current.getContentDescription()).append("] frame=")
          .append(current.getLeft()).append(',').append(current.getTop()).append(',')
          .append(current.getRight()).append(',').append(current.getBottom())
          .append(" visible=").append(visible.toShortString())
          .append(" padding=").append(current.getPaddingLeft()).append(',')
          .append(current.getPaddingTop()).append(',').append(current.getPaddingRight())
          .append(',').append(current.getPaddingBottom())
          .append(" scroll=").append(current.getScrollX()).append(',').append(current.getScrollY());
      if (current instanceof ViewGroup) details.append(" clipChildren=")
          .append(((ViewGroup) current).getClipChildren()).append(" clipToPadding=")
          .append(((ViewGroup) current).getClipToPadding());
      details.append(" <- ");
    }
    return details.toString();
  }

  private static void assertReadable(TextView text) {
    Layout layout = text.getLayout();
    assertNotNull("Native text layout exists", layout);
    int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
    int height = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
    assertTrue("Entire label height fits: " + text.getText(), layout.getHeight() <= height);
    for (int line = 0; line < layout.getLineCount(); line++) {
      assertEquals("No ellipsis: " + text.getText(), 0, layout.getEllipsisCount(line));
      // setSingleLine may use a VERY_WIDE layout. After pre-draw, actual drawing applies scrollX;
      // raw Layout coordinates are not coordinates inside the visible TextView content viewport.
      float left = layout.getLineLeft(line) - text.getScrollX();
      float right = layout.getLineRight(line) - text.getScrollX();
      String geometry = text.getText() + " left=" + left + " right=" + right
          + " viewport=" + width + " layout=" + layout.getWidth() + " scrollX=" + text.getScrollX();
      assertTrue("Label left edge fits: " + geometry, left >= -1f);
      assertTrue("Label right edge fits: " + geometry, right <= width + 1f);
    }
    assertTrue("Full label fits rather than silently clipping: " + text.getText(),
        text.getPaint().measureText(text.getText().toString()) <= width + 1f);
  }

  private static void touchDisabled(View view) {
    long now = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
        view.getWidth() / 2f, view.getHeight() / 2f, 0);
    MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP,
        view.getWidth() / 2f, view.getHeight() / 2f, 0);
    try {
      view.dispatchTouchEvent(down);
      view.dispatchTouchEvent(up);
    } finally { down.recycle(); up.recycle(); }
  }

  private static final class Routes implements WorkspaceBar.Actions {
    final List<WorkspaceBar.Mode> selected = new ArrayList<>();
    int tools, hide;
    public void select(WorkspaceBar.Mode mode) { selected.add(mode); }
    public void tools() { tools++; }
    public void hide() { hide++; }
  }

  private final class Host {
    final EditorHost proxy;
    final List<String> inserted = new ArrayList<>();
    String editor = "宿主聊天草稿";
    LinearLayout panel;
    String mode = "translation";
    int typing;

    Host(Context context) {
      proxy = (EditorHost) Proxy.newProxyInstance(EditorHost.class.getClassLoader(),
          new Class<?>[] {EditorHost.class}, (object, method, args) -> {
            switch (method.getName()) {
              case "context": return context;
              case "services": return services;
              case "privateMode": return false;
              case "full": return editor;
              case "mode": return mode;
              case "generation": return 1L;
              case "cancelVoice":
              case "cancelAdvisor":
              case "commitComposing":
              case "snapshot": return null;
              case "openPanel":
                mode = (String) args[0];
                assertEquals("translation", mode);
                assertEquals(KeyboardView.HEIGHT_DP, ((Integer) args[1]).intValue());
                panel = Ui.col(context);
                return panel;
              case "workspaceBar":
                assertEquals(WorkspaceBar.Mode.TRANSLATION, args[0]);
                return new WorkspaceBar(context, (WorkspaceBar.Mode) args[0], false, new Routes());
              case "protectedInsert": inserted.add((String) args[0]); return true;
              case "protectedReplace":
                if (!java.util.Objects.equals(editor, args[0])) return false;
                editor = (String) args[1]; return true;
              case "typing": typing++; mode = "input"; return null;
              case "toString": return "WorkspaceToolsTest.EditorHost";
              case "hashCode": return System.identityHashCode(object);
              case "equals": return object == args[0];
              default: throw new AssertionError("Unexpected editor boundary call: " + method.getName());
            }
          });
    }
  }
}
