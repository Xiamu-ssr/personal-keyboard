CREATE TABLE IF NOT EXISTS schema_migrations(version integer PRIMARY KEY,applied_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS users(id uuid PRIMARY KEY,handle text UNIQUE NOT NULL,password_hash text,recovery_hash text,status text NOT NULL DEFAULT 'active' CHECK(status IN('active','disabled','deleted')),created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS plans(id text PRIMARY KEY,version integer NOT NULL,status text NOT NULL CHECK(status IN('draft','published','retired')),definition jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),published_at timestamptz);
CREATE TABLE IF NOT EXISTS grants(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id),grant_key text NOT NULL,plan_snapshot jsonb NOT NULL,credit_limit bigint NOT NULL CHECK(credit_limit>=0),credit_used bigint NOT NULL DEFAULT 0 CHECK(credit_used>=0 AND credit_used<=credit_limit),sync_enabled boolean NOT NULL DEFAULT false,sync_max_bytes bigint NOT NULL DEFAULT 5242880,expires_at timestamptz,revoked boolean NOT NULL DEFAULT false,reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(user_id,grant_key));
CREATE TABLE IF NOT EXISTS sessions(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id),family_id uuid NOT NULL,token_hash text UNIQUE NOT NULL,replaced_by uuid,revoked boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now(),verified_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL);
CREATE TABLE IF NOT EXISTS jobs(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id),kind text NOT NULL CHECK(kind IN('asr','rewrite','advisor','translation','memory')),request_id uuid NOT NULL,payload_hash text NOT NULL,status text NOT NULL CHECK(status IN('queued','running','succeeded','failed','cancelled')),reserved_credits bigint NOT NULL DEFAULT 0,consumed_credits bigint NOT NULL DEFAULT 0,cost_micro_cny bigint NOT NULL DEFAULT 0,billing_snapshot jsonb NOT NULL DEFAULT '{}',input_cipher text,result_cipher text,error_code text,error_message text,has_images boolean NOT NULL DEFAULT false,provider_model text,provider_usage jsonb NOT NULL DEFAULT '{}',source_job uuid,created_at timestamptz NOT NULL DEFAULT now(),started_at timestamptz,finished_at timestamptz,expires_at timestamptz NOT NULL DEFAULT now()+interval '24 hours',UNIQUE(user_id,request_id));
CREATE INDEX IF NOT EXISTS jobs_account_balance ON jobs(user_id,status);
CREATE INDEX IF NOT EXISTS jobs_queue ON jobs(status,created_at);
CREATE TABLE IF NOT EXISTS job_allocations(job_id uuid NOT NULL REFERENCES jobs(id),grant_id uuid NOT NULL REFERENCES grants(id),credit_reserved bigint NOT NULL DEFAULT 0,credit_consumed bigint NOT NULL DEFAULT 0,PRIMARY KEY(job_id,grant_id));
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 1;
CREATE TABLE IF NOT EXISTS audits(id bigserial PRIMARY KEY,actor text NOT NULL,action text NOT NULL,target text NOT NULL,detail jsonb NOT NULL DEFAULT '{}',created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS admin_sessions(id uuid PRIMARY KEY,token_hash text UNIQUE NOT NULL,csrf_hash text NOT NULL,expires_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS admin_attempts(ip_hash text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS sync_keys(user_id uuid PRIMARY KEY REFERENCES users(id),key_cipher text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS sync_manifests(user_id uuid PRIMARY KEY REFERENCES users(id),revision bigint NOT NULL,blob_name text NOT NULL,byte_count bigint NOT NULL,sha256 text NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS orders(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES users(id),plan_snapshot jsonb NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>0),currency text NOT NULL DEFAULT 'CNY',status text NOT NULL DEFAULT 'pending' CHECK(status IN('pending','paid','closed','refunded')),transaction_id text UNIQUE,created_at timestamptz NOT NULL DEFAULT now(),paid_at timestamptz);
CREATE TABLE IF NOT EXISTS payment_nonces(nonce text PRIMARY KEY,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS personal_memory(user_id uuid PRIMARY KEY REFERENCES users(id),version bigint NOT NULL,body_cipher text NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS memory_changes(user_id uuid NOT NULL REFERENCES users(id),version bigint NOT NULL,body_cipher text NOT NULL,PRIMARY KEY(user_id,version));
CREATE TABLE IF NOT EXISTS memory_receipts(user_id uuid NOT NULL REFERENCES users(id),request_id uuid NOT NULL,payload_hash text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(user_id,request_id));
CREATE TABLE IF NOT EXISTS memory_history(user_id uuid NOT NULL REFERENCES users(id),history_id uuid NOT NULL,revision integer NOT NULL,payload_hash text NOT NULL,body_cipher text,next_offset integer NOT NULL DEFAULT 0,state text NOT NULL DEFAULT 'queued' CHECK(state IN('queued','done','superseded')),created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(user_id,history_id,revision));
CREATE INDEX IF NOT EXISTS memory_history_queue ON memory_history(user_id,state,created_at);
CREATE TABLE IF NOT EXISTS memory_work(user_id uuid PRIMARY KEY REFERENCES users(id),lease_token uuid,lease_until timestamptz,retry_at timestamptz NOT NULL DEFAULT now(),state text NOT NULL DEFAULT 'queued',error_code text);

ALTER TABLE memory_work ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS invitations(id uuid PRIMARY KEY,code_hash text UNIQUE NOT NULL,label text NOT NULL DEFAULT '',revoked boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,used_at timestamptz,used_by uuid REFERENCES users(id));
CREATE TABLE IF NOT EXISTS auth_attempts(action text NOT NULL,ip_hash text NOT NULL,subject_hash text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS auth_attempts_throttle ON auth_attempts(action,created_at DESC);

CREATE TABLE IF NOT EXISTS advisor_palettes (
 user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 version bigint NOT NULL, body_cipher text NOT NULL
);
CREATE TABLE IF NOT EXISTS palette_receipts (
 user_id uuid REFERENCES users(id) ON DELETE CASCADE,
 request_id uuid NOT NULL, payload_hash text NOT NULL, PRIMARY KEY(user_id,request_id)
);
CREATE TABLE IF NOT EXISTS credit_pricing(singleton integer PRIMARY KEY CHECK(singleton=1),version integer NOT NULL,definition jsonb NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
