const base = document.querySelector("meta[name=api-base]").content,
  $ = (id) => document.getElementById(id);
let csrf = "",
  view = "overview";
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const date = (v) => new Date(v).toLocaleString("zh-CN", { hour12: false });
async function api(path, body, method) {
  const r = await fetch(base + "/admin/api/" + path, {
    method: method || (body ? "POST" : "GET"),
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
    body: body ? JSON.stringify(body) : undefined,
    credentials: "same-origin",
  });
  const b = await r.json();
  if (!r.ok) {
    if (r.status === 401) logged(false);
    throw Error(b.error?.message || "请求失败");
  }
  return b;
}
function logged(on) {
  $("login").hidden = on;
  $("workspace").hidden = !on;
  $("logout").hidden = !on;
}
function error(e) {
  $("notice").textContent = e.message;
}
function table(head, rows) {
  return rows.length
    ? '<div class="table-wrap"><table><thead><tr>' +
        head.map((x) => "<th>" + esc(x) + "</th>").join("") +
        "</tr></thead><tbody>" +
        rows
          .map(
            (row) =>
              "<tr>" + row.map((x) => "<td>" + x + "</td>").join("") + "</tr>",
          )
          .join("") +
        "</tbody></table></div>"
    : '<div class="empty">暂无记录</div>';
}
function edit(title, content, save) {
  $("save").textContent = '保存';
  $("editor-title").textContent = title;
  $("editor-fields").innerHTML = content;
  $("editor").showModal();
  $("save").onclick = async (e) => {
    e.preventDefault();
    $("save").disabled = true;
    try {
      await save();
      $("editor").close();
      await load();
    } catch (e) {
      error(e);
    } finally {
      $("save").disabled = false;
    }
  };
}
async function load() {
  try {
    $("notice").textContent = "";
    document
      .querySelectorAll("[data-view]")
      .forEach((b) => b.classList.toggle("selected", b.dataset.view === view));
    const data = await api(view);
    const c = $("content");
    if (view === "overview") {
      const succeeded = data.jobs
          .filter((j) => j.status === "succeeded")
          .reduce((n, j) => n + j.count, 0),
        failed = data.jobs
          .filter((j) => j.status === "failed")
          .reduce((n, j) => n + j.count, 0);
      c.innerHTML =
        '<h1>今天的豆豆。</h1><div class="metrics">' +
        [
          ["用户", data.users],
          ["24h 成功任务", succeeded],
          ["24h 失败任务", failed],
        ]
          .map(
            ([k, v]) =>
              '<div class="card"><small>' +
              k +
              "</small><strong>" +
              v +
              "</strong></div>",
          )
          .join("") +
        '</div><h2 class="section-title">服务状态</h2><div class="cards">' +
        Object.entries(data.readiness)
          .map(
            ([k, v]) =>
              '<div class="card"><h3>' +
              {
                account: "账号登录",
                asr: "实时语音",
                text: "文字模型",
                vision: "图文模型",
              }[k] +
              '</h3><span class="' +
              (v ? "ok" : "pending") +
              '">' +
              (v ? "已配置" : "待配置") +
              "</span></div>",
          )
          .join("") +
        '</div><h2 class="section-title">提示词版本</h2>' +
        table(
          ["用途", "版本"],
          data.prompts.map((p) => [
            esc(p.kind),
            "<code>" + esc(p.revision) + "</code>",
          ]),
        );
    }
    if (view === "users") {
      c.innerHTML =
        "<h1>用户与权益</h1>" +
        table(
          ["账号", "转写剩余", "军师剩余", "状态", "注册", "操作"],
          data.map((u) => [
            esc(u.handle),
            esc(u.asr_remaining),
            esc(u.advisor_remaining),
            esc(u.status),
            date(u.created_at),
            '<button data-grant="' +
              u.id +
              '">调整权益</button> <button data-status="' +
              u.id +
              '" data-next="' +
              (u.status === "active" ? "disabled" : "active") +
              '">' +
              (u.status === "active" ? "停用" : "启用") +
              "</button>",
          ]),
        );
    }
    if (view === 'invitations') {
      c.innerHTML = '<div class="toolbar"><h1>邀请试用</h1><button id="new-invitation">生成邀请码</button></div>' +
        '<p>每码限一个新账号，赠送权益按注册时的欢迎套餐发放。完整邀请码只显示一次。</p>' +
        table(['备注', '状态', '创建', '到期', '操作'], data.map(i => [
          esc(i.label || '—'), i.used_at ? '已使用' : i.revoked ? '已撤销' : new Date(i.expires_at) <= new Date() ? '已过期' : '待使用',
          date(i.created_at), date(i.expires_at), !i.used_at && !i.revoked ? '<button data-revoke="' + esc(i.id) + '">撤销</button>' : '—'
        ]));
      $('new-invitation').onclick = () => {
        edit('生成邀请码', '<label>数量<input id="invite-count" type="number" min="1" max="20" value="1"></label><label>有效天数<input id="invite-days" type="number" min="1" max="90" value="30"></label><label>备注<input id="invite-label" maxlength="80" placeholder="例如第一批试用"></label>', async () => {
          const result = await api('invitations', { count: Number($('invite-count').value), days: Number($('invite-days').value), label: $('invite-label').value });
          // Show after this editor closes; codes never enter storage or audit logs.
          setTimeout(() => {
            edit('请保存邀请码', '<p>关闭后无法再次查看，可在列表中撤销。每行一个。</p><textarea id="invitation-codes" readonly aria-label="一次性邀请码"></textarea><button id="copy-invitations" type="button">复制</button>', async () => {});
            $('invitation-codes').value = result.invitations.map(i => i.code).join('\n');
            $('copy-invitations').onclick = async () => {
              try { await navigator.clipboard.writeText($('invitation-codes').value); $('copy-invitations').textContent = '已复制'; }
              catch { $('invitation-codes').select(); }
            };
            $('save').textContent = '已保存';
          }, 0);
        });
        $('save').textContent = '生成';
      };
    }
    if (view === "jobs")
      c.innerHTML =
        "<h1>任务记录</h1>" +
        table(
          ["用户", "类型", "状态", "字符 / 次数", "模型", "错误码", "时间"],
          data.map((j) => [
            esc(j.handle),
            esc(j.kind),
            esc(j.status),
            esc(j.consumed_asr + " / " + j.consumed_advisor),
            esc(j.provider_model || "—"),
            esc(j.error_code || "—"),
            date(j.created_at),
          ]),
        );
    if (view === "audits")
      c.innerHTML =
        "<h1>审计</h1>" +
        table(
          ["操作", "目标", "详情", "时间"],
          data.map((a) => [
            esc(a.action),
            "<code>" + esc(a.target) + "</code>",
            esc(JSON.stringify(a.detail)),
            date(a.created_at),
          ]),
        );
    if (view === "plans") {
      c.innerHTML =
        '<div class="toolbar"><h1>版本化套餐</h1><button id="new-plan">新增草稿</button></div><div class="cards">' +
        data
          .map(
            (p) =>
              '<div class="card"><h3>' +
              esc(p.definition.name) +
              "</h3><small>" +
              esc(p.id) +
              " · " +
              esc(p.status) +
              "</small><p>" +
              esc(p.definition.limits?.asr_characters ?? "待定") +
              " 字符 / " +
              esc(p.definition.limits?.advisor_generations ?? "待定") +
              ' 次军师</p><button data-plan="' +
              esc(p.id) +
              '">' +
              (p.status === "draft" ? "编辑" : "查看") +
              "</button> " +
              (p.status === "draft"
                ? '<button data-publish="' + esc(p.id) + '">发布</button>'
                : "") +
              "</div>",
          )
          .join("") +
        "</div>";
      const show = (p) =>
        edit(
          "套餐草稿",
          '<textarea id="plan-json" aria-label="套餐配置">' +
            esc(JSON.stringify(p, null, 2)) +
            "</textarea>",
          () => api("plans", JSON.parse($("plan-json").value)),
        );
      $("new-plan").onclick = () =>
        show({
          id: "paid-v2",
          name: "付费套餐",
          version: 2,
          grantMode: "verified_purchase",
          limits: { asr_characters: 100000, advisor_generations: 200 },
          features: { cloud_sync: true },
          validDays: 30,
          price: { amountMinor: 0, currency: "CNY" },
        });
      c.querySelectorAll("[data-plan]").forEach(
        (b) =>
          (b.onclick = () => {
            const p = data.find((p) => p.id === b.dataset.plan);
            if (p.status === "draft") show(p.definition);
            else
              edit(
                "已发布套餐（只读）",
                "<textarea readonly>" +
                  esc(JSON.stringify(p.definition, null, 2)) +
                  "</textarea>",
                async () => {},
              );
          }),
      );
    }
  } catch (e) {
    error(e);
  }
}
$("login").onsubmit = async (e) => {
  e.preventDefault();
  try {
    const b = await api("login", { password: $("password").value });
    $("password").value = "";
    csrf = b.csrf;
    logged(true);
    await load();
  } catch (e) {
    error(e);
  }
};
$("logout").onclick = async () => {
  try {
    await api("logout", {});
  } finally {
    csrf = "";
    logged(false);
  }
};
$("tabs").onclick = (e) => {
  if (e.target.dataset.view) {
    view = e.target.dataset.view;
    load();
  }
};
$("content").onclick = async (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  try {
    if (b.dataset.revoke) {
      await api('invitations/revoke', { id: b.dataset.revoke });
      await load();
    }
    if (b.dataset.grant)
      edit(
        "调整权益",
        '<label>转写字符<input id="grant-asr" type="number" min="0" value="0"></label><label>军师次数<input id="grant-advisor" type="number" min="0" value="0"></label><label>云同步<select id="grant-sync"><option value="false">不包含</option><option value="true">包含</option></select></label><label>原因<input id="grant-reason" maxlength="300" placeholder="填写可追溯的调整原因"></label>',
        () =>
          api("grants", {
            userId: b.dataset.grant,
            reason: $("grant-reason").value,
            plan: {
              limits: {
                asr_characters: Number($("grant-asr").value),
                advisor_generations: Number($("grant-advisor").value),
              },
              features: { cloud_sync: $("grant-sync").value === "true" },
            },
          }),
      );
    if (b.dataset.publish) {
      if (confirm("发布后不可原地修改，确认发布？")) {
        await api("plans/publish", { id: b.dataset.publish });
        await load();
      }
    }
    if (b.dataset.status) {
      await api("users/status", {
        id: b.dataset.status,
        status: b.dataset.next,
      });
      await load();
    }
  } catch (e) {
    error(e);
  }
};
api("session")
  .then((b) => {
    csrf = b.csrf;
    logged(true);
    load();
  })
  .catch(() => logged(false));
