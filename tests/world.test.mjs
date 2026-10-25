import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import YAML from "yaml";
import {
  validateWorld,
  nodeIndex,
  laneRoute,
  PacketHistory,
  ProductWorld,
} from "../spec/world.mjs";
const source = YAML.parse(
  await fs.readFile(new URL("../spec/spec.yaml", import.meta.url), "utf8"),
);

test("four business views share entities, never duplicate processing steps as nodes", () => {
  assert.deepEqual(validateWorld(source), {
    domains: 5,
    businesses: 4,
    nodes: 13,
    channels: 3,
    lanes: 6,
    prompts: 4,
  });
  assert.deepEqual(
    source.domains.find((d) => d.id === "app").nodes.map((n) => n.title),
    ["历史", "记忆缓存", "翻译草稿"],
  );
  assert.deepEqual(
    source.domains.find((d) => d.id === "cloud").nodes.map((n) => n.title),
    ["代理", "账户与权益", "调色盘", "热词", "文风", "人物"],
  );
  for (const view of source.businesses) {
    assert.equal(view.groups, undefined);
    assert.equal(view.edges, undefined);
  }
  for (const n of nodeIndex(source).values()) assert.equal(n.ports, undefined);
});
test("only six straight boundary-to-boundary lanes, with cards wholly in the gaps", () => {
  for (const c of source.world.channels) {
    const a = source.world.groups.find((g) => g.domain === c.a).rect;
    const b = source.world.groups.find((g) => g.domain === c.b).rect;
    for (const dir of ["ab", "ba"]) {
      const pts = laneRoute(source.world, c, dir);
      assert.equal(pts.length, 2);
      assert.equal(pts[0][1], pts[1][1]);
      assert.deepEqual(
        pts.map((p) => p[0]),
        dir === "ab" ? [a[0] + a[2], b[0]] : [b[0], a[0] + a[2]],
      );
    }
    assert(c.stack[0] - c.stack[2] / 2 > a[0] + a[2]);
    assert(c.stack[0] + c.stack[2] / 2 < b[0]);
  }
});
test("malformed topology, supplier bypass, workflow ports and future data are rejected", () => {
  for (const mutate of [
    (s) => s.world.channels.push(structuredClone(s.world.channels[0])),
    (s) => (s.world.channels[2].a = "app"),
    (s) => (s.world.channels[0].b = "cloud"),
    (s) => (s.world.groups[0].nodes[0].rect[0] = -100),
    (s) => (s.domains[1].nodes[0].ports = [{ id: "out" }]),
    (s) => (s.world.edges = [{ from: "app.history", to: "cloud.proxy" }]),
    (s) => (s.domains[1].color = s.domains[0].color),
    (s) => (s.businesses[0].events[0].after = ["future"]),
    (s) => (s.businesses[0].events[0].status = { "app.imaginary": "x" }),
    (s) => (s.businesses[0].events[0].prompt = "learn"),
    (s) => (s.prompts[0].target = "app.history"),
    (s) => (s.documents = [{ id: "unstructured-notes" }]),
    (s) => (s.world.channels[0].stack[2] = 600),
    (s) => s.world.groups[1].nodes.pop(),
    (s) => (s.memoryUpdate.memory = "retrieved_subset"),
    (s) => (s.memoryUpdate.partition = "people_and_topics"),
    (s) => (s.memoryUpdate.overflow = "truncate"),
    (s) => (s.memoryUpdate.growthCheck = "none"),
  ]) {
    const s = structuredClone(source);
    mutate(s);
    assert.throws(() => validateWorld(s));
  }
});
test("example clock preserves chronological packets and Cloud-owned prompt delivery", () => {
  for (const v of source.businesses) {
    const w = new ProductWorld(source.world, v);
    assert([...w.channels.values()].every((h) => h.items.length === 0));
    while (!w.done) {
      const e = w.step();
      assert(e.after.every((id) => w.seen.has(id)));
      if (e.prompt) {
        assert.equal(e.channel, "cloud-provider");
        assert.equal(e.direction, "ab");
      }
    }
    assert.equal(w.activeStreams.size, 0);
    assert.equal(w.journal.length, v.events.length);
    assert.equal(w.step(), null);
    assert.equal(w.journal.length, v.events.length);
    for (const h of w.channels.values()) if (h.items.length)
      assert(
        h.items.some((p) => p.direction === "ab") &&
          h.items.some((p) => p.direction === "ba"),
      );
    w.reset();
    assert.equal(w.cursor, 0);
    assert.deepEqual(w.status, {});
  }
});
test("packet history is bounded and arrivals do not displace a card being read", () => {
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
});
test("compiled Spec uses YAML details and prompts without a Markdown document layer", async () => {
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
  assert.equal(compiled.sources, undefined);
  assert.equal(compiled.documents, undefined);
  for (const p of source.prompts) {
    assert.equal(compiled.prompts.find((c) => c.id === p.id).system, p.system);
    assert.equal(p.document, undefined);
    assert.equal(await fs.readFile(new URL('../cloud/prompts/' + p.id + '.md', import.meta.url), 'utf8'), p.system.trimEnd() + '\n');
  }
  assert.deepEqual(source.prompts.find(p => p.id === 'reply').requestTemplate,
    JSON.parse(await fs.readFile(new URL('../cloud/prompts/reply-request.json', import.meta.url), 'utf8')));
  assert.equal(compiled.meta.status, source.meta.status);
  assert(!html.includes("data-doc="));
  assert(!html.includes("#doc/"));
  assert(!html.includes("data-prompt="));
  assert(!html.includes("function openPrompt("));
  assert(!html.includes("域之间的数据"));
  const markdownFiles = (await fs.readdir(new URL("../spec/", import.meta.url), { recursive: true }))
    .filter((name) => name.endsWith(".md"));
  assert.deepEqual(markdownFiles, []);
  assert(!html.includes('id="phase-tabs"'));
  assert(!html.includes('class="node-branch"'));
  assert(!html.includes('id="library-dialog"'));
  assert(!/<script[^>]+src=/.test(html));
});
test("advisor reads Cloud memory; learning sends full snapshots and only batches history", () => {
  const advisor = source.businesses.find((v) => v.id === "advisor");
  const upload = advisor.events.find((e) => e.channel === "app-cloud" && e.direction === "ab");
  assert.deepEqual(Object.keys(upload.fields), ["截图", "会话标识", "近期上下文", "意图"]);
  const request = advisor.events.find((e) => e.prompt === "reply");
  assert(request.fields.来自Cloud.includes("人物资料"));
  assert(request.fields.来自Cloud.includes("文风"));
  const learning = source.businesses.find((v) => v.id === "learning");
  assert.equal(learning.events[0].channel, "app-cloud");
  const learn = source.prompts.find((p) => p.id === "learn");
  assert.equal(learn.mode, "text");
  assert(learn.system.includes("完整个人记忆快照"));
  assert(learn.system.includes("baseVersion"));
  assert.equal(source.memoryUpdate.memory, "full_snapshot");
  assert.equal(source.memoryUpdate.partition, "history_only");
  const inserted = source.businesses.find((v) => v.id === "voice")
    .events.find((e) => e.id === "voice-11");
  assert.equal(inserted.fields.结果稿, inserted.fields.实际填入稿);
  const revised = source.businesses.find((v) => v.id === "voice")
    .events.find((e) => e.id === "voice-12");
  assert.notEqual(revised.fields.结果稿, inserted.fields.结果稿);
  const specText = JSON.stringify(source);
  assert(!/近期表达|识别上下文|五小时任务|相关人物索引|needsContext|最后有效改稿/.test(specText));
});
