import { readFileSync } from 'node:fs';
import { requireValue } from '../http/errors.mjs';
export const translationContract = JSON.parse(readFileSync(new URL('../contracts/translation.json', import.meta.url), 'utf8'));
const ids = new Set(translationContract.languages.map(l => l.id));
export function translationInput(raw) {
  requireValue(raw && typeof raw === 'object' && !Array.isArray(raw)
    && Object.keys(raw).every(key => ['text', 'sourceLanguage', 'targetLanguage'].includes(key)),
    'INVALID_INPUT', '翻译只接收本次原文和语言');
  requireValue(typeof raw.text === 'string' && raw.text.trim()
    && Array.from(raw.text).length <= translationContract.maxSourceCharacters,
    'INVALID_INPUT', '翻译原文须为1至4000字');
  const from = raw.sourceLanguage || 'auto';
  requireValue((from === 'auto' || ids.has(from)) && ids.has(raw.targetLanguage),
    'INVALID_LANGUAGE', '请选择支持的翻译语言');
  return { text: raw.text, sourceLanguage: from, targetLanguage: raw.targetLanguage };
}
export function translationResult(value, input) {
  requireValue(value && typeof value.text === 'string' && value.text.trim()
    && Array.from(value.text).length <= translationContract.maxResultCharacters
    && (ids.has(value.detectedLanguage) || value.detectedLanguage === 'und')
    && value.targetLanguage === input.targetLanguage,
    'MODEL_FORMAT', '译文没有完整返回，本次未扣分', 502);
  return { text: value.text.trim(), detectedLanguage: value.detectedLanguage,
    sourceLanguage: input.sourceLanguage, targetLanguage: input.targetLanguage };
}
