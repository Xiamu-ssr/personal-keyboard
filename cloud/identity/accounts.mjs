import crypto from "node:crypto";
import {
  uuid,
  randomToken,
  hash,
  digest,
  equal,
  seal,
  unseal,
  signToken,
  verifyToken,
} from "../security.mjs";
import { requireValue, ServiceError } from "../http/errors.mjs";
import { isUuid } from "../billing/ledger.mjs";
export function normalizePhone(value) {
  const phone = String(value || "")
    .replace(/[\s-]/g, "")
    .replace(/^(?:\+86|0086)/, "");
  requireValue(
    /^1[3-9]\d{9}$/.test(phone),
    "INVALID_PHONE",
    "请输入中国大陆手机号",
  );
  return "+86" + phone;
}
export class Accounts {
  constructor(db, ledger, cfg, sms) {
    Object.assign(this, { db, ledger, cfg, sms });
  }
  async challenge(value, ip) {
    requireValue(
      this.cfg.smsReady,
      "SMS_NOT_READY",
      "短信登录尚待企业资质、签名和模板配置；本地输入仍可使用",
      503,
    );
    const phone = normalizePhone(value),
      id = uuid(),
      code = crypto.randomInt(100000, 1000000).toString(),
      phoneHash = hash(this.cfg.tokenKey, phone),
      ipHash = hash(this.cfg.tokenKey, ip);
    await this.db.transaction(async (c) => {
      await c.query("SELECT pg_advisory_xact_lock(42390022)");
      const limits = (
        await c.query(
          `SELECT count(*) FILTER(WHERE phone_hash=$1 AND created_at>now()-interval '1 minute') cooldown,count(*) FILTER(WHERE phone_hash=$1) phone,count(*) FILTER(WHERE ip_hash=$2) ip,count(*) total FROM challenges WHERE created_at>now()-interval '1 day'`,
          [phoneHash, ipHash],
        )
      ).rows[0];
      requireValue(
        !Number(limits.cooldown) &&
          Number(limits.phone) < 8 &&
          Number(limits.ip) < 30 &&
          Number(limits.total) < 20,
        "SMS_RATE_LIMIT",
        "请求较多，请稍后再获取验证码",
        429,
      );
      await c.query(
        "INSERT INTO challenges(id,phone_hash,phone_cipher,phone_hint,ip_hash,code_hash,status,expires_at) VALUES($1,$2,$3,$4,$5,$6,'sending',now()+interval '5 minutes')",
        [
          id,
          phoneHash,
          seal(this.cfg.masterKey, phone, "phone"),
          phone.slice(3, 6) + "****" + phone.slice(-4),
          ipHash,
          hash(this.cfg.tokenKey, id + ":" + code),
        ],
      );
    });
    try {
      await this.sms.send(phone, code);
      await this.db.query("UPDATE challenges SET status='sent' WHERE id=$1", [
        id,
      ]);
    } catch (e) {
      await this.db.query("UPDATE challenges SET status='failed' WHERE id=$1", [
        id,
      ]);
      throw e;
    }
    return { challengeId: id, expiresIn: 300, retryAfter: 60 };
  }
  async verify(id, code) {
    requireValue(
      isUuid(id) && /^\d{6}$/.test(String(code)),
      "INVALID_CODE",
      "请输入六位验证码",
    );
    const out = await this.db.transaction(async (c) => {
      const challenge = (
        await c.query("SELECT * FROM challenges WHERE id=$1 FOR UPDATE", [id])
      ).rows[0];
      if (
        !challenge ||
        challenge.status !== "sent" ||
        challenge.attempts >= 5 ||
        new Date(challenge.expires_at) <= new Date()
      )
        return { error: "验证码已失效，请重新获取" };
      await c.query("UPDATE challenges SET attempts=attempts+1 WHERE id=$1", [
        id,
      ]);
      if (!equal(challenge.code_hash, hash(this.cfg.tokenKey, id + ":" + code)))
        return { error: "验证码不正确" };
      await c.query("UPDATE challenges SET status='consumed' WHERE id=$1", [
        id,
      ]);
      const user = (
        await c.query(
          `INSERT INTO users(id,phone_hash,phone_cipher,phone_hint) VALUES($1,$2,$3,$4) ON CONFLICT(phone_hash) DO UPDATE SET phone_hash=EXCLUDED.phone_hash RETURNING *`,
          [
            uuid(),
            challenge.phone_hash,
            challenge.phone_cipher,
            challenge.phone_hint,
          ],
        )
      ).rows[0];
      if (user.status !== "active") return { error: "账号不可用" };
      await this.db.accountLock(c, user.id);
      const plan = (
        await c.query(
          "SELECT definition FROM plans WHERE id=$1 AND status='published'",
          [this.cfg.plans.activeWelcomePlan],
        )
      ).rows[0]?.definition;
      requireValue(plan, "WELCOME_NOT_READY", "欢迎权益尚未配置", 503);
      await this.ledger.grant(c, user.id, "welcome", plan, "new-account");
      const session = await this.newSession(c, user.id, uuid());
      await this.db.audit(c, "user:" + user.id, "session.login", session.id);
      return this.tokens(user, session);
    });
    if (out.error) throw new ServiceError("INVALID_CODE", out.error, 401);
    return out;
  }
  async newSession(c, user, family, verifiedAt = new Date()) {
    const token = randomToken(),
      id = uuid(),
      expires = new Date(Date.now() + this.cfg.refreshMs);
    await c.query(
      "INSERT INTO sessions(id,user_id,family_id,token_hash,expires_at,verified_at) VALUES($1,$2,$3,$4,$5,$6)",
      [id, user, family, digest(token), expires, verifiedAt],
    );
    return { id, token, expires };
  }
  tokens(user, s) {
    const now = Date.now();
    return {
      user: { id: user.id, phoneHint: user.phone_hint },
      accessToken: signToken(this.cfg.tokenKey, {
        aud: "keyboard",
        sub: user.id,
        sid: s.id,
        iat: now,
        exp: now + this.cfg.accessMs,
      }),
      accessExpiresAt: now + this.cfg.accessMs,
      refreshToken: s.token,
      refreshExpiresAt: s.expires,
    };
  }
  async refresh(token) {
    requireValue(
      typeof token === "string" && token.length >= 40 && token.length < 100,
      "UNAUTHENTICATED",
      "请重新登录",
      401,
    );
    const result = await this.db.transaction(async (c) => {
      let s = (
        await c.query("SELECT * FROM sessions WHERE token_hash=$1", [
          digest(token),
        ])
      ).rows[0];
      if (!s) return null;
      const user = await this.db.accountLock(c, s.user_id);
      s = (
        await c.query("SELECT * FROM sessions WHERE id=$1 FOR UPDATE", [s.id])
      ).rows[0];
      if (s.revoked || s.replaced_by) {
        await c.query("UPDATE sessions SET revoked=true WHERE family_id=$1", [
          s.family_id,
        ]);
        return null;
      }
      if (user.status !== "active" || new Date(s.expires_at) <= new Date())
        return null;
      const next = await this.newSession(
        c,
        user.id,
        s.family_id,
        s.verified_at,
      );
      await c.query(
        "UPDATE sessions SET replaced_by=$2,revoked=true WHERE id=$1",
        [s.id, next.id],
      );
      return this.tokens(user, next);
    });
    requireValue(result, "UNAUTHENTICATED", "登录已过期，请重新登录", 401);
    return result;
  }
  async authenticate(token) {
    const claim = verifyToken(this.cfg.tokenKey, token, "keyboard");
    requireValue(
      isUuid(claim.sub) && isUuid(claim.sid),
      "UNAUTHENTICATED",
      "请重新登录",
      401,
    );
    const row = (
      await this.db.query(
        `SELECT u.id,u.phone_hint,s.id session_id,s.verified_at FROM users u JOIN sessions s ON s.user_id=u.id WHERE u.id=$1 AND s.id=$2 AND u.status='active' AND NOT s.revoked AND s.expires_at>now()`,
        [claim.sub, claim.sid],
      )
    ).rows[0];
    requireValue(row, "UNAUTHENTICATED", "请重新登录", 401);
    return row;
  }
  async logout(user) {
    await this.db.query("UPDATE sessions SET revoked=true WHERE id=$1", [
      user.session_id,
    ]);
    return { ok: true };
  }
  async delete(user, erasePersonalData) {
    await this.db.transaction(async (c) => {
      await this.db.accountLock(c, user.id);
      await c.query(
        "UPDATE users SET status='deleted',phone_cipher='',phone_hint='已注销' WHERE id=$1",
        [user.id],
      );
      await c.query("UPDATE sessions SET revoked=true WHERE user_id=$1", [
        user.id,
      ]);
      await c.query(
        "UPDATE jobs SET input_cipher=NULL,result_cipher=NULL WHERE user_id=$1",
        [user.id],
      );
      await c.query("DELETE FROM sync_keys WHERE user_id=$1", [user.id]);
      await erasePersonalData(c);
      await this.db.audit(c, "user:" + user.id, "account.delete", user.id);
    });
    return { ok: true };
  }
}
