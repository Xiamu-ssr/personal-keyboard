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
import { normalizeHandle, createPassword, verifyPassword } from "../identity/passwords.mjs";
import { modelInput, validateModelResult, advisorResponseFormat, advisorDirective } from "../models/text.mjs";
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
  assert.equal(normalizeHandle(" Fixture-User "), "fixture-user");
  assert.throws(() => normalizeHandle("a,b"));
  assert.equal(limitText("你 好 👨🏾abc", 4), "你 好 👨🏾");
  assert.equal(canonical({ b: 1, a: 2 }), canonical({ a: 2, b: 1 }));
});
test("commercial requests do not accept canonical memory copies or OCR-only advisor", () => {
  assert.throws(() => modelInput("advisor", { images: [], words: ["private"] }));
  assert.throws(() => modelInput("advisor", { context: "OCR text" }));
  assert.throws(() => modelInput("rewrite", { transcript: "x", styles: ["local copy"] }));
  assert.throws(() => modelInput("memory", { events: [] }));
});

test('password hashes are salted, asynchronous and verify without plaintext storage', async () => {
  const password = '一段可以记住的长句子-2026';
  const [a, b] = await Promise.all([createPassword(password), createPassword(password)]);
  assert.notEqual(a, b);
  assert(!a.includes(password));
  assert(await verifyPassword(password, a));
  assert(!await verifyPassword('wrong-password', a));
  assert(!await verifyPassword(password, undefined));
  await assert.rejects(createPassword('short'), /12/);
});


test('advisor output follows six configured slots or three variants without inventing identity', async()=>{
  const {defaultPalette,requestedSlots}=await import('../advisor/palette.mjs');
  const jpeg='data:image/jpeg;base64,'+Buffer.from([255,216,...Array(30).fill(1)]).toString('base64');
  const input={...modelInput('advisor',{images:[jpeg],platform:'fixture'}),people:[],slots:requestedSlots(defaultPalette())};
  const answerPositions=(slots,texts)=>Object.fromEntries(slots.map((_,i)=>['r'+(i+1),texts[i]]));
  const payload={replies:answerPositions(input.slots,input.slots.map(s=>'reply '+s.id)),messages:[{speaker:'other',image:1,text:'Hello'}],identity:{personId:'00000000-0000-4000-8000-000000000001',platform:'fixture',nickname:'小林',confidence:.9}};
  const full=validateModelResult('advisor',JSON.stringify(payload),input);
  assert.equal(full.replies.length,6);assert.equal(full.replies[0].style,'自然');assert.equal(full.identity.personId,'');
  assert.throws(()=>validateModelResult('advisor',JSON.stringify({...payload,replies:{r1:payload.replies.r1}}),input),/完整/);
  const variants={...input,slotId:'slot3',slots:requestedSlots(defaultPalette(),'slot3')};
  const format=advisorResponseFormat(variants);
  assert.equal(format.type,'json_schema');assert.equal(format.json_schema.strict,true);
  assert.deepEqual(format.json_schema.schema.properties.replies.required,['r1','r2','r3']);
  assert.equal(format.json_schema.schema.properties.replies.properties.r2.type,'string');
  const result=validateModelResult('advisor',JSON.stringify({...payload,replies:answerPositions(variants.slots,['a','b','c'])}),variants);
  assert(result.replies.every(r=>r.slotId==='slot3'&&r.style==='直说'));
  assert.throws(()=>validateModelResult('advisor',JSON.stringify({...payload,replies:answerPositions(variants.slots,['a','a','c'])}),variants),/重复/);
  assert.throws(()=>validateModelResult('advisor',JSON.stringify({...payload,replies:answerPositions(variants.slots,['a','b','c'])}),{...variants,previousReplies:['b']}),/重复/);
  const wrong=answerPositions(variants.slots,['a','b','c']);wrong.r2={text:'b'};
  assert.throws(()=>validateModelResult('advisor',JSON.stringify({...payload,replies:wrong}),variants),/格式/);
  const {readFile}=await import('node:fs/promises');
  const template=JSON.parse(await readFile(new URL('../prompts/reply-request.json',import.meta.url),'utf8'));
  const literal='保留 $& 和 $` 以及 {{replyCount}} 的原文';
  assert(advisorDirective({...variants,intent:literal},template).includes(literal));
});
