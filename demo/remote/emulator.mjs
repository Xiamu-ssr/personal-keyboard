import fs from "node:fs/promises";
import path from "node:path";
import net from "node:net";
import { spawn } from "node:child_process";
import { root, sdkRoot, adb, run } from "../tools.mjs";
import { DemoSession } from "../session.mjs";
const delay = (ms) => new Promise((r) => setTimeout(r, ms));
const home = path.join(root, ".runtime/web-emulator");
const avd = "doudou-keyboard-web35";
export const serial = "emulator-5580";
async function occupied(port) {
  return new Promise((resolve) => {
    const s = net.connect(port, "127.0.0.1");
    s.once("connect", () => {
      s.destroy();
      resolve(true);
    });
    s.once("error", () => resolve(false));
  });
}
export class DedicatedEmulator {
  process = null;
  session;
  constructor({ origin } = {}) {
    this.session = new DemoSession({ publicOrigin: origin });
  }
  phase = "stopped";
  startedAt = 0;
  lastActive = 0;
  async start() {
    if (this.phase !== "stopped") throw Error("已有演示正在准备或运行");
    this.phase = "starting";
    try {
      if (
        (await occupied(5580)) ||
        (await occupied(5581)) ||
        (await occupied(8561))
      )
        throw Error("专用模拟器端口被占用；未操作其他设备");
      const sdk = await sdkRoot();
      await fs.mkdir(home, { recursive: true });
      const env = { ...process.env, ANDROID_AVD_HOME: home };
      try {
        await fs.access(path.join(home, avd + ".ini"));
      } catch {
        const manager = path.join(sdk, "cmdline-tools/latest/bin/avdmanager");
        const child = spawn(
          manager,
          [
            "create",
            "avd",
            "--name",
            avd,
            "--package",
            "system-images;android-35;google_apis;arm64-v8a",
            "--device",
            "pixel_6",
          ],
          { env, stdio: ["pipe", "ignore", "pipe"] },
        );
        let error = "";
        child.stderr.on("data", (d) => (error += d));
        child.stdin.end("no\n");
        await new Promise((resolve, reject) => {
          child.once("error", reject);
          child.once("exit", (code) =>
            code === 0
              ? resolve()
              : reject(Error("创建专用模拟器失败：" + error.slice(-250))),
          );
        });
      }
      const log = await fs.open(path.join(home, "emulator.log"), "a", 0o600);
      this.process = spawn(
        path.join(sdk, "emulator/emulator"),
        [
          "-avd",
          avd,
          "-port",
          "5580",
          "-memory",
          "1536",
          "-cores",
          "2",
          "-no-window",
          "-no-audio",
          "-no-boot-anim",
          "-no-snapshot",
          "-wipe-data",
          "-gpu",
          "swiftshader_indirect",
        ],
        { env, stdio: ["ignore", log.fd, log.fd] },
      );
      await log.close();
      this.process.once("error", () => {});
      let booted = false;
      for (let i = 0; i < 100; i++) {
        if (this.process.exitCode !== null) break;
        try {
          if (
            (
              await adb(serial, ["shell", "getprop", "sys.boot_completed"], {
                timeout: 1500,
              })
            ).stdout.trim() === "1"
          ) {
            booted = true;
            break;
          }
        } catch {}
        await delay(1200);
      }
      if (!booted) throw Error("Android 启动超时；专用模拟器会自动清理");
      await adb(serial, ["shell", "wm", "size", "1080x2340"]);
      await adb(serial, ["shell", "wm", "density", "440"]);
      await adb(serial, [
        "shell",
        "settings",
        "put",
        "system",
        "screen_off_timeout",
        "1800000",
      ]);
      await adb(serial, [
        "shell",
        "settings",
        "put",
        "global",
        "stay_on_while_plugged_in",
        "3",
      ]);
      await this.session.start({ serial, enableCapture: true });
      await adb(serial, [
        "shell",
        "am",
        "start",
        "-W",
        "-f",
        "0x10008000",
        "-n",
        "app.doudou.ime/.app.MainActivity",
      ]);
      this.startedAt = this.lastActive = Date.now();
      this.phase = "running";
      return this.status();
    } catch (e) {
      await this.stop();
      if (e.cmd) {
        console.error(
          "Android preparation failed:",
          String(e.message).slice(-500),
        );
        throw Error("Android 演示准备失败，请重新启动；本次资源已回收");
      }
      throw e;
    }
  }
  status() {
    return {
      phase: this.phase,
      startedAt: this.startedAt,
      expiresAt: this.startedAt ? this.startedAt + 15 * 60_000 : 0,
    };
  }
  touch() {
    if (this.phase === "running") this.lastActive = Date.now();
  }
  async expire(now = Date.now()) {
    if (
      this.phase === "running" &&
      (now - this.startedAt >= 15 * 60_000 ||
        now - this.lastActive >= 2 * 60_000)
    )
      await this.stop();
  }
  async stop() {
    if (this.phase === "stopping") return;
    this.phase = "stopping";
    try {
      await this.session.stop();
    } finally {
      const child = this.process;
      if (child && child.exitCode === null) {
        try {
          await adb(serial, ["emu", "kill"], { timeout: 5000 });
        } catch {}
        for (let i = 0; i < 30 && child.exitCode === null; i++)
          await delay(100);
        if (child.exitCode === null) child.kill("SIGTERM");
        for (let i = 0; i < 20 && child.exitCode === null; i++)
          await delay(100);
        if (child.exitCode === null) child.kill("SIGKILL");
      }
      this.process = null;
      this.startedAt = 0;
      this.lastActive = 0;
      this.phase = "stopped";
    }
  }
}
