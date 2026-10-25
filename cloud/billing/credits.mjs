import { requireValue } from '../http/errors.mjs';

export const credits = milli => Number(milli) / 1000;
export function milliCredits(value) {
  const amount = Math.round(value * 1000);
  requireValue(typeof value === 'number' && Number.isFinite(value) && value >= 0
    && Number.isSafeInteger(amount) && Math.abs(amount / 1000 - value) < 1e-8,
    'INVALID_CREDITS', '积分须为非负数，最多三位小数');
  return amount;
}
export function validatePricing(raw) {
  requireValue(raw && raw.unit === 'Credit' && raw.creditMicroCny === 1000
    && Number.isSafeInteger(raw.markupBasisPoints) && raw.markupBasisPoints >= 10000
    && raw.markupBasisPoints <= 100000 && Array.isArray(raw.models) && raw.models.length > 0
    && raw.models.length <= 30, 'INVALID_PRICING', '积分单位或计价不正确');
  const seen = new Set();
  for (const model of raw.models) {
    requireValue(typeof model.id === 'string' && /^[a-z0-9][a-z0-9._/-]{1,120}$/.test(model.id)
      && !seen.has(model.id) && typeof model.name === 'string' && model.name.length <= 80,
      'INVALID_PRICING', '模型名称或标识不正确');
    seen.add(model.id);
    for (const key of ['inputMicroCnyPerMillion', 'outputMicroCnyPerMillion'])
      requireValue(Number.isSafeInteger(model[key]) && model[key] >= 0 && model[key] <= 1000000000,
        'INVALID_PRICING', '模型成本须为每百万 Token 的人民币微元整数');
  }
  return structuredClone(raw);
}

const whole = n => Number.isSafeInteger(n) && n >= 0 && n <= 100000000;
const divideUp = (n, d) => Number((n + d - 1n) / d);
/** Integer rational arithmetic: 1 milli-Credit = 1 micro-CNY. No per-token rounding. */
export function priceUsage(snapshot, input, output) {
  requireValue(whole(input) && whole(output), 'INVALID_USAGE', '供应商用量无效', 502);
  const numerator = BigInt(input) * BigInt(snapshot.rate.inputMicroCnyPerMillion)
    + BigInt(output) * BigInt(snapshot.rate.outputMicroCnyPerMillion);
  return {
    milliCredits: divideUp(numerator * BigInt(snapshot.markupBasisPoints), 10000000000n),
    costMicroCny: divideUp(numerator, 1000000n),
  };
}
export function normalizeUsage(usage) {
  const input = usage?.prompt_tokens ?? usage?.input_tokens;
  const output = usage?.completion_tokens ?? usage?.output_tokens;
  return whole(input) && whole(output) ? { input, output } : null;
}

export class CreditPricing {
  constructor(db) { this.db = db; }
  async get(c = this.db) {
    return (await c.query('SELECT version,definition FROM credit_pricing WHERE singleton=1')).rows[0];
  }
  async quote(request, available, c = this.db) {
    const row = await this.get(c);
    const rate = row.definition.models.find(m => m.id === request.model);
    requireValue(rate, 'PRICE_NOT_CONFIGURED', '此模型尚未配置积分价格', 503);
    const snapshot = { version: Number(row.version), model: request.model, rate,
      markupBasisPoints: row.definition.markupBasisPoints, priceBasis: row.definition.priceBasis };
    if ([3, 6].includes(request.replyCount)) snapshot.replyCount = request.replyCount;
    let input = request.inputTokens, output = request.outputTokens;
    if (request.asr) {
      let lo = 0, hi = request.asr.maxSeconds;
      while (lo < hi) {
        const seconds = Math.ceil((lo + hi) / 2);
        const amount = priceUsage(snapshot, request.asr.baseTokens + seconds * 100, seconds * 90).milliCredits;
        if (amount <= available) lo = seconds; else hi = seconds - 1;
      }
      requireValue(lo > 0, 'CREDITS_EXHAUSTED', '积分不足，已录内容仍保留在手机', 402);
      snapshot.maxAsrSeconds = lo;
      input = request.asr.baseTokens + lo * 100; output = lo * 90;
    }
    const amount = priceUsage(snapshot, input, output).milliCredits;
    requireValue(amount <= available, 'CREDITS_EXHAUSTED', '积分不足，本次未扣分', 402);
    return { ...snapshot, inputTokens: input, outputTokens: output, reserved: amount };
  }
  async save(actor, expectedVersion, value) {
    const definition = validatePricing(value);
    return this.db.transaction(async c => {
      const row = (await c.query('SELECT * FROM credit_pricing WHERE singleton=1 FOR UPDATE')).rows[0];
      requireValue(Number(row.version) === expectedVersion, 'PRICE_CONFLICT', '计价已更新，请刷新后修改', 409);
      definition.version = expectedVersion + 1;
      await c.query('UPDATE credit_pricing SET version=$1,definition=$2,updated_at=now() WHERE singleton=1',
        [definition.version, definition]);
      await this.db.audit(c, actor, 'credit-pricing.publish', String(definition.version), { previous: row.definition, definition });
      return { version: definition.version, definition };
    });
  }
}
