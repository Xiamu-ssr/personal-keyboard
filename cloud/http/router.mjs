import { ServiceError, requireValue } from "./errors.mjs";
export async function jsonBody(req, maximum = 100000) {
  requireValue(
    (req.headers["content-type"] || "").startsWith("application/json"),
    "CONTENT_TYPE",
    "请发送 JSON 请求",
    415,
  );
  let total = 0,
    chunks = [];
  for await (const b of req) {
    total += b.length;
    requireValue(total <= maximum, "TOO_LARGE", "请求内容过大", 413);
    chunks.push(b);
  }
  try {
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    requireValue(body && typeof body === 'object' && !Array.isArray(body), 'INVALID_JSON', '请发送 JSON 对象');
    return body;
  } catch {
    throw new ServiceError("INVALID_JSON", "请求内容格式错误");
  }
}
export function json(res, value, status = 200) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(value));
}
export function securityHeaders(res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader(
    "Content-Security-Policy",
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
  );
}
export function failure(res, e) {
  if (res.writableEnded) return;
  json(
    res,
    {
      error: {
        code: e instanceof ServiceError ? e.code : "INTERNAL_ERROR",
        message:
          e instanceof ServiceError ? e.message : "服务暂不可用，请稍后重试",
      },
    },
    e instanceof ServiceError ? e.status : 500,
  );
}
export const bearer = (req) =>
  /^Bearer (.+)$/i.exec(req.headers.authorization || "")?.[1];
export const cookie = (req) =>
  /keyboard_admin=([^;]+)/.exec(req.headers.cookie || "")?.[1];
export function sameOrigin(req, cfg) {
  const origin = req.headers.origin;
  if (origin)
    requireValue(
      origin === new URL(cfg.publicBase).origin,
      "ORIGIN_DENIED",
      "来源不受信任",
      403,
    );
}
