import { readFileSync } from 'node:fs';
import { canonical, seal, unseal } from '../security.mjs';
import { defaultPaletteSlots } from './expression.mjs';

const previous = JSON.parse(readFileSync(new URL('./previous-defaults.json', import.meta.url), 'utf8'));

/** Replace only unchanged built-in slots; preserve every user-edited name or preference. */
export function refreshDefaultSlots(slots) {
  const next = defaultPaletteSlots();
  return slots.map((slot, i) => canonical(slot) === canonical(previous[i]) ? next[i] : slot);
}

export async function migrateDefaultSlots(db, cfg) {
  await db.transaction(async c => {
    await c.query("SELECT pg_advisory_xact_lock(hashtext('palette-defaults-20261025'))");
    if ((await c.query('SELECT 1 FROM schema_migrations WHERE version=5')).rowCount) return;
    for (const row of (await c.query('SELECT user_id,version,body_cipher FROM advisor_palettes FOR UPDATE')).rows) {
      const slots = JSON.parse(unseal(cfg.masterKey, row.body_cipher, 'palette:' + row.user_id));
      const next = refreshDefaultSlots(slots);
      if (canonical(slots) === canonical(next)) continue;
      await c.query('UPDATE advisor_palettes SET version=version+1,body_cipher=$2 WHERE user_id=$1',
        [row.user_id, seal(cfg.masterKey, JSON.stringify(next), 'palette:' + row.user_id)]);
      await db.audit(c, 'migration', 'palette.defaults.refresh', row.user_id,
        { changedSlots: slots.filter((slot, i) => canonical(slot) !== canonical(next[i])).map(slot => slot.id) });
    }
    await c.query('INSERT INTO schema_migrations(version) VALUES(5)');
  });
}
