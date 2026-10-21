package app.doudou.ime.account;

import android.app.*;
import android.text.InputType;
import android.widget.*;
import app.doudou.ime.app.*;
import app.doudou.ime.design.Ui;
import org.json.*;

/** Phone identity and product entitlements, independent of supplier configuration. */
public final class AccountScreen extends Screen {
  private String challenge = "", phone = "", status = "";
  private long retryAt;
  private boolean busy;

  public AccountScreen(Activity a, KeyboardApp app, Navigator nav) {
    super(a, app, nav);
  }

  @Override
  protected void draw() {
    if (!app.cloud.loggedIn()) {
      LinearLayout card = section("登录豆豆", "本地输入无需登录。登录后使用语音、军师和账户权益。");
      EditText mobile = Ui.edit(activity, "手机号", phone);
      mobile.setInputType(InputType.TYPE_CLASS_PHONE);
      mobile.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
      card.addView(mobile);
      Ui.gap(activity, card, 12);
      TextView send =
          Ui.button(
              activity,
              retryAt > System.currentTimeMillis() ? "稍后重新获取" : "获取验证码",
              () -> {
                if (busy || System.currentTimeMillis() < retryAt) return;
                phone = mobile.getText().toString().trim();
                work(
                    () -> {
                      JSONObject r =
                          app.cloud.publicRequest(
                              "/v1/auth/challenges", new JSONObject().put("phone", phone));
                      challenge = r.getString("challengeId");
                      retryAt = System.currentTimeMillis() + 60000;
                      return "验证码已发送，有效期五分钟";
                    });
              });
      send.setEnabled(!busy);
      card.addView(send);
      if (!challenge.isEmpty()) {
        Ui.gap(activity, card, 16);
        EditText code = Ui.edit(activity, "六位验证码", "");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setFilters(
            new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(6)});
        card.addView(code);
        Ui.gap(activity, card, 12);
        TextView login =
            Ui.primary(
                activity,
                "登录",
                () -> {
                  String value = code.getText().toString();
                  work(
                      () -> {
                        app.cloud.login(challenge, value);
                        app.cloud.account();
                        return "登录成功";
                      });
                });
        login.setEnabled(!busy);
        card.addView(login);
      }
      add(card);
      caption("首次登录一次性赠送 10,000 转写字符和 20 次军师；不按月重置。");
      caption(status.isEmpty() ? "音频经豆豆 Cloud 转交语音服务；文字与记忆默认留在本机。" : status);
      return;
    }
    JSONObject account = app.cloud.cachedAccount(), ent = account.optJSONObject("entitlements");
    LinearLayout identity = section(app.cloud.phoneHint(), "豆豆账户");
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
    LinearLayout sync =
        section(
            "云同步",
            ent != null && ent.optBoolean("cloudSync") ? "已包含，资料在上传前加密。" : "免费账户保存在本机，付费套餐开放后可升级。");
    if (ent != null && ent.optBoolean("cloudSync")) {
      Switch enabled = new Switch(activity);
      enabled.setText("同步个人资料与文字历史");
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
      captionIn(sync, "换机恢复需要重新验证手机号。云端可通过账号恢复密钥，此方案不宣称端到端加密。");
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
                  return "已退出";
                }));
    action(
        management,
        "注销账户",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("注销豆豆账户？")
                .setMessage("云端账户将停用、登录失效、同步资料和恢复密钥删除；本机资料保留。此操作不可撤销，建议先导出。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "注销",
                    (d, w) ->
                        work(
                            () -> {
                              app.cloud.request(
                                  "/v1/account/delete",
                                  "POST",
                                  new JSONObject().put("confirm", true));
                              app.cloud.clearSession();
                              return "账户已注销";
                            }))
                .show());
    add(management);
    if (!status.isEmpty()) caption(status);
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
              });
        });
  }
}
