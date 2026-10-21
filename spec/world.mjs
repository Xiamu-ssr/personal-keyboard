export function validateWorld(spec) {
  const require = (condition, message) => {
    if (!condition) throw Error(message);
  };
  require(spec.schemaVersion === 2 &&
    spec.meta?.title, "Unsupported product schema");
  const domains = new Map(),
    colors = new Set(),
    documents = new Set(spec.documents?.map((d) => d.id));
  require(documents.size === spec.documents?.length, "Duplicate document");
  const refs = (item) => {
    for (const id of item.documents || [])
      require(documents.has(id), "Unknown document: " + id);
  };
  for (const d of spec.domains || []) {
    require(d.id && !domains.has(d.id), "Duplicate or missing domain");
    require(/^#[0-9a-f]{6}$/i.test(d.color) &&
      !colors.has(d.color), "Domain colors must be fixed and distinct");
    require(d.name &&
      d.summary &&
      d.responsibilities?.length &&
      d.data?.length &&
      d.code?.length, "Incomplete domain");
    domains.set(d.id, d);
    colors.add(d.color);
    refs(d);
  }
  require(domains.has("app") &&
    domains.has("cloud") &&
    domains.has("provider"), "Missing service boundary");
  const prompts = new Map();
  for (const p of spec.prompts || []) {
    require(p.id &&
      !prompts.has(p.id) &&
      p.owner === "cloud" &&
      documents.has(p.document), "Invalid prompt owner or document");
    require(/^cloud\/prompts\/[a-z-]+\.md$/.test(
      p.source,
    ), "Invalid prompt source");
    prompts.set(p.id, p);
  }
  require(spec.workflows?.length === 2 &&
    spec.workflows.some((f) => f.id === "voice") &&
    spec.workflows.some(
      (f) => f.id === "advisor",
    ), "Exactly two business workflows required");
  let nodeCount = 0,
    edgeCount = 0;
  const workflowIds = new Set(),
    consumers = new Set();
  for (const flow of spec.workflows) {
    require(!workflowIds.has(flow.id) &&
      flow.name &&
      flow.summary &&
      flow.width > 0 &&
      flow.height > 0, "Invalid workflow");
    workflowIds.add(flow.id);
    const nodes = new Map(),
      edges = new Set();
    for (const n of flow.nodes || []) {
      require(n.id &&
        !nodes.has(n.id) &&
        domains.has(n.domain), "Invalid step owner or identity");
      require(n.title &&
        n.summary &&
        n.input &&
        n.output &&
        Array.isArray(n.rules), "Incomplete step contract");
      const r = n.rect;
      require(Array.isArray(r) &&
        r.length === 4 &&
        r.every((x) => Number.isFinite(x) && x >= 0) &&
        r[2] > 0 &&
        r[3] > 0 &&
        r[0] + r[2] <= flow.width &&
        r[1] + r[3] <= flow.height, "Step outside graph");
      for (const id of n.prompts || []) {
        require(prompts.has(id) &&
          n.domain === "cloud", "Prompt must be assembled in Cloud");
        consumers.add(id);
      }
      refs(n);
      nodes.set(n.id, n);
      nodeCount++;
    }
    for (const e of flow.edges || []) {
      require(e.id &&
        !edges.has(e.id) &&
        nodes.has(e.from) &&
        nodes.has(e.to), "Dangling or duplicate relation");
      require(e.label &&
        e.note &&
        ["main", "optional", "learning"].includes(
          e.kind,
        ), "Incomplete relation");
      require(e.points?.length >= 2 &&
        e.points.every(
          (p) => p.length === 2 && p.every(Number.isFinite),
        ), "Missing relation path");
      const from = nodes.get(e.from),
        to = nodes.get(e.to);
      if (from.domain === "provider" || to.domain === "provider")
        require((from.domain === "cloud" && to.domain === "provider") ||
          (from.domain === "provider" &&
            to.domain ===
              "cloud"), "App must never bypass Cloud to a supplier");
      edges.add(e.id);
      edgeCount++;
    }
    for (const n of nodes.values()) {
      require(flow.edges.some(
        (e) => e.from === n.id || e.to === n.id,
      ), "Orphan business step");
      if (n.prompts?.length)
        require(flow.edges.some(
          (e) => e.from === n.id && nodes.get(e.to).domain === "provider",
        ), "Prompt has no model destination");
    }
  }
  require([...prompts.keys()].every((id) =>
    consumers.has(id),
  ), "Orphan prompt");
  return {
    domains: domains.size,
    workflows: workflowIds.size,
    steps: nodeCount,
    relations: edgeCount,
    prompts: prompts.size,
  };
}
