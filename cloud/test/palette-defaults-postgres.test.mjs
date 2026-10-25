import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import pg from 'pg';
import { Database } from '../db/database.mjs';
import { configuration } from '../config.mjs';
import { migrateDefaultSlots } from '../advisor/defaults-migration.mjs';
import { defaultPaletteSlots } from '../advisor/expression.mjs';
import { uuid, seal, unseal } from '../security.mjs';

test('PostgreSQL default-palette migration preserves edits, versions once and never repeats',
  { skip: process.env.KEYBOARD_DATABASE_TEST !== '1' }, async () => {
    const env = JSON.parse(await fs.readFile(process.env.KEYBOARD_TEST_ENV, 'utf8'));
    const url = new URL(env.DATABASE_URL);
    assert(['127.0.0.1', 'localhost'].includes(url.hostname) && url.pathname === '/keyboard_v091_test');
    const owner = new pg.Client({ connectionString: String(url) }); await owner.connect();
    const schema = 'palette_' + uuid().replaceAll('-', '');
    await owner.query('CREATE SCHEMA ' + schema);
    url.searchParams.set('options', '-c search_path=' + schema);
    const cfg = await configuration({ ...env, DATABASE_URL: String(url) }), db = new Database(String(url));
    try {
      await db.migrate(cfg.plans, cfg.pricing);
      const original = JSON.parse(await fs.readFile(new URL('../advisor/previous-defaults.json', import.meta.url), 'utf8'));
      const custom = structuredClone(original); custom[0].name = '本人自定义'; custom[4].attributes.emoji = 'many';
      const a = uuid(), b = uuid();
      for (const [id, slots] of [[a, original], [b, custom]]) {
        await db.query('INSERT INTO users(id,handle) VALUES($1,$2)', [id, 'fixture_' + id.slice(0, 8)]);
        await db.query('INSERT INTO advisor_palettes(user_id,version,body_cipher) VALUES($1,7,$2)',
          [id, seal(cfg.masterKey, JSON.stringify(slots), 'palette:' + id)]);
      }
      await migrateDefaultSlots(db, cfg);
      const read = async id => {
        const row = (await db.query('SELECT * FROM advisor_palettes WHERE user_id=$1', [id])).rows[0];
        return { version: Number(row.version), slots: JSON.parse(unseal(cfg.masterKey, row.body_cipher, 'palette:' + id)) };
      };
      assert.deepEqual(await read(a), { version: 8, slots: defaultPaletteSlots() });
      const saved = await read(b);
      assert.equal(saved.version, 8);
      assert.deepEqual(saved.slots[0], custom[0]); assert.deepEqual(saved.slots[4], custom[4]);
      assert.equal(saved.slots[1].name, 'Mean');
      await migrateDefaultSlots(db, cfg);
      assert.deepEqual(await read(b), saved);
      assert.equal((await db.query("SELECT count(*) FROM audits WHERE action='palette.defaults.refresh'")).rows[0].count, '2');
    } finally {
      await db.close(); await owner.query('DROP SCHEMA ' + schema + ' CASCADE'); await owner.end();
    }
  });
