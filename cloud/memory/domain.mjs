import { isUuid } from '../billing/ledger.mjs';
import { requireValue } from '../http/errors.mjs';
import { canonical, digest, uuid } from '../security.mjs';
import { learnedTerm, groundedPerson, groundedQuote, canonicalFactKey, canonicalFactScope, nicknameFact } from './quality.mjs';

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
  if (raw.kind === 'hotword') {
    out.value = { text: text(v.text, 60), variants: list(v.variants || [], 50, 60) };
    if (v.lexical) {
      requireValue(['name', 'technical', 'abbreviation', 'address'].includes(v.lexical.kind), 'MEMORY_FORMAT', '词条类型错误');
      out.value.lexical = { kind: v.lexical.kind, meaning: text(v.lexical.meaning, 160) };
    }
  }
  if (raw.kind === 'style') out.value = { scene: text(v.scene, 80), rules: list(v.rules || [], 30, 300), examples: list(v.examples || [], 12, 600) };
  if (raw.kind === 'person') {
    requireValue(Array.isArray(v.identities || []) && (v.identities || []).length <= 10, 'MEMORY_FORMAT', '人物身份过多');
    out.value = { name: text(v.name, 100), aliases: list(v.aliases || [], 30, 100), identities: (v.identities || []).map(i => ({ platform: text(i.platform, 120), nickname: text(i.nickname, 100), handle: text(i.handle || '', 150, true) })) };
  }
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
  requireValue(memory.hotwords.length <= 10000 && memory.styles.length <= 1000 && memory.people.length <= 500 && memory.candidates.length <= 1000, 'MEMORY_CAPACITY', '个人记忆容量已满；待办保留', 409);
  const allIds = new Set();
  for (const entity of [...memory.hotwords, ...memory.styles, ...memory.people, ...memory.people.flatMap(p => [...p.facts, ...p.relations])]) {
    requireValue(!allIds.has(entity.id), 'MEMORY_FORMAT', '记忆标识重复'); allIds.add(entity.id);
  }
  for (const p of memory.people) requireValue(p.facts.length <= 100 && p.relations.length <= 100, 'MEMORY_CAPACITY', '每个人物最多100条资料或关系', 409);
  for (const p of memory.people) for (const r of p.relations)
    requireValue(r.targetId === 'self' || people.has(r.targetId), 'MEMORY_REFERENCE', '关系指向的人物不存在');
  for (const [group, key] of [['hotwords', 'text'], ['styles', 'scene']])
    requireValue(new Set(memory[group].map(r => r[key])).size === memory[group].length, 'MEMORY_CONFLICT', '同一个词或场景已经存在，请更新原记录', 409);
}

function editDistance(a,b){
  let row=Array.from({length:b.length+1},(_,i)=>i);
  for(let i=1;i<=a.length;i++){const next=[i];for(let j=1;j<=b.length;j++)next[j]=Math.min(next[j-1]+1,row[j]+1,row[j-1]+(a[i-1]===b[j-1]?0:1));row=next;}return row[b.length];
}
export function correctedWords(history, op) {
  if (!['voice','advisor','recording'].includes(history.type) || op.kind !== 'hotword' || op.action !== 'upsert') return false;
  const before=history.insertedText||'',after=history.resultText||'';
  if(!before||!after||before===after)return false;
  let prefix=0,suffix=0;
  while(prefix<Math.min(before.length,after.length)&&before[prefix]===after[prefix])prefix++;
  while(suffix<Math.min(before.length-prefix,after.length-prefix)&&before[before.length-1-suffix]===after[after.length-1-suffix])suffix++;
  const start=Math.max(0,prefix-60),left=before.slice(start,before.length-Math.max(0,suffix-60)),right=after.slice(start,after.length-Math.max(0,suffix-60));
  if(left.length>400||right.length>400)return false;
  const baseline=editDistance(Array.from(left),Array.from(right));
  return op.value.variants.some(wrong=>wrong!==op.value.text&&left.includes(wrong)&&right.includes(op.value.text)&&editDistance(Array.from(left.replaceAll(wrong,op.value.text)),Array.from(right))<baseline);
}

