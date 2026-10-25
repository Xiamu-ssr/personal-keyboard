package app.doudou.ime.advisor;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.R;
import app.doudou.ime.capture.*;
import app.doudou.ime.design.Icons;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.*;
import app.doudou.ime.model.TextModelClient;
import app.doudou.ime.voice.VoiceButton;
import java.util.*;
import org.json.*;

/** One tap captures up to two screens; expressions and saved pages are the only reply controls. */
public final class AdvisorController extends PanelController {
  private final AdvisorSession advisor = new AdvisorSession();
  private final ReplyHistory replies = new ReplyHistory();
  private final TextModelClient model;
  private final Handler main = new Handler(Looper.getMainLooper());
  private String intent = "", lastApplied;
  private JSONObject unsaved;
  private int[] firstSignature;
  private boolean second, scrollPending;
  private boolean restoreNeeded;
  private boolean restoreAfterGesture;
  private boolean captureGesture;
  private int gestureTop;
  private int pendingGesture = -1;
  private View captureCancel;
  private TextView captureLabel;

  public AdvisorController(EditorHost host) {
    super(host);
    model = new TextModelClient(this);
  }

  public void cancel() {
    unsaved = null;
    pendingGesture = -1;
    restoreNeeded = false;
    restoreAfterGesture = false;
    captureGesture = false;
    advisor.cancel();
    model.cancel();
    replies.clear();
    intent = "";
    firstSignature = null;
    lastApplied = null;
  }

  /** Only our own bounded drag may temporarily hide this editor's input surface. */
  public boolean retainDuringGesture(boolean finishingInput) {
    return !finishingInput && captureGesture && advisor.generation == host.generation();
  }

  public boolean retainsInputView() {
    return captureGesture && advisor.generation == host.generation();
  }

  private void scroll(boolean earlier, int top, CaptureService.Scrolled callback) {
    final long generation = host.generation();
    captureGesture = true;
    CaptureService.Scrolled completed =
        success -> {
          if (generation != host.generation()) return;
          recoverInputView(generation, success, callback, 0);
        };
    if (earlier) CaptureService.earlier(top, completed);
    else CaptureService.later(top, completed);
  }

  private void recoverInputView(
      long generation, boolean success, CaptureService.Scrolled callback, int attempt) {
    if (generation != host.generation()) return;
    if (host.inputViewVisible()) {
      captureGesture = false;
      callback.complete(success);
      return;
    }
    if (attempt >= 40) {
      // Do not keep capturing or send a request with an absent editor.
      cancel();
      typing();
      return;
    }
    if (attempt == 0) host.showInputView();
    main.postDelayed(() -> recoverInputView(generation, success, callback, attempt + 1), 50);
  }

  private void cancelCapture() {
    if (advisor.stage == AdvisorSession.Stage.CANCELLED) return;
    long generation = host.generation();
    boolean pending = pendingGesture >= 0;
    boolean needsRestore = restoreNeeded;
    cancel();
    restoreNeeded = needsRestore;
    restoreAfterGesture = pending;
    captureGesture = pending;
    if (captureCancel != null) captureCancel.setEnabled(false);
    if (captureLabel != null) captureLabel.setText("正在返回");
    if (!pending)
      restore(
          () -> {
            if (generation == host.generation()) typing();
          });
  }

  public void leave() {
    model.cancel();
    if (replies.choices().isEmpty()) {
      cancelCapture();
      return;
    } else advisor.stage = AdvisorSession.Stage.RESULTS;
    typing();
  }

  public void start(boolean ignored) {
    if (advisor.valid(advisor.token(), host.generation())
        && (advisor.stage == AdvisorSession.Stage.RESULTS || advisor.stage == AdvisorSession.Stage.GENERATING
            || (advisor.stage == AdvisorSession.Stage.ERROR && advisor.pages() > 0))) {
      snapshot();
      panel();
      return;
    }
    beginCapture();
  }

  private void beginCapture() {
    if (privateMode()) return;
    if (!model.configured()) {
      message("登录后使用帮我回", "", "去登录", () -> settings("account"));
      return;
    }
    if (CaptureService.current == null) {
      message("允许主动截屏", "", "允许截屏", () -> settings("settings"));
      return;
    }
    commitComposing();
    host.cancelVoice();
    model.cancel();
    snapshot();
    advisor.begin(host.generation(), host.editorPackage());
    replies.clear();
    intent = "";
    lastApplied = null;
    firstSignature = null;
    captureStage(false, false);
  }

