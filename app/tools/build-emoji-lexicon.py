#!/usr/bin/env python3
"""Build the offline emoji keyword asset from hash-pinned Unicode sources.

Python 3.9+, standard library only. Network access is build-time only; use
--offline after the first build. --check compares bytes without rewriting assets.
No hand-authored aliases, phrase exceptions, transliteration, or user data.
"""

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET


CLDR_RELEASE = "49"
CLDR_COMMIT = "96e54b273d2da4ec6fbad350ba19ff23fa24a588"
EMOJI_VERSION = "17.0"
CLDR_RAW = "https://raw.githubusercontent.com/unicode-org/cldr/" + CLDR_COMMIT + "/"
# SHA-256 is over the original downloaded bytes, before XML parsing/normalization.
SOURCES = {
    "common/annotations/zh.xml": (
        CLDR_RAW + "common/annotations/zh.xml",
        "820de5034133077d2baa31464de0c91e748ae3c659880c2918bf6a55e560a282",
    ),
    "common/annotations/en.xml": (
        CLDR_RAW + "common/annotations/en.xml",
        "14d0e9bb5003c085250df4ac7bd7ba1e26d92e74174d7a3a2f42dc16e07fb155",
    ),
    "common/annotationsDerived/zh.xml": (
        CLDR_RAW + "common/annotationsDerived/zh.xml",
        "4fff4592df54051e4b8ebe49f17e3b6bd9c951303f99abeb989446b80ef64a1f",
    ),
    "common/annotationsDerived/en.xml": (
        CLDR_RAW + "common/annotationsDerived/en.xml",
        "a4a6c1a4c341d94179cb116623c5f44c47400a5bc55da483423ef7430ecf622e",
    ),
    "LICENSE": (
        CLDR_RAW + "LICENSE",
        "65eb33419e22a297550a811b5b535f5e532f9a7e0114b516c0ea18afd53c3804",
    ),
    "emoji-test.txt": (
        "https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt",
        "1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda",
    ),
}
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ASSET = ROOT / "app/app/src/main/assets/emoji-keywords.tsv"
DEFAULT_CACHE = ROOT / ".runtime/emoji-lexicon/sources"
MAX_KEYWORD_CHARS = 160


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def load_sources(cache, offline):
    result = {}
    for name, (url, expected) in SOURCES.items():
        path = cache / name
        if path.exists():
            raw = path.read_bytes()
        elif offline:
            raise ValueError("Missing offline source: " + str(path))
        else:
            request = urllib.request.Request(url, headers={"User-Agent": "DoudouEmojiLexiconBuilder/1"})
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
        actual = sha256(raw)
        if actual != expected:
            raise ValueError("SHA-256 mismatch for " + name + ": expected " + expected + ", got " + actual)
        if not path.exists():
            atomic_write(path, raw)
        result[name] = raw
    return result


def normalize_keyword(value):
    return " ".join(unicodedata.normalize("NFKC", value).lower().split())


def unqualified(value):
    # CLDR deliberately omits VS16 in cp attributes. Restore complete sequences
    # using emoji-test, rather than appending VS16 to arbitrary code points.
    return value.replace("\ufe0f", "")


def unmodified(value):
    return unqualified("".join(c for c in value if not 0x1F3FB <= ord(c) <= 0x1F3FF))


