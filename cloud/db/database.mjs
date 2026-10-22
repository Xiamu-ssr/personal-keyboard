import pg from "pg";
import fs from "node:fs/promises";
import { uuid } from "../security.mjs";
export class Database {
  constructor(url) {
    this.pool = new pg.Pool({
      connectionString: url,
      max: 5,
      connectionTimeoutMillis: 5000,
      idleTimeoutMillis: 10000,
    });
    this.pool.on("error", () =>
      console.error("Database connection interrupted"),
    );
  }
  query(text, values) {
    return this.pool.query(text, values);
  }
  async transaction(fn) {
    const c = await this.pool.connect();
    try {
      await c.query("BEGIN");
      const value = await fn(c);
      await c.query("COMMIT");
      return value;
    } catch (e) {
      await c.query("ROLLBACK");
      throw e;
    } finally {
      c.release();
    }
  }
  async migrate(plans) {
    await this.transaction(async (c) => {
      await c.query("SELECT pg_advisory_xact_lock(42390021)");
      await c.query(
        await fs.readFile(new URL("./schema.sql", import.meta.url), "utf8"),
      );
      for (const p of plans.plans)
        await c.query(
          "INSERT INTO plans(id,version,status,definition,published_at) VALUES($1,$2,$3,$4,CASE WHEN $3='published' THEN now() ELSE NULL END) ON CONFLICT(id) DO NOTHING",
          [p.id, p.version, p.status, p],
        );
      await c.query(
        "INSERT INTO schema_migrations(version) VALUES(1),(2) ON CONFLICT DO NOTHING",
      );
    });
  }
  async accountLock(c, id) {
    const r = await c.query("SELECT * FROM users WHERE id=$1 FOR UPDATE", [id]);
    return r.rows[0];
  }
  async audit(c, actor, action, target, detail = {}) {
    await c.query(
      "INSERT INTO audits(actor,action,target,detail) VALUES($1,$2,$3,$4)",
      [actor, action, target, detail],
    );
  }
  async cleanup() {
    await this.query(
      "UPDATE jobs SET input_cipher=NULL,result_cipher=NULL WHERE expires_at<now() AND (input_cipher IS NOT NULL OR result_cipher IS NOT NULL)",
    );
    await this.query(
      "DELETE FROM challenges WHERE created_at<now()-interval '2 days'",
    );
    await this.query(
      "DELETE FROM sessions WHERE expires_at<now()-interval '30 days'",
    );
    await this.query("DELETE FROM admin_sessions WHERE expires_at<now()");
    await this.query(
      "DELETE FROM admin_attempts WHERE created_at<now()-interval '1 day'",
    );
  }
  close() {
    return this.pool.end();
  }
}
