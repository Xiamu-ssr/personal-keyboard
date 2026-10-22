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
  requireValue(raw && typeof raw === 'object' && !Array.isArray(raw), 'INVALID_INPUT', '缺少任务内容');
  const allowed = kind === 'rewrite' ? ['transcript', 'recentContext', 'conversationRef'] : ['images', 'personId', 'conversationRef', 'recentContext', 'intent', 'style'];
  requireValue(Object.keys(raw).every(key => allowed.includes(key)), 'INVALID_INPUT', '个人记忆由云端读取，请勿重复上传');
  const input = { recentContext: bounded(raw.recentContext || '', 12000), conversationRef: bounded(raw.conversationRef || '', 250) };
  if (kind === 'rewrite') return { ...input, transcript: bounded(raw.transcript, 4000) };
  requireValue(kind === 'advisor', 'INVALID_KIND', '未知文字任务');
  requireValue(!raw.personId || isUuid(raw.personId), 'INVALID_INPUT', '人物标识无效');
  Object.assign(input, { intent: bounded(raw.intent || '', 1000), style: bounded(raw.style || '', 40), personId: raw.personId || '', imageDigests: [] });
  requireValue(Array.isArray(raw.images) && raw.images.length > 0 && raw.images.length <= 3, 'INVALID_IMAGE', '军师需要一至三张本次截图');
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
  if (kind === 'rewrite') {
    requireValue(typeof value.text === 'string' && value.text.trim() && value.text.length <= 6000, 'MODEL_FORMAT', '整理结果无效', 502);
    return { text: value.text.trim(), changed: value.text !== input.transcript, uncertainSpans: strings(value.uncertainSpans || []) };
  }
  requireValue(kind === 'advisor' && Array.isArray(value.replies) && value.replies.length === 3, 'MODEL_FORMAT', '模型未返回三条回复', 502);
  const replies = value.replies.map((r, i) => ({ style: ['温和', '直接', '轻松'][i], text: bounded(typeof r === 'string' ? r : r.text, 1000) }));
  requireValue(replies.every(r => r.text.trim()), 'MODEL_FORMAT', '回复不能为空', 502);
  requireValue(Array.isArray(value.messages) && value.messages.length <= 300, 'MODEL_FORMAT', '缺少截图文字依据', 502);
  const messages = value.messages.map(m => {
    requireValue(['self', 'other', 'unknown'].includes(m.speaker) && Number.isInteger(m.image) && m.image >= 1 && m.image <= input.imageDigests.length, 'MODEL_FORMAT', '截图消息归属无效', 502);
    return { speaker: m.speaker, text: bounded(m.text, 5000), image: m.image };
  });
  return { replies, messages, uncertainty: strings(value.uncertainty || []) };
}

export class TextModels {
  constructor(cfg, fetcher = fetch) { this.cfg = cfg; this.fetcher = fetcher; this.prompts = new Map(); }
  async init() {
    for (const [kind, file] of Object.entries({ advisor: 'reply', rewrite: 'rewrite' })) {
      const text = await fs.readFile(new URL('../prompts/' + file + '.md', import.meta.url), 'utf8');
      this.prompts.set(kind, { text, revision: digest(text).slice(0, 12) });
    }
  }
  async run(kind, input, images, signal) {
    requireValue(this.cfg.textReady, 'MODEL_NOT_READY', '云端文字模型尚未配置', 503);
    const visual = kind === 'advisor';
    requireValue(!visual || (images?.length && this.cfg.visionReady), 'VISION_NOT_READY', '军师图文模型尚未配置', 503);
    const prompt = this.prompts.get(kind), model = visual ? this.cfg.visionModel : this.cfg.textModel;
    const user = visual ? [{ type: 'text', text: JSON.stringify(input) }, ...images.map(url => ({ type: 'image_url', image_url: { url } }))] : JSON.stringify(input);
    let response;
    try {
      response = await this.fetcher(this.cfg.textBase.replace(/\/$/, '') + '/chat/completions', {
        method: 'POST', signal: AbortSignal.any([signal, AbortSignal.timeout(65000)]),
        headers: { Authorization: 'Bearer ' + this.cfg.modelKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, messages: [{ role: 'system', content: prompt.text + '\n请严格只输出 JSON 对象。' }, { role: 'user', content: user }],
          max_tokens: visual ? 6000 : 1600, temperature: visual ? 0.6 : 0.2, response_format: { type: 'json_object' }, enable_thinking: false }),
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
      provider: { model, usage: { ...output.usage, promptRevision: prompt.revision } } };
  }
}
