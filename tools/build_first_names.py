#!/usr/bin/env python3
"""Build Numen's list of common English first names, which autocorrect leaves alone when capitalized.

Source: US Social Security Administration baby names (public domain, US government work), via the
top-1000-per-year table at github.com/hadley/data-baby-names (baby-names.csv). A name is kept when
it reached MIN_SHARE of a year's births since 1940.

Usage (from the repository root):
  build_first_names.py baby-names.csv app/src/main/assets/common/dictionaries/en_names.txt
"""
import csv
import sys

FIRST_YEAR = 1940
MIN_SHARE = 0.0001


def main():
    source, out_path = sys.argv[1:3]
    best = {}
    with open(source, newline="") as f:
        for row in csv.DictReader(f):
            if int(row["year"]) >= FIRST_YEAR:
                name = row["name"].lower()
                best[name] = max(best.get(name, 0.0), float(row["percent"]))
    names = sorted(n for n, share in best.items() if share >= MIN_SHARE and n.isalpha() and len(n) >= 2)
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("# Common first names (US SSA baby names, public domain). Built by tools/build_first_names.py.\n")
        out.write("\n".join(names) + "\n")
    print(f"{len(names)} names", file=sys.stderr)


if __name__ == "__main__":
    main()
