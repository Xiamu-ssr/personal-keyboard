#!/usr/bin/env python3
"""Reproducible CC-BY-4.0 dictionary asset. Runtime has no downloader.
Source: amzxyz/rime-wanxiang-data jichu.dict.yaml; pinned revision and hash in manifest.
Transforms tone marks to ASCII pinyin, derives initials, retains the most frequent
400,000 readings up to 8 characters; no data is hand-added to rescue a test phrase.
"""
import pathlib, sqlite3, unicodedata, urllib.request, hashlib, json, sys, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
manifest_path=root/'lexicon'/'source.json'
manifest=json.loads(manifest_path.read_text())
raw=pathlib.Path(sys.argv[1]).read_bytes() if len(sys.argv)>1 else urllib.request.urlopen(manifest['url']).read()
assert hashlib.sha256(raw).hexdigest()==manifest['sha256'], 'source hash mismatch'
def code(s):
 s=s.replace('ü','v').replace('ǖ','v').replace('ǘ','v').replace('ǚ','v').replace('ǜ','v')
 return ''.join(c for c in unicodedata.normalize('NFD',s) if ord(c)<128 and (c.isalpha() or c==' ')).lower()
entries={}
for line in raw.decode().splitlines():
 fields=line.split('\t')
 if len(fields)<3:continue
 word,pinyin,weight=fields[:3]
 if len(word)<2 or len(word)>8:continue
 try:weight=int(weight)
 except ValueError:continue
 syllables=code(pinyin).split();full=''.join(syllables);initials=''.join(x[0] for x in syllables)
 if not full or len(syllables)!=len(word):continue
 key=(word,full);entries[key]=max(entries.get(key,0),weight)
ranked=sorted(entries.items(),key=lambda x:(-x[1],x[0]))[:400000]
asset=root/'app/src/main/assets/modern-lexicon.db';asset.unlink(missing_ok=True)
db=sqlite3.connect(asset)
db.executescript('PRAGMA page_size=4096; CREATE TABLE words(text TEXT NOT NULL,code TEXT NOT NULL,initials TEXT NOT NULL,weight INTEGER NOT NULL);')
# Resolve initials from source without storing the complete source in the repository.
chosen=dict(ranked);rows=[];seen=set()
for line in raw.decode().splitlines():
 f=line.split('\t')
 if len(f)<3:continue
 syllables=code(f[1]).split();key=(f[0],''.join(syllables))
 if key not in chosen or key in seen:continue
 rows.append((key[0],key[1],''.join(x[0] for x in syllables),chosen[key]));seen.add(key)
db.executemany('INSERT INTO words VALUES(?,?,?,?)',rows)
db.executescript('CREATE INDEX full_code ON words(code,weight DESC);CREATE INDEX initials_code ON words(initials,weight DESC);');db.commit();db.execute('VACUUM');db.close()
manifest['entries']=len(rows);manifest['assetSha256']=hashlib.sha256(asset.read_bytes()).hexdigest();manifest['transform']='top 400000 readings, length 2..8, tone-free pinyin with v for ü; indexed full and initial codes'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print('Indexed',len(rows),'readings, asset',asset.stat().st_size,'bytes')
