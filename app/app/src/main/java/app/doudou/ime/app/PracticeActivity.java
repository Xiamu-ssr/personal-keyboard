package app.doudou.ime.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** Two disposable local scenes. Sending here never contacts a real person or text model. */
public final class PracticeActivity extends app.doudou.ime.design.ThemedActivity {
  private boolean comments;
  private String replyTo = "";
  private app.doudou.ime.practice.CommentScene commentScene;
  private LinearLayout messages;
  private ScrollView chat;
  private EditText editor;
  private final java.util.ArrayList<String> sent = new java.util.ArrayList<>();

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    comments = "comments".equals(getIntent().getStringExtra("scene"));
    if (state != null) {
      comments = state.getBoolean("comments", comments);
      replyTo = state.getString("replyTo", "");
      java.util.ArrayList<String> previous = state.getStringArrayList("sent");
      if (previous != null) sent.addAll(previous);
    }
    render();
    if (state != null) {
      editor.setText(state.getString("draft", ""));
      int start = Math.min(editor.length(), Math.max(0, state.getInt("selectionStart")));
      int end = Math.min(editor.length(), Math.max(start, state.getInt("selectionEnd")));
      editor.setSelection(start, end);
    }
  }

  private void render() {
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.bg());
    Ui.pad(this, root, 12, 8);
    LinearLayout header = Ui.row(this);
    header.addView(
        Ui.icon(this, "返回", this::finish),
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, comments ? "评论练习" : "和小林聊天", 21, Ui.ink());
    title.setTypeface(null, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView switchScene =
        Ui.button(
            this,
            comments ? "聊天" : "评论",
            () -> {
              comments = !comments;
              sent.clear();
              replyTo = "";
              render();
            });
    switchScene.setBackground(Ui.pressable(this, Ui.surface(), 18));
    header.addView(switchScene);
    root.addView(header);
    TextView demo = Ui.text(this, "本机练习", 12, Ui.sub());
    demo.setGravity(Gravity.CENTER);
    Ui.pad(this, demo, 0, 8);
    if (!comments) root.addView(demo);
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
            if (!comments && bottom) post(() -> fullScroll(View.FOCUS_DOWN));
          }
        };
    chat.setFillViewport(true);
    chat.setContentDescription(comments ? "示例评论区" : "示例聊天记录");
    messages = Ui.col(this);
    chat.addView(messages);
    root.addView(chat, new LinearLayout.LayoutParams(-1, 0, 1));
    if (comments) {
      commentScene = new app.doudou.ime.practice.CommentScene(this, (name, anchor) -> {
        replyTo = name;
        editor.setHint("回复 " + name);
        editor.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        anchor.postDelayed(() -> anchor.requestRectangleOnScreen(new android.graphics.Rect(0, 0, anchor.getWidth(), anchor.getHeight()), false), 300);
      });
      commentScene.populate(messages);
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
    for (String value : sent) { if (comments) commentScene.addOwn(messages, value); else bubble("我", value, true); }
    LinearLayout composer = Ui.row(this);
    editor = Ui.edit(this, comments ? (replyTo.isEmpty() ? "说说你的看法…" : "回复 " + replyTo) : "消息", "");
    editor.setPrivateImeOptions("doudou:practice");
    editor.setId(View.generateViewId());
    editor.setMaxLines(4);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    composer.addView(editor, new LinearLayout.LayoutParams(0, -2, 1));
    TextView send =
        Ui.primary(
            this,
            comments ? "评论" : "发送",
            () -> {
              String value = editor.getText().toString().trim();
              if (value.isEmpty()) return;
              String posted = comments && !replyTo.isEmpty() ? "回复 " + replyTo + "：" + value : value;
              sent.add(posted);
              if (comments) commentScene.addOwn(messages, posted); else bubble("我", value, true);
              editor.setText("");
              replyTo = "";
              editor.setHint(comments ? "说说你的看法…" : "消息");
              chat.post(() -> chat.fullScroll(View.FOCUS_DOWN));
            });
    send.setMinWidth(Ui.dp(this, 64));
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, Ui.dp(this, 48));
    size.leftMargin = Ui.dp(this, 8);
    composer.addView(send, size);
    root.addView(composer);
    Ui.gap(this, root, 6);
    Ui.content(this, root);
    if (comments) {
      root.setFocusableInTouchMode(true); root.requestFocus();
      ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(), 0);
      chat.post(() -> chat.scrollTo(0, 0));
    } else {
      editor.requestFocus();
      chat.post(() -> chat.fullScroll(View.FOCUS_DOWN));
      editor.postDelayed(
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT),
        250);
    }
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("comments", comments); state.putStringArrayList("sent", sent);
    state.putString("replyTo", replyTo);
    if (editor != null) {
      state.putString("draft", editor.getText().toString());
      state.putInt("selectionStart", editor.getSelectionStart());
      state.putInt("selectionEnd", editor.getSelectionEnd());
    }
    super.onSaveInstanceState(state);
  }

  private void bubble(String name, String text, boolean own) {
    LinearLayout row = Ui.row(this);
    row.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
    Ui.pad(this, row, 0, 7);
    LinearLayout column = Ui.col(this);
    column.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
    if (!own) {
      TextView sender = Ui.text(this, name, 12, Ui.sub());
      Ui.pad(this, sender, 3, 5);
      column.addView(sender);
    }
    TextView content = Ui.text(this, text, 17, Ui.ink());
    content.setLineSpacing(Ui.dp(this, 4), 1);
    Ui.pad(this, content, 14, 12);
    content.setBackground(Ui.bg(this, own ? Ui.appTint() : Ui.panel(), 16));
    column.addView(content);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    if (own) lp.leftMargin = Ui.dp(this, 48);
    else lp.rightMargin = Ui.dp(this, 48);
    row.addView(column, lp);
    messages.addView(row);
  }
}
