import crypto from 'node:crypto';
import { promisify } from 'node:util';
import { equal } from '../security.mjs';
import { requireValue } from '../http/errors.mjs';

const scrypt = promisify(crypto.scrypt);
const options = { N: 32768, r: 8, p: 3, maxmem: 64 * 1024 * 1024 };
const prefix = 'scrypt$32768$8$3$';
export const dummyPassword = prefix + '00'.repeat(16) + '$' + '00'.repeat(32);
let running = 0;
const waiting = [];

export function normalizeHandle(value) {
  const handle = String(value || '').trim().toLowerCase();
  requireValue(/^[a-z0-9][a-z0-9_-]{2,31}$/.test(handle), 'INVALID_HANDLE', '账号需为3—32位字母、数字、下划线或短横线');
  return handle;
}
export function validatePassword(value) {
  requireValue(typeof value === 'string' && Array.from(value).length >= 12 && Array.from(value).length <= 128 && Buffer.byteLength(value) <= 512,
    'INVALID_PASSWORD', '密码需为12—128个字符，可以使用一句话');
  return value;
}
async function derive(password, salt) {
  requireValue(running < 2 || waiting.length < 6, 'AUTH_BUSY', '登录繁忙，请稍后重试', 503);
  if (running >= 2) await new Promise(resolve => waiting.push(resolve));
  else running++;
  try { return await scrypt(password, salt, 32, options); }
  finally { const next = waiting.shift(); if (next) next(); else running--; }
}
export async function createPassword(password) {
  validatePassword(password);
  const salt = crypto.randomBytes(16).toString('hex');
  return prefix + salt + '$' + (await derive(password, salt)).toString('hex');
}
export async function verifyPassword(password, stored) {
  requireValue(typeof password === 'string' && Buffer.byteLength(password) <= 512, 'INVALID_LOGIN', '账号或密码不正确', 401);
  const valid = /^scrypt\$32768\$8\$3\$[a-f0-9]{32}\$[a-f0-9]{64}$/.test(stored || '');
  const value = valid ? stored : dummyPassword;
  const pieces = value.split('$');
  const actual = (await derive(password, pieces[4])).toString('hex');
  return valid && equal(actual, pieces[5]);
}
