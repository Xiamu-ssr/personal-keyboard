import { expressionTemplates, expressionAttributes } from './expression.mjs';
import { seal, unseal } from '../security.mjs';

/** One-time document migration. No legacy expression type remains in the live API. */
export async function migratePalettes(db, cfg) {
  await db.transaction(async c => {
    await c.query("SELECT pg_advisory_xact_lock(hashtext('palette-schema-2'))");
    const rows = (await c.query('SELECT user_id,version,body_cipher FROM advisor_palettes FOR UPDATE')).rows;
    for (const row of rows) {
      const slots = JSON.parse(unseal(cfg.masterKey, row.body_cipher, 'palette:' + row.user_id));
      if (!Array.isArray(slots) || slots.length !== 6) throw new Error('Stored palette is invalid; migration stopped');
      const legacy = slots.some(s => Object.hasOwn(s, 'expression'));
      if (!legacy) {
        slots.forEach(s => expressionAttributes(s.attributes));
        continue;
      }
      const migrated = slots.map((s, i) => {
        const preset = expressionTemplates.find(p => p.id === s.expression);
        if (!preset || s.id !== 'slot' + (i + 1) || typeof s.name !== 'string') throw new Error('Unknown legacy palette; original document preserved');
        return { id: s.id, name: s.name, attributes: expressionAttributes(preset.attributes) };
      });
      await c.query('UPDATE advisor_palettes SET version=version+1,body_cipher=$2 WHERE user_id=$1', [row.user_id, seal(cfg.masterKey, JSON.stringify(migrated), 'palette:' + row.user_id)]);
    }
  });
}
