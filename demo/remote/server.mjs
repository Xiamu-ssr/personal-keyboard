import http from "node:http";
import net from "node:net";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import { DedicatedEmulator } from "./emulator.mjs";
export function createRemoteDemo({
  origin,
  emulator = new DedicatedEmulator({ origin }),
  prefix = "/keyboard-spec/live/",
} = {}) {
  if (!origin) throw Error("Remote demo needs an explicit origin");
  const sessions = new Map(),
    attempts = new Map(),
    sockets = new Set();
  let owner = null,
    starting = null,
    stopping = null;
  const releaseStopped = () => {
    if (emulator.phase === "stopped" && !starting && !stopping) owner = null;
  };
  const json = (res, code, value, headers = {}) => {
    res.writeHead(code, {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
      ...headers,
    });
    res.end(JSON.stringify(value));
  };
  const sessionFor = (req) => {
    const url = new URL(req.url, "http://local");
    const pathSession = url.pathname.startsWith(prefix + "session/")
      ? url.pathname.slice((prefix + "session/").length).split("/")[0]
      : "";
    const sid = req.headers["x-demo-session"] || pathSession;
    return typeof sid === "string" &&
      /^[a-f0-9]{48}$/.test(sid) &&
      sessions.get(sid) > Date.now()
      ? sid
      : null;
  };
  const validOrigin = (req) => req.headers.origin === origin;
  const limited = (req) => {
    const peer = req.headers["x-real-ip"] || req.socket.remoteAddress;
    const now = Date.now(),
      old = attempts.get(peer);
    const entry =
      old && old.since > now - 60_000 ? old : { since: now, count: 0 };
    entry.count++;
    attempts.set(peer, entry);
    if (attempts.size > 1000)
      for (const [p, v] of attempts)
        if (v.since < now - 60_000) attempts.delete(p);
    return entry.count > 8;
  };
  const current = (sid) => {
    releaseStopped();
    if (owner && owner !== sid) return { phase: "occupied" };
    const state = emulator.status();
    if (state.phase !== "running") return state;
    return { ...state, viewer: prefix + "session/" + sid + "/viewer/" };
  };
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, "http://local");
      if (req.method === "POST" && !validOrigin(req))
        return json(res, 403, { error: "Origin rejected" });
      if (url.pathname === prefix && req.method === "GET") {
        res.writeHead(200, {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
          "Content-Security-Policy": `default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors ${origin} https://xiamu-ssr.github.io; connect-src 'self' wss:; object-src 'none'; base-uri 'self'`,
          "Referrer-Policy": "no-referrer",
        });
        return res.end(
          await fs.readFile(new URL("./viewer.html", import.meta.url)),
        );
      }
      if (url.pathname === prefix + "api/session" && req.method === "POST") {
        if (limited(req))
          return json(res, 429, { error: "尝试过多，请一分钟后重试" });
        let raw = "";
        for await (const chunk of req) {
          raw += chunk;
          if (raw.length > 512)
            return json(res, 413, { error: "Body too large" });
        }
        if (raw.trim() && raw.trim() !== "{}")
          return json(res, 400, { error: "Invalid session request" });
        const sid = randomBytes(24).toString("hex");
        for (const [s, t] of sessions) if (t < Date.now()) sessions.delete(s);
        sessions.set(sid, Date.now() + 60 * 60_000);
        return json(res, 200, { session: sid });
      }
      const sid = sessionFor(req);
      if (!sid) return json(res, 401, { error: "请重新连接演示" });
      const sessionPrefix = prefix + "session/" + sid + "/";
      const route = url.pathname.startsWith(sessionPrefix)
        ? url.pathname.slice(sessionPrefix.length)
        : url.pathname.slice(prefix.length);
      if (req.method === "GET" && route === "api/status")
        return json(res, 200, current(sid));
      if (req.method === "POST" && route === "api/start") {
        releaseStopped();
        if ((owner && owner !== sid) || stopping)
          return json(res, 409, { error: "演示正在使用中，请稍后重试" });
        if (emulator.phase === "running") return json(res, 200, current(sid));
        if (!starting) {
          owner = sid;
          starting = Promise.resolve().then(() => emulator.start());
        }
        try {
          await starting;
        } finally {
          starting = null;
          releaseStopped();
        }
        return json(res, 200, current(sid));
      }
      if (req.method === "POST" && route === "api/stop") {
        releaseStopped();
        if (owner && owner !== sid)
          return json(res, 403, { error: "这不是你的演示" });
        for (const s of sockets) s.destroy();
        if (!stopping)
          stopping = (async () => {
            if (starting) await starting.catch(() => {});
            await emulator.stop();
          })();
        try {
          await stopping;
        } finally {
          stopping = null;
          owner = null;
        }
        return json(res, 200, current(sid));
      }
      if (req.method === "POST" && route === "api/ping") {
        if (owner === sid) emulator.touch();
        return json(res, 200, current(sid));
      }
      if (req.method === "GET" && route.startsWith("viewer/")) {
        if (owner !== sid) return json(res, 403, { error: "这不是你的演示" });
        if (emulator.phase !== "running")
          return json(res, 409, { error: "请先启动 Android" });
        const upstream = http.request(
          {
            host: "127.0.0.1",
            port: 8561,
            path: "/" + route.slice(7) + url.search,
            method: "GET",
          },
          (up) => {
            res.writeHead(up.statusCode, {
              "Content-Type":
                up.headers["content-type"] || "application/octet-stream",
              "Cache-Control": "no-store",
              "Referrer-Policy": "no-referrer",
            });
            up.pipe(res);
          },
        );
        upstream.once("error", () =>
          json(res, 502, { error: "Android 画面连接中断" }),
        );
        upstream.end();
        return;
      }
      json(res, 404, { error: "Not found" });
    } catch (e) {
      json(res, 400, { error: String(e.message).slice(0, 180) });
    }
  });
  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url, "http://local");
    const sid = sessionFor(req);
    if (
      !sid ||
      owner !== sid ||
      !validOrigin(req) ||
      emulator.phase !== "running" ||
      url.pathname !== prefix + "session/" + sid + "/viewer/" ||
      url.search !== ""
    ) {
      socket.end("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n");
      return;
    }
    const upstream = net.connect(8561, "127.0.0.1", () => {
      const lines = [
        `GET /${url.search} HTTP/1.1`,
        "Host: 127.0.0.1:8561",
        "Connection: Upgrade",
        "Upgrade: websocket",
        "Origin: " + origin,
      ];
      for (const h of [
        "sec-websocket-key",
        "sec-websocket-version",
        "sec-websocket-protocol",
        "sec-websocket-extensions",
      ])
        if (req.headers[h]) lines.push(h + ": " + req.headers[h]);
      upstream.write(lines.join("\r\n") + "\r\n\r\n");
      if (head.length) upstream.write(head);
      socket.pipe(upstream);
      upstream.pipe(socket);
    });
    sockets.add(socket);
    socket.once("close", () => {
      sockets.delete(socket);
      upstream.destroy();
    });
    socket.once("error", () => upstream.destroy());
    upstream.once("error", () => socket.destroy());
    upstream.once("close", () => socket.destroy());
  });
  const expiry = setInterval(() => {
    emulator
      .expire()
      .then(() => {
        if (emulator.phase === "stopped") for (const s of sockets) s.destroy();
      })
      .catch(() => {});
  }, 10_000);
  expiry.unref();
  return {
    server,
    emulator,
    async close() {
      clearInterval(expiry);
      for (const s of sockets) s.destroy();
      await emulator.stop();
      server.close();
    },
  };
}
