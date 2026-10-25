package app.doudou.ime.practice;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;

/** A local text-post comment thread, deliberately different from chat bubbles. */
public final class CommentScene {
  public interface Reply { void choose(String name, View anchor); }
  private final Context context;
  private final Reply reply;
  private TextView count;
  private int comments = 8;

  public CommentScene(Context c, Reply r) { context = c; reply = r; }

  public void populate(LinearLayout parent) {
    LinearLayout post = Ui.col(context); Ui.pad(context, post, 8, 16);
    LinearLayout author = Ui.row(context); author.setGravity(Gravity.CENTER_VERTICAL);
    author.addView(avatar("周", 40), new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
    LinearLayout who = Ui.col(context); Ui.pad(context, who, 12, 0);
    TextView name = Ui.text(context, "周末小厨房", 17, Ui.ink()); name.setTypeface(null, Typeface.BOLD);
    who.addView(name); who.addView(Ui.text(context, "我的笔记 · 昨天 18:30", 12, Ui.sub()));
    author.addView(who); post.addView(author); Ui.gap(context, post, 20);
    TextView title = Ui.text(context, "下班后的十五分钟厨房", 23, Ui.ink()); title.setTypeface(null, Typeface.BOLD); post.addView(title);
    Ui.gap(context, post, 12);
    TextView story = Ui.text(context, "第一次做番茄鸡蛋面，没想到厨房比工位还忙。\n\n番茄炒出汁，鸡蛋先盛出来，面快熟了再一起下锅。忙了一天，也想好好吃顿饭。\n\n你们下班后最常做什么菜？", 17, Ui.ink());
    story.setLineSpacing(Ui.dp(context, 4), 1); post.addView(story);
    Ui.gap(context, post, 14); post.addView(Ui.text(context, "#下班做饭  #一人食", 14, Ui.appAccent()));
    Ui.gap(context, post, 18); post.addView(Ui.text(context, "1,428 阅读   ·   342 赞   ·   86 收藏", 13, Ui.sub()));
    Ui.gap(context, post, 20); parent.addView(post); parent.addView(Ui.divider(context));
    count = Ui.text(context, "评论 8", 18, Ui.ink()); count.setTypeface(null, Typeface.BOLD); Ui.pad(context, count, 8, 18); parent.addView(count);
    comment(parent, "海盐", "看起来好香！番茄要先炒出汁。", false, false, "128");
    comment(parent, "小白", "同款下班做饭人，我通常只剩洗锅的力气。", false, false, "86");
    comment(parent, "阿橙", "我也爱做这个，再放一点葱花。", false, false, "42");
    comment(parent, "晚风", "我的拿手菜是点外卖哈哈哈。", false, false, "36");
    comment(parent, "一颗土豆", "求详细做法，鸡蛋是先炒还是后放呀？", false, false, "21");
    comment(parent, "周末小厨房", "回复一颗土豆：我先把鸡蛋炒好盛出来，最后再倒回去。", true, true, "12");
    comment(parent, "月亮", "看完饿了，感觉可以连吃三碗。", false, false, "9");
    comment(parent, "小林", "今晚也想试试，家里没有面条，用粉丝行吗？", false, false, "6");
  }

  public void addOwn(LinearLayout parent, String text) {
    comment(parent, "周末小厨房", text, true, false, "0");
    if (count != null) count.setText("评论 " + ++comments);
  }

  private TextView avatar(String name, int size) {
    TextView avatar = Ui.text(context, name.substring(0, 1), size == 40 ? 20 : 15, Ui.appAccent());
    avatar.setGravity(Gravity.CENTER); avatar.setBackground(Ui.bg(context, Ui.appTint(), size / 2));
    avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return avatar;
  }

  private void comment(LinearLayout parent, String name, String text, boolean author, boolean nested, String likes) {
    LinearLayout row = Ui.row(context); row.setGravity(Gravity.TOP); Ui.pad(context, row, nested ? 44 : 8, 12);
    row.addView(avatar(name, 30), new LinearLayout.LayoutParams(Ui.dp(context, 30), Ui.dp(context, 30)));
    LinearLayout content = Ui.col(context); Ui.pad(context, content, 12, 0);
    TextView label = Ui.text(context, name + (author ? " · 作者" : ""), 14, author ? Ui.appAccent() : Ui.sub()); label.setTypeface(null, Typeface.BOLD); content.addView(label);
    Ui.gap(context, content, 6);
    TextView message = Ui.text(context, text, 17, Ui.ink()); message.setLineSpacing(Ui.dp(context, 3), 1); content.addView(message);
    Ui.gap(context, content, 8); content.addView(Ui.text(context, "2 小时前 · 回复", 12, Ui.sub()));
    row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
    TextView number = Ui.text(context, likes + " 赞", 12, Ui.sub()); Ui.pad(context, number, 8, 0); row.addView(number);
    row.setBackground(Ui.pressable(context, android.graphics.Color.TRANSPARENT, 10)); row.setFocusable(true);
    row.setContentDescription("回复" + name + "的评论：" + text);
    row.setOnClickListener(v -> reply.choose(name, row)); parent.addView(row);
  }
}
