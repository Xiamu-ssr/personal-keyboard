import { translationInput, translationResult } from '../translation/contract.mjs';
import { memoryCounter } from '../memory/counter.mjs';
import fs from 'node:fs/promises';
import { requireValue, ServiceError } from '../http/errors.mjs';
import { digest } from '../security.mjs';
import { isUuid } from '../billing/ledger.mjs';

const bounded = (value, max, name = '文字') => {
  requireValue(typeof value === 'string' && value.length <= max, 'INVALID_INPUT', name + '过长或格式错误');
  return value;
};
const strings = (values, max = 20, length = 200) => {
  requireValue(Array.isArray(values) && values.length <= max, 'INVALID_INPUT', '资料数量过多');
  return values.map(v => bounded(v, length));
};
export function modelInput(kind, raw) {
  if (kind === 'translation') return translationInput(raw);
  requireValue(raw && typeof raw === 'object' && !Array.isArray(raw), 'INVALID_INPUT', '缺少任务内容');
  const allowed = kind === 'rewrite' ? ['transcript', 'recentContext', 'conversationRef'] : ['images', 'conversationRef', 'recentContext', 'intent', 'slotId', 'previousReplies', 'platform'];
  requireValue(Object.keys(raw).every(key => allowed.includes(key)), 'INVALID_INPUT', '个人记忆由云端读取，请勿重复上传');
  const input = { recentContext: bounded(raw.recentContext || '', 12000), conversationRef: bounded(raw.conversationRef || '', 250) };
  if (kind === 'rewrite') return { ...input, transcript: bounded(raw.transcript, 4000) };
  requireValue(kind === 'advisor', 'INVALID_KIND', '未知文字任务');
  requireValue(!raw.slotId || /^slot[1-6]$/.test(raw.slotId), 'INVALID_INPUT', '表达槽位无效');
  Object.assign(input, { intent: bounded(raw.intent || '', 1000), platform: bounded(raw.platform || '', 120), slotId: raw.slotId || '', previousReplies: strings(raw.previousReplies || [], 24, 1000), imageDigests: [] });
  requireValue(Array.isArray(raw.images) && raw.images.length > 0 && raw.images.length <= 2, 'INVALID_IMAGE', '军师需要一至两张本次截图');
  let size = 0;
  for (const image of raw.images) {
    requireValue(typeof image === 'string' && /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(image), 'INVALID_IMAGE', '仅支持本次 JPEG 截图');
    const bytes = Buffer.from(image.split(',')[1], 'base64'); size += bytes.length;
    requireValue(bytes.length > 16 && bytes[0] === 255 && bytes[1] === 216 && size <= 1800000, 'INVALID_IMAGE', '图片总量须小于1.8MB');
    input.imageDigests.push(digest(bytes));
  }
  return input;
}

export function validateModelResult(kind, text, input) {
  let value;
  try { value = JSON.parse(text.trim()); }
  catch { throw new ServiceError('MODEL_FORMAT', '模型结果格式不正确，预留已释放', 502); }
  if (kind === 'translation') return translationResult(value, input);
  if (kind === 'rewrite') {
    requireValue(typeof value.text === 'string' && value.text.trim() && value.text.length <= 6000, 'MODEL_FORMAT', '整理结果无效', 502);
    return { text: value.text.trim(), changed: value.text !== input.transcript, uncertainSpans: strings(value.uncertainSpans || []) };
  }
  const slots = input.slots || [];
  const keys = slots.map((_, i) => 'r' + (i + 1));
  requireValue(kind === 'advisor' && value.replies && !Array.isArray(value.replies) && [3, 6].includes(slots.length)
    && Object.keys(value.replies).length === keys.length && keys.every(k => Object.hasOwn(value.replies, k)), 'MODEL_FORMAT', '模型没有完整返回本次表达', 502);
  const replies = slots.map((slot, i) => {
    return { slotId: slot.id, style: slot.name, text: bounded(value.replies[keys[i]], 1000) };
  });
  requireValue(replies.every(r => r.text.trim()), 'MODEL_FORMAT', '回复不能为空', 502);
  const previous = new Set((input.previousReplies || []).map(text => text.trim()));
  requireValue(new Set(replies.map(r => r.text.trim())).size === replies.length && replies.every(r => !previous.has(r.text.trim())), 'MODEL_FORMAT', '这次换法有重复，请重试，预留已释放', 502);
  requireValue(Array.isArray(value.messages) && value.messages.length <= 300, 'MODEL_FORMAT', '缺少截图文字依据', 502);
  const messages = value.messages.map(m => {
    requireValue(['self', 'other', 'unknown'].includes(m.speaker) && Number.isInteger(m.image) && m.image >= 1 && m.image <= input.imageDigests.length, 'MODEL_FORMAT', '截图消息归属无效', 502);
    return { speaker: m.speaker, text: bounded(m.text, 5000), image: m.image };
  });
  const identity = value.identity || {};
  const personId = isUuid(identity.personId) && (input.people || []).some(p => p.id === identity.personId) ? identity.personId : '';
  return { replies, messages, identity: { personId, platform: bounded(identity.platform || input.platform || '', 120), nickname: bounded(identity.nickname || '', 100), handle: bounded(identity.handle || '', 150), confidence: Number.isFinite(identity.confidence) ? Math.max(0, Math.min(1, identity.confidence)) : 0 }, uncertainty: strings(value.uncertainty || []) };
}

