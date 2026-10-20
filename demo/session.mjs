import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";
import { root, adb, devices } from "./tools.mjs";
const PACKAGE = "app.doudou.ime",
  IME = PACKAGE + "/.KeyboardService",
  CAPTURE = PACKAGE + "/" + PACKAGE + ".CaptureService";
const runtime = path.join(root, ".runtime");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function streamResources(serial) {
  const rows = (await adb(serial, ["forward", "--list"])).stdout
    .split("\n")
    .map((s) => s.trim().split(/\s+/));
  const forwards = rows
    .filter(([device, , remote]) => device === serial && remote === "tcp:8886")
    .map(([, local]) => local);
  let pids = [];
  try {
    pids = (await adb(serial, ["shell", "pidof", "app_process"])).stdout
      .trim()
      .split(/\s+/)
      .filter((p) => /^\d+$/.test(p));
  } catch (e) {
    if (e.code !== 1) throw e;
  }
  const servers = [];
  for (const pid of pids) {
    try {
      const args = (
        await adb(serial, ["shell", "cat", "/proc/" + pid + "/cmdline"])
      ).stdout.split("\0");
      if (args.includes("com.genymobile.scrcpy.Server")) servers.push(pid);
    } catch {}
  }
  return { forwards, servers };
}
export class DemoSession {
  child = null;
  state = null;
  starting = false;
  async status() {
    let list = [];
    let reason = "";
    try {
      list = await devices();
    } catch (e) {
      reason = "Android SDK / ADB 不可用";
    }
    return {
      devices: list,
      running: !!this.child && this.child.exitCode === null,
      viewerUrl: "http://127.0.0.1:8561/",
      reason,
    };
  }
  async start(options = {}) {
    if (this.starting) throw Error("正在准备演示，请等待本次操作完成。");
    this.starting = true;
    try {
      return await this.prepare(options);
    } finally {
      this.starting = false;
    }
  }
  async prepare({ serial, enableCapture = false } = {}) {
    if (this.child) throw Error("已有演示，请先停止并恢复。");
    const list = await devices();
    if (!list.some((d) => d.serial === serial))
      throw Error("请选择已授权且在线的设备。");
    const window = (await adb(serial, ["shell", "dumpsys", "window"])).stdout;
    if (
      /(?:mDreamingLockscreen|mShowingLockscreen|isKeyguardShowing)=true/.test(
        window,
      )
    )
      throw Error("设备目前锁屏，请本人解锁后再开始。不会自动解锁。");
    const apk = path.join(root, "android/app/build/outputs/apk/debug/app-debug.apk");
    try {
      await fs.access(apk);
    } catch {
      throw Error("请先运行 npm run demo:build 构建原生 APK。");
    }
    const viewer = path.join(runtime, "ws-scrcpy/dist/index.js");
    try {
      await fs.access(viewer);
    } catch {
      throw Error(
        "请先运行 npm run demo:setup 安装固定版本的 Android 画面组件。",
      );
    }
    await fs.mkdir(runtime, { recursive: true });
    const get = async (key) =>
      (
        await adb(serial, ["shell", "settings", "get", "secure", key])
      ).stdout.trim();
    const resources = await streamResources(serial);
    if (resources.servers.length)
      throw Error("选中设备已有投屏服务，请先在原工具中停止，再运行本演示。");
    this.state = {
      serial,
      oldIme: await get("default_input_method"),
      oldEnabledImes: await get("enabled_input_methods"),
      oldServices: await get("enabled_accessibility_services"),
      oldAccessibilityEnabled: await get("accessibility_enabled"),
      changed: !!enableCapture,
      oldForwards: resources.forwards,
      viewerStarted: false,
      createdAt: new Date().toISOString(),
    };
    await fs.writeFile(
      path.join(runtime, "device-session.json"),
      JSON.stringify(this.state, null, 2),
      { mode: 0o600 },
    );
    try {
      try {
        await adb(serial, ["install", "-r", apk], { timeout: 60000 });
      } catch {
        throw Error(
          "豆豆输入法 APK 安装尚未完成：请在设备上确认本次安装，再重试。输入法与读屏尚未切换。",
        );
      }
      if (enableCapture) {
        await adb(serial, ["shell", "ime", "enable", IME]);
        await adb(serial, ["shell", "ime", "set", IME]);
        const services =
          this.state.oldServices === "null" ? "" : this.state.oldServices;
        const all = services.split(":").filter(Boolean);
        if (!all.includes(CAPTURE)) all.push(CAPTURE);
        await adb(serial, [
          "shell",
          "settings",
          "put",
          "secure",
          "enabled_accessibility_services",
          all.join(":"),
        ]);
        await adb(serial, [
          "shell",
          "settings",
          "put",
          "secure",
          "accessibility_enabled",
          "1",
        ]);
      }
      await adb(serial, [
        "shell",
        "am",
        "start",
        "-n",
        PACKAGE + "/.MainActivity",
      ]);
      const config = path.join(runtime, "viewer-config.json");
      await fs.writeFile(
        config,
        JSON.stringify({
          runGoogTracker: true,
          runApplTracker: false,
          server: [{ secure: false, port: 8561 }],
          remoteHostList: [],
        }),
      );
      const log = await fs.open(path.join(runtime, "viewer.log"), "a");
      this.state.viewerStarted = true;
      await fs.writeFile(
        path.join(runtime, "device-session.json"),
        JSON.stringify(this.state, null, 2),
        { mode: 0o600 },
      );
      this.child = spawn(process.execPath, [viewer], {
        cwd: path.dirname(viewer),
        env: {
          ...process.env,
          WS_SCRCPY_CONFIG: config,
          PERSONAL_KEYBOARD_DEVICE: serial,
          NODE_OPTIONS: "--max-old-space-size=256",
        },
        stdio: ["ignore", log.fd, log.fd],
      });
      await log.close();
      let ready = false;
      for (let i = 0; i < 40; i++) {
        if (this.child.exitCode !== null) break;
        try {
          const response = await fetch("http://127.0.0.1:8561/");
          if (response.ok) {
            ready = true;
            break;
          }
        } catch {}
        await sleep(250);
      }
      if (!ready)
        throw Error("Android 画面组件未启动，请查看本机 .runtime/viewer.log。");
      return {
        viewerUrl: "http://127.0.0.1:8561/",
        message:
          "豆豆输入法已运行。未勾选临时授权时，请在设备上选择输入法并按需开启主动截屏；没有配置模型时不会生成回复。",
      };
    } catch (e) {
      await this.stop();
      throw e;
    }
  }
  async stop() {
    const child = this.child;
    this.child = null;
    if (child && child.exitCode === null) {
      child.kill("SIGINT");
      for (let i = 0; i < 20 && child.exitCode === null; i++) await sleep(100);
      if (child.exitCode === null) child.kill("SIGTERM");
      for (let i = 0; i < 20 && child.exitCode === null; i++) await sleep(100);
      if (child.exitCode === null) child.kill("SIGKILL");
    }
    const state = this.state;
    this.state = null;
    if (!state) return;
    const restoreFailures = [];
    if (state.viewerStarted)
      try {
        const current = await streamResources(state.serial);
        for (const local of current.forwards)
          if (!state.oldForwards.includes(local))
            await adb(state.serial, ["forward", "--remove", local]);
        // No stream was present at start. Verify the exact server arguments before ending this demo's process.
        for (const pid of current.servers) {
          const args = (
            await adb(state.serial, [
              "shell",
              "cat",
              "/proc/" + pid + "/cmdline",
            ])
          ).stdout.split("\0");
          if (args.includes("1.19-ws8") && args.includes("8886"))
            await adb(state.serial, ["shell", "kill", pid]);
        }
      } catch {
        restoreFailures.push("投屏进程或端口清理需复核");
      }
    if (state.changed) {
      try {
        const current = (
          await adb(state.serial, [
            "shell",
            "settings",
            "get",
            "secure",
            "default_input_method",
          ])
        ).stdout.trim();
        if (
          current === IME ||
          current === PACKAGE + "/" + PACKAGE + ".KeyboardService"
        )
          await adb(state.serial, ["shell", "ime", "set", state.oldIme]);
        if (!state.oldEnabledImes.includes(PACKAGE))
          await adb(state.serial, ["shell", "ime", "disable", IME]);
        const now = (
          await adb(state.serial, [
            "shell",
            "settings",
            "get",
            "secure",
            "enabled_accessibility_services",
          ])
        ).stdout
          .trim()
          .split(":")
          .filter((x) => x && x !== "null");
        const originally = state.oldServices.split(":");
        const keep = now.filter(
          (x) => x !== CAPTURE || originally.includes(CAPTURE),
        );
        if (keep.length)
          await adb(state.serial, [
            "shell",
            "settings",
            "put",
            "secure",
            "enabled_accessibility_services",
            keep.join(":"),
          ]);
        else {
          await adb(state.serial, [
            "shell",
            "settings",
            "delete",
            "secure",
            "enabled_accessibility_services",
          ]);
          await adb(state.serial, [
            "shell",
            "settings",
            "put",
            "secure",
            "accessibility_enabled",
            state.oldAccessibilityEnabled === "1" ? "1" : "0",
          ]);
        }
      } catch {
        restoreFailures.push("原输入法与读屏设置恢复需复核");
      }
    }
    if (restoreFailures.length) {
      await fs.writeFile(
        path.join(runtime, "restore-needed.json"),
        JSON.stringify({ ...state, warnings: restoreFailures }),
        { mode: 0o600 },
      );
      throw Error(
        "设备恢复未完成：" +
          restoreFailures.join("；") +
          "。请恢复设备连接后处理。",
      );
    }
    await fs.rm(path.join(runtime, "device-session.json"), { force: true });
  }
}
