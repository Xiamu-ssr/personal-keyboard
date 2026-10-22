import fs from 'node:fs/promises';
import { requireValue, ServiceError } from '../http/errors.mjs';

export const memoryOutputContract = {
  baseVersion: '与输入一致的整数',
  operations: [{ action: 'upsert 或 delete', kind: 'hotword / style / person / fact / relation', id: '已有 ID 或 new:临时名', personId: 'fact/relation 的所属人物 ID',
    value: 'hotword={text,variants}; style={scene,rules,examples}; person={name,aliases}; fact={key,value,scope}; relation={targetId,type}',
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

/** Count and generate from the SAME messages; schema is part of the system text. */
export class MemoryModel {
  constructor(cfg, fetcher = fetch) { this.cfg = cfg; this.fetcher = fetcher; }
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
    let response;
    try {
      response = await this.fetcher(this.cfg.tokenizerUrl, { method: 'POST', signal: AbortSignal.timeout(25000),
        headers: { Authorization: 'Bearer ' + this.cfg.modelKey, 'Content-Type': 'application/json', ...(this.cfg.workspace ? { 'X-DashScope-WorkSpace': this.cfg.workspace } : {}) },
        body: JSON.stringify({ model: request.model, input: { messages: request.messages }, parameters: { enable_thinking: false, response_format: request.response_format } }),
      });
    } catch { throw new ServiceError('TOKEN_COUNTER_UNAVAILABLE', '无法完成精确容量检查，历史待办保留', 503); }
    const data = await boundedJson(response), count = data.usage?.input_tokens;
    requireValue(Number.isSafeInteger(count) && count > 0, 'TOKEN_COUNTER_UNAVAILABLE', '供应商未返回有效 Token 计数，历史待办保留', 503);
    return count;
  }
  async fits(request) {
    const n = await this.count(request);
    return n <= this.cfg.memoryMaxInputTokens && n + request.max_tokens <= this.cfg.memoryContextTokens;
  }
  async generate(request) {
    let response;
    try {
      response = await this.fetcher(this.cfg.textBase.replace(/\/$/, '') + '/chat/completions', {
        method: 'POST', signal: AbortSignal.timeout(65000),
        headers: { Authorization: 'Bearer ' + this.cfg.modelKey, 'Content-Type': 'application/json' }, body: JSON.stringify(request),
      });
    } catch { throw new ServiceError('MEMORY_PROVIDER', '记忆更新连接失败，历史待办保留', 503); }
    const data = await boundedJson(response, 300000);
    requireValue(data.choices?.[0]?.finish_reason === 'stop', 'MODEL_TRUNCATED', '记忆更新没有完整返回，历史待办保留', 503);
    try { return JSON.parse(data.choices[0].message.content); }
    catch { throw new ServiceError('MEMORY_FORMAT', '记忆更新格式无效，历史待办保留', 503); }
  }
}

export async function historyRequest(model, snapshot, history, offset) {
  const chars = Array.from(JSON.stringify(history));
  let length = Math.min(16000, chars.length - offset);
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
