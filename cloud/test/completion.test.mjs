import test from 'node:test';
import assert from 'node:assert/strict';
import { Jobs } from '../jobs/service.mjs';

function fixture(read) {
  const jobs = new Jobs(null, null, null, {}, null, null);
  jobs.get = read;
  return jobs;
}

test('completion is delivered without a polling interval, including completion during the first read', async () => {
  let reads = 0;
  const jobs = fixture(async () => {
    if (++reads === 1) {
      jobs.completion.publish('owned');
      return { status: 'running' };
    }
    return { status: 'succeeded', result: 'complete' };
  });
  const result = await jobs.wait({}, 'owned', 20000);
  assert.equal(result.result, 'complete');
  assert.equal(reads, 2);
  assert.equal(jobs.completion.waiters.size, 0);
});

test('timeout re-reads durable state and disconnect removes the subscription', async () => {
  const jobs = fixture(async () => ({ status: 'running' }));
  assert.equal((await jobs.wait({}, 'owned', 5)).status, 'running');
  assert.equal(jobs.completion.waiters.size, 0);
  const abort = new AbortController();
  const waiting = jobs.wait({}, 'owned', 20000, abort.signal);
  abort.abort();
  await waiting;
  assert.equal(jobs.completion.waiters.size, 0);
});

test('unowned jobs fail before waiting and terminal jobs return immediately', async () => {
  const jobs = fixture(async () => { throw new Error('not owned'); });
  await assert.rejects(jobs.wait({}, 'other', 20000), /not owned/);
  assert.equal(jobs.completion.waiters.size, 0);
  jobs.get = async () => ({ status: 'cancelled' });
  assert.equal((await jobs.wait({}, 'owned', 20000)).status, 'cancelled');
  assert.equal(jobs.completion.waiters.size, 0);
  await assert.rejects(jobs.wait({}, 'owned', 20001), /等待时长/);
});
