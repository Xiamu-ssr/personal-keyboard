-- SMS was never enabled for production registration. Stop rather than invent
-- credentials for an existing account if this precondition changes.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='phone_hash') THEN
    IF EXISTS (SELECT 1 FROM users) THEN
      RAISE EXCEPTION 'Existing SMS identities require an explicit account conversion before migration';
    END IF;
    ALTER TABLE users DROP COLUMN phone_hash, DROP COLUMN phone_cipher, DROP COLUMN phone_hint;
    ALTER TABLE users ADD COLUMN handle text NOT NULL UNIQUE, ADD COLUMN password_hash text, ADD COLUMN recovery_hash text;
  END IF;
END $$;
DROP TABLE IF EXISTS challenges;
