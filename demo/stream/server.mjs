import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { WebSocketServer } from "ws";
import { AdbServerClient } from "@yume-chan/adb";
import { AdbServerNodeTcpConnector } from "@yume-chan/adb-server-node-tcp";
import { AdbScrcpyClient, AdbScrcpyOptions3_3_3 } from "@yume-chan/adb-scrcpy";
import { root } from "../tools.mjs";
const serial = process.env.PERSONAL_KEYBOARD_DEVICE;
if (!serial) throw Error("An explicit selected Android device is required");
const jar = path.join(root, ".runtime/scrcpy/scrcpy-server-v3.3.3");
const serverPath = "/data/local/tmp/doudou-scrcpy-3.3.3.jar";
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://local");
  const names = {
    "/": ["demo/stream/index.html", "text/html; charset=utf-8"],
    "/bundle.js": [
      ".runtime/stream-web/bundle.js",
      "text/javascript; charset=utf-8",
    ],
  };
  const file = names[url.pathname];
  if (req.method !== "GET" || !file) {
    res.writeHead(404);
    res.end();
    return;
  }
  try {
    res.writeHead(200, {
      "Content-Type": file[1],
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
    });
    res.end(await fs.readFile(path.join(root, file[0])));
  } catch {
    res.writeHead(503);
    res.end("Run npm run demo:setup");
  }
});
const wss = new WebSocketServer({
  noServer: true,
  maxPayload: 2048,
  perMessageDeflate: false,
});
let active = null,
  closing = false;
server.on("upgrade", (req, socket, head) => {
  const allowed = [
    "http://127.0.0.1:8561",
    "http://localhost:8561",
    process.env.PERSONAL_KEYBOARD_ORIGIN,
  ].filter(Boolean);
  if (req.url !== "/" || !allowed.includes(req.headers.origin) || active) {
    socket.end("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n");
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws));
});
wss.on("connection", async (ws) => {
  const connection = { ws, client: null, adb: null };
  active = connection;
  let width = 0,
    height = 0,
    commands = Promise.resolve();
  const send = (value) => {
    if (ws.readyState === ws.OPEN) ws.send(value);
  };
  ws.on("message", (raw) => {
    if (!connection.client || raw.length > 2048) return;
    commands = commands
      .then(async () => {
        const value = JSON.parse(raw.toString()),
          control = connection.client?.controller;
        if (!control) return;
        if (
          value.type === "touch" &&
          [0, 1, 2].includes(value.action) &&
          Number.isFinite(value.x) &&
          Number.isFinite(value.y) &&
          width > 0 &&
          height > 0
        ) {
          await control.injectTouch({
            action: value.action,
            pointerId: 0n,
            pointerX: Math.max(0, Math.min(width - 1, Math.round(value.x))),
            pointerY: Math.max(0, Math.min(height - 1, Math.round(value.y))),
            videoWidth: width,
            videoHeight: height,
            pressure: value.action === 1 ? 0 : 1,
            actionButton: value.action === 0 ? 1 : 0,
            buttons: value.action === 1 ? 0 : 1,
          });
        } else if (
          value.type === "key" &&
          Number.isInteger(value.keyCode) &&
          ([3, 4, 19, 20, 21, 22, 62, 66, 67].includes(value.keyCode) ||
            (value.keyCode >= 7 && value.keyCode <= 16) ||
            (value.keyCode >= 29 && value.keyCode <= 54))
        ) {
          await control.injectKeyCode({
            action: 0,
            keyCode: value.keyCode,
            repeat: 0,
            metaState: 0,
          });
          await control.injectKeyCode({
            action: 1,
            keyCode: value.keyCode,
            repeat: 0,
            metaState: 0,
          });
        }
      })
      .catch(() => {});
  });
  ws.on("close", async () => {
    try {
      await connection.client?.close();
      await connection.adb?.close();
    } catch {}
    if (active === connection) active = null;
  });
  try {
    const adbServer = new AdbServerClient(
      new AdbServerNodeTcpConnector({ host: "127.0.0.1", port: 5037 }),
    );
    const adb = await adbServer.createAdb({ serial });
    connection.adb = adb;
    const bytes = await fs.readFile(jar);
    await AdbScrcpyClient.pushServer(
      adb,
      new ReadableStream({
        start(controller) {
          controller.enqueue(bytes);
          controller.close();
        },
      }),
      serverPath,
    );
    const client = await AdbScrcpyClient.start(
      adb,
      serverPath,
      new AdbScrcpyOptions3_3_3({
        audio: false,
        control: true,
        videoCodec: "h264",
        maxSize: 1280,
        videoBitRate: 1_500_000,
        maxFps: 24,
        clipboardAutosync: false,
        stayAwake: true,
        powerOffOnClose: false,
        tunnelForward: true,
      }),
    );
    connection.client = client;
    const output = client.output.getReader();
    (async () => {
      while (!(await output.read()).done) {}
    })().catch(() => {});
    if (ws.readyState !== ws.OPEN) {
      await client.close();
      return;
    }
    const video = await client.videoStream;
    width = video.width;
    height = video.height;
    video.sizeChanged((size) => {
      width = size.width;
      height = size.height;
    });
    send(
      JSON.stringify({
        type: "metadata",
        codec: video.metadata.codec,
        width,
        height,
      }),
    );
    const reader = video.stream.getReader();
    while (ws.readyState === ws.OPEN) {
      const { value, done } = await reader.read();
      if (done) break;
      // Bounded network pressure: close a stalled viewer rather than buffer Android video forever.
      if (ws.bufferedAmount > 4 * 1024 * 1024) {
        ws.close(1008, "Slow viewer");
        break;
      }
      const header = Buffer.alloc(9);
      header[0] = value.type === "configuration" ? 0 : value.keyframe ? 2 : 1;
      header.writeBigUInt64BE(value.pts || 0n, 1);
      send(Buffer.concat([header, value.data]));
    }
  } catch (error) {
    console.error("Selected Android video failed:", error.message);
    send(
      JSON.stringify({
        type: "error",
        message: "视频服务连接失败，可重新连接",
      }),
    );
    ws.close(1011);
  }
});
server.listen(8561, "127.0.0.1");
async function close() {
  if (closing) return;
  closing = true;
  active?.ws.close();
  try {
    await active?.client?.close();
    await active?.adb?.close();
  } catch {}
  wss.close();
  server.close();
  process.exit(0);
}
process.once("SIGINT", close);
process.once("SIGTERM", close);
