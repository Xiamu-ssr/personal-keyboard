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
  let streamTimer = null,
    streams = new Map(),
    graphScale = 1;
  const flowData = () => data.workflows.find((f) => f.id === state.flow);
  function scaleGraph(value) {
    const f = flowData();
    graphScale = Math.max(0.28, Math.min(1.4, value));
    el("graph-stage").style.transform = `scale(${graphScale})`;
    el("graph-size").style.width = f.width * graphScale + "px";
    el("graph-size").style.height = f.height * graphScale + "px";
    el("zoom-level").textContent = Math.round(graphScale * 100) + "%";
  }
  function packetCards(channel) {
    const h = streams.get(channel.id);
    return h
      .visible()
      .map((p) => {
        const from = domains.get(p.from.split(".")[0]),
          to = domains.get(p.to.split(".")[0]);
        return `<button class="packet-card ${p.kind}" data-packet="${channel.id}:${p.sequence}" tabindex="${p.offset === 0 ? "0" : "-1"}" aria-hidden="${p.offset !== 0}" style="--offset:${p.offset};--distance:${Math.abs(p.offset)};--packet-color:${from.color}"><span>${esc(from.name)} <b>→</b> ${esc(to.name)}<i>#${String(p.sequence).padStart(3, "0")}</i></span><strong>${esc(p.title)}</strong><p>${esc(p.body)}</p></button>`;
      })
      .join("");
  }
  function renderStream(channel) {
    const host = el("stream-" + channel.id),
      h = streams.get(channel.id),
      viewport = host.querySelector(".packet-viewport");
    const template = document.createElement("template");
    template.innerHTML = packetCards(channel);
    const desired = [...template.content.children],
      keys = new Set(desired.map((n) => n.dataset.packet));
    for (const node of [...viewport.children])
      if (!keys.has(node.dataset.packet)) {
        node.style.setProperty("--offset", -3);
        node.style.opacity = 0;
        node._removalTimer = setTimeout(() => node.remove(), 660);
      }
    for (const next of desired) {
      let node = [...viewport.children].find(
        (n) => n.dataset.packet === next.dataset.packet,
      );
      if (node) {
        clearTimeout(node._removalTimer);
        node.setAttribute("style", next.getAttribute("style"));
        node.tabIndex = next.tabIndex;
        node.setAttribute("aria-hidden", next.getAttribute("aria-hidden"));
      } else {
        node = next;
        const target = node.getAttribute("style");
        node.style.setProperty("--offset", 3);
        node.style.opacity = 0;
        viewport.appendChild(node);
        requestAnimationFrame(() =>
          requestAnimationFrame(() => {
            if (node.isConnected) {
              node.setAttribute("style", target);
            }
          }),
        );
      }
    }
    host.querySelector(".stream-live").hidden = h.follow;
  }
  function graph() {
    clearInterval(streamTimer);
    streams = new Map();
    const f = flowData(),
      w = el("flow-wires");
    el("workflow-title").textContent = f.name;
    el("workflow-summary").textContent = f.summary;
    el("graph-stage").style.width = f.width + "px";
    el("graph-stage").style.height = f.height + "px";
    w.innerHTML = "";
    w.setAttribute("viewBox", `0 0 ${f.width} ${f.height}`);
    const defs = svg("defs", {}, w);
    for (const d of data.domains) {
      const marker = svg(
        "marker",
        {
          id: "arrow-" + d.id,
          viewBox: "0 0 10 10",
          refX: 9,
          refY: 5,
          markerWidth: 6,
          markerHeight: 6,
          orient: "auto",
        },
        defs,
      );
      svg("path", { d: "M0 0 10 5 0 10z", fill: d.color }, marker);
    }
    for (const c of f.channels) {
      for (const direction of ["ab", "ba"]) {
        const ps = c.paths[direction],
          from = direction === "ab" ? c.a : c.b,
          to = direction === "ab" ? c.b : c.a;
        const d =
          ps.length === 3
            ? `M${ps[0]} Q${ps[1]} ${ps[2]}`
            : ps.map((p, i) => (i ? "L" : "M") + p.join(" ")).join(" ");
        svg(
          "path",
          {
            d,
            class: "domain-wire",
            stroke: domains.get(from).color,
            "marker-end": "url(#arrow-" + from + ")",
            "data-channel": c.id,
            "data-direction": direction,
          },
          w,
        );
        svg(
          "path",
          {
            d,
            class: "edge-hit",
            role: "button",
            tabindex: 0,
            "aria-label": domains.get(from).name + " → " + domains.get(to).name,
            "data-edge": c.id,
          },
          w,
        );
      }
      streams.set(c.id, new PacketHistory(c.packets));
    }
    el("flow-nodes").innerHTML = f.groups
      .map((g) => {
        const d = domains.get(g.domain);
        return `<article class="domain-group" data-group="${g.domain}" style="--domain:${d.color};left:${g.rect[0]}px;top:${g.rect[1]}px;width:${g.rect[2]}px;height:${g.rect[3]}px"><button class="group-heading" data-domain="${d.id}"><span class="domain-icon"><svg viewBox="0 0 24 24">${icons[d.icon]}</svg></span><h3>${esc(d.name)}</h3><span>↗</span></button><div class="capabilities">${g.children.map((n) => `<div class="capability" data-capability="${g.domain}.${n.id}"><button class="capability-detail" data-step="${g.domain}.${n.id}"><strong>${esc(n.title)}</strong><span>${esc(n.summary)}</span></button>${(n.prompts || []).map((id) => `<button class="capability-prompt" data-prompt="${id}" aria-label="${esc(prompts.get(id).name)}">提示词 → 模型</button>`).join("")}</div>`).join("")}</div></article>`;
      })
      .join("");
    el("flow-streams").innerHTML = f.channels
      .map(
        (c) =>
          `<section class="packet-stream" id="stream-${c.id}" tabindex="0" aria-label="${esc(domains.get(c.a).name + "与" + domains.get(c.b).name + "的数据卡片，可滚动查看历史")}" data-stream="${c.id}" style="left:${c.stack[0] - c.stack[2] / 2}px;top:${c.stack[1] - c.stack[3] / 2}px;width:${c.stack[2]}px;height:${c.stack[3]}px"><div class="packet-viewport">${packetCards(c)}</div><div class="stream-controls"><button data-history="${c.id}" data-delta="-1" aria-label="${esc(c.id)} 较早数据">↑</button><span>模拟数据</span><button data-history="${c.id}" data-delta="1" aria-label="${esc(c.id)} 较晚数据">↓</button></div><button class="stream-live" data-live="${c.id}" hidden>回到最新</button></section>`,
      )
      .join("");
    for (const c of f.channels) {
      const host = el("stream-" + c.id),
        h = streams.get(c.id);
      host.addEventListener(
        "wheel",
        (e) => {
          e.preventDefault();
          e.stopPropagation();
          h.scroll(Math.sign(e.deltaY));
          renderStream(c);
        },
        { passive: false },
      );
      host.addEventListener("keydown", (e) => {
        if (["ArrowUp", "ArrowDown", "Home", "End"].includes(e.key)) {
          e.preventDefault();
          if (e.key === "End") h.latest();
          else if (e.key === "Home") {
            h.follow = false;
            h.index = 0;
          } else h.scroll(e.key === "ArrowUp" ? -1 : 1);
          renderStream(c);
        }
      });
      let touchY = null;
      host.addEventListener(
        "touchstart",
        (e) => {
          touchY = e.touches[0].clientY;
        },
        { passive: true },
      );
      host.addEventListener(
        "touchmove",
        (e) => {
          if (touchY === null) return;
          const delta = touchY - e.touches[0].clientY;
          if (Math.abs(delta) > 18) {
            e.preventDefault();
            h.scroll(delta > 0 ? 1 : -1);
            touchY = e.touches[0].clientY;
            renderStream(c);
          }
        },
        { passive: false },
      );
      host.addEventListener("touchend", () => (touchY = null));
    }
    el("workflow-tabs")
      .querySelectorAll("button")
      .forEach((b) => {
        b.classList.toggle("active", b.dataset.flow === f.id);
        b.setAttribute("aria-pressed", String(b.dataset.flow === f.id));
      });
    scaleGraph(
      innerWidth < 700
        ? 1
        : Math.min(1, (el("graph-scroll").clientWidth - 24) / f.width),
    );
    if (innerWidth < 700) el("graph-scroll").scrollLeft = 400;
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    el("pause-streams").textContent = reduced ? "播放数据" : "暂停动画";
    state.paused = reduced;
    streamTimer = setInterval(() => {
      if (
        state.paused ||
        state.section !== "business" ||
        !el("inspector").hidden ||
        document.hidden
      )
        return;
      for (const c of f.channels) {
        const host = el("stream-" + c.id);
        if (host.matches(":hover") || host.contains(document.activeElement))
          continue;
        streams.get(c.id).tick();
        renderStream(c);
      }
    }, 3000);
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
    const [owner, child] = id.split("."),
      group = flowData().groups.find((g) => g.domain === owner),
      n = group?.children.find((n) => n.id === child);
    if (!n) return;
    const d = domains.get(owner);
    const related = flowData()
      .channels.flatMap((c) => c.packets)
      .filter((p) => p.from === id || p.to === id);
    openPanel(
      n.title,
      n.summary,
      flowData().name + " / " + d.name,
      section("数据往来", list(related.map((p) => p.title + "：" + p.body))) +
        ((n.prompts || []).length
          ? section(
              "提示词与模型",
              '<div class="related">' +
                n.prompts
                  .map(
                    (p) =>
                      `<button data-prompt="${p}">${esc(prompts.get(p).name)} ↗</button>`,
                  )
                  .join("") +
                "</div><p>Cloud 组装 → " +
                (n.targets || [])
                  .map((t) =>
                    esc(
                      flowData()
                        .groups.flatMap((g) =>
                          g.children.map((c) => ({
                            ...c,
                            key: g.domain + "." + c.id,
                          })),
                        )
                        .find((c) => c.key === t)?.title,
                    ),
                  )
                  .join("、") +
                "</p>",
            )
          : "") +
        ((n.documents || []).length
          ? section("相关约定", docs(n.documents))
          : "") +
        section(
          "负责的域",
          `<div class="related"><button data-domain="${d.id}">${esc(d.name)} ↗</button></div>`,
        ),
      d,
    );
    hash("step/" + state.flow + "/" + id);
  }
  function edgePanel(id, sequence) {
    const c = flowData().channels.find((c) => c.id === id);
    if (!c) return;
    const p = sequence
      ? streams.get(c.id)?.items.find((p) => p.sequence === Number(sequence))
      : null;
    openPanel(
      p ? p.title : "两个域的数据往来",
      p ? p.body : "每个方向一条连线；卡片按时间顺序滚动。",
      flowData().name,
      p
        ? section(
            "传递路径",
            `<div class="related"><button data-step="${p.from}">${esc(p.from)} ↗</button><button data-step="${p.to}">${esc(p.to)} ↗</button></div>`,
          )
        : section(
            "消息约定",
            list(c.packets.map((p) => p.title + "：" + p.body)),
          ),
      domains.get(c.a),
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
    if (t.dataset.history) {
      const c = flowData().channels.find((c) => c.id === t.dataset.history);
      streams.get(c.id).scroll(Number(t.dataset.delta));
      renderStream(c);
      return;
    }
    if (t.dataset.live) {
      const c = flowData().channels.find((c) => c.id === t.dataset.live);
      streams.get(c.id).latest();
      renderStream(c);
      return;
    }
    if (t.dataset.packet) {
      const [id, sequence] = t.dataset.packet.split(":");
      edgePanel(id, sequence);
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
  el("zoom-in").addEventListener("click", () => scaleGraph(graphScale + 0.1));
  el("zoom-out").addEventListener("click", () => scaleGraph(graphScale - 0.1));
  el("zoom-fit").addEventListener("click", () =>
    scaleGraph((el("graph-scroll").clientWidth - 24) / flowData().width),
  );
  el("pause-streams").addEventListener("click", () => {
    state.paused = !state.paused;
    el("pause-streams").textContent = state.paused ? "播放数据" : "暂停动画";
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
