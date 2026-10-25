import { uuid, canonical, digest, seal, unseal } from '../security.mjs';
import { requireValue } from '../http/errors.mjs';
import { billableCharacters } from './metering.mjs';
import { CreditPricing, credits, milliCredits, normalizeUsage, priceUsage } from './credits.mjs';
export { billableCharacters };
export const isUuid = v => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
const payloadHash = (kind, input, hasImages, sourceJob) => digest(canonical({ kind, input, hasImages, sourceJob }));

/** One currency. Every reservation and settlement serializes on its account, across devices. */
export class Ledger {
  constructor(db, cfg) { this.db = db; this.cfg = cfg; this.pricing = new CreditPricing(db); }
  async grant(c, user, key, plan, reason) {
    requireValue(plan.status === 'published' && plan.limits, 'INVALID_PLAN', '套餐尚未发布');
    const amount = milliCredits(plan.limits.credits);
    await c.query(`INSERT INTO grants(id,user_id,grant_key,plan_snapshot,credit_limit,sync_enabled,expires_at,reason)
      VALUES($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT(user_id,grant_key) DO NOTHING`,
      [uuid(), user, key, plan, amount, !!plan.features?.cloud_sync,
        plan.validDays ? new Date(Date.now() + plan.validDays * 86400000) : null, reason]);
  }
  async balance(user, c = this.db) {
    const { rows } = await c.query(`SELECT g.*,COALESCE(r.amount,0)::bigint AS reserved FROM grants g
      LEFT JOIN (SELECT a.grant_id,sum(a.credit_reserved) amount FROM job_allocations a JOIN jobs j ON j.id=a.job_id WHERE j.status IN('queued','running') GROUP BY a.grant_id) r ON r.grant_id=g.id
      WHERE g.user_id=$1 AND NOT g.revoked AND (g.expires_at IS NULL OR g.expires_at>now()) ORDER BY g.expires_at NULLS LAST,g.created_at`, [user]);
    const sum = key => rows.reduce((n, r) => n + Number(r[key]), 0);
    const total = sum('credit_limit'), used = sum('credit_used'), reserved = sum('reserved');
    return {
      credits: { total: credits(total), used: credits(used), reserved: credits(reserved), available: credits(Math.max(0, total - used - reserved)) },
      cloudSync: rows.some(g => g.sync_enabled),
      syncMaxBytes: Math.max(0, ...rows.filter(g => g.sync_enabled).map(g => Number(g.sync_max_bytes))),
      grants: rows.map(g => ({ id: g.id, name: g.plan_snapshot.name, expiresAt: g.expires_at })),
      _available: Math.max(0, total - used - reserved), _rows: rows,
    };
  }
  publicBalance(balance) { const { _rows, _available, ...value } = balance; return value; }
  async existing(user, requestId, kind, input, { hasImages = false, sourceJob = null } = {}, c = this.db) {
    requireValue(isUuid(requestId), 'INVALID_REQUEST', '请求标识无效');
    const job = (await c.query('SELECT * FROM jobs WHERE user_id=$1 AND request_id=$2', [user, requestId])).rows[0];
    if (!job) return null;
    requireValue(job.payload_hash === payloadHash(kind, input, hasImages, sourceJob), 'REQUEST_CONFLICT', '同一请求标识不能用于不同内容', 409);
    return ['queued', 'running', 'succeeded'].includes(job.status) ? job : null;
  }
  async reserve(user, requestId, kind, input, { hasImages = false, sourceJob = null, quote, execution } = {}) {
    requireValue(isUuid(requestId), 'INVALID_REQUEST', '请求标识无效');
    requireValue(['asr', 'rewrite', 'advisor', 'translation'].includes(kind), 'INVALID_KIND', '未知模型任务');
    const fingerprint = payloadHash(kind, input, hasImages, sourceJob);
    return this.db.transaction(async c => {
      const account = await this.db.accountLock(c, user);
      requireValue(account?.status === 'active', 'ACCOUNT_DISABLED', '账号不可用', 403);
      const old = (await c.query('SELECT * FROM jobs WHERE user_id=$1 AND request_id=$2 FOR UPDATE', [user, requestId])).rows[0];
      if (old) {
        requireValue(old.payload_hash === fingerprint, 'REQUEST_CONFLICT', '同一请求标识不能用于不同内容', 409);
        if (['queued', 'running', 'succeeded'].includes(old.status)) return { job: old, created: false };
        requireValue(old.attempts < 3 && new Date(old.expires_at) > new Date(), 'RETRY_LIMIT', '此任务已结束，请重新发起', 409);
      }
      const active = Number((await c.query("SELECT count(*) n FROM jobs WHERE user_id=$1 AND status IN('queued','running')", [user])).rows[0].n);
      requireValue(active < this.cfg.maxJobs, 'BUSY', '已有任务进行中，请稍后重试', 429);
      if (kind !== 'asr') {
        await c.query('SELECT pg_advisory_xact_lock(42390026)');
        const pending = Number((await c.query("SELECT count(*) n FROM jobs WHERE kind IN('rewrite','advisor','translation') AND status IN('queued','running')")).rows[0].n);
        requireValue(pending < this.cfg.maxTextQueue, 'SERVICE_BUSY', '当前任务较多，本次未扣分', 429);
      }
      if (kind === 'rewrite') {
        requireValue(isUuid(sourceJob), 'SOURCE_REQUIRED', '请先完成一次语音转写');
        const source = (await c.query("SELECT id FROM jobs WHERE id=$1 AND user_id=$2 AND kind='asr' AND status='succeeded' AND expires_at>now()", [sourceJob, user])).rows[0];
        requireValue(source, 'SOURCE_REQUIRED', '原语音任务不存在或已过期');
        const count = Number((await c.query("SELECT count(*) n FROM jobs WHERE user_id=$1 AND source_job=$2 AND kind='rewrite' AND status IN('succeeded','queued','running')", [user, sourceJob])).rows[0].n);
        requireValue(count < 3, 'REWRITE_LIMIT', '本段语音已整理三次，可直接编辑原文', 429);
      }
      const balance = await this.balance(user, c);
      requireValue(quote, 'PRICE_REQUIRED', '任务缺少积分报价', 503);
      const snapshot = await this.pricing.quote(quote, balance._available, c);
      const id = old?.id || uuid(), encrypted = seal(this.cfg.masterKey, execution || input, id);
      if (old) {
        await c.query('DELETE FROM job_allocations WHERE job_id=$1', [id]);
        await c.query(`UPDATE jobs SET status=$2,reserved_credits=$3,billing_snapshot=$4,input_cipher=$5,result_cipher=NULL,error_code=NULL,error_message=NULL,started_at=NULL,finished_at=NULL,attempts=attempts+1 WHERE id=$1`,
          [id, kind === 'asr' ? 'running' : 'queued', snapshot.reserved, snapshot, encrypted]);
      } else {
        await c.query(`INSERT INTO jobs(id,user_id,kind,request_id,payload_hash,status,reserved_credits,billing_snapshot,input_cipher,has_images,source_job) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
          [id, user, kind, requestId, fingerprint, kind === 'asr' ? 'running' : 'queued', snapshot.reserved, snapshot, encrypted, hasImages, sourceJob]);
      }
      let remaining = snapshot.reserved;
      for (const grant of balance._rows) {
        const take = Math.min(remaining, Number(grant.credit_limit) - Number(grant.credit_used) - Number(grant.reserved));
        if (take > 0) await c.query('INSERT INTO job_allocations(job_id,grant_id,credit_reserved) VALUES($1,$2,$3)', [id, grant.id, take]);
        remaining -= take;
        if (!remaining) break;
      }
      requireValue(remaining === 0, 'LEDGER_CONFLICT', '积分预留失败，请重试', 409);
      return { job: (await c.query('SELECT * FROM jobs WHERE id=$1', [id])).rows[0], created: true };
    });
  }
  async finish(id, outcome, result = null, provider = {}, error = {}) {
    return this.db.transaction(async c => {
      let job = (await c.query('SELECT user_id FROM jobs WHERE id=$1', [id])).rows[0];
      if (!job) return null;
      const account = await this.db.accountLock(c, job.user_id);
      if (account?.status !== 'active') { outcome = 'cancelled'; result = null; }
      job = (await c.query('SELECT * FROM jobs WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!['queued', 'running'].includes(job.status)) return job;
      let amount = 0, cost = 0;
      if (outcome === 'succeeded') {
        const usage = normalizeUsage(provider.usage);
        const matched = provider.model === job.billing_snapshot.model;
        if (usage && matched) {
          const price = priceUsage(job.billing_snapshot, usage.input, usage.output);
          amount = Math.min(price.milliCredits, Number(job.reserved_credits)); cost = price.costMicroCny;
          if (amount !== price.milliCredits) await this.db.audit(c, 'billing', 'quote.exceeded', id, { actual: price.milliCredits, charged: amount });
        } else await this.db.audit(c, 'billing', 'usage.unavailable', id, { modelMatched: matched, charged: 0 });
      }
      let remaining = amount;
      const allocations = (await c.query('SELECT * FROM job_allocations WHERE job_id=$1 ORDER BY grant_id', [id])).rows;
      for (const allocation of allocations) {
        const take = Math.min(remaining, Number(allocation.credit_reserved));
        await c.query('UPDATE grants SET credit_used=credit_used+$2 WHERE id=$1', [allocation.grant_id, take]);
        await c.query('UPDATE job_allocations SET credit_consumed=$3 WHERE job_id=$1 AND grant_id=$2', [id, allocation.grant_id, take]);
        remaining -= take;
      }
      requireValue(remaining === 0, 'LEDGER_CONFLICT', '积分账本分配异常', 500);
      return (await c.query(`UPDATE jobs SET status=$2,consumed_credits=$3,cost_micro_cny=$4,result_cipher=$5,input_cipher=NULL,error_code=$6,error_message=$7,provider_model=$8,provider_usage=$9,finished_at=now() WHERE id=$1 RETURNING *`,
        [id, outcome, amount, cost, result ? seal(this.cfg.masterKey, result, id) : null, error.code || null, error.message || null, provider.model || null, provider.usage || {}])).rows[0];
    });
  }
  view(job) {
    const value = { id: job.id, requestId: job.request_id, kind: job.kind, status: job.status,
      consumed: { credits: credits(job.consumed_credits) }, reservedCredits: credits(job.reserved_credits),
      pricingVersion: job.billing_snapshot?.version || 0, createdAt: job.created_at };
    if (job.result_cipher) value.result = JSON.parse(unseal(this.cfg.masterKey, job.result_cipher, job.id));
    else if (job.status === 'succeeded') value.error = { code: 'RESULT_EXPIRED', message: '结果恢复期为24小时，请查看本机历史' };
    if (job.error_code) value.error = { code: job.error_code, message: job.error_message };
    return value;
  }
  async recover() {
    const jobs = (await this.db.query("SELECT id FROM jobs WHERE status IN('queued','running')")).rows;
    for (const job of jobs) await this.finish(job.id, 'failed', null, {}, { code: 'SERVICE_RESTARTED', message: '服务重启，预留已释放，可重试原任务' });
  }
}
