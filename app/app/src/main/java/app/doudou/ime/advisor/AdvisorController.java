package app.doudou.ime.advisor;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.capture.LocalOcr;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.model.TextModelClient;
import java.util.*;

/** Owns explicit capture, identity, context and reply lifecycle. */
public final class AdvisorController extends PanelController {
  private final AdvisorSession advisor = new AdvisorSession();
  private final ReplyHistory replyHistory = new ReplyHistory();
  private final TextModelClient model;
  private final Handler main = new Handler(Looper.getMainLooper());
  private String intent = "";
  private final String[] styleNames = {"温和", "直接", "轻松"};

  public AdvisorController(EditorHost host) {
    super(host);
    model = new TextModelClient(this);
  }

  public void cancel() {
    advisor.cancel();
    model.cancel();
    replyHistory.clear();
    intent = "";
  }

  public void start(boolean supplement) {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用军师读屏。", null);
      return;
    }
    if (CaptureService.current == null) {
      message("军师需要主动截屏权限", "只在你点击时截屏并在本机识字。请到设置开启，返回后再试。", () -> start(false));
      return;
    }
    if (supplement && advisor.pages() >= AdvisorSession.MAX_PAGES) {
      Ui.toast(this, "已补读六屏，可修正当前文字或重新开始");
      return;
    }
    commitComposing();
    host.cancelVoice();
    model.cancel();
    if (!supplement) {
      snapshot();
      advisor.begin(host.generation(), host.editorPackage());
      replyHistory.clear();
      intent = "";
    } else {
      advisor.stage = AdvisorSession.Stage.RESIZING;
      replyHistory.clear();
    }
    final int token = advisor.token();
    final long gen = host.generation();
    reset("capturing", 76);
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.text(this, "正在读取当前屏幕…", 14, Ui.SUB),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1));
    bar.addView(
        Ui.button(
            this,
            "取消",
            () -> {
              advisor.cancel();
              typing();
            }));
    root.addView(bar);
    awaitLayout(token, gen, 0, -1, 0);
  }

  private void awaitLayout(int token, long gen, int count, int last, int stable) {
    if (!advisor.valid(token, gen) || !host.mode().equals("capturing")) return;
    int[] loc = new int[2];
    root.getLocationOnScreen(loc);
    int top = loc[1];
    boolean measured =
        root.isLaidOut() && root.getHeight() <= Ui.dp(this, 90) + host.bottomInset() && top > 100;
    int next = measured && top == last ? stable + 1 : 0;
    if (next >= 3) {
      advisor.stage = AdvisorSession.Stage.CAPTURING;
      CaptureService.capture(
          top,
          (bitmap, error) -> {
            if (!advisor.valid(token, host.generation()) || gen != host.generation()) {
              if (bitmap != null) bitmap.recycle();
              return;
            }
            if (error != null) {
              advisor.fail(error);
              advisorPanel();
              return;
            }
            advisor.stage = AdvisorSession.Stage.READING;
            LocalOcr.read(
                bitmap,
                (text, why) -> {
                  if (!advisor.valid(token, host.generation()) || gen != host.generation()) return;
                  if (why != null || text == null || text.trim().isEmpty()) {
                    advisor.fail(why == null ? "当前屏没有识别到文字，可以补充或重新截屏" : why);
                    advisorPanel();
                    return;
                  }
                  try {
                    advisor.append(text);
                  } catch (IllegalArgumentException limit) {
                    advisor.fail(limit.getMessage());
                  }
                  advisorPanel();
                });
          });
      return;
    }
    if (count > 40) {
      advisor.fail("宿主布局没有稳定，未截屏；可以重试");
      advisorPanel();
      return;
    }
    main.postDelayed(() -> awaitLayout(token, gen, count + 1, top, next), 50);
  }

  private void advisorPanel() {
    if (!advisor.valid(advisor.token(), host.generation())) return;
    reset("advisor", 284);
    panelHeading(
        "军师 · " + (advisor.person > 0 ? personName(advisor.person) : "选择聊天对象"),
        () -> {
          advisor.cancel();
          model.cancel();
          typing();
        });
    ScrollView scroll = new ScrollView(this);
    LinearLayout content = Ui.col(this);
    Ui.pad(this, content, 12, 4);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (advisor.stage == AdvisorSession.Stage.ERROR) {
      TextView e = Ui.text(this, advisor.error, 14, Ui.RED);
      content.addView(e);
      content.addView(Ui.button(this, "重新读当前屏", () -> start(false)));
    }
    if (!advisor.context().isEmpty()) {
      if (advisor.person == 0) {
        TextView help = Ui.text(this, "已读当前屏 · 请选择对象，记忆按人物隔离。", 12, Ui.SUB);
        content.addView(help);
        Ui.gap(this, content, 8);
        for (MemoryStore.Item p : app.store.list("person", 0))
          if (p.aux.isEmpty() || p.aux.equals(host.editorPackage())) {
            content.addView(
                Ui.cell(
                    this,
                    "人物",
                    p.title,
                    "",
                    "",
                    () -> {
                      advisor.identify(p.id);

                      advisorPanel();
                    }));
          }
        content.addView(
            Ui.cell(
                this,
                "添加",
                "创建这个聊天对象",
                "",
                "",
                () ->
                    editLocal(
                        "给聊天对象起个名字",
                        "",
                        name -> {
                          if (name.trim().isEmpty()) {
                            advisorPanel();
                            return;
                          }
                          long id =
                              app.store.save(
                                  0,
                                  0,
                                  "person",
                                  0,
                                  name.trim(),
                                  "",
                                  host.editorPackage(),
                                  "manual");
                          advisor.identify(id);

                          advisorPanel();
                        })));
      } else if (replyHistory.replies().isEmpty()
          || advisor.stage == AdvisorSession.Stage.GENERATING) {
        TextView info =
            Ui.text(
                this,
                model.configured() ? "写下你想表达的立场，再生成三种说法。" : "文字模型尚未连接；本地读屏可用，配置后就能生成。",
                12,
                Ui.SUB);
        content.addView(info);
        Ui.gap(this, content, 10);
        content.addView(
            Ui.button(
                this,
                intent.isEmpty() ? "写下我想表达的意思" : intent,
                () ->
                    editLocal(
                        "想表达的意思",
                        intent,
                        value -> {
                          intent = value;
                          advisorPanel();
                        })));
        Ui.gap(this, content, 8);
        if (advisor.stage == AdvisorSession.Stage.GENERATING)
          content.addView(Ui.text(this, "正在组织表达…", 14, Ui.ACCENT));
        else
          content.addView(
              Ui.primary(
                  this,
                  model.configured() ? "生成三条回复" : "去配置文字模型",
                  () -> {
                    if (model.configured()) generate("");
                    else settings("settings");
                  }));
      } else {
        for (int index = 0; index < replyHistory.replies().size(); index++) {
          final int at = index;
          LinearLayout card = Ui.card(this);
          TextView label =
              Ui.text(
                  this,
                  !replyHistory.style().isEmpty()
                      ? replyHistory.style() + " · " + (index + 1)
                      : styleNames[index],
                  12,
                  Ui.ACCENT);
          card.addView(label);
          Ui.gap(this, card, 6);
          card.addView(Ui.text(this, replyHistory.replies().get(index), 16, Ui.INK));
          LinearLayout actions = Ui.row(this);
          Ui.gap(this, card, 8);
          actions.addView(
              Ui.button(this, "填入", () -> applyReply(replyHistory.replies().get(at))),
              new LinearLayout.LayoutParams(0, -2, 1));
          actions.addView(
              Ui.button(
                  this,
                  "更多",
                  () ->
                      generate(
                          replyHistory.style().isEmpty() ? styleNames[at] : replyHistory.style())),
              new LinearLayout.LayoutParams(0, -2, 1));
          card.addView(actions);
          content.addView(card);
          Ui.gap(this, content, 8);
        }
        if (replyHistory.canBack())
          content.addView(
              Ui.button(
                  this,
                  "返回上一组",
                  () -> {
                    replyHistory.back();
                    advisor.stage = AdvisorSession.Stage.RESULTS;
                    advisorPanel();
                  }));
      }
    }
    LinearLayout bottom = Ui.row(this);
    Ui.pad(this, bottom, 9, 4);
    TextView ocr = Ui.button(this, "识字", this::editContext);
    ocr.setContentDescription("查看 / 修正本次识字");
    ocr.setTextSize(12);
    bottom.addView(ocr, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    bottom.addView(
        Ui.button(this, "补当前屏", () -> start(true)),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    bottom.addView(
        Ui.button(
            this,
            "存文字",
            () -> {
              if (!advisor.context().isEmpty()) {
                history("主动读屏", advisor.context(), "capture:" + host.editorPackage());
                Ui.toast(this, "已存到本机历史，不是人物事实");
              }
            }),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    bottom.addView(
        Ui.button(
            this,
            "取消",
            () -> {
              model.cancel();
              advisor.cancel();
              typing();
            }),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
    for (int i = 0; i < bottom.getChildCount(); i++) {
      TextView button = (TextView) bottom.getChildAt(i);
      button.setTextSize(12);
      button.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
      ((LinearLayout.LayoutParams) button.getLayoutParams())
          .setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
    }
    root.addView(bottom);
  }

  private void editContext() {
    editLocal(
        "识字文字 · 请核对说话人",
        advisor.context(),
        updated -> {
          long person = advisor.person;
          advisor.begin(host.generation(), host.editorPackage());
          advisor.append(updated);
          if (person > 0) advisor.identify(person);
          replyHistory.clear();
          advisorPanel();
        });
  }

  private String personName(long id) {
    MemoryStore.Item p = app.store.get(id);
    return p == null ? "未确认人物" : p.title;
  }

  private void generate(String style) {
    if (!advisor.canGenerate() || advisor.stage == AdvisorSession.Stage.GENERATING) return;
    MemoryStore.Item person = app.store.get(advisor.person);
    if (person == null
        || person.deleted
        || (!person.aux.isEmpty() && !person.aux.equals(host.editorPackage()))) {
      advisor.fail("人物或应用范围已变化，请重新确认");
      advisorPanel();
      return;
    }
    final int token = advisor.token();
    final long gen = host.generation();
    String goal = intent;
    advisor.stage = AdvisorSession.Stage.GENERATING;

    advisorPanel();
    model.reply(
        host.editorPackage(),
        advisor.person,
        advisor.context(),
        goal,
        style,
        (out, error) -> {
          if (!advisor.valid(token, host.generation()) || gen != host.generation()) return;
          if (error != null) {
            if (!replyHistory.replies().isEmpty()) {
              advisor.stage = AdvisorSession.Stage.RESULTS;
              Ui.toast(this, error);
            } else advisor.fail(error);
            advisorPanel();
            return;
          }
          replyHistory.accept(out, style, !style.isEmpty());
          advisor.stage = AdvisorSession.Stage.RESULTS;
          advisorPanel();
        });
  }

  private void applyReply(String value) {
    MemoryStore.Item p = app.store.get(advisor.person);
    if (p == null || p.deleted || (!p.aux.isEmpty() && !p.aux.equals(host.editorPackage()))) {
      advisor.fail("人物范围已变化，请重新确认");
      advisorPanel();
      return;
    }
    if (protectedInsert(value)) {
      history("采纳的军师草稿", value, "advisor");
      advisor.cancel();
      model.cancel();
      typing();
    }
  }
}
