# Open source notices

## Android Open Source Project LatinIME

Pastiera's full virtual keyboard mode uses AOSP LatinIME as a reference for keyboard geometry and includes AOSP-derived keyboard visual assets.

- Project: Android Open Source Project, LatinIME (`platform/packages/inputmethods/LatinIME`)
- Source repository: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- Pinned source revision used as reference: `127336e9f29d69607eab55982324b210279ae8c5`
- License: Apache License, Version 2.0

The Apache License, Version 2.0 text is included in `assets/common/licenses/Apache-2.0.txt` and in the repository under `third_party/licenses/Apache-2.0.txt`.

Pastiera does not import AOSP dictionaries.

## Google Material Symbols / Material Icons

Pastiera uses icon artwork from Google Material Symbols / Material Icons for parts of the app UI and keyboard chrome.

- Project: Material Symbols / Material Icons
- Project page: https://fonts.google.com/icons
- Source repository: https://github.com/google/material-design-icons
- License: Apache License, Version 2.0

The Apache License, Version 2.0 text is included in `assets/common/licenses/Apache-2.0.txt` and in the repository under `third_party/licenses/Apache-2.0.txt`.

## OpenGameArt Typing Sounds

Pastiera includes short typing sound samples derived from CC0 sound effects.

- Project: Keyboard Soundpack #1 [Typing and Single Keystrokes]
- Author: unicaegames
- Source: https://opengameart.org/content/keyboard-soundpack-1-typing-and-single-keystrokes
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)
- Derived files:
  - `res/raw/typing_click_1.ogg`
  - `res/raw/typing_click_2.ogg`
  - `res/raw/typing_click_3.ogg`
  - `res/raw/typing_click_4.ogg`

- Project: Typewriter sounds
- Author: Cassie-OrbitGames
- Source: https://opengameart.org/content/typewriter-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

- Project: Mechanical Sounds
- Author: BMacZero
- Source: https://opengameart.org/content/mechanical-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

Typing sound derivatives are included under `res/raw/typing_*.ogg`.

## Unicode CLDR Emoji Annotations

- Project: Unicode Common Locale Data Repository (CLDR)
- Source repository: https://github.com/unicode-org/cldr-json
- Source path used by generator: `cldr-json/cldr-annotations-full/annotations`
- License: Unicode Data Files and Software License / Unicode License

Pastiera's local emoji search assets under `assets/common/emoji_search/*.tsv`
are generated from Unicode CLDR annotation data by `scripts/generate_emoji_search_assets.py`.
The generated TSV files are filtered to the emoji set bundled with Pastiera and normalized
for compact local lookup.

## Leipzig Corpora Collection / Wortschatz Leipzig

- Project: Leipzig Corpora Collection / Wortschatz Leipzig
- Project pages: https://corpora.uni-leipzig.de/ and https://wortschatz.uni-leipzig.de/
- Companion asset repository: https://github.com/palsoftware/pastiera-dict
- License: Creative Commons attribution terms for the downloaded corpus/frequency-list data;
  the Leipzig frequency dictionary word lists are documented as CC-BY 3.0, while current
  downloadable corpus data may carry corpus-specific terms.

Pastiera's bundled base dictionaries under `assets/common/dictionaries/*_base.json`
and the generated serialized dictionaries under `assets/common/dictionaries_serialized/*_base.dict`
are frequency-list derivatives built mainly from Leipzig Corpora Collection / Wortschatz Leipzig
word-frequency data, with project-maintained filtering, truncation, normalization, and additional
entries. The companion `pastiera-dict` repository distributes larger downloadable `.dict` assets
and their manifests.

Provenance note: the maintained dictionary pipeline and current maintainer-provided sources point
mainly to Leipzig/Wortschatz frequency data. Some older bundled entries predate the current
documentation trail, so their exact upstream corpus IDs are not fully reconstructed here.

## eellak GSOC 2019 Greek Morphological Dictionary

- Project: GSOC 2019 - Development of a Greek open source Morphological dictionary and application of it to Greek spelling tools
- Source repository: https://github.com/eellak/gsoc2019-greek-morpho
- Source data used for `el_base.dict`: `data/spell_dict_with_freq.dic`
- License: GPLv3 for source code; CC BY-SA 3.0 for the produced morphological database

The downloadable Greek base dictionary distributed through the companion `pastiera-dict`
repository is derived from the top frequency-ranked, letter-only entries in the eellak
Greek spelling dictionary with Pastiera-specific filtering, truncation, and normalization.

## English word-pair counts (sentence-aware correction)

`dictionaries/en_pairs.txt` contains word-pair counts built by `tools/build_context_pairs.py`
(details in `dictionaries/en_pairs_NOTICE.txt`) from:

- Simple English Wikipedia (https://simple.wikipedia.org) — CC BY-SA 4.0
- Tatoeba English sentences (https://tatoeba.org) — CC BY 2.0 FR
- Google Books Ngram top English 2-grams via https://github.com/orgtre/google-books-ngram-frequency — CC BY 3.0

Only aggregated counts are included. The file is distributed under CC BY-SA 4.0.

## English recognised words (SCOWL)

`dictionaries/en_known.txt` lists real English words that autocorrect leaves alone, built by
`tools/build_known_words.py` from SCOWL size 60 (http://wordlist.aspell.net).

Copyright 2000-2018 by Kevin Atkinson

Permission to use, copy, modify, distribute and sell these word lists, the associated scripts,
the output created from the scripts, and its documentation for any purpose is hereby granted
without fee, provided that the above copyright notice appears in all copies and that both that
copyright notice and this permission notice appear in supporting documentation. Kevin Atkinson
makes no representations about the suitability of this array for any purpose. It is provided
"as is" without express or implied warranty.

SCOWL also includes material from the Moby Words II lists (public domain), the ENABLE word list
and 12Dicts by Alan Beale (public domain), and the UK English Wordlist with Frequency
Classification by Brian Kelk; see the SCOWL Copyright file for the full attributions.
