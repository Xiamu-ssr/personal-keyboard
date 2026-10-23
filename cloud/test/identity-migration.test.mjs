import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import pg from 'pg';
import crypto from 'node:crypto';

test('SMS migration converts an empty schema and refuses to erase existing identities', { skip: process.env.KEYBOARD_DATABASE_TEST !== '1' }, async () => {
  const env = JSON.parse(await fs.readFile(process.env.KEYBOARD_TEST_ENV || new URL('../../.runtime/v06/test-env.json', import.meta.url), 'utf8'));
  assert(new URL(env.DATABASE_URL).pathname.endsWith('_test'), 'must use disposable test database');
  const pool = new pg.Pool({ connectionString: env.DATABASE_URL, max: 1 });
  const client = await pool.connect();
  const migration = await fs.readFile(new URL('../db/identity-migration.sql', import.meta.url), 'utf8');
  const schema = 'identity_' + crypto.randomBytes(8).toString('hex');
  try {
    await client.query('BEGIN');
    await client.query(`CREATE SCHEMA ${schema}; SET LOCAL search_path TO ${schema}`);
    await client.query('CREATE TABLE users(id uuid PRIMARY KEY, phone_hash text, phone_cipher text, phone_hint text); CREATE TABLE challenges(id uuid)');
    await client.query('SAVEPOINT empty_schema');
    await client.query(migration);
    const columns = (await client.query("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users'")).rows.map(r => r.column_name);
    assert(columns.includes('handle') && columns.includes('password_hash') && !columns.includes('phone_hash'));
    await client.query('ROLLBACK TO SAVEPOINT empty_schema');
    await client.query("INSERT INTO users(id,phone_hash,phone_cipher,phone_hint) VALUES($1,'kept','kept','kept')", [crypto.randomUUID()]);
    await client.query('SAVEPOINT populated_schema');
    await assert.rejects(client.query(migration), /explicit account conversion/);
    await client.query('ROLLBACK TO SAVEPOINT populated_schema');
    assert.equal((await client.query('SELECT phone_hash FROM users')).rows[0].phone_hash, 'kept');
  } finally {
    await client.query('ROLLBACK');
    client.release();
    await pool.end();
  }
});
