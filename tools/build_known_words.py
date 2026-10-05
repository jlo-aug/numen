#!/usr/bin/env python3
"""Build Numen's English recognised-word list from SCOWL (wordlist.aspell.net).

The 49k-word suggestion dictionary lacks many real words ("bothers", "drooped", "nosy",
"congrats"), and aggressive autocorrect treats an unknown word as a typo. This list only marks
words as real so autocorrect leaves them alone; it is never used for suggestions or corrections.

Uses SCOWL size 60 (the size spell checkers ship as "normal"): American, British and Canadian
spellings, plus contractions. Proper names and abbreviations are left out. Possessives are left
out too ("dog's"): autocorrect already accepts a known word followed by 's.

Usage (from the repository root):
  build_known_words.py SCOWL_DIR/final app/src/main/assets/common/dictionaries/en_known.txt
SCOWL: https://sourceforge.net/projects/wordlist/files/SCOWL/ (tested with 2020.12.07).
"""
import glob
import json
import re
import sys

ASSETS = "app/src/main/assets/common/dictionaries"
MAX_SIZE = 60
KINDS = ("english-words", "american-words", "british-words", "canadian-words",
         "english-contractions", "american-contractions", "british-contractions", "canadian-contractions")
WORD = re.compile(r"^[a-z]+(?:'[a-z]+)?$")


def main():
    final_dir, out_path = sys.argv[1:3]
    # Words the dictionaries hold in lowercase. A word they only hold capitalised ("Curt",
    # "Billy") is still listed, so lowercase "curt" counts as real and is not capitalised.
    known = set()
    for name in ("en_base.json", "en_extra.json"):
        known |= {entry["w"] for entry in json.load(open(f"{ASSETS}/{name}")) if entry["w"].islower()}
    words = set()
    for kind in KINDS:
        for path in glob.glob(f"{final_dir}/{kind}.*"):
            if int(path.rsplit(".", 1)[1]) <= MAX_SIZE:
                for line in open(path, encoding="latin-1"):
                    word = line.strip()
                    if WORD.match(word) and not word.endswith("'s") and word not in known:
                        words.add(word)
    with open(out_path, "w") as f:
        f.write("# Numen English recognised words: real words autocorrect must leave alone.\n")
        f.write("# Built by tools/build_known_words.py from SCOWL size 60 (Copyright 2000-2018 Kevin Atkinson;\n")
        f.write("# permissive license, see THIRD_PARTY_NOTICES.md). One lowercase word per line, sorted.\n")
        f.write("\n".join(sorted(words)) + "\n")
    print(f"wrote {len(words)} words", file=sys.stderr)


if __name__ == "__main__":
    main()
