import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { configuration } from "./config.mjs";
import { Database } from "./db/database.mjs";
import { Ledger, isUuid } from "./billing/ledger.mjs";
import { Accounts } from "./identity/accounts.mjs";
import { AliyunSms } from "./identity/sms.mjs";
import { TextModels } from "./models/text.mjs";
import { connectAsr } from "./models/asr.mjs";
import { Jobs } from "./jobs/service.mjs";
import { SyncService } from "./sync/service.mjs";
import { Admin } from "./operator/admin.mjs";
import { randomToken, digest } from "./security.mjs";
import { requireValue } from "./http/errors.mjs";
import {
  jsonBody,
  json,
  securityHeaders,
  failure,
  bearer,
  cookie,
  sameOrigin,
} from "./http/router.mjs";

export async function createCloudServer({
  cfg,
  db = new Database(cfg.databaseUrl),
  sms = new AliyunSms(cfg),
  models = new TextModels(cfg),
  asrSocket,
} = {}) {
  await db.migrate(cfg.plans);
  await models.init();
  const ledger = new Ledger(db, cfg),
    accounts = new Accounts(db, ledger, cfg, sms),
    jobs = new Jobs(db, ledger, models, cfg),
    sync = new SyncService(db, ledger, cfg),
    admin = new Admin(db, ledger, cfg, models);
  await ledger.recover();
  const ip = (req) =>
    cfg.trustProxy
      ? String(req.headers["x-real-ip"] || req.socket.remoteAddress)
      : req.socket.remoteAddress;
  const server = http.createServer(async (req, res) => {
    securityHeaders(res);
    try {
      const rawPath = new URL(req.url, "http://cloud.local").pathname;
      requireValue(
        rawPath.startsWith(cfg.pathPrefix),
        "NOT_FOUND",
        "路径不存在",
        404,
      );
      const route = rawPath.slice(cfg.pathPrefix.length) || "/",
        method = req.method,
        mutating = !["GET", "HEAD"].includes(method);
      sameOrigin(req, cfg);
      if (method === "GET" && route === "/healthz") {
        await db.query("SELECT 1");
        return json(res, {
          status: "ok",
          version: "0.4.0-alpha.1",
          readiness: {
            sms: cfg.smsReady,
            asr: cfg.asrReady,
            text: cfg.textReady,
            vision: cfg.visionReady,
          },
          paidPurchases: false,
        });
      }
      if (method === "GET" && route === "/v1/catalog")
        return json(res, {
          plans: (
            await db.query(
              "SELECT definition FROM plans WHERE status='published'",
            )
          ).rows.map((r) => r.definition),
          metering: cfg.plans.metering,
          purchaseEnabled: false,
        });
      if (method === "POST" && route === "/v1/auth/challenges")
        return json(
          res,
          await accounts.challenge((await jsonBody(req)).phone, ip(req)),
          201,
        );
      if (method === "POST" && route === "/v1/auth/verify") {
        const b = await jsonBody(req);
        return json(res, await accounts.verify(b.challengeId, b.code));
      }
      if (method === "POST" && route === "/v1/auth/refresh")
        return json(
          res,
          await accounts.refresh((await jsonBody(req)).refreshToken),
        );
      if (route.startsWith("/admin")) {
        if (
          method === "GET" &&
          ["/admin", "/admin/", "/admin/admin.js", "/admin/admin.css"].includes(
            route,
          )
        ) {
          const file = route.endsWith(".js")
            ? "admin.js"
            : route.endsWith(".css")
              ? "admin.css"
              : "index.html";
          res.setHeader(
            "Content-Type",
            file.endsWith(".js")
              ? "text/javascript; charset=utf-8"
              : file.endsWith(".css")
                ? "text/css; charset=utf-8"
                : "text/html; charset=utf-8",
          );
          const html = await fs.readFile(
            new URL("./admin/" + file, import.meta.url),
            "utf8",
          );
          res.end(html.replaceAll("__API_BASE__", cfg.pathPrefix || ""));
          return;
        }
        if (method === "POST" && route === "/admin/api/login") {
          const b = await jsonBody(req);
          const session = await admin.login(b.password, ip(req));
          res.setHeader(
            "Set-Cookie",
            `keyboard_admin=${session.token}; HttpOnly; Secure; SameSite=Strict; Path=${cfg.pathPrefix || "/"}`,
          );
          return json(res, { csrf: session.csrf });
        }
        const session = await admin.authenticate(
            cookie(req),
            req.headers["x-csrf-token"],
            mutating,
          ),
          actor = "admin:" + session.id;
        if (method === "GET" && route === "/admin/api/session") {
          const csrf = randomToken();
          await db.query("UPDATE admin_sessions SET csrf_hash=$2 WHERE id=$1", [
            session.id,
            digest(csrf),
          ]);
          return json(res, { csrf });
        }
        if (method === "POST" && route === "/admin/api/logout") {
          await db.query("DELETE FROM admin_sessions WHERE id=$1", [
            session.id,
          ]);
          res.setHeader(
            "Set-Cookie",
            `keyboard_admin=; HttpOnly; Secure; SameSite=Strict; Max-Age=0; Path=${cfg.pathPrefix || "/"}`,
          );
          return json(res, { ok: true });
        }
        if (method === "GET" && route === "/admin/api/overview")
          return json(res, await admin.overview());
        if (method === "GET" && route === "/admin/api/users")
          return json(res, await admin.users());
        if (method === "GET" && route === "/admin/api/jobs")
          return json(res, await admin.jobs());
        if (method === "GET" && route === "/admin/api/plans")
          return json(res, await admin.plans());
        if (method === "POST" && route === "/admin/api/plans")
          return json(res, await admin.savePlan(actor, await jsonBody(req)));
        if (method === "POST" && route === "/admin/api/plans/publish")
          return json(
            res,
            await admin.publish(actor, (await jsonBody(req)).id),
          );
        if (method === "POST" && route === "/admin/api/grants")
          return json(res, await admin.grant(actor, await jsonBody(req)));
        if (method === "POST" && route === "/admin/api/users/status") {
          const b = await jsonBody(req);
          return json(res, await admin.status(actor, b.id, b.status));
        }
        if (method === "GET" && route === "/admin/api/audits")
          return json(
            res,
            (
              await db.query(
                "SELECT actor,action,target,detail,created_at FROM audits ORDER BY id DESC LIMIT 100",
              )
            ).rows,
          );
        if (method === "GET" && route === "/admin/api/orders")
          return json(
            res,
            (
              await db.query(
                "SELECT id,user_id,amount_minor,status,created_at,paid_at FROM orders ORDER BY created_at DESC LIMIT 100",
              )
            ).rows,
          );
      } else if (route.startsWith("/v1/")) {
        const user = await accounts.authenticate(bearer(req));
        if (method === "GET" && route === "/v1/account")
          return json(res, {
            user: { id: user.id, phoneHint: user.phone_hint },
            entitlements: ledger.publicBalance(await ledger.balance(user.id)),
            services: {
              asr: cfg.asrReady,
              text: cfg.textReady,
              vision: cfg.visionReady,
              sms: cfg.smsReady,
            },
          });
        if (method === "POST" && route === "/v1/auth/logout")
          return json(res, await accounts.logout(user));
        if (method === "POST" && route === "/v1/account/delete") {
          const b = await jsonBody(req);
          requireValue(
            b.confirm === true,
            "CONFIRMATION_REQUIRED",
            "请明确确认注销",
          );
          requireValue(
            Date.now() - new Date(user.verified_at) < 600000,
            "REAUTH_REQUIRED",
            "注销前请重新验证手机号",
            401,
          );
          await sync.remove(user);
          const active = (
            await db.query(
              "SELECT id FROM jobs WHERE user_id=$1 AND status IN('running','queued')",
              [user.id],
            )
          ).rows;
          for (const j of active) await jobs.cancel(user, j.id);
          return json(res, await accounts.delete(user));
        }
        if (method === "POST" && route === "/v1/jobs")
          return json(
            res,
            await jobs.submit(user, await jsonBody(req, 2600000)),
            202,
          );
        const match = /^\/v1\/jobs\/([0-9a-f-]+)(\/cancel)?$/.exec(route);
        if (match && method === "GET" && !match[2])
          return json(res, await jobs.get(user, match[1]));
        if (match && method === "POST" && match[2])
          return json(res, await jobs.cancel(user, match[1]));
        if (method === "GET" && route === "/v1/jobs")
          return json(
            res,
            (
              await db.query(
                "SELECT id,kind,status,consumed_asr,consumed_advisor,error_code,created_at FROM jobs WHERE user_id=$1 ORDER BY created_at DESC LIMIT 30",
                [user.id],
              )
            ).rows,
          );
        if (method === "POST" && route === "/v1/sync/key")
          return json(res, await sync.key(user));
        if (method === "GET" && route === "/v1/sync")
          return json(res, await sync.read(user));
        if (method === "PUT" && route === "/v1/sync")
          return json(
            res,
            await sync.write(user, await jsonBody(req, 7100000)),
          );
        if (method === "DELETE" && route === "/v1/sync")
          return json(res, await sync.remove(user));
        if (method === "POST" && route === "/v1/orders")
          return json(
            res,
            {
              error: {
                code: "PAYMENTS_NOT_READY",
                message: "付费套餐与商户支付尚未开放",
              },
            },
            503,
          );
      }
      json(res, { error: { code: "NOT_FOUND", message: "接口不存在" } }, 404);
    } catch (e) {
      if (!e.code) console.error("Request failed");
      failure(res, e);
    }
  });
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;
  const sockets = new WebSocketServer({ noServer: true, maxPayload: 16000 });
  server.on("upgrade", (req, socket, head) => {
    void (async () => {
      requireValue(
        new URL(req.url, "http://local").pathname ===
          cfg.pathPrefix + "/v1/asr",
        "NOT_FOUND",
        "接口不存在",
        404,
      );
      sameOrigin(req, cfg);
      const token = bearer(req),
        user = await accounts.authenticate(token);
      user.accessToken = token;
      sockets.handleUpgrade(req, socket, head, (ws) =>
        connectAsr(ws, user, { cfg, ledger, accounts, makeSocket: asrSocket }),
      );
    })().catch(() => {
      socket.end("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
    });
  });
  const queue = setInterval(() => jobs.pump(), 1000),
    clean = setInterval(
      () => db.cleanup().catch(() => console.error("Cleanup pending")),
      600000,
    );
  queue.unref();
  clean.unref();
  return {
    server,
    db,
    ledger,
    accounts,
    jobs,
    sync,
    admin,
    async close() {
      clearInterval(queue);
      clearInterval(clean);
      for (const ws of sockets.clients) ws.close(1001, "Service closing");
      await jobs.stop();
      await new Promise((r) => server.close(r));
      await db.close();
    },
  };
}
if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const cfg = await configuration(),
    app = await createCloudServer({ cfg });
  app.server.listen(cfg.port, cfg.host, () =>
    console.log("Keyboard Cloud listening"),
  );
  let closing = false;
  for (const signal of ["SIGTERM", "SIGINT"])
    process.on(signal, async () => {
      if (closing) return;
      closing = true;
      await app.close();
      process.exit(0);
    });
}
