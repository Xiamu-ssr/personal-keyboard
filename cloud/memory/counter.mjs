import { Worker } from 'node:worker_threads';
import { ServiceError, requireValue } from '../http/errors.mjs';

/** One bounded CPU worker keeps tokenization away from the ASR event loop. */
export class QwenCounter {
  constructor() { this.pending = new Map(); this.sequence = 0; }
  start() {
    if (this.worker) return;
    const worker = new Worker(new URL('./counter-worker.mjs', import.meta.url), {
      execArgv: [],
      resourceLimits: { maxOldGenerationSizeMb: 384 },
    });
    this.worker = worker;
    const fail = () => {
      if (this.worker !== worker) return;
      this.worker = null;
      for (const item of this.pending.values()) { clearTimeout(item.timer); item.reject(new ServiceError('TOKEN_COUNTER_UNAVAILABLE', '本地容量检查暂不可用，历史待办保留', 503)); }
      this.pending.clear();
    };
    worker.on('error', fail); worker.on('exit', fail);
    worker.on('message', ({ id, tokens, contentTokens, protocolTokens, revision, error }) => {
      const item = this.pending.get(id);
      if (!item) return;
      clearTimeout(item.timer); this.pending.delete(id);
      if (error) item.reject(new ServiceError('TOKEN_COUNTER_UNAVAILABLE', '请求不符合已验证的模型计数协议，历史待办保留', 503));
      else item.resolve({ tokens, contentTokens, protocolTokens, revision });
    });
    worker.unref();
  }
  count(request) {
    requireValue(this.pending.size < 8, 'TOKEN_COUNTER_BUSY', '容量检查繁忙，历史待办保留', 503);
    this.start();
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { void this.worker?.terminate(); }, 30_000);
      this.pending.set(id, { resolve, reject, timer });
      this.worker.postMessage({ id, request });
    });
  }
  async close() { if (this.worker) await this.worker.terminate(); }
}

export const memoryCounter = new QwenCounter();
