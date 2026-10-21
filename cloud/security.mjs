import crypto from "node:crypto";
import { requireValue } from "./http/errors.mjs";
export const randomToken = () => crypto.randomBytes(32).toString("base64url");
export const uuid = () => crypto.randomUUID();
export const digest = (value) =>
  crypto.createHash("sha256").update(value).digest("hex");
export const hash = (key, value) =>
  crypto.createHmac("sha256", key).update(value).digest("hex");
export const equal = (a, b) => {
  const x = Buffer.from(String(a)),
    y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
};
export function seal(key, value, aad = "") {
  const iv = crypto.randomBytes(12),
    c = crypto.createCipheriv("aes-256-gcm", key, iv);
  c.setAAD(Buffer.from(aad));
  const body = Buffer.concat([
    c.update(typeof value === "string" ? value : JSON.stringify(value), "utf8"),
    c.final(),
  ]);
  return [iv, c.getAuthTag(), body]
    .map((x) => x.toString("base64url"))
    .join(".");
}
export function unseal(key, value, aad = "") {
  const [iv, tag, body] = value
    .split(".")
    .map((x) => Buffer.from(x, "base64url"));
  const c = crypto.createDecipheriv("aes-256-gcm", key, iv);
  c.setAAD(Buffer.from(aad));
  c.setAuthTag(tag);
  return Buffer.concat([c.update(body), c.final()]).toString("utf8");
}
export function signToken(key, payload) {
  const p = Buffer.from(JSON.stringify(payload)).toString("base64url");
  return p + "." + hash(key, p);
}
export function verifyToken(key, token, audience, now = Date.now()) {
  const [p, sig, ...extra] = String(token || "").split(".");
  requireValue(
    p && sig && !extra.length && equal(hash(key, p), sig),
    "UNAUTHENTICATED",
    "请重新登录",
    401,
  );
  let value;
  try {
    value = JSON.parse(Buffer.from(p, "base64url").toString());
  } catch {
    requireValue(false, "UNAUTHENTICATED", "请重新登录", 401);
  }
  requireValue(
    value.aud === audience &&
      Number.isSafeInteger(value.exp) &&
      Number.isSafeInteger(value.iat) &&
      value.iat >= 0 &&
      value.exp > now &&
      value.iat <= now + 30000,
    "UNAUTHENTICATED",
    "登录已过期，请重新登录",
    401,
  );
  return value;
}
export function passwordHash(
  password,
  salt = crypto.randomBytes(16).toString("hex"),
) {
  return salt + ":" + crypto.scryptSync(password, salt, 32).toString("hex");
}
export function passwordMatches(password, stored) {
  const [salt, value] = String(stored || "").split(":");
  if (!salt || !value) return false;
  return equal(passwordHash(password, salt), stored);
}
export function canonical(value) {
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]";
  if (value && typeof value === "object")
    return (
      "{" +
      Object.keys(value)
        .sort()
        .map((k) => JSON.stringify(k) + ":" + canonical(value[k]))
        .join(",") +
      "}"
    );
  return JSON.stringify(value);
}
