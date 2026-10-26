import { readFileSync } from 'node:fs';
import { requireValue } from '../http/errors.mjs';

/** One contract supplies both the native editor and the prompt compiler. */
export const paletteContract = JSON.parse(readFileSync(new URL('../contracts/palette.json', import.meta.url), 'utf8'));
export const paletteSchemaVersion = paletteContract.schemaVersion;
export const expressionTemplates = paletteContract.templates;
export const attributes = paletteContract.attributes;

export function expressionAttributes(raw) {
  requireValue(raw && typeof raw === 'object' && !Array.isArray(raw)
    && Object.keys(raw).length === attributes.length
    && Object.keys(raw).every(key => attributes.some(a => a.id === key)), 'PALETTE_FORMAT', '表达属性不完整');
  return Object.fromEntries(attributes.map(a => {
    const value = raw[a.id], valid = new Set(a.options.map(o => o.id));
    if (a.kind === 'multi') {
      requireValue(Array.isArray(value) && value.length <= a.maxItems && new Set(value).size === value.length
        && value.every(v => valid.has(v)) && (!value.includes('plain') || value.length === 1), 'PALETTE_FORMAT', '表达手法组合无效');
      // Option order is canonical, so the same choices cannot create spurious conflicts.
      return [a.id, a.options.filter(o => value.includes(o.id)).map(o => o.id)];
    }
    requireValue(typeof value === 'string' && valid.has(value), 'PALETTE_FORMAT', a.name + '无效');
    return [a.id, value];
  }));
}

export function expressionInstruction(raw) {
  const value = expressionAttributes(raw);
  const rules = attributes.flatMap(a => {
    const selected = a.kind === 'multi' ? value[a.id] : [value[a.id]];
    return a.options.filter(o => selected.includes(o.id) && o.instruction).map(o => a.name + '（' + o.name + '）：' + o.instruction);
  });
  return rules.join('\n');
}

export function defaultPaletteSlots() {
  return expressionTemplates.slice(0, 6).map((p, i) => ({ id: 'slot' + (i + 1), name: p.name, attributes: expressionAttributes(p.attributes) }));
}
