import test from 'node:test';
import assert from 'node:assert/strict';
import { uuid } from '../security.mjs';
import { emptyMemory, changeMemory, resolveCandidate, memoryDelta } from '../memory/domain.mjs';
import { historyEvent } from '../memory/history.mjs';
import { MemoryModel, historyRequest } from '../memory/model.mjs';

const history = () => ({ id: uuid(), revision: 2, type: 'voice', asrText: '豆包', insertedText: '豆包', resultText: '豆豆', conversationRef: '' });
const wordOp = h => ({ kind: 'hotword', action: 'upsert', id: 'new:word', value: { text: '豆豆', variants: ['豆包'] }, sourceId: h.id, sourceRevision: h.revision, category: 'correction', confidence: 0.99 });
const options = h => ({ history: h, origin: { historyId: h.id, revision: h.revision } });

test('only explicit inserted-to-result corrections add reversible hotwords automatically', () => {
  const h = history(), initial = emptyMemory(), result = changeMemory(initial, [wordOp(h)], options(h));
  assert.equal(initial.hotwords.length, 0); assert.equal(result.memory.hotwords[0].text, '豆豆');
  assert.deepEqual(Object.values(result.memory.origins), [{ historyId: h.id, revision: 2 }]);
  const rewritten = { ...h, asrText: '豆包', insertedText: '豆豆', resultText: '豆豆' };
  const candidate = changeMemory(initial, [wordOp(rewritten)], options(rewritten)).memory;
  assert.equal(candidate.hotwords.length, 0); assert.equal(candidate.candidates.length, 1);
  assert.throws(() => changeMemory(initial, [{ ...wordOp(h), sourceId: uuid() }], options(h)), /来源/);
  assert.throws(() => changeMemory(initial, [{ ...wordOp(h), confidence: '1' }], options(h)), /置信度/);
  const removed = changeMemory(result.memory, [{ kind: 'hotword', action: 'delete', id: result.memory.hotwords[0].id }], { manual: true }).memory;
  const repeated = changeMemory(removed, [wordOp(h)], options(h)).memory;
  assert.equal(repeated.hotwords.length, 0); assert.equal(repeated.candidates.length, 1);
  assert.equal(memoryDelta(result.memory, removed)[0].deleted, true);
});

test('person facts update individually; manual protection and style confirmation preserve other fields', () => {
  const personId = uuid(), companyId = uuid(), nicknameId = uuid();
  let current = changeMemory(emptyMemory(), [
    { kind: 'person', action: 'upsert', id: personId, value: { name: '小林', aliases: [] } },
    { kind: 'fact', action: 'upsert', personId, id: companyId, value: { key: '公司', value: '海岚', scope: '当前' } },
    { kind: 'fact', action: 'upsert', personId, id: nicknameId, value: { key: '称呼', value: '林同学', scope: '工作' } },
  ], { manual: true }).memory;
  const h = history();
  current = changeMemory(current, [{ kind: 'fact', action: 'upsert', personId, id: companyId, value: { key: '公司', value: '云杉', scope: '当前' }, sourceId: h.id, sourceRevision: h.revision, category: 'observation', confidence: 0.99 }], options(h)).memory;
  assert.equal(current.people[0].facts[0].value, '海岚');
  const candidateId = current.candidates[0].id;
  current = resolveCandidate(current, candidateId, true);
  assert.equal(current.people[0].facts[0].value, '云杉'); assert.equal(current.people[0].facts[1].value, '林同学');
  const style = { kind: 'style', action: 'upsert', id: 'new:style', value: { scene: '工作', rules: ['简洁'], examples: [] }, sourceId: h.id, sourceRevision: h.revision, category: 'observation', confidence: 1 };
  current = changeMemory(current, [style], options(h)).memory;
  assert.equal(current.styles.length, 0);
  current = resolveCandidate(current, current.candidates[0].id, true);
  assert.equal(current.styles[0].scene, '工作');
  assert.throws(() => changeMemory(current, [{ kind: 'relation', action: 'upsert', personId, id: uuid(), value: { targetId: uuid(), type: '同事' } }], { manual: true }), /人物不存在/);
});

test('every history fragment receives the same COMPLETE memory, with Unicode-safe boundaries', async () => {
  const memory = emptyMemory(); memory.people.push({ id: uuid(), name: 'full-memory-sentinel', aliases: [], facts: [], relations: [] });
  const input = historyEvent({ ...history(), resultText: '👩🏽'.repeat(1000) }); const seen = [];
  const model = { request: (mem, version, piece) => ({ mem, version, piece }), fits: async req => { seen.push(req); return req.piece.text.length < 400; } };
  const result = await historyRequest(model, { version: 9, memory }, input, 0);
  assert.ok(result.end < result.total);
  for (const req of seen) { assert.deepEqual(req.mem, memory); assert.equal(req.version, 9); assert.equal(req.piece.text.includes('\ufffd'), false); }
  await assert.rejects(historyRequest({ ...model, fits: async () => false }, { version: 9, memory }, input, 0), /完整记忆/);
});

test('provider counter sees the exact generation messages, schema and protocol fields; no character fallback', async () => {
  let counted;
  const model = new MemoryModel({ textReady: true, modelKey: 'fixture', textModel: 'qwen-flash', tokenizerUrl: 'https://fixture.invalid/tokenizer', memoryOutputTokens: 8192, memoryMaxInputTokens: 10000, memoryContextTokens: 20000 }, async (_url, opts) => { counted = JSON.parse(opts.body); return new Response(JSON.stringify({ usage: { input_tokens: 3000 } })); });
  await model.init(); const request = model.request(emptyMemory(), 1, history());
  assert.equal(await model.fits(request), true); assert.deepEqual(counted.input.messages, request.messages);
  assert.ok(request.messages[0].content.includes('sourceRevision')); assert.deepEqual(counted.parameters.response_format, request.response_format);
  model.fetcher = async () => new Response('{}');
  await assert.rejects(model.fits(request), /Token/);
  model.fetcher = async () => { throw Error('offline'); };
  await assert.rejects(model.fits(request), /精确容量/);
});
