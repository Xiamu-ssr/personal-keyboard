(function () {
  const data = JSON.parse(document.getElementById("spec-data").textContent);
  const el = (id) => document.getElementById(id);
  const text = (s) =>
    String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  function cost() {
    const minutes = Number(el("minutes").value),
      replies = Number(el("replies").value);
    el("minutes-label").textContent = minutes + " 分钟";
    el("replies-label").textContent = replies + " 次";
    const r = estimateCost({
      minutesPerDay: minutes,
      repliesPerDay: replies,
      asr: el("asr").value,
      model: el("text-model").value,
      freeHours: el("free-hours").checked ? 10 : 0,
    });
    el("cost-total").textContent = "¥" + r.total.toFixed(2);
    el("cost-details").innerHTML = [
      ["语音识别 · " + r.hours.toFixed(1) + " 小时", r.speech],
      ["军师回复", r.replies],
      ["语音整理", r.voice],
      ["异步分类与记忆", r.extraction],
    ]
      .map(
        ([label, value]) =>
          "<dt>" + label + "</dt><dd>¥" + value.toFixed(2) + "</dd>",
      )
      .join("");
  }
  ["minutes", "replies", "asr", "text-model", "free-hours"].forEach((id) =>
    el(id).addEventListener("input", cost),
  );
  cost();
  el("verification").innerHTML =
    "<p>" +
    text(data.verification.status || "pending") +
    "</p><ul>" +
    (data.verification.checks || [])
      .map((c) => "<li>" + text(c.name) + "：" + text(c.status) + "</li>")
      .join("") +
    "</ul>";
  let token = "",
    current = null;
  const local =
    location.protocol === "http:" &&
    ["127.0.0.1", "localhost"].includes(location.hostname);
  document.body.dataset.specMode = local ? "local" : "static";
  async function api(path, body) {
    const r = await fetch(path, {
      method: body ? "POST" : "GET",
      headers: { "Content-Type": "application/json", "X-Demo-Token": token },
      body: body ? JSON.stringify(body) : undefined,
    });
    const d = await r.json();
    if (!r.ok) throw Error(d.error || "本地服务异常");
    return d;
  }
  function status(label) {
    el("demo-status").textContent = label;
  }
  function showFrame(url) {
    el("native-placeholder").hidden = !!url;
    el("android-frame").hidden = !url;
    if (url) el("android-frame").src = url;
    else el("android-frame").removeAttribute("src");
  }
  async function refresh() {
    if (!local) {
      status(location.protocol === "file:" ? "静态阅览" : "云端阅览");
      [
        "device-select",
        "refresh-devices",
        "prepare-demo",
        "stop-demo",
        "enable-capture",
      ].forEach((id) => (el(id).disabled = true));
      el("demo-message").textContent =
        "这里可查看产品世界、关系契约和成本估算。原生 APK 已构建，真机验收留待设备解锁；演示回复为固定数据。";
      const placeholder = el("native-placeholder");
      placeholder.querySelector("h3").textContent = "原生 APK 演示留待本机验收";
      placeholder.querySelector("p").textContent =
        "真实 Android 画面需要连接本机设备，云端阅览不会访问或控制你的手机。";
      const link = placeholder.querySelector("a");
      link.dataset.doc = "interaction";
      link.textContent = "查看军师交互与高度变化";
      return;
    }
    try {
      const s = await api("/api/session");
      token = s.token;
      const d = await api("/api/demo/status");
      current = d;
      el("device-select").innerHTML = d.devices.length
        ? d.devices
            .map(
              (x) =>
                '<option value="' +
                text(x.serial) +
                '">' +
                text(x.label) +
                "</option>",
            )
            .join("")
        : '<option value="">没有已授权设备</option>';
      status(d.running ? "Android 画面已连接" : "本地服务在线");
      if (d.running) showFrame(d.viewerUrl);
    } catch (e) {
      status("本地服务离线");
      el("demo-message").textContent = e.message;
    }
  }
  async function action(which) {
    if (!local) return;
    const buttons = [el("prepare-demo"), el("stop-demo")];
    buttons.forEach((b) => (b.disabled = true));
    try {
      if (which === "start") {
        status("正在准备 Android…");
        const d = await api("/api/demo/start", {
          serial: el("device-select").value,
          enableCapture: el("enable-capture").checked,
        });
        showFrame(d.viewerUrl);
        status("真实 Android 画面");
        el("demo-message").textContent = d.message;
      } else {
        await api("/api/demo/stop", {});
        showFrame(null);
        status("演示已停止");
        el("demo-message").textContent =
          "已恢复本次临时切换的输入法/读屏设置，未关闭用户设备。";
      }
    } catch (e) {
      status("需要处理");
      el("demo-message").textContent = e.message;
    } finally {
      buttons.forEach((b) => (b.disabled = false));
    }
  }
  el("refresh-devices").addEventListener("click", refresh);
  el("prepare-demo").addEventListener("click", () => action("start"));
  el("stop-demo").addEventListener("click", () => action("stop"));
  refresh();
})();
