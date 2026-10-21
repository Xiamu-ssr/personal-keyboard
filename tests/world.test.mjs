import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import YAML from "yaml";
import {
  validateWorld,
  nodeIndex,
  portAt,
  edgeRoute,
  PacketHistory,
  ProductWorld,
} from "../spec/world.mjs";
const source = YAML.parse(
  await fs.readFile(new URL("../spec/spec.yaml", import.meta.url), "utf8"),
);
test("domains contain spatial nodes with actual typed input and output edges", () => {
  const counts = validateWorld(source);
  assert.deepEqual(counts, {
    domains: 5,
    workflows: 2,
    nodes: 29,
    relations: 42,
    channels: 6,
    prompts: 3,
  });
  for (const f of source.workflows) {
    assert.equal(new Set(f.groups.map((g) => g.domain)).size, f.groups.length);
    const index = nodeIndex(f);
    for (const e of f.edges) {
      const from = portAt(f, e.from),
        to = portAt(f, e.to),
        path = edgeRoute(f, e);
      assert.equal(from.dir, "out");
      assert.equal(to.dir, "in");
      assert.equal(from.type, to.type);
      assert.deepEqual(path[0], [from.x, from.y]);
      assert.deepEqual(path.at(-1), [to.x, to.y]);
      assert(index.has(e.from.split(".").slice(0, 2).join(".")));
    }
    for (const c of f.channels) {
      const edges = f.edges.filter((e) => e.channel === c.id);
      assert(edges.some((e) => e.from.startsWith(c.a + ".")));
      assert(edges.some((e) => e.from.startsWith(c.b + ".")));
      for (const lane of [c.paths.ab, c.paths.ba]) assert(lane.length >= 2);
    }
  }
});
test("invalid node ownership, ports, domain buses and causal prerequisites are rejected", () => {
  for (const mutate of [
    (s) =>
      s.workflows[0].channels.push(structuredClone(s.workflows[0].channels[0])),
    (s) => (s.workflows[0].channels.find((c) => c.b === "provider").a = "app"),
    (s) =>
      s.workflows[0].groups[0].nodes[0].ports.push(
        structuredClone(s.workflows[0].groups[0].nodes[0].ports[0]),
      ),
    (s) => (s.workflows[0].edges[0].to = "app.missing.audio"),
    (s) => (s.workflows[0].groups[0].nodes[0].ports[0].type = "wrong"),
    (s) => (s.workflows[0].events[0].after = ["future-event"]),
    (s) => (s.workflows[0].groups[0].nodes[0].rect[0] = -100),
    (s) => (s.domains[1].color = s.domains[0].color),
    (s) =>
      (s.workflows[0].edges = s.workflows[0].edges.filter(
        (e) => !e.from.startsWith("cloud.rewrite."),
      )),
  ]) {
    const s = structuredClone(source);
    mutate(s);
    assert.throws(() => validateWorld(s));
  }
});
test("a single causal world starts with no future packets and stops at memory reuse", () => {
  for (const f of source.workflows) {
    const w = new ProductWorld(f);
    assert([...w.channels.values()].every((h) => h.items.length === 0));
    let result = false,
      edit = false,
      learn = false;
    while (!w.done) {
      const e = w.step();
      assert(e.after.every((id) => w.seen.has(id)));
      if (e.id === "final-app") {
        result = true;
        assert(w.seen.has("request-model"));
      }
      if (f.id === "voice" && e.id === "partial-app") {
        assert(w.activeStreams.has("upload"));
        assert(w.activeStreams.has("model"));
      }
      if (e.id === "save-edit") {
        edit = true;
        assert(result);
        assert(w.seen.has("edit-observed"));
      }
      if (e.id === "editor-cleared") {
        assert(edit);
        assert(w.facts.lastObservedText);
        assert.equal(w.facts.finality, "unknown_after_clear");
        assert(w.facts.insertedText);
      }
      if (e.id === "learning-job") {
        learn = true;
        assert(w.seen.has("five-hours"));
        assert(edit);
      }
      if (e.id === "reuse-memory") {
        assert(learn);
        assert.equal(w.facts.memoryRevision, w.facts.reusedRevision);
        assert.equal(w.facts.historyRetained, true);
      }
    }
    assert.equal(w.journal.length, f.events.length);
    assert.equal(w.activeStreams.size, 0);
    assert.equal(w.step(), null);
    assert.equal(w.journal.length, f.events.length);
    assert(
      w.channels
        .get("phone-app")
        .items.some((p) => p.from === "phone.editor.edits"),
    );
    w.reset();
    assert.equal(w.cursor, 0);
    assert.deepEqual(w.facts, {});
    assert([...w.channels.values()].every((h) => h.items.length === 0));
  }
});
test("packet history is bounded, chronological and keeps the packet being read", () => {
  const h = new PacketHistory(20);
  assert.equal(h.visible().length, 0);
  for (let i = 0; i < 10; i++) h.append({ id: "e" + i });
  h.scroll(-3);
  const seq = h.items[h.index].sequence;
  h.append({ id: "later" });
  assert.equal(h.items[h.index].sequence, seq);
  assert.equal(h.follow, false);
  h.latest();
  assert.equal(h.items[h.index].sequence, h.sequence);
  for (let i = 0; i < 50; i++) h.append({ id: "e" + i });
  assert.equal(h.items.length, 20);
  assert(h.visible().every((p) => Math.abs(p.offset) <= 2));
  assert.equal(h.items.at(-1).sequence, h.sequence);
});
test("compiled Spec shares catalog and source prompts while marking target versus released behavior", async () => {
  const html = await fs.readFile(
    new URL("../spec/index.html", import.meta.url),
    "utf8",
  );
  const compiled = JSON.parse(
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
  assert(
    compiled.sources.decisions.html.includes('href="#doc/implementation"'),
  );
  assert(compiled.sources.decisions.html.includes("不做 OCR 前置"));
  assert(compiled.sources.implementation.html.includes("没有重新发布 APK"));
  assert(!html.includes('id="library-dialog"'));
  assert(!/<script[^>]+src=/.test(html));
  assert(html.includes('id="step-world"'));
  assert(html.includes('id="reset-world"'));
});
