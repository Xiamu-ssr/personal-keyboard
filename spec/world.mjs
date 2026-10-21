/** Domain topology and packet contracts are shared by the browser and build checks. */
export function validateWorld(spec) {
  const need = (x, m) => {
    if (!x) throw Error(m);
  };
  need(
    spec.schemaVersion === 3 && spec.meta?.title,
    "Unsupported product schema",
  );
  const domains = new Map(),
    colors = new Set(),
    documents = new Set(spec.documents?.map((d) => d.id));
  need(documents.size === spec.documents?.length, "Duplicate document");
  const refs = (x) => {
    for (const id of x.documents || [])
      need(documents.has(id), "Unknown document: " + id);
  };
  for (const d of spec.domains || []) {
    need(d.id && !domains.has(d.id), "Duplicate domain");
    need(
      /^#[0-9a-f]{6}$/i.test(d.color) && !colors.has(d.color),
      "Domain colors must be distinct",
    );
    need(
      d.name &&
        d.summary &&
        d.responsibilities?.length &&
        d.data?.length &&
        d.code?.length,
      "Incomplete domain",
    );
    domains.set(d.id, d);
    colors.add(d.color);
    refs(d);
  }
  const prompts = new Map();
  for (const p of spec.prompts || []) {
    need(
      p.id &&
        !prompts.has(p.id) &&
        p.owner === "cloud" &&
        documents.has(p.document) &&
        /^cloud\/prompts\/[a-z-]+\.md$/.test(p.source),
      "Invalid prompt ownership",
    );
    prompts.set(p.id, p);
  }
  need(
    spec.workflows?.length === 2 &&
      spec.workflows[0].id === "voice" &&
      spec.workflows[1].id === "advisor",
    "Exactly two business graphs required",
  );
  let steps = 0,
    relations = 0;
  const used = new Set();
  for (const flow of spec.workflows) {
    need(
      flow.name && flow.summary && flow.width > 0 && flow.height > 0,
      "Incomplete graph",
    );
    const groups = new Map(),
      children = new Map(),
      pairs = new Set();
    for (const g of flow.groups || []) {
      need(
        domains.has(g.domain) && !groups.has(g.domain),
        "Domain must occur once per graph",
      );
      const r = g.rect;
      need(
        r?.length === 4 &&
          r.every((n) => Number.isFinite(n) && n >= 0) &&
          r[2] > 0 &&
          r[3] > 0 &&
          r[0] + r[2] <= flow.width &&
          r[1] + r[3] <= flow.height,
        "Domain outside graph",
      );
      groups.set(g.domain, g);
      need(g.children?.length, "Empty domain");
      for (const n of g.children) {
        const id = g.domain + "." + n.id;
        need(
          n.id && !children.has(id) && n.title && n.summary,
          "Incomplete capability",
        );
        children.set(id, { ...n, domain: g.domain });
        refs(n);
        for (const p of n.prompts || []) {
          need(
            g.domain === "cloud" && prompts.has(p),
            "Prompt must be assembled in Cloud",
          );
          used.add(p);
        }
        steps++;
      }
    }
    for (const c of flow.channels || []) {
      need(
        c.a !== c.b && groups.has(c.a) && groups.has(c.b),
        "Dangling channel",
      );
      const pair = [c.a, c.b].sort().join(":");
      need(!pairs.has(pair), "At most two directed lines per pair of domains");
      pairs.add(pair);
      if ([c.a, c.b].includes("provider"))
        need(
          [c.a, c.b].includes("cloud"),
          "Supplier traffic must go through Cloud",
        );
      need(
        c.stack?.length === 4 &&
          c.stack.every(Number.isFinite) &&
          c.packets?.length,
        "Missing packet carousel",
      );
      for (const d of ["ab", "ba"])
        need(
          c.paths?.[d]?.length >= 2 &&
            c.paths[d].every((p) => p.length === 2 && p.every(Number.isFinite)),
          "Missing directed path",
        );
      relations += 2;
      for (const p of c.packets) {
        const a = children.get(p.from),
          b = children.get(p.to);
        need(
          a &&
            b &&
            a.domain !== b.domain &&
            [a.domain, b.domain].every((id) => [c.a, c.b].includes(id)),
          "Packet endpoints must belong to the two domains",
        );
        need(
          p.title &&
            p.body &&
            ["main", "learning", "optional"].includes(p.kind),
          "Incomplete data packet",
        );
      }
    }
    for (const [id, n] of children) {
      if (n.prompts?.length) {
        need(
          n.targets?.length &&
            n.targets.every(
              (t) => children.has(t) && children.get(t).domain === "provider",
            ),
          "Prompt has no model destination",
        );
        for (const t of n.targets)
          need(
            flow.channels.some((c) =>
              c.packets.some((p) => p.from === id && p.to === t),
            ),
            "Prompt must travel to its model in a packet",
          );
      }
    }
  }
  need(
    [...prompts.keys()].every((id) => used.has(id)),
    "Orphan prompt",
  );
  return {
    domains: domains.size,
    workflows: spec.workflows.length,
    steps,
    relations,
    prompts: prompts.size,
  };
}
/** A bounded, chronological log. Reading older cards never loses the live tail. */
export class PacketHistory {
  constructor(packets, limit = 160) {
    this.pattern = packets;
    this.limit = limit;
    this.sequence = 0;
    this.items = [];
    this.index = -1;
    this.follow = true;
    for (let i = 0; i < Math.min(5, packets.length); i++) this.tick();
  }
  tick() {
    const packet = {
      ...this.pattern[this.sequence % this.pattern.length],
      sequence: ++this.sequence,
    };
    this.items.push(packet);
    if (this.items.length > this.limit) {
      this.items.shift();
      this.index = Math.max(0, this.index - 1);
    }
    if (this.follow) this.index = Math.max(0, this.items.length - 2);
    return packet;
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
    this.index = Math.max(0, this.items.length - 2);
  }
  visible() {
    return this.items
      .map((p, i) => ({ ...p, offset: i - this.index }))
      .filter((p) => Math.abs(p.offset) <= 2);
  }
}
