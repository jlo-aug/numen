#!/usr/bin/env python3
"""Build Numen's English word-triple counts, used to rank autocorrect fixes by the two words before.

Source (attribution in assets/common/dictionaries/en_triples_NOTICE.txt): Tatoeba English
sentences (CC BY 2.0 FR), everyday text close to how people message. Every 50th sentence is held
out, as in build_context_pairs.py, because ChatSimulationTest types those to measure autocorrect.
Only triples seen at least MIN_COUNT times are kept: rarer ones barely change the ranking
(measured with ChatSimulationTest) and would only grow the app.

Usage (from the repository root):
  build_context_triples.py TATOEBA_ENG.tsv.bz2 app/src/main/assets/common/dictionaries/en_triples.txt
Input: eng_sentences.tsv.bz2 from downloads.tatoeba.org/exports/per_language/eng/.
"""
import bz2
import re
import sys
from collections import Counter

SENTENCE = re.compile(r"[^.!?\n]+[.!?]?")
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")
START = "<s>"
HELD_OUT_EVERY = 50  # Must match build_context_pairs.py.
MIN_COUNT = 10


def main():
    tatoeba, out_path = sys.argv[1:3]
    counts = Counter()
    with bz2.open(tatoeba, "rt", encoding="utf-8", errors="ignore") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or (parts[0].isdigit() and int(parts[0]) % HELD_OUT_EVERY == 0):
                continue
            for sentence in SENTENCE.findall(parts[2].lower().replace("’", "'")):
                words = TOKEN.findall(sentence)
                if words:
                    words.insert(0, START)
                    counts.update(zip(words, words[1:], words[2:]))
    lines = sorted(f"{a} {b} {c}\t{n}" for (a, b, c), n in counts.items() if n >= MIN_COUNT)
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    print(f"{len(lines)} triples", file=sys.stderr)


if __name__ == "__main__":
    main()
