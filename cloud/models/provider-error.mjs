import { ServiceError } from '../http/errors.mjs';

/** Keep only bounded provider identifiers; never persist response bodies, prompts or credentials. */
export async function providerFailure(response) {
  let value = {};
  try {
    const chunks = []; let bytes = 0;
    for await (const chunk of response.body) {
      bytes += chunk.length;
      if (bytes > 16384) break;
      chunks.push(chunk);
    }
    if (bytes <= 16384) value = JSON.parse(Buffer.concat(chunks).toString());
  } catch { /* Non-JSON upstream errors still retain their HTTP status. */ }
  const safe = text => typeof text === 'string' && /^[A-Za-z0-9._:-]{1,160}$/.test(text) ? text : '';
  const code = safe(value.error?.code || value.code);
  let category = 'PROVIDER_ERROR', message = '模型服务暂时不可用，本次未扣积分';
  if (/Arrearage|InsufficientBalance/i.test(code)) {
    category = 'PROVIDER_BALANCE'; message = '模型服务余额不足，本次未扣积分';
  } else if (/FreeTierOnly/i.test(code)) {
    category = 'PROVIDER_FREE_QUOTA'; message = '模型服务免费额度已用完，本次未扣积分';
  } else if (/insufficient.?quota|quota.exhausted/i.test(code)) {
    category = 'PROVIDER_QUOTA'; message = '模型服务额度不足，本次未扣积分';
  } else if (response.status === 429 || /Throttling|RateLimit/i.test(code)) {
    category = 'PROVIDER_LIMIT'; message = '模型服务繁忙，请稍后重试';
  } else if (response.status === 401 || response.status === 403) {
    category = 'PROVIDER_CONFIGURATION'; message = '模型服务授权异常，本次未扣积分';
  }
  const error = new ServiceError(category, message, 502);
  error.provider = { error: { httpStatus: response.status, code,
    requestId: safe(value.request_id || response.headers.get('x-request-id')) } };
  return error;
}
