#!/usr/bin/env python3
"""Offline evaluation of sentence-aware correction on held-out text.

Mirrors ContextCorrector.review() over the shipped pair counts and measures, on clean text the
counts never saw (manual pages, novels, docs):
  - wrong changes: correct words it would rewrite. This must stay near zero.
  - fixes: realistic one-key slips injected into the same text ("some" -> "dome") that it repairs.

Usage (from the repository root): eval_context_correction.py TEXT_FILE [TEXT_FILE...]
Keep the rules and constants in sync with ContextCorrector.kt.
"""
import json
import random
import re
import sys
from collections import Counter

ASSETS = "app/src/main/assets/common/dictionaries"
MIN_WORD_LENGTH = 3
MAX_REVIEWED_FREQUENCY = 160
MIN_REPLACEMENT_FREQUENCY = 140
MIN_FREQUENCY_GAIN = 25

GRID = {
    **{c: (0, i) for i, c in enumerate("qwertyuiop")},
    **{c: (1, i) for i, c in enumerate("asdfghjkl")},
    "z": (2, 0), "x": (2, 1), "c": (2, 2), "v": (2, 3), "b": (2, 6), "n": (2, 7), "m": (2, 8),
}


def neighbors(a, b):
    if a not in GRID or b not in GRID:
        return False
    (r1, c1), (r2, c2) = GRID[a], GRID[b]
    return ((r1 - r2) ** 2 + (c1 - c2) ** 2) ** 0.5 <= 1.5


def plausible_extra_letter(word, i):
    return any(word[i] == word[j] or neighbors(word[i], word[j]) for j in (i - 1, i + 1) if 0 <= j < len(word))


def slips(word):
    out = set()
    for i in range(len(word)):
        for c in "abcdefghijklmnopqrstuvwxyz":
            if c != word[i] and neighbors(word[i], c):
                out.add(word[:i] + c + word[i + 1:])
        if i + 1 < len(word):
            out.add(word[:i] + word[i + 1] + word[i] + word[i + 2:])
        if plausible_extra_letter(word, i):
            out.add(word[:i] + word[i + 1:])
    for i in range(len(word) + 1):
        for c in "abcdefghijklmnopqrstuvwxyz":
            out.add(word[:i] + c + word[i:])
    out.discard(word)
    return out


def keeps_first_letter(typed, candidate):
    return candidate[0] == typed[0] or len(candidate) == len(typed)


def load():
    base = {}
    for entry in json.load(open(f"{ASSETS}/en_base.json")):
        w = entry["w"].lower()
        base[w] = max(base.get(w, 0), entry.get("f", 1))
    slang = {entry["w"].lower() for entry in json.load(open(f"{ASSETS}/en_extra.json"))}
    pairs = {}
    for line in open(f"{ASSETS}/en_pairs.txt"):
        if not line.startswith("#"):
            pair, _, count = line.rstrip("\n").partition("\t")
            pairs[pair] = int(count)
    return base, slang, pairs


class Corrector:
    def __init__(self, base, protected, pairs):
        self.base, self.protected, self.pairs = base, protected, pairs
        self.cache = {}

    def count(self, a, b):
        return self.pairs.get(f"{a} {b}", 0)

    def candidates(self, typed, frequency):
        if typed not in self.cache:
            self.cache[typed] = [
                c for c in slips(typed)
                if len(c) >= MIN_WORD_LENGTH and keeps_first_letter(typed, c)
                and self.base.get(c, 0) >= max(MIN_REPLACEMENT_FREQUENCY, frequency + MIN_FREQUENCY_GAIN)
            ]
        return self.cache[typed]

    def review(self, before, typed, after):
        if len(typed) < MIN_WORD_LENGTH or typed in self.protected:
            return None
        frequency = self.base.get(typed, 0)
        if frequency <= 0 or frequency >= MAX_REVIEWED_FREQUENCY:
            return None
        if self.count(before, typed) > 0 or self.count(typed, after) > 0:
            return None
        best, best_evidence = None, 0
        for candidate in self.candidates(typed, frequency):
            evidence = self.count(before, candidate) * self.count(candidate, after)
            if evidence > best_evidence:
                best, best_evidence = candidate, evidence
        return best


SENTENCE = re.compile(r"[^.!?]+[.!?]?")
WORD = re.compile(r"^[a-z]+$")


def triples(text):
    """(before, word, after) for consecutive plain words, with <s>/</s> at sentence edges.
    Hard-wrapped lines are joined; blank lines separate paragraphs."""
    for paragraph in re.split(r"\n\s*\n", text.replace("’", "'")):
        for sentence in SENTENCE.findall(" ".join(paragraph.split()).lower()):
            tokens = sentence.split()
            if not tokens:
                continue
            ends = tokens[-1].endswith((".", "!", "?"))
            tokens[-1] = tokens[-1].rstrip(".!?")
            words = ["<s>"] + tokens + (["</s>"] if ends else [])
            for i in range(1, len(words) - 1):
                window = words[i - 1:i + 2]
                if all(t in ("<s>", "</s>") or WORD.match(t) for t in window):
                    yield tuple(window)


def slip_kind(typed, intended):
    if len(typed) < len(intended):
        return "missed letter"
    if len(typed) > len(intended):
        return "extra letter"
    return "swapped letters" if sum(a != b for a, b in zip(typed, intended)) == 2 else "neighbour key"


# Rough share of one-key slips on a physical keyboard.
SLIP_WEIGHTS = {"neighbour key": 0.35, "missed letter": 0.30, "extra letter": 0.20, "swapped letters": 0.15}


def inject(cases, base, slang, rng):
    """One realistic slip per word that has one: a real, rarer word one key away."""
    for before, intended, after in cases:
        frequency = base.get(intended, 0)
        if len(intended) < MIN_WORD_LENGTH or frequency < MIN_REPLACEMENT_FREQUENCY:
            continue
        by_kind = {}
        for typed in sorted(slips(intended)):
            if (len(typed) >= MIN_WORD_LENGTH and 0 < base.get(typed, 0) < MAX_REVIEWED_FREQUENCY
                    and frequency >= base[typed] + MIN_FREQUENCY_GAIN and typed not in slang
                    and keeps_first_letter(typed, intended)):
                by_kind.setdefault(slip_kind(typed, intended), []).append(typed)
        if by_kind:
            kinds = sorted(by_kind)
            kind = rng.choices(kinds, [SLIP_WEIGHTS[k] for k in kinds])[0]
            yield before, rng.choice(by_kind[kind]), after, intended


def main():
    base, slang, pairs = load()
    corrector = Corrector(base, slang, pairs)
    for path in sys.argv[1:]:
        cases = list(triples(open(path, errors="ignore").read()))
        wrong = Counter((b, w, a, f) for b, w, a in cases if (f := corrector.review(b, w, a)))
        injected = list(inject(cases, base, slang, random.Random(7)))
        fixed = sum(corrector.review(b, typed, a) == intended for b, typed, a, intended in injected)
        print(f"{path}: {len(cases)} words, {sum(wrong.values())} wrong changes; "
              f"{fixed}/{len(injected)} injected slips fixed ({100 * fixed / max(1, len(injected)):.0f}%)")
        for case, n in wrong.most_common(10):
            print(f"    {n}x {case}")


if __name__ == "__main__":
    main()
