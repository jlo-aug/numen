# Changelog

## Numen 1.2 (October 2026)

- Number lock: double-tap Alt to type numbers and symbols, and it now stays on after Space until
  you tap Alt again.
- Autocorrect learns from your typing reports:
  - "its" / "it's" by the next word ("and its mostly" -> "it's mostly", "it's own" -> "its own"),
  - spelling mistakes with the wrong vowel ("sepereate" -> "separate", "definatly" -> "definitely"),
  - short slips both neighbouring words explain ("the cat hs fur" -> "has"),
  - "id" -> "I'd" when the next word shows it ("id like"), and words run together
    ("alot" -> "a lot", "aswell" -> "as well"),
  - long words missing doubled letters ("acomodation" -> "accommodation").
- Fewer wrong fixes on real misspellings: a word is only split in two when the halves go
  together in real text, so "contuned" no longer becomes "con tuned".
- Autocorrect now knows the Titan 2 Elite's real bottom row (Z X C V B N M, no gap), so slips on
  those keys are judged correctly, and its word-pair data was rebuilt to match.
- All-caps words ("TXU") and first names ("Amy") are left alone.
- A new way of choosing fixes, fitted to real typing: each candidate is weighed by how likely your
  fingers made that slip on the Titan 2 Elite's keys and how well it fits the words around it. It
  also catches typos that make another real word once the next word shows it ("a letter form my
  mom" -> "from").
- Backspace right after an autocorrect undoes it even when punctuation followed the word
  ("back plate!" -> "backplate!"); your punctuation stays. Thanks to the user who reported it.
- Plurals of known words ("autocorrects") and two-word compounds that aren't a common phrase
  ("backplate") are left as you typed them.
- A fix made at the space is looked at again once you type the next word, and switched when the
  next word clearly shows another one ("thes is" -> "the", then "this is").
- Misspelled contractions are fixed even without the apostrophe ("deosnt" -> "doesn't",
  "coudlnt" -> "couldn't", "dnot" -> "don't"); a real word never turns into one.
- Rare words that are usually slips are fixed when the next word shows it ("fir the" -> "for the",
  "nit sure" -> "not sure").
- Typos that spell another common word are fixed when the words around them leave no doubt
  ("he best way" -> "the best way", "thank you for you help" -> "your help"), learned from how
  often people mix the two up (Aalto ITE typing dataset). Two very common words are only swapped
  for a phrase people really write.
- A lone letter or a two-letter slip is fixed once the next word shows what it was ("o tired" ->
  "so tired", "te best" -> "the best", "t the shops" -> "to the shops"). Texting shorthand ("u",
  "r", "k") and abbreviations ("uk") stay.
- Measured on held-back real typing (Aalto 136M keystrokes on physical keyboards; Aalto ITE typing
  dataset on phones): physical-keyboard typos fixed 47% -> 57%, wrong fixes 10.3% -> 9.4%, and
  correct words changed 0.7% -> 0.4%. On the same typing, Gboard's spell checker fixes 47%.
- The keyboard log in Diagnostics covers about the last 400 words, still only in memory.
- A leaner app: Numen no longer carries Pastiera features it doesn't use. Gone are support for
  other hardware (Clicks keyboard cases, Q25, BlackBerry Key2, Minimal Phone), trackpad gestures
  and Shizuku, dictionary and layout downloads, update checks and "what's new" pages. Numen now
  asks for no internet, Bluetooth or overlay permissions.
- English only, all the way through: the other languages' dictionaries, layouts and translations
  are gone, and so are language switching, the language button and app-language settings.
  Autocorrect is trained on English typing, so they did nothing for it. Text replacements now
  always use the English set, even on a phone set to another language.
- Smart quotes are now a simple "Curly quotes" switch that gives “English quotes”. It used to
  default to German guillemets.
- Dictionaries, layouts and settings files that earlier versions downloaded or imported are
  deleted once; an old imported dictionary could otherwise replace the tuned English one.
- On a phone set to Turkish, corrections no longer turn "i" into "İ".
- A keyboard bar saved with the old language button no longer shows the emoji button twice.
- Removed settings that did nothing on an English keyboard: per-layout themes and symbol
  overrides, and "Ctrl shortcuts follow layout". A theme you had picked for the English layout
  carries over.

## Numen 1.0.1 (October 2026)

- Caps Lock no longer switches itself off while you type.
- Holding a key right after the keyboard comes back types one Alt character, not repeated letters.
- Stronger autocorrect: 76% of typos fixed in testing, up from 61%, and correct words changed less
  often (1 in 4,600). It now:
  - picks the fix that fits the words before ("pick up some kilk" -> "milk"),
  - treats a neighbouring key like any other slip, on the first letter too ("Vool" -> "Cool"),
  - fixes slips on names you've typed twice in a conversation, and leaves common first names alone,
  - fixes days and places mid-sentence ("next Sudnay"), and missed spaces next to names ("andMary"),
  - knows Wikipedia's list of common misspellings ("arguement" -> "argument"),
  - turns "ill" into "I'll" when the next word shows it ("ill check"), and "I ll" or "don t" into
    "I'll" and "don't".
- 40 more slang words are left alone ("tryna", "lowkey", "hmu", "smth", "yeet"), and so are words
  held for emphasis ("sooo", "pleaseee").

## Numen 1.0.0 (October 2026)

First release, based on Pastiera 0.86.

- The keyboard bar stays hidden while you type on the keys. Press SYM to show it.
- Stronger autocorrect, on by default: missed and extra letters, missed spaces, two-letter slips,
  mangled long words. Names, slang and contractions are left alone, and 44,000 more English words
  are recognised.
- Sentence-aware fixes, on by default ("its"/"it's", "dome" → "some").
- Backspace undoes the last correction. A word you undo and keep twice is learned.
- Home screen with setup status, typing stats and quick switches.
- Short first-run setup and simpler settings.
- Own app ID, icon and name. No update checks.

Pastiera's history: https://github.com/palsoftware/pastiera/releases
