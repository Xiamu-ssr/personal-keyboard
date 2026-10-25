import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { credits, milliCredits, priceUsage, normalizeUsage, validatePricing } from '../billing/credits.mjs';
import { modelInput, validateModelResult } from '../models/text.mjs';
import { validatePlan } from '../operator/admin.mjs';
import { usageExamples } from '../billing/examples.mjs';
const policy = JSON.parse(await fs.readFile(new URL('../config/credits.json', import.meta.url), 'utf8'));
const snapshot = index => ({ rate: policy.models[index], markupBasisPoints: policy.markupBasisPoints });

test('displayed workload examples use the current tariff exactly once and omit unavailable models', () => {
  const models = { asrModel: policy.models[0].id, textModel: policy.models[1].id, visionModel: policy.models[1].id };
  const examples = usageExamples(policy, models);
  assert.deepEqual(examples.map(e => [e.id, e.credits]), [
    ['recording', 0], ['asr', 11.288], ['advisor', 32.463], ['variants', 19.25], ['translation', .609],
  ]);
  assert.equal(usageExamples(policy, {}).length, 1);
  const changed = { ...policy, markupBasisPoints: 15000 };
  assert.equal(usageExamples(changed, models).find(e => e.id === 'translation').credits, .522);
});

test('Credit uses exact thousandths and supplier standard input/output prices with 75 percent markup', () => {
  assert.equal(credits(milliCredits(10.001)), 10.001);
  for (const invalid of [-1, NaN, Infinity, '1', 0.0001]) assert.throws(() => milliCredits(invalid));
  assert.deepEqual(priceUsage(snapshot(1), 1000, 1000), { milliCredits: 6125, costMicroCny: 3500 });
  assert.deepEqual(priceUsage(snapshot(0), 1000, 1000), { milliCredits: 18375, costMicroCny: 10500 });
  assert.equal(priceUsage(snapshot(1), 0, 0).milliCredits, 0);
  assert.equal(priceUsage(snapshot(1), 1, 0).milliCredits, 2);
  assert.equal(priceUsage(snapshot(1), 1000000, 1000000).milliCredits, 6125000);
});
test('Reasoning tokens are already in completion usage; cached tokens are part of standard input usage', () => {
  assert.deepEqual(normalizeUsage({ prompt_tokens: 1000, completion_tokens: 100, prompt_tokens_details: { cached_tokens: 700 }, completion_tokens_details: { reasoning_tokens: 90 } }), { input: 1000, output: 100 });
  assert.deepEqual(normalizeUsage({ input_tokens: 541, output_tokens: 23 }), { input: 541, output: 23 });
  assert.equal(normalizeUsage({ total_tokens: 100 }), null);
  assert.equal(normalizeUsage({ prompt_tokens: -1, completion_tokens: 2 }), null);
});
test('Operator cannot publish malformed prices or dual-currency plans', () => {
  assert.equal(validatePricing(policy).markupBasisPoints, 17500);
  assert.throws(() => validatePricing({ ...policy, creditMicroCny: 1 }));
  assert.throws(() => validatePricing({ ...policy, models: [...policy.models, policy.models[0]] }));
  assert.throws(() => validatePricing({ ...policy, markupBasisPoints: 0 }));
  const plan = { id: 'fixture-credit', name: '测试', version: 1, grantMode: 'once_per_account', limits: { credits: 1000 }, price: { amountMinor: 0, currency: 'CNY' } };
  assert.equal(validatePlan(plan).limits.credits, 1000);
  assert.throws(() => validatePlan({ ...plan, limits: { asr_characters: 1000, advisor_generations: 20 } }));
});
test('Translation has an independent bounded input and cannot smuggle personal memory or unsupported languages', () => {
  assert.deepEqual(modelInput('translation', { text: '明天见', targetLanguage: 'en' }), { text: '明天见', sourceLanguage: 'auto', targetLanguage: 'en' });
  for (const raw of [{ text: '', targetLanguage: 'en' }, { text: 'a'.repeat(4001), targetLanguage: 'en' },
    { text: 'hi', targetLanguage: 'unknown' }, { text: 'hi', targetLanguage: 'en', people: ['secret'] }])
    assert.throws(() => modelInput('translation', raw));
  assert.equal(modelInput('translation', { text: '😀'.repeat(4000), targetLanguage: 'zh' }).text.length, 8000);
  const input = modelInput('translation', { text: '明天见', targetLanguage: 'en' });
  assert.equal(validateModelResult('translation', JSON.stringify({ text: 'See you tomorrow.', detectedLanguage: 'zh', targetLanguage: 'en' }), input).text, 'See you tomorrow.');
  assert.throws(() => validateModelResult('translation', JSON.stringify({ text: '明日', detectedLanguage: 'zh', targetLanguage: 'ja' }), input));
  assert.throws(() => validateModelResult('translation', '{}', input));
});
