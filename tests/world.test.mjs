import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import YAML from "yaml";
import { validateWorld, PacketHistory } from "../spec/world.mjs";
const source = YAML.parse(
  await fs.readFile(new URL("../spec/spec.yaml", import.meta.url), "utf8"),
);
test("domain graph has one group per domain and at most one line each way", () => {
  const counts = validateWorld(source);
  assert.equal(counts.domains, 5);
  assert.equal(counts.workflows, 2);
  assert.equal(counts.relations, 16);
  for (const f of source.workflows) {
    assert.equal(new Set(f.groups.map((g) => g.domain)).size, f.groups.length);
    assert(
      !f.groups
        .flatMap((g) => g.children)
        .some((n) => /^\d+[ ·]/.test(n.title)),
    );
  }
});
test("reject duplicate domain pair, supplier bypass and missing prompt destination", () => {
  for (const mutate of [
    (x) =>
      x.workflows[0].channels.push(structuredClone(x.workflows[0].channels[0])),
    (x) => (x.workflows[0].channels.find((c) => c.b === "provider").a = "app"),
    (x) =>
      (x.workflows[0].groups
        .find((g) => g.domain === "cloud")
        .children.find((c) => c.id === "rewrite").targets = []),
    (x) => (x.workflows[0].channels[0].packets[0].to = "provider.text"),
    (x) => (x.domains[1].color = x.domains[0].color),
  ]) {
    const x = structuredClone(source);
    mutate(x);
    assert.throws(() => validateWorld(x));
  }
});
test("packet history preserves reading position, live tail and bounded chronological records", () => {
  const h = new PacketHistory(source.workflows[0].channels[1].packets, 20);
  for (let i = 0; i < 10; i++) h.tick();
  h.scroll(-3);
  const seq = h.items[h.index].sequence;
  h.tick();
  assert.equal(h.items[h.index].sequence, seq);
  assert.equal(h.follow, false);
  h.latest();
  assert.equal(h.items[h.index].sequence, h.sequence - 1);
  for (let i = 0; i < 50; i++) h.tick();
  assert.equal(h.items.length, 20);
  assert.equal(h.items.at(-1).sequence, h.sequence);
  assert(h.visible().every((p) => Math.abs(p.offset) <= 2));
});
test("compiled Spec shares plans and prompt documents", async () => {
  const html = await fs.readFile(
      new URL("../spec/index.html", import.meta.url),
      "utf8",
    ),
    compiled = JSON.parse(
      html.match(
        /<script id="spec-data" type="application\/json">([\s\S]*?)<\/script>/,
      )[1],
    );
  assert.deepEqual(validateWorld(compiled), validateWorld(source));
  assert.deepEqual(
    compiled.catalog,
    JSON.parse(
      await fs.readFile(
        new URL("../cloud/config/plans.json", import.meta.url),
        "utf8",
      ),
    ),
  );
  for (const p of source.prompts)
    assert(compiled.sources[p.document].html.length);
  assert(!html.includes('id="library-dialog"'));
  assert(!/<script[^>]+src=/.test(html));
});
