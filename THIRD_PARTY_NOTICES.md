# Third-party Notices

Numen, a modified version of Pastiera, is licensed under the GNU General Public License version 3. Some components are derived from third-party open source projects and retain their original attribution requirements.

## Android Open Source Project LatinIME

- Project: Android Open Source Project, LatinIME (`platform/packages/inputmethods/LatinIME`)
- Source repository: https://android.googlesource.com/platform/packages/inputmethods/LatinIME
- Pinned source revision used as reference: `127336e9f29d69607eab55982324b210279ae8c5`
- License: Apache License, Version 2.0
- License text: `third_party/licenses/Apache-2.0.txt`

### Derived scope

Pastiera's full virtual keyboard mode uses AOSP LatinIME as a reference for keyboard geometry and visual assets:

- `app/src/main/java/it/palsoftware/pastiera/inputmethod/aospkeyboard/AospKeyboardView.kt`
- AOSP-derived keyboard background, key, spacebar, preview, popup and selected-popup `.9.png` resources under:
  - `app/src/main/res/drawable-mdpi/`
  - `app/src/main/res/drawable-hdpi/`
  - `app/src/main/res/drawable-xhdpi/`
  - `app/src/main/res/drawable-xxhdpi/`
  - `app/src/main/res/drawable-xxxhdpi/`

### Non-derived scope

Pastiera does not import AOSP dictionaries. Suggestions, status bars, prediction bars, IME lifecycle, settings, PKB behavior and custom dictionaries remain Pastiera-specific unless separately documented.

## OpenGameArt Typing Sounds

Pastiera includes short typing sound samples derived from CC0 sound effects.

### Keyboard Soundpack #1 [Typing and Single Keystrokes]

- Author: unicaegames
- Source: https://opengameart.org/content/keyboard-soundpack-1-typing-and-single-keystrokes
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)
- Derived files:
  - `app/src/main/res/raw/typing_click_1.ogg`
  - `app/src/main/res/raw/typing_click_2.ogg`
  - `app/src/main/res/raw/typing_click_3.ogg`
  - `app/src/main/res/raw/typing_click_4.ogg`

### Typewriter sounds

- Author: Cassie-OrbitGames
- Source: https://opengameart.org/content/typewriter-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

### Mechanical Sounds

- Author: BMacZero
- Source: https://opengameart.org/content/mechanical-sounds
- License: Creative Commons Zero 1.0 Universal (CC0 1.0)

Typing sound derivatives are included under `app/src/main/res/raw/typing_*.ogg`.

## Unicode CLDR Emoji Annotations

- Project: Unicode Common Locale Data Repository (CLDR)
- Source repository: https://github.com/unicode-org/cldr-json
- Source path used by generator: `cldr-json/cldr-annotations-full/annotations`
- License: Unicode Data Files and Software License / Unicode License

Pastiera's local emoji search assets under `app/src/main/assets/common/emoji_search/*.tsv`
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

Pastiera's bundled base dictionaries under `app/src/main/assets/common/dictionaries/*_base.json`
and the generated serialized dictionaries under `app/src/main/assets/common/dictionaries_serialized/*_base.dict`
are frequency-list derivatives built mainly from Leipzig Corpora Collection / Wortschatz Leipzig
word-frequency data, with project-maintained filtering, truncation, normalization, and additional
entries. The companion `pastiera-dict` repository distributes larger downloadable `.dict` assets
and their manifests. Conversion and serialization scripts live under `scripts/`.

Provenance note: the maintained dictionary pipeline and current maintainer-provided sources point
mainly to Leipzig/Wortschatz frequency data. Some older bundled entries predate the current
documentation trail, so their exact upstream corpus IDs are not fully reconstructed here.

## English word-pair counts (sentence-aware correction)

`app/src/main/assets/common/dictionaries/en_pairs.txt` contains word-pair counts built by
`tools/build_context_pairs.py` (details in `app/src/main/assets/common/dictionaries/en_pairs_NOTICE.txt`) from:

- Simple English Wikipedia (https://simple.wikipedia.org), CC BY-SA 4.0
- Tatoeba English sentences (https://tatoeba.org), CC BY 2.0 FR
- Google Books Ngram top English 2-grams via https://github.com/orgtre/google-books-ngram-frequency, CC BY 3.0

Only aggregated counts are included. The file is distributed under CC BY-SA 4.0.

## English word-triple counts (autocorrect ranking)

`app/src/main/assets/common/dictionaries/en_triples.txt` contains word-triple counts built by
`tools/build_context_triples.py` (details in `app/src/main/assets/common/dictionaries/en_triples_NOTICE.txt`) from
Tatoeba English sentences (https://tatoeba.org), CC BY 2.0 FR. Only aggregated counts are included.

## English common misspellings (Wikipedia)

`app/src/main/assets/common/dictionaries/en_misspellings.txt` lists common misspellings and their fixes, built by
`tools/build_misspellings.py` from Wikipedia's "Lists of common misspellings/For machines"
(https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines), CC BY-SA 4.0.
The file is distributed under CC BY-SA 4.0.

## English first names (US Social Security Administration)

`app/src/main/assets/common/dictionaries/en_names.txt` lists common first names that autocorrect leaves alone, built by
`tools/build_first_names.py` from US Social Security Administration baby-name data (public domain, a work of the
US government), via the top-1000-per-year table at https://github.com/hadley/data-baby-names.

## English recognised words (SCOWL)

`app/src/main/assets/common/dictionaries/en_known.txt` lists real English words that autocorrect leaves alone, built by
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

## Test data (not shipped in the app)

- `app/src/test/resources/simulation/tatoeba_heldout_en.txt`: 3,000 English sentences from
  [Tatoeba](https://tatoeba.org) by Tatoeba contributors, CC BY 2.0 FR, used by the keyboard
  simulation tests. They are held out of the word-pair counts.
- `app/src/test/resources/simulation/chat_messages_en.txt`: messages written for Numen's tests.
