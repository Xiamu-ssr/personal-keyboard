import { Palettes } from './advisor/palette.mjs';
import { migratePalettes } from './advisor/migrate.mjs';
import { migrateDefaultSlots } from './advisor/defaults-migration.mjs';
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { configuration } from "./config.mjs";
import { Database } from "./db/database.mjs";
import { Ledger, isUuid } from "./billing/ledger.mjs";
import { Accounts } from "./identity/accounts.mjs";
import { Invitations } from "./identity/invitations.mjs";
import { TextModels } from "./models/text.mjs";
import { connectAsr } from "./models/asr.mjs";
import { Jobs } from "./jobs/service.mjs";
import { SyncService } from "./sync/service.mjs";
import { PersonalMemory } from './memory/service.mjs';
import { MemoryModel } from './memory/model.mjs';
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

const { version: productVersion } = JSON.parse(await fs.readFile(new URL('./package.json', import.meta.url), 'utf8'));

export async function createCloudServer({
  cfg,
  db = new Database(cfg.databaseUrl),
  models = new TextModels(cfg),
  asrSocket,
  memoryModel = new MemoryModel(cfg),
} = {}) {
  await db.migrate(cfg.plans, cfg.pricing);
  await migratePalettes(db, cfg);
  await migrateDefaultSlots(db, cfg);
  await models.init();
  await memoryModel.init();
  const ledger = new Ledger(db, cfg),
    accounts = new Accounts(db, ledger, cfg),
    invitations = new Invitations(db),
    palettes = new Palettes(db, cfg),
    memory = new PersonalMemory(db, memoryModel, cfg),
    jobs = new Jobs(db, ledger, models, cfg, memory, palettes),
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
          version: productVersion,
          readiness: {
            account: true,
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
      if (method === 'GET' && route === '/v1/credit-pricing')
        return json(res, await ledger.pricing.get());
      if (method === "POST" && ["/v1/auth/register", "/v1/auth/login", "/v1/auth/recover"].includes(route)) {
        const action = route.split('/').at(-1);
        return json(res, await accounts[action](await jsonBody(req, 4096), ip(req)), action === 'register' ? 201 : 200);
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
        if (method === 'GET' && route === '/admin/api/credit-pricing') return json(res, await ledger.pricing.get());
        if (method === 'POST' && route === '/admin/api/credit-pricing') {
          const body = await jsonBody(req, 16384);
          return json(res, await ledger.pricing.save(actor, body.expectedVersion, body.definition));
        }
        if (method === "GET" && route === "/admin/api/overview")
          return json(res, await admin.overview());
        if (method === "GET" && route === "/admin/api/users")
          return json(res, await admin.users());
        if (method === 'GET' && route === '/admin/api/invitations')
          return json(res, await invitations.list());
        if (method === 'POST' && route === '/admin/api/invitations')
          return json(res, await invitations.create(actor, await jsonBody(req, 4096)), 201);
        if (method === 'POST' && route === '/admin/api/invitations/revoke')
          return json(res, await invitations.revoke(actor, (await jsonBody(req, 4096)).id));
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
            user: { id: user.id, handle: user.handle },
            entitlements: ledger.publicBalance(await ledger.balance(user.id)),
            services: {
              asr: cfg.asrReady,
              text: cfg.textReady,
              vision: cfg.visionReady,
              account: true,
            },
          });
        if (method === "POST" && route === "/v1/auth/logout")
          return json(res, await accounts.logout(user));
        if (method === 'POST' && route === '/v1/auth/reauth')
          return json(res, await accounts.reauthenticate(user, (await jsonBody(req, 4096)).password, ip(req)));
        if (method === 'POST' && route === '/v1/auth/password')
          return json(res, await accounts.changePassword(user, await jsonBody(req, 4096), ip(req)));
        if (method === 'POST' && route === '/v1/auth/recovery-code')
          return json(res, await accounts.rotateRecovery(user, (await jsonBody(req, 4096)).password, ip(req)));
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
            "注销前请重新验证账号密码",
            401,
          );
          const result = await accounts.delete(user, c => memory.remove(user, c));
          await sync.remove(user);
          const active = (
            await db.query(
              "SELECT id FROM jobs WHERE user_id=$1 AND status IN('running','queued')",
              [user.id],
            )
          ).rows;
          for (const j of active) await jobs.cancel(user, j.id);
          return json(res, result);
        }
        if (method === "POST" && route === "/v1/jobs")
          return json(
            res,
            await jobs.submit(user, await jsonBody(req, 2600000)),
            202,
          );
        if (method === 'GET' && route.startsWith('/v1/asr/requests/')) return json(res, await jobs.byRequest(user, route.slice('/v1/asr/requests/'.length)));
        if (method === 'GET' && route === '/v1/advisor/palette') return json(res, await palettes.get(user.id));
        if (method === 'POST' && route === '/v1/advisor/palette') return json(res, await palettes.set(user, await jsonBody(req, 12000)));
        if (method === 'GET' && route === '/v1/memory')
          return json(res, await memory.sync(user, Number(new URL(req.url, 'http://local').searchParams.get('after') || 0)));
        if (method === 'POST' && route === '/v1/memory')
          return json(res, await memory.mutate(user, await jsonBody(req, 300000)));
        if (method === 'POST' && route === '/v1/memory/history')
          return json(res, await memory.ingest(user, await jsonBody(req, 2000000)), 202);
        const match = /^\/v1\/jobs\/([0-9a-f-]+)(\/cancel)?$/.exec(route);
        if (match && method === "GET" && !match[2])
          return json(res, await jobs.get(user, match[1]));
        if (match && method === "POST" && match[2])
          return json(res, await jobs.cancel(user, match[1]));
        if (method === 'GET' && route === '/v1/jobs') return json(res, { jobs:
          (await db.query("SELECT id,kind,status,consumed_credits,billing_snapshot,provider_usage,created_at FROM jobs WHERE user_id=$1 AND billing_snapshot ? 'version' ORDER BY created_at DESC LIMIT 50", [user.id])).rows
            .map(row => ({ id: row.id, kind: row.kind, status: row.status, credits: Number(row.consumed_credits) / 1000,
              pricingVersion: Number(row.billing_snapshot.version || 0), createdAt: row.created_at,
              billing: { inputTokens: row.provider_usage?.prompt_tokens ?? row.provider_usage?.input_tokens ?? null,
                outputTokens: row.provider_usage?.completion_tokens ?? row.provider_usage?.output_tokens ?? null,
                inputCreditsPerThousand: row.billing_snapshot.rate.inputMicroCnyPerMillion * row.billing_snapshot.markupBasisPoints / 10000000000,
                outputCreditsPerThousand: row.billing_snapshot.rate.outputMicroCnyPerMillion * row.billing_snapshot.markupBasisPoints / 10000000000 }
            })) });
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
      requireValue(sockets.clients.size < 8, 'ASR_BUSY', '当前语音会话较多，请稍后再试', 429);
      sockets.handleUpgrade(req, socket, head, (ws) =>
        connectAsr(ws, user, { cfg, ledger, accounts, memory, makeSocket: asrSocket }),
      );
    })().catch((error) => {
      const status = error.status === 429 ? '429 Too Many Requests' : '401 Unauthorized';
      socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`);
    });
  });
  const queue = setInterval(() => { jobs.pump(); void memory.pump(); }, 1000),
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
    invitations,
    jobs,
    sync,
    memory,
    admin,
    async close() {
      clearInterval(queue);
      clearInterval(clean);
      for (const ws of sockets.clients) ws.close(1001, "Service closing");
      await jobs.stop();
      await memory.stop();
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
