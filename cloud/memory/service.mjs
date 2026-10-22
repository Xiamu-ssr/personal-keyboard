import { emptyMemory, changeMemory, resolveCandidate, memoryDelta, retractRevision } from './domain.mjs';
import { historyEvent } from './history.mjs';
import { historyRequest } from './model.mjs';
import { canonical, digest, seal, unseal, uuid } from '../security.mjs';
import { isUuid } from '../billing/ledger.mjs';
import { requireValue } from '../http/errors.mjs';

export class PersonalMemory {
  constructor(db, model, cfg) { Object.assign(this, { db, model, cfg }); this.running = false; this.stopped = false; }
  encode(user, value) { return seal(this.cfg.masterKey, JSON.stringify(value), 'memory:' + user); }
  decode(user, value) { return JSON.parse(unseal(this.cfg.masterKey, value, 'memory:' + user)); }
  async snapshot(user, c = this.db, lock = false) {
    const row = (await c.query('SELECT version,body_cipher FROM personal_memory WHERE user_id=$1' + (lock ? ' FOR UPDATE' : ''), [user])).rows[0];
    return row ? { version: Number(row.version), memory: this.decode(user, row.body_cipher) } : { version: 0, memory: emptyMemory() };
  }
  async sync(user, after = 0) {
    requireValue(Number.isSafeInteger(after) && after >= 0, 'MEMORY_VERSION', '记忆版本无效');
    // Keep snapshot and deltas consistent even while another request commits.
    return this.db.transaction(async c => {
      await this.db.accountLock(c, user.id);
      const current = await this.snapshot(user.id, c);
      requireValue(after <= current.version, 'MEMORY_CONFLICT', '记忆缓存版本超前，请重新同步', 409);
      const status = (await c.query('SELECT state,error_code FROM memory_work WHERE user_id=$1', [user.id])).rows[0] || { state: 'idle', error_code: null };
      if (!after) return { ...current, reset: true, status };
      const rows = (await c.query('SELECT body_cipher FROM memory_changes WHERE user_id=$1 AND version>$2 ORDER BY version LIMIT 201', [user.id, after])).rows;
      if (rows.length > 200) return { ...current, reset: true, status };
      const changes = rows.flatMap(r => this.decode(user.id, r.body_cipher));
      return { version: current.version, reset: false, changes, candidates: current.memory.candidates, status };
    });
  }
  async ensureCapacity(memory, version) {
    const request = this.model.request(memory, version, { id: 'capacity-check', revision: 1, start: 0, end: 16, total: 16, text: '最小历史更新容量检查。' });
    requireValue(await this.model.fits(request), 'MEMORY_CAPACITY', '完整记忆无法装入模型上下文，更新已暂停；待办保留', 409);
  }
  async commit(c, user, before, memory) {
    const version = before.version + 1;
    await c.query('INSERT INTO personal_memory(user_id,version,body_cipher) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET version=excluded.version,body_cipher=excluded.body_cipher,updated_at=now()', [user, version, this.encode(user, memory)]);
    await c.query('INSERT INTO memory_changes(user_id,version,body_cipher) VALUES($1,$2,$3)', [user, version, this.encode(user, memoryDelta(before.memory, memory))]);
    return { version, memory, reset: true };
  }
  async mutate(user, body) {
    requireValue(isUuid(body.requestId) && Number.isSafeInteger(body.baseVersion) && body.baseVersion >= 0, 'MEMORY_FORMAT', '缺少记忆请求标识或版本');
    const hash = digest(canonical(body));
    const receipt = (await this.db.query('SELECT payload_hash FROM memory_receipts WHERE user_id=$1 AND request_id=$2', [user.id, body.requestId])).rows[0];
    if (receipt) { requireValue(receipt.payload_hash === hash, 'REQUEST_CONFLICT', '请求标识已用于其他内容', 409); return this.sync(user); }
    const before = await this.snapshot(user.id);
    requireValue(before.version === body.baseVersion, 'MEMORY_CONFLICT', '个人记忆已更新，请同步后重试', 409);
    let changed;
    if (body.candidateId) {
      requireValue(isUuid(body.candidateId) && typeof body.accept === 'boolean', 'MEMORY_FORMAT', '候选确认格式错误');
      changed = { memory: resolveCandidate(before.memory, body.candidateId, body.accept), idMap: {} };
    } else changed = changeMemory(before.memory, body.operations, { manual: true });
    await this.ensureCapacity(changed.memory, before.version + 1);
    return this.db.transaction(async c => {
      const account = await this.db.accountLock(c, user.id);
      requireValue(account?.status === 'active', 'ACCOUNT_DISABLED', '账号不可用', 403);
      const previous = (await c.query('SELECT payload_hash FROM memory_receipts WHERE user_id=$1 AND request_id=$2', [user.id, body.requestId])).rows[0];
      if (previous) { requireValue(previous.payload_hash === hash, 'REQUEST_CONFLICT', '请求标识冲突', 409); return { ...(await this.snapshot(user.id, c)), reset: true }; }
      const latest = await this.snapshot(user.id, c, true);
      requireValue(latest.version === before.version, 'MEMORY_CONFLICT', '个人记忆已变化，请同步后重试', 409);
      const result = await this.commit(c, user.id, before, changed.memory);
      await c.query('INSERT INTO memory_receipts(user_id,request_id,payload_hash) VALUES($1,$2,$3)', [user.id, body.requestId, hash]);
      await c.query("UPDATE memory_work SET state='queued',attempts=0,retry_at=now(),error_code=NULL WHERE user_id=$1 AND (lease_until IS NULL OR lease_until<now())", [user.id]);
      return { ...result, idMap: changed.idMap };
    });
  }
  async ingest(user, raw) {
    requireValue(Array.isArray(raw.events) && raw.events.length > 0 && raw.events.length <= 24, 'HISTORY_FORMAT', '每批1至24条历史增量');
    const events = raw.events.map(historyEvent), seen = new Set();
    for (const e of events) { requireValue(!seen.has(e.id), 'HISTORY_FORMAT', '一批中历史标识不能重复'); seen.add(e.id); }
    await this.db.transaction(async c => {
      const account = await this.db.accountLock(c, user.id);
      requireValue(account?.status === 'active', 'ACCOUNT_DISABLED', '账号不可用', 403);
      const before = await this.snapshot(user.id, c, true); let updated = before.memory;
      for (const event of events) {
        const hash = digest(canonical(event));
        const old = (await c.query('SELECT revision,payload_hash FROM memory_history WHERE user_id=$1 AND history_id=$2 ORDER BY revision DESC LIMIT 1', [user.id, event.id])).rows[0];
        if (old && old.revision >= event.revision) {
          if (old.revision === event.revision) requireValue(old.payload_hash === hash, 'HISTORY_CONFLICT', '相同历史修订内容不同', 409);
          continue;
        }
        await c.query("UPDATE memory_history SET state='superseded',body_cipher=NULL WHERE user_id=$1 AND history_id=$2 AND state='queued'", [user.id, event.id]);
        await c.query('INSERT INTO memory_history(user_id,history_id,revision,payload_hash,body_cipher,state) VALUES($1,$2,$3,$4,$5,$6)', [user.id, event.id, event.revision, hash, event.deleted ? null : this.encode(user.id, event), event.deleted ? 'done' : 'queued']);
        updated = retractRevision(updated, event.id, event.revision);
      }
      if (canonical(updated) !== canonical(before.memory)) await this.commit(c, user.id, before, updated);
      await c.query("INSERT INTO memory_work(user_id) VALUES($1) ON CONFLICT(user_id) DO UPDATE SET retry_at=now(),attempts=0,state=CASE WHEN memory_work.lease_until>now() THEN memory_work.state ELSE 'queued' END", [user.id]);
    });
    void this.pump();
    return { accepted: events.map(e => ({ id: e.id, revision: e.revision })) };
  }
  async pump() {
    if (this.running || this.stopped) return;
    this.running = true; const token = uuid(); let row;
    try {
      row = (await this.db.query("UPDATE memory_work SET lease_token=$1,lease_until=now()+interval '10 minutes',state='running',attempts=attempts+1 WHERE user_id=(SELECT w.user_id FROM memory_work w JOIN users u ON u.id=w.user_id WHERE u.status='active' AND w.retry_at<=now() AND (w.lease_until IS NULL OR w.lease_until<now()) AND EXISTS(SELECT 1 FROM memory_history h WHERE h.user_id=w.user_id AND h.state='queued') ORDER BY w.retry_at FOR UPDATE OF w SKIP LOCKED LIMIT 1) RETURNING user_id", [token])).rows[0];
      if (row) await this.process(row.user_id, token);
    } catch (error) {
      if (row) await this.db.query("UPDATE memory_work SET lease_until=NULL,state=CASE WHEN $4 OR attempts>=3 THEN 'paused' ELSE 'waiting' END,error_code=$3,retry_at=CASE WHEN $4 OR attempts>=3 THEN 'infinity'::timestamptz ELSE now()+interval '5 minutes' END WHERE user_id=$1 AND lease_token=$2", [row.user_id, token, String(error.code || 'MEMORY_UNAVAILABLE').slice(0, 60), ['MEMORY_CAPACITY', 'TOKEN_COUNTER_MISMATCH'].includes(error.code)]).catch(() => {});
    } finally { this.running = false; }
  }
  async process(user, token) {
    const row = (await this.db.query("SELECT * FROM memory_history WHERE user_id=$1 AND state='queued' ORDER BY created_at,history_id LIMIT 1", [user])).rows[0];
    if (!row) return this.release(user, token);
    const history = this.decode(user, row.body_cipher), before = await this.snapshot(user);
    const part = await historyRequest(this.model, before, history, row.next_offset);
    const result = await this.model.generate(part.request);
    requireValue(result.baseVersion === before.version, 'MEMORY_CONFLICT', '模型返回版本不符', 409);
    const changed = changeMemory(before.memory, result.operations, { history, origin: { historyId: history.id, revision: history.revision } }).memory;
    await this.ensureCapacity(changed, before.version + 1);
    await this.db.transaction(async c => {
      const account = await this.db.accountLock(c, user);
      requireValue(account?.status === 'active', 'ACCOUNT_DISABLED', '账号不可用', 403);
      const lease = (await c.query('SELECT lease_token,lease_until FROM memory_work WHERE user_id=$1 FOR UPDATE', [user])).rows[0];
      requireValue(lease?.lease_token === token && new Date(lease.lease_until) > new Date(), 'MEMORY_CONFLICT', '记忆任务租约已变化', 409);
      const current = await this.snapshot(user, c, true);
      requireValue(current.version === before.version, 'MEMORY_CONFLICT', '主本已变化，本批将基于新主本重算', 409);
      const event = (await c.query('SELECT state,next_offset FROM memory_history WHERE user_id=$1 AND history_id=$2 AND revision=$3 FOR UPDATE', [user, history.id, history.revision])).rows[0];
      if (event?.state !== 'queued' || event.next_offset !== row.next_offset) return;
      if (canonical(current.memory) !== canonical(changed)) await this.commit(c, user, current, changed);
      await c.query("UPDATE memory_history SET next_offset=$4,state=CASE WHEN $5 THEN 'done' ELSE 'queued' END,body_cipher=CASE WHEN $5 THEN NULL ELSE body_cipher END WHERE user_id=$1 AND history_id=$2 AND revision=$3", [user, history.id, history.revision, part.end, part.end === part.total]);
    });
    await this.release(user, token);
  }
  release(user, token) {
    return this.db.query("UPDATE memory_work SET lease_until=NULL,attempts=0,state=CASE WHEN EXISTS(SELECT 1 FROM memory_history h WHERE h.user_id=$1 AND h.state='queued') THEN 'queued' ELSE 'idle' END,error_code=NULL,retry_at=now() WHERE user_id=$1 AND lease_token=$2", [user, token]);
  }
  async forTask(user, kind, input) {
    const { memory } = await this.snapshot(user);
    if (kind === 'rewrite') return { ...input, words: memory.hotwords.map(w => w.text), styles: memory.styles };
    const selected = memory.people.find(p => p.id === input.personId);
    const ids = new Set(selected ? [selected.id, ...selected.relations.map(r => r.targetId)] : []);
    const people = memory.people.filter(p => ids.has(p.id));
    return { ...input, people, styles: memory.styles }; // Advisor never receives the separate hotword list.
  }
  async remove(user, transaction) {
    // Account deletion holds the user lock and erases memory in the same transaction.
    for (const table of ['memory_receipts', 'memory_history', 'memory_changes', 'memory_work', 'personal_memory']) await transaction.query('DELETE FROM ' + table + ' WHERE user_id=$1', [user.id]);
  }
  async stop() { this.stopped = true; while (this.running) await new Promise(r => setTimeout(r, 50)); }
}
