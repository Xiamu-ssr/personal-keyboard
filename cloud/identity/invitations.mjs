import { randomToken, uuid, digest } from '../security.mjs';
import { isUuid } from '../billing/ledger.mjs';
import { requireValue } from '../http/errors.mjs';

/** Admission is separate from identity, prices and welcome entitlements. */
export class Invitations {
  constructor(db) { this.db = db; }
  async create(actor, { count = 1, days = 30, label = '' } = {}) {
    requireValue(Number.isInteger(count) && count >= 1 && count <= 20 && Number.isInteger(days) && days >= 1 && days <= 90 && typeof label === 'string' && label.length <= 80,
      'INVALID_INVITATION', '每批可生成1—20个邀请码，有效期1—90天');
    return this.db.transaction(async c => {
      const codes = [];
      for (let i = 0; i < count; i++) {
        const id = uuid(), code = 'DDI-' + randomToken();
        const result = await c.query("INSERT INTO invitations(id,code_hash,label,expires_at) VALUES($1,$2,$3,now()+($4::integer*interval '1 day')) RETURNING expires_at", [id, digest(code), label, days]);
        codes.push({ id, code, expiresAt: result.rows[0].expires_at });
      }
      await this.db.audit(c, actor, 'invitation.create', codes[0].id, { count, days, label });
      return { invitations: codes, oneTimeDisplay: true };
    });
  }
  async list() {
    return (await this.db.query('SELECT id,label,created_at,expires_at,used_at,revoked FROM invitations ORDER BY created_at DESC LIMIT 200')).rows;
  }
  async revoke(actor, id) {
    requireValue(isUuid(id), 'INVALID_INVITATION', '邀请码标识不正确');
    return this.db.transaction(async c => {
      const r = await c.query('UPDATE invitations SET revoked=true WHERE id=$1 AND used_at IS NULL RETURNING id', [id]);
      requireValue(r.rowCount, 'INVITATION_UNAVAILABLE', '邀请码已使用或不存在', 409);
      await this.db.audit(c, actor, 'invitation.revoke', id);
      return { ok: true };
    });
  }
}
