import test from 'node:test';
import assert from 'node:assert/strict';
import { attributes, expressionAttributes, expressionInstruction, expressionTemplates } from '../advisor/expression.mjs';
import { defaultPalette, paletteValue, requestedSlots } from '../advisor/palette.mjs';
import { refreshDefaultSlots } from '../advisor/defaults-migration.mjs';
import { readFileSync } from 'node:fs';

test('six named slots contain complete independent preferences instead of one expression type', () => {
  const palette = defaultPalette();
  assert.equal(palette.schemaVersion, 2);
  assert.equal(palette.slots.length, 6);
  assert.equal(attributes.length, 9);
  for (const slot of palette.slots) {
    assert.equal(Object.keys(slot.attributes).length, 9);
    assert(!Object.hasOwn(slot, 'expression'));
  }
  assert.deepEqual(paletteValue(palette.slots), palette.slots);
  const drafts = requestedSlots(palette, 'slot3');
  assert.equal(drafts.length, 3);
  assert.deepEqual(drafts.map(s => s.variant), [1,2,3]);
  assert(drafts.every(s => s.instruction.includes('轻幽默')));
  assert.deepEqual(palette.slots.map(s => s.name), ['克制暧昧', 'Mean', '接梗', '共情', '干练', '边界']);
});

test('new defaults migrate untouched slots once without overwriting a custom name or attribute', () => {
  const previous = JSON.parse(readFileSync(new URL('../advisor/previous-defaults.json', import.meta.url), 'utf8'));
  previous[0].name = '我的自定义';
  previous[3].attributes.emoji = 'none';
  const next = refreshDefaultSlots(previous);
  assert.deepEqual(next[0], previous[0]);
  assert.deepEqual(next[3], previous[3]);
  assert.equal(next[1].name, 'Mean');
  assert.equal(next[2].name, '接梗');
  assert.deepEqual(refreshDefaultSlots(next), next);
});

test('romantic intent, warmth, irony and emotion are independently expressible', () => {
  const mean = expressionTemplates.find(p => p.id === 'mean');
  const flirt = expressionTemplates.find(p => p.id === 'flirt');
  assert.equal(mean.attributes.emotion, 'calm');
  assert.deepEqual(mean.attributes.rhetoric, ['irony']);
  assert.equal(flirt.attributes.warmth, 'intimate');
  assert.deepEqual(flirt.attributes.rhetoric, ['hint', 'flirt']);
  const noEmoji = { ...flirt.attributes, emoji: 'none' };
  const compiled = expressionInstruction(noEmoji);
  assert(compiled.includes('暧昧试探'));
  assert(compiled.includes('不把暧昧对象当已确认的恋人'));
  assert(compiled.includes('不使用 Emoji'));
  const polite = { ...noEmoji, rhetoric: [] };
  assert(!expressionInstruction(polite).includes('用轻轻示好'));
  const slots = defaultPalette().slots;
  slots[0] = { ...slots[0], name: '粉红泡泡', attributes: polite };
  assert(!requestedSlots({ slots })[0].instruction.includes('粉红泡泡'), 'name is not a hidden style prompt');
});

test('explicit off overrides inheritance; unlearned styles have an explicit fallback', () => {
  const raw = Object.fromEntries(attributes.map(a => [a.id, a.kind === 'multi' ? [] : 'inherit']));
  const inherited = expressionInstruction(raw);
  assert(inherited.includes('没有适用文风时'));
  const off = expressionInstruction({ ...raw, rhetoric: ['plain'], emoji: 'none' });
  assert(off.includes('不使用 Emoji'));
  assert(off.includes('不用玩笑、反讽或暗示'));
});

test('invalid or contradictory expressions cannot enter storage or the prompt compiler', () => {
  const base = expressionTemplates[0].attributes;
  for (const raw of [null, [], {}, { ...base, unknown: 'x' }, { ...base, emotion: 'invented' },
    { ...base, rhetoric: ['plain','irony'] }, { ...base, rhetoric: ['flirt','flirt'] },
    { ...base, rhetoric: ['flirt','hint','humor','teasing'] }]) assert.throws(() => expressionAttributes(raw));
  const slots = defaultPalette().slots;
  assert.throws(() => paletteValue(slots.map((s,i) => i ? s : { ...s, expression: 'natural' })));
  assert.throws(() => paletteValue(slots.slice(1)));
  assert.deepEqual(expressionAttributes({ ...base, rhetoric: ['teasing','humor'] }).rhetoric, ['humor','teasing']);
});
