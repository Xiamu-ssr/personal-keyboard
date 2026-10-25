import { credits, priceUsage } from './credits.mjs';

// Explicit illustrative workloads, not durations, character tariffs or measured averages.
const scenarios = [
  { id: 'recording', title: '保存、播放录音', input: 0, output: 0, role: null },
  { id: 'asr', title: '一段语音转写', input: 1000, output: 100, role: 'asrModel' },
  { id: 'advisor', title: '帮我回 · 六条', input: 8000, output: 4500, role: 'visionModel' },
  { id: 'variants', title: '单格换说法 · 三条', input: 7000, output: 2000, role: 'visionModel' },
  { id: 'translation', title: '一段短句翻译', input: 300, output: 40, role: 'textModel' },
];

export function usageExamples(definition, models) {
  return scenarios.flatMap(s => {
    if (!s.role) return [{ ...s, credits: 0, model: null }];
    const rate = definition.models.find(m => m.id === models[s.role]);
    if (!rate) return [];
    const amount = priceUsage({ rate, markupBasisPoints: definition.markupBasisPoints }, s.input, s.output);
    return [{ ...s, credits: credits(amount.milliCredits), model: rate.id }];
  });
}
