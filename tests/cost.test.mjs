import test from "node:test";
import assert from "node:assert/strict";
import { estimateCost } from "../spec/cost.mjs";
test("official unit conversions and no-cache baseline", () => {
  const c = estimateCost();
  assert.equal(c.hours, 15);
  assert.ok(Math.abs(c.speech - 12.96) < 1e-9);
  assert.ok(Math.abs(c.total - 17.268) < 1e-9);
});
test("free allowance is optional, not a negative charge or permanent assumption", () => {
  assert.ok(Math.abs(estimateCost({ freeHours: 10 }).total - 8.628) < 1e-9);
  assert.equal(estimateCost({ minutesPerDay: 0, freeHours: 10 }).speech, 0);
  assert.throws(() => estimateCost({ minutesPerDay: -1 }), RangeError);
});
