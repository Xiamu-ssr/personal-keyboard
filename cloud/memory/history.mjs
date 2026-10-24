import { isUuid } from '../billing/ledger.mjs';
import { requireValue } from '../http/errors.mjs';

const bounded = (value, length) => {
  requireValue(typeof value === 'string' && value.length <= length, 'HISTORY_FORMAT', '历史格式错误'); return value;
};
export function historyEvent(raw) {
  requireValue(raw && isUuid(raw.id) && Number.isSafeInteger(raw.revision) && raw.revision > 0 && ['voice', 'advisor', 'recording'].includes(raw.type), 'HISTORY_FORMAT', '历史标识或修订无效');
  if (raw.deleted === true) return { id: raw.id, revision: raw.revision, type: raw.type, deleted: true, messages: [], asrText: '', insertedText: '', resultText: '', conversationRef: '' };
  const value = { id: raw.id, revision: raw.revision, type: raw.type,
    conversationRef: bounded(raw.conversationRef || '', 250),
    asrText: bounded(raw.asrText || '', 50000), insertedText: bounded(raw.insertedText || '', 50000), resultText: bounded(raw.resultText || '', 50000),
    identity: raw.identity && typeof raw.identity === 'object' ? { platform: bounded(raw.identity.platform || '', 120), nickname: bounded(raw.identity.nickname || '', 100), handle: bounded(raw.identity.handle || '', 150), personId: isUuid(raw.identity.personId) ? raw.identity.personId : '' } : {},
    messages: [],
  };
  requireValue(Array.isArray(raw.messages || []) && (raw.messages || []).length <= 300, 'HISTORY_FORMAT', '聊天依据过多');
  for (const message of raw.messages || []) {
    requireValue(['self', 'other', 'unknown'].includes(message.speaker), 'HISTORY_FORMAT', '聊天说话人无效');
    value.messages.push({ speaker: message.speaker, text: bounded(message.text, 5000) });
  }
  requireValue(value.resultText || value.asrText || value.messages.length, 'HISTORY_FORMAT', '历史没有可整理的内容');
  return value;
}
