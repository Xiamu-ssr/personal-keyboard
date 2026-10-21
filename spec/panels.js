(function () {
  const data = JSON.parse(document.getElementById("spec-data").textContent);
  const el = (id) => document.getElementById(id);
  const text = (s) =>
    String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
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
    el("native-video").pause();
    el("native-placeholder").hidden = !!url;
    el("native-gallery").hidden = !!url;
    el("android-frame").hidden = !url;
    if (url) el("android-frame").src = url;
    else el("android-frame").removeAttribute("src");
  }
  async function refresh() {
    if (!local) {
      status("实机截图");
      el("device-select").closest(".demo-controls").hidden = true;
      el("enable-capture").closest(".consent").hidden = true;
      el("demo-message").hidden = true;
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
  const video = el("native-video");
  const still = el("native-image");
  function playPreview() {
    if (!video.hidden && !matchMedia("(prefers-reduced-motion: reduce)").matches)
      video.play().catch(() => {});
  }
  el("native-gallery").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      video.pause();
      el("native-gallery").querySelectorAll("button").forEach((b) =>
        b.setAttribute("aria-pressed", String(b === button)),
      );
      const movie = button.dataset.video;
      video.hidden = !movie;
      still.hidden = !!movie;
      if (movie) {
        video.poster = "./demo/" + button.dataset.poster;
        video.src = "./demo/" + movie;
        video.setAttribute("aria-label", "真实 APK 录屏：" + button.querySelector("span").textContent);
        playPreview();
      } else {
        still.src = "./demo/" + button.dataset.image;
        still.alt = "豆豆输入法 · " + button.querySelector("span").textContent;
      }
    });
  });
  el("native-dialog").addEventListener("spec:open", () => {
    if (!local) playPreview();
  });
  el("native-dialog").addEventListener("close", () => {
    video.pause();
    if (location.hash === "#demo")
      history.replaceState(null, "", location.pathname + location.search);
  });
  if (location.hash === "#demo" && !local) {
    el("native-dialog").showModal();
    playPreview();
  }
})();
