# tools/fonts

`build_jost.py` builds the app's Jost faces, `core-ui/src/main/res/font/jost_{400,500,600,700,800}.ttf`,
from the Jost variable font in `source/` (Indestructible Type, OFL 1.1, Google Fonts release 3.710,
checked by SHA-256). Jost has no Vietnamese horn, hook above or dot below, so without this build
Vietnamese text would mix two fonts. Per weight the script:

- instances the variable font (static faces: Android 6 has no variable fonts);
- draws the horn (U+01A0, U+01A1, U+01AF, U+01B0, U+031B), the hook above (U+0309), the dot below (U+0323), the arrows
  ← → ↑ ↓ ↔, the check mark ✓ and the dong sign (U+20AB), each from strokes measured on that instance,
  so they keep Jost's weight from 400 to 800;
- composes every Vietnamese letter (all vowels with the five tones, capitals included) from Jost's
  own letters and spacing marks. A tone goes to the right of the circumflex and above the breve;
  a capital stack is squashed until it fits under the 1070 ascent (the build prints the highest
  glyph);
- gives the new letters their base letters' kerning classes, and adds `mark`/`mkmk` anchors for
  text that keeps marks apart;
- subsets to Latin-1, Latin Extended-A, Vietnamese, punctuation and the UI symbols, with the
  `kern`, `mark`, `mkmk` and `tnum` features. Vertical metrics stay as Jost's (ascent 1070,
  descent 375); the family stays "Jost" (its OFL names no Reserved Font Name).

Run `python3 tools/fonts/build_jost.py` (needs fontTools; `--out DIR` writes elsewhere). The
output is identical byte for byte on every run. Never edit the TTFs; change the script.

`specimen.py OUT.png [FONT_DIR]` renders the built faces beside Plum Sans (needs Pillow and
HarfBuzz's `hb-view`); `specimen.png` is its output for the current build.
