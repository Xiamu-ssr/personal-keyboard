import { requireValue } from '../http/errors.mjs';
import { canonical, digest, seal, unseal } from '../security.mjs';
import { isUuid } from '../billing/ledger.mjs';
import { defaultPaletteSlots, expressionAttributes, expressionInstruction, paletteSchemaVersion } from './expression.mjs';

export const defaultPalette = () => ({ schemaVersion: paletteSchemaVersion, version: 0, slots: defaultPaletteSlots() });
export function paletteValue(raw) {
  requireValue(Array.isArray(raw) && raw.length === 6, 'PALETTE_FORMAT', '调色盘需要六种表达');
  return raw.map((v, i) => {
    requireValue(v && Object.keys(v).length === 3 && v.id === 'slot' + (i + 1) && typeof v.name === 'string' && v.name.trim().length > 0 && [...v.name.trim()].length <= 16, 'PALETTE_FORMAT', '表达名称或属性无效');
    return { id: v.id, name: v.name.trim(), attributes: expressionAttributes(v.attributes) };
  });
}
export function requestedSlots(palette, slotId) {
  if (!slotId) return palette.slots.map(s => ({ ...s, instruction: expressionInstruction(s.attributes) }));
  const slot = palette.slots.find(s => s.id === slotId);
  requireValue(slot, 'PALETTE_FORMAT', '表达已变化，请重新打开');
  return [0, 1, 2].map(i => ({ ...slot, variant: i + 1, instruction: expressionInstruction(slot.attributes) }));
}
export class Palettes {
  constructor(db, cfg) { Object.assign(this, { db, cfg }); }
  async get(user, c = this.db) {
    const row = (await c.query('SELECT version,body_cipher FROM advisor_palettes WHERE user_id=$1', [user])).rows[0];
    return row ? { schemaVersion: paletteSchemaVersion, version: Number(row.version), slots: JSON.parse(unseal(this.cfg.masterKey, row.body_cipher, 'palette:' + user)) } : defaultPalette();
  }
  async set(user, raw) {
    requireValue(isUuid(raw.requestId) && Number.isSafeInteger(raw.baseVersion), 'PALETTE_FORMAT', '缺少调色盘版本');
    const slots = paletteValue(raw.slots), hash = digest(canonical(raw));
    return this.db.transaction(async c => {
      await this.db.accountLock(c, user.id);
      const receipt = (await c.query('SELECT payload_hash FROM palette_receipts WHERE user_id=$1 AND request_id=$2', [user.id, raw.requestId])).rows[0];
      if (receipt) { requireValue(receipt.payload_hash === hash, 'REQUEST_CONFLICT', '请求内容已变化', 409); return this.get(user.id, c); }
      const old = await this.get(user.id, c);
      requireValue(old.version === raw.baseVersion, 'PALETTE_CONFLICT', '调色盘已更新，请刷新后再修改', 409);
      const version = old.version + 1;
      await c.query('INSERT INTO advisor_palettes(user_id,version,body_cipher) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET version=excluded.version,body_cipher=excluded.body_cipher', [user.id, version, seal(this.cfg.masterKey, JSON.stringify(slots), 'palette:' + user.id)]);
      await c.query('INSERT INTO palette_receipts(user_id,request_id,payload_hash) VALUES($1,$2,$3)', [user.id, raw.requestId, hash]);
      return { schemaVersion: paletteSchemaVersion, version, slots };
    });
  }
}
