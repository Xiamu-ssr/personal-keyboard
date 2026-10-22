import { isUuid } from '../billing/ledger.mjs';
import { requireValue } from '../http/errors.mjs';
import { canonical, digest, uuid } from '../security.mjs';

const groups = { hotword: 'hotwords', style: 'styles', person: 'people' };
const text = (value, max, empty = false) => {
  requireValue(typeof value === 'string' && value.length <= max && (empty || value.trim()), 'MEMORY_FORMAT', '个人记忆格式错误');
  return value.trim();
};
const list = (value, max, length) => {
  requireValue(Array.isArray(value) && value.length <= max, 'MEMORY_FORMAT', '个人记忆列表过长');
  return [...new Set(value.map(v => text(v, length)))];
};
export const emptyMemory = () => ({ hotwords: [], styles: [], people: [], protected: [], blockedWords: [], candidates: [], origins: {} });
export const entityKey = op => op.kind + ':' + (op.personId ? op.personId + ':' : '') + op.id;
export function findEntity(memory, op) {
  if (groups[op.kind]) return memory[groups[op.kind]].find(v => v.id === op.id);
  const person = memory.people.find(v => v.id === op.personId);
  return person?.[op.kind === 'fact' ? 'facts' : 'relations']?.find(v => v.id === op.id);
}
export const fingerprint = value => digest(canonical(value || null));

export function operation(raw, { ids = new Map(), createId = uuid } = {}) {
  requireValue(raw && typeof raw === 'object' && ['hotword', 'style', 'person', 'fact', 'relation'].includes(raw.kind) && ['upsert', 'delete'].includes(raw.action), 'MEMORY_FORMAT', '未知个人记忆操作');
  const resolve = id => {
    requireValue(isUuid(id) || /^new:[a-zA-Z0-9_-]{1,64}$/.test(id || ''), 'MEMORY_FORMAT', '记忆标识错误');
    if (!id.startsWith('new:')) return id;
    if (!ids.has(id)) ids.set(id, createId());
    return ids.get(id);
  };
  const out = { kind: raw.kind, action: raw.action, id: resolve(raw.id) };
  if (['fact', 'relation'].includes(raw.kind)) out.personId = resolve(raw.personId);
  if (raw.action === 'delete') return out;
  const v = raw.value;
  requireValue(v && typeof v === 'object' && !Array.isArray(v), 'MEMORY_FORMAT', '缺少记忆内容');
  if (raw.kind === 'hotword') out.value = { text: text(v.text, 60), variants: list(v.variants || [], 50, 60) };
  if (raw.kind === 'style') out.value = { scene: text(v.scene, 80), rules: list(v.rules || [], 30, 300), examples: list(v.examples || [], 12, 600) };
  if (raw.kind === 'person') out.value = { name: text(v.name, 100), aliases: list(v.aliases || [], 30, 100) };
  if (raw.kind === 'fact') out.value = { key: text(v.key, 100), value: text(v.value, 2000), scope: text(v.scope || '当前', 100) };
  if (raw.kind === 'relation') out.value = { targetId: v.targetId === 'self' ? 'self' : resolve(v.targetId), type: text(v.type, 100) };
  return out;
}

function apply(memory, op) {
  let entries;
  if (groups[op.kind]) entries = memory[groups[op.kind]];
  else {
    const p = memory.people.find(v => v.id === op.personId);
    requireValue(p, 'MEMORY_REFERENCE', '所属人物不存在');
    entries = p[op.kind === 'fact' ? 'facts' : 'relations'];
  }
  const index = entries.findIndex(v => v.id === op.id);
  if (op.action === 'delete') {
    requireValue(index >= 0, 'MEMORY_REFERENCE', '记忆已删除', 409);
    if (op.kind === 'person') {
      for (const p of memory.people) p.relations = p.relations.filter(r => r.targetId !== op.id);
    }
    entries.splice(index, 1);
  } else {
    const value = { id: op.id, ...op.value };
    if (op.kind === 'person') Object.assign(value, { facts: entries[index]?.facts || [], relations: entries[index]?.relations || [] });
    if (index < 0) entries.push(value); else entries[index] = value;
  }
}

export function validateReferences(memory) {
  const people = new Set(memory.people.map(p => p.id));
  requireValue(memory.hotwords.length <= 10000 && memory.styles.length <= 1000 && memory.people.length <= 5000 && memory.candidates.length <= 1000, 'MEMORY_CAPACITY', '个人记忆容量已满；待办保留', 409);
  const allIds = new Set();
  for (const entity of [...memory.hotwords, ...memory.styles, ...memory.people, ...memory.people.flatMap(p => [...p.facts, ...p.relations])]) {
    requireValue(!allIds.has(entity.id), 'MEMORY_FORMAT', '记忆标识重复'); allIds.add(entity.id);
  }
  for (const p of memory.people) for (const r of p.relations)
    requireValue(r.targetId === 'self' || people.has(r.targetId), 'MEMORY_REFERENCE', '关系指向的人物不存在');
  for (const [group, key] of [['hotwords', 'text'], ['styles', 'scene']])
    requireValue(new Set(memory[group].map(r => r[key])).size === memory[group].length, 'MEMORY_CONFLICT', '同一个词或场景已经存在，请更新原记录', 409);
}

