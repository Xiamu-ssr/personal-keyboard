# Decoder source

The pinned libgooglepinyin 0.1.2 archive is an Apache-2.0 portable fork of AOSP PinyinIME. `SOURCE.json` records its archive hash and original file hashes. `NOTICE` is bundled in the APK and available in Settings.

Local fixes are confined to bounded search allocation:

- `DictTrie::extend_dict` rejects exhausted pools or stale handles; extension counts only marks actually allocated.
- A root search without an available dictionary milestone is a no-match, rather than an assertion failure.
- `MatrixSearch::extend_dmi` clears candidate counts before early exits. Cached candidates may extend a matrix path only when a DMI was allocated; otherwise backtracking could reference uninitialized memory after pool exhaustion.

The bundled binary dictionary is built from the same archive's UTF-16 source data with 64-bit little-endian `size_t`, matching the two supported Android ABIs. Reproduce it with `python3 android/tools/native-host.py --rebuild-dictionary`. It is not the incompatible 32-bit AOSP binary.

`--sanitize` checks repeated initials and seeded random input with AddressSanitizer and UndefinedBehaviorSanitizer. JVM tests additionally exercise Chinese sentence ranking, partial composition and personal vocabulary.
