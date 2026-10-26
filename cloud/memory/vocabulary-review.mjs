import { canonical, digest } from '../security.mjs';
import { requireValue } from '../http/errors.mjs';
import { lexicalEntry } from './quality.mjs';

/** Explicit operator migration of old automatic vocabulary. Never an LLM delete permission. */
export function reviewVocabulary(current, plan) {
  requireValue(plan.expectedDigest === digest(canonical(current)), 'MEMORY_CONFLICT', '记忆已变化，重新审查', 409);
  requireValue(Array.isArray(plan.entries) && plan.entries.length <= 10000, 'MEMORY_FORMAT', '词库审查格式错误');
  const memory = structuredClone(current), seen = new Set();
  for (const entry of plan.entries) {
    requireValue(!seen.has(entry.id), 'MEMORY_FORMAT', '审查词条重复'); seen.add(entry.id);
    const word = memory.hotwords.find(w => w.id === entry.id), key = 'hotword:' + entry.id;
    requireValue(word && !memory.protected.includes(key), 'MEMORY_REFERENCE', '只能审查现有自动词条');
    requireValue(['keep', 'drop'].includes(entry.action), 'MEMORY_FORMAT', '未知词库审查操作');
    if (entry.action === 'keep') {
      requireValue(lexicalEntry({ ...word, lexical: entry.lexical }), 'MEMORY_FORMAT', '缺少具体词义');
      word.lexical = { kind: entry.lexical.kind, meaning: entry.lexical.meaning.trim() };
    } else {
      memory.hotwords = memory.hotwords.filter(w => w.id !== entry.id);
      delete memory.origins[key];
      memory.candidates = memory.candidates.filter(c => !(c.operation.kind === 'hotword' && c.operation.id === entry.id));
    }
  }
  return memory;
}
