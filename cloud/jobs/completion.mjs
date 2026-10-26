/** A transient completion signal; PostgreSQL remains the durable source of job state. */
export class CompletionSignal {
  constructor() { this.waiters = new Map(); }

  subscribe(id, { milliseconds, signal } = {}) {
    let finish, timer;
    const promise = new Promise(resolve => {
      finish = () => {
        clearTimeout(timer); signal?.removeEventListener('abort', finish);
        const entries = this.waiters.get(id); entries?.delete(finish);
        if (!entries?.size) this.waiters.delete(id);
        resolve();
      };
      if (!this.waiters.has(id)) this.waiters.set(id, new Set());
      this.waiters.get(id).add(finish);
      timer = setTimeout(finish, milliseconds);
      if (signal?.aborted) finish(); else signal?.addEventListener('abort', finish, { once: true });
    });
    return { promise, close: finish };
  }

  publish(id) { for (const finish of [...(this.waiters.get(id) || [])]) finish(); }
  close() { for (const id of [...this.waiters.keys()]) this.publish(id); }
}
