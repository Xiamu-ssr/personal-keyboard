import WebSocket from "ws";
import { uuid } from "../security.mjs";
import { requireValue } from "../http/errors.mjs";
import { billableCharacters } from "../billing/ledger.mjs";
import { recognitionVocabulary } from '../memory/quality.mjs';
export function asrInput(raw) {
  requireValue(raw.words === undefined, 'INVALID_INPUT', '热词由云端个人记忆提供');
  requireValue(
    typeof (raw.context || "") === "string" &&
      (raw.context || "").length <= 400,
    "INVALID_INPUT",
    "近期上下文过长",
  );
  return { context: raw.context || "", polish: raw.polish !== false };
}
export function limitText(text, maximum) {
  let count = 0,
    out = "";
  for (const ch of text.normalize("NFC")) {
    if (!/\s/u.test(ch) && ++count > maximum) break;
    out += ch;
  }
  return out.trim();
}
export function connectAsr(
  client,
  user,
  {
    cfg,
    ledger,
    accounts,
    memory,
    makeSocket = (url, opts) => new WebSocket(url, opts),
  },
) {
  let job = null,
    provider = null,
    started = false,
    finishing = false,
    terminal = false,
    bytes = 0,
    maximumSeconds = cfg.maxAsrSeconds,
    limited = false,
    configured = false,
    owns = false;
  let ping;
  let task = uuid(),
    partial = "",
    usage = {},
    finals = new Map(),
    timers = new Set();
  const timer = (fn, ms) => {
    const t = setTimeout(fn, ms);
    timers.add(t);
    return t;
  };
  const send = (value) => {
    if (client.readyState === WebSocket.OPEN)
      client.send(JSON.stringify(value));
  };
  const text = () => [...finals.values(), partial].filter(Boolean).join("");
  const clean = () => {
    clearInterval(ping);
    for (const t of timers) clearTimeout(t);
    timers.clear();
    provider?.close();
  };
  const end = async (outcome, result, error) => {
    if (terminal) return;
    terminal = true;
    clean();
    try {
      if (job && owns) {
        const done = await ledger.finish(
          job.id,
          outcome,
          result,
          { model: cfg.asrModel, usage },
          error,
        );
        send({ type: "job", ...ledger.view(done) });
      } else if (error) send({ type: "error", error });
    } catch {
      send({
        type: "error",
        error: {
          code: "SETTLEMENT_PENDING",
          message: "结果正在对账，请查看任务记录",
        },
      });
    }
    client.close();
  };
  const fail = (code, message) => void end("failed", null, { code, message });
  const finish = () => {
    if (finishing || terminal) return;
    finishing = true;
    if (!started) return fail("ASR_NOT_STARTED", "语音连接尚未就绪");
    provider.send(
      JSON.stringify({
        header: { action: "finish-task", task_id: task, streaming: "duplex" },
        payload: { input: {} },
      }),
    );
    timer(() => fail("ASR_TIMEOUT", "供应商未返回最终结果，预留已释放"), 15000);
  };
  const setupTimer = timer(() => fail("CONFIG_TIMEOUT", "语音连接超时"), 10000);
  client.on("message", (data, binary) => {
    if (terminal) return;
    if (binary) {
      if (!started || finishing) return;
      bytes += data.length;
      if (data.length > 6400) return fail("AUDIO_LIMIT", "音频分片过大");
      if (bytes > maximumSeconds * 32000) { limited = true; finish(); return; }
      if (provider.bufferedAmount > 256000)
        return fail("ASR_BACKPRESSURE", "网络较慢，请重试短一些的语音");
      provider.send(data);
      return;
    }
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return fail("INVALID_INPUT", "语音协议消息格式错误");
    }
    if (msg.type === "cancel") {
      void end("cancelled", null, {
        code: "CANCELLED",
        message: "已取消，预留已释放",
      });
      return;
    }
    if (msg.type === "finish") {
      finish();
      return;
    }
    if (msg.type !== "configure" || configured)
      return fail("INVALID_INPUT", "语音配置重复或无效");
    configured = true;
    clearTimeout(setupTimer);
    void (async () => {
      requireValue(cfg.asrReady, "ASR_NOT_READY", "云端实时语音尚未配置", 503);
      const input = asrInput(msg);
      const vocabulary = recognitionVocabulary((await memory.snapshot(user.id)).memory, input.context);
      const quote = { model: cfg.asrModel, asr: { maxSeconds: cfg.maxAsrSeconds,
        baseTokens: 256 + Buffer.byteLength(JSON.stringify({ context: input.context, vocabulary })) } };
      const reserved = await ledger.reserve(user.id, msg.requestId, 'asr', input, { quote });
      job = reserved.job;
      owns = reserved.created;
      if (terminal) {
        if (owns)
          await ledger.finish(
            job.id,
            "cancelled",
            null,
            {},
            { code: "CANCELLED", message: "连接已结束" },
          );
        return;
      }
      if (!owns) {
        send({ type: "job", ...ledger.view(job) });
        terminal = true;
        clean();
        client.close();
        return;
      }
      maximumSeconds = job.billing_snapshot.maxAsrSeconds;
      send({
        type: "accepted",
        jobId: job.id,
        maximumCharacters: cfg.maxAsrCharacters,
        maximumSeconds,
      });
      provider = makeSocket(
        `wss://${cfg.workspace}.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference`,
        {
          headers: { Authorization: "Bearer " + cfg.modelKey },
          handshakeTimeout: 10000,
          maxPayload: 256000,
        },
      );
      timer(() => {
        if (!started) fail("ASR_START_TIMEOUT", "供应商启动超时");
      }, 15000);
      provider.on("open", () => {
        if (terminal) return;
        provider.send(
          JSON.stringify({
            header: { action: "run-task", task_id: task, streaming: "duplex" },
            payload: {
              task_group: "audio",
              task: "asr",
              function: "recognition",
              model: cfg.asrModel,
              parameters: {
                format: "pcm",
                sample_rate: 16000,
                intermediate_result_enabled: true,
                disfluency_removal_enabled: input.polish,
                vad_model: "near_meeting_16k",
                vocabulary,
              },
              input: input.context
                ? {
                    context: [
                      {
                        role: "user",
                        content: [{ type: "input_text", text: input.context }],
                      },
                    ],
                  }
                : {},
            },
          }),
        );
      });
      provider.on("message", (data) => {
        if (terminal) return;
        let m;
        try {
          m = JSON.parse(data.toString());
        } catch {
          return fail("ASR_FORMAT", "供应商语音协议错误");
        }
        const event = m.header?.event;
        if (event === "task-started") {
          started = true;
          send({ type: "ready", jobId: job.id });
          timer(() => { limited = true; finish(); }, maximumSeconds * 1000);
        }
        if (event === "task-failed")
          fail("ASR_PROVIDER_ERROR", "语音供应商拒绝了本次任务，预留已释放");
        if (m.payload?.usage) usage = m.payload.usage;
        const sentence = m.payload?.output?.sentence;
        if (sentence && typeof sentence.text === "string") {
          if (sentence.sentence_end) {
            finals.set(
              String(
                sentence.sentence_id ?? sentence.begin_time ?? finals.size,
              ),
              sentence.text,
            );
            partial = "";
          } else partial = sentence.text;
          send({ type: "partial", text: text() });
          if (billableCharacters(text()) >= cfg.maxAsrCharacters) { limited = true; finish(); }
        }
        if (event === "task-finished") {
          const final = [...finals.values()].join("").trim();
          if (!final)
            return fail("NO_SPEECH", "没有得到有效语音，请重新说一次");
          void end("succeeded", {
            text: final,
            limited,
          });
        }
      });
      provider.on("error", () =>
        fail("ASR_NETWORK", "语音供应商连接失败，预留已释放"),
      );
      provider.on("close", () => {
        if (!terminal) fail("ASR_DISCONNECTED", "语音连接提前结束，预留已释放");
      });
    })().catch((e) =>
      fail(e.code || "ASR_ERROR", e.code ? e.message : "语音连接失败"),
    );
  });
  client.on("close", () => {
    if (!terminal)
      void end("cancelled", null, {
        code: "DISCONNECTED",
        message: "连接中断，预留已释放",
      });
  });
  client.on("error", () => {
    if (!terminal)
      void end("cancelled", null, {
        code: "DISCONNECTED",
        message: "连接中断，预留已释放",
      });
  });
  ping = setInterval(() => {
    if (terminal) {
      clearInterval(ping);
      return;
    }
    if (client.readyState === WebSocket.OPEN) client.ping();
    void accounts
      .authenticate(user.accessToken)
      .catch(() => fail("UNAUTHENTICATED", "登录已结束"));
  }, 20000);
}
