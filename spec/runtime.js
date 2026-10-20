(function () {
  const data = JSON.parse(document.getElementById("spec-data").textContent);
  const el = (id) => document.getElementById(id);
  const esc = (value) =>
    String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  const domains = new Map(data.domains.map((d) => [d.id, d]));
  const capabilities = new Map(
    data.domains.flatMap((d) =>
      d.chips.map((c) => [c.id, { ...c, owner: d.id }]),
    ),
  );
  const edges = new Map(data.edges.map((e) => [e.id, e]));
  const icons = {
    chat: '<path d="M5 4h14v12H9l-4 4V4z"/><path d="M9 8h6M9 12h4"/>',
    keyboard:
      '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M6 9h.1M10 9h.1M14 9h.1M18 9h.1M6 12h.1M10 12h.1M14 12h.1M18 12h.1M7 16h10"/>',
    scan: '<path d="M3 8V4h4M17 4h4v4M21 16v4h-4M7 20H3v-4M7 9h10M7 12h7M7 15h9"/>',
    spark:
      '<path d="m12 2 2.8 6.5L21 11l-6.2 2.5L12 20l-2.8-6.5L3 11l6.2-2.5L12 2zM20 2v4M18 4h4"/>',
    layers: '<path d="m12 3 10 5-10 5L2 8l10-5zM3 12l9 5 9-5M3 16l9 5 9-5"/>',
    rules:
      '<rect x="4" y="3" width="16" height="18" rx="3"/><path d="m8 8 1 1 2-2M13 8h3m-8 6 1 1 2-2M13 14h3"/>',
    sync: '<path d="M4 9a8 8 0 0 1 14-3l2 3M20 15a8 8 0 0 1-14 3l-2-3M20 4v5h-5M4 20v-5h5"/>',
  };
  const state = {
    view: "all",
    selection: null,
    tab: "business",
    focus: null,
    mobile: false,
    preview: {
      phase: "normal",
      draft: "周末我都可以",
      previous: null,
      style: null,
      variants: [0, 0, 0],
    },
  };
  const svgNS = "http://www.w3.org/2000/svg";
  function svg(tag, attributes, parent) {
    const n = document.createElementNS(svgNS, tag);
    for (const [k, v] of Object.entries(attributes)) n.setAttribute(k, v);
    parent?.appendChild(n);
    return n;
  }
  function icon(name) {
    return (
      '<svg viewBox="0 0 24 24" aria-hidden="true">' + icons[name] + "</svg>"
    );
  }
  const counts = validateWorld(data);
  el("revision").textContent =
    "v" + data.meta.version + " · " + data.meta.reviewedAt;
  el("world-count").textContent =
    `${counts.domains} 个域 · ${counts.capabilities} 项能力 · ${counts.relations} 条关系`;
  el("views").innerHTML = data.views
    .map(
      (v) =>
        `<button data-view="${v.id}" aria-pressed="${v.id === "all"}" class="${v.id === "all" ? "active" : ""}">${esc(v.name)}</button>`,
    )
    .join("");
  el("legend").innerHTML = Object.values(data.kinds)
    .map((k) => `<span><i style="--color:${k.color}"></i>${esc(k.name)}</span>`)
    .join("");
  for (const [i, d] of data.domains.entries()) {
    const node = document.createElement("article");
    node.className = "domain" + (d.chips.length ? "" : " simple");
    node.dataset.id = d.id;
    node.dataset.chipCount = d.chips.length;
    node.style.setProperty("--zone", data.zones[d.zone].color);
    node.innerHTML = `<button class="domain-heading" data-domain="${d.id}"><span class="domain-icon">${icon(d.icon)}</span><strong>${esc(d.name)}</strong><span class="domain-number">${String(i + 1).padStart(2, "0")}</span></button><p class="domain-role">${esc(d.role)}</p>${d.chips.length ? `<div class="domain-chips">${d.chips.map((c) => `<button class="domain-chip" data-chip="${c.id}"><strong>${esc(c.name)}</strong><span>${esc(c.desc)}</span></button>`).join("")}</div>` : ""}<span class="mobile-chip-count">${d.chips.length ? d.chips.length + " 项能力" : esc(d.mobileHint || "可选连接")}</span>`;
    el("world").appendChild(node);
  }
  const defs = svg("defs", {}, el("wires"));
  for (const [id, k] of Object.entries(data.kinds)) {
    const m = svg(
      "marker",
      {
        id: "arrow-" + id,
        viewBox: "0 0 10 10",
        refX: 8.5,
        refY: 5,
        markerWidth: 6,
        markerHeight: 6,
        orient: "auto-start-reverse",
      },
      defs,
    );
    svg("path", { d: "M0 0 10 5 0 10z", fill: k.color }, m);
  }
  for (const edge of data.edges) {
    const color = data.kinds[edge.kind].color,
      g = svg(
        "g",
        { class: "edge", "data-edge-id": edge.id, style: "--wire:" + color },
        el("wires"),
      );
    svg(
      "path",
      {
        class: "wire" + (edge.external ? " external" : ""),
        stroke: color,
        "marker-end": `url(#arrow-${edge.kind})`,
        ...(edge.both ? { "marker-start": `url(#arrow-${edge.kind})` } : {}),
      },
      g,
    );
    svg(
      "path",
      {
        class: "wire-hit",
        role: "button",
        tabindex: 0,
        "aria-label":
          domains.get(edge.from).name +
          " → " +
          domains.get(edge.to).name +
          "：" +
          edge.name,
        "data-relation": edge.id,
      },
      g,
    );
    const label = svg(
      "g",
      { class: "wire-label", "data-relation": edge.id },
      g,
    );
    svg("rect", { rx: 6 }, label);
    svg("text", {}, label);
  }
  const labelLayer = svg("g", { id: "wire-labels" }, el("wires"));
  for (const edge of data.edges) {
    const wrapper = svg(
      "g",
      {
        class: "edge-label-group",
        "data-label-edge-id": edge.id,
        style: "--wire:" + data.kinds[edge.kind].color,
      },
      labelLayer,
    );
    wrapper.appendChild(
      el("wires").querySelector(`[data-edge-id="${edge.id}"] .wire-label`),
    );
  }
  function sizeMap() {
    state.mobile = matchMedia("(max-width:760px)").matches;
    const containerWidth = el("map-viewport").clientWidth;
    let w, h, scale;
    if (state.mobile) {
      w = containerWidth;
      h = 450;
      scale = 1;
    } else {
      w = data.layout.width;
      h = data.layout.height;
      scale = Math.min(
        (containerWidth - 28) / w,
        Math.max(620, innerHeight - 235) / h,
        1.15,
      );
    }
    Object.assign(el("stage-box").style, {
      width: w * scale + "px",
      height: h * scale + "px",
    });
    Object.assign(el("world").style, {
      width: w + "px",
      height: h + "px",
      transform: `scale(${scale})`,
    });
    for (const d of data.domains) {
      const node = el("world").querySelector(`[data-id="${d.id}"]`);
      const r = state.mobile
        ? d.mobile.map((v, i) => (v * (i % 2 ? h : w)) / 100)
        : d.rect;
      Object.assign(node.style, {
        left: r[0] + "px",
        top: r[1] + "px",
        width: r[2] + "px",
        height: r[3] + "px",
      });
      node.querySelector(".domain-heading strong").textContent =
        state.mobile && d.id === "rules" ? "业务约定" : d.name;
    }
    drawWires();
  }
  function rect(id) {
    const n = el("world").querySelector(`[data-id="${id}"]`);
    return {
      x: n.offsetLeft,
      y: n.offsetTop,
      w: n.offsetWidth,
      h: n.offsetHeight,
    };
  }
  function port(r, side, offset = 0.5) {
    if (side === "top") return { x: r.x + r.w * offset, y: r.y };
    if (side === "bottom") return { x: r.x + r.w * offset, y: r.y + r.h };
    if (side === "left") return { x: r.x, y: r.y + r.h * offset };
    return { x: r.x + r.w, y: r.y + r.h * offset };
  }
  function drawWires() {
    el("wires").setAttribute(
      "viewBox",
      `0 0 ${el("world").offsetWidth} ${el("world").offsetHeight}`,
    );
    for (const e of data.edges) {
      const a = rect(e.from),
        b = rect(e.to);
      let [out, incoming] = e.ports;
      if (state.mobile && e.id === "e_inference") {
        out = "top";
        incoming = "bottom";
      }
      if (state.mobile && e.id === "e_identity") {
        out = "right";
        incoming = "top";
      }
      const offset = state.mobile && e.id === "e_inference" ? 0.5 : e.offset;
      const p = port(a, out, offset),
        q = port(b, incoming, offset),
        distance = Math.hypot(q.x - p.x, q.y - p.y),
        bend = Math.min(state.mobile ? 36 : 80, Math.max(15, distance * 0.45));
      const delta = {
        top: [0, -bend],
        bottom: [0, bend],
        left: [-bend, 0],
        right: [bend, 0],
      };
      const v = delta[out],
        z = delta[incoming];
      let d = `M${p.x},${p.y} C${p.x + v[0]},${p.y + v[1]} ${q.x + z[0]},${q.y + z[1]} ${q.x},${q.y}`;
      if (!state.mobile && e.via)
        d =
          "M" +
          p.x +
          "," +
          p.y +
          " L" +
          e.via.map((x) => x.join(",")).join(" L") +
          " L" +
          q.x +
          "," +
          q.y;
      if (state.mobile && e.mobileVia) {
        const w = el("world").offsetWidth,
          h = el("world").offsetHeight;
        d =
          "M" +
          p.x +
          "," +
          p.y +
          " L" +
          e.mobileVia
            .map(([x, y]) => [(x * w) / 100, (y * h) / 100].join(","))
            .join(" L") +
          " L" +
          q.x +
          "," +
          q.y;
      }
      const group = el("wires").querySelector(`[data-edge-id="${e.id}"]`),
        path = group.querySelector(".wire");
      path.setAttribute("d", d);
      group.querySelector(".wire-hit").setAttribute("d", d);
      const fraction = state.mobile
        ? e.mobileLabelFraction || 0.5
        : e.labelFraction || 0.5;
      const at = path.getPointAtLength(path.getTotalLength() * fraction);
      const label = el("wire-labels").querySelector(
          `[data-relation="${e.id}"]`,
        ),
        name = state.mobile ? e.short : e.name,
        labelWidth = name.length * (state.mobile ? 9 : 11) + 12;
      label.setAttribute("transform", `translate(${at.x},${at.y})`);
      const box = label.querySelector("rect");
      box.setAttribute("x", -labelWidth / 2);
      box.setAttribute("y", state.mobile ? -8 : -10);
      box.setAttribute("width", labelWidth);
      box.setAttribute("height", state.mobile ? 16 : 20);
      label.querySelector("text").textContent = name;
    }
  }
  function refreshView() {
    const focus = relationsForView(data, state.view),
      activeEdges = new Set(focus.edges),
      activeDomains = new Set(focus.domains);
    document.querySelectorAll("[data-view]").forEach((b) => {
      b.classList.toggle("active", b.dataset.view === state.view);
      b.setAttribute("aria-pressed", String(b.dataset.view === state.view));
    });
    for (const node of document.querySelectorAll(".domain"))
      node.classList.toggle("dimmed", !activeDomains.has(node.dataset.id));
    for (const g of document.querySelectorAll(".edge,.edge-label-group")) {
      const id = g.dataset.edgeId || g.dataset.labelEdgeId;
      g.classList.toggle("dimmed", !activeEdges.has(id));
      g.classList.toggle("active", state.view !== "all" && activeEdges.has(id));
    }
    const notes = {
      all: "点开域、能力或连线，查看业务与实现。",
      advisor:
        "军师：缩矮等待布局 → 本机截屏 / OCR → 确认人物并取记忆 → 生成三条建议 → 用户选中填入。",
      voice: "语音取用热词与文风，可信改词、改句再反哺；人物记忆也可按需取用。",
      learning:
        "输入与语音积累表达习惯；已知聊天或主动补充积累人物与事件，在设置中统一管理。",
      boundary:
        "只有虚线跨出本机：模型服务接收必要音频或文字；豆豆同步默认关闭，按类别与人物选择。原始聊天截图默认留在本机。",
    };
    el("view-note").textContent = notes[state.view];
  }
  function section(title, body) {
    return body
      ? `<section class="detail-section"><h3>${esc(title)}</h3>${body}</section>`
      : "";
  }
  function list(items) {
    return items?.length
      ? "<ul>" + items.map((s) => "<li>" + esc(s) + "</li>").join("") + "</ul>"
      : "";
  }
  function documents(ids) {
    return (
      '<div class="related">' +
      [...new Set(ids || [])]
        .map(
          (id) =>
            `<button data-doc="${id}">${esc(data.sources[id].title)} ↗</button>`,
        )
        .join("") +
      "</div>"
    );
  }
  function relationLinks(domainId, capabilityId) {
    return (
      '<div class="related">' +
      data.edges
        .filter(
          (e) =>
            (e.from === domainId || e.to === domainId) &&
            (!capabilityId ||
              !e.capabilities ||
              e.capabilities.includes(capabilityId)),
        )
        .map(
          (e) =>
            `<button data-relation="${e.id}">${esc(domains.get(e.from).name)} → ${esc(domains.get(e.to).name)}<br>${esc(e.name)}</button>`,
        )
        .join("") +
      "</div>"
    );
  }
  function objectCard(id) {
    const o = data.objects[id];
    return `<section class="object-card"><div class="object-title"><strong>${esc(o.name)}</strong><button data-domain="${o.owner}">归属 ${esc(domains.get(o.owner).name)} ↗</button></div><div class="fields">${Object.entries(
      o.fields,
    )
      .map(([k, v]) => `<code>${esc(k)}</code><span>${esc(v)}</span>`)
      .join("")}</div><p class="invariant">${esc(o.invariant)}</p></section>`;
  }
  function openPanel() {
    for (const dialog of document.querySelectorAll("dialog[open]"))
      dialog.close();
    if (el("inspector").hidden) state.focus = document.activeElement;
    el("inspector").hidden = false;
    el("panel-shade").hidden = false;
    el("world-main").inert = true;
    el("design-review").inert = true;
    document.querySelector(".header").inert = true;
  }
  function closePanel(restore = true) {
    el("inspector").hidden = true;
    el("panel-shade").hidden = true;
    el("world-main").inert = false;
    el("design-review").inert = false;
    document.querySelector(".header").inert = false;
    document
      .querySelectorAll(".selected")
      .forEach((n) => n.classList.remove("selected"));
    if (restore && state.focus?.isConnected) state.focus.focus();
  }
  function select(kind, id, tab = "business") {
    state.selection = { kind, id };
    state.tab = tab;
    openPanel();
    renderDetail();
    el("close-inspector").focus();
    const url = new URL(location.href);
    url.hash = kind + "/" + id;
    history.replaceState(null, "", url);
  }
  function renderDetail() {
    const selection = state.selection;
    if (!selection) return;
    el("inspector-tabs").hidden = ["document", "library"].includes(
      selection.kind,
    );
    el("inspector-tabs")
      .querySelectorAll("button")
      .forEach((b) => {
        b.classList.toggle("active", b.dataset.tab === state.tab);
        b.setAttribute("aria-pressed", String(b.dataset.tab === state.tab));
      });
    let heading = "",
      subtitle = "",
      breadcrumb = "",
      body = "";
    document
      .querySelectorAll(
        ".domain.selected,.domain-chip.selected,.edge.selected,.edge-label-group.selected",
      )
      .forEach((n) => n.classList.remove("selected"));
    if (selection.kind === "document") {
      const doc = data.sources[selection.id];
      heading = doc.title;
      breadcrumb = "约定与来源";
      subtitle = "世界图节点所引用的完整约定。";
      body = `<button class="back-link" id="back-library">← 全部约定</button><article class="document-body">${doc.html}</article>`;
    } else if (selection.kind === "library") {
      heading = "约定与来源";
      breadcrumb = "参考资料";
      subtitle =
        "规格主视图是产品世界；原始业务、提示词、调研和验收证据在这里查阅。";
      body =
        '<div class="library-list">' +
        Object.entries(data.sources)
          .map(
            ([id, doc]) =>
              `<button data-doc="${id}">${esc(doc.title)} ↗</button>`,
          )
          .join("") +
        "</div>";
    } else if (selection.kind === "edge") {
      const e = edges.get(selection.id);
      heading = e.name;
      subtitle = e.business;
      breadcrumb = domains.get(e.from).name + " → " + domains.get(e.to).name;
      el("wires")
        .querySelector(`[data-edge-id="${e.id}"]`)
        .classList.add("selected");
      el("wire-labels")
        .querySelector(`[data-label-edge-id="${e.id}"]`)
        .classList.add("selected");
      if (state.tab === "business")
        body =
          section("何时发生", `<p>${esc(e.trigger)}</p>`) +
          section("传递什么", `<p>${esc(e.payload)}</p>`) +
          section("成立条件", `<p class="invariant">${esc(e.guard)}</p>`) +
          section(
            "关联能力",
            '<div class="related">' +
              (e.capabilities || [])
                .map(
                  (id) =>
                    `<button data-chip="${id}">${esc(capabilities.get(id).name)} ↗</button>`,
                )
                .join("") +
              "</div>",
          );
      if (state.tab === "technical")
        body =
          section("实现约定", `<p>${esc(e.technical)}</p>`) +
          section(
            "边界",
            `<p>${esc(e.external ? "跨出本机的服务调用，图中用虚线表示。" : "本机或系统组件之间的关系。")}</p>`,
          ) +
          section("条件检查", `<p>${esc(e.guard)}</p>`);
      if (state.tab === "data")
        body = (e.objects || []).map(objectCard).join("");
      body += section(
        "两端职责",
        `<div class="related"><button data-domain="${e.from}">${esc(domains.get(e.from).name)} ↗</button><button data-domain="${e.to}">${esc(domains.get(e.to).name)} ↗</button></div>`,
      );
    } else {
      const item =
          selection.kind === "domain"
            ? domains.get(selection.id)
            : capabilities.get(selection.id),
        owner = selection.kind === "domain" ? item : domains.get(item.owner);
      heading = item.name;
      subtitle = item.role || item.desc;
      breadcrumb =
        data.zones[owner.zone].name +
        " / " +
        (selection.kind === "domain" ? "域" : owner.name);
      el("world")
        .querySelector(`[data-id="${owner.id}"]`)
        .classList.add("selected");
      if (selection.kind === "chip")
        el("world")
          .querySelector(`[data-chip="${item.id}"]`)
          .classList.add("selected");
      const rules = item.business || owner.business,
        tech = item.technical || owner.technical;
      if (state.tab === "business") {
        body = section("行为", list(rules));
        if (item.chips?.length)
          body += section(
            "域内能力",
            '<div class="capability-list">' +
              item.chips
                .map(
                  (c) =>
                    `<button data-chip="${c.id}"><strong>${esc(c.name)}</strong><small>${esc(c.desc)} ↗</small></button>`,
                )
                .join("") +
              "</div>",
          );
        if (item.preview || item.id === "keyboard")
          body += section("界面约定", previewMarkup());
        if (item.id === "keyboard_settings")
          body += section(
            "管理",
            '<div class="related"><button data-domain="models">模型服务 ↗</button><button data-chip="memory_words">热词与文风 ↗</button><button data-chip="memory_people">人物关系图与资料 ↗</button><button data-domain="doudou">豆豆同步 ↗</button></div>',
          );
        if (item.id === "memory_people")
          body += section(
            "人物资料示例",
            '<div class="object-card"><strong>林然 · 朋友</strong><p>偏好安静的地方</p><p class="invariant">来源：当前对话中的原话。演示人物，不是真实资料。</p><p>人物 → 偏好 / 事件 → 消息证据 → 修订记录</p></div>',
          );
      }
      if (state.tab === "technical") body = section("实现", list(tech));
      if (state.tab === "data")
        body =
          (item.objects || owner.objects || []).map(objectCard).join("") ||
          "<p>此能力沿用所属域的数据；查看相连关系中的对象契约。</p>";
      body +=
        section(
          "相连关系",
          relationLinks(owner.id, item.chips ? null : item.id),
        ) + section("引用约定", documents(item.documents || owner.documents));
    }
    el("breadcrumb").textContent = breadcrumb;
    el("inspector-heading").innerHTML =
      `<span class="scope-pill">${selection.kind === "edge" ? "关系契约" : selection.kind === "chip" ? "域内能力" : selection.kind === "domain" ? "域 / 规划规格" : "规格依据"}</span><h2>${esc(heading)}</h2><p>${esc(subtitle)}</p>`;
    el("inspector-content").innerHTML = body;
    el("inspector-content").scrollTop = 0;
  }
  const samples = [
    ["好呀，找家安静点的店，我们慢慢聊。", "可以呀，安静点的地方正合适。"],
    ["好呀，找个安静的小店，周末见。", "那就找家清静的小店，周末好好聊。"],
    [
      "可以。我们选个安静的地方，时间再确认。",
      "没问题，找家安静的店，具体时间再定。",
    ],
  ];
  function previewMarkup() {
    const p = state.preview;
    return `<p class="preview-label">交互示意 · 固定样例，非真实 APK / 模型 / 截图</p><div class="phone-preview" data-phase="${p.phase}"><div class="preview-head"><strong>林然</strong><span>演示对话</span></div><div class="preview-chat"><p>最近事情有点多。</p><p class="mine">周末一起吃饭，聊一聊？</p><p>好呀，想找个安静点的地方。</p></div><div class="preview-composer"><input id="preview-draft" aria-label="示意草稿" value="${esc(p.draft)}"><span>自行发送</span></div><div class="preview-keys"><div class="candidate-line"><span>${p.phase === "normal" ? "好的　好呀　可以" : p.phase === "shrunk" ? "正在等待布局 → 读取当前屏" : "同一意图 · 三种表达"}</span><button data-preview="${p.phase === "normal" ? "shrunk" : "normal"}">${p.phase === "normal" ? "✧ 军师" : "返回"}</button></div>${p.phase === "normal" ? ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"].map((row) => '<div class="key-row">' + [...row].map((k) => "<span>" + k + "</span>").join("") + "</div>").join("") : p.phase === "shrunk" ? '<div class="preview-progress">缩矮键区，让聊天露出更多</div>' : '<div class="preview-results">' + ["自然", "温和", "简洁"].map((s, i) => `<button data-reply="${i}"><strong>${s}${p.style === i ? " · 已选" : ""}</strong>${esc(samples[i][p.variants[i]])}</button>`).join("") + "</div>"}</div></div><div class="preview-controls"><button data-preview="normal" class="${p.phase === "normal" ? "active" : ""}">日常键区</button><button data-preview="shrunk" class="${p.phase === "shrunk" ? "active" : ""}">缩矮读屏</button><button data-preview="results" class="${p.phase === "results" ? "active" : ""}">展开结果</button></div>${p.style !== null ? `<div class="preview-controls"><button id="preview-more">只换“${["自然", "温和", "简洁"][p.style]}”</button><button id="preview-undo" ${p.previous === null ? "disabled" : ""}>撤销填入</button></div>` : ""}<p class="preview-label">点建议只改变示意草稿；这里没有发送功能，也不会访问手机。</p>`;
  }
  function rerenderPreview() {
    const current =
      el("inspector-content").querySelector(".phone-preview")?.parentElement;
    if (current) {
      const position = el("inspector-content").scrollTop;
      current.outerHTML = section("界面约定", previewMarkup());
      el("inspector-content").scrollTop = position;
    }
  }
  function openModal(id) {
    closePanel(false);
    el(id).showModal();
  }
  document.addEventListener("click", (event) => {
    const target = event.target.closest("button,[data-doc],[data-relation]");
    if (!target) return;
    if (target.dataset.view) {
      state.view = target.dataset.view;
      refreshView();
      return;
    }
    if (target.dataset.domain) {
      select("domain", target.dataset.domain);
      return;
    }
    if (target.dataset.chip) {
      const chip = capabilities.get(target.dataset.chip);
      if (chip.action === "cost") {
        openModal("cost-dialog");
        return;
      }
      if (chip.id.startsWith("rules_")) select("document", chip.documents[0]);
      else select("chip", chip.id);
      return;
    }
    if (target.dataset.relation) {
      select("edge", target.dataset.relation);
      return;
    }
    if (target.dataset.tab) {
      state.tab = target.dataset.tab;
      renderDetail();
      return;
    }
    if (target.dataset.doc) {
      event.preventDefault();
      select("document", target.dataset.doc);
      return;
    }
    if (target.dataset.close) {
      el(target.dataset.close).close();
      return;
    }
    if (target.dataset.preview) {
      state.preview.phase = target.dataset.preview;
      rerenderPreview();
      return;
    }
    if (target.dataset.reply !== undefined) {
      const i = Number(target.dataset.reply),
        p = state.preview;
      if (p.previous === null) p.previous = p.draft;
      p.style = i;
      p.draft = samples[i][p.variants[i]];
      rerenderPreview();
      return;
    }
    if (target.id === "preview-more") {
      const p = state.preview;
      p.variants[p.style] = 1 - p.variants[p.style];
      rerenderPreview();
      return;
    }
    if (target.id === "preview-undo") {
      state.preview.draft = state.preview.previous;
      state.preview.previous = null;
      rerenderPreview();
      return;
    }
    if (target.id === "back-library") select("library", "all");
  });
  document.addEventListener("input", (event) => {
    if (event.target.id === "preview-draft")
      state.preview.draft = event.target.value;
  });
  document.addEventListener("keydown", (event) => {
    if (
      event.target.classList.contains("wire-hit") &&
      ["Enter", " "].includes(event.key)
    ) {
      event.preventDefault();
      select("edge", event.target.dataset.relation);
    }
    if (!el("inspector").hidden) {
      if (event.key === "Escape") {
        event.preventDefault();
        closePanel();
      }
      if (event.key === "Tab") {
        const focusable = [
          ...el("inspector").querySelectorAll("button,a,input,select,textarea"),
        ].filter((n) => !n.disabled && n.getClientRects().length);
        const first = focusable[0],
          last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    }
  });
  el("close-inspector").addEventListener("click", () => closePanel());
  el("panel-shade").addEventListener("click", () => closePanel());
  el("open-native").addEventListener("click", () => openModal("native-dialog"));
  el("open-library").addEventListener("click", () => select("library", "all"));
  new ResizeObserver(sizeMap).observe(el("map-viewport"));
  window.addEventListener("resize", sizeMap);
  sizeMap();
  refreshView();
  const match = location.hash.match(/^#(domain|chip|edge|document)\/([\w-]+)$/);
  if (
    match &&
    ((match[1] === "domain" && domains.has(match[2])) ||
      (match[1] === "chip" && capabilities.has(match[2])) ||
      (match[1] === "edge" && edges.has(match[2])) ||
      (match[1] === "document" && data.sources[match[2]]))
  )
    select(match[1], match[2]);
})();
