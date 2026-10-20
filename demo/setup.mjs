import fs from "node:fs/promises";
import path from "node:path";
import { root, run } from "./tools.mjs";
const runtime = path.join(root, ".runtime"),
  vendor = path.join(runtime, "ws-scrcpy");
const lock = JSON.parse(
  await fs.readFile(path.join(root, "demo/third-party.lock.json"), "utf8"),
)["ws-scrcpy"];
await fs.mkdir(runtime, { recursive: true });
async function step(cmd, args, cwd = vendor) {
  console.log("Running " + cmd + " " + args.slice(0, 3).join(" "));
  try {
    const r = await run(cmd, args, {
      cwd,
      timeout: 240000,
      maxBuffer: 8 * 1024 * 1024,
      env: { ...process.env, NODE_OPTIONS: "--max-old-space-size=768" },
    });
    if (r.stdout) console.log(r.stdout.slice(-1500));
  } catch (e) {
    throw Error(
      cmd +
        " failed: " +
        String(e.stderr || e.stdout || e.message).slice(-2400),
    );
  }
}
try {
  await fs.access(path.join(vendor, ".git"));
} catch {
  await step(
    "git",
    ["clone", "--filter=blob:none", "--no-checkout", lock.repository, vendor],
    runtime,
  );
}
await step("git", ["fetch", "--depth", "1", "origin", lock.commit]);
await step("git", ["checkout", "--detach", lock.commit]);
const actual = (
  await run("git", ["rev-parse", "HEAD"], { cwd: vendor })
).stdout.trim();
if (actual !== lock.commit) throw Error("Unexpected viewer revision");
await fs.writeFile(
  path.join(vendor, "build.config.override.json"),
  JSON.stringify(
    {
      SCRCPY_LISTENS_ON_ALL_INTERFACES: false,
      USE_WEBCODECS: true,
      USE_BROADWAY: false,
      USE_H264_CONVERTER: true,
      USE_TINY_H264: false,
      INCLUDE_DEV_TOOLS: false,
      INCLUDE_ADB_SHELL: false,
      INCLUDE_FILE_LISTING: false,
      INCLUDE_APPL: false,
      INCLUDE_GOOG: true,
      PATHNAME: "/",
    },
    null,
    2,
  ),
);
const serverPath = path.join(vendor, "src/server/services/HttpServer.ts");
let source = await fs.readFile(serverPath, "utf8");
const old = "server.listen(port, () => {";
if (source.includes(old))
  source = source.replace(old, "server.listen(port, '127.0.0.1', () => {");
else if (!source.includes("server.listen(port, '127.0.0.1', () => {"))
  throw Error("Viewer listener source changed");
await fs.writeFile(serverPath, source);
const trackerPath=path.join(vendor,"src/server/goog-device/services/ControlCenter.ts");
let tracker=await fs.readFile(trackerPath,"utf8");
const trackerEntry="private handleConnected(udid: string, state: string): void {";
const selectedGuard="if (!process.env.PERSONAL_KEYBOARD_DEVICE || udid !== process.env.PERSONAL_KEYBOARD_DEVICE) return;";
if(!tracker.includes(selectedGuard)){
  if(!tracker.includes(trackerEntry))throw Error("Viewer device tracker source changed");
  tracker=tracker.replace(trackerEntry,trackerEntry+"\n        "+selectedGuard);
  await fs.writeFile(trackerPath,tracker);
}
const hasLock = await fs.access(path.join(vendor, "package-lock.json")).then(
  () => true,
  () => false,
);
await step("npm", [
  hasLock ? "ci" : "install",
  "--ignore-scripts",
  "--omit=optional",
  "--legacy-peer-deps",
  "--no-audit",
  "--no-fund",
]);
await step("npm", ["run", "dist"]);
await fs.writeFile(
  path.join(runtime, "viewer-ready.json"),
  JSON.stringify({
    commit: actual,
    builtAt: new Date().toISOString(),
    loopbackOnly: true,
    selectedDeviceOnly: true,
  }),
);
console.log("Android viewer ready. Start the Spec with npm run spec:serve.");
