import test from 'node:test';
import assert from 'node:assert/strict';
import { uuid } from '../security.mjs';
import { emptyMemory, changeMemory, resolveCandidate, memoryDelta, retractRevision, validateReferences } from '../memory/domain.mjs';
import { historyEvent } from '../memory/history.mjs';
import { MemoryModel, historyRequest, historyBatch } from '../memory/model.mjs';
import { QwenCounter } from '../memory/counter.mjs';

const history = () => ({ id: uuid(), revision: 2, type: 'voice', asrText: '豆包', insertedText: '豆包', resultText: '豆豆', conversationRef: '' });
const wordOp = h => ({ kind: 'hotword', action: 'upsert', id: 'new:word', value: { text: '豆豆', variants: ['豆包'], lexical: { kind: 'name', meaning: '输入法产品名称' } }, sourceId: h.id, sourceRevision: h.revision, category: 'correction', confidence: 0.99, quote: h.resultText });
const options = h => ({ history: h, origin: { historyId: h.id, revision: h.revision } });

test('only explicit inserted-to-result corrections add reversible hotwords automatically', () => {
  const h = history(), initial = emptyMemory(), result = changeMemory(initial, [wordOp(h)], options(h));
  assert.equal(initial.hotwords.length, 0); assert.equal(result.memory.hotwords[0].text, '豆豆');
  assert.deepEqual(Object.values(result.memory.origins), [{ historyId: h.id, revision: 2 }]);
  const rewritten = { ...h, asrText: '豆包', insertedText: '豆豆', resultText: '豆豆' };
  const candidate = changeMemory(initial, [wordOp(rewritten)], options(rewritten)).memory;
  assert.equal(candidate.hotwords.length, 0); assert.equal(candidate.candidates.length, 0);
  assert.throws(() => changeMemory(initial, [{ ...wordOp(h), sourceId: uuid() }], options(h)), /来源/);
  assert.throws(() => changeMemory(initial, [{ ...wordOp(h), confidence: '1' }], options(h)), /置信度/);
  const removed = changeMemory(result.memory, [{ kind: 'hotword', action: 'delete', id: result.memory.hotwords[0].id }], { manual: true }).memory;
  const repeated = changeMemory(removed, [wordOp(h)], options(h)).memory;
  assert.equal(repeated.hotwords.length, 0); assert.equal(repeated.candidates.length, 0);
  assert.equal(memoryDelta(result.memory, removed)[0].deleted, true);
});

