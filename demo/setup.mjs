import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { build } from "esbuild";
import { root } from "./tools.mjs";
const lock = JSON.parse(
  await fs.readFile(path.join(root, "demo/third-party.lock.json"), "utf8"),
)["scrcpy-server"];
const folder = path.join(root, ".runtime/scrcpy"),
  file = path.join(folder, "scrcpy-server-v" + lock.version);
await fs.mkdir(folder, { recursive: true });
let bytes;
try {
  bytes = await fs.readFile(file);
} catch {}
if (
  !bytes ||
  createHash("sha256").update(bytes).digest("hex") !== lock.sha256
) {
  const response = await fetch(lock.url);
  if (!response.ok) throw Error("Official scrcpy server download failed");
  bytes = Buffer.from(await response.arrayBuffer());
  if (createHash("sha256").update(bytes).digest("hex") !== lock.sha256)
    throw Error("Official scrcpy server checksum mismatch");
  await fs.writeFile(file, bytes, { mode: 0o600 });
}
await build({
  entryPoints: [path.join(root, "demo/stream/client.mjs")],
  bundle: true,
  format: "esm",
  minify: true,
  target: "es2022",
  outfile: path.join(root, ".runtime/stream-web/bundle.js"),
});
console.log(
  "Pinned scrcpy " +
    lock.version +
    " and Tango WebCodecs viewer ready; loopback and selected-device only.",
);
