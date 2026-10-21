import test from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import net from "node:net";
import { createRemoteDemo } from "../demo/remote/server.mjs";
import { DedicatedEmulator } from "../demo/remote/emulator.mjs";
test("remote Android opens without a password but retains same-origin sessions and fixed device scope", async (t) => {
  const origin = "https://demo.example";
  let calls = 0;
  const emulator = {
    phase: "stopped",
    status() {
      return { phase: this.phase };
    },
    async start() {
      calls++;
      this.phase = "running";
      return this.status();
    },
    async stop() {
      this.phase = "stopped";
    },
    async expire() {},
    touch() {},
  };
  const app = createRemoteDemo({ origin, emulator });
  app.server.listen(0, "127.0.0.1");
  await once(app.server, "listening");
  t.after(() => app.close());
  const base =
    "http://127.0.0.1:" + app.server.address().port + "/keyboard-spec/live/";
  assert.equal((await fetch(base + "api/status")).status, 401);
  assert.equal(
    (
      await fetch(base + "api/start", {
        method: "POST",
        headers: { Origin: origin },
      })
    ).status,
    401,
  );
  const login = await fetch(base + "api/session", {
    method: "POST",
    headers: { Origin: origin },
    body: "{}",
  });
  assert.equal(login.status, 200);
  const { session } = await login.json();
  assert.match(session, /^[a-f0-9]{48}$/);
  assert.equal(login.headers.get("set-cookie"), null);
  const headers = { "X-Demo-Session": session, Origin: origin };
  assert.equal(
    (
      await fetch(base + "api/start", {
        method: "POST",
        headers: { ...headers, Origin: "https://attacker.example" },
      })
    ).status,
    403,
  );
  assert.equal(calls, 0);
  const started = await (
    await fetch(base + "api/start", {
      method: "POST",
      headers,
      body: JSON.stringify({ serial: "other-phone" }),
    })
  ).json();
  assert.equal(calls, 1);
  assert.match(
    started.viewer,
    /^\/keyboard-spec\/live\/session\/[a-f0-9]{48}\/viewer\/$/,
  );
  assert.doesNotMatch(started.viewer, /other-phone/);
  for (const query of [
    "action=proxy-ws&udid=emulator-5580&remote=tcp%3A8886",
    "action=proxy-adb&udid=someone-elses-phone&remote=tcp%3A8886",
    "action=proxy-adb&udid=emulator-5580&remote=tcp%3A5037",
    "action=proxy-adb&udid=emulator-5580&remote=tcp%3A8886&path=/shell",
  ]) {
    const response = await new Promise((resolve, reject) => {
      const socket = net.connect(app.server.address().port, "127.0.0.1", () =>
        socket.write(
          `GET /keyboard-spec/live/session/${session}/viewer/?${query} HTTP/1.1\r\nHost: local\r\nOrigin: ${origin}\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n`,
        ),
      );
      let result = "";
      socket.setTimeout(1500, () => {
        socket.destroy();
        reject(Error("Rejected stream must close promptly"));
      });
      socket.on("data", (d) => (result += d));
      socket.once("end", () => resolve(result));
      socket.once("error", reject);
    });
    assert.match(response, /^HTTP\/1.1 403 Forbidden/);
  }
  const another = await (
    await fetch(base + "api/session", {
      method: "POST",
      headers: { Origin: origin },
      body: "{}",
    })
  ).json();
  const outsider = { Origin: origin, "X-Demo-Session": another.session };
  assert.equal(
    (await (await fetch(base + "api/status", { headers: outsider })).json())
      .phase,
    "occupied",
  );
  assert.equal(
    (
      await fetch(base + "api/start", {
        method: "POST",
        headers: outsider,
        body: "{}",
      })
    ).status,
    409,
  );
  assert.equal(
    (
      await fetch(base + "api/stop", {
        method: "POST",
        headers: outsider,
        body: "{}",
      })
    ).status,
    403,
  );
  assert.equal(
    (
      await fetch(base + "session/" + another.session + "/viewer/", {
        headers: outsider,
      })
    ).status,
    403,
  );
  assert.equal(calls, 1);
  assert.equal(
    (await fetch(base + "api/start", { method: "POST", headers, body: "{}" }))
      .status,
    200,
  );
  assert.equal(calls, 1);
  assert.equal((await fetch(base + "api/anything", { headers })).status, 404);
  assert.equal((await fetch(base + "api/status", { headers })).status, 200);
  assert.equal(
    (await fetch(base + "api/stop", { method: "POST", headers })).status,
    200,
  );
});
test("public demo session creation is rate limited", async (t) => {
  const emulator = { async expire() {}, async stop() {} };
  const app = createRemoteDemo({
    origin: "https://demo.example",
    emulator,
  });
  app.server.listen(0, "127.0.0.1");
  await once(app.server, "listening");
  t.after(() => app.close());
  const url =
    "http://127.0.0.1:" +
    app.server.address().port +
    "/keyboard-spec/live/api/session";
  for (let n = 0; n < 9; n++)
    assert.equal(
      (
        await fetch(url, {
          method: "POST",
          headers: { Origin: "https://demo.example" },
          body: "{}",
        })
      ).status,
      n === 8 ? 429 : 200,
    );
});
test("idle and absolute lifetime reclaim the emulator even while the page is closed", async () => {
  for (const [idle, elapsed] of [
    [121000, 121000],
    [0, 901000],
  ]) {
    const demo = new DedicatedEmulator();
    let stopped = 0;
    demo.phase = "running";
    demo.startedAt = 1000;
    demo.lastActive = 1000 + elapsed - idle;
    demo.stop = async () => stopped++;
    await demo.expire(1000 + elapsed);
    assert.equal(stopped, 1);
  }
  const demo = new DedicatedEmulator();
  demo.phase = "running";
  demo.startedAt = 1000;
  demo.lastActive = 2000;
  let stopped = 0;
  demo.stop = async () => stopped++;
  await demo.expire(3000);
  assert.equal(stopped, 0);
});
test("closing while Android boots stops the newly created emulator", async (t) => {
  let finish,
    stops = 0;
  const emulator = {
    phase: "stopped",
    status() {
      return { phase: this.phase };
    },
    async start() {
      this.phase = "starting";
      await new Promise((r) => (finish = r));
      this.phase = "running";
    },
    async stop() {
      stops++;
      this.phase = "stopped";
    },
    async expire() {},
    touch() {},
  };
  const origin = "https://demo.example",
    app = createRemoteDemo({ origin, emulator });
  app.server.listen(0, "127.0.0.1");
  await once(app.server, "listening");
  t.after(() => app.close());
  const base = `http://127.0.0.1:${app.server.address().port}/keyboard-spec/live/api/`;
  const login = await (
    await fetch(base + "session", {
      method: "POST",
      headers: { Origin: origin },
      body: "{}",
    })
  ).json();
  const options = {
    method: "POST",
    headers: { Origin: origin, "X-Demo-Session": login.session },
    body: "{}",
  };
  const start = fetch(base + "start", options);
  while (!finish) await new Promise((r) => setImmediate(r));
  const stop = fetch(base + "stop", options);
  await new Promise((r) => setTimeout(r, 10));
  finish();
  assert.equal((await start).status, 200);
  assert.equal((await stop).status, 200);
  assert.equal(stops, 1);
  assert.equal(emulator.phase, "stopped");
});
