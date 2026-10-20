import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
import { root } from "./tools.mjs";
import { DemoSession } from "./session.mjs";
const mime = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".md": "text/plain; charset=utf-8",
  ".yaml": "text/plain; charset=utf-8",
  ".apk": "application/vnd.android.package-archive",
};
export function createSpecServer({ session = new DemoSession() } = {}) {
  const token = randomBytes(24).toString("hex");
  const json = (res, status, body) => {
    res.writeHead(status, {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    });
    res.end(JSON.stringify(body));
  };
  const server = http.createServer(async (req, res) => {
    try {
      const host = req.headers.host || "";
      if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/.test(host))
        return json(res, 403, { error: "Local host required" });
      if (req.headers.origin && req.headers.origin !== "http://" + host)
        return json(res, 403, { error: "Origin rejected" });
      const url = new URL(req.url, "http://" + host);
      if (url.pathname.startsWith("/api/")) {
        if (url.pathname === "/api/session" && req.method === "GET")
          return json(res, 200, { token });
        if (req.headers["x-demo-token"] !== token)
          return json(res, 403, {
            error: "Open the local Spec to authorize a demo action",
          });
        if (url.pathname === "/api/demo/status" && req.method === "GET")
          return json(res, 200, await session.status());
        if (["/api/demo/start", "/api/demo/stop"].includes(url.pathname)) {
          if (req.method !== "POST")
            return json(res, 405, { error: "POST required" });
          let raw = "";
          for await (const chunk of req) {
            raw += chunk;
            if (raw.length > 4096)
              return json(res, 413, { error: "Request too large" });
          }
          const body = raw ? JSON.parse(raw) : {};
          if (url.pathname.endsWith("/start"))
            return json(res, 200, await session.start(body));
          if (session.starting)
            throw Error("正在准备演示，待本次操作结束后再停止并恢复。");
          await session.stop();
          return json(res, 200, { status: "stopped" });
        }
        return json(res, 404, { error: "Unknown local API" });
      }
      if (req.method !== "GET" && req.method !== "HEAD")
        return json(res, 405, { error: "GET required" });
      let relative =
        decodeURIComponent(url.pathname).replace(/^\/+/, "") || "index.html";
      if (relative === "spec") relative = "index.html";
      if (relative.startsWith("spec/")) relative = relative.slice(5);
      if (relative.split("/").some((x) => x === ".." || x.startsWith(".")))
        return json(res, 404, { error: "Not found" });
      const target = path.resolve(root, "spec", relative),
        allowed = path.join(root, "spec") + path.sep;
      if (!target.startsWith(allowed) || !mime[path.extname(target)])
        return json(res, 404, { error: "Not found" });
      const data = await fs.readFile(target);
      res.writeHead(200, {
        "Content-Type": mime[path.extname(target)],
        "X-Content-Type-Options": "nosniff",
        "Cache-Control": "no-cache",
      });
      res.end(req.method === "HEAD" ? undefined : data);
    } catch (e) {
      json(res, e.code === "ENOENT" ? 404 : 400, {
        error:
          e.code === "ENOENT" ? "Not found" : String(e.message).slice(0, 180),
      });
    }
  });
  return { server, session };
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const app = createSpecServer();
  app.server.listen(8560, "127.0.0.1", () =>
    console.log(
      "Spec: http://127.0.0.1:8560 (Android demo starts only on request)",
    ),
  );
  let closing = false;
  const close = async () => {
    if (closing) return;
    closing = true;
    try {
      await app.session.stop();
    } finally {
      app.server.close();
      process.exit(0);
    }
  };
  process.once("SIGINT", close);
  process.once("SIGTERM", close);
}
