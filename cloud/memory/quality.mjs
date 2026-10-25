import fs from 'node:fs';

export const personFields = JSON.parse(fs.readFileSync(new URL('../contracts/person.json', import.meta.url), 'utf8')).fields;
const termTypes = new Set(['name', 'technical', 'abbreviation', 'address']);

/** Short reusable vocabulary, never a sentence, bare quantity or unfinished input. */
export function reusableTerm(term) {
  if (typeof term !== 'string' || !term.trim() || Array.from(term).length > 32) return false;
  if (/[\r\n。！？!?；;，,]/u.test(term) || /^[-+\d.,%\s]+$/u.test(term)) return false;
  return !/^[+-]?\d+(?:[.,]\d+)?\s*(?:毫米|厘米|千米|公里|米|毫升|升|千克|公斤|克|吨|岁|点|小时|分钟|秒|天|周|个月|月|年|元|块|万|亿|%|kg|cm|mm|km|ml|gb|mb)$/iu.test(term);
}

export function evidenceText(history) {
  return [history.resultText || '', ...(history.messages || []).filter(m => m.speaker !== 'unknown').map(m => m.text)].join('\n');
}

export function groundedQuote(raw, history) {
  return typeof raw.quote === 'string' && raw.quote.length > 0 && raw.quote.length <= 2000
    && evidenceText(history).includes(raw.quote);
}

export function learnedTerm(raw, history, term) {
  return reusableTerm(term) && termTypes.has(raw.termType) && groundedQuote(raw, history)
    && raw.quote.includes(term) && (history.resultText || '').includes(raw.quote);
}

export function canonicalFactKey(key) {
  const field = personFields.find(f => f.kind === 'fact' && (f.key === key || f.aliases.some(a => a.toLowerCase() === key.toLowerCase())));
  return field?.key || key;
}

export const canonicalFactScope = scope => !scope || ['current', '当前'].includes(scope.toLowerCase()) ? '当前' : scope;

export const nicknameFact = key => ['nickname', 'name', '昵称', '称呼'].includes(key.toLowerCase());

export function verifiedIdentity(identity, history) {
  const source = history.identity || {};
  return identity.platform === source.platform && identity.nickname === source.nickname
    && !!source.platform && !!source.nickname && (!identity.handle || identity.handle === source.handle);
}

/** An unlabelled name mention is not enough to create a contact. */
export function groundedPerson(op, raw, source, operations, raws, old) {
  if (!groundedQuote(raw, source) || ![op.value.name, ...op.value.aliases].some(n => raw.quote.includes(n))) return false;
  if (!op.value.identities.every(i => verifiedIdentity(i, source) || old?.identities?.some(v => JSON.stringify(v) === JSON.stringify(i)))) return false;
  if (old || op.value.identities.some(i => verifiedIdentity(i, source))) return true;
  return operations.some((child, i) => child.personId === op.id && child.action === 'upsert'
    && ['fact', 'relation'].includes(child.kind) && !nicknameFact(child.value.key || '')
    && raws[i].sourceId === raw.sourceId && raws[i].sourceRevision === raw.sourceRevision
    && raws[i].category === 'observation' && raws[i].confidence >= .9 && groundedQuote(raws[i], source)
    && raws[i].quote.includes(op.value.name)
    && raws[i].quote.includes(child.kind === 'fact' ? child.value.value : child.value.type));
}