/** Pure transaction proposal: personal facts are never overwritten as a whole person. */
export function changeMemory(current, raws, { manual = false, history, histories, origin, createId = uuid } = {}) {
  requireValue(Array.isArray(raws) && raws.length <= 40, 'MEMORY_FORMAT', '每次最多40项操作');
  const memory = structuredClone(current), ids = new Map();
  const operations = raws.map(raw => operation(raw, { ids, createId }));
  const touched = new Set();
  for (let i = 0; i < operations.length; i++) {
    const op = operations[i], raw = raws[i];
    if (!manual && op.kind === 'fact' && op.action === 'upsert') {
      op.value.key = canonicalFactKey(op.value.key);
      op.value.scope = canonicalFactScope(op.value.scope);
      const person = memory.people.find(p => p.id === op.personId);
      const same = person?.facts.find(f => canonicalFactKey(f.key) === op.value.key && canonicalFactScope(f.scope) === op.value.scope);
      if (same && same.id !== op.id) op.id = same.id;
    }
    const key = entityKey(op);
    requireValue(!touched.has(key), 'MEMORY_FORMAT', '同一项不能在一批中重复修改'); touched.add(key);
    const old = findEntity(memory, op);
    const source = histories ? histories.find(h => h.id === raw.sourceId && h.revision === raw.sourceRevision) : history;
    const evidence = source ? { historyId: source.id, revision: source.revision } : origin;
    if (!manual) {
      requireValue(evidence && source && raw.sourceId === source.id && raw.sourceRevision === source.revision, 'MEMORY_SOURCE', '更新来源与历史修订不符');
      requireValue(['correction', 'observation'].includes(raw.category) && Number.isFinite(raw.confidence) && raw.confidence >= 0 && raw.confidence <= 1, 'MEMORY_FORMAT', '更新置信度或分类无效');
      if (raw.confidence < 0.9 || memory.blockedWords.includes(op.value?.text)) continue;
      const explicitCorrection = raw.category === 'correction' && raw.confidence >= 0.95 && correctedWords(source, op);
      if (raw.category === 'correction' && !explicitCorrection) continue;
      if (source.practice === true) continue;
      if (source.type === 'recording' && op.kind !== 'hotword') continue;
      if (op.action === 'upsert' && op.kind === 'hotword' && !learnedTerm(raw, source, op.value)) continue;
      if (op.action === 'upsert' && op.kind === 'person' && !groundedPerson(op, raw, source, operations, raws, old)) continue;
      if (op.action === 'upsert' && ['fact', 'relation'].includes(op.kind)
          && (!groundedQuote(raw, source) || !raw.quote.includes(op.kind === 'fact' ? op.value.value : op.value.type))) continue;
      if (op.action === 'upsert' && op.kind === 'fact' && nicknameFact(op.value.key)) continue;
      const ownText = source.resultText || '';
      const adopted=(histories || [source]).filter(h=>h.type!=='recording').map(h=>h.resultText || '').join('\n');
      // Suggestions are not the user's style; require a verbatim example from their adopted text.
      if (op.kind === 'style' && (!op.value?.examples.length || !op.value.examples.every(e => adopted.includes(e) || old?.examples?.includes(e)))) continue;
      if (op.kind === 'hotword' && !explicitCorrection) {
        const term = op.value?.text;
        if (!term || !ownText.includes(term)) continue;
      }
      if (op.action === 'delete') continue; // Retractions use evidence revisions; deletion is never inferred from silence.
      if (old && canonical(Object.fromEntries(Object.keys(op.value).map(k => [k, old[k]]))) === canonical(op.value)) continue;
      const conflict = memory.protected.includes(key) || (old && ['fact', 'relation', 'person'].includes(op.kind) && canonical(Object.fromEntries(Object.keys(op.value).map(k=>[k,old[k] || (Array.isArray(op.value[k])?[]:'')]))) !== canonical(op.value));
      if (conflict) {
        if (!memory.candidates.some(c => c.expected === fingerprint(old) && canonical(c.operation) === canonical(op)))
          memory.candidates.push({ id: createId(), operation: op, expected: fingerprint(old), origin: evidence, reason: text(raw.reason || '这项资料与已有记录不同', 400) });
        continue;
      }
    }
    if (manual && op.kind === 'hotword' && op.action === 'delete' && old && !memory.blockedWords.includes(old.text)) memory.blockedWords.push(old.text);
    if (manual && op.kind === 'hotword' && op.action === 'upsert') memory.blockedWords = memory.blockedWords.filter(w => w !== op.value.text);
    if(!manual && op.personId && !memory.people.some(p=>p.id===op.personId))continue;
    apply(memory, op);
    if (manual && !memory.protected.includes(key)) memory.protected.push(key);
    if (evidence) memory.origins[key] = evidence;
    if (!manual && op.action === 'upsert') findEntity(memory, op).confidence = raw.confidence;
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
  const keep = (kind, record, personId) => {
    const key = entityKey({ kind, id: record.id, personId }), source = memory.origins[key];
    if (!memory.protected.includes(key) && source?.historyId === historyId && source.revision < revision) { delete memory.origins[key]; return false; }
    return true;
  };
  memory.hotwords = memory.hotwords.filter(w => keep('hotword', w));
  memory.styles = memory.styles.filter(w => keep('style', w));
  for (const p of memory.people) {
    p.facts = p.facts.filter(f => keep('fact', f, p.id));
    p.relations = p.relations.filter(r => keep('relation', r, p.id));
  }
  memory.people = memory.people.filter(p => p.facts.length || p.relations.length || keep('person', p));
  const ids = new Set(memory.people.map(p => p.id));
  for (const p of memory.people) p.relations = p.relations.filter(r => r.targetId === 'self' || ids.has(r.targetId));
  memory.candidates = memory.candidates.filter(c => c.origin.historyId !== historyId || c.origin.revision >= revision);
  return memory;
}
