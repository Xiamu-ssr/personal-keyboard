/** Entities stay fixed while example data moves between their owners. */
export function nodeIndex(spec) {
  const index = new Map();
  for (const g of spec.world.groups) {
    const d = spec.domains.find((d) => d.id === g.domain);
    for (const p of g.nodes) {
      const n = d.nodes.find((n) => n.id === p.id);
      index.set(`${d.id}.${n.id}`, { ...n, rect: p.rect, domain: d.id });
    }
  }
  return index;
}
/** Exactly one straight lane per direction, ending at domain boundaries. */
export function laneRoute(world, channel, direction) {
  const a = world.groups.find((g) => g.domain === channel.a).rect;
  const b = world.groups.find((g) => g.domain === channel.b).rect;
  const y = channel.lanes[direction];
  return direction === "ab"
    ? [
        [a[0] + a[2], y],
        [b[0], y],
      ]
    : [
        [b[0], y],
        [a[0] + a[2], y],
      ];
}
export function validateWorld(spec) {
  const need = (v, m) => {
    if (!v) throw Error(m);
  };
  need(
    spec.schemaVersion === 5 && spec.meta?.title,
    "Unsupported domain schema",
  );
  const docs = new Set(spec.documents.map((d) => d.id));
  need(docs.size === spec.documents.length, "Duplicate document");
  const refs = (x) => {
    for (const id of x.documents || [])
      need(docs.has(id), "Unknown document " + id);
  };
  const domains = new Map(),
    colors = new Set();
  for (const d of spec.domains) {
    need(
      d.id && d.name && d.summary && !domains.has(d.id),
      "Duplicate or incomplete domain",
    );
    need(
      /^#[a-f\d]{6}$/i.test(d.color) && !colors.has(d.color),
      "Distinct domain colors required",
    );
    domains.set(d.id, d);
    colors.add(d.color);
    refs(d);
    const ids = new Set();
    for (const n of d.nodes || []) {
      need(
        n.id && n.title && n.summary && !ids.has(n.id),
        "Duplicate or incomplete entity",
      );
      need(!n.ports, "Entity nodes do not have workflow ports");
      ids.add(n.id);
      refs(n);
    }
  }
  const w = spec.world,
    placed = new Set(),
    pairs = new Set(),
    channels = new Set();
  need(w.width > 0 && w.height > 0, "World bounds required");
  for (const g of w.groups) {
    need(
      domains.has(g.domain) && !placed.has(g.domain),
      "A domain occurs once",
    );
    placed.add(g.domain);
    const [x, y, width, height] = g.rect;
    need(
      x >= 0 && y >= 0 && x + width <= w.width && y + height <= w.height,
      "Domain outside canvas",
    );
    const entities = domains.get(g.domain).nodes || [];
    need(
      g.nodes.length === entities.length &&
        new Set(g.nodes.map((n) => n.id)).size === entities.length,
      "Place every entity exactly once",
    );
    for (const n of g.nodes) {
      need(
        entities.some((e) => e.id === n.id),
        "Unknown entity",
      );
      const [nx, ny, nw, nh] = n.rect;
      need(
        nx >= 12 &&
          ny >= 60 &&
          nw >= 80 &&
          nh >= 40 &&
          nx + nw <= width - 12 &&
          ny + nh <= height - 12,
        "Entity outside domain",
      );
    }
  }
  const nodes = nodeIndex(spec),
    prompts = new Set(spec.prompts.map((p) => p.id));
  for (const p of spec.prompts)
    need(
      p.owner === "cloud" &&
        docs.has(p.document) &&
        p.contract?.input &&
        p.contract?.output,
      "Cloud owns prompt contracts",
    );
  for (const n of nodes.values())
    for (const id of n.prompts || [])
      need(
        n.domain === "cloud" && prompts.has(id),
        "Prompt attached outside Cloud",
      );
  for (const c of w.channels) {
    const pair = [c.a, c.b].sort().join(":");
    need(
      c.a !== c.b &&
        placed.has(c.a) &&
        placed.has(c.b) &&
        !pairs.has(pair) &&
        !channels.has(c.id),
      "Only one pair of domain lanes",
    );
    pairs.add(pair);
    channels.add(c.id);
    const a = w.groups.find((g) => g.domain === c.a).rect,
      b = w.groups.find((g) => g.domain === c.b).rect;
    need(a[0] + a[2] < b[0], "Lanes run between left and right domains");
    need(
      !w.groups.some((g) => g.rect[0] > a[0] && g.rect[0] < b[0]),
      "A lane cannot cross another domain",
    );
    need(
      c.lanes.ab < c.lanes.ba &&
        [c.lanes.ab, c.lanes.ba].every(
          (y) =>
            y > Math.max(a[1], b[1]) && y < Math.min(a[1] + a[3], b[1] + b[3]),
        ),
      "Two distinct return lanes",
    );
    need(c.stack?.length === 4, "Scrollable card stack required");
    const [cx, cy, sw, sh] = c.stack;
    need(
      cx - sw / 2 > a[0] + a[2] &&
        cx + sw / 2 < b[0] &&
        cy - sh / 2 >= 0 &&
        cy + sh / 2 <= w.height,
      "Cards stay between domains",
    );
    if ([c.a, c.b].includes("provider"))
      need([c.a, c.b].includes("cloud"), "Suppliers only connect to Cloud");
  }
  need(!w.edges, "No internal workflow wiring");
  need(
    spec.businesses.length === 2 &&
      spec.businesses.map((v) => v.id).join(",") === "voice,advisor",
    "Two business views required",
  );
  for (const view of spec.businesses) {
    const seen = new Set();
    for (const e of view.events) {
      need(
        e.id && !seen.has(e.id) && e.title && e.body && e.delay >= 0,
        "Incomplete data event",
      );
      need(
        (e.after || []).every((id) => seen.has(id)),
        "Future event dependency",
      );
      need(
        e.channel
          ? channels.has(e.channel) && ["ab", "ba"].includes(e.direction)
          : nodes.has(e.node),
        "Unknown data channel or local entity",
      );
      for (const id of Object.keys(e.status || {}))
        need(nodes.has(id), "Unknown state owner");
      if (e.proposal)
        need(docs.has(e.proposal), "Proposals need a reviewable explanation");
      for (const id of [...(e.startStreams || []), ...(e.stopStreams || [])]) {
        const [channel, dir] = id.split(":");
        need(
          channels.has(channel) && ["ab", "ba"].includes(dir),
          "Unknown streaming lane",
        );
      }
      seen.add(e.id);
    }
    need(
      view.events[0]?.channel === "phone-app" &&
        view.events[0].direction === "ab",
      "Examples begin with a user action",
    );
  }
  return {
    domains: domains.size,
    businesses: spec.businesses.length,
    nodes: nodes.size,
    channels: channels.size,
    lanes: channels.size * 2,
    prompts: prompts.size,
  };
}
/** Reading older cards neither advances the example nor jumps to new arrivals. */
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
/** A single example clock updates data, never the domain topology. */
export class ProductWorld {
  constructor(world, view) {
    this.world = world;
    this.view = view;
    this.reset();
  }
  reset() {
    this.cursor = 0;
    this.current = null;
    this.done = false;
    this.status = {};
    this.seen = new Set();
    this.activeStreams = new Set();
    this.journal = [];
    this.channels = new Map(
      this.world.channels.map((c) => [c.id, new PacketHistory()]),
    );
  }
  step() {
    const e = this.view.events[this.cursor];
    if (!e) {
      this.done = true;
      return null;
    }
    if (!(e.after || []).every((id) => this.seen.has(id)))
      throw Error("Missing prerequisite");
    const c = this.world.channels.find((c) => c.id === e.channel);
    const packet = {
      ...e,
      from: c ? (e.direction === "ab" ? c.a : c.b) : undefined,
      to: c ? (e.direction === "ab" ? c.b : c.a) : undefined,
    };
    if (c) this.channels.get(c.id).append(packet);
    Object.assign(this.status, e.status || {});
    for (const id of e.startStreams || []) this.activeStreams.add(id);
    for (const id of e.stopStreams || []) this.activeStreams.delete(id);
    this.current = packet;
    this.journal.push(packet);
    this.seen.add(e.id);
    this.cursor++;
    this.done = this.cursor === this.view.events.length;
    return packet;
  }
}
