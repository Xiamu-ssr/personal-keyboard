package app.doudou.ime.advisor;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.model.TextModelClient;
import java.util.*;
import org.json.*;

/** Screenshot → one multimodal request → three selectable replies. */
public final class AdvisorController extends PanelController {
  private final AdvisorSession advisor = new AdvisorSession();
  private final ReplyHistory replies = new ReplyHistory();
  private final TextModelClient model;
  private final Handler main = new Handler(Looper.getMainLooper());
  private String intent = "";
  private final String[] styles = {"温和", "直接", "轻松"};

  public AdvisorController(EditorHost host) {
    super(host);
    model = new TextModelClient(this);
  }

  public void cancel() {
    advisor.cancel();
    model.cancel();
    replies.clear();
    intent = "";
  }

  public void start(boolean supplement) {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用军师读屏。", null);
      return;
    }
    if (CaptureService.current == null) {
      message("允许主动截屏", "点军师时截取当前屏幕，用于生成回复。", () -> settings("settings"));
      return;
    }
    if (supplement && advisor.pages() >= AdvisorSession.MAX_PAGES) {
      Ui.toast(this, "本次最多三屏，可重新截取");
      return;
    }
    commitComposing();
    host.cancelVoice();
    model.cancel();
    if (!supplement) {
      snapshot();
      advisor.begin(host.generation(), host.editorPackage());
      replies.clear();
      intent = "";
    } else {
      advisor.stage = AdvisorSession.Stage.RESIZING;
      replies.clear();
    }
    final int token = advisor.token();
    final long generation = host.generation();
    reset("capturing", 76);
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.text(this, "正在截取当前屏幕…", 14, Ui.SUB),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1));
    bar.addView(
        Ui.button(
            this,
            "取消",
            () -> {
              cancel();
              typing();
            }));
    root.addView(bar);
    awaitLayout(token, generation, 0, -1, 0);
  }

  private void awaitLayout(int token, long generation, int count, int last, int stable) {
    if (!advisor.valid(token, generation) || !host.mode().equals("capturing")) return;
    int[] location = new int[2];
    root.getLocationOnScreen(location);
    int top = location[1];
    boolean measured =
        root.isLaidOut() && root.getHeight() <= Ui.dp(this, 90) + host.bottomInset() && top > 100;
    int next = measured && top == last ? stable + 1 : 0;
    if (next >= 3) {
      advisor.stage = AdvisorSession.Stage.CAPTURING;
      CaptureService.capture(
          top,
          (bitmap, error) -> {
            if (!advisor.valid(token, host.generation()) || generation != host.generation()) {
              if (bitmap != null) bitmap.recycle();
              return;
            }
            if (error != null) {
              advisor.fail(error);
              panel();
              return;
            }
            advisor.stage = AdvisorSession.Stage.READING;
            final String previousHistory = advisor.historyId;
            app.worker.execute(
                () -> {
                  try {
                    int width = Math.min(1000, bitmap.getWidth()),
                        height =
                            Math.max(
                                1, (int) ((long) bitmap.getHeight() * width / bitmap.getWidth()));
                    Bitmap small = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    small.compress(Bitmap.CompressFormat.JPEG, 65, output);
                    if (small != bitmap) small.recycle();
                    if (output.size() > 550000) throw new IllegalStateException("截图过大，请重试当前屏幕");
                    String image =
                        "data:image/jpeg;base64,"
                            + android.util.Base64.encodeToString(
                                output.toByteArray(), android.util.Base64.NO_WRAP);
                    if (!advisor.valid(token, generation)) return;
                    String savedHistory = previousHistory;
                    if (app.prefs().getBoolean("history", true)) {
                      if (savedHistory.isEmpty())
                        savedHistory = app.histories.create("advisor", "");
                      app.histories.screenshot(savedHistory, bitmap);
                    }
                    final String history = savedHistory;
                    main.post(
                        () -> {
                          if (advisor.valid(token, host.generation())
                              && generation == host.generation()) {
                            advisor.historyId = history;
                            advisor.image(image);
                            panel();
                          }
                        });
                  } catch (Exception ex) {
                    main.post(
                        () -> {
                          if (advisor.valid(token, host.generation())) {
                            advisor.fail(ex.getMessage());
                            panel();
                          }
                        });
                  } finally {
                    bitmap.recycle();
                  }
                });
          });
      return;
    }
    if (count > 40) {
      advisor.fail("当前页面尚未稳定，请重试");
      panel();
      return;
    }
    main.postDelayed(() -> awaitLayout(token, generation, count + 1, top, next), 50);
  }

  private void panel() {
    if (!advisor.valid(advisor.token(), host.generation())) return;
    reset("advisor", 284);
    panelHeading(
        advisor.personName.isEmpty() ? "军师" : advisor.personName,
        () -> {
          cancel();
          typing();
        });
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = Ui.col(this);
    Ui.pad(this, content, 12, 3);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (advisor.stage == AdvisorSession.Stage.ERROR) {
      content.addView(Ui.text(this, advisor.error, 14, Ui.RED));
      Ui.gap(this, content, 6);
    }
    if (replies.replies().isEmpty() || advisor.stage == AdvisorSession.Stage.GENERATING) {
      LinearLayout choices = Ui.row(this);
      choices.addView(
          Ui.button(
              this,
              advisor.personName.isEmpty() ? "选择聊天对象" : advisor.personName,
              this::choosePerson),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
      choices.addView(
          Ui.button(this, "已截 " + advisor.pages() + " 屏", () -> start(true)),
          new LinearLayout.LayoutParams(Ui.dp(this, 100), Ui.dp(this, 40)));
      content.addView(choices);
      content.addView(
          Ui.button(
              this,
              intent.isEmpty() ? "我想表达…" : intent,
              () ->
                  editLocal(
                      "我想表达",
                      intent,
                      value -> {
                        intent = value;
                        panel();
                      })));
      Ui.gap(this, content, 6);
      if (advisor.stage == AdvisorSession.Stage.GENERATING)
        content.addView(Ui.text(this, "正在看图组织回复…", 14, Ui.SUB));
      else
        content.addView(
            Ui.primary(
                this,
                model.configured() ? "生成三条回复" : "登录豆豆账户",
                () -> {
                  if (model.configured()) generate("");
                  else settings("account");
                }));
    } else {
      for (int i = 0; i < replies.replies().size(); i++) {
        final int index = i;
        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.bg(this, Ui.SURFACE, 14));
        Ui.pad(this, card, 12, 4);
        TextView text = Ui.text(this, replies.replies().get(i), 15, Ui.INK);
        text.setMaxLines(2);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setContentDescription(styles[i] + "回复");
        text.setOnClickListener(v -> applyReply(replies.replies().get(index)));
        card.addView(text, new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1));
        card.addView(
            Ui.button(
                this,
                "更多",
                () -> generate(replies.style().isEmpty() ? styles[index] : replies.style())),
            new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        content.addView(card);
        Ui.gap(this, content, 5);
      }
    }
    LinearLayout footer = Ui.row(this);
    footer.addView(
        Ui.button(this, "重新截图", () -> start(false)),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    if (replies.canBack())
      footer.addView(
          Ui.button(
              this,
              "上一组",
              () -> {
                replies.back();
                advisor.stage = AdvisorSession.Stage.RESULTS;
                panel();
              }),
          new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    footer.addView(
        Ui.button(this, "补充一屏", () -> start(true)),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    root.addView(footer);
  }

  private void choosePerson() {
    model.cancel();
    reset("advisor", 284);
    panelHeading("聊天对象", this::panel);
    ScrollView scroll = new ScrollView(this);
    LinearLayout people = Ui.col(this);
    scroll.addView(people);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    people.addView(
        Ui.button(
            this,
            "暂不指定",
            () -> {
              advisor.identify("", "");
              replies.clear();
              panel();
            }));
    for (MemoryStore.Item person : app.store.list("person", 0))
      people.addView(
          Ui.cell(
              this,
              "人物",
              person.title,
              "",
              "",
              () -> {
                advisor.identify(person.globalId, person.title);
                replies.clear();
                panel();
              }));
    people.addView(Ui.button(this, "管理人物", () -> settings("people")));
  }

  private void generate(String style) {
    if (!advisor.canGenerate() || advisor.stage == AdvisorSession.Stage.GENERATING) return;
    final int token = advisor.token();
    final long generation = host.generation();
    advisor.stage = AdvisorSession.Stage.GENERATING;
    panel();
    model.reply(
        advisor.personId,
        advisor.conversationRef(),
        intent,
        style,
        advisor.images(),
        (output, error) -> {
          if (!advisor.valid(token, host.generation()) || generation != host.generation()) return;
          if (error != null) {
            advisor.fail(error);
            panel();
            return;
          }
          try {
            JSONArray values = output.getJSONArray("replies"),
                messages = output.getJSONArray("messages");
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < values.length(); i++)
              texts.add(values.getJSONObject(i).getString("text"));
            if (!advisor.historyId.isEmpty())
              app.histories.interpreted(advisor.historyId, messages);
            replies.accept(texts, style, !style.isEmpty(), messages.toString());
            advisor.stage = AdvisorSession.Stage.RESULTS;
            panel();
          } catch (Exception ex) {
            advisor.fail("返回内容不完整，请重试");
            panel();
          }
        });
  }

  private void applyReply(String value) {
    if (protectedInsert(value)) {
      if (!advisor.historyId.isEmpty()) {
        try {
          app.histories.interpreted(advisor.historyId, new JSONArray(replies.messages()));
          app.histories.inserted(advisor.historyId, value, advisor.conversationRef());
          host.trackHistory(advisor.historyId);
        } catch (JSONException ignored) {
        }
      }
      advisor.cancel();
      model.cancel();
      typing();
    }
  }
}
