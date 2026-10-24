package app.doudou.ime.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** Two disposable local scenes. Sending here never contacts a real person or text model. */
public final class PracticeActivity extends Activity {
  private boolean comments;
  private LinearLayout messages;
  private ScrollView chat;
  private EditText editor;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    comments = "comments".equals(getIntent().getStringExtra("scene"));
    render();
  }

  private void render() {
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    Ui.pad(this, root, 12, 8);
    LinearLayout header = Ui.row(this);
    header.addView(
        Ui.icon(this, "返回", this::finish),
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, comments ? "评论练习" : "和小林聊天", 21, Ui.INK);
    title.setTypeface(null, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView switchScene =
        Ui.button(
            this,
            comments ? "聊天" : "评论",
            () -> {
              comments = !comments;
              render();
            });
    switchScene.setBackground(Ui.pressable(this, Ui.SURFACE, 18));
    header.addView(switchScene);
    root.addView(header);
    TextView demo = Ui.text(this, "本机练习", 12, Ui.SUB);
    demo.setGravity(Gravity.CENTER);
    Ui.pad(this, demo, 0, 8);
    root.addView(demo);
    chat =
        new ScrollView(this) {
          private boolean atLatest = true;

          @Override
          protected void onScrollChanged(int l, int t, int oldL, int oldT) {
            super.onScrollChanged(l, t, oldL, oldT);
            atLatest =
                getChildCount() == 0
                    || t + getHeight() >= getChildAt(0).getHeight() - Ui.dp(getContext(), 4);
          }

          @Override
          protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            // Keep the pre-layout anchor; child measurements may already describe the new viewport.
            boolean bottom = oldH == 0 || atLatest;
            super.onSizeChanged(w, h, oldW, oldH);
            if (bottom) post(() -> fullScroll(View.FOCUS_DOWN));
          }
        };
    chat.setFillViewport(true);
    chat.setContentDescription("示例聊天记录");
    messages = Ui.col(this);
    chat.addView(messages);
    root.addView(chat, new LinearLayout.LayoutParams(-1, 0, 1));
    if (comments) {
      LinearLayout post = Ui.card(this);
      post.addView(Ui.text(this, "周末小厨房", 18, Ui.APP_ACCENT));
      Ui.gap(this, post, 12);
      TextView story = Ui.text(this, "第一次做番茄鸡蛋面，没想到厨房比工位还忙。\n\n你们下班后最常做什么菜？", 20, Ui.INK);
      story.setLineSpacing(Ui.dp(this, 7), 1);
      post.addView(story);
      messages.addView(post);
      Ui.gap(this, messages, 18);
      for (String s :
          new String[] {
            "海盐|看起来好香！番茄要先炒出汁。",
            "小白|同款下班做饭人，我通常只剩洗锅的力气。",
            "阿橙|我也爱做这个，再放一点葱花。",
            "晚风|我的拿手菜是点外卖哈哈哈。",
            "一颗土豆|求详细做法，鸡蛋是先炒还是后放呀？",
            "周末小厨房|我先把鸡蛋炒好盛出来，最后再倒回去。",
            "月亮|看完饿了，感觉可以连吃三碗。",
            "小林|今晚也想试试，家里没有面条，用粉丝行吗？"
          }) {
        String[] v = s.split("\\|", 2);
        bubble(v[0], v[1], false);
      }
    } else {
      for (String s :
          new String[] {
            "小林|最近忙完了吗？",
            "我|项目昨天交了，终于能歇一下。",
            "小林|那找个时间一起吃饭吧。",
            "我|好啊，你想吃什么？",
            "小林|想吃火锅，听说附近新开了一家。",
            "我|我都行，别太辣就好。",
            "小林|可以点鸳鸯锅，我来找位置。",
            "我|那我下班后过去。",
            "小林|地铁口出来走五分钟就到。",
            "我|行，确定了把地址发我。",
            "小林|对了，听说你最近在做输入法？",
            "我|对，还在打磨语音和帮我回。",
            "小林|等做好了我也来试试。",
            "我|没问题，到时候叫你。",
            "小林|今晚有空吗？要不要一起吃个饭？"
          }) {
        String[] v = s.split("\\|", 2);
        bubble(v[0], v[1], v[0].equals("我"));
      }
    }
    LinearLayout composer = Ui.row(this);
    editor = Ui.edit(this, comments ? "说说你的看法…" : "消息", "");
    editor.setId(View.generateViewId());
    editor.setMaxLines(4);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    composer.addView(editor, new LinearLayout.LayoutParams(0, -2, 1));
    TextView send =
        Ui.primary(
            this,
            "发送",
            () -> {
              String value = editor.getText().toString().trim();
              if (value.isEmpty()) return;
              bubble("我", value, true);
              editor.setText("");
              chat.post(() -> chat.fullScroll(View.FOCUS_DOWN));
            });
    send.setMinWidth(Ui.dp(this, 64));
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, Ui.dp(this, 48));
    size.leftMargin = Ui.dp(this, 8);
    composer.addView(send, size);
    root.addView(composer);
    Ui.gap(this, root, 6);
    Ui.content(this, root);
    editor.requestFocus();
    chat.post(() -> chat.fullScroll(View.FOCUS_DOWN));
    editor.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT),
        250);
  }

  private void bubble(String name, String text, boolean own) {
    LinearLayout row = Ui.row(this);
    row.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
    Ui.pad(this, row, 0, 7);
    LinearLayout column = Ui.col(this);
    column.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
    if (!own) {
      TextView sender = Ui.text(this, name, 12, Ui.SUB);
      Ui.pad(this, sender, 3, 5);
      column.addView(sender);
    }
    TextView content = Ui.text(this, text, 17, Ui.INK);
    content.setLineSpacing(Ui.dp(this, 4), 1);
    Ui.pad(this, content, 14, 12);
    content.setBackground(Ui.bg(this, own ? Ui.APP_TINT : Ui.PANEL, 16));
    column.addView(content);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    if (own) lp.leftMargin = Ui.dp(this, 48);
    else lp.rightMargin = Ui.dp(this, 48);
    row.addView(column, lp);
    messages.addView(row);
  }
}
