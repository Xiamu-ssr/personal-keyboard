import fs from "node:fs/promises";
import path from "node:path";
import { requireValue } from "./http/errors.mjs";
export async function configuration(env = process.env) {
  const plans = JSON.parse(
    await fs.readFile(new URL("./config/plans.json", import.meta.url), "utf8"),
  );
  const providers = JSON.parse(
    await fs.readFile(
      new URL("./config/providers.json", import.meta.url),
      "utf8",
    ),
  );
  const pricing = JSON.parse(await fs.readFile(new URL('./config/credits.json', import.meta.url), 'utf8'));
  const cfg = {
    pricing,
    host: env.HOST || "127.0.0.1",
    port: Number(env.PORT || 8080),
    databaseUrl: env.DATABASE_URL,
    publicBase: env.PUBLIC_BASE_URL || "https://106.14.88.147/keyboard-api",
    pathPrefix: env.PATH_PREFIX || "",
    masterKey: env.MASTER_KEY ? Buffer.from(env.MASTER_KEY, "hex") : null,
    tokenKey: env.TOKEN_SECRET,
    adminPasswordHash: env.ADMIN_PASSWORD_HASH || "",
    trustProxy: env.TRUST_PROXY === "1",
    workspace: env.BAILIAN_WORKSPACE_ID || "",
    modelKey: env.BAILIAN_API_KEY || "",
    textBase: env.BAILIAN_TEXT_BASE_URL || "",
    asrModel: providers.asr.routes[providers.asr.default].model,
    textModel: env.TEXT_MODEL || providers.text.default,
    visionModel: env.VISION_MODEL || providers.text.vision,
    storageRoot: path.resolve(env.STORAGE_ROOT || ".runtime/cloud-sync"),
    jobTtlHours: 24,
    accessMs: 15 * 60_000,
    refreshMs: 30 * 24 * 3600_000,
    maxAsrSeconds: 90,
    maxAsrCharacters: 2000,
    maxJobs: 3,
    workerConcurrency: 2,
    maxTextQueue: 16,
    memoryMaxInputTokens: Number(env.MEMORY_MAX_INPUT_TOKENS || providers.text.maxInputTokens),
    memoryContextTokens: Number(env.MEMORY_CONTEXT_TOKENS || providers.text.contextTokens),
    memoryOutputTokens: Number(env.MEMORY_OUTPUT_TOKENS || 8192),
    plans,
    wechat: {
      appId: env.WECHAT_APP_ID || "",
      merchantId: env.WECHAT_MERCHANT_ID || "",
      apiV3Key: env.WECHAT_API_V3_KEY || "",
      platformCert: env.WECHAT_PLATFORM_CERT || "",
    },
  };
  requireValue(
    cfg.databaseUrl,
    "CONFIGURATION",
    "DATABASE_URL is required",
    503,
  );
  requireValue(
    cfg.masterKey?.length === 32 && cfg.tokenKey?.length >= 32,
    "CONFIGURATION",
    "Server encryption and token keys are required",
    503,
  );
  requireValue(
    new URL(cfg.publicBase).protocol === "https:",
    "CONFIGURATION",
    "Public endpoint must use HTTPS",
    503,
  );
  cfg.asrReady = !!(cfg.modelKey && cfg.workspace);
  cfg.textReady = !!(cfg.modelKey && cfg.textBase && cfg.textModel);
  cfg.visionReady = cfg.textReady && !!cfg.visionModel;
  for (const key of ['memoryMaxInputTokens', 'memoryContextTokens', 'memoryOutputTokens'])
    requireValue(Number.isSafeInteger(cfg[key]) && cfg[key] > 0, 'CONFIGURATION', 'Memory model capacity is invalid', 503);
  return cfg;
}
