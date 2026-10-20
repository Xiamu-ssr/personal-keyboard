import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import YAML from "yaml";
import { validateWorld, relationsForView } from "../spec/world.mjs";
const source = YAML.parse(
  await fs.readFile(new URL("../spec/spec.yaml", import.meta.url), "utf8"),
);
const clone = () => structuredClone(source);
test("world has complete owned objects and selectable business/implementation relations", () => {
  assert.deepEqual(validateWorld(source), {
    domains: 8,
    capabilities: 17,
    relations: 10,
    objects: 10,
  });
  const advisor = relationsForView(source, "advisor");
  assert(advisor.edges.includes("e_capture"));
  assert(advisor.domains.includes("memory"));
  assert(!advisor.domains.includes("doudou"));
  assert.deepEqual(relationsForView(source, "boundary").edges, [
    "e_inference",
    "e_sync",
  ]);
});
test("world rejects dangling references, incomplete contracts and unmarked egress", () => {
  for (const mutate of [
    (x) => x.domains[0].objects.push("missing"),
    (x) => (x.domains[0].chips[0].documents = ["missing"]),
    (x) => (x.edges[0].to = "missing"),
    (x) => (x.edges[0].guard = ""),
    (x) => (x.objects.contact.owner = "missing"),
    (x) => x.views[1].edges.push("missing"),
    (x) => (x.edges.find((e) => e.id === "e_sync").external = false),
    (x) => (x.domains[0].mobile = [90, 90, 30, 30]),
    (x) => x.domains[1].chips.push(structuredClone(x.domains[0].chips[0])),
  ]) {
    const spec = clone();
    mutate(spec);
    assert.throws(() => validateWorld(spec));
  }
});
test("generated self-contained world keeps metadata and rendered sources separate", async () => {
  const html = await fs.readFile(
    new URL("../spec/index.html", import.meta.url),
    "utf8",
  );
  const match = html.match(
    /<script id="spec-data" type="application\/json">([\s\S]*?)<\/script>/,
  );
  const compiled = JSON.parse(match[1]);
  assert.deepEqual(validateWorld(compiled), validateWorld(source));
  assert.deepEqual(
    Object.keys(compiled.sources),
    source.documents.map((d) => d.id),
  );
  for (const doc of Object.values(compiled.sources))
    assert(doc.html.trim().length > 0);
  assert(!/<script[^>]+src=/.test(html));
});
