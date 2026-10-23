import { uuid, randomToken, hash, digest, equal, signToken, verifyToken } from '../security.mjs';
import { requireValue, ServiceError } from '../http/errors.mjs';
import { isUuid } from '../billing/ledger.mjs';
import { normalizeHandle, validatePassword, createPassword, verifyPassword } from './passwords.mjs';

export class Accounts {
  constructor(db, ledger, cfg) { Object.assign(this, { db, ledger, cfg }); }
  async throttle(action, handle, ip) {
    const subject = hash(this.cfg.tokenKey, handle), address = hash(this.cfg.tokenKey, ip || 'unknown');
    const registration = action === 'register';
    await this.db.transaction(async c => {
      await c.query('SELECT pg_advisory_xact_lock(42390024)');
      const r = (await c.query(`SELECT count(*) FILTER(WHERE ip_hash=$1) ip,count(*) FILTER(WHERE subject_hash=$2) subject,count(*) total FROM auth_attempts WHERE action=$3 AND created_at>now()-($4::integer*interval '1 minute')`, [address, subject, action, registration ? 1440 : 15])).rows[0];
      requireValue(Number(r.ip) < (registration ? 20 : 60) && Number(r.subject) < (registration ? 5 : 10) && Number(r.total) < (registration ? 200 : 1000), 'AUTH_RATE_LIMIT', '尝试较多，请稍后再试', 429);
      await c.query('INSERT INTO auth_attempts(action,ip_hash,subject_hash) VALUES($1,$2,$3)', [action, address, subject]);
    });
  }
  validInvitation(row) {
    requireValue(row && !row.revoked && !row.used_at && new Date(row.expires_at) > new Date(), 'INVITATION_UNAVAILABLE', '邀请码无效、已使用或已过期', 403);
  }
  async register(input, ip) {
    const handle = normalizeHandle(input.handle), password = validatePassword(input.password);
    requireValue(typeof input.invitation === 'string' && /^DDI-[A-Za-z0-9_-]{43}$/.test(input.invitation), 'INVITATION_UNAVAILABLE', '请输入有效的邀请码', 403);
    await this.throttle('register', handle, ip);
    const invitationHash = digest(input.invitation);
    this.validInvitation((await this.db.query('SELECT * FROM invitations WHERE code_hash=$1', [invitationHash])).rows[0]);
    const passwordHash = await createPassword(password), recoveryCode = 'DDR-' + randomToken();
    try {
      return await this.db.transaction(async c => {
        const invitation = (await c.query('SELECT * FROM invitations WHERE code_hash=$1 FOR UPDATE', [invitationHash])).rows[0];
        this.validInvitation(invitation);
        const user = (await c.query('INSERT INTO users(id,handle,password_hash,recovery_hash) VALUES($1,$2,$3,$4) RETURNING *', [uuid(), handle, passwordHash, hash(this.cfg.tokenKey, recoveryCode)])).rows[0];
        await c.query('UPDATE invitations SET used_at=now(),used_by=$2 WHERE id=$1', [invitation.id, user.id]);
        const plan = (await c.query("SELECT definition FROM plans WHERE id=$1 AND status='published'", [this.cfg.plans.activeWelcomePlan])).rows[0]?.definition;
        requireValue(plan, 'WELCOME_NOT_READY', '欢迎权益尚未配置', 503);
        await this.ledger.grant(c, user.id, 'welcome', plan, 'invited-account');
        const session = await this.newSession(c, user.id, uuid());
        await this.db.audit(c, 'user:' + user.id, 'account.register', user.id, { invitationId: invitation.id });
        return { ...this.tokens(user, session), recoveryCode };
      });
    } catch (e) {
      if (e.code === '23505') throw new ServiceError('HANDLE_UNAVAILABLE', '这个账号已有人使用，请换一个', 409);
      throw e;
    }
  }
  async login(input, ip) {
    const handle = normalizeHandle(input.handle);
    await this.throttle('login', handle, ip);
    const row = (await this.db.query('SELECT * FROM users WHERE handle=$1', [handle])).rows[0];
    const valid = await verifyPassword(input.password, row?.password_hash);
    requireValue(valid && row?.status === 'active', 'INVALID_LOGIN', '账号或密码不正确', 401);
    return this.db.transaction(async c => {
      const user = await this.db.accountLock(c, row.id);
      requireValue(user?.status === 'active' && user.password_hash === row.password_hash, 'INVALID_LOGIN', '账号或密码不正确', 401);
      const session = await this.newSession(c, user.id, uuid());
      await this.db.audit(c, 'user:' + user.id, 'session.login', session.id);
      return this.tokens(user, session);
    });
  }
  async recover(input, ip) {
    const handle = normalizeHandle(input.handle), password = validatePassword(input.password);
    await this.throttle('recover', handle, ip);
    requireValue(typeof input.recoveryCode === 'string' && /^DDR-[A-Za-z0-9_-]{43}$/.test(input.recoveryCode), 'INVALID_RECOVERY', '账号或恢复码不正确', 401);
    const nextHash = await createPassword(password), recoveryCode = 'DDR-' + randomToken();
    return this.db.transaction(async c => {
      const user = (await c.query('SELECT * FROM users WHERE handle=$1 FOR UPDATE', [handle])).rows[0];
      requireValue(user?.status === 'active' && equal(user.recovery_hash, hash(this.cfg.tokenKey, input.recoveryCode)), 'INVALID_RECOVERY', '账号或恢复码不正确', 401);
      await c.query('UPDATE users SET password_hash=$2,recovery_hash=$3 WHERE id=$1', [user.id, nextHash, hash(this.cfg.tokenKey, recoveryCode)]);
      await c.query('UPDATE sessions SET revoked=true WHERE user_id=$1', [user.id]);
      const session = await this.newSession(c, user.id, uuid());
      await this.db.audit(c, 'user:' + user.id, 'account.recover', user.id);
      return { ...this.tokens(user, session), recoveryCode };
    });
  }
  async passwordAccount(user, password, ip) {
    await this.throttle('reauth', user.handle, ip);
    const row = (await this.db.query('SELECT * FROM users WHERE id=$1', [user.id])).rows[0];
    const valid = await verifyPassword(password, row?.password_hash);
    requireValue(valid && row?.status === 'active', 'INVALID_LOGIN', '密码不正确', 403);
    return row;
  }
  async reauthenticate(user, password, ip) {
    const row = await this.passwordAccount(user, password, ip);
    return this.db.transaction(async c => {
      const current = await this.db.accountLock(c, user.id);
      requireValue(current?.status === 'active' && current.password_hash === row.password_hash, 'INVALID_LOGIN', '账号已变化，请重新登录', 403);
      const r = await c.query('UPDATE sessions SET verified_at=now() WHERE id=$1 AND user_id=$2 AND NOT revoked', [user.session_id, user.id]);
      requireValue(r.rowCount, 'UNAUTHENTICATED', '请重新登录', 401);
      return { ok: true };
    });
  }
  async changePassword(user, input, ip) {
    const row = await this.passwordAccount(user, input.currentPassword, ip);
    const nextHash = await createPassword(input.password);
    return this.db.transaction(async c => {
      const current = await this.db.accountLock(c, user.id);
      requireValue(current?.status === 'active' && current.password_hash === row.password_hash, 'INVALID_LOGIN', '账号已变化，请重新登录', 403);
      await c.query('UPDATE users SET password_hash=$2 WHERE id=$1', [user.id, nextHash]);
      await c.query('UPDATE sessions SET revoked=true WHERE user_id=$1', [user.id]);
      const session = await this.newSession(c, user.id, uuid());
      await this.db.audit(c, 'user:' + user.id, 'account.password', user.id);
      return this.tokens(current, session);
    });
  }
  async rotateRecovery(user, password, ip) {
    const row = await this.passwordAccount(user, password, ip), recoveryCode = 'DDR-' + randomToken();
    return this.db.transaction(async c => {
      const current = await this.db.accountLock(c, user.id);
      requireValue(current?.status === 'active' && current.password_hash === row.password_hash, 'INVALID_LOGIN', '账号已变化，请重新登录', 403);
      await c.query('UPDATE users SET recovery_hash=$2 WHERE id=$1', [user.id, hash(this.cfg.tokenKey, recoveryCode)]);
      await this.db.audit(c, 'user:' + user.id, 'account.recovery-code', user.id);
      return { recoveryCode };
    });
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
      user: { id: user.id, handle: user.handle },
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
        `SELECT u.id,u.handle,s.id session_id,s.verified_at FROM users u JOIN sessions s ON s.user_id=u.id WHERE u.id=$1 AND s.id=$2 AND u.status='active' AND NOT s.revoked AND s.expires_at>now()`,
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
        "UPDATE users SET status='deleted',handle='deleted_'||replace(id::text,'-',''),password_hash=NULL,recovery_hash=NULL WHERE id=$1",
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
