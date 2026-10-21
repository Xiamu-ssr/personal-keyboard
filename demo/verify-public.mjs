import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {root} from './tools.mjs';
const manifest=JSON.parse(await fs.readFile(path.join(root,'spec/release.json'),'utf8'));
for(const [name,expected]of Object.entries(manifest.files)){
 if(!/^(doudou-keyboard\.apk|(?:home|typing|tools|voice|memory|people|advisor|settings)\.png)$/.test(name))throw Error('Unexpected public artifact');
 const bytes=await fs.readFile(path.join(root,'spec/demo',name));
 if(bytes.length!==expected.bytes||createHash('sha256').update(bytes).digest('hex')!==expected.sha256)throw Error('Public artifact digest mismatch: '+name);
}
console.log('Verified public APK and eight native screenshots against the release manifest.');
