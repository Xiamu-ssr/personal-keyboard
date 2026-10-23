package app.doudou.ime.input;

import android.content.*;
import android.view.View;
import android.widget.PopupMenu;
import app.doudou.ime.design.Ui;

/** Low-frequency actions stay in a small anchored menu, without leaving the editor. */
public final class KeyboardMenu {
  private final EditorHost host;
  public KeyboardMenu(EditorHost host) { this.host = host; }

  public void show(View anchor) {
    PopupMenu menu = Ui.menu(host.context(), anchor);
    menu.getMenu().add("粘贴文字").setOnMenuItemClickListener(item -> { anchor.post(this::paste); return true; });
    menu.getMenu().add("编辑文字").setOnMenuItemClickListener(item -> { anchor.post(host::editText); return true; });
    menu.getMenu().add("隐私输入").setCheckable(true).setChecked(host.privateMode())
        .setOnMenuItemClickListener(item -> { anchor.post(host::togglePrivacy); return true; });
    menu.getMenu().add("键盘设置").setOnMenuItemClickListener(item -> { anchor.post(() -> host.settings("input-settings")); return true; });
    menu.show();
  }

  private void paste() {
    if (host.privateMode()) { Ui.toast(host.context(), "隐私输入不读取剪贴板"); return; }
    ClipboardManager manager = (ClipboardManager) host.context().getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = manager.getPrimaryClip();
    CharSequence text = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).getText();
    if (text == null || text.length() == 0) { Ui.toast(host.context(), "剪贴板里没有文字"); return; }
    if (text.length() > 10000) { Ui.toast(host.context(), "这段文字太长，请在输入框中粘贴"); return; }
    host.commitComposing();
    host.insert(text.toString());
    host.typing();
  }
}
