#!/usr/bin/env python3
"""Build the real decoder for JVM tests; optionally reproduce the bundled dictionary."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
VENDOR = ROOT / 'android/app/src/main/cpp/vendor/aosp-pinyin'
OUT = ROOT / '.runtime/native-host'
ARCHIVE_URL = 'https://deb.debian.org/debian/pool/main/libg/libgooglepinyin/libgooglepinyin_0.1.2.orig.tar.bz2'
ARCHIVE_SHA = '1a339ae45721a60b9fadd15e43c34b9bb27af3bb999c00ed0d88b4084cfd0637'
parser = argparse.ArgumentParser()
parser.add_argument('--rebuild-dictionary', action='store_true')
parser.add_argument('--sanitize', action='store_true')
args = parser.parse_args()
OUT.mkdir(parents=True, exist_ok=True)
sources = sorted(str(p) for p in (VENDOR / 'share').glob('*.cpp'))
common = ['clang++', '-std=c++11', '-O2', '-Wno-deprecated-declarations', '-Wno-writable-strings', '-I'+str(VENDOR/'include')]

def run(command, name):
    with (OUT / (name+'.log')).open('w') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f'{name} failed; see {OUT / (name+".log")}')

if args.rebuild_dictionary:
    archive = OUT / 'libgooglepinyin-0.1.2.tar.bz2'
    if not archive.exists():
        archive.write_bytes(urllib.request.urlopen(ARCHIVE_URL, timeout=45).read())
    if hashlib.sha256(archive.read_bytes()).hexdigest() != ARCHIVE_SHA:
        raise SystemExit('Dictionary archive checksum mismatch')
    with tarfile.open(archive) as tar:
        for name in ['data/rawdict_utf16_65105_freq.txt', 'data/valid_utf16.txt', 'tools/pinyinime_dictbuilder.cpp']:
            data = tar.extractfile('libgooglepinyin-0.1.2/'+name).read()
            # The upstream tool uses a relative include; only its build harness changes.
            if name.endswith('.cpp'):
                data = data.replace(b'"../include/dicttrie.h"', b'"dicttrie.h"')
            (OUT / Path(name).name).write_bytes(data)
    run(common+[str(OUT/'pinyinime_dictbuilder.cpp')]+sources+['-o',str(OUT/'dictbuilder')], 'dictionary-build')
    destination = ROOT / 'android/app/src/main/assets/dict_pinyin.dat'
    run([str(OUT/'dictbuilder'), str(OUT/'rawdict_utf16_65105_freq.txt'), str(OUT/'valid_utf16.txt'), str(destination)], 'dictionary')
    print('Rebuilt little-endian 64-bit dictionary:', hashlib.sha256(destination.read_bytes()).hexdigest())

system = platform.system()
if system == 'Darwin':
    java = Path(subprocess.check_output(['/usr/libexec/java_home'], text=True).strip())
    library, jni_os = 'libdoudou_decoder.dylib', 'darwin'
elif system == 'Linux':
    java = Path(os.environ['JAVA_HOME'])
    library, jni_os = 'libdoudou_decoder.so', 'linux'
else:
    raise SystemExit('Host tests support macOS/Linux; use connectedAndroidTest on other hosts.')
run(common+['-shared','-fPIC','-I'+str(java/'include'),'-I'+str(java/'include'/jni_os),str(ROOT/'android/app/src/main/cpp/decoder_jni.cpp')]+sources+['-o',str(OUT/library)], 'jni-build')
print('Host JNI decoder ready')
if args.sanitize:
    run(common+['-O1','-g','-fsanitize=address,undefined','-fno-sanitize-recover=all','-fno-omit-frame-pointer',str(ROOT/'android/app/src/test/native/decoder-boundaries.cpp')]+sources+['-o',str(OUT/'decoder-boundaries')], 'sanitizer-build')
    run([str(OUT/'decoder-boundaries'),str(ROOT/'android/app/src/main/assets/dict_pinyin.dat'),str(OUT/'sanitizer.user')], 'sanitizer')
    print('Decoder boundaries passed AddressSanitizer and UndefinedBehaviorSanitizer')
