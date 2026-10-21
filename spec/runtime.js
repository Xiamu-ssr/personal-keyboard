(function () {
  const el = (id) => document.getElementById(id);
  const data = JSON.parse(el("spec-data").textContent);
  validateWorld(data);
  const esc = (v) =>
    String(v ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  const domains = new Map(data.domains.map((d) => [d.id, d]));
  const prompts = new Map(data.prompts.map((p) => [p.id, p]));
  const state = {
    section: "domains",
    flow: "voice",
    running: false,
    scale: 1,
    focus: null,
  };
  const motion = matchMedia("(prefers-reduced-motion: reduce)");
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
  const icon = (d) =>
    `<span class="domain-icon"><svg viewBox="0 0 24 24" aria-hidden="true">${icons[d.icon]}</svg></span>`;
  const section = (name, body) =>
    `<section class="detail-section"><h3>${esc(name)}</h3>${body}</section>`;
  const list = (items) =>
    `<ul>${items.map((s) => `<li>${esc(s)}</li>`).join("")}</ul>`;
  const docs = (ids) =>
    `<div class="related">${(ids || []).map((id) => `<button data-doc="${id}">${id.endsWith("-prompt") ? "0.4 源码 · " : ""}${esc(data.sources[id].title)} ↗</button>`).join("")}</div>`;
  const welcome = data.catalog.plans.find(
    (p) => p.id === data.catalog.activeWelcomePlan,
  );
  el("domain-cards").innerHTML =
    data.domains
      .map(
        (d, i) =>
          `<button class="domain-card" data-domain="${d.id}" style="--domain:${d.color}"><span class="card-heading">${icon(d)}<strong>${esc(d.name)}</strong><span>${String(i + 1).padStart(2, "0")}</span></span><p class="card-summary">${esc(d.summary)}</p><ul>${d.responsibilities.map((s) => `<li>${esc(s)}</li>`).join("")}</ul><span class="card-more">数据与约定 ↗</span></button>`,
      )
      .join("") +
    `<button class="domain-card offer-card" data-doc="commercial" style="--domain:#5b807c"><span class="card-heading"><strong>新账号赠送</strong><span>一次性</span></span><div class="offer-number"><strong>${welcome.limits.asr_characters.toLocaleString("en-US")}</strong><span>转写字符</span></div><div class="offer-number"><strong>${welcome.limits.advisor_generations}</strong><span>次军师生成</span></div><p>免费资料留本机 · 价格与权益由后台配置</p><span class="card-more">账户权益 ↗</span></button>`;
  el("workflow-tabs").innerHTML = data.businesses
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
  const pathData = (points) =>
    points.map((p, i) => (i ? "L" : "M") + p.join(" ")).join(" ");
  let world,
    timer,
    frame,
    wirePaths = new Map(),
    initialized = false,
    resizeFrame;
  const flowData = () => data.businesses.find((f) => f.id === state.flow);
  const nodes = nodeIndex(data);
  function stopMotion() {
    cancelAnimationFrame(frame);
    el("flow-wires")
      .querySelectorAll(".traveler")
      .forEach((n) => n.remove());
    el("flow-wires")
      .querySelectorAll(".active")
      .forEach((n) => n.classList.remove("active"));
    el("flow-wires")
      .querySelectorAll(".streaming")
      .forEach((n) => n.classList.remove("streaming"));
  }
  function pause() {
    state.running = false;
    el("business-section").dataset.playing = "false";
    clearTimeout(timer);
    stopMotion();
    el("pause-streams").textContent = world?.done ? "↺ 重播" : "▶ 播放";
  }
  function scaleGraph(value, anchor) {
    const host = el("graph-scroll"),
      f = data.world,
      old = state.scale;
    const center = anchor || [host.clientWidth / 2, host.clientHeight / 2];
    const focus = [
      (host.scrollLeft + center[0]) / old,
      (host.scrollTop + center[1]) / old,
    ];
    state.scale = Math.max(0.28, Math.min(1.6, value));
    el("graph-stage").style.transform = `scale(${state.scale})`;
    const w = f.width * state.scale,
      h = f.height * state.scale;
    el("graph-size").style.width = w + "px";
    el("graph-size").style.height = h + "px";
    el("graph-size").style.marginTop =
      Math.max(0, (host.clientHeight - h) / 2) + "px";
    host.scrollLeft = focus[0] * state.scale - center[0];
    host.scrollTop = focus[1] * state.scale - center[1];
    el("zoom-level").textContent = Math.round(state.scale * 100) + "%";
  }
  function fitGraph(initial = false) {
    const host = el("graph-scroll"),
      f = data.world;
    if (!host.clientWidth) return;
    const scale = Math.min(
      (host.clientWidth - 24) / f.width,
      (host.clientHeight - 24) / f.height,
      1.3,
    );
    scaleGraph(
      initial && host.clientWidth < 650 ? Math.max(0.76, scale) : scale,
    );
    host.scrollLeft = initial && host.clientWidth < 650 ? 342 * state.scale : 0;
    host.scrollTop = 0;
  }
  function packetCards(c) {
    const h = world.channels.get(c.id);
    return h
      .visible()
      .map((p) => {
        const from = domains.get(p.from),
          to = domains.get(p.to);
        return `<button class="packet-card" data-packet="${c.id}:${p.sequence}" tabindex="${p.offset === 0 ? 0 : -1}" aria-hidden="${p.offset !== 0}" style="--offset:${p.offset};--distance:${Math.abs(p.offset)};--packet-color:${from.color}"><span>${esc(from.shortName)} → ${esc(to.shortName)}<i>${p.proposal ? "待选 · " : ""}${p.sequence}</i></span><strong>${esc(p.title)}</strong><p>${esc(p.body)}</p></button>`;
      })
      .join("");
  }
  function renderStream(c) {
    const host = el("stream-" + c.id),
      h = world.channels.get(c.id),
      vp = host.querySelector(".packet-viewport");
    host.classList.toggle("empty", !h.items.length);
    const t = document.createElement("template");
    t.innerHTML = packetCards(c);
    const desired = [...t.content.children],
      keys = new Set(desired.map((n) => n.dataset.packet));
    for (const n of [...vp.children])
      if (!keys.has(n.dataset.packet)) {
        n.style.opacity = 0;
        n.style.setProperty("--offset", -3);
        clearTimeout(n._remove);
        n._remove = setTimeout(() => n.remove(), 400);
      }
    for (const next of desired) {
      let n = [...vp.children].find(
        (n) => n.dataset.packet === next.dataset.packet,
      );
      if (n) {
        clearTimeout(n._remove);
        n.setAttribute("style", next.getAttribute("style"));
        n.tabIndex = next.tabIndex;
        n.setAttribute("aria-hidden", next.getAttribute("aria-hidden"));
      } else {
        n = next;
        const target = n.getAttribute("style");
        n.style.setProperty("--offset", 3);
        n.style.opacity = 0;
        vp.appendChild(n);
        requestAnimationFrame(() =>
          requestAnimationFrame(() => {
            if (n.isConnected) n.setAttribute("style", target);
          }),
        );
      }
    }
    host.querySelector(".stream-live").hidden = h.follow;
    host.querySelector(".stream-count").textContent = h.items.length
      ? `${h.index + 1} / ${h.items.length}`
      : "尚无数据";
  }
  function drawWorld() {
    pause();
    const f = data.world;
    world = new ProductWorld(f, flowData());
    wirePaths = new Map();
    const w = el("flow-wires");
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
          markerWidth: 5,
          markerHeight: 5,
          orient: "auto",
        },
        defs,
      );
      svg("path", { d: "M0 0 10 5 0 10z", fill: d.color }, marker);
    }
    el("graph-stage").style.width = f.width + "px";
    el("graph-stage").style.height = f.height + "px";
    for (const c of f.channels)
      for (const direction of ["ab", "ba"]) {
        const from = domains.get(direction === "ab" ? c.a : c.b);
        const path = svg(
          "path",
          {
            d: pathData(laneRoute(f, c, direction)),
            class: "domain-lane",
            stroke: from.color,
            "marker-end": `url(#arrow-${from.id})`,
            "data-channel": c.id,
            "data-direction": direction,
          },
          w,
        );
        wirePaths.set(c.id + ":" + direction, path);
      }
    el("flow-nodes").innerHTML = f.groups
      .map((g) => {
        const d = domains.get(g.domain);
        return `<section class="domain-group" data-group="${d.id}" aria-label="${esc(d.name)}" style="--domain:${d.color};left:${g.rect[0]}px;top:${g.rect[1]}px;width:${g.rect[2]}px;height:${g.rect[3]}px"><button class="group-heading" data-domain="${d.id}">${icon(d)}<strong>${esc(d.name)}</strong><span>↗</span></button>${g.nodes
          .map((placed) => {
            const id = g.domain + "." + placed.id,
              n = nodes.get(id),
              r = placed.rect;
            return `<div class="world-node" data-node="${id}" style="left:${r[0]}px;top:${r[1]}px;width:${r[2]}px;height:${r[3]}px"><button class="node-body" data-open-node="${id}">${n.eyebrow ? `<small>${esc(n.eyebrow)}</small>` : ""}<strong>${esc(n.title)}</strong><span>${esc(n.summary)}</span><em data-status="${id}"></em></button></div>`;
          })
          .join(
            "",
          )}<span class="group-footer">${esc(g.footer)}</span></section>`;
      })
      .join("");
    el("flow-streams").innerHTML = f.channels
      .map(
        (c) =>
          `<section class="packet-stream empty" id="stream-${c.id}" tabindex="0" data-stream="${c.id}" aria-label="${esc(domains.get(c.a).name)}与${esc(domains.get(c.b).name)}数据历史" style="left:${c.stack[0] - c.stack[2] / 2}px;top:${c.stack[1] - c.stack[3] / 2}px;width:${c.stack[2]}px;height:${c.stack[3]}px"><div class="packet-viewport"></div><div class="stream-controls"><button data-history="${c.id}" data-delta="-1" aria-label="较早数据">↑</button><span class="stream-count">尚无数据</span><button data-history="${c.id}" data-delta="1" aria-label="较晚数据">↓</button></div><button class="stream-live" data-live="${c.id}" hidden>回到最新</button></section>`,
      )
      .join("");
    for (const c of f.channels) {
      const host = el("stream-" + c.id);
      host.addEventListener(
        "wheel",
        (e) => {
          e.preventDefault();
          e.stopPropagation();
          world.channels.get(c.id).scroll(Math.sign(e.deltaY));
          renderStream(c);
        },
        { passive: false },
      );
      let sy;
      host.addEventListener("pointerdown", (e) => {
        sy = e.clientY;
      });
      host.addEventListener("pointerup", (e) => {
        if (sy !== undefined && Math.abs(e.clientY - sy) > 15) {
          world.channels.get(c.id).scroll(e.clientY < sy ? 1 : -1);
          renderStream(c);
        }
        sy = undefined;
      });
      host.addEventListener("keydown", (e) => {
        if (!["ArrowUp", "ArrowDown", "Home", "End"].includes(e.key)) return;
        e.preventDefault();
        const h = world.channels.get(c.id);
        if (e.key === "End") h.latest();
        else if (e.key === "Home") {
          h.follow = false;
          h.index = 0;
        } else h.scroll(e.key === "ArrowUp" ? -1 : 1);
        renderStream(c);
      });
    }
    initialized = true;
    updateState();
    requestAnimationFrame(() => fitGraph(true));
  }
  function updateState() {
    el("business-section").dataset.playing = String(state.running);
    el("flow-nodes")
      .querySelectorAll(".world-node")
      .forEach((n) => {
        const value = world.status[n.dataset.node];
        n.classList.toggle("has-state", !!value);
        n.querySelector("em").textContent = value || "";
        n.classList.toggle(
          "current",
          !!world.current &&
            (world.current.node === n.dataset.node ||
              Object.hasOwn(world.current.status || {}, n.dataset.node)),
        );
      });
    el("world-event").textContent =
      world.current?.title || "点播放，看数据怎么走";
    el("world-event").title = world.current?.body || flowData().summary;
    el("event-count").textContent =
      `${world.cursor} / ${flowData().events.length}`;
    el("proposal-status").hidden = !world.current?.proposal;
    el("pause-streams").textContent = state.running
      ? "Ⅱ 暂停"
      : world.done
        ? "↺ 重播"
        : "▶ 播放";
  }
  function animateEdge(event) {
    stopMotion();
    if (state.running)
      for (const id of world.activeStreams)
        wirePaths.get(id)?.classList.add("streaming");
    const p = wirePaths.get(event.channel + ":" + event.direction);
    if (!p) return;
    p.classList.add("active");
    if (motion.matches) return;
    const color = domains.get(event.from).color;
    const particle = svg(
      "circle",
      { r: 4.5, fill: color, class: "traveler" },
      el("flow-wires"),
    );
    const length = p.getTotalLength(),
      start = performance.now(),
      duration = 650;
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration),
        a = p.getPointAtLength(t * length);
      particle.setAttribute("cx", a.x);
      particle.setAttribute("cy", a.y);
      if (t < 1) frame = requestAnimationFrame(tick);
      else particle.remove();
    };
    frame = requestAnimationFrame(tick);
  }
  function step() {
    const e = world.step();
    if (!e) {
      pause();
      return;
    }
    updateState();
    animateEdge(e);
    for (const c of data.world.channels) renderStream(c);
    if (world.done) {
      clearTimeout(timer);
      state.running = false;
      updateState();
    }
  }
  function schedule() {
    if (!state.running || document.hidden) return;
    const wait = flowData().events[world.cursor]?.delay ?? 900;
    timer = setTimeout(
      () => {
        if (!state.running) return;
        step();
        if (state.running) schedule();
      },
      Math.max(850, wait),
    );
  }
  function play() {
    if (world.done) drawWorld();
    state.running = true;
    updateState();
    if (!world.cursor) step();
    schedule();
  }
  function openPanel(title, summary, body, breadcrumb = "节点与数据") {
    pause();
    state.focus = true;
    el("breadcrumb").textContent = breadcrumb;
    el("inspector-heading").innerHTML =
      `<h2>${esc(title)}</h2>${summary ? `<p>${esc(summary)}</p>` : ""}`;
    el("inspector-content").innerHTML = body;
    el("panel-shade").hidden = false;
    el("inspector").hidden = false;
    el("close-inspector").focus();
  }
  function closePanel() {
    el("inspector").hidden = true;
    el("panel-shade").hidden = true;
    state.focus = null;
  }
  function openDomain(id) {
    const d = domains.get(id);
    if (!d) return;
    openPanel(
      d.name,
      d.summary,
      section("职责", list(d.responsibilities)) +
        section("持有的数据", list(d.data)) +
        section(
          "代码域",
          `<div class="code-list">${d.code.map((c) => `<code>${esc(c)}</code>`).join("")}</div>`,
        ) +
        docs(d.documents),
      "基础域",
    );
  }
  function openNode(id) {
    const n = nodes.get(id);
    if (!n) return;
    openPanel(
      n.title,
      n.summary,
      section("职责与数据", list(n.details)) +
        (world.status[id]
          ? section("本次示例", `<p>${esc(world.status[id])}</p>`)
          : "") +
        (n.prompts?.length
          ? section(
              "代理持有的提示词",
              n.prompts
                .map(
                  (p) =>
                    `<button class="prompt-link" data-prompt="${p}">${esc(prompts.get(p).name)} ↗</button>`,
                )
                .join(" "),
            )
          : "") +
        docs(n.documents),
      domains.get(n.domain).name + " / " + (n.eyebrow || "子节点"),
    );
  }
  function openPacket(key) {
    const [cid, seq] = key.split(":"),
      p = world.channels
        .get(cid)
        ?.items.find((p) => p.sequence === Number(seq));
    if (!p) return;
    openPanel(
      p.title,
      p.body,
      section(
        "域之间的数据",
        `<p>${esc(domains.get(p.from).name)} → ${esc(domains.get(p.to).name)}</p>`,
      ) +
        (p.proposal
          ? section(
              "推荐方案 · 尚未确认",
              `<p>这是方案演示，不代表已经确定或完成实现。</p>` +
                docs([p.proposal]),
            )
          : "") +
        (p.prompt
          ? section(
              "随请求传递",
              `<button class="prompt-link" data-prompt="${p.prompt}">${esc(prompts.get(p.prompt).name)} ↗</button>`,
            )
          : ""),
      "数据卡片 / #" + p.sequence,
    );
  }
  function openDoc(id) {
    const d = data.sources[id];
    if (!d) return;
    openPanel(
      d.title,
      "",
      `<article class="document-body">${d.html}</article>`,
      "约定与实现",
    );
    el("inspector-content").scrollTop = 0;
  }
  function openPrompt(id) {
    const p = prompts.get(id);
    if (!p) return;
    openPanel(
      p.name,
      "提示词由 Cloud 持有；具体方案状态见输入约定。",
      section("输入 → Cloud 组装 → 供应商", `<p>${esc(p.contract.input)}</p>`) +
        section("约束", list(p.contract.rules)) +
        section("模型必须返回", `<p>${esc(p.contract.output)}</p>`) +
        section(
          "实现对照",
          `<p class="muted">以上区分推荐和既定约定；已发布 0.4 源码另列：</p><code>${esc(p.source)}</code>` +
            docs([p.document, "implementation"]),
        ),
      "Cloud → 模型",
    );
  }
  function route() {
    const parts = location.hash.slice(1).split("/");
    if (parts[0] === "doc") {
      openDoc(parts[1]);
      return;
    }
    const sectionId = parts[0] === "business" ? "business" : "domains";
    const changed = state.flow !== (parts[1] || "voice");
    state.section = sectionId;
    if (["voice", "advisor"].includes(parts[1])) state.flow = parts[1];
    el("domains-section").hidden = state.section !== "domains";
    el("business-section").hidden = state.section !== "business";
    el("workflow-tabs").hidden = state.section !== "business";
    document.querySelectorAll("[data-section]").forEach((b) => {
      b.classList.toggle("active", b.dataset.section === state.section);
      b.setAttribute(
        "aria-selected",
        String(b.dataset.section === state.section),
      );
    });
    document
      .querySelectorAll("[data-flow]")
      .forEach((b) =>
        b.setAttribute("aria-pressed", String(b.dataset.flow === state.flow)),
      );
    if (state.section === "business") {
      if (changed || !initialized) drawWorld();
      else requestAnimationFrame(() => fitGraph(true));
    } else pause();
  }
  document.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    if (b.dataset.section) {
      location.hash =
        b.dataset.section === "business" ? "business/" + state.flow : "domains";
      closePanel();
    } else if (b.dataset.flow) {
      location.hash = "business/" + b.dataset.flow;
      closePanel();
    } else if (b.dataset.domain) openDomain(b.dataset.domain);
    else if (b.dataset.openNode) openNode(b.dataset.openNode);
    else if (b.dataset.doc) openDoc(b.dataset.doc);
    else if (b.dataset.prompt) openPrompt(b.dataset.prompt);
    else if (b.dataset.packet) openPacket(b.dataset.packet);
    else if (b.dataset.history) {
      const c = data.world.channels.find((c) => c.id === b.dataset.history);
      world.channels.get(c.id).scroll(Number(b.dataset.delta));
      renderStream(c);
    } else if (b.dataset.live) {
      const c = data.world.channels.find((c) => c.id === b.dataset.live);
      world.channels.get(c.id).latest();
      renderStream(c);
    } else if (b.dataset.close) el(b.dataset.close).close();
  });
  el("pause-streams").addEventListener("click", () =>
    state.running ? pause() : play(),
  );
  el("step-world").addEventListener("click", () => {
    pause();
    if (world.done) drawWorld();
    step();
  });
  el("reset-world").addEventListener("click", drawWorld);
  el("zoom-in").addEventListener("click", () => scaleGraph(state.scale * 1.15));
  el("zoom-out").addEventListener("click", () =>
    scaleGraph(state.scale / 1.15),
  );
  el("zoom-fit").addEventListener("click", () => fitGraph());
  el("close-inspector").addEventListener("click", closePanel);
  el("panel-shade").addEventListener("click", closePanel);
  el("open-native").addEventListener("click", () => {
    pause();
    el("native-dialog").showModal();
    el("native-dialog").dispatchEvent(new Event("spec:open"));
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closePanel();
    if (
      e.code === "Space" &&
      state.section === "business" &&
      !state.focus &&
      !e.target.closest("button, input, textarea") &&
      !el("native-dialog").open
    ) {
      e.preventDefault();
      state.running ? pause() : play();
    }
  });
  const host = el("graph-scroll"),
    pointers = new Map();
  let drag, pinch;
  host.addEventListener("pointerdown", (e) => {
    if (e.target.closest("button,.packet-stream")) return;
    pointers.set(e.pointerId, [e.clientX, e.clientY]);
    host.setPointerCapture(e.pointerId);
    if (pointers.size === 1)
      drag = {
        x: e.clientX,
        y: e.clientY,
        left: host.scrollLeft,
        top: host.scrollTop,
      };
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      pinch = {
        dist: Math.hypot(a[0] - b[0], a[1] - b[1]),
        scale: state.scale,
      };
      drag = null;
    }
    host.classList.add("dragging");
  });
  host.addEventListener("pointermove", (e) => {
    if (!pointers.has(e.pointerId)) return;
    pointers.set(e.pointerId, [e.clientX, e.clientY]);
    if (pointers.size === 2 && pinch) {
      const [a, b] = [...pointers.values()],
        r = host.getBoundingClientRect();
      scaleGraph(
        (pinch.scale * Math.hypot(a[0] - b[0], a[1] - b[1])) / pinch.dist,
        [(a[0] + b[0]) / 2 - r.left, (a[1] + b[1]) / 2 - r.top],
      );
    } else if (drag) {
      host.scrollLeft = drag.left - (e.clientX - drag.x);
      host.scrollTop = drag.top - (e.clientY - drag.y);
    }
  });
  const release = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    drag = null;
    if (!pointers.size) host.classList.remove("dragging");
  };
  host.addEventListener("pointerup", release);
  host.addEventListener("pointercancel", release);
  host.addEventListener(
    "wheel",
    (e) => {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const r = host.getBoundingClientRect();
        scaleGraph(state.scale * Math.exp(-e.deltaY * 0.005), [
          e.clientX - r.left,
          e.clientY - r.top,
        ]);
      }
    },
    { passive: false },
  );
  new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => {
      if (state.section === "business") fitGraph(true);
    });
  }).observe(host);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) pause();
  });
  window.addEventListener("hashchange", route);
  route();
})();
