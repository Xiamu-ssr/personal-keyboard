export function estimateCost({
  minutesPerDay = 30,
  repliesPerDay = 100,
  voiceEditsPerDay = 60,
  extractsPerDay = 100,
  days = 30,
  asr = "funasr",
  model = "qwen3.7-flash",
  freeHours = 0,
} = {}) {
  for (const v of [
    minutesPerDay,
    repliesPerDay,
    voiceEditsPerDay,
    extractsPerDay,
    days,
    freeHours,
  ])
    if (!Number.isFinite(v) || v < 0)
      throw new RangeError("Usage must be finite and non-negative");
  const prices = {
    "qwen3.7-flash": { input: 0.2, output: 0.8 },
    "qwen3.5-flash": { input: 0.2, output: 2 },
    "qwen-flash": { input: 0.15, output: 1.5 },
  };
  const asrPrices = { paraformer: 0.864, funasr: 1.188 };
  if (!prices[model] || !(asr in asrPrices))
    throw new RangeError("Unknown pricing profile");
  const p = prices[model],
    hours = (minutesPerDay * days) / 60;
  const text = (calls, input, output) =>
    (calls * days * (input * p.input + output * p.output)) / 1000000;
  const speech = Math.max(0, hours - freeHours) * asrPrices[asr];
  const replies = text(repliesPerDay, 3000, 600),
    voice = text(voiceEditsPerDay, 500, 200),
    extraction = text(extractsPerDay, 600, 100);
  return {
    hours,
    speech,
    replies,
    voice,
    extraction,
    total: speech + replies + voice + extraction,
    currency: "CNY",
  };
}
