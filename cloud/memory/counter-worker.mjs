import fs from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { parentPort } from 'node:worker_threads';
import { Tokenizer } from '@huggingface/tokenizers';
import { Template } from '@huggingface/jinja';

const directory = new URL('./tokenizer/', import.meta.url);
const manifest = JSON.parse(await fs.readFile(new URL('manifest.json', directory), 'utf8'));
const contents = {};
for (const file of manifest.files) {
  const bytes = await fs.readFile(new URL(file.file, directory));
  if (createHash('sha256').update(bytes).digest('hex') !== file.sha256) throw Error('Tokenizer integrity check failed');
  contents[file.file] = bytes.toString('utf8');
}
const tokenizer = new Tokenizer(JSON.parse(contents['tokenizer.json']), JSON.parse(contents['tokenizer_config.json']));
const template = new Template(contents['chat_template.jinja']);
// This is a capacity allowance, not a claim about billable tokens. Provider
// generation usage is checked against the budget before applying any operation.
const protocolTokens = 256;
parentPort.on('message', ({ id, request }) => {
  try {
    if (request.model !== manifest.model || request.enable_thinking !== false ||
        request.response_format?.type !== 'json_object' || request.messages?.length !== 2 ||
        request.messages[0].role !== 'system' || request.messages[1].role !== 'user' ||
        request.messages.some(m => typeof m.content !== 'string') || request.tools) throw Error('Unverified request shape');
    const prompt = template.render({ messages: request.messages, enable_thinking: false, add_generation_prompt: true });
    const contentTokens = tokenizer.encode(prompt).ids.length;
    parentPort.postMessage({ id, tokens: contentTokens + protocolTokens, contentTokens, protocolTokens, revision: manifest.revision });
  } catch { parentPort.postMessage({ id, error: true }); }
});
