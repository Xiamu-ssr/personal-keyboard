// Reference accounting domain. Production persistence must commit these state
// transitions in database transactions under the account's balance lock.
export function billableCharacters(text) {
  if (typeof text !== "string") throw Error("Final ASR text required");
  return Array.from(text.normalize("NFC")).filter((c) => !/\s/u.test(c)).length;
}

export class EntitlementLedger {
  #grants = new Map();
  #jobs = new Map();

  grantOnce({ accountId, grantId, plan, proof }) {
    if (!accountId || !grantId)
      throw Error("Account and grant identity required");
    if (plan.status !== "published" || !plan.limits)
      throw Error("Published plan required");
    if (plan.grantMode === "verified_purchase" && proof?.verified !== true)
      throw Error("Verified payment receipt required");
    const key =
      plan.grantMode === "once_per_account"
        ? `${accountId}:welcome`
        : `${accountId}:${grantId}`;
    const existing = this.#grants.get(key);
    if (existing) return structuredClone(existing);
    for (const value of Object.values(plan.limits))
      if (!Number.isSafeInteger(value) || value < 0)
        throw Error("Invalid entitlement limit");
    const grant = { accountId, grantId, plan: structuredClone(plan), used: {} };
    this.#grants.set(key, grant);
    return structuredClone(grant);
  }

  balance(accountId, meter) {
    const grants = [...this.#grants.values()].filter(
      (g) => g.accountId === accountId,
    );
    const total = grants.reduce((n, g) => n + (g.plan.limits[meter] || 0), 0);
    const used = grants.reduce((n, g) => n + (g.used[meter] || 0), 0);
    const reserved = [...this.#jobs.values()]
      .filter(
        (j) =>
          j.accountId === accountId &&
          j.meter === meter &&
          j.status === "reserved",
      )
      .reduce((n, j) => n + j.maximum, 0);
    return { total, used, reserved, available: total - used - reserved };
  }

  reserve({ accountId, requestId, meter, maximum }) {
    if (!accountId || !requestId)
      throw Error("Account and request identity required");
    if (!["asr_characters", "advisor_generations"].includes(meter))
      throw Error("Unknown meter");
    if (!Number.isSafeInteger(maximum) || maximum <= 0)
      throw Error("Positive reservation required");
    const key = `${accountId}:${requestId}`,
      existing = this.#jobs.get(key);
    if (existing) {
      if (existing.meter !== meter || existing.maximum !== maximum)
        throw Error("Idempotency key payload conflict");
      return structuredClone(existing);
    }
    if (this.balance(accountId, meter).available < maximum)
      throw Error("Entitlement exhausted");
    const job = { accountId, requestId, meter, maximum, status: "reserved" };
    this.#jobs.set(key, job);
    return structuredClone(job);
  }

  settle({ accountId, requestId, amount, outcome }) {
    const job = this.#jobs.get(`${accountId}:${requestId}`);
    if (!job) throw Error("Reservation not found");
    if (!["success", "failed", "cancelled"].includes(outcome))
      throw Error("Invalid outcome");
    if (job.status !== "reserved") {
      if (job.outcome !== outcome || job.amount !== amount)
        throw Error("Terminal result conflict");
      return structuredClone(job);
    }
    if (!Number.isSafeInteger(amount) || amount < 0 || amount > job.maximum)
      throw Error("Settlement exceeds reservation");
    if (outcome !== "success" && amount !== 0)
      throw Error("Failures cannot consume allowance");
    if (
      job.meter === "advisor_generations" &&
      outcome === "success" &&
      amount !== 1
    )
      throw Error("One successful generation consumes one unit");
    let remaining = amount;
    for (const grant of this.#grants.values()) {
      if (grant.accountId !== accountId) continue;
      const used = grant.used[job.meter] || 0;
      const take = Math.min(
        remaining,
        (grant.plan.limits[job.meter] || 0) - used,
      );
      grant.used[job.meter] = used + take;
      remaining -= take;
      if (!remaining) break;
    }
    if (remaining) throw Error("Inconsistent grant ledger");
    Object.assign(job, {
      status: outcome === "success" ? "settled" : "released",
      outcome,
      amount,
    });
    return structuredClone(job);
  }

  canSync(accountId) {
    return [...this.#grants.values()].some(
      (g) => g.accountId === accountId && g.plan.features.cloud_sync === true,
    );
  }
}
