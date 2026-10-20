export function validateWorld(spec) {
  const fail = (message) => {
    throw new Error(message);
  };
  if (
    !spec.meta?.title ||
    !spec.layout?.width ||
    !spec.layout?.height ||
    !spec.domains?.length
  )
    fail("Missing world metadata");
  const domains = new Map(),
    chips = new Set(),
    edges = new Set();
  const documents = new Set(spec.documents?.map((d) => d.id));
  if (documents.size !== spec.documents?.length) fail("Duplicate document");
  function references(item) {
    for (const id of item.documents || [])
      if (!documents.has(id)) fail("Unknown document: " + id);
    for (const id of item.objects || [])
      if (!spec.objects?.[id]) fail("Unknown object: " + id);
  }
  for (const domain of spec.domains) {
    if (!domain.id || domains.has(domain.id))
      fail("Duplicate or missing domain");
    domains.set(domain.id, domain);
    if (!spec.zones?.[domain.zone]) fail("Unknown zone: " + domain.zone);
    for (const [key, width, height] of [
      ["rect", spec.layout.width, spec.layout.height],
      ["mobile", 100, 100],
    ]) {
      const rect = domain[key];
      if (
        !Array.isArray(rect) ||
        rect.length !== 4 ||
        rect.some((n) => !Number.isFinite(n) || n < 0) ||
        rect[2] <= 0 ||
        rect[3] <= 0 ||
        rect[0] + rect[2] > width ||
        rect[1] + rect[3] > height
      )
        fail("Invalid domain bounds: " + domain.id);
    }
    if (
      !domain.role ||
      !domain.business?.length ||
      !domain.technical?.length ||
      !Array.isArray(domain.chips)
    )
      fail("Incomplete domain: " + domain.id);
    references(domain);
    for (const chip of domain.chips) {
      if (!chip.id || chips.has(chip.id) || !chip.name)
        fail("Duplicate or missing capability");
      chips.add(chip.id);
      references(chip);
    }
  }
  for (const edge of spec.edges || []) {
    if (!edge.id || edges.has(edge.id)) fail("Duplicate or missing edge");
    edges.add(edge.id);
    if (!domains.has(edge.from) || !domains.has(edge.to))
      fail("Unknown edge endpoint: " + edge.id);
    if (!spec.kinds?.[edge.kind]) fail("Unknown relation kind");
    for (const field of [
      "business",
      "trigger",
      "payload",
      "guard",
      "technical",
    ])
      if (typeof edge[field] !== "string" || !edge[field].trim())
        fail("Incomplete relation " + edge.id + ": " + field);
    if (
      !Array.isArray(edge.ports) ||
      edge.ports.length !== 2 ||
      edge.ports.some((p) => !["top", "right", "bottom", "left"].includes(p))
    )
      fail("Invalid ports");
    if (domains.get(edge.to).zone === "service" && !edge.external)
      fail("Unmarked service boundary: " + edge.id);
    references(edge);
  }
  const views = new Set();
  for (const view of spec.views || []) {
    if (!view.id || views.has(view.id) || !Array.isArray(view.edges))
      fail("Invalid view");
    views.add(view.id);
    for (const id of view.edges)
      if (!edges.has(id)) fail("Unknown view relation: " + id);
  }
  for (const object of Object.values(spec.objects || {})) {
    if (
      !domains.has(object.owner) ||
      !object.name ||
      !object.invariant ||
      !Object.keys(object.fields || {}).length
    )
      fail("Incomplete owned object");
  }
  return {
    domains: domains.size,
    capabilities: chips.size,
    relations: edges.size,
    objects: Object.keys(spec.objects || {}).length,
  };
}

export function relationsForView(spec, viewId) {
  const view = spec.views.find((v) => v.id === viewId);
  if (!view) throw new Error("Unknown view");
  const edges =
    viewId === "all"
      ? spec.edges
      : spec.edges.filter((e) => view.edges.includes(e.id));
  return {
    edges: edges.map((e) => e.id),
    domains: [...new Set(edges.flatMap((e) => [e.from, e.to]))],
  };
}
