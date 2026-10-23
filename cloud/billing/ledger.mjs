import { uuid, canonical, digest, seal, unseal } from "../security.mjs";
import { requireValue } from "../http/errors.mjs";
import { billableCharacters } from "./metering.mjs";
export { billableCharacters };
export const isUuid = (v) =>
  typeof v === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    v,
  );

/** All balance transitions serialize on users.id, including concurrent devices. */
export class Ledger {
  constructor(db, cfg) {
    this.db = db;
    this.cfg = cfg;
  }
  async grant(c, user, key, plan, reason) {
    requireValue(
      plan.status === "published" && plan.limits,
      "INVALID_PLAN",
      "套餐尚未发布",
    );
    for (const n of Object.values(plan.limits))
      requireValue(
        Number.isSafeInteger(n) && n >= 0,
        "INVALID_PLAN",
        "额度须为非负整数",
      );
    await c.query(
      `INSERT INTO grants(id,user_id,grant_key,plan_snapshot,asr_limit,advisor_limit,sync_enabled,expires_at,reason)
      VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT(user_id,grant_key) DO NOTHING`,
      [
        uuid(),
        user,
        key,
        plan,
        plan.limits.asr_characters || 0,
        plan.limits.advisor_generations || 0,
        !!plan.features?.cloud_sync,
        plan.validDays
          ? new Date(Date.now() + plan.validDays * 86400000)
          : null,
        reason,
      ],
    );
  }
  async balance(user, c = this.db) {
    const { rows } = await c.query(
      `SELECT g.*,COALESCE(r.a,0)::bigint AS ra,COALESCE(r.b,0)::integer AS rb FROM grants g
      LEFT JOIN (SELECT a.grant_id,sum(a.asr_reserved) a,sum(a.advisor_reserved) b FROM job_allocations a JOIN jobs j ON j.id=a.job_id WHERE j.status IN('queued','running') GROUP BY a.grant_id) r ON r.grant_id=g.id
      WHERE g.user_id=$1 AND NOT g.revoked AND (g.expires_at IS NULL OR g.expires_at>now()) ORDER BY g.expires_at NULLS LAST,g.created_at`,
      [user],
    );
    const sum = (k) => rows.reduce((n, r) => n + Number(r[k]), 0);
    return {
      asr: {
        total: sum("asr_limit"),
        used: sum("asr_used"),
        reserved: sum("ra"),
        available: Math.max(0, sum("asr_limit") - sum("asr_used") - sum("ra")),
      },
      advisor: {
        total: sum("advisor_limit"),
        used: sum("advisor_used"),
        reserved: sum("rb"),
        available: Math.max(
          0,
          sum("advisor_limit") - sum("advisor_used") - sum("rb"),
        ),
      },
      cloudSync: rows.some((g) => g.sync_enabled),
      syncMaxBytes: Math.max(
        0,
        ...rows
          .filter((g) => g.sync_enabled)
          .map((g) => Number(g.sync_max_bytes)),
      ),
      grants: rows.map((g) => ({
        id: g.id,
        name: g.plan_snapshot.name,
        expiresAt: g.expires_at,
      })),
      _rows: rows,
    };
  }
  publicBalance(b) {
    const { _rows, ...publicValue } = b;
    return publicValue;
  }
  async reserve(
    user,
    requestId,
    kind,
    input,
    { hasImages = false, sourceJob = null } = {},
  ) {
    requireValue(isUuid(requestId), "INVALID_REQUEST", "请求标识无效");
    const payloadHash = digest(
      canonical({ kind, input, hasImages, sourceJob }),
    );
    return this.db.transaction(async (c) => {
      const account = await this.db.accountLock(c, user);
      requireValue(
        account?.status === "active",
        "ACCOUNT_DISABLED",
        "账号不可用",
        403,
      );
      const old = (
        await c.query(
          "SELECT * FROM jobs WHERE user_id=$1 AND request_id=$2 FOR UPDATE",
          [user, requestId],
        )
      ).rows[0];
      if (old) {
        requireValue(
          old.payload_hash === payloadHash,
          "REQUEST_CONFLICT",
          "同一请求标识不能用于不同内容",
          409,
        );
        if (["queued", "running", "succeeded"].includes(old.status))
          return { job: old, created: false };
        requireValue(
          old.attempts < 3 && new Date(old.expires_at) > new Date(),
          "RETRY_LIMIT",
          "此任务已结束，请重新发起",
          409,
        );
      }
      const active = Number(
        (
          await c.query(
            "SELECT count(*) n FROM jobs WHERE user_id=$1 AND status IN('queued','running')",
            [user],
          )
        ).rows[0].n,
      );
      requireValue(
        active < this.cfg.maxJobs,
        "BUSY",
        "已有任务进行中，请稍后重试",
        429,
      );
      if (['rewrite', 'advisor'].includes(kind)) {
        // Global capacity must be reserved atomically across different accounts.
        // In-flight advisor pictures remain in RAM, so registration count cannot
        // be allowed to turn into an unbounded image queue.
        await c.query('SELECT pg_advisory_xact_lock(42390026)');
        const pending = Number((await c.query("SELECT count(*) n FROM jobs WHERE kind IN('rewrite','advisor') AND status IN('queued','running')")).rows[0].n);
        requireValue(pending < this.cfg.maxTextQueue, 'SERVICE_BUSY', '当前任务较多，请稍后重试，本次未扣量', 429);
      }
      if (kind === "rewrite") {
        requireValue(
          isUuid(sourceJob),
          "SOURCE_REQUIRED",
          "请先完成一次语音转写",
        );
        const source = (
          await c.query(
            "SELECT id FROM jobs WHERE id=$1 AND user_id=$2 AND kind='asr' AND status='succeeded' AND expires_at>now()",
            [sourceJob, user],
          )
        ).rows[0];
        requireValue(source, "SOURCE_REQUIRED", "原语音任务不存在或已过期");
        const n = Number(
          (
            await c.query(
              "SELECT count(*) n FROM jobs WHERE user_id=$1 AND source_job=$2 AND kind='rewrite' AND status IN('succeeded','queued','running')",
              [user, sourceJob],
            )
          ).rows[0].n,
        );
        requireValue(
          n < 3,
          "REWRITE_LIMIT",
          "本段语音已整理三次，可直接编辑原文",
          429,
        );
      }
      if (kind === "memory") {
        const n = Number(
          (
            await c.query(
              "SELECT count(*) n FROM jobs WHERE user_id=$1 AND kind='memory' AND created_at>now()-interval '1 day'",
              [user],
            )
          ).rows[0].n,
        );
        requireValue(
          n < 6,
          "LEARNING_LIMIT",
          "今天的整理预算已用完，新增历史会留待下次",
          429,
        );
      }
      const balance = await this.balance(user, c),
        asr =
          kind === "asr"
            ? Math.min(this.cfg.maxAsrCharacters, balance.asr.available)
            : 0,
        advisor = kind === "advisor" ? 1 : 0;
      requireValue(
        kind !== "asr" || asr > 0,
        "ASR_EXHAUSTED",
        "转写字符已用完",
        402,
      );
      requireValue(
        !advisor || balance.advisor.available >= 1,
        "ADVISOR_EXHAUSTED",
        "军师次数已用完",
        402,
      );
      const id = old?.id || uuid(),
        encrypted = seal(this.cfg.masterKey, input, id);
      if (old) {
        await c.query("DELETE FROM job_allocations WHERE job_id=$1", [id]);
        await c.query(
          "UPDATE jobs SET status=$2,reserved_asr=$3,reserved_advisor=$4,input_cipher=$5,result_cipher=NULL,error_code=NULL,error_message=NULL,started_at=NULL,finished_at=NULL,attempts=attempts+1 WHERE id=$1",
          [id, kind === "asr" ? "running" : "queued", asr, advisor, encrypted],
        );
      } else
        await c.query(
          `INSERT INTO jobs(id,user_id,kind,request_id,payload_hash,status,reserved_asr,reserved_advisor,input_cipher,has_images,source_job) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
          [
            id,
            user,
            kind,
            requestId,
            payloadHash,
            kind === "asr" ? "running" : "queued",
            asr,
            advisor,
            encrypted,
            hasImages,
            sourceJob,
          ],
        );
      let ra = asr,
        rb = advisor;
      for (const g of balance._rows) {
        const a = Math.min(
            ra,
            Number(g.asr_limit) - Number(g.asr_used) - Number(g.ra),
          ),
          b = Math.min(
            rb,
            Number(g.advisor_limit) - Number(g.advisor_used) - Number(g.rb),
          );
        if (a || b)
          await c.query(
            "INSERT INTO job_allocations(job_id,grant_id,asr_reserved,advisor_reserved) VALUES($1,$2,$3,$4)",
            [id, g.id, a, b],
          );
        ra -= a;
        rb -= b;
        if (!ra && !rb) break;
      }
      requireValue(!ra && !rb, "LEDGER_CONFLICT", "额度预留失败，请重试", 409);
      return {
        job: (await c.query("SELECT * FROM jobs WHERE id=$1", [id])).rows[0],
        created: true,
      };
    });
  }
  async finish(id, outcome, result = null, provider = {}, error = {}) {
    return this.db.transaction(async (c) => {
      let job = (await c.query("SELECT user_id FROM jobs WHERE id=$1", [id]))
        .rows[0];
      if (!job) return null;
      const account = await this.db.accountLock(c, job.user_id);
      if (account?.status !== "active") { outcome = "cancelled"; result = null; }
      job = (await c.query("SELECT * FROM jobs WHERE id=$1 FOR UPDATE", [id]))
        .rows[0];
      if (!["queued", "running"].includes(job.status)) return job;
      const a =
          outcome === "succeeded" && job.kind === "asr"
            ? billableCharacters(result.text)
            : 0,
        b = outcome === "succeeded" && job.kind === "advisor" ? 1 : 0;
      requireValue(
        a <= job.reserved_asr && b <= job.reserved_advisor,
        "LEDGER_CONFLICT",
        "结算超出预留",
        500,
      );
      let ra = a,
        rb = b;
      const allocations = (
        await c.query(
          "SELECT * FROM job_allocations WHERE job_id=$1 ORDER BY grant_id",
          [id],
        )
      ).rows;
      for (const al of allocations) {
        const takeA = Math.min(ra, al.asr_reserved),
          takeB = Math.min(rb, al.advisor_reserved);
        await c.query(
          "UPDATE grants SET asr_used=asr_used+$2,advisor_used=advisor_used+$3 WHERE id=$1",
          [al.grant_id, takeA, takeB],
        );
        await c.query(
          "UPDATE job_allocations SET asr_consumed=$3,advisor_consumed=$4 WHERE job_id=$1 AND grant_id=$2",
          [id, al.grant_id, takeA, takeB],
        );
        ra -= takeA;
        rb -= takeB;
      }
      requireValue(!ra && !rb, "LEDGER_CONFLICT", "账本分配异常", 500);
      return (
        await c.query(
          `UPDATE jobs SET status=$2,consumed_asr=$3,consumed_advisor=$4,result_cipher=$5,input_cipher=NULL,error_code=$6,error_message=$7,provider_model=$8,provider_usage=$9,finished_at=now() WHERE id=$1 RETURNING *`,
          [
            id,
            outcome,
            a,
            b,
            result ? seal(this.cfg.masterKey, result, id) : null,
            error.code || null,
            error.message || null,
            provider.model || null,
            provider.usage || {},
          ],
        )
      ).rows[0];
    });
  }
  view(job) {
    const value = {
      id: job.id,
      requestId: job.request_id,
      kind: job.kind,
      status: job.status,
      consumed: { asr: job.consumed_asr, advisor: job.consumed_advisor },
      createdAt: job.created_at,
    };
    if (job.result_cipher)
      value.result = JSON.parse(
        unseal(this.cfg.masterKey, job.result_cipher, job.id),
      );
    else if (job.status === "succeeded")
      value.error = {
        code: "RESULT_EXPIRED",
        message: "结果恢复期为24小时，请查看本机历史",
      };
    if (job.error_code)
      value.error = { code: job.error_code, message: job.error_message };
    return value;
  }
  async recover() {
    const jobs = (
      await this.db.query(
        "SELECT id FROM jobs WHERE status IN('queued','running')",
      )
    ).rows;
    for (const j of jobs)
      await this.finish(
        j.id,
        "failed",
        null,
        {},
        {
          code: "SERVICE_RESTARTED",
          message: "服务重启，预留已释放，可重试原任务",
        },
      );
  }
}
