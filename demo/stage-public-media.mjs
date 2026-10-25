import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { root } from "./tools.mjs";

const destination = path.join(root, "spec/demo");
await fs.mkdir(destination, { recursive: true });
let staged = 0;
for (const [manifestName, extension, directory] of [
  ["release.json", ".png", "screenshots"],
  ["media.json", ".mp4", "recordings"],
]) {
  const manifest = JSON.parse(
    await fs.readFile(path.join(root, "spec", manifestName), "utf8"),
  );
  for (const [name, expected] of Object.entries(manifest.files)) {
    if (name === "doudou-keyboard.apk" && extension === ".png") continue;
    if (path.basename(name) !== name || path.extname(name) !== extension)
      throw Error("Unexpected public media: " + name);
    const bytes = await fs.readFile(
      path.join(root, "spec/media", directory, name),
    );
    if (
      bytes.length !== expected.bytes ||
      createHash("sha256").update(bytes).digest("hex") !== expected.sha256
    )
      throw Error("Public media digest mismatch: " + name);
    await fs.writeFile(path.join(destination, name), bytes);
    staged += 1;
  }
}
console.log(`Staged ${staged} verified images and recordings from spec/media/.`);
