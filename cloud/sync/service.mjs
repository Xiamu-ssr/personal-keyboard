import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { uuid, seal, unseal, digest } from "../security.mjs";
import { requireValue } from "../http/errors.mjs";
export class SyncService {
  constructor(db, ledger, cfg) {
    Object.assign(this, { db, ledger, cfg });
  }
  async allowed(user, c = this.db) {
    requireValue((await c.query('SELECT status FROM users WHERE id=$1', [user.id])).rows[0]?.status === 'active', 'ACCOUNT_DISABLED', '账号不可用', 403);
    const b = await this.ledger.balance(user.id, c);
    requireValue(
      b.cloudSync,
      "SYNC_NOT_INCLUDED",
      "云同步属于付费权益；本机资料仍可导出",
      402,
    );
    return b;
  }
  async key(user) {
    await this.allowed(user);
    requireValue(
      Date.now() - new Date(user.verified_at).getTime() < 600000,
      "REAUTH_REQUIRED",
      "换机恢复前请重新验证手机号",
      401,
    );
    return this.db.transaction(async (c) => {
      await this.db.accountLock(c, user.id);
      await this.allowed(user, c);
      let row = (
        await c.query("SELECT key_cipher FROM sync_keys WHERE user_id=$1", [
          user.id,
        ])
      ).rows[0];
      if (!row) {
        const encrypted = seal(
          this.cfg.masterKey,
          crypto.randomBytes(32).toString("base64"),
          user.id,
        );
        await c.query(
          "INSERT INTO sync_keys(user_id,key_cipher) VALUES($1,$2)",
          [user.id, encrypted],
        );
        row = { key_cipher: encrypted };
      }
      return {
        key: unseal(this.cfg.masterKey, row.key_cipher, user.id),
        recovery: "account-bound-server-envelope",
      };
    });
  }
  async read(user) {
    await this.allowed(user);
    const m = (
      await this.db.query("SELECT * FROM sync_manifests WHERE user_id=$1", [
        user.id,
      ])
    ).rows[0];
    if (!m) return { revision: 0, blob: null };
    const bytes = await fs.readFile(
      path.join(this.cfg.storageRoot, m.blob_name),
    );
    requireValue(
      digest(bytes) === m.sha256,
      "SYNC_CORRUPT",
      "同步文件校验失败",
      500,
    );
    return {
      revision: Number(m.revision),
      blob: bytes.toString("base64"),
      sha256: m.sha256,
    };
  }
  async write(user, input) {
    requireValue(
      Number.isSafeInteger(input.baseRevision) &&
        input.baseRevision >= 0 &&
        typeof input.blob === "string" &&
        /^[A-Za-z0-9+/=]+$/.test(input.blob),
      "INVALID_SYNC",
      "同步包格式错误",
    );
    const bytes = Buffer.from(input.blob, "base64");
    requireValue(bytes.length >= 28, "INVALID_SYNC", "同步包必须经过设备加密");
    let old,
      newName,
      committed = false;
    try {
      const result = await this.db.transaction(async (c) => {
        await this.db.accountLock(c, user.id);
        const balance = await this.allowed(user, c);
        requireValue(
          bytes.length <= balance.syncMaxBytes,
          "SYNC_TOO_LARGE",
          "同步资料超过套餐容量",
          413,
        );
        requireValue(
          (
            await c.query("SELECT user_id FROM sync_keys WHERE user_id=$1", [
              user.id,
            ])
          ).rows.length,
          "SYNC_KEY_REQUIRED",
          "请先获取同步加密密钥",
        );
        const m = (
          await c.query(
            "SELECT * FROM sync_manifests WHERE user_id=$1 FOR UPDATE",
            [user.id],
          )
        ).rows[0];
        requireValue(
          Number(m?.revision || 0) === input.baseRevision,
          "SYNC_CONFLICT",
          "另一台设备已更新，请先合并云端资料",
          409,
        );
        newName = user.id + "-" + uuid() + ".bin";
        await fs.mkdir(this.cfg.storageRoot, { recursive: true, mode: 0o700 });
        await fs.writeFile(path.join(this.cfg.storageRoot, newName), bytes, {
          flag: "wx",
          mode: 0o600,
        });
        old = m?.blob_name;
        const next = input.baseRevision + 1,
          sha = digest(bytes);
        await c.query(
          "INSERT INTO sync_manifests(user_id,revision,blob_name,byte_count,sha256) VALUES($1,$2,$3,$4,$5) ON CONFLICT(user_id) DO UPDATE SET revision=EXCLUDED.revision,blob_name=EXCLUDED.blob_name,byte_count=EXCLUDED.byte_count,sha256=EXCLUDED.sha256,updated_at=now()",
          [user.id, next, newName, bytes.length, sha],
        );
        return { revision: next, sha256: sha };
      });
      committed = true;
      return result;
    } catch (e) {
      if (newName)
        await fs
          .unlink(path.join(this.cfg.storageRoot, newName))
          .catch(() => {});
      throw e;
    } finally {
      if (committed && old)
        await fs.unlink(path.join(this.cfg.storageRoot, old)).catch(() => {});
    }
  }
  async remove(user) {
    const old = await this.db.transaction(async (c) => {
      await this.db.accountLock(c, user.id);
      const m = (
        await c.query(
          "DELETE FROM sync_manifests WHERE user_id=$1 RETURNING blob_name",
          [user.id],
        )
      ).rows[0];
      await c.query("DELETE FROM sync_keys WHERE user_id=$1", [user.id]);
      return m?.blob_name;
    });
    if (old)
      await fs.unlink(path.join(this.cfg.storageRoot, old)).catch(() => {});
    return { ok: true };
  }
}
