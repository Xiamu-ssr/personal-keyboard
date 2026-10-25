import test from 'node:test';
import assert from 'node:assert/strict';
import { providerFailure } from '../models/provider-error.mjs';

test('upstream balance, free-tier quota and throttling cannot be confused with user Credit', async () => {
  for (const [status, code, category] of [
    [403, 'Arrearage', 'PROVIDER_BALANCE'],
    [403, 'AllocationQuota.FreeTierOnly', 'PROVIDER_FREE_QUOTA'],
    [429, 'insufficient_quota', 'PROVIDER_QUOTA'],
    [429, 'Throttling.RateQuota', 'PROVIDER_LIMIT'],
    [403, 'AccessDenied.Unpurchased', 'PROVIDER_CONFIGURATION'],
  ]) {
    const error = await providerFailure(new Response(JSON.stringify({ error: { code, message: 'private body' }, request_id: 'safe-request-1' }), { status }));
    assert.equal(error.code, category);
    assert.equal(error.status, 502);
    assert.equal(error.provider.error.code, code);
    assert.equal(error.provider.error.requestId, 'safe-request-1');
    assert(!JSON.stringify(error).includes('private body'));
  }
});

test('non-JSON and oversized provider errors retain only safe diagnostic identifiers', async () => {
  assert.equal((await providerFailure(new Response('<html>failure</html>', { status: 500 }))).code, 'PROVIDER_ERROR');
  const error = await providerFailure(new Response(JSON.stringify({ error: { code: 'x'.repeat(20000) } }), { status: 500 }));
  assert.deepEqual(error.provider.error, { httpStatus: 500, code: '', requestId: '' });
});