export function advisorResponseFormat(input) {
  const string = maxLength => ({ type: 'string', maxLength });
  const object = properties => ({ type: 'object', properties, required: Object.keys(properties), additionalProperties: false });
  const schema = object({
    messages: { type: 'array', maxItems: 300, items: object({ speaker: { type: 'string', enum: ['self', 'other', 'unknown'] }, text: string(5000), image: { type: 'integer', minimum: 1, maximum: input.imageDigests.length } }) },
    identity: object({ personId: string(36), platform: string(120), nickname: string(100), handle: string(150), confidence: { type: 'number', minimum: 0, maximum: 1 } }),
    // Required named positions prevent a provider from merging equal-looking styles.
    // The public result remains the ordered reply array consumed by Android.
    replies: object(Object.fromEntries(input.slots.map((_, i) => ['r' + (i + 1), { ...string(1000), minLength: 1 }]))),
    uncertainty: { type: 'array', maxItems: 20, items: string(200) },
  });
  return { type: 'json_schema', json_schema: { name: 'advisor_reply', strict: true, schema } };
}

export function advisorDirective(input, template) {
  return template.prefix.replaceAll('{{replyCount}}', String(input.slots.length))
    + (input.intent.trim() ? template.explicit.replaceAll('{{intent}}', () => input.intent) : template.unspecified)
    + template.suffix;
}

export class TextModels {
  constructor(cfg, fetcher = fetch) { this.cfg = cfg; this.fetcher = fetcher; this.prompts = new Map(); }
  async init() {
    for (const [kind, file] of Object.entries({ advisor: 'reply', rewrite: 'rewrite', translation: 'translation' })) {
      const text = await fs.readFile(new URL('../prompts/' + file + '.md', import.meta.url), 'utf8');
      const request = kind === 'advisor' ? JSON.parse(await fs.readFile(new URL('../prompts/reply-request.json', import.meta.url), 'utf8')) : null;
      this.prompts.set(kind, { text, request, revision: digest(text + (request ? JSON.stringify(request) : '')).slice(0, 12) });
    }
  }
  async prepare(kind, input) {
    requireValue(this.cfg.textReady, 'MODEL_NOT_READY', '云端文字模型尚未配置', 503);
    const visual = kind === 'advisor';
    requireValue(!visual || (input.imageDigests?.length && this.cfg.visionReady), 'VISION_NOT_READY', '军师图文模型尚未配置', 503);
    const prompt = this.prompts.get(kind), model = visual ? this.cfg.visionModel : this.cfg.textModel;
    const directive = visual ? advisorDirective(input, prompt.request) : '';

    const systemText = prompt.text + '\n请严格只输出 JSON 对象。';
    const responseFormat = visual ? advisorResponseFormat(input) : { type: 'json_object' };
    // The verified text counter receives a conservative representation including the
    // schema; image and generation/reasoning allowances are reserved separately.
    const textRequest = { model: this.cfg.textModel, enable_thinking: false, response_format: { type: 'json_object' }, messages: [{ role: 'system', content: systemText }, { role: 'user', content: JSON.stringify(visual ? { input, response_format: responseFormat, directive } : input) }] };
    const tokens = (await memoryCounter.count(textRequest)).tokens;
    // Visual reasoning, its schema/protocol and output share a conservative 16K
    // allowance. This does not change the exact memory-learning input protocol.
    requireValue(tokens + (visual ? input.imageDigests.length * 4096 : 0) + (visual ? 16384 : 8192) <= this.cfg.memoryContextTokens && tokens <= this.cfg.memoryMaxInputTokens, 'MEMORY_CAPACITY', '个人资料已超出模型容量，请精简后重试', 409);
    const outputLimit = visual ? 16384 : kind === 'translation' ? 8192 : 1600;
    return { kind, input, model, directive, systemText, responseFormat, tokens, outputLimit, promptRevision: prompt.revision };
  }
  async execute(prepared, images, signal) {
    const { kind, input, model, directive, systemText, responseFormat, outputLimit, promptRevision } = prepared;
    const visual = kind === 'advisor';
    const user = visual ? [{ type: 'text', text: JSON.stringify(input) }, ...images.map(url => ({ type: 'image_url', image_url: { url } })), { type: 'text', text: directive }] : JSON.stringify(input);
    let response;
    try {
      response = await this.fetcher(this.cfg.textBase.replace(/\/$/, '') + '/chat/completions', {
        method: 'POST', signal: AbortSignal.any([signal, AbortSignal.timeout(65000)]),
        headers: { Authorization: 'Bearer ' + this.cfg.modelKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, messages: [{ role: 'system', content: systemText }, { role: 'user', content: user }],
          max_completion_tokens: outputLimit, temperature: visual ? 0.3 : 0.2, response_format: responseFormat, enable_thinking: visual,
          ...(visual ? { thinking_budget: 4096 } : {}) }),
      });
    } catch {
      if (signal.aborted) throw new ServiceError('CANCELLED', '任务已取消', 409);
      throw new ServiceError('MODEL_NETWORK', '模型连接失败，请稍后重试', 502);
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new ServiceError(response.status === 429 ? 'PROVIDER_LIMIT' : 'PROVIDER_ERROR', response.status === 429 ? '供应商限流，请稍后重试' : '供应商调用失败，预留已释放', 502);
    }
    let size = 0; const chunks = [];
    for await (const chunk of response.body) { size += chunk.length; requireValue(size <= 300000, 'MODEL_FORMAT', '模型返回过大', 502); chunks.push(chunk); }
    let output;
    try { output = JSON.parse(Buffer.concat(chunks).toString()); }
    catch { throw new ServiceError('MODEL_FORMAT', '模型返回格式不正确', 502); }
    requireValue(output.choices?.[0]?.finish_reason === 'stop', 'MODEL_TRUNCATED', '模型结果没有完整返回，请重试', 502);
    return { result: validateModelResult(kind, output.choices?.[0]?.message?.content || '', input),
      provider: { model, usage: { ...output.usage, promptRevision } } };
  }
}
