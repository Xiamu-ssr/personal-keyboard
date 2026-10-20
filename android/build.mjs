import fs from "node:fs/promises";
import path from "node:path";
import { root, run, sdkRoot } from "../demo/tools.mjs";
const sdk = await sdkRoot(),
  dir = path.join(root, "android"),
  build = path.join(dir, "build");
const versions = (await fs.readdir(path.join(sdk, "build-tools"))).sort(
  (a, b) => a.localeCompare(b, undefined, { numeric: true }),
);
const tools = path.join(sdk, "build-tools", versions.at(-1));
const platform = (await fs.readdir(path.join(sdk, "platforms")))
  .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
  .at(-1);
const androidJar = path.join(sdk, "platforms", platform, "android.jar");
for (const d of ["classes", "generated", "dex"])
  await fs.mkdir(path.join(build, d), { recursive: true });
const step = async (exe, args) => {
  try {
    return await run(exe, args, {
      timeout: 120000,
      maxBuffer: 3 * 1024 * 1024,
    });
  } catch (e) {
    throw Error(
      path.basename(exe) +
        " failed: " +
        String(e.stderr || e.message).slice(-1800),
    );
  }
};
await step(path.join(tools, "aapt2"), [
  "compile",
  "--dir",
  path.join(dir, "res"),
  "-o",
  path.join(build, "resources.zip"),
]);
await step(path.join(tools, "aapt2"), [
  "link",
  "-o",
  path.join(build, "app.ap_"),
  "-I",
  androidJar,
  "--manifest",
  path.join(dir, "AndroidManifest.xml"),
  "--java",
  path.join(build, "generated"),
  path.join(build, "resources.zip"),
]);
async function files(dir, ext) {
  let out = [];
  for (const e of await fs.readdir(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...(await files(p, ext)));
    else if (p.endsWith(ext)) out.push(p);
  }
  return out;
}
await step("javac", [
  "-encoding",
  "UTF-8",
  "-source",
  "8",
  "-target",
  "8",
  "-classpath",
  androidJar,
  "-d",
  path.join(build, "classes"),
  ...(await files(path.join(dir, "src"), ".java")),
  ...(await files(path.join(build, "generated"), ".java")),
]);
await step(path.join(tools, "d8"), [
  "--min-api",
  "30",
  "--lib",
  androidJar,
  "--output",
  path.join(build, "dex"),
  ...(await files(path.join(build, "classes"), ".class")),
]);
const unsigned = path.join(build, "unsigned.apk");
await fs.copyFile(path.join(build, "app.ap_"), unsigned);
await step("zip", ["-q", "-j", unsigned, path.join(build, "dex/classes.dex")]);
const aligned = path.join(build, "aligned.apk");
await step(path.join(tools, "zipalign"), ["-f", "4", unsigned, aligned]);
const key = path.join(build, "demo.keystore");
try {
  await fs.access(key);
} catch {
  await step("keytool", [
    "-genkeypair",
    "-keystore",
    key,
    "-storepass",
    "android",
    "-keypass",
    "android",
    "-alias",
    "demo",
    "-dname",
    "CN=Personal Keyboard Demo",
    "-keyalg",
    "RSA",
    "-keysize",
    "2048",
    "-validity",
    "3650",
  ]);
  await fs.chmod(key, 0o600);
}
const apk = path.join(build, "personal-keyboard-demo.apk");
await step(path.join(tools, "apksigner"), [
  "sign",
  "--ks",
  key,
  "--ks-pass",
  "pass:android",
  "--key-pass",
  "pass:android",
  "--out",
  apk,
  aligned,
]);
await step(path.join(tools, "apksigner"), ["verify", apk]);
const { createHash } = await import("node:crypto");
const sha256 = createHash("sha256")
  .update(await fs.readFile(apk))
  .digest("hex");
await fs.writeFile(
  path.join(build, "artifact.json"),
  JSON.stringify(
    {
      package: "app.personalkeyboard.demo",
      version: "0.0.1",
      sha256,
      bytes: (await fs.stat(apk)).size,
      production: false,
    },
    null,
    2,
  ),
);
console.log("Built native demo APK: " + apk + "\nSHA256 " + sha256);