test('person facts retain manual protection; reliable anchored styles apply directly', () => {
  const personId = uuid(), companyId = uuid(), nicknameId = uuid();
  let current = changeMemory(emptyMemory(), [
    { kind: 'person', action: 'upsert', id: personId, value: { name: '小林', aliases: [] } },
    { kind: 'fact', action: 'upsert', personId, id: companyId, value: { key: '公司', value: '海岚', scope: '当前' } },
    { kind: 'fact', action: 'upsert', personId, id: nicknameId, value: { key: '称呼', value: '林同学', scope: '工作' } },
  ], { manual: true }).memory;
  const h = { ...history(), resultText: '小林现在在云杉上班。' };
  current = changeMemory(current, [{ kind: 'fact', action: 'upsert', personId, id: companyId, value: { key: '公司', value: '云杉', scope: '当前' }, sourceId: h.id, sourceRevision: h.revision, category: 'observation', confidence: 0.99, quote: h.resultText }], options(h)).memory;
  assert.equal(current.people[0].facts[0].value, '海岚');
  const candidateId = current.candidates[0].id;
  current = resolveCandidate(current, candidateId, true);
  assert.equal(current.people[0].facts[0].value, '云杉'); assert.equal(current.people[0].facts[1].value, '林同学');
  const style = { kind: 'style', action: 'upsert', id: 'new:style', value: { scene: '工作', rules: ['简洁'], examples: [] }, sourceId: h.id, sourceRevision: h.revision, category: 'observation', confidence: 1 };
  current = changeMemory(current, [style], options(h)).memory;
  assert.equal(current.styles.length, 0, 'an unanchored style is skipped, not made into a chore');
  const anchored={...style,value:{...style.value,examples:[h.resultText]}};
  current=changeMemory(current,[anchored],options(h)).memory;
  assert.equal(current.styles[0].scene,'工作');assert.equal(current.candidates.length,0);
  current=retractRevision(current,h.id,h.revision+1);
  assert.equal(current.styles.length,0,'deleted/revised evidence retracts automatic style');
  assert.equal(current.people[0].facts[1].value,'林同学','manual records survive');
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

test('counter receives unchanged generation messages and schema; no character fallback', async () => {
  let counted;
  const model = new MemoryModel({ textReady: true, modelKey: 'fixture', textModel: 'qwen3.8-flash', memoryOutputTokens: 8192, memoryMaxInputTokens: 10000, memoryContextTokens: 20000 }, { counter: { count: async req => { counted = structuredClone(req); return { tokens: 3000 }; } } });
  await model.init(); const request = model.request(emptyMemory(), 1, history());
  assert.equal(await model.fits(request), true); assert.deepEqual(counted.messages, request.messages);
  assert.ok(request.messages[0].content.includes('sourceRevision')); assert.deepEqual(counted.response_format, request.response_format);
  request.messages[1].content += ' changed';
  model.counter = { count: async () => ({}) };
  await assert.rejects(model.fits(request), /Token/);
  model.counter = { count: async () => { throw Error('counter unavailable'); } };
  await assert.rejects(model.fits(request), /counter unavailable/);
});

test('pinned official tokenizer includes the chat template and protocol allowance', async t => {
  const counter = new QwenCounter(); t.after(() => counter.close());
  const request = { model: 'qwen3.8-flash', enable_thinking: false, response_format: { type: 'json_object' }, messages: [
    { role: 'system', content: '只输出JSON。' }, { role: 'user', content: '豆豆 CPA 👩🏽\nบริษัท 稀有人名' },
  ] };
  const counted = await counter.count(request);
  assert.equal(counted.revision, 'de4b8e4d43b917e7706784d8bb445c9af86a3540');
  assert.ok(counted.contentTokens > 10); assert.equal(counted.protocolTokens, 256);
  assert.equal(counted.tokens, counted.contentTokens + counted.protocolTokens);
  await assert.rejects(counter.count({ ...request, model: 'unverified-model' }), /已验证/);
  await assert.rejects(counter.count({ ...request, enable_thinking: true }), /已验证/);
});

test('generation cannot commit an over-budget or unverified provider token count', async () => {
  let calls = 0;
  const cfg = { textReady: true, textModel: 'qwen3.8-flash', textBase: 'https://fixture.invalid', memoryOutputTokens: 8192, memoryMaxInputTokens: 10000, memoryContextTokens: 20000 };
  const model = new MemoryModel(cfg, { counter: { count: async () => ({ tokens: 500 }) }, fetcher: async () => {
    calls++; return new Response(JSON.stringify({ usage: { prompt_tokens: 501 }, choices: [{ finish_reason: 'stop', message: { content: '{"baseVersion":0,"operations":[]}' } }] }));
  } });
  await model.init(); const request = model.request(emptyMemory(), 0, history());
  await assert.rejects(model.generate(request), { code: 'TOKEN_COUNTER_MISMATCH' });
  assert.equal(calls, 1);
  model.cfg.memoryMaxInputTokens = 499;
  await assert.rejects(model.generate(request), { code: 'MEMORY_CAPACITY' });
  assert.equal(calls, 1);
});

test('whole histories batch together while every request includes all old memory', async()=>{
  const memory=emptyMemory();memory.hotwords.push({id:uuid(),text:'全量旧词',variants:[]});
  const histories=[history(),history(),history()];const seen=[];
  const model={request:(m,v,h)=>({memory:m,version:v,history:h}),fits:async request=>{seen.push(request);return request.history.length<=2;}};
  const result=await historyBatch(model,{memory,version:8},histories.map(h=>({history:h,next_offset:0})));
  assert.equal(result.accepted.length,2);assert.deepEqual(result.request.history,histories.slice(0,2));
  for(const request of seen)assert.deepEqual(request.memory,memory);
  const operations=histories.slice(0,2).map((h,i)=>({...wordOp(h),id:'new:word'+i,value:{text:'豆豆'+i,variants:['豆包']},category:'observation'}));
  assert.throws(()=>changeMemory(memory,[{...operations[0],sourceId:uuid()}],{histories}),/来源/);
});

test('person facts are bounded independently and identities do not collapse same-name people',()=>{
  const memory=emptyMemory();const one=uuid(),two=uuid();
  const changed=changeMemory(memory,[{kind:'person',action:'upsert',id:one,value:{name:'小林',identities:[{platform:'微信',nickname:'小林',handle:'lin1'}]}},{kind:'person',action:'upsert',id:two,value:{name:'小林',identities:[{platform:'微信',nickname:'小林',handle:'lin2'}]}}],{manual:true}).memory;
  assert.equal(changed.people.length,2);
  changed.people[0].facts=Array.from({length:101},(_,i)=>({id:uuid(),key:'事实'+i,value:'内容',scope:'当前'}));
  assert.throws(()=>validateReferences(changed),/100条/);
});
