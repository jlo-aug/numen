#!/usr/bin/env python3
"""Build Numen's English word-pair counts for sentence-aware correction.

Sources (attribution required, see assets/common/dictionaries/en_pairs_NOTICE.txt):
  - Simple English Wikipedia (CC BY-SA 4.0): article text, counted here.
  - Tatoeba English sentences (CC BY 2.0 FR): everyday, conversational sentences, counted here
    with double weight because encyclopedia text under-represents how people actually write.
  - Google Books Ngram top 2-grams via github.com/orgtre/google-books-ngram-frequency (CC BY 3.0).
  - The hand-written pairs in tools/en_pairs_curated.txt.

Only pairs the corrector can use are kept: pairs touching a common replacement word, or a real
word one key-slip away from one, including sentence-start "<s> word" and sentence-end "word </s>".

Usage (from the repository root):
  build_context_pairs.py SIMPLEWIKI.xml.bz2 TATOEBA_ENG.tsv.bz2 BOOKS_2GRAMS.csv \
      app/src/main/assets/common/dictionaries/en_pairs.txt
Inputs: simplewiki-latest-pages-articles.xml.bz2 (dumps.wikimedia.org), Tatoeba eng_sentences.tsv.bz2
(downloads.tatoeba.org/exports/per_language/eng/), 2grams_english.csv (github.com/orgtre/
google-books-ngram-frequency, ngrams/). Set PAIRS_CACHE=/path/counts.pkl to count the corpora once
and reuse the counts while tuning.
"""
import bz2
import csv
import json
import os
import pickle
import re
import sys
from collections import Counter

ASSETS = "app/src/main/assets/common/dictionaries"
MIN_REPLACEMENT_FREQUENCY = 140  # Must match ContextCorrector.
MIN_FREQUENCY_GAIN = 25
START = "<s>"
END = "</s>"

# Physical key grid used by SuggestionEngine (row, column): the Titan 2 Elite, Z under S.
GRID = {
    **{c: (0, i) for i, c in enumerate("qwertyuiop")},
    **{c: (1, i) for i, c in enumerate("asdfghjkl")},
    **{c: (2, i + 1) for i, c in enumerate("zxcvbnm")},
}


def neighbors(a, b):
    if a not in GRID or b not in GRID:
        return False
    (r1, c1), (r2, c2) = GRID[a], GRID[b]
    return ((r1 - r2) ** 2 + (c1 - c2) ** 2) ** 0.5 <= 1.5


def slips(word):
    out = set()
    for i in range(len(word)):
        for c in "abcdefghijklmnopqrstuvwxyz":
            if c != word[i] and neighbors(word[i], c):
                out.add(word[:i] + c + word[i + 1:])
        if i + 1 < len(word):
            out.add(word[:i] + word[i + 1] + word[i] + word[i + 2:])
        out.add(word[:i] + word[i + 1:])
    for i in range(len(word) + 1):
        for c in "abcdefghijklmnopqrstuvwxyz":
            out.add(word[:i] + c + word[i:])
    out.discard(word)
    return out


def load_dictionary():
    freq = {}
    for name in ("en_base.json", "en_extra.json"):
        for entry in json.load(open(f"{ASSETS}/{name}")):
            w = entry["w"].lower()
            freq[w] = max(freq.get(w, 0), entry.get("f", 1))
    return freq


def relevant_words(freq):
    common = {w for w, f in freq.items() if f >= MIN_REPLACEMENT_FREQUENCY}
    reviewed = set()
    for w, f in freq.items():
        if len(w) < 3 or not w.isalpha():
            continue
        if any(s in common and freq[s] >= f + MIN_FREQUENCY_GAIN for s in slips(w)):
            reviewed.add(w)
    return common, reviewed


TEMPLATE = re.compile(r"\{\{[^{}]*\}\}")
TABLE = re.compile(r"\{\|.*?\|\}", re.S)
REF = re.compile(r"<ref[^>]*?/>|<ref.*?</ref>", re.S)
TAG = re.compile(r"<[^>]+>")
LINK = re.compile(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]")
EXTERNAL = re.compile(r"\[https?://[^\s\]]+ ?([^\]]*)\]")
HEADING = re.compile(r"^=+.*?=+\s*$", re.M)
SENTENCE = re.compile(r"[^.!?\n]+[.!?]?")
TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")


def clean(markup):
    text = markup
    for _ in range(3):  # Nested templates.
        text = TEMPLATE.sub(" ", text)
    text = TABLE.sub(" ", text)
    text = REF.sub(" ", text)
    text = TAG.sub(" ", text)
    text = LINK.sub(r"\1", text)
    text = EXTERNAL.sub(r"\1", text)
    text = HEADING.sub(" ", text)
    text = re.sub(r"'{2,}", "", text)
    text = re.sub(r"^[*#:;].*$", " ", text, flags=re.M)  # Lists and indents are rarely sentences.
    return text


