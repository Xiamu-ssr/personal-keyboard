import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import pg from 'pg';
import { configuration } from '../config.mjs';
import { Database } from '../db/database.mjs';
import { Ledger } from '../billing/ledger.mjs';
import { uuid, seal, digest, canonical } from '../security.mjs';
const enabled = process.env.KEYBOARD_DATABASE_TEST === '1';
async function isolated(fn) {
  const env = JSON.parse(await fs.readFile(process.env.KEYBOARD_TEST_ENV, 'utf8'));
  const url = new URL(env.DATABASE_URL);
  assert(['127.0.0.1', 'localhost'].includes(url.hostname) && url.pathname === '/keyboard_v091_test', 'Credit tests require the owned local test database');
  const owner = new pg.Client({ connectionString: String(url) }); await owner.connect();
  const schema = 'credit_' + uuid().replaceAll('-', '');
  await owner.query('CREATE SCHEMA ' + schema);
  url.searchParams.set('options', '-c search_path=' + schema);
  const cfg = await configuration({ ...env, DATABASE_URL: String(url) });
  const db = new Database(String(url));
  try { await fn(db, cfg); } finally { await db.close(); await owner.query('DROP SCHEMA ' + schema + ' CASCADE'); await owner.end(); }
}
async function legacy(db, cfg, active = false) {
  await db.query(await fs.readFile(new URL('./fixtures/billing-v1.sql', import.meta.url), 'utf8'));
  const user = uuid(), grant = uuid(), job = uuid(), requestId = uuid();
  const input = { context: '', polish: true };
  await db.query("INSERT INTO users(id,handle) VALUES($1,'migration-fixture')", [user]);
  await db.query("INSERT INTO grants(id,user_id,grant_key,plan_snapshot,asr_limit,advisor_limit,asr_used,advisor_used,reason) VALUES($1,$2,'welcome',$3,10000,20,1234,3,'fixture')", [grant, user, { name: '旧权益', limits: { asr_characters: 10000, advisor_generations: 20 } }]);
  const result = seal(cfg.masterKey, { text: '保留原结果' }, job);
  await db.query("INSERT INTO jobs(id,user_id,kind,request_id,payload_hash,status,result_cipher,consumed_asr,consumed_advisor) VALUES($1,$2,'asr',$3,$4,$5,$6,1234,0)",
    [job, user, requestId, digest(canonical({ kind: 'asr', input, hasImages: false, sourceJob: null })), active ? 'running' : 'succeeded', result]);
  await db.query('INSERT INTO job_allocations(job_id,grant_id,asr_consumed) VALUES($1,$2,1234)', [job, grant]);
  const memory = seal(cfg.masterKey, { unchanged: true }, user);
  await db.query('INSERT INTO personal_memory(user_id,version,body_cipher) VALUES($1,17,$2)', [user, memory]);
  return { user, job, requestId, input, memory, result };
}
test('PostgreSQL one-way Credit migration preserves remaining rights, result recovery, memory and audit evidence', { skip: !enabled }, async () => isolated(async (db, cfg) => {
  const old = await legacy(db, cfg);
  await db.migrate(cfg.plans, cfg.pricing);
  const ledger = new Ledger(db, cfg);
  assert.equal((await ledger.balance(old.user)).credits.available, 3453.2);
  assert.equal((await db.query('SELECT body_cipher,version FROM personal_memory WHERE user_id=$1', [old.user])).rows[0].body_cipher, old.memory);
  assert.equal((await db.query('SELECT result_cipher FROM jobs WHERE id=$1', [old.job])).rows[0].result_cipher, old.result);
  const restored = await ledger.existing(old.user, old.requestId, 'asr', old.input);
  assert.equal(ledger.view(restored).result.text, '保留原结果');
  assert.equal((await db.query('SELECT count(*) n FROM billing_v1_archive')).rows[0].n, '3');
  assert.equal((await db.query("SELECT count(*) n FROM information_schema.columns WHERE table_schema=current_schema() AND column_name IN('asr_limit','advisor_limit','reserved_asr','asr_reserved')")).rows[0].n, '0');
  await db.migrate(cfg.plans, cfg.pricing);
  assert.equal((await ledger.balance(old.user)).credits.available, 3453.2, 'second startup must not issue a second conversion');
}));
test('PostgreSQL Credit migration rolls back completely while an old task is in flight', { skip: !enabled }, async () => isolated(async (db, cfg) => {
  const old = await legacy(db, cfg, true);
  await assert.rejects(db.migrate(cfg.plans, cfg.pricing), e => e.code === 'BILLING_MIGRATION_BUSY');
  assert.equal((await db.query('SELECT asr_limit FROM grants WHERE user_id=$1', [old.user])).rows[0].asr_limit, '10000');
  await db.query("UPDATE jobs SET status='failed' WHERE id=$1", [old.job]);
  await db.migrate(cfg.plans, cfg.pricing);
  assert.equal((await new Ledger(db, cfg).balance(old.user)).credits.available, 3453.2);
}));
test('PostgreSQL Credit settlement is concurrent, frozen-price, refundable, bounded and exactly once', { skip: !enabled }, async () => isolated(async (db, cfg) => {
  await db.migrate(cfg.plans, cfg.pricing);
  const ledger = new Ledger(db, cfg), user = uuid();
  await db.query("INSERT INTO users(id,handle) VALUES($1,'credit-fixture')", [user]);
  await db.transaction(c => ledger.grant(c, user, 'welcome', cfg.plans.plans[0], 'fixture'));
  const input = { text: 'example', sourceLanguage: 'auto', targetLanguage: 'en' }, requestId = uuid();
  const options = { quote: { model: cfg.textModel, inputTokens: 1000, outputTokens: 1000 } };
  const concurrent = await Promise.all(Array.from({ length: 8 }, () => ledger.reserve(user, requestId, 'translation', input, options)));
  assert.equal(concurrent.filter(r => r.created).length, 1);
  assert.equal((await ledger.balance(user)).credits.reserved, 6.125);
  const next = structuredClone(cfg.pricing); next.markupBasisPoints = 20000;
  await ledger.pricing.save('test', 1, next);
  await assert.rejects(ledger.pricing.save('test', 1, next), e => e.code === 'PRICE_CONFLICT');
  const provider = { model: cfg.textModel, usage: { prompt_tokens: 1000, completion_tokens: 1000, completion_tokens_details: { reasoning_tokens: 800 } } };
  await Promise.all(Array.from({ length: 8 }, () => ledger.finish(concurrent[0].job.id, 'succeeded', { text: 'kept' }, provider)));
  assert.equal((await ledger.balance(user)).credits.used, 6.125, 'freeze the old price and never double-count reasoning or retries');
  assert.equal((await ledger.reserve(user, requestId, 'translation', input, options)).created, false);
  const newer = await ledger.reserve(user, uuid(), 'translation', input, options);
  assert.equal(newer.job.billing_snapshot.version, 2);
  await ledger.finish(newer.job.id, 'failed');
  assert.equal((await ledger.balance(user)).credits.reserved, 0);
  assert.equal((await ledger.balance(user)).credits.used, 6.125);
  const tiny = await ledger.reserve(user, uuid(), 'translation', input,
    { quote: { model: cfg.textModel, inputTokens: 1, outputTokens: 0 } });
  const result = await ledger.finish(tiny.job.id, 'succeeded', { text: 'Do not lose the result' }, provider);
  assert.equal(ledger.view(result).result.text, 'Do not lose the result');
  assert.equal(Number(result.consumed_credits), Number(result.reserved_credits), 'never exceed the quoted reservation');
  assert.equal((await db.query("SELECT count(*) n FROM audits WHERE action='quote.exceeded'")).rows[0].n, '1');
  const missing = await ledger.reserve(user, uuid(), 'translation', input, options);
  await ledger.finish(missing.job.id, 'succeeded', { text: 'kept too' }, { model: cfg.textModel });
  assert.equal((await db.query('SELECT consumed_credits FROM jobs WHERE id=$1', [missing.job.id])).rows[0].consumed_credits, '0');
  assert.equal((await db.query("SELECT count(*) n FROM audits WHERE action='usage.unavailable'")).rows[0].n, '1');
  const limitedUser = uuid(); await db.query("INSERT INTO users(id,handle) VALUES($1,'limited-fixture')", [limitedUser]);
  await db.transaction(c => ledger.grant(c, limitedUser, 'small', { ...cfg.plans.plans[0], limits: { credits: 10 } }, 'fixture'));
  const pair = await Promise.allSettled([ledger.reserve(limitedUser, uuid(), 'translation', input, options), ledger.reserve(limitedUser, uuid(), 'translation', input, options)]);
  assert.equal(pair.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(pair.find(r => r.status === 'rejected').reason.code, 'CREDITS_EXHAUSTED');
}));
