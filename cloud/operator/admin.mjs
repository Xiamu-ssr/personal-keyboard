import {
  uuid,
  randomToken,
  digest,
  hash,
  passwordMatches,
} from "../security.mjs";
import { isUuid } from "../billing/ledger.mjs";
import { requireValue } from "../http/errors.mjs";
export function validatePlan(p) {
  requireValue(
    p &&
      /^[a-z][a-z0-9-]{2,60}$/.test(p.id) &&
      typeof p.name === "string" &&
      p.name.length <= 60 &&
      Number.isSafeInteger(p.version) &&
      p.version > 0,
    "INVALID_PLAN",
    "套餐标识、名称或版本不正确",
  );
  requireValue(
    ["once_per_account", "verified_purchase"].includes(p.grantMode),
    "INVALID_PLAN",
    "发放模式不正确",
  );
  for (const k of ["asr_characters", "advisor_generations"])
    requireValue(
      Number.isSafeInteger(p.limits?.[k]) &&
        p.limits[k] >= 0 &&
        p.limits[k] <= 100000000,
      "INVALID_PLAN",
      "额度须为整数",
    );
  requireValue(
    Number.isSafeInteger(p.price?.amountMinor) &&
      p.price.amountMinor >= 0 &&
      p.price.currency === "CNY",
    "INVALID_PLAN",
    "价格必须为人民币分",
  );
  requireValue(
    !p.validDays ||
      (Number.isSafeInteger(p.validDays) &&
        p.validDays > 0 &&
        p.validDays <= 3650),
    "INVALID_PLAN",
    "有效天数不正确",
  );
  return { ...p, features: { cloud_sync: !!p.features?.cloud_sync } };
}
export class Admin {
  constructor(db, ledger, cfg, models) {
    Object.assign(this, { db, ledger, cfg, models });
  }
  async login(password, ip) {
    requireValue(
      this.cfg.adminPasswordHash,
      "ADMIN_NOT_READY",
      "后台尚未配置",
      503,
    );
    requireValue(
      typeof password === "string" && password.length <= 160,
      "INVALID_LOGIN",
      "登录失败",
      401,
    );
    const key = hash(this.cfg.tokenKey, ip);
    await this.db.transaction(async (c) => {
      await c.query("SELECT pg_advisory_xact_lock(42390023)");
      const r = (
        await c.query(
          "SELECT count(*) FILTER(WHERE ip_hash=$1) ip,count(*) total FROM admin_attempts WHERE created_at>now()-interval '15 minutes'",
          [key],
        )
      ).rows[0];
      requireValue(
        Number(r.ip) < 8 && Number(r.total) < 60,
        "RATE_LIMIT",
        "尝试过多，请15分钟后重试",
        429,
      );
      await c.query("INSERT INTO admin_attempts(ip_hash) VALUES($1)", [key]);
    });
    requireValue(
      passwordMatches(password, this.cfg.adminPasswordHash),
      "INVALID_LOGIN",
      "密码不正确",
      401,
    );
    const token = randomToken(),
      csrf = randomToken();
    await this.db.query(
      "INSERT INTO admin_sessions(id,token_hash,csrf_hash,expires_at) VALUES($1,$2,$3,now()+interval '8 hours')",
      [uuid(), digest(token), digest(csrf)],
    );
    return { token, csrf };
  }
  async authenticate(token, csrf, mutating) {
    requireValue(
      token && token.length < 100,
      "ADMIN_LOGIN_REQUIRED",
      "请登录后台",
      401,
    );
    const s = (
      await this.db.query(
        "SELECT * FROM admin_sessions WHERE token_hash=$1 AND expires_at>now()",
        [digest(token)],
      )
    ).rows[0];
    requireValue(s, "ADMIN_LOGIN_REQUIRED", "后台登录已失效", 401);
    requireValue(
      !mutating || (csrf && digest(csrf) === s.csrf_hash),
      "CSRF",
      "请刷新后台页面",
      403,
    );
    return s;
  }
  async overview() {
    const result = await this.db.query(
      "SELECT kind,status,count(*)::integer count,sum(consumed_asr)::integer asr,sum(consumed_advisor)::integer advisor FROM jobs WHERE created_at>now()-interval '1 day' GROUP BY kind,status",
    );
    const count = (
      await this.db.query(
        "SELECT count(*)::integer count FROM users WHERE status='active'",
      )
    ).rows[0].count;
    return {
      users: count,
      jobs: result.rows,
      readiness: {
        sms: this.cfg.smsReady,
        asr: this.cfg.asrReady,
        text: this.cfg.textReady,
        vision: this.cfg.visionReady,
      },
      prompts: [...this.models.prompts].map(([kind, p]) => ({
        kind,
        revision: p.revision,
      })),
    };
  }
  async users() {
    return (
      await this.db.query(
        `SELECT u.id,u.phone_hint,u.status,u.created_at,COALESCE(sum(g.asr_limit-g.asr_used) FILTER(WHERE NOT g.revoked AND (g.expires_at IS NULL OR g.expires_at>now())),0)::bigint asr_remaining,COALESCE(sum(g.advisor_limit-g.advisor_used) FILTER(WHERE NOT g.revoked AND (g.expires_at IS NULL OR g.expires_at>now())),0)::integer advisor_remaining FROM users u LEFT JOIN grants g ON g.user_id=u.id GROUP BY u.id ORDER BY u.created_at DESC LIMIT 200`,
      )
    ).rows;
  }
  async jobs() {
    return (
      await this.db.query(
        "SELECT j.id,u.phone_hint,j.kind,j.status,j.consumed_asr,j.consumed_advisor,j.error_code,j.provider_model,j.provider_usage,j.created_at FROM jobs j JOIN users u ON u.id=j.user_id ORDER BY j.created_at DESC LIMIT 150",
      )
    ).rows;
  }
  async plans() {
    return (
      await this.db.query(
        "SELECT id,version,status,definition FROM plans ORDER BY created_at DESC",
      )
    ).rows;
  }
  async savePlan(actor, input) {
    const plan = validatePlan(input);
    return this.db.transaction(async (c) => {
      const old = (
        await c.query("SELECT * FROM plans WHERE id=$1 FOR UPDATE", [plan.id])
      ).rows[0];
      requireValue(
        !old || old.status === "draft",
        "IMMUTABLE_PLAN",
        "已发布套餐不可修改，请创建新版本",
        409,
      );
      plan.status = "draft";
      await c.query(
        "INSERT INTO plans(id,version,status,definition) VALUES($1,$2,'draft',$3) ON CONFLICT(id) DO UPDATE SET version=EXCLUDED.version,definition=EXCLUDED.definition",
        [plan.id, plan.version, plan],
      );
      await this.db.audit(c, actor, "plan.save", plan.id);
      return plan;
    });
  }
  async publish(actor, id) {
    return this.db.transaction(async (c) => {
      const row = (
        await c.query("SELECT * FROM plans WHERE id=$1 FOR UPDATE", [id])
      ).rows[0];
      requireValue(row?.status === "draft", "INVALID_PLAN", "只能发布草稿套餐");
      const p = validatePlan(row.definition);
      p.status = "published";
      await c.query(
        "UPDATE plans SET status='published',definition=$2,published_at=now() WHERE id=$1",
        [id, p],
      );
      await this.db.audit(c, actor, "plan.publish", id);
      return p;
    });
  }
  async grant(actor, input) {
    requireValue(
      isUuid(input.userId) &&
        typeof input.reason === "string" &&
        input.reason.trim().length >= 4 &&
        input.reason.length <= 300,
      "INVALID_GRANT",
      "请选择用户并填写调整原因",
    );
    const p = validatePlan({
      id: "manual-adjustment",
      name: "运营调整",
      version: 1,
      grantMode: "verified_purchase",
      price: { amountMinor: 0, currency: "CNY" },
      ...input.plan,
      status: "published",
    });
    return this.db.transaction(async (c) => {
      requireValue(
        (await this.db.accountLock(c, input.userId))?.status === "active",
        "NOT_FOUND",
        "用户不可用",
        404,
      );
      const key = "manual:" + uuid();
      await this.ledger.grant(c, input.userId, key, p, input.reason);
      await this.db.audit(c, actor, "grant.create", input.userId, {
        key,
        reason: input.reason,
        limits: p.limits,
        sync: p.features.cloud_sync,
      });
      return { ok: true };
    });
  }
  async status(actor, id, status) {
    requireValue(
      isUuid(id) && ["active", "disabled"].includes(status),
      "INVALID_STATUS",
      "用户状态错误",
    );
    return this.db.transaction(async (c) => {
      await this.db.accountLock(c, id);
      await c.query(
        "UPDATE users SET status=$2 WHERE id=$1 AND status!='deleted'",
        [id, status],
      );
      if (status === "disabled")
        await c.query("UPDATE sessions SET revoked=true WHERE user_id=$1", [
          id,
        ]);
      await this.db.audit(c, actor, "user." + status, id);
      return { ok: true };
    });
  }
}