def build(sources):
    qualified, order = {}, {}
    emoji_test = sources["emoji-test.txt"].decode("utf-8")
    if "# Version: " + EMOJI_VERSION + "\n" not in emoji_test:
        raise ValueError("Unexpected emoji-test version")
    for line in emoji_test.splitlines():
        data = line.split("#", 1)[0]
        if ";" not in data:
            continue
        code, status = (part.strip() for part in data.split(";", 1))
        if status != "fully-qualified":
            continue
        emoji = "".join(chr(int(cp, 16)) for cp in code.split())
        key = unqualified(emoji)
        if key in qualified:
            raise ValueError("Ambiguous fully-qualified emoji: " + key)
        qualified[key] = emoji
        order[emoji] = len(order)

    # Per-locale labels provide a deterministic relevance hint, not aliases.
    names = defaultdict(set)
    pairs = defaultdict(set)
    source_stats = {}
    for path in SOURCES:
        if not path.endswith(".xml"):
            continue
        locale = Path(path).stem
        root = ET.fromstring(sources[path])  # stdlib does not fetch the external DTD
        seen, kept, missing = 0, 0, set()
        for annotation in root.findall("./annotations/annotation"):
            seen += 1
            if annotation.get("alt") or annotation.get("draft") in {"unconfirmed", "provisional"}:
                continue
            cp = unqualified(annotation.attrib["cp"])
            if cp not in qualified:
                missing.add(cp)  # typographic symbols/components are not suggestions
                continue
            text = annotation.text or ""
            if text.strip() in {"", "↑↑↑", "∅∅∅"}:
                continue
            emoji = qualified[cp]
            kept += 1
            if annotation.get("type") == "tts":
                names[(locale, emoji)].add(normalize_keyword(text))
                words = [text]
            else:
                words = text.split("|")
            for word in words:
                keyword = normalize_keyword(word)
                if not keyword or len(keyword.encode("utf-16-le")) // 2 > MAX_KEYWORD_CHARS:
                    continue
                if any(unicodedata.category(c) in {"Cc", "Cs"} for c in keyword):
                    continue
                pairs[keyword].add((locale, emoji))
        source_stats[path] = {
            "annotations": seen,
            "emojiAnnotations": kept,
            "nonRgiCodePointsSkipped": len(missing),
        }

    rows, raw_pairs, folded = {}, 0, 0
    for keyword in sorted(pairs):
        candidates = defaultdict(set)
        for locale, emoji in pairs[keyword]:
            candidates[emoji].add(locale)
        raw_pairs += len(candidates)
        # Generic keywords keep the neutral base when it is already present for
        # that exact keyword. Explicit skin-tone keywords/names retain variants.
        bases = {unqualified(emoji) for emoji in candidates}
        retained = []
        for emoji in candidates:
            base = unmodified(emoji)
            if base != unqualified(emoji) and base in bases:
                folded += 1
                continue
            retained.append(emoji)

        def rank(emoji):
            labels = {label for locale in candidates[emoji] for label in names[(locale, emoji)]}
            han = any("\u3400" <= c <= "\u9fff" for c in keyword)
            related = any(keyword in label if han else label.startswith(keyword + " ") for label in labels)
            relevance = 0 if keyword in labels else 1 if related else 2
            tones = sum(0x1F3FB <= ord(c) <= 0x1F3FF for c in emoji)
            return relevance, tones, order[emoji]

        rows[keyword] = sorted(retained, key=rank)

    stats = {
        "schema": 1,
        "cldrRelease": CLDR_RELEASE,
        "cldrCommit": CLDR_COMMIT,
        "emojiVersion": EMOJI_VERSION,
        "keywords": len(rows),
        "emoji": len({emoji for values in rows.values() for emoji in values}),
        "mappings": sum(len(values) for values in rows.values()),
        "rawMappings": raw_pairs,
        "redundantToneMappingsRemoved": folded,
        "sourceStats": source_stats,
    }
    lines = [
        "# doudou-emoji-keywords schema=1; UTF-8; LF",
        "# keyword<TAB>fully-qualified-emoji[ SPACE fully-qualified-emoji ...]",
        "# CLDR " + CLDR_RELEASE + " commit=" + CLDR_COMMIT + "; Emoji " + EMOJI_VERSION,
        "# Keywords: NFKC, lowercase, collapsed whitespace; sorted by Unicode code point.",
        "# Values: exact short name, Han name containment / English name prefix, fewer skin modifiers, CLDR emoji-test order.",
        "# Includes zh/en annotations AND annotationsDerived. No hand-authored keywords.",
        "# Generic-keyword skin variants collapse only when that keyword also maps to the neutral base.",
        "# Copyright © 1991–2026 Unicode, Inc. CLDR data; © 2025 Unicode, Inc. emoji-test data.",
        "# SPDX-License-Identifier: Unicode-3.0",
    ]
    for name, (url, digest) in SOURCES.items():
        lines.append("# source " + name + " sha256=" + digest + " " + url)
    lines.append("# counts " + json.dumps({k: stats[k] for k in ["keywords", "emoji", "mappings"]}, sort_keys=True))
    lines.append("# Upstream license follows (specification text is not included in this asset):")
    lines.extend("# " + line if line else "#" for line in sources["LICENSE"].decode("utf-8").splitlines())
    lines.extend(keyword + "\t" + " ".join(values) for keyword, values in rows.items())
    data = ("\n".join(lines) + "\n").encode("utf-8")
    stats.update(assetBytes=len(data), assetSha256=sha256(data))
    stats["sources"] = {name: {"url": url, "sha256": digest} for name, (url, digest) in SOURCES.items()}
    return data, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--offline", action="store_true", help="never download; require all pinned source files")
    parser.add_argument("--output", type=Path, default=DEFAULT_ASSET)
    parser.add_argument("--check", action="store_true", help="fail if the output differs; do not rewrite it")
    parser.add_argument("--report", type=Path, help="optional deterministic JSON evidence (no timestamps)")
    args = parser.parse_args()
    try:
        data, stats = build(load_sources(args.cache_dir, args.offline))
        if args.check:
            if not args.output.is_file() or args.output.read_bytes() != data:
                raise ValueError("Asset differs from pinned sources: " + str(args.output))
        else:
            atomic_write(args.output, data)
        if args.report:
            atomic_write(args.report, (json.dumps(stats, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        print(json.dumps({k: stats[k] for k in ["keywords", "emoji", "mappings", "assetBytes", "assetSha256"]}, sort_keys=True))
    except (OSError, ValueError, ET.ParseError) as error:
        print("emoji lexicon: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
