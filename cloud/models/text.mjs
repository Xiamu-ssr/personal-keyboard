import fs from "node:fs/promises";
import { requireValue, ServiceError } from "../http/errors.mjs";
import { digest } from "../security.mjs";
const bounded = (s, max, name = "文字") => {
  requireValue(
    typeof s === "string" && s.length <= max,
    "INVALID_INPUT",
    name + "过长或格式错误",
  );
  return s;
};
const strings = (a, max = 50, len = 300) => {
  requireValue(
    Array.isArray(a) && a.length <= max,
    "INVALID_INPUT",
    "资料数量过多",
  );
  return a.map((s) => bounded(s, len));
};
export function modelInput(kind, raw) {
  requireValue(
    raw && typeof raw === "object" && !Array.isArray(raw),
    "INVALID_INPUT",
    "缺少任务内容",
  );
  const memory = {
    words: strings(raw.words || []),
    styles: strings(raw.styles || [], 12, 600),
  };
  if (kind === "rewrite")
    return { ...memory, transcript: bounded(raw.transcript, 4000) };
  if (kind === "advisor") {
    const input = {
      ...memory,
      context: bounded(raw.context || "", 16000),
      intent: bounded(raw.intent || "", 1000),
      style: bounded(raw.style || "", 40),
      contactMemories: strings(raw.contactMemories || [], 30, 600),
      subject: bounded(raw.subject || "", 120),
      imageDigests: [],
    };
    const images = raw.images || [];
    requireValue(
      Array.isArray(images) && images.length <= 3,
      "INVALID_IMAGE",
      "最多三张聊天图片",
    );
    let size = 0;
    for (const img of images) {
      requireValue(
        typeof img === "string" &&
          /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(img),
        "INVALID_IMAGE",
        "仅支持本次 JPEG 图片",
      );
      const b = Buffer.from(img.split(",")[1], "base64");
      size += b.length;
      requireValue(
        b.length > 16 && b[0] === 255 && b[1] === 216 && size <= 1800000,
        "INVALID_IMAGE",
        "图片总量须小于 1.8MB",
      );
      input.imageDigests.push(digest(b));
    }
    requireValue(
      input.context.trim() || images.length,
      "EMPTY_CONTEXT",
      "请先读取聊天内容",
    );
    return input;
  }
  if (kind === "memory") {
    requireValue(
      Array.isArray(raw.events) &&
        raw.events.length > 0 &&
        raw.events.length <= 24,
      "INVALID_INPUT",
      "每批最多整理24条新增历史",
    );
    const seen = new Set();
    const events = raw.events.map((e) => {
      const id = bounded(e.id, 100);
      requireValue(id && !seen.has(id), "INVALID_INPUT", "来源标识重复");
      seen.add(id);
      return {
        id,
        text: bounded(e.text, 1600),
        kind: bounded(e.kind || "history", 40),
        subject: bounded(e.subject || "", 100),
        state: bounded(e.state || "unconfirmed", 40),
      };
    });
    const old = raw.memories || [];
    requireValue(
      Array.isArray(old) && old.length <= 30,
      "INVALID_INPUT",
      "旧记忆过多",
    );
    const memories = old.map((m) => ({
      id: bounded(m.id, 100),
      content: bounded(m.content, 500),
      revision: Number.isSafeInteger(m.revision) ? m.revision : 0,
      scope: bounded(m.scope || "self", 100),
    }));
    const input = { events, memories };
    requireValue(
      JSON.stringify(input).length <= 16000,
      "INPUT_TOO_LARGE",
      "请分批整理新增历史",
    );
    return input;
  }
  throw new ServiceError("INVALID_KIND", "未知任务类型");
}
const jsonObject = (text) => {
  try {
    return JSON.parse(
      text
        .trim()
        .replace(/^```(?:json)?\s*/, "")
        .replace(/\s*```$/, ""),
    );
  } catch {
    throw new ServiceError(
      "MODEL_FORMAT",
      "模型结果格式不正确，预留将释放",
      502,
    );
  }
};
export function validateModelResult(kind, text, input) {
  const r = jsonObject(text);
  if (kind === "rewrite") {
    requireValue(
      typeof r.text === "string" && r.text.trim() && r.text.length <= 6000,
      "MODEL_FORMAT",
      "整理结果无效",
      502,
    );
    return {
      text: r.text.trim(),
      changed: r.text !== input.transcript,
      uncertainSpans: strings(r.uncertainSpans || [], 20, 200),
    };
  }
  if (kind === "advisor") {
    requireValue(
      Array.isArray(r.replies) && r.replies.length === 3,
      "MODEL_FORMAT",
      "模型未返回三条回复",
      502,
    );
    const replies = r.replies.map((reply, i) => ({
      style: ["温和", "直接", "轻松"][i],
      text: bounded(typeof reply === "string" ? reply : reply.text, 1000),
    }));
    requireValue(
      replies.every((x) => x.text.trim()),
      "MODEL_FORMAT",
      "回复不能为空",
      502,
    );
    return { replies, uncertainty: strings(r.uncertainty || [], 10, 200) };
  }
  requireValue(
    Array.isArray(r.proposals) && r.proposals.length <= 20,
    "MODEL_FORMAT",
    "学习结果格式不正确",
    502,
  );
  const sources = new Set(input.events.map((e) => e.id));
  const proposals = r.proposals.map((p) => {
    requireValue(
      [
        "vocabulary_correction",
        "personal_style",
        "contact_style",
        "factual_edit",
        "ignore",
      ].includes(p.category),
      "MODEL_FORMAT",
      "未知记忆分类",
      502,
    );
    requireValue(
      ["self", "contact"].includes(p.scope) &&
        Array.isArray(p.sourceIds) &&
        p.sourceIds.length > 0 &&
        p.sourceIds.length <= 8 &&
        p.sourceIds.every((id) => sources.has(id)),
      "MODEL_SCOPE",
      "记忆引用了本批以外的来源",
      502,
    );
    const subject = bounded(p.subject || "", 100);
    requireValue(
      p.scope === "self" ||
        (subject &&
          input.events.some(
            (e) => e.subject === subject && p.sourceIds.includes(e.id),
          )),
      "MODEL_SCOPE",
      "人物来源不匹配",
      502,
    );
    requireValue(
      Number.isFinite(p.confidence) &&
        p.confidence >= 0 &&
        p.confidence <= 1 &&
        Number.isSafeInteger(p.baseRevision) &&
        p.baseRevision >= 0,
      "MODEL_FORMAT",
      "记忆置信度或版本错误",
      502,
    );
    return {
      category: p.category,
      scope: p.scope,
      subject,
      content: bounded(p.content, 500),
      sourceIds: p.sourceIds,
      baseRevision: p.baseRevision,
      confidence: p.confidence,
      reason: bounded(p.reason || "", 400),
    };
  });
  return { proposals };
}
export class TextModels {
  constructor(cfg, fetcher = fetch) {
    this.cfg = cfg;
    this.fetcher = fetcher;
    this.prompts = new Map();
  }
  async init() {
    for (const [kind, file] of Object.entries({
      advisor: "reply",
      rewrite: "rewrite",
      memory: "learn",
    })) {
      const text = await fs.readFile(
        new URL("../prompts/" + file + ".md", import.meta.url),
        "utf8",
      );
      this.prompts.set(kind, { text, revision: digest(text).slice(0, 12) });
    }
  }
  async run(kind, input, images, signal) {
    requireValue(
      this.cfg.textReady,
      "MODEL_NOT_READY",
      "云端文字模型尚未配置",
      503,
    );
    const visual = images?.length > 0;
    requireValue(
      !visual || this.cfg.visionReady,
      "VISION_NOT_READY",
      "云端图文模型尚未配置",
      503,
    );
    const prompt = this.prompts.get(kind),
      model = visual ? this.cfg.visionModel : this.cfg.textModel;
    const user = visual
      ? [
          { type: "text", text: JSON.stringify(input) },
          ...images.map((url) => ({ type: "image_url", image_url: { url } })),
        ]
      : JSON.stringify(input);
    let response;
    try {
      response = await this.fetcher(
        this.cfg.textBase.replace(/\/$/, "") + "/chat/completions",
        {
          method: "POST",
          signal: AbortSignal.any([signal, AbortSignal.timeout(65000)]),
          headers: {
            Authorization: "Bearer " + this.cfg.modelKey,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            model,
            messages: [
              {
                role: "system",
                content: prompt.text + "\n请严格只输出 JSON 对象。",
              },
              { role: "user", content: user },
            ],
            max_tokens: kind === "memory" ? 2400 : 1600,
            temperature: kind === "rewrite" ? 0.2 : 0.6,
            response_format: { type: "json_object" },
            enable_thinking: false,
          }),
        },
      );
    } catch (e) {
      if (signal.aborted)
        throw new ServiceError("CANCELLED", "任务已取消", 409);
      throw new ServiceError("MODEL_NETWORK", "模型连接失败，请稍后重试", 502);
    }
    if (!response.ok) {
      await response.body?.cancel();
      throw new ServiceError(
        response.status === 429 ? "PROVIDER_LIMIT" : "PROVIDER_ERROR",
        response.status === 429
          ? "供应商限流，请稍后重试"
          : "供应商调用失败，预留已释放",
        502,
      );
    }
    let size = 0,
      chunks = [];
    for await (const b of response.body) {
      size += b.length;
      requireValue(size <= 300000, "MODEL_FORMAT", "模型返回过大", 502);
      chunks.push(b);
    }
    let output;
    try {
      output = JSON.parse(Buffer.concat(chunks).toString());
    } catch {
      throw new ServiceError("MODEL_FORMAT", "模型返回格式不正确", 502);
    }
    requireValue(
      output.choices?.[0]?.finish_reason !== "length",
      "MODEL_TRUNCATED",
      "模型结果被截断，请重试",
      502,
    );
    const result = validateModelResult(
      kind,
      output.choices?.[0]?.message?.content || "",
      input,
    );
    return {
      result,
      provider: {
        model,
        usage: { ...output.usage, promptRevision: prompt.revision },
      },
    };
  }
}
