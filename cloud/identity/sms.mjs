import crypto from "node:crypto";
import { digest, uuid } from "../security.mjs";
import { requireValue, ServiceError } from "../http/errors.mjs";
const encode = (s) =>
  encodeURIComponent(s).replace(
    /[!'()*]/g,
    (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase(),
  );
export function smsRequest(cfg, phone, code, now = new Date(), nonce = uuid()) {
  const query = Object.entries({
    PhoneNumbers: phone.replace(/^\+86/, ""),
    SignName: cfg.smsSignature,
    TemplateCode: cfg.smsTemplate,
    TemplateParam: JSON.stringify({ code }),
  })
    .sort(([a], [b]) => a.localeCompare(b, "en"))
    .map(([k, v]) => encode(k) + "=" + encode(v))
    .join("&");
  const headers = {
    host: "dysmsapi.aliyuncs.com",
    "x-acs-action": "SendSms",
    "x-acs-version": "2017-05-25",
    "x-acs-date": now.toISOString().replace(/\.\d{3}Z$/, "Z"),
    "x-acs-signature-nonce": nonce,
    "x-acs-content-sha256": digest(""),
  };
  if (cfg.smsToken) headers["x-acs-security-token"] = cfg.smsToken;
  const names = Object.keys(headers).sort(),
    signed = names.join(";"),
    canonicalHeaders = names
      .map((k) => k + ":" + headers[k].trim() + "\n")
      .join("");
  const canonical = [
    "POST",
    "/",
    query,
    canonicalHeaders,
    signed,
    digest(""),
  ].join("\n");
  const signature = crypto
    .createHmac("sha256", cfg.smsKeySecret)
    .update("ACS3-HMAC-SHA256\n" + digest(canonical))
    .digest("hex");
  headers.Authorization = `ACS3-HMAC-SHA256 Credential=${cfg.smsKeyId},SignedHeaders=${signed},Signature=${signature}`;
  return {
    url: "https://dysmsapi.aliyuncs.com/?" + query,
    options: { method: "POST", headers, signal: AbortSignal.timeout(10000) },
  };
}
export class AliyunSms {
  constructor(cfg, fetcher = fetch) {
    this.cfg = cfg;
    this.fetcher = fetcher;
  }
  async send(phone, code) {
    requireValue(
      this.cfg.smsReady,
      "SMS_NOT_READY",
      "短信资质、签名或模板尚未配置",
      503,
    );
    const req = smsRequest(this.cfg, phone, code);
    let r;
    // SendSms has no provider idempotency. Never automatically resend on timeout.
    try {
      r = await this.fetcher(req.url, req.options);
      const out = await r.json();
      if (r.ok && out.Code === "OK") return;
    } catch {}
    throw new ServiceError("SMS_UNAVAILABLE", "短信暂未发出，请稍后重试", 503);
  }
}
