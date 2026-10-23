package app.doudou.ime.account;

import android.app.*;
import android.content.*;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.LearningWorker;
import org.json.*;

/** Invited accounts, recovery and entitlements. No supplier or SMS configuration in the App. */
public final class AccountScreen extends Screen {
  private String mode = "login", handle = "", status = "", pendingRecovery = "";
  private boolean busy;

  public AccountScreen(Activity a, KeyboardApp app, Navigator nav) { super(a, app, nav); }

  private EditText field(LinearLayout parent, String hint, boolean secret) {
    EditText edit = Ui.edit(activity, hint, "");
    edit.setSingleLine(true);
    edit.setEnabled(!busy);
    edit.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
    edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    edit.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
    parent.addView(edit);
    Ui.gap(activity, parent, 10);
    return edit;
  }

  @Override protected void draw() {
    if (!app.cloud.loggedIn()) {
      String title = mode.equals("register") ? "创建豆豆账户" : mode.equals("recover") ? "找回账户" : "欢迎回来";
      LinearLayout card = section(title, mode.equals("register") ? "受邀试用，首次注册赠送 10,000 字符和 20 次军师。" : mode.equals("recover") ? "用恢复码设置新密码，旧设备将退出登录。" : "登录后，语音、军师和个人记忆随你使用。");
      Ui.gap(activity, card, 18);
      EditText name = field(card, "账号 · 3—32 位字母或数字", false);
      name.setText(handle);
      name.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(32) });
      EditText password = field(card, mode.equals("recover") ? "新密码 · 至少 12 个字符" : "密码 · 至少 12 个字符", true);
      EditText confirm = mode.equals("login") ? null : field(card, "再输入一次密码", true);
      EditText code = mode.equals("register") ? field(card, "邀请码", false) : mode.equals("recover") ? field(card, "恢复码", true) : null;
      String currentMode = mode;
      TextView submit = Ui.primary(activity, busy ? "正在连接…" : mode.equals("register") ? "创建账户" : mode.equals("recover") ? "重设密码并登录" : "登录", () -> {
        if (busy) return;
        handle = name.getText().toString().trim();
        final String value = password.getText().toString(), token = code == null ? "" : code.getText().toString().trim();
        if (confirm != null && !value.equals(confirm.getText().toString())) { Ui.toast(activity, "两次密码不一致"); return; }
        if (value.codePointCount(0, value.length()) < 12) { Ui.toast(activity, "密码至少 12 个字符，可以使用一句话"); return; }
        work(() -> {
          if (currentMode.equals("register")) pendingRecovery = app.cloud.register(handle, value, token);
          else if (currentMode.equals("recover")) pendingRecovery = app.cloud.recover(handle, value, token);
          else app.cloud.login(handle, value);
          app.cloud.account();
          app.personalization.activate();
          LearningWorker.enqueue(app);
          return "登录成功";
        });
      });
      submit.setEnabled(!busy);
      card.addView(submit);
      Ui.gap(activity, card, 12);
      LinearLayout choices = Ui.row(activity);
      for (String next : new String[] { "login", "register", "recover" }) {
        if (next.equals(mode)) continue;
        TextView item = Ui.button(activity, next.equals("login") ? "已有账号" : next.equals("register") ? "受邀注册" : "忘记密码", () -> {
          if (busy) return;
          handle = name.getText().toString().trim(); mode = next; status = ""; nav.refresh();
        });
        item.setEnabled(!busy);
        choices.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
      }
      card.addView(choices);
      add(card);
      caption(status.isEmpty() ? "普通打字无需登录。历史保存在手机，个人记忆随账户同步。" : status);
      return;
    }
    JSONObject account = app.cloud.cachedAccount(), ent = account.optJSONObject("entitlements");
    LinearLayout identity = section(app.cloud.handle(), "豆豆账户");
    action(
        identity,
        busy ? "正在刷新…" : "刷新账户用量",
        () ->
            work(
                () -> {
                  app.cloud.account();
                  return "用量已更新";
                }));
    add(identity);
    LinearLayout allowance = section("可用权益", "成功后扣量，失败和原任务重试不重复扣。");
    if (ent != null) {
      JSONObject asr = ent.optJSONObject("asr"), advisor = ent.optJSONObject("advisor");
      metric(allowance, "转写字符", asr == null ? 0 : asr.optLong("available"));
      metric(allowance, "军师次数", advisor == null ? 0 : advisor.optLong("available"));
    } else captionIn(allowance, "刷新后查看用量");
    add(allowance);
    LinearLayout memory = section("个人记忆", "热词、文风和人物随账户同步，普通输入使用本机缓存。");
    action(
        memory,
        "立即同步",
        () ->
            work(
                () -> {
                  app.personalization.sync();
                  return "个人记忆已同步";
                }));
    add(memory);
    LinearLayout sync =
        section(
            "历史云备份",
            ent != null && ent.optBoolean("cloudSync")
                ? "包含历史与截图，上传前加密。"
                : "历史与截图目前保存在手机；备份权益尚未开放。");
    if (ent != null && ent.optBoolean("cloudSync")) {
      Switch enabled = new Switch(activity);
      enabled.setText("备份历史与截图");
      enabled.setTextColor(Ui.INK);
      enabled.setChecked(app.cloud.preferences().getBoolean("syncEnabled", false));
      enabled.setOnCheckedChangeListener(
          (b, v) -> {
            app.cloud.preferences().edit().putBoolean("syncEnabled", v).apply();
            if (v)
              work(
                  () -> {
                    app.sync.run();
                    return "同步完成";
                  });
          });
      sync.addView(enabled);
      action(
          sync,
          "立即同步",
          () ->
              work(
                  () -> {
                    app.sync.run();
                    return "同步完成";
                  }));
      captionIn(sync, "换机恢复需要验证账号密码。云端可通过账号恢复密钥，此方案不宣称端到端加密。");
    }
    add(sync);
    LinearLayout management = section("账户管理", "退出不会清除本机资料。");
    action(
        management,
        "退出登录",
        () ->
            work(
                () -> {
                  app.cloud.logout();
                  app.personalization.activate();
                  return "已退出";
                }));
    action(management, "验证账号", () -> passwordPrompt("验证账号", password -> {
      app.cloud.reauthenticate(password);
      return "验证完成，可继续云备份或换机恢复";
    }));
    action(management, "修改密码", this::changePassword);
    action(management, "重新生成恢复码", () -> passwordPrompt("重新生成恢复码", password -> {
      pendingRecovery = app.cloud.request(app.cloud.userId(), "/v1/auth/recovery-code", "POST", new JSONObject().put("password", password)).getString("recoveryCode");
      return "旧恢复码已作废";
    }));
    action(management, "注销账户", () -> new AlertDialog.Builder(activity)
      .setTitle("注销豆豆账户？")
      .setMessage("云端账户、个人记忆和备份将删除；本机资料保留。此操作不可撤销。")
      .setNegativeButton("取消", null)
      .setPositiveButton("继续", (d, w) -> passwordPrompt("输入密码以注销", password -> {
        app.cloud.reauthenticate(password);
        app.cloud.request(app.cloud.userId(), "/v1/account/delete", "POST", new JSONObject().put("confirm", true));
        app.cloud.clearSession();
        app.personalization.activate();
        return "账户已注销";
      })).show());
    add(management);
    if (!status.isEmpty()) caption(status);
  }

  private interface PasswordTask { String run(String password) throws Exception; }

  private void passwordPrompt(String title, PasswordTask task) {
    if (busy) return;
    LinearLayout fields = Ui.col(activity);
    Ui.pad(activity, fields, 24, 12);
    EditText password = field(fields, "账号密码", true);
    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setView(fields)
      .setNegativeButton("取消", null).setPositiveButton("确认", (d, w) -> {
        String value = password.getText().toString(); password.setText(""); work(() -> task.run(value));
      }).create();
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  private void changePassword() {
    if (busy) return;
    LinearLayout fields = Ui.col(activity); Ui.pad(activity, fields, 24, 12);
    EditText current = field(fields, "当前密码", true), next = field(fields, "新密码 · 至少 12 个字符", true), confirm = field(fields, "再输入一次新密码", true);
    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("修改密码").setView(fields)
      .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
    dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String old = current.getText().toString(), password = next.getText().toString();
      if (!password.equals(confirm.getText().toString())) { Ui.toast(activity, "两次密码不一致"); return; }
      current.setText(""); next.setText(""); confirm.setText(""); dialog.dismiss();
      work(() -> { app.cloud.changePassword(old, password); return "密码已更新，其他设备已退出"; });
    }));
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  private void showRecovery() {
    if (pendingRecovery.isEmpty() || activity.isFinishing()) return;
    final String value = pendingRecovery; pendingRecovery = "";
    LinearLayout fields = Ui.col(activity); Ui.pad(activity, fields, 24, 12);
    TextView text = Ui.text(activity, value, 16, Ui.INK); text.setTextIsSelectable(true); fields.addView(text);
    Ui.gap(activity, fields, 16);
    fields.addView(Ui.button(activity, "复制恢复码", () -> {
      ClipData clip = ClipData.newPlainText("豆豆账户恢复码", value);
      PersistableBundle extras = new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
      clip.getDescription().setExtras(extras);
      ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
      Ui.toast(activity, "已复制，请保存在自己的密码管理器");
    }));
    AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("请保存恢复码")
      .setMessage("只显示这一次。忘记密码时可用它找回账户；请像密码一样保管。")
      .setView(fields).setCancelable(false).setPositiveButton("我已保存", null).create();
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  }

  private void metric(LinearLayout card, String name, long value) {
    Ui.gap(activity, card, 16);
    card.addView(
        Ui.text(activity, String.format(java.util.Locale.CHINA, "%,d", value), 30, Ui.INK));
    captionIn(card, name);
  }

  private interface Task {
    String run() throws Exception;
  }

  private void work(Task task) {
    if (busy) return;
    busy = true;
    status = "正在处理…";
    nav.refresh();
    app.network.execute(
        () -> {
          String result;
          try {
            result = task.run();
          } catch (Exception e) {
            result = e.getMessage() == null ? "连接失败，请稍后重试" : e.getMessage();
          }
          final String value = result;
          activity.runOnUiThread(
              () -> {
                busy = false;
                status = value;
                nav.refresh();
                showRecovery();
              });
        });
  }
}
