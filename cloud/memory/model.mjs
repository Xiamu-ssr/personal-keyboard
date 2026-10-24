import fs from 'node:fs/promises';
import { requireValue, ServiceError } from '../http/errors.mjs';
import { digest } from '../security.mjs';
import { memoryCounter } from './counter.mjs';

export const memoryOutputContract = {
  baseVersion: '与输入一致的整数',
  operations: [{ action: 'upsert 或 delete', kind: 'hotword / style / person / fact / relation', id: '已有 UUID；新增必须匹配 ^new:[A-Za-z0-9_-]{1,64}$，例如 new:word1，不能含中文', personId: '仅 fact/relation 填所属人物 UUID 或本批 new:person1，其他类型不填',
    value: 'hotword={text:结果稿里纠正后的标准词,variants:[填入稿里纠正前的错误写法]}; style={scene,rules,examples}; person={name,aliases,identities:[{platform,nickname,handle}]}; fact={key,value,scope}; relation={targetId,type}',
    sourceId: '本批历史 ID', sourceRevision: '本批历史修订', category: 'correction 或 observation', confidence: '0至1', reason: '简短理由' }],
};

async function boundedJson(response, limit = 32 * 1024 * 1024) {
  requireValue(response.ok, 'MEMORY_PROVIDER', '记忆服务暂不可用，历史待办保留', 503);
  let size = 0; const chunks = [];
  for await (const chunk of response.body) {
    size += chunk.length;
    requireValue(size <= limit, 'MEMORY_PROVIDER', '记忆服务响应过大，历史待办保留', 503);
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ServiceError('MEMORY_PROVIDER', '记忆服务响应无效，历史待办保留', 503); }
}

/** The pinned official tokenizer counts the SAME prompt, including its schema. */
export class MemoryModel {
  constructor(cfg, { fetcher = fetch, counter = memoryCounter } = {}) {
    Object.assign(this, { cfg, fetcher, counter }); this.counts = new WeakMap();
  }
  async init() { this.prompt = await fs.readFile(new URL('../prompts/learn.md', import.meta.url), 'utf8'); }
  request(memory, baseVersion, history) {
    return { model: this.cfg.textModel,
      messages: [
        { role: 'system', content: this.prompt + '\n只输出 JSON，结构约定：' + JSON.stringify(memoryOutputContract) },
        { role: 'user', content: JSON.stringify({ baseVersion, memory, history }) },
      ], max_tokens: this.cfg.memoryOutputTokens, temperature: 0.1,
      response_format: { type: 'json_object' }, enable_thinking: false };
  }
  async count(request) {
    requireValue(this.cfg.textReady, 'MODEL_NOT_READY', '记忆更新等待云端模型配置', 503);
    const signature = digest(JSON.stringify(request)), previous = this.counts.get(request);
    if (previous?.signature === signature) return previous.tokens;
    const counted = await this.counter.count(request), count = counted.tokens;
    requireValue(Number.isSafeInteger(count) && count > 0, 'TOKEN_COUNTER_UNAVAILABLE', '分词器未返回有效 Token 计数，历史待办保留', 503);
    this.counts.set(request, { signature, tokens: count });
    return count;
  }
  async fits(request) {
    const n = await this.count(request);
    return n <= this.cfg.memoryMaxInputTokens && n + request.max_tokens <= this.cfg.memoryContextTokens;
  }
  async generate(request) {
    const budget = await this.count(request);
    requireValue(budget <= this.cfg.memoryMaxInputTokens && budget + request.max_tokens <= this.cfg.memoryContextTokens,
      'MEMORY_CAPACITY', '完整记忆超出上下文，历史待办保留', 409);
    let response;
    try {
      response = await this.fetcher(this.cfg.textBase.replace(/\/$/, '') + '/chat/completions', {
        method: 'POST', signal: AbortSignal.timeout(65000),
        headers: { Authorization: 'Bearer ' + this.cfg.modelKey, 'Content-Type': 'application/json' }, body: JSON.stringify(request),
      });
    } catch { throw new ServiceError('MEMORY_PROVIDER', '记忆更新连接失败，历史待办保留', 503); }
    const data = await boundedJson(response, 300000);
    requireValue(Number.isSafeInteger(data.usage?.prompt_tokens) && data.usage.prompt_tokens <= budget,
      'TOKEN_COUNTER_MISMATCH', '供应商计数超过本地容量预算，更新已暂停，历史待办保留', 503);
    requireValue(data.choices?.[0]?.finish_reason === 'stop', 'MODEL_TRUNCATED', '记忆更新没有完整返回，历史待办保留', 503);
    try { return JSON.parse(data.choices[0].message.content); }
    catch { throw new ServiceError('MEMORY_FORMAT', '记忆更新格式无效，历史待办保留', 503); }
  }
}

export async function historyRequest(model, snapshot, history, offset) {
  const chars = Array.from(JSON.stringify(history));
  let length = chars.length - offset;
  requireValue(length > 0, 'MEMORY_HISTORY', '历史游标错误');
  while (true) {
    const piece = { id: history.id, revision: history.revision, type: history.type,
      conversationRef: history.conversationRef, start: offset, end: offset + length, total: chars.length,
      text: chars.slice(offset, offset + length).join('') };
    const request = model.request(snapshot.memory, snapshot.version, piece);
    if (await model.fits(request)) return { request, end: piece.end, total: chars.length };
    requireValue(length > 16, 'MEMORY_CAPACITY', '完整记忆无法装入模型上下文，更新已暂停；历史待办保留', 409);
    length = Math.max(16, Math.floor(length / 2));
  }
}

/** Batch whole revisions first. Only an individually oversized history is fragmented. */
export async function historyBatch(model, snapshot, rows) {
  let records = [], accepted = [];
  for (const row of rows) {
    const total = Array.from(JSON.stringify(row.history)).length;
    if (row.next_offset > 0) break;
    const candidate = [...records, row.history];
    const request = model.request(snapshot.memory, snapshot.version, candidate);
    if (!(await model.fits(request))) break;
    records = candidate;
    accepted.push({ history: row.history, offset: row.next_offset, end: total, total });
  }
  if (accepted.length) return { request: model.request(snapshot.memory, snapshot.version, records), accepted };
  const first = rows[0], part = await historyRequest(model, snapshot, first.history, first.next_offset);
  return { request: part.request, accepted: [{ history: first.history, offset: first.next_offset, end: part.end, total: part.total }] };
}
