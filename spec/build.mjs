import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";
import { marked, Renderer } from "marked";
import { validateWorld } from "./world.mjs";

const dir = path.dirname(fileURLToPath(import.meta.url));
const promptRoot = path.resolve(dir, "../cloud/prompts") + path.sep;
const spec = YAML.parse(await fs.readFile(path.join(dir, "spec.yaml"), "utf8"));
const counts = validateWorld(spec);
const ids = new Set();
const documents = {};
for (const doc of spec.documents) {
  if (ids.has(doc.id)) throw Error("Duplicate document");
  ids.add(doc.id);
  const resolved = await fs.realpath(path.resolve(dir, doc.file));
  if (
    (!resolved.startsWith(dir + path.sep) &&
      !resolved.startsWith(promptRoot)) ||
    !doc.file.endsWith(".md")
  )
    throw Error("Document escaped spec directory");
  const source = await fs.readFile(resolved, "utf8");
  const renderer = new Renderer();
  renderer.html = ({ text }) =>
    text.replace(/&/g, "&amp;").replace(/</g, "&lt;");
  const html = marked.parse(source, { renderer });
  documents[doc.id] = { title: doc.title, html };
}
let verification = { status: "pending", checks: [] };
try {
  verification = JSON.parse(
    await fs.readFile(path.join(dir, "verification.json"), "utf8"),
  );
} catch {}
const [template, css, script, world, panels, catalog] = await Promise.all([
  ...["template.html", "style.css", "runtime.js", "world.mjs", "panels.js"].map(
    (f) => fs.readFile(path.join(dir, f), "utf8"),
  ),
  fs.readFile(path.resolve(dir, "../cloud/config/plans.json"), "utf8"),
]);
const json = JSON.stringify({
  ...spec,
  catalog: JSON.parse(catalog),
  sources: documents,
  verification,
})
  .replace(/</g, "\\u003c")
  .replace(/\u2028/g, "\\u2028")
  .replace(/\u2029/g, "\\u2029");
const output = template
  .replace("<!-- SPEC_STYLE -->", "<style>" + css + "</style>")
  .replace(
    "<!-- SPEC_DATA -->",
    '<script id="spec-data" type="application/json">' + json + "</script>",
  )
  .replace(
    "<!-- SPEC_SCRIPT -->",
    "<script>" +
      world.replaceAll("export function", "function").replaceAll("export class", "class") +
      "\n" +
      script +
      "\n" +
      panels +
      "</script>",
  );
await fs.writeFile(path.join(dir, "index.html"), output);
console.log(
  "Built spec/index.html: " +
    spec.domains.length +
    " domains, " +
    counts.workflows +
    " workflows, " +
    counts.relations +
    " relations, " +
    ids.size +
    " documents.",
);
