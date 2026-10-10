A keyboard app for the Unihertz Titan 2 Elite, tuned for typing in English.

In ancient Rome, a numen was a small spirit that protected the places people loved. The character you met on first launch is the numen of your keyboard.

Numen is a fork of [Pastiera](https://github.com/palsoftware/pastiera) 0.86 (GPL-3.0). It keeps the keyboard bar hidden while you type and adds stronger autocorrect, sentence-aware fixes and typing stats. It is not an official Pastiera release.
---
## Links
#### [Source code & releases](https://github.com/jlo-aug/numen)
#### [Report an issue](https://github.com/jlo-aug/numen/issues)
---
## Based on Pastiera
#### Creator
Andrea Palumbo (PalSoftware) · [Support the creator on Ko-fi](https://ko-fi.com/palsoftware)

#### Main developers
Patrick Zauner, Andrea Palumbo

#### Additional contributors
Justin Mitchell, NeoTheFox, Oleksii Ilienko, Nikola Vukobrat, Mircea Horea IONICĂ, Nikita Tseykovets, Ivan Bulanov, Ratmir Karabut, Troidem, Vasu Bhatia, Zsolt Sz. Sz. Raven, Burekmaster (SVG logo assets), glebkuchay, drpepper240, Matej Drobnič, astroboii47
---
#### Pastiera beta testing team
Laggy Luke, Vittorio, Emmanuel, Sadako, NotTeganQuinn, DrumSyBeat, [Shane Craig (ShaneCraig.Tech)](https://shanecraig.tech/), [Returning Retro (Retro Jake)](https://www.returningretro.com/)

#### Special thanks
The Unihertz Titan 2 Discord, the Unihertz Titan 2 Facebook page and everyone there for the outstanding support!
---
#### [Original project: Pastiera on GitHub](https://github.com/palsoftware/pastiera)
---
## Open source notices
#### Android Open Source Project LatinIME
Numen's on-screen keyboard (inherited from Pastiera) uses AOSP LatinIME as a reference for keyboard geometry and includes AOSP-derived keyboard visual assets.

License: Apache License, Version 2.0. Source: [platform/packages/inputmethods/LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME) at commit `127336e9f29d69607eab55982324b210279ae8c5`.

#### Google Material Symbols / Material Icons
Numen, like Pastiera, uses icon artwork from [Google Material Symbols / Material Icons](https://fonts.google.com/icons) for parts of the app UI and keyboard chrome.

License: Apache License, Version 2.0. Source: [google/material-design-icons](https://github.com/google/material-design-icons).

#### OpenGameArt Typing Sounds
Numen includes short typing sound samples, inherited from Pastiera, derived from CC0 sound effects:

- [Keyboard Soundpack #1 (Typing and Single Keystrokes)](https://opengameart.org/content/keyboard-soundpack-1-typing-and-single-keystrokes) by unicaegames, CC0 1.0.
- [Typewriter sounds](https://opengameart.org/content/typewriter-sounds) by Cassie-OrbitGames, CC0 1.0.
- [Mechanical Sounds](https://opengameart.org/content/mechanical-sounds) by BMacZero, CC0 1.0.

#### Unicode CLDR
Emoji search keywords are generated from [Unicode CLDR](https://github.com/unicode-org/cldr-json) annotation data and filtered for the bundled emoji set (originally prepared by Pastiera).

#### Leipzig Corpora Collection / Wortschatz Leipzig
Bundled base dictionaries are frequency-list derivatives built mainly from [Leipzig Corpora Collection / Wortschatz Leipzig](https://corpora.uni-leipzig.de/) word-frequency data, with filtering, normalization and additions by the Pastiera project.

#### English word-pair counts (sentence-aware corrections)
Numen's word-pair counts are built from [Simple English Wikipedia](https://simple.wikipedia.org) (CC BY-SA 4.0), [Tatoeba](https://tatoeba.org) sentences (CC BY 2.0 FR) and the Google Books Ngram top English 2-grams via [orgtre/google-books-ngram-frequency](https://github.com/orgtre/google-books-ngram-frequency) (CC BY 3.0). Only aggregated counts are included; the file is distributed under CC BY-SA 4.0.

#### SCOWL word list
Numen's list of recognized English words is built from [SCOWL](http://wordlist.aspell.net) size 60. Copyright 2000-2018 by Kevin Atkinson; permission to use, copy, modify, distribute and sell is granted provided the copyright and permission notice appear in all copies. SCOWL includes public-domain material from Moby Words II, 12Dicts and ENABLE.

#### English word triples
Numen's word-triple counts for ranking corrections are built from [Tatoeba](https://tatoeba.org) English sentences (CC BY 2.0 FR). Only aggregated counts are included.

#### Aalto ITE typing dataset
Numen's real-word confusions ("you" typed for "your") are built from the [Aalto ITE typing dataset](https://doi.org/10.5281/zenodo.12528163) by Aalto University (CC BY 4.0). Only aggregated counts are included. Autocorrect was also tuned and tested on this dataset and on the [136M keystrokes dataset](https://userinterfaces.aalto.fi/136Mkeystrokes/) (Dhakal et al. 2018), neither of which is included in the app.

#### Common misspellings
Numen's list of well-known misspellings is built from Wikipedia's [Lists of common misspellings](https://en.wikipedia.org/wiki/Wikipedia:Lists_of_common_misspellings/For_machines) (CC BY-SA 4.0). The list is distributed under CC BY-SA 4.0.

#### First names
The first names autocorrect leaves alone come from US Social Security Administration baby-name data (public domain).
