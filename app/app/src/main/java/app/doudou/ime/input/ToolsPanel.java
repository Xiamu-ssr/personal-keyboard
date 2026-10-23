package app.doudou.ime.input;

import android.content.*;
import android.view.*;
import android.widget.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.MemoryStore;

/** On-demand utility grid; no permanent row of product modes. */
public final class ToolsPanel extends PanelController {
  public ToolsPanel(EditorHost host) {
    super(host);
  }

  public void show() {
    commitComposing();
    reset("tools", 284);
    panelHeading("随手工具", this::typing);
    String[] labels = {
      "语音输入", "原地军师", "热词短语", "剪贴板", "移动光标", "撤销上屏", privateMode() ? "退出隐私" : "隐私输入", "设置"
    };
    String[] icons = {"语音", "军师", "词典", "剪贴板", "光标", "撤销", "隐私", "设置"};
    Runnable[] actions = {
      () -> {
        host.cancelAdvisor();
        newVoice();
      },
      () -> advisor(),
      this::phrases,
      this::clipboard,
      this::cursor,
      host::undoInsertion,
      host::togglePrivacy,
      () -> settings("")
    };
    for (int r = 0; r < 2; r++) {
      LinearLayout row = Ui.row(this);
      Ui.pad(this, row, 7, 3);
      for (int col = 0; col < 4; col++) {
        int at = r * 4 + col;
        View tile = Ui.tile(this, icons[at], labels[at], actions[at]);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5));
        row.addView(tile, lp);
      }
      root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    Ui.gap(this, root, 8);
  }

  private void newVoice() {
    host.openVoice();
  }

  private void advisor() {
    host.openAdvisor();
  }

  private void phrases() {
    reset("phrases", 284);
    panelHeading("热词与常用表达", this::show);
    ScrollView scroll = new ScrollView(this);
    LinearLayout list = Ui.col(this);
    Ui.pad(this, list, 14, 0);
    scroll.addView(list);
    java.util.List<MemoryStore.Item> words = app.store.list("word", 0);
    for (MemoryStore.Item item : words)
      list.addView(
          Ui.cell(
              this,
              "",
              item.title,
              item.body,
              "",
              () -> {
                insert(item.title);
                typing();
              }));
    if (words.isEmpty()) Ui.empty(this, list, "还没有热词", "在 App 的记忆页添加，也能用于语音整理。");
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(Ui.button(this, "管理我的热词", () -> settings("memory")));
  }

  private void clipboard() {
    if (privateMode()) {
      message("隐私输入", "隐私模式不读取剪贴板。", "", null);
      return;
    }
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    ClipData clip = clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      message("剪贴板是空的", "不会保存剪贴板历史。", "", null);
      return;
    }
    CharSequence raw = clip.getItemAt(0).getText();
    if (raw == null || raw.length() > 10000) {
      message("无法粘贴", "支持一万字以内的纯文字。", "", null);
      return;
    }
    String value = raw.toString();
    reset("clipboard", 284);
    panelHeading("本次剪贴板", this::show);
    ScrollView scroll = new ScrollView(this);
    TextView text = Ui.text(this, value, 16, Ui.INK);
    Ui.pad(this, text, 16, 8);
    scroll.addView(text);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(
        Ui.primary(
            this,
            "填入文字",
            () -> {
              insert(value);
              typing();
            }));
  }

  private void cursor() {
    reset("cursor", 284);
    panelHeading("移动光标", this::show);
    LinearLayout row = Ui.row(this);
    for (String title : new String[] {"←", "全选", "→"}) {
      TextView button =
          Ui.button(
              this,
              title,
              () -> {
                if (title.equals("全选")) host.selectAll();
                else host.moveCursor(title.equals("←") ? -1 : 1);
              });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 80), 1);
      lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), 0);
      row.addView(button, lp);
    }
    root.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(Ui.button(this, "退格", host::delete));
    Ui.gap(this, root, 6);
    root.addView(Ui.button(this, "返回键盘", this::typing));
  }
}
