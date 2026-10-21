(function () {
  const data = JSON.parse(document.getElementById("spec-data").textContent),
    el = (id) => document.getElementById(id);
  const esc = (x) =>
    String(x ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  validateWorld(data);
  const domains = new Map(data.domains.map((d) => [d.id, d])),
    prompts = new Map(data.prompts.map((p) => [p.id, p]));
  const state = { section: "domains", flow: "voice", focus: null };
  const icons = {
    phone:
      '<rect x="6" y="2" width="12" height="20" rx="3"/><path d="M10 18h4"/>',
    keyboard:
      '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M6 9h.1M10 9h.1M14 9h.1M18 9h.1M6 12h.1M10 12h.1M14 12h.1M18 12h.1M7 16h10"/>',
    cloud:
      '<path d="M6 18a5 5 0 0 1-1-10 7 7 0 0 1 13-1 5.5 5.5 0 0 1 0 11H6z"/>',
    spark:
      '<path d="m12 2 2.8 6.5L21 11l-6.2 2.5L12 20l-2.8-6.5L3 11l6.2-2.5L12 2z"/>',
    admin:
      '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M3 9h18M9 9v12M13 13h4M13 17h4"/>',
  };
  const section = (name, body) =>
    `<section class="detail-section"><h3>${esc(name)}</h3>${body}</section>`;
  const list = (items) =>
    "<ul>" + items.map((s) => `<li>${esc(s)}</li>`).join("") + "</ul>";
  const docs = (ids) =>
    '<div class="related">' +
    ids
      .map(
        (id) =>
          `<button data-doc="${id}">${esc(data.sources[id].title)} ↗</button>`,
      )
      .join("") +
    "</div>";
  el("revision").textContent = data.meta.reviewedAt;
  el("domain-cards").innerHTML = data.domains
    .map(
      (d, i) =>
        `<article class="domain-card" style="--domain:${d.color}"><header class="card-heading"><span class="domain-icon"><svg viewBox="0 0 24 24" aria-hidden="true">${icons[d.icon]}</svg></span><h2>${esc(d.name)}</h2><span>${String(i + 1).padStart(2, "0")}</span></header><p class="card-summary">${esc(d.summary)}</p>${list(d.responsibilities)}<button data-domain="${d.id}">查看数据、代码与约定 ↗</button></article>`,
    )
    .join("");
  const welcome = data.catalog.plans.find(
    (p) => p.id === data.catalog.activeWelcomePlan,
  );
  el("free-characters").textContent =
    welcome.limits.asr_characters.toLocaleString("en-US");
  el("free-advisor").textContent = welcome.limits.advisor_generations;
  el("domain-legend").innerHTML = data.domains
    .filter((d) => d.id !== "admin")
    .map(
      (d) => `<span><i style="--domain:${d.color}"></i>${esc(d.name)}</span>`,
    )
    .join("");
  el("workflow-tabs").innerHTML = data.workflows
    .map(
      (f) =>
        `<button data-flow="${f.id}" aria-pressed="false">${esc(f.name)}</button>`,
    )
    .join("");
  const ns = "http://www.w3.org/2000/svg";
  function svg(tag, attrs, parent) {
    const n = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
    parent.appendChild(n);
    return n;
  }
  function labelPosition(e) {
    if (e.labelAt) return e.labelAt;
    const pairs = e.points
      .slice(1)
      .map((p, i) => ({
        a: e.points[i],
        b: p,
        len: Math.hypot(p[0] - e.points[i][0], p[1] - e.points[i][1]),
      }))
      .sort((a, b) => b.len - a.len);
    const p = pairs[0];
    if (p.len < 140) return null;
    return [(p.a[0] + p.b[0]) / 2, (p.a[1] + p.b[1]) / 2 - 15];
  }
  function graph() {
    const f = data.workflows.find((f) => f.id === state.flow),
      w = el("flow-wires");
    el("workflow-title").textContent = f.name;
    el("workflow-summary").textContent = f.summary;
    el("graph-stage").style.width = f.width + "px";
    el("graph-stage").style.height = f.height + "px";
    w.innerHTML = "";
    w.setAttribute("viewBox", `0 0 ${f.width} ${f.height}`);
    const defs = svg("defs", {}, w);
    for (const [k, color] of Object.entries({
      main: "#98aabb",
      optional: "#a08cc2",
      learning: "#7da99e",
    })) {
      const m = svg(
        "marker",
        {
          id: "arrow-" + k,
          viewBox: "0 0 10 10",
          refX: 8.5,
          refY: 5,
          markerWidth: 6,
          markerHeight: 6,
          orient: "auto",
        },
        defs,
      );
      svg("path", { d: "M0 0 10 5 0 10z", fill: color }, m);
    }
    for (const e of f.edges) {
      const path = e.points
          .map((p, i) => (i ? "L" : "M") + p.join(" "))
          .join(" "),
        g = svg("g", {}, w);
      svg(
        "path",
        {
          d: path,
          class: "flow-line " + e.kind,
          "marker-end": `url(#arrow-${e.kind})`,
        },
        g,
      );
      svg(
        "path",
        {
          d: path,
          class: "edge-hit",
          role: "button",
          tabindex: 0,
          "aria-label": e.label,
          "data-edge": e.id,
        },
        g,
      );
      const pos = labelPosition(e);
      if (pos) {
        const label = svg(
            "g",
            {
              class: "edge-label",
              "data-edge": e.id,
              transform: `translate(${pos[0]},${pos[1]})`,
            },
            g,
          ),
          width =
            Array.from(e.label).reduce(
              (n, c) => n + (/[^\x00-\xff]/.test(c) ? 12 : 6.5),
              0,
            ) + 16;
        svg("rect", { x: -width / 2, y: -12, width, height: 24, rx: 6 }, label);
        svg("text", {}, label).textContent = e.label;
      }
    }
    el("flow-nodes").innerHTML = f.nodes
      .map((n) => {
        const d = domains.get(n.domain);
        return `<article class="flow-node ${n.prompts.length ? "has-prompt" : ""}" data-node="${n.id}" style="--domain:${d.color};left:${n.rect[0]}px;top:${n.rect[1]}px;width:${n.rect[2]}px;height:${n.rect[3]}px"><button class="step-button" data-step="${n.id}"><span class="step-owner"><i></i>${esc(d.name)}</span><h3>${esc(n.title)}</h3><p class="step-summary">${esc(n.summary)}</p></button>${n.prompts.map((id) => `<button class="prompt-link" data-prompt="${id}">查看提示词 →</button>`).join("")}</article>`;
      })
      .join("");
    el("workflow-tabs")
      .querySelectorAll("button")
      .forEach((b) => {
        b.classList.toggle("active", b.dataset.flow === f.id);
        b.setAttribute("aria-pressed", String(b.dataset.flow === f.id));
      });
  }
  function hash(value) {
    history.replaceState(null, "", "#" + value);
  }
  function show(sectionName, flow = state.flow, update = true) {
    state.section = sectionName;
    state.flow = flow;
    el("domains-section").hidden = sectionName !== "domains";
    el("business-section").hidden = sectionName !== "business";
    el("sections")
      .querySelectorAll("button")
      .forEach((b) => {
        b.classList.toggle("active", b.dataset.section === sectionName);
        b.setAttribute(
          "aria-selected",
          String(b.dataset.section === sectionName),
        );
      });
    if (sectionName === "business") graph();
    if (update)
      hash(sectionName === "domains" ? "domains" : "business/" + flow);
  }
  function closePanel() {
    el("inspector").hidden = true;
    el("panel-shade").hidden = true;
    el("spec-main").inert = false;
    document.querySelector(".header").inert = false;
    if (state.focus?.isConnected) state.focus.focus();
    hash(state.section === "domains" ? "domains" : "business/" + state.flow);
  }
  function openPanel(title, subtitle, breadcrumb, body, domain) {
    if (el("inspector").hidden) state.focus = document.activeElement;
    el("breadcrumb").textContent = breadcrumb;
    el("breadcrumb").style.color = domain?.color || "";
    el("inspector-heading").innerHTML =
      `<h2>${esc(title)}</h2><p>${esc(subtitle)}</p>`;
    el("inspector-content").innerHTML = body;
    el("inspector-content").scrollTop = 0;
    el("inspector").hidden = false;
    el("panel-shade").hidden = false;
    el("spec-main").inert = true;
    document.querySelector(".header").inert = true;
    el("close-inspector").focus();
  }
  function documentPanel(id, promptId) {
    const doc = data.sources[id];
    if (!doc) return;
    const p = prompts.get(promptId);
    openPanel(
      doc.title,
      p
        ? "由 Cloud 组装，发送给图中对应的文字模型。"
        : "当前域或业务步骤的详细约定。",
      p ? p.source : "产品约定",
      `<article class="document-body">${doc.html}</article>`,
      domains.get("cloud"),
    );
    hash(promptId ? "prompt/" + promptId : "document/" + id);
  }
  function domainPanel(id) {
    const d = domains.get(id);
    if (!d) return;
    openPanel(
      d.name,
      d.summary,
      "基础域",
      section("职责", list(d.responsibilities)) +
        section("负责的数据", list(d.data)) +
        section(
          "代码归属",
          '<div class="code-list">' +
            d.code.map((p) => `<code>${esc(p)}</code>`).join("") +
            "</div>",
        ) +
        section("相关约定", docs(d.documents)),
      d,
    );
    hash("domain/" + id);
  }
  function stepPanel(id) {
    const f = data.workflows.find((f) => f.id === state.flow),
      n = f.nodes.find((n) => n.id === id);
    if (!n) return;
    const d = domains.get(n.domain);
    openPanel(
      n.title,
      n.summary,
      f.name + " / " + d.name,
      section("输入", `<p>${esc(n.input)}</p>`) +
        section("输出", `<p>${esc(n.output)}</p>`) +
        (n.rules.length ? section("处理约定", list(n.rules)) : "") +
        (n.prompts.length
          ? section(
              "由 Cloud 使用的提示词",
              '<div class="related">' +
                n.prompts
                  .map(
                    (p) =>
                      `<button data-prompt="${p}">${esc(prompts.get(p).name)} ↗</button>`,
                  )
                  .join("") +
                "</div>",
            )
          : "") +
        section(
          "负责的域",
          `<div class="related"><button data-domain="${d.id}">${esc(d.name)} ↗</button></div>`,
        ) +
        (n.documents.length ? section("相关约定", docs(n.documents)) : ""),
      d,
    );
    hash("step/" + state.flow + "/" + id);
  }
  function edgePanel(id) {
    const f = data.workflows.find((f) => f.id === state.flow),
      e = f.edges.find((e) => e.id === id);
    if (!e) return;
    const a = f.nodes.find((n) => n.id === e.from),
      b = f.nodes.find((n) => n.id === e.to);
    openPanel(
      e.label,
      e.note,
      f.name,
      section(
        "从哪里",
        `<p>${esc(domains.get(a.domain).name)} · ${esc(a.title)}</p>`,
      ) +
        section(
          "到哪里",
          `<p>${esc(domains.get(b.domain).name)} · ${esc(b.title)}</p>`,
        ) +
        section(
          "查看步骤",
          `<div class="related"><button data-step="${a.id}">来源步骤 ↗</button><button data-step="${b.id}">接收步骤 ↗</button></div>`,
        ),
    );
  }
  document.addEventListener("click", (e) => {
    const t = e.target.closest("button,[data-edge],.brand");
    if (!t) return;
    if (t.classList.contains("brand")) {
      e.preventDefault();
      closePanel();
      show("domains");
      return;
    }
    if (t.dataset.section) {
      closePanel();
      show(t.dataset.section);
      return;
    }
    if (t.dataset.flow) {
      show("business", t.dataset.flow);
      return;
    }
    if (t.dataset.domain) {
      domainPanel(t.dataset.domain);
      return;
    }
    if (t.dataset.step) {
      stepPanel(t.dataset.step);
      return;
    }
    if (t.dataset.edge) {
      edgePanel(t.dataset.edge);
      return;
    }
    if (t.dataset.prompt) {
      documentPanel(prompts.get(t.dataset.prompt).document, t.dataset.prompt);
      return;
    }
    if (t.dataset.doc) {
      e.preventDefault();
      documentPanel(t.dataset.doc);
      return;
    }
    if (t.dataset.close) el(t.dataset.close).close();
  });
  document.addEventListener("keydown", (e) => {
    if (
      e.target.classList.contains("edge-hit") &&
      ["Enter", " "].includes(e.key)
    ) {
      e.preventDefault();
      edgePanel(e.target.dataset.edge);
    }
    if (el("inspector").hidden) return;
    if (e.key === "Escape") {
      e.preventDefault();
      closePanel();
    }
    if (e.key === "Tab") {
      const items = [
        ...el("inspector").querySelectorAll("button,a,input,select"),
      ].filter((n) => !n.disabled && n.getClientRects().length);
      const first = items[0],
        last = items.at(-1);
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  });
  el("close-inspector").addEventListener("click", closePanel);
  el("panel-shade").addEventListener("click", closePanel);
  el("open-native").addEventListener("click", () => {
    el("native-dialog").showModal();
    el("native-dialog").dispatchEvent(new Event("spec:open"));
  });
  const route = location.hash.slice(1).split("/");
  if (route[0] === "business" && data.workflows.some((f) => f.id === route[1]))
    show("business", route[1], false);
  else show("domains", state.flow, false);
  if (route[0] === "domain") domainPanel(route[1]);
  if (route[0] === "document") documentPanel(route[1]);
  if (route[0] === "prompt" && prompts.has(route[1]))
    documentPanel(prompts.get(route[1]).document, route[1]);
  if (route[0] === "step" && data.workflows.some((f) => f.id === route[1])) {
    show("business", route[1], false);
    stepPanel(route[2]);
  }
})();
