# UI

- Every value comes from the theme in `core-ui`: colours, type styles, spacing, radii. No colour
  or size literals in screens; the guard in `verify.sh` fails on them.
- Units are dp and sp. The reference device is the Retroid Pocket 5 (768×432 dp); layout must
  also hold at 576×432 (Retroid Pocket Nova) and about 837×471 (AYN Thor). Rules by width class
  are in [../design/rules.md](../design/rules.md).
- Text keeps its size across devices. A wider screen shows more; a narrower one slides.
- Colours are derived, never picked per screen: background, text (light or dark) and accent go
  in, every token comes out of the rules in [../design/rules.md](../design/rules.md) › Colour.
  Tests cover the Graphite and Paper presets and one custom background. Contrast is enforced by
  `Readability` in `core-ui` and `ColorSetsTest`: a text colour the person picks needs 7:1 on the
  background (`Readability.TEXT_MIN_CONTRAST`) and an accent 3:1 (`ACCENT_MIN_CONTRAST`); derived
  secondary text reaches 11:1 where the text allows, facts on the focused row 6:1, small muted text
  4.6:1, key-cap borders and chips 3:1; ready-made sets keep text at 4.5:1 (also on the focused row's
  fill) and the accent at 3:1. A combination that fails is never offered, and a stored one is drawn
  adjusted, so every screen stays readable.
- One focus style everywhere: a tinted pill with an accent ring (rows, `focusPill`) or an accent ring
  (covers, icons, swatches); no dot means focus. Accent text without a fill means "current, not
  focused". Two focused rows on one screen is a defect.
- Fonts: the three Plum fonts (Sans, Serif, Mono), each with its tuning row.
- A row's right-hand text: `year · region` ("1994 · USA") in a single system's list with box art (the year alone where `listShowsPlace` says there is no room: narrow beside the details, Largest text; the region alone without box art); the system's short name in
  Recent, Favorites and Search, except at the narrow details stop, where the 56 dp list peek shows the year.
- Focus must always be visible: the focused row or cell has the focus background or ring; a
  screen never has nothing focused.
- Empty, loading, error and very long text states are designed, not left to chance: an empty
  system is hidden, a long title is cut with an ellipsis, a failed load says what failed.
- Every screen change ships with screenshot tests at 768×432 and 576×432, dark and light
  ([testing.md](testing.md)). A screenshot that differs from the design is a defect, not taste.

## Motion

All durations are tokens in `PlumMotion` (`core-ui`); screens never type a number.

- Only `transform` and `alpha` change (`graphicsLayer`, draw phase); layout is never animated, so
  a frame costs no measure or layout pass on a weak GPU and CPU.
- The strip slides over `DURATION_MS` (180 ms), ease-out. New content fades in over `FADE_IN_MS`
  (110 ms), ease-out, alpha only.
- Every list and grid glides after its jump (home, Apps, search, the game panels, Settings) over
  `GLIDE_MS` (200 ms) on a decelerating curve: the list scrolls in layout at once, then its rows are
  drawn where they were and travel to their place. The focus pill (lists) and the focus ring (grids)
  travel between rows and cells the same way. One duration everywhere keeps the whole app feeling
  like one surface. A held key retargets each repeat linearly at the repeats' speed, so the rows
  never stop between repeats.
- A grid still scrolls only when the row below the focus is not whole, and its new row slides in
  clipped by the grid's edge.
- No dropped frames at the screen's refresh rate; when a frame budget and a motion conflict, the
  motion goes.
- With the system animation scale at 0, or Settings › Advanced › Animations off, everything jumps.

Details per screen: [../design/rules.md](../design/rules.md) › Shapes and motion. Budgets for the
rest of the app: [performance.md](performance.md).

