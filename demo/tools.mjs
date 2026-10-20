import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";
export const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
export const run = promisify(execFile);
export async function sdkRoot() {
  for (const p of [
    process.env.ANDROID_SDK_ROOT,
    process.env.ANDROID_HOME,
    "/opt/homebrew/share/android-commandlinetools",
    path.join(os.homedir(), "Library/Android/sdk"),
  ].filter(Boolean)) {
    try {
      await fs.access(path.join(p, "platform-tools", "adb"));
      return p;
    } catch {}
  }
  throw Error("未找到 Android SDK；请设置 ANDROID_SDK_ROOT。");
}
export async function adb(serial, args, options = {}) {
  const sdk = await sdkRoot();
  return run(
    path.join(sdk, "platform-tools/adb"),
    serial ? ["-s", serial, ...args] : args,
    { timeout: 20000, maxBuffer: 4 * 1024 * 1024, ...options },
  );
}
export async function devices() {
  const { stdout } = await adb(null, ["devices", "-l"]);
  return stdout
    .split("\n")
    .slice(1)
    .filter((l) => /^\S+\s+device\b/.test(l))
    .map((l) => ({
      serial: l.split(/\s+/)[0],
      label:
        (l.match(/model:(\S+)/)?.[1] || "Android") +
        (l.startsWith("emulator-") ? " · 独立模拟器" : " · 真机"),
    }));
}
