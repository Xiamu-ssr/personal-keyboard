#!/usr/bin/env python3
"""Build an initials index from the same pinned AOSP source as the native decoder."""
from pathlib import Path
import urllib.request, hashlib, tarfile, json
ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'.runtime/native-host'; CACHE.mkdir(parents=True,exist_ok=True)
URL='https://deb.debian.org/debian/pool/main/libg/libgooglepinyin/libgooglepinyin_0.1.2.orig.tar.bz2'
SHA='1a339ae45721a60b9fadd15e43c34b9bb27af3bb999c00ed0d88b4084cfd0637'
archive=CACHE/'libgooglepinyin-0.1.2.tar.bz2'
if not archive.exists(): archive.write_bytes(urllib.request.urlopen(URL,timeout=45).read())
if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA:raise SystemExit('Dictionary source digest mismatch')
with tarfile.open(archive) as source:
 raw=source.extractfile('libgooglepinyin-0.1.2/data/rawdict_utf16_65105_freq.txt').read().decode('utf-16')
rows={}
for line in raw.splitlines():
 fields=line.split()
 if len(fields)<5:continue
 word=fields[0];syllables=fields[3:]
 if not 2<=len(word)<=8 or len(syllables)!=len(word) or not all(x.isascii() and x.isalpha() for x in syllables):continue
 initials=''.join(x[0] for x in syllables)
 rows[(initials,word)]=(''.join(syllables),float(fields[1]))
# Contemporary expressions are editable lexicon data, not special cases in the decoder.
extra=ROOT/'app/app/src/main/assets/expressions.tsv'
for line in extra.read_text().splitlines():
 if not line or line.startswith('#'):continue
 word,spelling,frequency=line.split('\t');syllables=spelling.split()
 rows[(''.join(x[0] for x in syllables),word)]=(''.join(syllables),float(frequency))
groups={}
for (initials,word),(spelling,freq) in rows.items():groups.setdefault(initials,[]).append((word,spelling,freq))
lines=[]
for code,entries in sorted(groups.items()):
 for word,spelling,freq in sorted(entries,key=lambda x:-x[2])[:48]:lines.append('\t'.join([code,word,spelling]))
destination=ROOT/'app/app/src/main/assets/initials.tsv'
destination.write_text('\n'.join(lines)+'\n')
meta={'entries':len(lines),'groups':len(groups),'bytes':destination.stat().st_size,'source_url':URL,'source_sha256':SHA,'index_sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),'license':'Apache-2.0','max_candidates_per_initials':48}
(destination.parent/'initials-info.json').write_text(json.dumps(meta,indent=2)+'\n')
print(json.dumps(meta))
