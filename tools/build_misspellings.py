#!/usr/bin/env python3
"""Build Numen's list of common English misspellings ("recieve" -> "receive").

Source: Wikipedia:Lists of common misspellings/For machines (CC BY-SA 4.0), fetched with
  https://en.wikipedia.org/w/index.php?title=Wikipedia:Lists_of_common_misspellings/For_machines&action=raw
Only entries with a single, lowercase-letters-only correction are kept. Autocorrect applies an
entry only when the typed word is not a word it knows, so real words are never rewritten.

Usage (from the repository root):
  build_misspellings.py RAW_LIST.txt app/src/main/assets/common/dictionaries/en_misspellings.txt
"""
import re
import sys

ENTRY = re.compile(r"^\s*([a-z]+)->([a-z]+)\s*$")


def main():
    source, out_path = sys.argv[1:3]
    entries = {}
    with open(source, encoding="utf-8") as f:
        for line in f:
            match = ENTRY.match(line)
            if match and match.group(1) != match.group(2):
                entries.setdefault(match.group(1), match.group(2))
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("# Common misspellings from Wikipedia (CC BY-SA 4.0). Built by tools/build_misspellings.py.\n")
        out.write("".join(f"{typo}->{fix}\n" for typo, fix in sorted(entries.items())))
    print(f"{len(entries)} misspellings", file=sys.stderr)


if __name__ == "__main__":
    main()
