import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";
import { validateWorld } from "./world.mjs";

const dir = path.dirname(fileURLToPath(import.meta.url));
const spec = YAML.parse(await fs.readFile(path.join(dir, "spec.yaml"), "utf8"));
const counts = validateWorld(spec);
for (const domain of spec.domains)
  for (const node of domain.nodes || [])
    if (node.format)
      node.format.preview = YAML.stringify(node.format.example, { lineWidth: 48 });
const [template, css, script, world, panels, catalog] = await Promise.all([
  ...["template.html", "style.css", "runtime.js", "world.mjs", "panels.js"].map(
    (f) => fs.readFile(path.join(dir, f), "utf8"),
  ),
  fs.readFile(path.resolve(dir, "../cloud/config/plans.json"), "utf8"),
]);
const json = JSON.stringify({
  ...spec,
  catalog: JSON.parse(catalog),
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
      world
        .replaceAll("export function", "function")
        .replaceAll("export class", "class") +
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
    counts.businesses +
    " business views, " +
    counts.lanes +
    " domain lanes, " +
    counts.prompts +
    " YAML prompts.",
);
