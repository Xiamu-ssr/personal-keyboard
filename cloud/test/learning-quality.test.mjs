import test from 'node:test';
import assert from 'node:assert/strict';
import { uuid } from '../security.mjs';
import { emptyMemory, changeMemory } from '../memory/domain.mjs';
import { historyEvent } from '../memory/history.mjs';

const history = (text, extra = {}) => ({ id: uuid(), revision: 1, type: 'voice', resultText: text, insertedText: text, ...extra });
const proposal = (h, kind, value, extra = {}) => ({ kind, action: 'upsert', id: 'new:' + kind,
  value, sourceId: h.id, sourceRevision: h.revision, category: 'observation', confidence: .99, quote: h.resultText, ...extra });
const learn = (h, ops, old = emptyMemory()) => changeMemory(old, ops, { history: h }).memory;

test('learn reusable vocabulary from adopted text, never measurements, sentences or invented evidence', () => {
  for (const [term, termType] of [['1.5米', 'technical'], ['十二点到了。', 'name'], ['hao', 'pinyin'], ['123', 'technical']]) {
    const h = history(term);
    assert.equal(learn(h, [proposal(h, 'hotword', { text: term }, { termType })]).hotwords.length, 0);
  }
  const h = history('我们正在试用Nebula-X。', { asrText: '多出来的词' });
  const op = proposal(h, 'hotword', { text: 'Nebula-X' }, { termType: 'name' });
  assert.equal(learn(h, [op]).hotwords[0].text, 'Nebula-X');
  assert.equal(learn(h, [{ ...op, quote: 'Nebula-X不存在的证据' }]).hotwords.length, 0);
  assert.equal(learn(h, [{ ...op, quote: h.asrText, value: { text: h.asrText } }]).hotwords.length, 0);
});

test('practice never changes personal memory; ambient recordings cannot teach personal style or people', () => {
  const h = history('小周是我的同事。', { practice: true });
  assert.equal(historyEvent(h).practice, true);
  const ops = [proposal(h, 'hotword', { text: '小周' }, { termType: 'name' }),
    proposal(h, 'style', { scene: '工作', rules: ['简洁'], examples: [h.resultText] })];
  assert.deepEqual(learn(h, ops), emptyMemory());
  assert.equal(learn({ ...h, practice: false, type: 'recording' }, [ops[1]]).styles.length, 0);
});

test('a name and repeated nickname are insufficient to create a contact; grounded introductions are enough', () => {
  const h = history('小周');
  const person = proposal(h, 'person', { name: '小周' });
  const nickname = proposal(h, 'fact', { key: 'nickname', value: '小周' }, { personId: person.id });
  assert.equal(learn(h, [person, nickname]).people.length, 0);
  const intro = history('小周是我的同事。');
  const p = proposal(intro, 'person', { name: '小周' });
  const r = proposal(intro, 'relation', { targetId: 'self', type: '同事' }, { personId: p.id });
  const result = learn(intro, [p, r]);
  assert.equal(result.people.length, 1); assert.equal(result.people[0].relations[0].type, '同事');
});

test('visible platform identity can identify a contact; fabricated identity cannot', () => {
  const h = history('', { type: 'advisor', identity: { platform: 'QQ', nickname: '小周', handle: 'fixture-handle' },
    messages: [{ speaker: 'other', text: '小周：周五开会。' }] });
  const op = proposal(h, 'person', { name: '小周', identities: [h.identity] }, { quote: '小周：周五开会。' });
  assert.equal(learn(h, [op]).people.length, 1);
  assert.equal(learn(h, [{ ...op, value: { ...op.value, identities: [{ ...h.identity, handle: 'different' }] } }]).people.length, 0);
});

test('fixed field aliases and new IDs cannot bypass protected facts; identical data makes no duplicate conflict', () => {
  const personId = uuid(), factId = uuid();
  const initial = changeMemory(emptyMemory(), [
    { kind: 'person', action: 'upsert', id: personId, value: { name: '小周' } },
    { kind: 'fact', action: 'upsert', personId, id: factId, value: { key: '公司', value: '星野' } },
  ], { manual: true }).memory;
  const h = history('小周在云杉工作。');
  const op = proposal(h, 'fact', { key: 'company', value: '云杉', scope: 'current' }, { personId });
  const result = learn(h, [op], initial);
  assert.equal(result.people[0].facts.length, 1); assert.equal(result.people[0].facts[0].value, '星野');
  assert.equal(result.candidates.length, 1); assert.equal(result.candidates[0].operation.id, factId);
  const same = history('小周在星野工作。');
  assert.equal(learn(same, [proposal(same, 'fact', { key: 'company', value: '星野' }, { personId })], initial).candidates.length, 0);
  const invented = proposal(h, 'fact', { key: '性别', value: '女' }, { personId });
  assert.equal(learn(h, [invented], initial).people[0].facts.length, 1);
});
