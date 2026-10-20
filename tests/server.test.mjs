import test from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import { createSpecServer } from "../demo/server.mjs";
test("device actions require local origin, token and POST; no filesystem escape", async (t) => {
  let calls = 0;
  const app = createSpecServer({
    session: {
      status: async () => ({ devices: [], running: false }),
      start: async () => {
        calls++;
        return {};
      },
      stop: async () => {
        calls++;
      },
    },
  });
  app.server.listen(0, "127.0.0.1");
  await once(app.server, "listening");
  t.after(() => app.server.close());
  const base = "http://127.0.0.1:" + app.server.address().port;
  assert.equal(
    (await fetch(base + "/api/demo/start", { method: "POST", body: "{}" }))
      .status,
    403,
  );
  const { token } = await (await fetch(base + "/api/session")).json();
  const headers = { "X-Demo-Token": token };
  assert.equal(
    (await fetch(base + "/api/demo/start", { headers })).status,
    405,
  );
  assert.equal(
    (
      await fetch(base + "/api/demo/start", {
        method: "POST",
        headers: { ...headers, Origin: "https://other.example" },
        body: "{}",
      })
    ).status,
    403,
  );
  assert.equal(calls, 0);
  assert.equal((await fetch(base + "/%2e%2e/.env")).status, 404);
  assert.equal(
    (await fetch(base + "/api/demo/status", { headers })).status,
    200,
  );
  assert.equal(
    (
      await fetch(base + "/api/demo/start", {
        method: "POST",
        headers,
        body: "{}",
      })
    ).status,
    200,
  );
  assert.equal(calls, 1);
  app.session.starting = true;
  assert.equal(
    (
      await fetch(base + "/api/demo/stop", {
        method: "POST",
        headers,
        body: "{}",
      })
    ).status,
    400,
  );
  assert.equal(
    calls,
    1,
    "stop must not race an in-progress installation/settings change",
  );
});
