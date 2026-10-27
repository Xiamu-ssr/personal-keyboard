package app.doudou.ime.input;

import android.content.Context;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.input.engine.PinyinEngine;
import java.util.*;
import java.util.function.Consumer;

/** Recycled word grid over a complete, immutable decoder query. All pages remain reachable. */
public final class CandidatePanel extends LinearLayout {
  public static final int PAGE_SIZE = 48;
  private final PinyinEngine.Query query;
  private final Consumer<PinyinEngine.Word> choose;
  private final GridView grid;
  private final TextView pageLabel, previous, next;
  private final BaseAdapter adapter;
  private List<PinyinEngine.Word> words = Collections.emptyList();
  private int page;

  public CandidatePanel(Context context, String raw, PinyinEngine.Query query, Runnable back,
      Consumer<PinyinEngine.Word> choose) {
    super(context);
    this.query = query;
    this.choose = choose;
    setOrientation(VERTICAL);
    setBackgroundColor(Ui.keyboard());
    LinearLayout header = Ui.row(context);
    header.addView(app.doudou.ime.design.Icons.button(context,
        app.doudou.ime.R.drawable.ic_arrow_back, "返回键盘", back), new LayoutParams(Ui.dp(context, 48), -1));
    TextView spelling = Ui.text(context, raw, 13, Ui.sub());
    spelling.setSingleLine(true);
    spelling.setEllipsize(android.text.TextUtils.TruncateAt.START);
    spelling.setContentDescription(query.continuation ? "联想候选" : "正在输入拼音：" + raw);
    spelling.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
    Ui.pad(context, spelling, 12, 0);
    header.addView(spelling, new LayoutParams(0, -1, 1));
    addView(header, new LayoutParams(-1, Ui.dp(context, 48)));

    grid = new GridView(context);
    grid.setNumColumns(GridView.AUTO_FIT);
    grid.setColumnWidth(Ui.dp(context, 92));
    grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    grid.setHorizontalSpacing(Ui.dp(context, 4));
    grid.setVerticalSpacing(Ui.dp(context, 4));
    grid.setPadding(Ui.dp(context, 5), Ui.dp(context, 4), Ui.dp(context, 5), Ui.dp(context, 4));
    grid.setClipToPadding(false);
    grid.setContentDescription(query.continuation ? "联想候选词网格" : "拼音候选词网格");
    adapter = new BaseAdapter() {
      public int getCount() { return words.size(); }
      public Object getItem(int position) { return words.get(position); }
      public long getItemId(int position) { return (long) page * PAGE_SIZE + position; }

      public View getView(int position, View recycled, ViewGroup parent) {
        PinyinEngine.Word word = words.get(position);
        TextView cell = recycled instanceof TextView ? (TextView) recycled
            : Ui.text(context, "", 20, Ui.ink());
        cell.setText(word.text);
        cell.setGravity(Gravity.CENTER);
        cell.setMinHeight(Ui.dp(context, 48));
        cell.setPadding(Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10));
        // A long phrase wraps within its cell and is never ellipsized into an unselectable fragment.
        cell.setSingleLine(false);
        cell.setMaxLines(Integer.MAX_VALUE);
        cell.setEllipsize(null);
        cell.setBackground(Ui.pressable(context, Ui.surface(), 7));
        cell.setContentDescription(word.correctionCost > 0
            ? word.text + "，拼写纠正为 " + word.spelling : word.text);
        cell.setFocusable(true);
        cell.setOnClickListener(v -> CandidatePanel.this.choose.accept(word));
        return cell;
      }
    };
    grid.setAdapter(adapter);
    addView(grid, new LayoutParams(-1, 0, 1));

    LinearLayout footer = Ui.row(context);
    previous = Ui.button(context, "上一页", () -> showPage(page - 1));
    next = Ui.button(context, "下一页", () -> showPage(page + 1));
    pageLabel = Ui.text(context, "", 14, Ui.sub());
    pageLabel.setGravity(Gravity.CENTER);
    pageLabel.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
    footer.addView(previous, new LayoutParams(Ui.dp(context, 88), -1));
    footer.addView(pageLabel, new LayoutParams(0, -1, 1));
    footer.addView(next, new LayoutParams(Ui.dp(context, 88), -1));
    addView(footer, new LayoutParams(-1, Ui.dp(context, 48)));
    showPage(0);
  }

  private void showPage(int selected) {
    int count = Math.max(1, (query.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    page = Math.max(0, Math.min(selected, count - 1));
    words = query.page(page * PAGE_SIZE, PAGE_SIZE);
    adapter.notifyDataSetChanged();
    grid.setSelection(0);
    pageLabel.setText((page + 1) + " / " + count);
    pageLabel.setContentDescription("候选第 " + (page + 1) + " 页，共 " + count
        + " 页，" + query.size() + " 个词条");
    previous.setEnabled(page > 0);
    next.setEnabled(page + 1 < count);
    previous.setAlpha(previous.isEnabled() ? 1 : .4f);
    next.setAlpha(next.isEnabled() ? 1 : .4f);
  }
}
