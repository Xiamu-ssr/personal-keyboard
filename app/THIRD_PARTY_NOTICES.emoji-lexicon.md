# Offline emoji semantic lexicon

This notice covers only `app/src/main/assets/emoji-keywords.tsv` and its
builder, `tools/build-emoji-lexicon.py`. No user dictionary, editor text,
telemetry, model, emoji audio, or runtime downloader is involved.

## Sources and license

The semantic keywords and short names are from Unicode CLDR **49**,
tag `release-49`, commit **96e54b273d2da4ec6fbad350ba19ff23fa24a588**.
Both `common/annotations/{zh,en}.xml` and
`common/annotationsDerived/{zh,en}.xml` are actually parsed. `zh` is the
Simplified Chinese locale. Official references:

- [CLDR release archive](https://cldr.unicode.org/index/downloads)
- [Pinned CLDR release](https://github.com/unicode-org/cldr/releases/tag/release-49)
- [Pinned Chinese annotations](https://github.com/unicode-org/cldr/blob/96e54b273d2da4ec6fbad350ba19ff23fa24a588/common/annotations/zh.xml)
- [Pinned Chinese derived annotations](https://github.com/unicode-org/cldr/blob/96e54b273d2da4ec6fbad350ba19ff23fa24a588/common/annotationsDerived/zh.xml)
- [Unicode Emoji 17.0 emoji-test.txt](https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt)

CLDR keywords include non-emoji characters. The builder restricts values to
the **fully-qualified** sequences in Unicode Emoji **17.0**, restores the
VS16 selectors omitted from CLDR `cp` attributes using that exact mapping,
and uses emoji-test's CLDR keyboard order for tie-breaking. Standalone skin
and hair components, typographic symbols, and non-RGI sequences are excluded.
No glyphs or fonts are included; rendering support is the caller's concern.

The data is licensed under **Unicode-3.0** (UNICODE LICENSE V3).
Copyright © 1991–2026 Unicode, Inc. (CLDR data); © 2025 Unicode, Inc.
(emoji-test). The complete pinned upstream license is retained in
`licenses/emoji-lexicon-UNICODE-LICENSE.txt` and in the generated TSV's comment
header, so it accompanies the packaged data. The upstream license also
describes a separate restriction on specification publications; no LDML
specification text is redistributed in this asset.

All original-source SHA-256 digests are verified **before** parsing:

| File | SHA-256 |
| --- | --- |
| common/annotations/zh.xml | `820de5034133077d2baa31464de0c91e748ae3c659880c2918bf6a55e560a282` |
| common/annotations/en.xml | `14d0e9bb5003c085250df4ac7bd7ba1e26d92e74174d7a3a2f42dc16e07fb155` |
| common/annotationsDerived/zh.xml | `4fff4592df54051e4b8ebe49f17e3b6bd9c951303f99abeb989446b80ef64a1f` |
| common/annotationsDerived/en.xml | `a4a6c1a4c341d94179cb116623c5f44c47400a5bc55da483423ef7430ecf622e` |
| CLDR LICENSE | `65eb33419e22a297550a811b5b535f5e532f9a7e0114b516c0ea18afd53c3804` |
| Emoji 17.0 emoji-test.txt | `1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda` |

The six full URLs and digests are also embedded in the builder and TSV.
Builds never fetch a moving branch or silently accept changed source bytes.

## Transform and TSV schema 1

UTF-8, LF, one row per keyword: `keyword<TAB>emoji1 emoji2 ...`.
Header comment lines start with `#` and have no tab. A `#<TAB>...` data row
is legal. Blank lines are ignored. Keywords are NFKC normalized, lowercased,
and have whitespace trimmed/collapsed. Whole short names and pipe-delimited
keywords are indexed; there is no substring expansion, pinyin conversion,
translation, or hand-authored colloquial alias list. Keywords over 160 UTF-16
units are excluded. Rows sort by Unicode code point; values are unique.

Within each exact keyword, ranking is deterministic: exact short name first,
then a Chinese short name containing the keyword / an English short name
starting with the whole keyword and a space, then fewer skin modifiers,
then emoji-test CLDR order. This is a lexical ranking hint, not a model or
usage/popularity estimate. For a generic keyword, a skin-tone variant is
removed only if that **same keyword** also maps to its neutral base.
Explicit skin-tone keywords and full derived names remain searchable.

Current generated corpus: **14,888 keywords**, **3,944 emoji sequences**,
**35,223 mappings**, **705,397 bytes**. It removes 26,155 redundant tone
mappings from 61,378 unique raw mappings. Asset SHA-256:
`766cd98ce329ca92112b7453405341b56c07bd4c5bba782359aa8ae211c43253`.

## API and conservative completion behavior

`EmojiLexicon.read(Reader)` returns an immutable Java 8-compatible instance.
The caller owns/closes the Reader. Malformed rows raise `IOException` with
the row number, without copying row text into an error. Duplicate normalized
keys merge in file order. Loading has no Android or JNI dependency.

`suggest(keyword, limit)` performs only normalized exact-key lookup and
returns up to `limit` immutable values. The composing caller resolves pinyin
to complete Chinese word candidates first. For example, `shengri` itself is
not an alias; `生日` yields `[🎂, 🥳]`. Returned suggestions do not replace text.

`after(recentText, limit)` checks only the longest complete keyword at the
end and returns at most **two** values. It may ignore terminal punctuation
and spaces, but a final line break ends the context. A match starts at the
beginning or a whitespace/punctuation boundary; continuous Chinese is not
segmented. Thus `生日` and `好的，谢谢！` work; `今天生日` and `今天很开心`
are deliberately not guessed. If the complete keyword `不开心` is supplied,
its own sad semantics are used instead of extracting `开心`.

Passive suggestions need at least two letters including Han, or three
without Han. A single `笑` still works in `suggest`, while `after("笑", 2)`
is empty. English words do not match inside `unhappy`, `some_coffee`, or
`https://coffee`. The routine reads only a bounded tail and retains no
context/history. Nonpositive limits, null queries and unknown keywords yield
an empty list; `2` is a ceiling, not a minimum to be padded.

Real-data `suggest(keyword, 2)` results:

| Keyword | Suggestions |
| --- | --- |
| 微笑 | ☺️ 😊 |
| 开心 / 笑 | 😄 😆 |
| 哈哈 | 😃 😄 |
| 谢谢 | 🙏 |
| 生日 | 🎂 🥳 |
| 晚安 | 😴 🥱 |
| 咖啡 / coffee | ☕ |
| 爱心 | 🤎 🤍 |
| 红心 | ❤️ |
| 女程序员: 中等肤色 | 👩🏽‍💻 |
| 旗: 中国 | 🇨🇳 |

These reflect CLDR coverage, not arbitrary chat interpretation. For example,
CLDR tags the red heart with `红心 / 心 / 爱`; the exact `爱心` keyword maps
to other hearts/hands. It is not expanded to every heart. No OpenMoji or
other secondary dataset is used.

## Reproduce without Gradle or a device

From the repository root, Python 3.9+ and only the standard library:

```sh
python3 app/tools/build-emoji-lexicon.py
python3 app/tools/build-emoji-lexicon.py --offline --check \
  --report .runtime/emoji-lexicon/build-report.json
```

The first command caches six pinned sources in ignored
`.runtime/emoji-lexicon/sources/`. `--offline` never downloads. `--check`
compares generated bytes with the asset and never rewrites that asset.
`--cache-dir` and `--output` permit isolated clean builds. The optional JSON
report includes per-source annotation counts, mapping counts and all hashes;
no timestamps enter the deterministic output.

The independent JUnit 4 class is
`app/src/test/java/app/doudou/ime/input/engine/EmojiLexiconTest.java`.
Compile just it and `EmojiLexicon.java` with `javac --release 8 -encoding UTF-8`
and the project's existing JUnit 4.13.2 / Hamcrest 1.3 jars on the classpath;
run `org.junit.runner.JUnitCore app.doudou.ime.input.engine.EmojiLexiconTest`
with `-Demoji.lexicon.asset=/absolute/path/to/emoji-keywords.tsv`.
No shared test file or Android build configuration is changed.