export function correctedWords(history, op) {
  if (history.type !== 'voice' || op.kind !== 'hotword' || op.action !== 'upsert') return false;
  const before = history.insertedText || '', after = history.resultText || '';
  if (!before || !after || before === after) return false;
  // Validate an observed edit, never apply a global replacement to user input.
  return op.value.variants.some(wrong => wrong !== op.value.text && before.includes(wrong)
    && before.replaceAll(wrong, op.value.text) === after);
}

/** Pure transaction proposal: personal facts are never overwritten as a whole person. */
export function changeMemory(current, raws, { manual = false, history, origin, createId = uuid } = {}) {
  requireValue(Array.isArray(raws) && raws.length <= 40, 'MEMORY_FORMAT', '每次最多40项操作');
  const memory = structuredClone(current), ids = new Map();
  const operations = raws.map(raw => operation(raw, { ids, createId }));
  const touched = new Set();
  for (let i = 0; i < operations.length; i++) {
    const op = operations[i], key = entityKey(op), raw = raws[i];
    requireValue(!touched.has(key), 'MEMORY_FORMAT', '同一项不能在一批中重复修改'); touched.add(key);
    const old = findEntity(memory, op);
    if (!manual) {
      requireValue(origin && history && raw.sourceId === history.id && raw.sourceRevision === history.revision, 'MEMORY_SOURCE', '更新来源与历史修订不符');
      requireValue(['correction', 'observation'].includes(raw.category) && Number.isFinite(raw.confidence) && raw.confidence >= 0 && raw.confidence <= 1, 'MEMORY_FORMAT', '更新置信度或分类无效');
      const auto = !memory.protected.includes(key) && !memory.blockedWords.includes(op.value?.text) && raw.category === 'correction' && raw.confidence >= 0.95 && correctedWords(history, op);
      if (!auto) {
        if (memory.candidates.some(c => c.origin.historyId === origin.historyId && c.origin.revision === origin.revision && canonical(c.operation) === canonical(op))) continue;
        memory.candidates.push({ id: createId(), operation: op, expected: fingerprint(old), origin, reason: text(raw.reason || '请核对这项个人记忆', 400) });
        continue;
      }
    }
    if (manual && op.kind === 'hotword' && op.action === 'delete' && old && !memory.blockedWords.includes(old.text)) memory.blockedWords.push(old.text);
    if (manual && op.kind === 'hotword' && op.action === 'upsert') memory.blockedWords = memory.blockedWords.filter(w => w !== op.value.text);
    apply(memory, op);
    if (manual && !memory.protected.includes(key)) memory.protected.push(key);
    if (origin) memory.origins[key] = origin;
    if (op.action === 'delete') {
      delete memory.origins[key]; memory.protected = memory.protected.filter(v => v !== key);
      if (op.kind === 'person') {
        const prefix = ':' + op.id + ':';
        for (const k of Object.keys(memory.origins)) if (k.includes(prefix)) delete memory.origins[k];
        memory.protected = memory.protected.filter(k => !k.includes(prefix));
      }
    }
  }
  validateReferences(memory);
  return { memory, idMap: Object.fromEntries(ids) };
}

export function resolveCandidate(current, id, accept) {
  const memory = structuredClone(current), i = memory.candidates.findIndex(c => c.id === id);
  requireValue(i >= 0, 'NOT_FOUND', '候选已处理', 404);
  const candidate = memory.candidates[i];
  if (accept) {
    requireValue(fingerprint(findEntity(memory, candidate.operation)) === candidate.expected, 'MEMORY_CONFLICT', '相关资料已变化，请重新整理', 409);
    apply(memory, candidate.operation);
    const key = entityKey(candidate.operation);
    if (!memory.protected.includes(key)) memory.protected.push(key);
    memory.origins[key] = candidate.origin;
    if (candidate.operation.kind === 'hotword' && candidate.operation.action === 'upsert') memory.blockedWords = memory.blockedWords.filter(w => w !== candidate.operation.value.text);
  }
  memory.candidates.splice(i, 1); validateReferences(memory); return memory;
}

export function memoryDelta(before, after) {
  const changes = [];
  for (const group of ['hotwords', 'styles', 'people']) {
    const old = new Map(before[group].map(v => [v.id, v]));
    for (const record of after[group]) {
      if (canonical(old.get(record.id) || null) !== canonical(record)) changes.push({ group, record });
      old.delete(record.id);
    }
    for (const id of old.keys()) changes.push({ group, id, deleted: true });
  }
  return changes;
}

export function retractRevision(current, historyId, revision) {
  const memory = structuredClone(current);
  memory.hotwords = memory.hotwords.filter(word => {
    const key = 'hotword:' + word.id, source = memory.origins[key];
    if (!memory.protected.includes(key) && source?.historyId === historyId && source.revision < revision) { delete memory.origins[key]; return false; }
    return true;
  });
  memory.candidates = memory.candidates.filter(c => c.origin.historyId !== historyId || c.origin.revision >= revision);
  return memory;
}
