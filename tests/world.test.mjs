import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import YAML from "yaml";
import { validateWorld } from "../spec/world.mjs";
const source = YAML.parse(
  await fs.readFile(new URL("../spec/spec.yaml", import.meta.url), "utf8"),
);
const clone = () => structuredClone(source);

test("fixed domain colors and two workflows keep Cloud prompt ownership and model destinations", () => {
  const counts = validateWorld(source);
  assert.equal(counts.domains, 5);
  assert.equal(counts.workflows, 2);
  assert.equal(counts.prompts, 3);
  assert.deepEqual(
    source.workflows.map((f) => f.id),
    ["voice", "advisor"],
  );
  for (const flow of source.workflows) {
    const nodes = new Map(flow.nodes.map((n) => [n.id, n]));
    for (const node of nodes.values()) {
      if (!node.prompts.length) continue;
      assert.equal(node.domain, "cloud");
      assert(
        flow.edges.some(
          (e) => e.from === node.id && nodes.get(e.to).domain === "provider",
        ),
      );
    }
    assert(
      flow.nodes.some((n) => n.id.endsWith("_apply") && n.domain === "app"),
    );
    assert(
      flow.edges.some((e) => e.kind === "learning" && e.to.endsWith("_apply")),
    );
  }
});

test("graph rejects bypassing Cloud, orphan prompts and incomplete or dangling contracts", () => {
  for (const mutate of [
    (x) => {
      x.domains[1].color = x.domains[0].color;
    },
    (x) => {
      x.domains[0].documents = ["missing"];
    },
    (x) => {
      x.workflows[0].edges[0].to = "missing";
    },
    (x) => {
      x.workflows[0].nodes[0].input = "";
    },
    (x) => {
      x.workflows[0].nodes[0].rect[0] = 3000;
    },
    (x) => {
      x.prompts[0].owner = "app";
    },
    (x) => {
      x.prompts[0].source = "spec/prompts/rewrite.md";
    },
    (x) => {
      x.workflows.push(structuredClone(x.workflows[0]));
    },
    (x) => {
      const f = x.workflows[0],
        edge = f.edges.find(
          (e) => f.nodes.find((n) => n.id === e.to).domain === "provider",
        );
      edge.from = f.nodes.find((n) => n.domain === "app").id;
    },
    (x) => {
      const f = x.workflows[0],
        node = f.nodes.find((n) => n.prompts.includes("rewrite"));
      f.edges = f.edges.filter((e) => e.from !== node.id);
    },
  ]) {
    const spec = clone();
    mutate(spec);
    assert.throws(() => validateWorld(spec));
  }
});

test("compiled Spec uses the same entitlement config and contextual prompt documents", async () => {
  const html = await fs.readFile(
    new URL("../spec/index.html", import.meta.url),
    "utf8",
  );
  const compiled = JSON.parse(
    html.match(
      /<script id="spec-data" type="application\/json">([\s\S]*?)<\/script>/,
    )[1],
  );
  const catalog = JSON.parse(
    await fs.readFile(
      new URL("../cloud/config/plans.json", import.meta.url),
      "utf8",
    ),
  );
  assert.deepEqual(validateWorld(compiled), validateWorld(source));
  assert.deepEqual(compiled.catalog, catalog);
  assert.deepEqual(
    Object.keys(compiled.sources),
    source.documents.map((d) => d.id),
  );
  for (const p of source.prompts) {
    assert.equal(
      source.documents.find((d) => d.id === p.document).file,
      "../" + p.source,
    );
    assert(compiled.sources[p.document].html.trim().length > 0);
  }
  assert(!html.includes('id="library-dialog"'));
  assert(!/<script[^>]+src=/.test(html));
});
