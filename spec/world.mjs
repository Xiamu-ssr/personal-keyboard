/** Executable product contracts. No network, device access or provider calls. */
export function nodeIndex(flow) {
  const nodes = new Map();
  for (const g of flow.groups)
    for (const n of g.nodes)
      nodes.set(g.domain + "." + n.id, { ...n, domain: g.domain, group: g });
  return nodes;
}
export function portAt(flow, endpoint) {
  const [domain, id, key] = endpoint.split(".");
  const node = nodeIndex(flow).get(domain + "." + id);
  const port = node?.ports.find((p) => p.id === key);
  if (!port) throw Error("Unknown port: " + endpoint);
  const sameSide = node.ports.filter((p) => p.side === port.side),
    i = sameSide.indexOf(port);
  const [x, y, w, h] = node.rect,
    [gx, gy] = node.group.rect;
  const f = (i + 1) / (sameSide.length + 1);
  return {
    x: gx + x + (port.side === "right" ? w : port.side === "left" ? 0 : w * f),
    y: gy + y + (port.side === "bottom" ? h : port.side === "top" ? 0 : h * f),
    side: port.side,
    dir: port.dir,
    type: port.type,
    node,
    port,
  };
}
export function edgeRoute(flow, edge) {
  const a = portAt(flow, edge.from),
    b = portAt(flow, edge.to);
  const offset = (p, d) => [
    p.x + (p.side === "right" ? d : p.side === "left" ? -d : 0),
    p.y + (p.side === "bottom" ? d : p.side === "top" ? -d : 0),
  ];
  const lead = offset(a, 13),
    tail = offset(b, 13);
  if (edge.channel) {
    const c = flow.channels.find((c) => c.id === edge.channel),
      direction = a.node.domain === c.a ? "ab" : "ba";
    const path = c.paths[direction],
      start = path[0],
      finish = path.at(-1);
    const sign = Math.sign(finish[0] - start[0]) || 1,
      exitX = start[0] - sign * 12,
      enterX = finish[0] + sign * 12;
    return [
      [a.x, a.y],
      lead,
      ...(edge.exitVia || [
        [exitX, lead[1]],
        [exitX, start[1]],
      ]),
      ...path,
      ...(edge.entryVia || [
        [enterX, finish[1]],
        [enterX, tail[1]],
      ]),
      tail,
      [b.x, b.y],
    ];
  }
  if (edge.via) return [[a.x, a.y], lead, ...edge.via, tail, [b.x, b.y]];
  if (Math.abs(a.x - b.x) < 30)
    return [[a.x, a.y], lead, [lead[0], tail[1]], tail, [b.x, b.y]];
  const mid = (lead[0] + tail[0]) / 2;
  return [[a.x, a.y], lead, [mid, lead[1]], [mid, tail[1]], tail, [b.x, b.y]];
}
export function validateWorld(spec) {
  const need = (v, m) => {
    if (!v) throw Error(m);
  };
  need(
    spec.schemaVersion === 4 && spec.meta?.title,
    "Unsupported product schema",
  );
  const domains = new Map(),
    colors = new Set(),
    documents = new Set(spec.documents.map((d) => d.id));
  need(documents.size === spec.documents.length, "Duplicate document");
  const refs = (x) => {
    for (const r of x.documents || [])
      need(documents.has(r), "Unknown document " + r);
  };
  for (const d of spec.domains) {
    need(
      d.id && !domains.has(d.id) && d.name && d.summary,
      "Incomplete domain",
    );
    need(
      /^#[0-9a-f]{6}$/i.test(d.color) && !colors.has(d.color),
      "Domain colors must be distinct",
    );
    domains.set(d.id, d);
    colors.add(d.color);
    refs(d);
  }
  const prompts = new Map(spec.prompts.map((p) => [p.id, p]));
  for (const p of prompts.values())
    need(
      p.owner === "cloud" &&
        documents.has(p.document) &&
        p.contract?.input &&
        p.contract?.output &&
        p.contract?.rules?.length,
      "Prompt ownership",
    );
  need(
    spec.workflows.length === 2 &&
      spec.workflows[0].id === "voice" &&
      spec.workflows[1].id === "advisor",
    "Exactly two business graphs",
  );
  let relations = 0,
    nodesCount = 0,
    channelsCount = 0;
  for (const f of spec.workflows) {
    const nodes = nodeIndex(f),
      owners = new Set(),
      pairs = new Set(),
      edges = new Map(),
      seenEvents = new Set();
    need(f.width > 0 && f.height > 0, "Graph bounds");
    for (const g of f.groups) {
      need(
        domains.has(g.domain) && !owners.has(g.domain),
        "Each domain occurs once",
      );
      owners.add(g.domain);
      const [x, y, w, h] = g.rect;
      need(
        x >= 0 && y >= 0 && x + w <= f.width && y + h <= f.height,
        "Domain outside canvas",
      );
      const ids = new Set();
      for (const n of g.nodes) {
        need(
          n.title && n.summary && !ids.has(n.id),
          "Duplicate or incomplete node",
        );
        ids.add(n.id);
        refs(n);
        const [nx, ny, nw, nh] = n.rect;
        need(
          nx >= 12 &&
            ny >= 42 &&
            nw >= 80 &&
            nh >= 40 &&
            nx + nw <= w - 12 &&
            ny + nh <= h - 12,
          "Node outside its domain",
        );
        need(
          n.ports?.length &&
            new Set(n.ports.map((p) => p.id)).size === n.ports.length,
          "Real nodes need distinct ports",
        );
        for (const p of n.ports)
          need(
            ["in", "out"].includes(p.dir) &&
              ["left", "right", "top", "bottom"].includes(p.side) &&
              p.type,
            "Invalid port",
          );
        for (const p of n.prompts || [])
          need(
            g.domain === "cloud" && prompts.has(p),
            "Prompts belong to Cloud",
          );
      }
    }
    for (const c of f.channels) {
      const pair = [c.a, c.b].sort().join(":");
      need(
        c.a !== c.b && owners.has(c.a) && owners.has(c.b) && !pairs.has(pair),
        "At most two domain bus lines",
      );
      pairs.add(pair);
      for (const d of ["ab", "ba"])
        need(c.paths[d].length >= 2, "Missing return bus");
      need(c.stack.length === 4, "Missing scrollable packet history");
      if ([c.a, c.b].includes("provider"))
        need([c.a, c.b].includes("cloud"), "Supplier bypass");
    }
    for (const e of f.edges) {
      need(e.id && !edges.has(e.id), "Duplicate edge");
      edges.set(e.id, e);
      const a = portAt(f, e.from),
        b = portAt(f, e.to);
      need(
        a.dir === "out" && b.dir === "in" && a.type === b.type,
        "Output/input type mismatch",
      );
      if (a.node.domain !== b.node.domain) {
        const c = f.channels.find((c) => c.id === e.channel);
        need(
          c &&
            [a.node.domain, b.node.domain].every((d) => [c.a, c.b].includes(d)),
          "Cross-domain node edge needs a shared bus",
        );
      } else need(!e.channel, "Internal node edges cannot use a domain bus");
      need(e.title && e.detail, "Data must be named on each node connection");
    }
    for (const [id, n] of nodes) {
      need(
        f.edges.some(
          (e) => e.from.startsWith(id + ".") || e.to.startsWith(id + "."),
        ),
        "Orphan node",
      );
      for (const p of n.prompts || [])
        need(
          f.edges.some(
            (e) => e.from.startsWith(id + ".") && e.to.startsWith("provider."),
          ),
          "Prompt has no model destination",
        );
    }
    for (const e of f.events) {
      need(e.id && !seenEvents.has(e.id), "Duplicate event");
      need(
        (e.after || []).every((id) => seenEvents.has(id)),
        "Event violates causality",
      );
      need(
        (e.edge && edges.has(e.edge)) || (e.node && nodes.has(e.node)),
        "Event needs real endpoints",
      );
      need(e.title && e.body && e.delay >= 0, "Incomplete event");
      for (const id of Object.keys(e.status || {}))
        need(nodes.has(id), "State update outside graph");
      for (const id of [...(e.startStreams || []), ...(e.stopStreams || [])])
        need(edges.has(id), "Stream needs a real node edge");
      seenEvents.add(e.id);
    }
    need(
      f.events[0].edge && edges.get(f.events[0].edge).from.startsWith("phone."),
      "User phone starts the world",
    );
    need(
      f.events.some((e) => e.phase === "learning") &&
        f.events.some((e) => e.phase === "reuse"),
      "History must feed back into later use",
    );
    relations += f.edges.length;
    nodesCount += nodes.size;
    channelsCount += f.channels.length;
  }
  return {
    domains: domains.size,
    workflows: spec.workflows.length,
    nodes: nodesCount,
    relations,
    channels: channelsCount,
    prompts: prompts.size,
  };
}
/** Append-only observations; reading old packets never advances the simulated world. */
export class PacketHistory {
  constructor(limit = 80) {
    this.limit = limit;
    this.items = [];
    this.index = -1;
    this.follow = true;
    this.sequence = 0;
  }
  append(packet) {
    this.items.push({ ...packet, sequence: ++this.sequence });
    if (this.items.length > this.limit) {
      this.items.shift();
      this.index = Math.max(0, this.index - 1);
    }
    if (this.follow) this.index = this.items.length - 1;
    return this.items.at(-1);
  }
  scroll(delta) {
    this.follow = false;
    this.index = Math.max(
      0,
      Math.min(this.items.length - 1, this.index + delta),
    );
  }
  latest() {
    this.follow = true;
    this.index = this.items.length - 1;
  }
  visible() {
    return this.items
      .map((p, i) => ({ ...p, offset: i - this.index }))
      .filter((p) => Math.abs(p.offset) <= 2);
  }
}
/** One causal clock drives all nodes and packet histories, never independent random loops. */
export class ProductWorld {
  constructor(flow) {
    this.flow = flow;
    this.reset();
  }
  reset() {
    this.cursor = 0;
    this.current = null;
    this.done = false;
    this.status = {};
    this.facts = {};
    this.seen = new Set();
    this.activeStreams = new Set();
    this.channels = new Map(
      this.flow.channels.map((c) => [c.id, new PacketHistory()]),
    );
    this.journal = [];
  }
  step() {
    const e = this.flow.events[this.cursor];
    if (!e) {
      this.done = true;
      return null;
    }
    if (!(e.after || []).every((id) => this.seen.has(id)))
      throw Error("Causal prerequisite missing");
    const edge = this.flow.edges.find((x) => x.id === e.edge);
    const packet = { ...e, edge: edge?.id, from: edge?.from, to: edge?.to };
    if (edge?.channel) this.channels.get(edge.channel).append(packet);
    Object.assign(this.status, e.status || {});
    Object.assign(this.facts, e.facts || {});
    for (const id of e.startStreams || []) this.activeStreams.add(id);
    for (const id of e.stopStreams || []) this.activeStreams.delete(id);
    this.current = packet;
    this.journal.push(packet);
    this.seen.add(e.id);
    this.cursor++;
    this.done = this.cursor === this.flow.events.length;
    return packet;
  }
}