def articles(path):
    """Yields wikitext of main-namespace articles, streaming the bz2 XML."""
    in_text = False
    namespace = None
    buffer = []
    with bz2.open(path, "rt", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if "<ns>" in line:
                namespace = line.strip()
            if not in_text:
                start = line.find("<text")
                if start >= 0:
                    body = line[line.find(">", start) + 1:]
                    if "</text>" in body:
                        if namespace == "<ns>0</ns>":
                            yield body[: body.find("</text>")]
                    else:
                        in_text = True
                        buffer = [body]
            else:
                end = line.find("</text>")
                if end >= 0:
                    buffer.append(line[:end])
                    in_text = False
                    if namespace == "<ns>0</ns>":
                        yield "".join(buffer)
                else:
                    buffer.append(line)


TATOEBA_WEIGHT = 2
# Every 50th Tatoeba sentence is never counted: it is the held-out everyday text that the keyboard
# simulation (ChatSimulationTest) types, so sentence-aware fixes are measured on unseen sentences.
HELD_OUT_EVERY = 50


def is_held_out(sentence_id):
    return sentence_id.isdigit() and int(sentence_id) % HELD_OUT_EVERY == 0
# A pair with a reviewable word is evidence that the word is meant there ("hooks are"), so even
# rare ones are kept: "never seen" must mean never seen, not pruned. Pairs of common words are
# evidence for a fix and must be solid. Tuned with tools/eval_context_correction.py.
MIN_REVIEWABLE_COUNT = 2
MIN_COMMON_COUNT = 20


def count_sentence(sentence, counts, keep, freq, weight=1):
    words = TOKEN.findall(sentence)
    if not words:
        return
    words.insert(0, START)
    if sentence.rstrip().endswith((".", "!", "?")):
        words.append(END)
    for a, b in zip(words, words[1:]):
        if (a in keep or b in keep) and (a in freq or a == START) and (b in freq or b == END):
            counts[f"{a} {b}"] += weight


def count_corpora(wiki, tatoeba, keep, freq):
    counts = Counter()
    pages = 0
    for markup in articles(wiki):
        pages += 1
        text = clean(markup).lower()
        for sentence in SENTENCE.findall(text):
            count_sentence(sentence, counts, keep, freq)
        if pages % 50_000 == 0:
            print(f"  {pages} articles, {len(counts)} pairs", file=sys.stderr)
    print(f"articles {pages}; raw pairs {len(counts)}", file=sys.stderr)

    sentences = 0
    with bz2.open(tatoeba, "rt", encoding="utf-8", errors="ignore") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3 and not is_held_out(parts[0]):
                sentences += 1
                for sentence in SENTENCE.findall(parts[2].lower().replace("\u2019", "'")):
                    count_sentence(sentence, counts, keep, freq, TATOEBA_WEIGHT)
    print(f"tatoeba sentences {sentences}; raw pairs {len(counts)}", file=sys.stderr)
    return counts


def main():
    wiki, tatoeba, books_csv, out_path = sys.argv[1:5]
    freq = load_dictionary()
    common, reviewed = relevant_words(freq)
    keep = common | reviewed
    print(f"dictionary {len(freq)} words; common {len(common)}; reviewable {len(reviewed)}", file=sys.stderr)
    cache = os.environ.get("PAIRS_CACHE")
    if cache and os.path.exists(cache):
        counts = pickle.load(open(cache, "rb"))
    else:
        counts = count_corpora(wiki, tatoeba, keep, freq)
        if cache:
            pickle.dump(counts, open(cache, "wb"))

    # Google Books top pairs and the hand-written pairs: real, common phrases. Give them at
    # least a solid count so they always count as evidence.
    with open(books_csv) as f:
        for row in csv.DictReader(f):
            a, _, b = row["ngram"].lower().partition(" ")
            if a in freq and b in freq and " " not in b:
                counts[f"{a} {b}"] = max(counts[f"{a} {b}"], 50)
    for line in open("tools/en_pairs_curated.txt"):
        line = line.strip().lower()
        if line and not line.startswith("#") and "\t" not in line:
            counts[line] = max(counts[line], 20)

    def keep_pair(pair, n):
        a, b = pair.split(" ")
        return n >= (MIN_REVIEWABLE_COUNT if a in reviewed or b in reviewed else MIN_COMMON_COUNT)

    kept = [(pair, n) for pair, n in counts.items() if " " in pair and keep_pair(pair, n)]
    with open(out_path, "w") as f:
        f.write("# Numen English word-pair counts. See en_pairs_NOTICE.txt for sources and licenses.\n")
        f.write("# Format: first<space>second<tab>count. <s> and </s> mark the start and end of a sentence.\n")
        for pair, n in sorted(kept):
            f.write(f"{pair}\t{n}\n")
    print(f"wrote {len(kept)} pairs", file=sys.stderr)


if __name__ == "__main__":
    main()
