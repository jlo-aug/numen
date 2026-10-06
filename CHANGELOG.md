# Changelog

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
