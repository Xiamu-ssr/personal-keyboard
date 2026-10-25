import { requireValue } from '../http/errors.mjs';

/** One-way currency migration. Preserve old financial evidence, never serve a legacy balance. */
export async function migrateCredits(c, policy) {
  const old = (await c.query("SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='grants' AND column_name='asr_limit'")).rowCount;
  if (!old) return;
  const active = Number((await c.query("SELECT count(*) n FROM jobs WHERE status IN('queued','running')")).rows[0].n);
  requireValue(active === 0, 'BILLING_MIGRATION_BUSY', '请先等待任务结束，再迁移积分', 503);
  await c.query(`CREATE TABLE IF NOT EXISTS billing_v1_archive(table_name text NOT NULL,row_key text NOT NULL,body jsonb NOT NULL,migrated_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(table_name,row_key));
    INSERT INTO billing_v1_archive SELECT 'grants',id::text,to_jsonb(g),now() FROM grants g ON CONFLICT DO NOTHING;
    INSERT INTO billing_v1_archive SELECT 'jobs',id::text,jsonb_build_object('reserved_asr',reserved_asr,'reserved_advisor',reserved_advisor,'consumed_asr',consumed_asr,'consumed_advisor',consumed_advisor),now() FROM jobs ON CONFLICT DO NOTHING;
    INSERT INTO billing_v1_archive SELECT 'allocations',job_id::text||':'||grant_id::text,to_jsonb(a),now() FROM job_allocations a ON CONFLICT DO NOTHING;
    ALTER TABLE grants ADD COLUMN credit_limit bigint NOT NULL DEFAULT 0, ADD COLUMN credit_used bigint NOT NULL DEFAULT 0;
    ALTER TABLE jobs ADD COLUMN reserved_credits bigint NOT NULL DEFAULT 0, ADD COLUMN consumed_credits bigint NOT NULL DEFAULT 0, ADD COLUMN cost_micro_cny bigint NOT NULL DEFAULT 0, ADD COLUMN billing_snapshot jsonb NOT NULL DEFAULT '{}';
    ALTER TABLE job_allocations ADD COLUMN credit_reserved bigint NOT NULL DEFAULT 0, ADD COLUMN credit_consumed bigint NOT NULL DEFAULT 0;`);
  const conversion = policy.legacyConversion;
  await c.query('UPDATE grants SET credit_limit=(asr_limit-asr_used)*$1+(advisor_limit-advisor_used)*$2',
    [conversion.milliCreditsPerAsrCharacter, conversion.milliCreditsPerAdvisorGeneration]);
  await c.query(`ALTER TABLE grants DROP COLUMN asr_limit,DROP COLUMN asr_used,DROP COLUMN advisor_limit,DROP COLUMN advisor_used;
    ALTER TABLE grants ADD CHECK(credit_limit>=0),ADD CHECK(credit_used>=0 AND credit_used<=credit_limit);
    ALTER TABLE jobs DROP COLUMN reserved_asr,DROP COLUMN consumed_asr,DROP COLUMN reserved_advisor,DROP COLUMN consumed_advisor;
    ALTER TABLE jobs DROP CONSTRAINT jobs_kind_check;
    ALTER TABLE jobs ADD CHECK(kind IN('asr','rewrite','advisor','translation','memory'));
    ALTER TABLE job_allocations DROP COLUMN asr_reserved,DROP COLUMN asr_consumed,DROP COLUMN advisor_reserved,DROP COLUMN advisor_consumed;
    UPDATE plans SET status='retired' WHERE NOT (definition->'limits' ? 'credits');
    INSERT INTO schema_migrations(version) VALUES(4) ON CONFLICT DO NOTHING;`);
  await c.query("INSERT INTO audits(actor,action,target,detail) VALUES('migration','credits.v1','all', $1)",
    [{ pricingVersion: policy.version, conversion, legacyFinancialEvidence: 'billing_v1_archive' }]);
}
