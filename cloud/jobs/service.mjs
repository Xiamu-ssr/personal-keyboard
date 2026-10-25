import { requestedSlots } from '../advisor/palette.mjs';
import { modelInput } from "../models/text.mjs";
import { unseal } from "../security.mjs";
import { isUuid } from "../billing/ledger.mjs";
import { requireValue, ServiceError } from "../http/errors.mjs";
export class Jobs {
  constructor(db, ledger, models, cfg, memory, palettes) {
    Object.assign(this, { db, ledger, models, cfg, memory, palettes });
    this.images = new Map();
    this.controllers = new Map();
    this.running = 0;
    this.stopped = false;
  }
  async submit(user, request) {
    requireValue(
      ["rewrite", "advisor", "translation"].includes(request.kind),
      "INVALID_KIND",
      "未知模型任务",
    );
    requireValue(
      this.cfg.textReady,
      "MODEL_NOT_READY",
      "云端文字模型尚未配置",
      503,
    );
    const input = modelInput(request.kind, request.input);
    const options = { hasImages: input.imageDigests?.length > 0, sourceJob: request.sourceJob || null };
    const existing = await this.ledger.existing(user.id, request.requestId, request.kind, input, options);
    if (existing) return this.ledger.view(existing);
    const prepared = await this.models.prepare(request.kind, await this.taskInput(user.id, request.kind, input));
    const quote = { model: prepared.model, inputTokens: prepared.tokens + (input.imageDigests?.length || 0) * 4096, outputTokens: prepared.outputLimit,
      ...(request.kind === 'advisor' ? { replyCount: input.slotId ? 3 : 6 } : {}) };
    const { job, created } = await this.ledger.reserve(user.id, request.requestId, request.kind, input,
      { ...options, quote, execution: prepared });
    if (created && input.imageDigests?.length)
      this.images.set(job.id, request.input.images);
    if (created) this.pump();
    return this.ledger.view(job);
  }
  async byRequest(user, id) {
    requireValue(isUuid(id), 'INVALID_REQUEST', '请求标识无效');
    const row = (await this.db.query("SELECT * FROM jobs WHERE user_id=$1 AND request_id=$2 AND kind='asr'", [user.id,id])).rows[0];
    return row ? this.ledger.view(row) : { status: 'not_found' };
  }
  async get(user, id) {
    requireValue(isUuid(id), "INVALID_REQUEST", "任务标识无效");
    const j = (
      await this.db.query("SELECT * FROM jobs WHERE id=$1 AND user_id=$2", [
        id,
        user.id,
      ])
    ).rows[0];
    requireValue(j, "NOT_FOUND", "任务不存在", 404);
    return this.ledger.view(j);
  }
  async cancel(user, id) {
    await this.get(user, id);
    this.controllers.get(id)?.abort();
    this.images.delete(id);
    return this.ledger.view(
      await this.ledger.finish(
        id,
        "cancelled",
        null,
        {},
        { code: "CANCELLED", message: "已取消，预留已释放" },
      ),
    );
  }
  async pump() {
    if (this.stopped || this.pumping) return;
    this.pumping = true;
    try {
      while (this.running < this.cfg.workerConcurrency && !this.stopped) {
        const job = await this.db.transaction(
          async (c) =>
            (
              await c.query(
                "UPDATE jobs SET status='running',started_at=now() WHERE id=(SELECT id FROM jobs WHERE status='queued' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *",
              )
            ).rows[0],
        );
        if (!job) break;
        this.running++;
        void this.run(job).finally(() => {
          this.running--;
          this.pump();
        });
      }
    } catch {
      console.error("Job queue unavailable");
    } finally {
      this.pumping = false;
    }
  }
  async run(job) {
    const controller = new AbortController();
    this.controllers.set(job.id, controller);
    try {
      const input = JSON.parse(
        unseal(this.cfg.masterKey, job.input_cipher, job.id),
      );
      const images = this.images.get(job.id) || [];
      requireValue(
        !job.has_images || images.length,
        "IMAGE_EXPIRED",
        "图片已释放，请重新读屏",
      );
      const { result, provider } = await this.models.execute(
        input,
        images,
        controller.signal,
      );
      await this.ledger.finish(job.id, "succeeded", result, provider);
    } catch (e) {
      await this.ledger
        .finish(
          job.id,
          controller.signal.aborted ? "cancelled" : "failed",
          null,
          e.provider ? { model: e.providerModel, usage: e.provider } : {},
          {
            code: e instanceof ServiceError ? e.code : "MODEL_ERROR",
            message:
              e instanceof ServiceError ? e.message : "任务失败，预留已释放",
          },
        )
        .catch(() => console.error("Job settlement pending"));
    } finally {
      this.images.delete(job.id);
      this.controllers.delete(job.id);
    }
  }
  async taskInput(user, kind, input) {
    if (kind === 'translation') return input;
    const prepared = await this.memory.forTask(user, kind, input);
    if (kind === 'advisor') prepared.slots = requestedSlots(await this.palettes.get(user), input.slotId);
    return prepared;
  }
  async stop() {
    this.stopped = true;
    for (const c of this.controllers.values()) c.abort();
    while (this.running || this.pumping)
      await new Promise((r) => setTimeout(r, 50));
  }
}
