import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import {
  seal,
  unseal,
  signToken,
  verifyToken,
  passwordHash,
  passwordMatches,
  canonical,
} from "../security.mjs";
import { normalizePhone } from "../identity/accounts.mjs";
import { modelInput, validateModelResult } from "../models/text.mjs";
import { limitText } from "../models/asr.mjs";
test("encrypted records are bound to account and reject tampering", () => {
  const k = crypto.randomBytes(32),
    s = seal(k, { a: "豆豆" }, "account-a");
  assert.deepEqual(JSON.parse(unseal(k, s, "account-a")), { a: "豆豆" });
  assert.throws(() => unseal(k, s, "account-b"));
  assert.throws(() => unseal(k, s.slice(0, -4) + "abcd", "account-a"));
});
test("access tokens reject wrong signature, audience and expiry", () => {
  const k = "secret",
    v = { aud: "keyboard", sub: "a", iat: 1000, exp: 2000 },
    t = signToken(k, v);
  assert.deepEqual(verifyToken(k, t, "keyboard", 1500), v);
  for (const f of [
    () => verifyToken("wrong", t, "keyboard", 1500),
    () => verifyToken(k, t, "admin", 1500),
    () => verifyToken(k, t, "keyboard", 2000),
    () => verifyToken(k, t + ".x", "keyboard", 1500),
  ])
    assert.throws(f);
  assert(passwordMatches("good", passwordHash("good")));
  assert(!passwordMatches("bad", passwordHash("good")));
});
test("normalization and final-character cap preserve Unicode boundaries", () => {
  assert.equal(normalizePhone("138 0000 1234"), "+8613800001234");
  assert.throws(() => normalizePhone("1,2"));
  assert.equal(limitText("你 好 👨🏾abc", 4), "你 好 👨🏾");
  assert.equal(canonical({ b: 1, a: 2 }), canonical({ a: 2, b: 1 }));
});
test("commercial requests do not accept canonical memory copies or OCR-only advisor", () => {
  assert.throws(() => modelInput("advisor", { images: [], words: ["private"] }));
  assert.throws(() => modelInput("advisor", { context: "OCR text" }));
  assert.throws(() => modelInput("rewrite", { transcript: "x", styles: ["local copy"] }));
  assert.throws(() => modelInput("memory", { events: [] }));
});