  private void captureStage(boolean second, boolean move) {
    this.second = second;
    scrollPending = move;
    advisor.stage = AdvisorSession.Stage.RESIZING;
    reset("capturing", 56);
    root.setPadding(
        Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), host.bottomInset() + Ui.dp(this, 4));
    root.setBackgroundColor(Ui.keyboard());
    LinearLayout bar = Ui.row(this);
    ProgressBar spinner = new ProgressBar(this);
    spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.accent()));
    bar.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
    TextView label = Ui.text(this, "正在看聊天", 14, Ui.sub());
    Ui.pad(this, label, 12, 0);
    label.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
    TextView cancel = Ui.button(this, "取消", this::cancelCapture);
    captureCancel = cancel;
    captureLabel = label;
    cancel.setMinHeight(0);
    cancel.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
    bar.addView(cancel, new LinearLayout.LayoutParams(-2, -1));
    root.addView(bar, new LinearLayout.LayoutParams(-1, 0, 1));
    awaitLayout(advisor.token(), host.generation(), 0, -1, 0);
  }

  private void awaitLayout(int token, long generation, int count, int last, int stable) {
    if (!advisor.valid(token, host.generation()) || !host.mode().equals("capturing")) return;
    int[] location = new int[2];
    root.getLocationOnScreen(location);
    int top = location[1];
    boolean measured =
        root.isLaidOut() && root.getHeight() <= Ui.dp(this, 70) + host.bottomInset() && top > 100;
    int next = measured && top == last ? stable + 1 : 0;
    if (next >= 3) {
      if (scrollPending) {
        scrollPending = false;
        gestureTop = top;
        restoreNeeded = true;
        pendingGesture = token;
        scroll(
            true,
            top,
            success -> {
              if (pendingGesture == token) pendingGesture = -1;
              if (generation != host.generation()) return;
              if (!advisor.valid(token, host.generation())) {
                if (restoreAfterGesture) {
                  restoreAfterGesture = false;
                  restore(
                      () -> {
                        if (generation == host.generation()) typing();
                      });
                } else restoreNeeded = false;
                return;
              }
              if (!success) {
                restore(() -> generate(""));
                return;
              }
              main.postDelayed(() -> awaitLayout(token, generation, 0, -1, 0), 450);
            });
        return;
      }
      capture(token, generation, top);
      return;
    }
    if (count > 40) {
      fail("当前页面尚未稳定，请重试");
      return;
    }
    main.postDelayed(() -> awaitLayout(token, generation, count + 1, top, next), 50);
  }

  private void capture(int token, long generation, int top) {
    advisor.stage = AdvisorSession.Stage.CAPTURING;
    final boolean earlier = second;
    CaptureService.capture(
        top,
        (bitmap, error) -> {
          if (!advisor.valid(token, host.generation())) {
            if (bitmap != null) bitmap.recycle();
            return;
          }
          if (error != null) {
            if (advisor.pages() > 0) restore(() -> generate(""));
            else fail(error);
            return;
          }
          int[] signature = CaptureService.signature(bitmap);
          if (earlier && !CaptureDifference.changed(firstSignature, signature)) {
            bitmap.recycle();
            restore(() -> generate(""));
            return;
          }
          advisor.stage = AdvisorSession.Stage.READING;
          app.worker.execute(
              () -> {
                try {
                  int width = Math.min(1000, bitmap.getWidth()),
                      height =
                          Math.max(
                              1, (int) ((long) bitmap.getHeight() * width / bitmap.getWidth()));
                  Bitmap small = Bitmap.createScaledBitmap(bitmap, width, height, true);
                  java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                  small.compress(Bitmap.CompressFormat.JPEG, 65, out);
                  if (small != bitmap) small.recycle();
                  if (out.size() > 550000) throw new IllegalStateException("截图过大，请重试");
                  String image =
                      "data:image/jpeg;base64,"
                          + android.util.Base64.encodeToString(
                              out.toByteArray(), android.util.Base64.NO_WRAP);
                  main.post(
                      () -> {
                        if (!advisor.valid(token, host.generation())
                            || host.generation() != generation) return;
                        advisor.image(image, earlier);
                        if (!earlier) {
                          firstSignature = signature;
                          captureStage(true, true);
                        } else
                          restore(
                              () -> {
                                if (advisor.valid(token, host.generation())) generate("");
                              });
                      });
                } catch (Exception e) {
                  main.post(
                      () -> {
                        if (advisor.valid(token, host.generation())) fail("截图保存失败，请重试");
                      });
                } finally {
                  bitmap.recycle();
                }
              });
        });
  }

  private void restore(Runnable done) {
    if (!restoreNeeded) {
      done.run();
      return;
    }
    restoreNeeded = false;
    scroll(false, gestureTop, success -> root.postOnAnimation(() -> root.postOnAnimation(done)));
  }

  private void fail(String error) {
    if (restoreNeeded) {
      restore(() -> fail(error));
      return;
    }
    advisor.fail(error);
    if (host.mode().equals("advisor") || host.mode().equals("capturing")) panel();
  }

  private void panel() {
    if (!advisor.valid(advisor.token(), host.generation())) return;
    reset("advisor", 340);
    sheet();
    root.addView(host.workspaceBar(WorkspaceBar.Mode.ADVISOR), new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
    if (advisor.stage == AdvisorSession.Stage.GENERATING) {
      LinearLayout progress = Ui.col(this);
      progress.setGravity(Gravity.CENTER);
      VoiceButton animation = new VoiceButton(this, () -> {});
      progress.addView(animation, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 48)));
      animation.setState(VoiceButton.State.REWRITING);
      Ui.gap(this, progress, 16);
      TextView loading = Ui.text(this, "想想怎么回…", 16, Ui.sub());
      loading.setGravity(Gravity.CENTER);
      progress.addView(loading, new LinearLayout.LayoutParams(-1, -2));
      root.addView(progress, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    if (advisor.stage == AdvisorSession.Stage.ERROR) {
      LinearLayout error = Ui.col(this);
      Ui.pad(this, error, 20, 20);
      error.addView(Ui.text(this, advisor.error, 15, Ui.sub()));
      Ui.gap(this, error, 16);
      error.addView(
          Ui.primary(
              this,
              advisor.pages() == 0 ? "重新截图" : "重试",
              () -> {
                if (advisor.pages() == 0) beginCapture();
                else generate("");
              }));
      if (!replies.choices().isEmpty())
        error.addView(
            Ui.button(
                this,
                "返回已有答案",
                () -> {
                  advisor.stage = AdvisorSession.Stage.RESULTS;
                  panel();
                }));
      root.addView(error, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.col(this);
    Ui.pad(this, list, 10, 2);
    scroll.addView(list);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    int choiceNumber = 0;
    for (ReplyHistory.Choice choice : replies.choices()) {
      LinearLayout row = Ui.row(this);
      row.setGravity(Gravity.TOP);
      row.setBackground(Ui.bg(this, Ui.surface(), 14));
      LinearLayout answer = Ui.col(this);
      Ui.pad(this, answer, 12, 12);
      String name = choice.name + (replies.choices().size() == 3 ? " · " + (++choiceNumber) : "");
      TextView label = Ui.text(this, name, 12, Ui.accent());
      answer.addView(label);
      Ui.gap(this, answer, 7);
      TextView text = Ui.text(this, choice.text, 17, Ui.ink());
      text.setLineSpacing(Ui.dp(this, 3), 1);
      answer.addView(text);
      answer.setOnClickListener(v -> applyReply(choice.text));
      answer.setOnLongClickListener(
          v -> {
            editLocal("修改回复", choice.text, this::applyReply);
            return true;
          });
      answer.setFocusable(true);
      answer.setContentDescription(choice.name + "回复");
      row.addView(answer, new LinearLayout.LayoutParams(0, -2, 1));
      View variants = Ui.icon(this, "换一条", () -> generate(choice.slotId));
      variants.setContentDescription("换一条" + choice.name);
      row.addView(variants, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 52)));
      list.addView(row);
      Ui.gap(this, list, 8);
    }
    LinearLayout footer = Ui.row(this);
    Ui.pad(this, footer, 8, 4);
    View options = Ui.icon(this, "更多", () -> {});
    options.setContentDescription("回复选项");
    options.setOnClickListener(v -> options(v));
    footer.addView(options, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    View back =
        Icons.button(
            this,
            R.drawable.ic_arrow_back,
            "上一组",
            () -> {
              replies.back();
              panel();
            });
    back.setEnabled(replies.canBack());
    back.setAlpha(replies.canBack() ? 1 : .3f);
    footer.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView position = Ui.text(this, replies.position(), 13, Ui.sub());
    position.setGravity(Gravity.CENTER);
    footer.addView(position, new LinearLayout.LayoutParams(Ui.dp(this, 52), -2));
    View forward =
        Icons.button(
            this,
            R.drawable.ic_arrow_back,
            "下一组",
            () -> {
              replies.forward();
              panel();
            });
    forward.setRotation(180);
    forward.setEnabled(replies.canForward());
    forward.setAlpha(replies.canForward() ? 1 : .3f);
    footer.addView(forward, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    footer.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
    TextView more = Ui.button(this, "换一批", () -> generate(""));
    more.setTextColor(Ui.accent());
    footer.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 100), Ui.dp(this, 48)));
    root.addView(footer);
  }

  private void options(View anchor) {
    PopupMenu menu = Ui.menu(this, anchor);
    menu.getMenu()
        .add("我想表达…")
        .setOnMenuItemClickListener(
            i -> {
              anchor.post(
                  () ->
                      editLocal(
                          "我想表达",
                          intent,
                          value -> {
                            intent = value;
                            advisor.stage = AdvisorSession.Stage.READY;
                            generate("");
                          }));
              return true;
            });
    menu.getMenu()
        .add("重新截图")
        .setOnMenuItemClickListener(
            i -> {
              anchor.post(this::beginCapture);
              return true;
            });
    menu.show();
  }

  private void sheet() {
    root.setPadding(
        Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), host.bottomInset() + Ui.dp(this, 4));
    android.graphics.drawable.GradientDrawable shape = Ui.bg(this, Ui.sheet(), 18);
    float r = Ui.dp(this, 18);
    shape.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
    shape.setStroke(Ui.dp(this, 1), Ui.sheetOutline());
    android.graphics.drawable.LayerDrawable gutter =
        new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[] {
              new android.graphics.drawable.ColorDrawable(Ui.keyboard()), shape
            });
    gutter.setLayerInset(1, 0, Ui.dp(this, 6), 0, host.bottomInset());
    root.setBackground(gutter);
  }

  private void generate(String slot) {
    if (advisor.pages() == 0 || advisor.stage == AdvisorSession.Stage.GENERATING) return;
    final int token = advisor.token();
    final long generation = host.generation();
    advisor.stage = AdvisorSession.Stage.GENERATING;
    panel();
    if (unsaved != null) {
      saveOutput(unsaved, token, generation);
      return;
    }
    model.reply(
        advisor.conversationRef(),
        host.editorPackage(),
        intent,
        slot,
        replies.recentTexts(),
        advisor.images(),
        (output, error) -> {
          if (!advisor.valid(token, host.generation()) || generation != host.generation()) return;
          if (error != null) {
            fail(error);
            return;
          }
          unsaved = output;
          saveOutput(output, token, generation);
        });
  }

  private void saveOutput(JSONObject output, int token, long generation) {
    final String previousHistory = advisor.historyId;
    final List<String> captured = advisor.images();
    final String application = advisor.packageName, account = app.cloud.userId();
    app.worker.execute(
        () -> {
          try {
            if (!account.equals(app.cloud.userId()) || !advisor.valid(token, host.generation()))
              return;
            JSONObject identity = output.optJSONObject("identity");
            if (identity != null) identity.put("application", application);
            String saved = app.histories.saveAdvisor(previousHistory, output, captured, account);
            main.post(
                () -> {
                  if (!advisor.valid(token, host.generation()) || generation != host.generation())
                    return;
                  try {
                    advisor.historyId = saved;
                    unsaved = null;
                    JSONObject subject = output.optJSONObject("identity");
                    if (subject != null && subject.optDouble("confidence") >= .9) {
                      advisor.personId = subject.optString("personId");
                      advisor.personName = subject.optString("nickname");
                    }
                    JSONArray values = output.getJSONArray("replies");
                    List<ReplyHistory.Choice> choices = new ArrayList<>();
                    for (int i = 0; i < values.length(); i++) {
                      JSONObject v = values.getJSONObject(i);
                      choices.add(
                          new ReplyHistory.Choice(
                              v.getString("slotId"), v.getString("style"), v.getString("text")));
                    }
                    replies.accept(choices, output.getJSONArray("messages").toString());
                    advisor.stage = AdvisorSession.Stage.RESULTS;
                    if (host.mode().equals("advisor")) panel();
                  } catch (Exception e) {
                    fail("回复未完整保存，请重试");
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (advisor.valid(token, host.generation())) fail("手机存储不可用，请释放空间后重试");
                });
          }
        });
  }

  private void applyReply(String text) {
    if (lastApplied != null && lastApplied.equals(host.full())) {
      host.undoInsertion();
      snapshot();
    }
    if (protectedInsert(text)) {
      if (!advisor.historyId.isEmpty()) {
        app.histories.inserted(advisor.historyId, text, advisor.conversationRef());
        host.trackHistory(advisor.historyId);
      }
      lastApplied = host.full();
      model.cancel();
      typing();
    }
  }
}
