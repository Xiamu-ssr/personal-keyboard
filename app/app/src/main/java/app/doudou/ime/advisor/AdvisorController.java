package app.doudou.ime.advisor;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.capture.CaptureService;
import app.doudou.ime.capture.CaptureDifference;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.EditorHost;
import app.doudou.ime.input.PanelController;
import app.doudou.ime.memory.MemoryStore;
import app.doudou.ime.model.TextModelClient;
import app.doudou.ime.voice.VoiceButton;
import java.util.*;
import org.json.*;

/** Screenshot → one multimodal request → three selectable replies. */
public final class AdvisorController extends PanelController {
  private final AdvisorSession advisor = new AdvisorSession();
  private final ReplyHistory replies = new ReplyHistory();
  private final TextModelClient model;
  private final Handler main = new Handler(Looper.getMainLooper());
  private String intent = "";
  private boolean captureEarlier, addingPage;
  private int[] lastSignature;
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
    captureEarlier=addingPage=false;lastSignature=null;
  }

  public void start(boolean supplement) {
    beginCapture(supplement,supplement);
  }

  private void beginCapture(boolean supplement,boolean moveEarlier) {
    if (privateMode()) {
      message("隐私输入", "此输入框不启用军师读屏。", "", null);
      return;
    }
    if (!model.configured()) {
      message("登录后使用军师", "普通打字不需要登录。", "去登录", () -> settings("account"));
      return;
    }
    if (CaptureService.current == null) {
      message("允许主动截屏", "点「帮我回」时截取当前屏幕，用于生成回复。", "允许截屏", () -> settings("permissions"));
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
      lastSignature=null;
    } else {
      advisor.stage = AdvisorSession.Stage.RESIZING;
    }
    addingPage=supplement;captureEarlier=moveEarlier;
    final int token = advisor.token();
    final long generation = host.generation();
    reset("capturing", 56);
    sheet();
    LinearLayout bar = Ui.row(this);
    bar.addView(
        Ui.text(this, supplement ? "正在补看更早的聊天…" : "正在截取当前屏幕…", 14, Ui.SUB),
        new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
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
        root.isLaidOut() && root.getHeight() <= Ui.dp(this, 70) + host.bottomInset() && top > 100;
    int next = measured && top == last ? stable + 1 : 0;
    if (next >= 3) {
      if(captureEarlier) {
        CaptureService.earlier(top,success->{
          if(!advisor.valid(token,host.generation()) || !host.mode().equals("capturing"))return;
          if(!success){advisor.fail("这页无法翻动，可返回聊天后手动翻页再试");panel();return;}
          captureEarlier=false;
          main.postDelayed(()->awaitLayout(token,generation,0,-1,0),420);
        });
        return;
      }
      advisor.stage = AdvisorSession.Stage.CAPTURING;
      final boolean prepend = addingPage;
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
            int[] signature=CaptureService.signature(bitmap);
            if(prepend && !CaptureDifference.changed(lastSignature,signature)) {
              bitmap.recycle();advisor.stage=AdvisorSession.Stage.RESULTS;panel();
              Ui.toast(this,"没有看到更早内容，已保留原回复");return;
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
                      app.histories.screenshot(savedHistory, bitmap, prepend);
                    }
                    final String history = savedHistory;
                    main.post(
                        () -> {
                          if (advisor.valid(token, host.generation())
                              && generation == host.generation()) {
                            advisor.historyId = history;
                            lastSignature=signature;
                            if(prepend)replies.prependImage();
                            advisor.image(image, prepend);
                            generate("");
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
    reset("advisor", 320);
    sheet();
    TextView handle=Ui.text(this,"",1,Ui.SUB);
    handle.setBackground(Ui.bg(this,0xff606263,2));
    LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(Ui.dp(this,30),Ui.dp(this,3));
    handleSize.gravity=Gravity.CENTER_HORIZONTAL;handleSize.topMargin=Ui.dp(this,6);handleSize.bottomMargin=Ui.dp(this,3);
    root.addView(handle,handleSize);
    LinearLayout heading = Ui.row(this);
    heading.addView(Ui.icon(this, "返回", () -> { cancel(); typing(); }), new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, advisor.personName.isEmpty() ? "怎么回" : "回复" + advisor.personName, 17, Ui.INK);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    View options = Ui.icon(this, "更多", () -> {});
    options.setOnClickListener(v -> options(v));
    heading.addView(options, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    if(advisor.pages()>0){
      boolean old=advisor.stage==AdvisorSession.Stage.RESULTS && replies.imageOffset()>0;
      TextView pages=Ui.text(this,(old ? advisor.pages()-replies.imageOffset() : advisor.pages())+(old ? "屏旧组" : " 屏"),12,Ui.SUB);
      Ui.pad(this,pages,8,0);heading.addView(pages,new LinearLayout.LayoutParams(-2,-2));
    }
    root.addView(heading);
    boolean waiting = advisor.stage == AdvisorSession.Stage.GENERATING;
    options.setEnabled(!waiting);
    if (waiting) {
      LinearLayout progress = Ui.col(this);
      progress.setGravity(Gravity.CENTER);
      VoiceButton animation = new VoiceButton(this, () -> {});
      progress.addView(animation, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 48)));
      animation.setState(VoiceButton.State.REWRITING);
      Ui.gap(this, progress, 18);
      TextView text = Ui.text(this, "看懂聊天，想想怎么回…", 16, Ui.INK);
      text.setGravity(Gravity.CENTER);
      Ui.pad(this, text, 20, 0);
      text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
      progress.addView(text);
      root.addView(progress, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    if (advisor.stage == AdvisorSession.Stage.ERROR) {
      LinearLayout error = Ui.col(this);
      Ui.pad(this, error, 20, 10);
      TextView detail = Ui.text(this, advisor.error, 15, Ui.SUB);
      detail.setLineSpacing(Ui.dp(this, 4), 1);
      error.addView(detail);
      Ui.gap(this, error, 16);
      error.addView(Ui.primary(this, advisor.retryCapture ? "重试截图" : "重新生成", () -> {
        if(advisor.retryCapture)beginCapture(addingPage,captureEarlier);else generate("");
      }));
      if (!replies.replies().isEmpty()) {
        Ui.gap(this, error, 8);
        error.addView(Ui.button(this, "保留上一组", () -> { advisor.stage = AdvisorSession.Stage.RESULTS; panel(); }));
      }
      root.addView(error, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    LinearLayout content = Ui.col(this);
    Ui.pad(this, content, 12, 0);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < replies.replies().size(); i++) {
      final String reply = replies.replies().get(i);
      LinearLayout card = Ui.row(this);
      card.setMinimumHeight(Ui.dp(this, 66));
      card.setBackground(Ui.pressable(this, Ui.SURFACE, 12));
      Ui.pad(this, card, 12, 10);
      TextView style = Ui.text(this, replies.style().isEmpty() ? styles[i] : replies.style(), 12, Ui.SUB);
      card.addView(style, new LinearLayout.LayoutParams(Ui.dp(this, 36), -2));
      TextView text = Ui.text(this, reply, 16, Ui.INK);
      text.setLineSpacing(Ui.dp(this, 2), 1);
      card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      card.setOnClickListener(v -> applyReply(reply));
      card.setOnLongClickListener(v -> { editLocal("修改这条回复", reply, this::applyReply); return true; });
      card.setContentDescription(styles[i] + "回复");
      card.setFocusable(true);
      content.addView(card);
      Ui.gap(this, content, 6);
    }
    LinearLayout footer = Ui.row(this);
    Ui.pad(this, footer, 12, 0);
    TextView earlier=Ui.button(this,advisor.pages()<AdvisorSession.MAX_PAGES ? "多看一屏 · 1次" : "已看三屏",()->start(true));
    earlier.setTextSize(13);earlier.setEnabled(advisor.pages()<AdvisorSession.MAX_PAGES);
    earlier.setContentDescription("多看一屏");earlier.setTooltipText("翻到更早的聊天，合并截图重新生成；成功扣 1 次");
    footer.addView(earlier, new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
    TextView more = Ui.button(this, "换一组 · 1次", () -> generate(""));
    more.setTextSize(14);
    more.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
    more.setBackground(Ui.pressable(this, android.graphics.Color.TRANSPARENT, 12));
    footer.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 48)));
    root.addView(footer);
  }

  private void options(View anchor) {
    PopupMenu menu = Ui.menu(this, anchor);
    menu.getMenu().add("聊天对象").setOnMenuItemClickListener(i -> { anchor.post(this::choosePerson); return true; });
    menu.getMenu().add("我想表达…").setOnMenuItemClickListener(i -> {
      anchor.post(() -> editLocal("我想表达", intent, value -> { intent = value; advisor.stage = AdvisorSession.Stage.READY; generate(""); })); return true;
    });
    menu.getMenu().add("重新截图").setOnMenuItemClickListener(i -> { anchor.post(() -> start(false)); return true; });
    if (replies.canBack()) menu.getMenu().add("上一组").setOnMenuItemClickListener(i -> { anchor.post(() -> { replies.back(); advisor.stage = AdvisorSession.Stage.RESULTS; panel(); }); return true; });
    menu.show();
  }

  private void sheet() {
    android.graphics.drawable.GradientDrawable surface=Ui.bg(this,0xff282a2c,0);
    float radius=Ui.dp(this,18);
    surface.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
    surface.setStroke(Ui.dp(this,1),0xff484b4d);
    root.setBackground(surface);root.setClipToOutline(true);
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
              generate("");
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
                generate("");
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
            replies.accept(texts, style, !replies.replies().isEmpty(), messages.toString());
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
          JSONArray messages=new JSONArray(replies.messages());
          for(int i=0;i<messages.length();i++){
            JSONObject message=messages.getJSONObject(i);
            if(message.has("image"))message.put("image",message.getInt("image")+replies.imageOffset());
          }
          app.histories.interpreted(advisor.historyId, messages);
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
