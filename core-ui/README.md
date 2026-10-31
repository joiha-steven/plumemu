# core-ui

Theme (colours, Plum fonts, type scale, sizes, motion), shared components and width classes. See
[docs/design/rules.md](../docs/design/rules.md).

Holds the theme (step B1b), the shared components of the main screen (step C3b) and the
screenshot harness every module's tests use. No screens.
Built on Compose foundation only; no Material.

Namespace: `com.plumemu.coreui`.

## Files

All theme files are in `src/main/java/com/plumemu/coreui/theme/`, the only place in main
sources where colour, dp and sp literals may appear.

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library with Compose; exposes Compose `ui`, `ui-text`, `foundation` as `api`; test fixtures and Roborazzi |
| `theme/Rgb.kt` | `Rgb`, an opaque `0xRRGGBB` colour; hex parsing and the board's rounding |
| `theme/ColorMath.kt` | `mix`, `luminance`, `contrast`, `reach`, `fromHsl`, ported from the design board |
| `theme/ColorPresets.kt` | `TextMode` (Light/Dark), `BackgroundPreset` (8), `AccentPreset` (6, two colour sets) |
| `theme/ColorChoices.kt` | `BackgroundChoice` and `AccentChoice`: a preset or the person's own colour |
| `theme/ColorSets.kt` | `ColorSet`: the ten ready-made sets of Settings › Appearance › Colors, `matching` (the current set, derived from the three colours), A/B/X/Y glyph colours by letter (`ButtonColors`), drawn on the hint key caps through `PlumTheme.buttons` while the set is current (`keyCapLook` in `components/Bars.kt`); tests `ColorSetsTest`, `KeyCapLookTest` |
| `theme/PlumColors.kt` | `PlumColors.derive(background, textMode, accent)`: every colour token (with the keyboard's `tray`, `trayKey`, `trayEdge`, shadow) |
| `theme/CustomPalette.kt` | The 48-swatch "your own colour" palette of the background (8 hues × 6 shades) and the 40-swatch one of the accent (8 hues × 5 shades, not on the board) |
| `theme/PlumComposeColors.kt` | Tokens as Compose `Color`, plus the focus glow |
| `theme/PlumFont.kt` | `PlumFont` (Sans, Serif, Mono) and its tuning row: size factor, line height, weight adjustment |
| `theme/PlumFonts.kt` | Compose `FontFamily` per font; which real face draws each design weight |
| `theme/TypeScale.kt` | `TextSize` (six steps, Small to Largest; `defaultFor(width)`, `heightScale`) and `TypeRole`, the type table of design/rules.md |
| `theme/PlumTypography.kt` | Compose `TextStyle` per `TypeRole` |
| `theme/SecondScreenDimens.kt` | The second screen's picking sizes, Roomy and Compact (margins, type roles, gaps, the calm mark) |
| `theme/SecondScreenInGame.kt` | The second screen's in-game card: `InGameDimens` (time and battery sizes, the battery glyph, the shift steps, 2 dp sideways and 4 dp up and down; the battery part's roles, frame, gaps, chart heights and strokes, `partStyle`) and `InGameColors` (fixed, dim, on black; the part's text, low, ahead, usual and tick colours) |
| `theme/PlumDimens.kt` | Fixed sizes: strip margins, columns, game and system rows, bars and key caps, corners, switch, focus ring, swatches, setup (RP5-O1) the home screen question, side-panel gap, badges |
| `theme/PlumLayout.kt` | `WidthClass`, `CoverShape` (width over height) with its `CoverClass` (landscape, square, tall), `GridSize` (Small, Medium, Large; the cells per class and size in `PlumLayout.gridCell`) and the width formulas (list, list without art, details stop, grid columns per class and size, gaps, cover width and row height, settings, strip height, rows that fit) |
| `theme/PlumMotion.kt` | 180 ms ease-out (slides), 200 ms decelerating glide (`GLIDE_MS`, `Decelerate`), 110 ms fade-in, the press dip (`PRESS_*`, `HINT_PRESS_SCALE`) and the favourite star's `STAR_START_SCALE`, the scan dot's 1.6 s breathing (`SCAN_PULSE_*`, `EaseInOut`), and the one switch that says whether motion is on |
| `theme/ListGlide.kt` | `rememberListGlide`: a lazy list or grid that jumps in layout and glides there by `translationY`, with two strides of rows composed past each edge; `LocalGlideClock` (default `System::nanoTime`) is the glide's clock, which frame tests set to the compose clock |
| `theme/ContentFade.kt` | `rememberContentFade` and `Modifier.fadingIn`: new content fades in (alpha only) when its key changes |
| `theme/Settle.kt` | `Settle`: a value set while composing and settled to 0 on frames, the engine of both |
| `theme/PlumTheme.kt` | `PlumThemeSettings` (stored `text` and `accent`; the drawn `textColor`, `textMode`, `accentColor` are settled by `Readability`; `gridSize`), the `PlumTheme` composable (observes the system animator scale), `ProvidePlumType` (the type alone, no palette or animator-scale observer: the second screen's in-game battery part) and `PlumTheme.colors/type/layout/motion/dimens` |
| `components/SystemBadge.kt` | `SystemBadge`: a system's short name in a `chip` ring with an accent dot, at least 72 x 48, never cut (the home band, later the second screen's card) |
| `components/GlidingPill.kt` | `rememberGlidingPill`: the focus pill of the home lists drawn once by the list behind its rows, gliding from row to row over `GLIDE_MS` on `Decelerate`; `LocalGlidingPill` tells the rows to stop drawing their own; test `GlidingPillFramesTest` (app) |
| `components/FocusTravel.kt` | `rememberFocusTravel`: the focus ring of the home grids (games, Apps) travelling in X and Y from the cell it leaves to the one it lands on over `GLIDE_MS` on `Decelerate`; the grid calls `moved` when the focus moves and reads `offset` while drawing; test `GridFocusTravelFramesTest` (app) |
| `components/Rows.kt` | `GameRow`, `SystemRow` (`hasDot`: the new-games dot) and their looks: the one focus language (fill + dot, current, preview; `MenuOpen` for the game whose panel is open); a game row's title colour read while drawing (`drawnLook`, `GameRowLook.composedAs`), so Preview <-> Normal recomposes nothing; the favorite star after a title (`FAVORITE_STAR`, grows in on Y) |
| `components/FieldRow.kt` | `FieldRow`: a one-line text field drawn as an option row (focused: the focus pill and a caret at the end of a pre-filled text; an optional label column, so a column of fields starts its texts on one line); Fix name…, Use my own name and the ScreenScraper sign-in; `keyboardHint` / `InlineKeyboard`: no full-screen keyboard, the field named in it (`hintText`); `FieldRowFrame`: the same row around a caller's own line (a field typed with Plumemu's keyboard, no text field) |
| `components/OptionRow.kt` | `OptionRow` (the Settings option row: 40 high, 16 sp, optional badge, value at the right; focused, normal, disabled) and `Badge` ("Current", "Default") |
| `components/DrawnBorder.kt` | `Modifier.borderDrawn(width, { color }, corner)`: a border whose colour is read while drawing, so a colour that moves (a slider's preview) recomposes nothing |
| `components/WifiGlyph.kt` | The top bar's Wi-Fi glyph (arcs and a dot on a Canvas, its ink as tall as and centred on the digits beside it) |
| `components/ChargingGlyph.kt` | The top bar's charging bolt (a filled path on a Canvas, its ink as tall as and centred on the digits after it) |
| `components/HintPressed.kt` | A hint's pressed look: `LocalHeldKeys` (the key caps whose button is held, from core-input's `InputRouter.held`), `keyCapNames` (a cap's text to button names), the press animation (60 ms in, 100 ms out, 100 ms minimum, scale 0.92 on the cap) and the touch tracker; tests `HintPressedTest`, `HintPressedScreenshotTest` |
| `components/Bars.kt` | `TopBar` (place with emphasis, `placeAlpha` for the fade, clock, Wi-Fi glyph, battery; `BatteryStatus` draws the percentage with the charging bolt before it), `HintBar`, `FittingHintBar` (hints at their natural width; those that do not fit give way in a given order), `TitledHintBar` (the grid's bar with the focused title), `Hint` (round key cap + label; `hasDot` adds the accent dot after it, an update found; an A/B/X/Y cap filled with its letter's colour while the current set has glyph colours, `keyCapLook`) |
| `components/EdgeFade.kt` | `EdgeFade`: the 32 dp fade at the right edge over content that runs past it; `ListEdgeFade`: the 20 dp fade (`PlumDimens.listEdgeFade`) at the bottom or top edge of a list that scrolls, drawn only while rows continue past it |
| `components/ScanPulse.kt` | `ScanPulse`: the small accent dot that breathes (alpha 35–100 %, 1.6 s, `graphicsLayer`, no recomposition) and `ScanPulseLine`, the dot at the first baseline of a status text while a scan runs; solid with motion off; `LocalScanPulsePhase` pins it for screenshots |
| `components/CentredMessage.kt` | Centred title and line for states the board does not draw |
| `components/Cover.kt` | `CoverTile`, the stand-in cover (flat `sel` tile with the title, in the grid size's style; untitled in the details column) until real box art |
| `components/CoverGlowColor.kt` | `CoverGlowColor`: a cover's glow colour, the average of its image from a 16 x 16 copy, lifted when dark |
| `components/CoverShadow.kt` | `CoverShadow` / `rememberCoverShadow` / `Modifier.coverShadow`: the soft shadow under every grid cover (one small blurred ALPHA_8 corner bitmap per look, drawn as nine slices; the focused cover's deeper one drawn by the grid), its `drawGlow` (the focused cover's tinted glow, same slices) and `drawCoverRim` (the 1 dp rim light) |
| `components/FocusRing.kt` | `DrawScope.drawFocusRing`: the 2.5 dp ring 3 dp out with the soft glow, drawn only (by the parent, over the item) |
| `components/keyboard/` | Plumemu's own on-screen keyboard: `KeyboardLayout.kt` (pages, keys, spans, `KeyboardSpec`), `KeyboardModel.kt` (the rules, pure Kotlin: focus, Up/Down by nearest centre, wrap on a fresh press, shift once/locked, pages, events), `KeyboardState.kt` (the model for Compose), `PlumKeyboard.kt` (tray and keys, taps), `KeyboardIcons.kt` (shift, delete, hide) |
| `src/main/res/font/` | Plum Sans 400/600, Plum Serif 400/600, Plum Mono 400/700 (TTF from `plum-fonts/dist`) |
| `src/main/assets/licenses/` | `plum-<family>-OFL.txt`, the SIL OFL 1.1 text of each font, shipped in the APK |
| `src/testFixtures/java/com/plumemu/coreui/testing/ReferenceShots.kt` | Screenshot harness: four reference shots per screen (optionally per font) in one call; reports every mismatch |
| `src/testFixtures/java/com/plumemu/coreui/theme/ProvideGridSize.kt` | `ProvideGridSize`: one grid size for a subtree (shots and tests) |
| `src/test/java/com/plumemu/coreui/theme/` | Unit tests: tokens, settings, palette, type scale, font metrics, dimensions, layout, motion, animator observer (Robolectric), shot names, font files |
| `src/test/java/com/plumemu/coreui/components/keyboard/` | The keyboard's rules (`KeyboardModelTest`), taps and recompositions (`PlumKeyboardTest`), fit at 576 and 768 (`KeyboardFitTest`), the focused action key's look (`KeyboardFocusLookTest`), shots (`KeyboardScreenshotTest`) |
| `src/test/java/com/plumemu/coreui/theme/TokenSheetScreenshotTest.kt` | Token sheets (colours, type in all three fonts) for a visual check |
