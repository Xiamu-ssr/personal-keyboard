import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { root } from "./tools.mjs";
const manifest = JSON.parse(
  await fs.readFile(path.join(root, "spec/release.json"), "utf8"),
);
const product = JSON.parse(
  await fs.readFile(path.join(root, "package.json"), "utf8"),
);
const artifact = JSON.parse(
  await fs.readFile(path.join(root, "spec/demo/artifact.json"), "utf8"),
);
if (
  manifest.version !== product.version ||
  artifact.version !== manifest.version ||
  artifact.sha256 !== manifest.files["doudou-keyboard.apk"]?.sha256
)
  throw Error("Public build version mismatch");
if (Object.keys(manifest.files).length !== 10)
  throw Error("Expected APK and nine native screenshots");
for (const [name, expected] of Object.entries(manifest.files)) {
  if (
    !/^(doudou-keyboard\.apk|(?:home|typing|tools|voice|memory|people|advisor|settings|account)\.png)$/.test(
      name,
    )
  )
    throw Error("Unexpected public artifact");
  const bytes = await fs.readFile(path.join(root, "spec/demo", name));
  if (
    bytes.length !== expected.bytes ||
    createHash("sha256").update(bytes).digest("hex") !== expected.sha256
  )
    throw Error("Public artifact digest mismatch: " + name);
}
const media = JSON.parse(
  await fs.readFile(path.join(root, "spec/media.json"), "utf8"),
);
if (
  media.version !== manifest.version ||
  media.sourceApkSha256 !== manifest.files["doudou-keyboard.apk"].sha256
)
  throw Error("Recording APK version mismatch");
if (
  Object.keys(media.files).sort().join(",") !==
  "advisor-tour.mp4,keyboard-tour.mp4"
)
  throw Error("Unexpected recording manifest");
for (const [name, expected] of Object.entries(media.files)) {
  const bytes = await fs.readFile(path.join(root, "spec/demo", name));
  if (
    bytes.length !== expected.bytes ||
    createHash("sha256").update(bytes).digest("hex") !== expected.sha256
  )
    throw Error("Recording digest mismatch: " + name);
}
console.log(
  "Verified APK, nine native screenshots and two APK recordings against their manifests.",
);
