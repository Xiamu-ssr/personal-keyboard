import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import {
  EntitlementLedger,
  billableCharacters,
} from "../domains/entitlements.mjs";
const config = JSON.parse(
  await fs.readFile(new URL("../config/plans.json", import.meta.url), "utf8"),
);
const welcome = config.plans.find((p) => p.id === config.activeWelcomePlan);
const setup = () => {
  const ledger = new EntitlementLedger();
  ledger.grantOnce({ accountId: "one", grantId: "welcome", plan: welcome });
  return ledger;
};
test("welcome is one grant, fixed at issue time, with no managed sync", () => {
  const ledger = setup(),
    changed = structuredClone(welcome);
  changed.limits.asr_characters = 90000;
  ledger.grantOnce({
    accountId: "one",
    grantId: "another-welcome-request",
    plan: changed,
  });
  assert.equal(ledger.balance("one", "asr_characters").total, 10000);
  assert.equal(ledger.canSync("one"), false);
  assert.equal(ledger.balance("one", "advisor_generations").available, 20);
});
test("concurrent reservations cannot overspend and retry cannot double charge", () => {
  const ledger = setup();
  const job = {
    accountId: "one",
    requestId: "voice-1",
    meter: "asr_characters",
    maximum: 8000,
  };
  ledger.reserve(job);
  ledger.reserve(job);
  assert.equal(ledger.balance("one", job.meter).reserved, 8000);
  assert.throws(
    () => ledger.reserve({ ...job, requestId: "voice-2", maximum: 3000 }),
    /exhausted/,
  );
  const result = {
    accountId: "one",
    requestId: job.requestId,
    amount: 200,
    outcome: "success",
  };
  ledger.settle(result);
  ledger.settle(result);
  assert.equal(ledger.balance("one", job.meter).used, 200);
  assert.equal(ledger.balance("one", job.meter).available, 9800);
  assert.throws(() => ledger.settle({ ...result, amount: 201 }), /conflict/);
});
test("cancel or failure releases budget; more is a new advisor task", () => {
  const ledger = setup();
  const job = {
    accountId: "one",
    requestId: "reply",
    meter: "advisor_generations",
    maximum: 1,
  };
  ledger.reserve(job);
  ledger.settle({
    accountId: "one",
    requestId: "reply",
    amount: 0,
    outcome: "failed",
  });
  assert.equal(ledger.balance("one", job.meter).available, 20);
  for (const requestId of ["retry-new", "more"]) {
    ledger.reserve({ ...job, requestId });
    ledger.settle({
      accountId: "one",
      requestId,
      amount: 1,
      outcome: "success",
    });
  }
  assert.equal(ledger.balance("one", job.meter).used, 2);
  assert.throws(
    () => ledger.reserve({ ...job, requestId: "more", maximum: 2 }),
    /conflict/,
  );
});
test("unknown accounts cannot consume and unfinished drafts cannot grant", () => {
  const ledger = setup();
  assert.throws(
    () =>
      ledger.reserve({
        accountId: "other",
        requestId: "x",
        meter: "asr_characters",
        maximum: 1,
      }),
    /exhausted/,
  );
  assert.throws(
    () =>
      ledger.grantOnce({
        accountId: "one",
        grantId: "paid",
        plan: config.plans[1],
      }),
    /Published/,
  );
  assert.equal(billableCharacters(" 豆豆\nHello e\u0301！ "), 9);
});
