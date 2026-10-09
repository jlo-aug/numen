#!/usr/bin/env python3
"""Build Numen's English real-word confusions: how often a typed word was meant as another one.

A typo that makes another real word ("you other" for "your other", "he best" for "the best")
cannot be caught by spelling. ContextCorrector weighs each confusion's prior against how well the
two words fit their neighbours in Numen's word pairs and triples.

Source (attribution in assets/common/dictionaries/en_confusions_NOTICE.txt): the Aalto ITE typing
dataset (CC BY 4.0), what people typed next to what they meant to type. Its participants typed
the same sentences, so a confusion counts once per distinct context. Only confusions a single slip
apart on the Titan 2 Elite keys and seen in at least MIN_CONTEXTS contexts are kept, and never
words the text replacements already rewrite ("id", "cant") or apostrophe-only differences.

It also writes en_short_words.txt next to it: common words of up to three letters with how many
word pairs each starts, the candidates when a lone letter or two-letter non-word is fixed by its
neighbours ("o tired" -> "so tired").

Usage (from the repository root):
  build_confusions.py ITE_EVAL.tsv SKIP_LINES app/src/main/assets/common/dictionaries/en_confusions.txt
ITE_EVAL.tsv has one case per line: set, words before, typed, intended, next word, phone result.
SKIP_LINES leaves the first lines (the tuning slice) out.
"""
import collections
import json
import math
import re
import sys

DICT = "app/src/main/assets/common/dictionaries/"
REPLACEMENTS = "app/src/main/assets/common/autocorrect/auto_corrections_en.json"
MAX_COST = 1.2
SHORT_WORD_MIN_FREQUENCY = 140
MIN_CONTEXTS = 10

# Titan 2 Elite key grid: Z sits under S, and the bottom row has no gap.
GRID = {**{c: (0, i) for i, c in enumerate("qwertyuiop")},
        **{c: (1, i) for i, c in enumerate("asdfghjkl")},
        **{c: (2, i + 1) for i, c in enumerate("zxcvbnm")}}
VOWELS = set("aeiou")
# The same error costs as NoisyChannel.kt.
NEAR, FAR, VOWEL, MISS, MISS_DOUBLE, EXTRA, EXTRA_FAT, SWAP = 0.5, 1.2, 0.6, 0.7, 0.4, 0.9, 0.5, 0.5


def near(a, b):
    if a not in GRID or b not in GRID:
        return False
    (r1, c1), (r2, c2) = GRID[a], GRID[b]
    return math.hypot(r1 - r2, c1 - c2) <= 1.5


def cost(t, w):
    prev2 = None
    prev = [j * MISS for j in range(len(w) + 1)]
    for i in range(1, len(t) + 1):
        a = t[i - 1]
        before = t[i - 2] if i > 1 else None
        after = t[i] if i < len(t) else None
        fat = (before is not None and (before == a or near(a, before))) or \
              (after is not None and (after == a or near(a, after)))
        extra = EXTRA_FAT if fat else EXTRA
        cur = [prev[0] + EXTRA] + [0.0] * len(w)
        for j in range(1, len(w) + 1):
            b = w[j - 1]
            sub = 0.0 if a == b else NEAR if near(a, b) else VOWEL if a in VOWELS and b in VOWELS else FAR
            miss = MISS_DOUBLE if j > 1 and w[j - 1] == w[j - 2] else MISS
            c = min(prev[j] + extra, cur[j - 1] + miss, prev[j - 1] + sub)
            if i > 1 and j > 1 and a == w[j - 2] and before == b and a != b:
                c = min(c, prev2[j - 2] + SWAP)
            cur[j] = c
        prev2, prev = prev, cur
    return prev[len(w)]


def previous_word(before):
    stripped = before.rstrip()
    if not stripped or stripped[-1] in ".!?,;:":
        return "<s>"
    words = [re.sub(r"[^a-z']", "", w.lower()) for w in before.split()]
    words = [w for w in words if w]
    return words[-1] if words else "<s>"


def main():
    ite, skip, out_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    words = json.load(open(DICT + "en_base.json"))
    real = {e["w"] for e in words if e["w"].isalpha() and e["w"].islower() and e.get("f", 0) > 0}
    replaced = set(json.load(open(REPLACEMENTS)))
    # How often each word starts a pair: the denominator for "word, next word".
    starts = collections.Counter()
    for line in open(DICT + "en_pairs.txt"):
        if line.startswith("#"):
            continue
        key, _, n = line.rstrip("\n").rpartition("\t")
        starts[key.partition(" ")[0]] += int(n)

    seen = collections.defaultdict(set)
    meant = collections.defaultdict(set)
    for i, line in enumerate(open(ite)):
        if i < skip:
            continue
        _, before, typed, want, nxt, _ = line.rstrip("\n").split("\t")
        t, w = typed.lower(), want.lower()
        if not t.isalpha() or t not in real:
            continue
        context = (previous_word(before), w, nxt.lower())
        seen[t].add(context)
        if w != t:
            meant[(t, w)].add(context)

    rows = []
    for (t, w), contexts in meant.items():
        plain = w.replace("'", "")
        if (len(t) < 2 or t in replaced or plain == t or len(contexts) < MIN_CONTEXTS or
                not (plain in real or w in starts) or cost(t, plain) > MAX_COST):
            continue
        prior = len(contexts) / len(seen[t])
        if prior >= 0.999:
            continue
        rows.append((t, w, math.log(prior / (1 - prior)), starts[t], starts[w]))
    rows.sort()
    with open(out_path, "w") as out:
        out.write("# Numen English real-word confusions. See en_confusions_NOTICE.txt for the source.\n")
        out.write("# Format: typed<tab>meant<tab>prior log-odds<tab>pairs starting with typed<tab>pairs starting with meant\n")
        for t, w, odds, ut, uw in rows:
            out.write(f"{t}\t{w}\t{odds:.3f}\t{ut}\t{uw}\n")
    print(len(rows), "confusions", file=sys.stderr)

    frequency = {e["w"]: e.get("f", 0) for e in words}
    short = sorted(w for w in real if len(w) <= 3 and frequency.get(w, 0) >= SHORT_WORD_MIN_FREQUENCY and
                   any(c in "aeiouy" for c in w))
    with open(out_path.replace("en_confusions.txt", "en_short_words.txt"), "w") as out:
        out.write("# Numen English short words, the fixes for a lone letter or two-letter non-word.\n")
        out.write("# Built by tools/build_confusions.py from Numen's dictionary and word pairs. Format: word<tab>pairs it starts\n")
        for w in short:
            out.write(f"{w}\t{starts[w]}\n")
    print(len(short), "short words", file=sys.stderr)


if __name__ == "__main__":
    main()
