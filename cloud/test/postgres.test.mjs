import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { configuration } from "../config.mjs";
import { createCloudServer } from "../server.mjs";
import { uuid, passwordHash } from "../security.mjs";
import { WebSocket, WebSocketServer } from "ws";
const enabled = process.env.KEYBOARD_DATABASE_TEST === "1";
test(
  "PostgreSQL identity, concurrent settlement, model jobs and encrypted sync",
  { skip: !enabled },
  async () => {
    const env = JSON.parse(
      await fs.readFile(
        new URL("../../.runtime/v05/test-env.json", import.meta.url),
        "utf8",
      ),
    );
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "keyboard-sync-"));
    const cfg = await configuration({
      ...env,
      ADMIN_PASSWORD_HASH: passwordHash("test-password"),
      STORAGE_ROOT: root,
    });
    Object.assign(cfg, {
      smsReady: true,
      textReady: true,
      visionReady: true,
      asrReady: true,
      workspace: "test",
      modelKey: "test",
    });
    const modelCalls = [];
    const memoryModel = {
      async init() {},
      request(memory, version, history) { return { memory, version, history }; },
      async fits(request) { return !this.tooLarge; },
      async generate(request) {
        modelCalls.push(structuredClone(request));
        const h = JSON.parse(request.history.text);
        return { baseVersion: request.version, operations: h.resultText === '豆豆' && h.insertedText === '豆包' ? [{ kind: 'hotword', action: 'upsert', id: 'new:word', value: { text: '豆豆', variants: ['豆包'] }, sourceId: h.id, sourceRevision: h.revision, category: 'correction', confidence: 1 }] : [] };
      },
    };
    const sent = [],
      sms = {
        async send(phone, code) {
          sent.push({ phone, code });
        },
      };
    const models = {
      prompts: new Map([["advisor", { revision: "fixture" }]]),
      async init() {},
      async run(kind, input, images, signal) {
        await new Promise((r) => setTimeout(r, 25));
        if (input.intent === "fail") throw Error("fixture failure");
        this.lastInput = input;
        return {
          result:
            kind === "advisor"
              ? {
                  messages: [{ speaker: "other", text: "fixture visible chat", image: 1 }],
                  replies: [
                    { style: "温和", text: "fixture-a" },
                    { style: "直接", text: "fixture-b" },
                    { style: "轻松", text: "fixture-c" },
                  ],
                }
              : { text: "fixture-polish" },
          provider: { model: "fixture-text", usage: { input_tokens: 10 } },
        };
      },
    };
    const provider = new WebSocketServer({ port: 0, host: "127.0.0.1" });
    await new Promise((r) => provider.once("listening", r));
    let audioBytes = 0;
    provider.on("connection", (s) =>
      s.on("message", (b, binary) => {
        if (binary) {
          audioBytes += b.length;
          return;
        }
        const m = JSON.parse(b.toString());
        if (m.header.action === "run-task")
          s.send(JSON.stringify({ header: { event: "task-started" } }));
        if (m.header.action === "finish-task") {
          s.send(
            JSON.stringify({
              header: { event: "result-generated" },
              payload: {
                output: {
                  sentence: {
                    sentence_id: 1,
                    text: "豆豆你好",
                    sentence_end: true,
                  },
                },
              },
            }),
          );
          s.send(JSON.stringify({ header: { event: "task-finished" } }));
        }
      }),
    );
    const app = await createCloudServer({
      cfg,
      sms,
      models,
      memoryModel,
      asrSocket: () =>
        new WebSocket("ws://127.0.0.1:" + provider.address().port),
    });
    app.server.listen(0, "127.0.0.1");
    await new Promise((r) => app.server.once("listening", r));
    const base = "http://127.0.0.1:" + app.server.address().port;
    try {
      await app.db.query(
        "TRUNCATE users,plans,challenges,sessions,jobs,job_allocations,grants,audits,admin_sessions,admin_attempts,sync_keys,sync_manifests,orders,payment_nonces CASCADE",
      );
      await app.db.migrate(cfg.plans);
      const request = async (route, body, token, method) => {
        const r = await fetch(base + route, {
          method: method || (body ? "POST" : "GET"),
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: "Bearer " + token } : {}),
          },
          body: body ? JSON.stringify(body) : undefined,
        });
        return { status: r.status, value: await r.json() };
      };
      const challenge = (
        await request("/v1/auth/challenges", { phone: "13800001234" })
      ).value;
      assert.equal(sent.length, 1);
      assert.equal(
        (
          await request("/v1/auth/verify", {
            challengeId: challenge.challengeId,
            code: "000000",
          })
        ).status,
        401,
      );
      assert.equal(
        (
          await app.db.query("SELECT attempts FROM challenges WHERE id=$1", [
            challenge.challengeId,
          ])
        ).rows[0].attempts,
        1,
      );
      let auth = (
        await request("/v1/auth/verify", {
          challengeId: challenge.challengeId,
          code: sent[0].code,
        })
      ).value;
      assert(auth.accessToken);
      assert.equal(
        (
          await request("/v1/auth/verify", {
            challengeId: challenge.challengeId,
            code: sent[0].code,
          })
        ).status,
        401,
      );
      const uid = auth.user.id,
        token = auth.accessToken,
        initial = (await request("/v1/account", null, token)).value;
      assert.equal(initial.entitlements.asr.available, 10000);
      assert.equal(initial.entitlements.advisor.available, 20);
      const id = uuid(),
        input = { context: "", polish: true };
      const reservations = await Promise.all(
        Array.from({ length: 8 }, () =>
          app.ledger.reserve(uid, id, "asr", input),
        ),
      );
      assert.equal(reservations.filter((x) => x.created).length, 1);
      const job = reservations[0].job;
      await Promise.all(
        Array.from({ length: 8 }, () =>
          app.ledger.finish(job.id, "succeeded", { text: "你 好 👩" }),
        ),
      );
      assert.equal((await app.ledger.balance(uid)).asr.used, 3);
      await assert.rejects(
        app.ledger.reserve(uid, id, "asr", { ...input, context: "changed" }),
        /同一请求/,
      );
      const r1 = await app.ledger.reserve(uid, uuid(), "advisor", {
          context: "x",
        }),
        r2 = await app.ledger.reserve(uid, uuid(), "advisor", { context: "x" });
      assert.equal((await app.ledger.balance(uid)).advisor.reserved, 2);
      await app.ledger.finish(
        r1.job.id,
        "failed",
        null,
        {},
        { code: "TEST", message: "fixture" },
      );
      await app.ledger.finish(r2.job.id, "cancelled");
      assert.equal((await app.ledger.balance(uid)).advisor.available, 20);
      // Canonical memory is free, CAS-protected, and applied on the server.
      const wordId = uuid(), personId = uuid(), factId = uuid(), styleId = uuid();
      const manual = { requestId: uuid(), baseVersion: 0, operations: [
        { kind: 'hotword', action: 'upsert', id: wordId, value: { text: '云杉', variants: [] } },
        { kind: 'person', action: 'upsert', id: personId, value: { name: '小林', aliases: ['林同学'] } },
        { kind: 'fact', action: 'upsert', id: factId, personId, value: { key: '公司', value: '海岚', scope: '当前' } },
        { kind: 'style', action: 'upsert', id: styleId, value: { scene: '工作', rules: ['简洁'], examples: [] } },
      ] };
      assert.equal((await request('/v1/memory', manual, token)).status, 200);
      assert.equal((await request('/v1/memory', manual, token)).value.version, 1);
      assert.equal((await request('/v1/memory', { ...manual, operations: [] }, token)).status, 409);
      const race = await Promise.all(['海岚甲', '海岚乙'].map(value => request('/v1/memory', { requestId: uuid(), baseVersion: 1, operations: [{ kind: 'fact', action: 'upsert', personId, id: factId, value: { key: '公司', value, scope: '当前' } }] }, token)));
      assert.deepEqual(race.map(r => r.status).sort(), [200, 409]);
      const memory = (await request('/v1/memory', null, token)).value;
      assert.equal(memory.memory.people[0].name, '小林'); assert.equal(memory.version, 2);
      assert.equal((await request('/v1/memory?after=1', null, token)).value.changes[0].group, 'people');
      assert.equal((await request('/v1/jobs', { requestId: uuid(), kind: 'advisor', input: { context: 'OCR-only' } }, token)).status, 400);
      const jpeg = 'data:image/jpeg;base64,' + Buffer.from([255, 216, ...Array(40).fill(0)]).toString('base64');
      const req = {
        requestId: uuid(),
        kind: "advisor",
        input: { images: [jpeg], personId, recentContext: "result-only context", intent: "reply" },
      };
      let pending = (await request("/v1/jobs", req, token)).value;
      assert(pending.id);
      let done;
      for (let i = 0; i < 30; i++) {
        done = (await request("/v1/jobs/" + pending.id, null, token)).value;
        if (done.status === "succeeded") break;
        await new Promise((r) => setTimeout(r, 50));
      }
      assert.equal(done.status, "succeeded");
      assert.equal(done.result.replies.length, 3);
      assert.equal(done.result.messages[0].speaker, 'other');
      assert.equal(models.lastInput.people[0].id, personId); assert.equal(models.lastInput.styles[0].scene, '工作');
      assert.equal(models.lastInput.words, undefined); assert.equal(models.lastInput.recentContext, 'result-only context');
      // The server acknowledges a history revision durably before any inference.
      app.memory.stopped = true;
      const history = { id: uuid(), revision: 1, type: 'voice', asrText: '豆包', insertedText: '豆包', resultText: '豆豆', messages: [] };
      const historyReq = { events: [history] };
      assert.equal((await request('/v1/memory/history', historyReq, token)).status, 202);
      assert.equal((await request('/v1/memory/history', historyReq, token)).status, 202);
      assert.equal((await request('/v1/memory/history', { events: [{ ...history, resultText: 'tampered' }] }, token)).status, 409);
      assert.equal((await app.db.query('SELECT count(*) n FROM memory_history WHERE history_id=$1', [history.id])).rows[0].n, '1');
      app.memory.stopped = false; await app.memory.pump(); app.memory.stopped = true;
      assert.equal(modelCalls.length, 1); assert.equal(modelCalls[0].memory.people[0].id, personId);
      let learned = (await request('/v1/memory', null, token)).value;
      assert(learned.memory.hotwords.some(w => w.text === '豆豆'));
      assert.equal((await app.db.query('SELECT body_cipher FROM memory_history WHERE history_id=$1', [history.id])).rows[0].body_cipher, null);
      // A newer edit retracts old automatic learning and is processed from its own cursor.
      assert.equal((await request('/v1/memory/history', { events: [{ ...history, revision: 2, resultText: '豆包' }] }, token)).status, 202);
      learned = (await request('/v1/memory', null, token)).value;
      assert(!learned.memory.hotwords.some(w => w.text === '豆豆'));
      memoryModel.tooLarge = true; app.memory.stopped = false; await app.memory.pump(); app.memory.stopped = true;
      assert.equal(modelCalls.length, 1);
      assert.equal((await app.db.query('SELECT next_offset,state FROM memory_history WHERE history_id=$1 AND revision=2', [history.id])).rows[0].next_offset, 0);
      assert.equal((await request('/v1/memory/history', { events: [{ id: history.id, revision: 3, type: 'voice', deleted: true }] }, token)).status, 202);
      assert.equal((await app.db.query("SELECT count(*) n FROM memory_history WHERE history_id=$1 AND state='queued'", [history.id])).rows[0].n, '0');
      memoryModel.tooLarge = false;
      assert.equal((await request("/v1/jobs", req, token)).value.id, done.id);
      assert.equal((await app.ledger.balance(uid)).advisor.used, 1);
      const ws = new WebSocket(base.replace("http:", "ws:") + "/v1/asr", {
        headers: { Authorization: "Bearer " + token },
      });
      const asrResult = await new Promise((resolve, reject) => {
        ws.on("open", () =>
          ws.send(
            JSON.stringify({
              type: "configure",
              requestId: uuid(),
              context: "",
              polish: true,
            }),
          ),
        );
        ws.on("message", (b) => {
          const m = JSON.parse(b.toString());
          if (m.type === "ready") {
            ws.send(Buffer.alloc(3200));
            ws.send(JSON.stringify({ type: "finish" }));
          }
          if (m.type === "job") resolve(m);
          if (m.type === "error") reject(Error(m.error.message));
        });
        ws.on("error", reject);
      });
      assert.equal(asrResult.result.text, "豆豆你好");
      assert.equal(audioBytes, 3200);
      const asrToken = auth.accessToken;
      const rotation = (
        await request("/v1/auth/refresh", { refreshToken: auth.refreshToken })
      ).value;
      assert(rotation.accessToken);
      assert.equal((await request("/v1/account", null, asrToken)).status, 401);
      assert.equal(
        (await request("/v1/auth/refresh", { refreshToken: auth.refreshToken }))
          .status,
        401,
      );
      assert.equal(
        (await request("/v1/account", null, rotation.accessToken)).status,
        401,
      );
      // A second independently verified device gets the same once-only welcome grant.
      await app.db.query(
        "UPDATE challenges SET created_at=now()-interval '2 minutes' WHERE phone_hash=(SELECT phone_hash FROM users WHERE id=$1)",
        [uid],
      );
      const ch2 = (
        await request("/v1/auth/challenges", { phone: "13800001234" })
      ).value;
      auth = (
        await request("/v1/auth/verify", {
          challengeId: ch2.challengeId,
          code: sent.at(-1).code,
        })
      ).value;
      assert.equal(
        (
          await app.db.query(
            "SELECT count(*) n FROM grants WHERE user_id=$1 AND grant_key='welcome'",
            [uid],
          )
        ).rows[0].n,
        "1",
      );
      assert.equal(
        (await request("/v1/sync", null, auth.accessToken)).status,
        402,
      );
      await app.admin.grant("fixture-admin", {
        userId: uid,
        reason: "integration test",
        plan: {
          limits: { asr_characters: 0, advisor_generations: 0 },
          features: { cloud_sync: true },
        },
      });
      const key = (await request("/v1/sync/key", {}, auth.accessToken)).value
        .key;
      assert.equal(Buffer.from(key, "base64").length, 32);
      const blob = crypto.randomBytes(100).toString("base64");
      assert.equal(
        (
          await request(
            "/v1/sync",
            { baseRevision: 0, blob },
            auth.accessToken,
            "PUT",
          )
        ).value.revision,
        1,
      );
      assert.equal(
        (
          await request(
            "/v1/sync",
            { baseRevision: 0, blob },
            auth.accessToken,
            "PUT",
          )
        ).status,
        409,
      );
      assert.equal(
        (await request("/v1/sync", null, auth.accessToken)).value.blob,
        blob,
      );
      const login = await fetch(base + "/admin/api/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password: "test-password" }),
      });
      assert.equal(login.status, 200);
      const adminCookie = login.headers.get("set-cookie").split(";")[0],
        csrf = (await login.json()).csrf;
      const forbidden = await fetch(base + "/admin/api/grants", {
        method: "POST",
        headers: { "Content-Type": "application/json", cookie: adminCookie },
        body: "{}",
      });
      assert.equal(forbidden.status, 403);
      assert(csrf);
      const hostile = await fetch(base + "/admin/api/overview", {
        headers: { cookie: adminCookie, Origin: "https://evil.invalid" },
      });
      assert.equal(hostile.status, 403);
      await assert.rejects(
        app.admin.savePlan("fixture-admin", { ...cfg.plans.plans[0] }),
        /已发布/,
      );
      const rows = (
        await app.db.query(
          "SELECT input_cipher,result_cipher FROM jobs WHERE status='succeeded'",
        )
      ).rows;
      assert(
        rows.every(
          (r) =>
            !r.input_cipher && !String(r.result_cipher).includes("fixture"),
        ),
      );
      const late = await app.ledger.reserve(uid, uuid(), 'advisor', { intent: 'late' });
      assert.equal((await request('/v1/account/delete', { confirm: true }, auth.accessToken)).status, 200);
      for (const table of ['personal_memory', 'memory_history', 'memory_receipts', 'memory_work']) assert.equal((await app.db.query('SELECT count(*) n FROM ' + table + ' WHERE user_id=$1', [uid])).rows[0].n, '0');
      assert.equal((await request('/v1/account', null, auth.accessToken)).status, 401);
      assert.equal((await app.ledger.finish(late.job.id, 'succeeded', { replies: [] })).result_cipher, null);
      await assert.rejects(app.sync.key({ id: uid, verified_at: new Date() }), /账号不可用/);
    } finally {
      await app.close();
      await new Promise((r) => provider.close(r));
      await fs.rm(root, { recursive: true, force: true });
    }
  },
);
