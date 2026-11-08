# Design rules

All sizes in dp.

## Reference sizes

| Device | Screen | Size in dp | Class |
|---|---|---|---|
| Retroid Pocket 5 (reference) | 5.5", 1080p, 16:9 | 768×432 | Wide |
| AYN Thor (upper screen), Odin 3 | 6", 1080p, 16:9 | about 837×471 | Wide |
| Retroid Pocket 4 Pro | 4.7", 1334×750, 16:9 | about 667×375 | Narrow |
| Retroid Pocket Nova | 4.5", 1280×960, 4:3 | 576×432 | Narrow |
| Retroid Pocket Nova (measured on the device) | 4.5", 1280×960 at 320 dpi, 4:3 | 640×432 with Android's 48 dp bar, 640×480 full screen | Narrow, 640 and up |

Screenshot tests take 768×432 and 576×432 for every screen; the 4:3 sizes (`ReferenceShots.Size.FourThree`
640×480 and `FourThreeWithBar` 640×432) are taken for home (systems open, games, details, art grid, search),
Settings, setup and Largest-text search; a new screen with a layout that depends on the width adds them too
(`sizes = ReferenceShots.Size.entries`). The Thor's second screen is covered by `LayoutCThorScreenshotTest` and
the 837×471 text-size shots as before.

## The strip

- Margins 24 left and right, 14 top, 12 bottom (9 under a hint bar). Top bar 18 high at Small or one line of the place name's 16 step (16 sp on the font's line: `PlumLayout.topBarLine`) when that is taller, so it grows with the font and size (23.1 dp in Jost at Small, 25.4 at Large: the strip starts 49.4 down, the board's 49.3); the 20 name's taller line reaches into the margins above and below, never cut; while a keyboard tray is up it keeps 18 × the size's factor (`topBarHeightTyping`) and draws the name in the top bar's 13, so the field and the 36 dp keys fit; bottom hint bar 22 high, 10 between the top bar and the content. **Footer zone**: one token for every screen with hint keys (home list, grid, Search, Settings, setup, the panels over them): 9 above the bar, the 22 bar, 9 under it, 40 in all at Small (`PlumDimens.footerGap`, `footerBelow`, `PlumLayout.footerZone`; the bar grows with the text size, the 9s do not). Pinned by `FooterZoneTest`.
- Hint bar info line (grid): the focused title is `infoLineTitle` (16 / 600) and the meta after it `infoLineMeta` (11 since the mono step: the board's 11.33 left the ramp for its nearest step) in `infoMeta` (the board's `text2`: the page 0.84 toward the text on dark, #C9C5BB on Graphite; 0.80 on light, about #4A4740 on Paper; `muted` where a custom palette leaves it under 4.5:1 on the page), centred with the keys in the 22 high bar (the bar and the grid's rows do not change). The meta follows the title after a 9.33 gap (`PlumDimens.infoLineGap`, the board's 14 px), with no dot between them (the meta's own " · " stays between year and publisher). One rule: the title takes 16, or steps down to 13 (`gridFocusTitle`, `smallFact`) so that no hint key is dropped; a fit check, never a per-font or per-size case. The hint that gives way to the meta by design (`dropBeforeMeta`: L2 R2 Switch system) does not step the title down unless 13 would keep it too: at 768 x 432 Small that hint goes either way, so the title stays 16. Checked by `HintTitleStepTest`, `InfoLineGapTest`. A titled tile's title stays `gridFocusTitle` (13).
- Hint bar: its labels and key caps, and the bar's own height, grow with the text size only up to factor 1.1 (Medium; `TextSize.HINT_FACTOR_CAP`, `PlumLayout.hintScale`), so the bar keeps SELECT on a 576 list in Sans and Mono at the default size; pinned by `TypeScaleTest`, `HomeViewToggleHintTest`. Each hint at its natural width, 9 apart from a label to the next key's collar on every bar (16 to the next cap, less the collar's 7; the board's wide keys have 13 px padding each side, 4 dp wider than the old caps, and 9 keeps A, Y, X, START and SELECT on a 576 list in Sans and Mono at the default size). When they do not all fit, hints give way as **Hint drop order** (next) says, the one place that order is written; pinned by `HintDropOrderTest`. A hint is never cut and hints never overlap.
- **Hint drop order** (the one place; `HINT_DROP_ORDER`, `GRID_HINT_DROP_ORDER`, `TYPING_HINT_DROP_ORDER` in `home/HomeBars.kt`, `APPS_GRID_HINT_DROP_ORDER` in `home/AppsHints.kt`, `TitledHintBar` in core-ui). Short of width a bar drops hints from the left of this list; a key not in a bar's list stays, and only a bar still too narrow after the whole list loses keys from its right end. A (Play / Select / Open / Type) is never dropped on any bar. L2 R2 is first everywhere it exists. Where a bar's hints are not drawn, they are neither shown nor tappable.

  | Bar | Leaves first to last | Never leaves by order |
  |---|---|---|
  | Games list, details stop, Search results | L2 R2 Switch system, L1 R1 Size (grids only), R3 Sort, B Back, SELECT | A, Y, X, START |
  | Cover grid (one system, Recent, Favorites) | L2 R2, Y Favorite, L1 R1 Size, B Back, SELECT | A, START |
  | Apps grid (and installed-games grids) | L2 R2, X Search, START Menu, B Back, SELECT | A |
  | Systems column | (SELECT) | A, X, START |
  | Typing (Search keyboard, panel and Settings fields) | L1 R1 Cursor, SELECT Shift, Y 123 | A, B, X, START |
  | Game panels (menu, Info, messages) | none: A and B only | A, B |

  The two grid bars carry the focused title, then its year and publisher (the meta), in the same room: L2 R2 goes before the meta (by design, and only if that keeps the meta); the title steps from 16 to 13 so that no other key is dropped; if still short the meta goes (L2 R2 always leaves before the title is cut; a title that shows whole at 13 with the same keys beats one cut at 16); then the title is cut with an ellipsis on one line, down to a minimum (`PlumDimens.hintTitleMin`, 96 dp: about 12 characters at the 13 style), and only a bar still short with the title at that minimum drops keys, in the table's order. **A key leaves only once the title is at its minimum**, so a 70-character title keeps B Back, L1 R1 and SELECT where the room allows (the long rows below). Search results and the games list have no title in the bar, so a long title changes nothing there. SELECT outlasts B because by touch SELECT is the only way between a list and its grid, while B has the system's back gesture.

  What stays, measured in Sans, Graphite (`HintDropOrderTest`; "long" is a 70-character focused title, "short" Mega Man X on a cover grid and Gallery in Apps):

  | Bar | 576x432 Small | 576x432 Larger | 640x480 Small | 640x480 Larger | 768x432 Small | 768x432 Larger |
  |---|---|---|---|---|---|---|
  | Systems column | A X START SELECT | same | same | same | same | same |
  | Games list | A B Y X START SELECT | A Y X START SELECT | A B Y X START SELECT | same | A B Y X R3 START SELECT | same |
  | Details stop (narrow only) | A B Y START | same | same | same | no stop: the list's | the list's |
  | Cover grid, short | A B L1R1 SELECT | same | A B Y L1R1 SELECT | A B L1R1 SELECT | A B Y L1R1 SELECT | same |
  | Cover grid, long | A B L1R1 SELECT | same | A B Y L1R1 SELECT | A B L1R1 SELECT | A B Y L1R1 SELECT | A B Y L1R1 SELECT |
  | Apps grid, short | A B START SELECT | same | A B X START SELECT | same | A B X START L2R2 SELECT | A B X START SELECT |
  | Apps grid, long | A B START SELECT | A B SELECT | A B X START SELECT | A B START SELECT | A B X START SELECT | same |
  | Search results | A B X Y START | same | same | same | same | same |
  | Search typing | A B X START Y L1R1 | same | same | same | same | same |

  Hints are listed left to right as drawn. A games list at 576 Larger has no room for SELECT-and-B together (A, Y, X, START and SELECT need 614 to 670 dp of 528).
- Columns: systems 200, games list at least 400, details 248, 24 between columns.
- **Systems column alignment**: every system label starts at the page margin (24), the
  same left line as the top bar text and the first hint key cap, focused or not, so the label never moves when
  focus does. The column itself starts at the screen edge and is margin + systems column width wide (200 × the text size factor: 224 at Small, 314 at Largest). The focus
  pill (the focused row is the same pill everywhere) starts 14 before the label, so 10 from the screen edge,
  and runs to the column's right edge at the row height; the label does not move with focus. Settings' sections column follows the same rule (below).
  Checked (pill inset 10, label 14 inside it, centred on the row) by `HomeSystemsAlignTest` at four sizes and all six text sizes.
- **System info band**: the games list of a system starts with a
  band at the list's left edge, as the list's **first item** (it scrolls away with the rows; in both column focuses; Enter and
  Back change nothing): `SystemBadge` (the V2 board's RAISED chip, `SeatLook.raisedChip`: radial gradient, inset light, inset shade, ring and drop, all derived from the theme; the short name
  in capitals in the `systemTag` role (13 / 700) tracked 0.09 em in `strong`, in the person's chosen font; 34 dp tall times the text size's height
  scale, 12 padding each side, corners 8, wider with the text, never cut; no dot, no outline), 14 to its right the facts line (`meta` 13, `text2`, centred on the badge, one line: it drops the kind, then the
  maker, to fit between the list's left edge and the screen's right margin), 8 under the badge a 1 dp hairline in `sel` (drawn by the list under its focus pill, so the first row's slab, at rest or gliding, always covers it: `drawsBandRule`, `BandRuleAndFooterFadeTest`), then
  the rows (one row fewer on screen while the band is in view; focus on the first game scrolls the list fully to its top, a deep remembered row opens with the band out of view). Nothing for Recent, Favorites, Apps or a system the platform
  data does not know; a system with no facts shows the badge alone. About text: under the facts line, `meta` 13 `text2`, 4 below it (`factsRowGap`), up to 3 lines, whole or not shown (never an ellipsis), in a column as wide as the facts line's room; the band grows to hold it (the hairline stays 8 below the taller of badge and text). Settings › Home screen › System info off: no band. Checked by
  `SystemInfoBandTest` (numbers at 576, 768 and 837 wide, Small and Largest; a tap keeps its row under the finger; no drift
  on Enter / Back); shots `home_systems_info*`.
- **Wide** (screen ≥ 768): games focused shows the 48 dp system peek, the list and the details
  side by side; the list takes the extra width (`list = width − 368`, so 400 on the RP5 and about
  469 on the Thor).
- **Second screen up** (layout C): while its window shows, a list has no details column and no
  narrow details stop and is `width − 96` (as without box art on a narrow screen); a grid is unchanged. Back in the
  same frame when the window goes (Second screen, below).
- **Without box art**: wide screens keep the 248 details column
  beside the `width − 368` list, text only and top aligned where the cover box starts with box art:
  the title (`detailsTitle`), 4 under it "Super Nintendo · USA" (`meta`, the details' `·` separator;
  the system alone without a region), 12 under that the facts table (`smallFact`, as with box art). No
  cover box, synopsis, year, publisher or genre. Narrow screens have no column and no details stop;
  the list is `width − 96`. Shots `home_details_text*`.
- **Narrow** (screen < 768): the list stays 400 (368 at 640); at the games stop the details column takes the room from the end of the list to the
  right margin (`homeDetailsFit`; it replaces the peek cut by the screen's edge): the wide layout (cover on top, title, facts block with its cells
  measured against that width, `LocalDetailsColumnWidth`), the same drop order when the height is short. Under the cover's 140 dp of room
  (576, and 640 at Largest text) the column is left out, never cut; with the systems focused it is off screen and not composed. Details are a third stop. In the details stop
  the list slides out until 56 dp of it show (its years, 12 dp from the edge) and details get
  `width − 104` (472 on the Nova): title and facts on the left, cover 228×164 on the right (so the peek shows the text first), description underneath.
  Settings below 640 (576): with a section focused the options column runs on under the right fade, so values may be
  hidden until an option is focused and the column widens (the
  fitted options column below is for 640 and up only).
- **Narrow, 640 and up** (4:3, the Nova's 640 dp; `PlumLayout.narrowListWidth`, `isNarrowFitted`,
  `settingsOptionsPreviewWidth`): a preview on the right of the open systems (or sections) column must end inside
  the 24 margin, or its right-hand column (year, region, system code in Recent and Favorites; a Settings row's value)
  is cut at the screen edge or off screen.
  - With box art (details beside the list on this screen) the games list is `min(400, width − systemsOpen − 24 − 24)`
    where `systemsOpen` is the margin plus the systems column (224 at Small, scaled by the text size): 368 at 640 at
    Small, 395 at 667, 400 from 672; a text size that would leave it under 320 (Huge and Largest at 640) keeps 400
    with the fade, as at 576. It covers the games stop (list at 72, details from 24 after the list **to the right margin**) and the details stop (`width − 104`, ends at the right margin).
  - Without box art, or with the details on the Thor's second screen, the list is still `width − 96` (544 at 640, 571
    at 667) once the games have the focus; only **while the systems are open** its width is the same `narrowListWidth`
    (`HomeStrip`, `previewFits`), so Recent, Favorites and every system show their year, region or code whole. The width
    changes at the moment the focus changes column; no stop depends on it (no details).
  - Settings with a section focused: the options column is `width − 272` (368 at 640, sections are 200 + margin +
    gap) so labels and values both fit (a long label wraps or is cut by its own row, the value stays); it widens to
    `width − 48` when an option is focused (`OptionsColumn`, `isPreview`). Checked in Vietnamese at Large and Largest.
  - Grids keep `width − 96`: the cover grid's preview with the systems open still runs under the fade, its last column ending at the screen's edge at every width (left as it was); the Apps grid's reaches the right margin (below).
  Checked by `HomeFourThree480BoundsTest` / `HomeFourThree432BoundsTest` (every home state, Recent with and without
  art), `SettingsSectionPreviewVi*Test` and `FourThreeScreens*BoundsTest` (Settings, setup, search).
- Systems focused with no room for the whole details column: the list runs to the right margin, its right-hand text on that margin, no third-column piece and no right edge fade. The list changes width once, at the column change; its right-hand text moves with the slide, drawn, not laid out.
- Systems focused: the details column (wide) shows only its cover, or nothing without box art: its text would be cut mid-word at the screen edge (as on narrow screens). The strip rests at its start; content past the right edge runs under a 32 dp
  fade. No fade where nothing continues (grids). The Apps grid's preview shows whole columns only, and its last whole column ends exactly at the right margin, the clock's and the hint bar's, at every width and text size: the room the whole columns leave is shared between their gaps (the first cell stays, the gaps are equal), drawn per cell so a touch lands where the cell is drawn; it has no fade and nothing of it is cut. The spread follows the strip's own position (1 at the systems' stop, 0 at the games'), so while the strip slides or a finger drags it the cells return to the full grid's places with its motion, never in a jump, and show what the move brings in (`AppsGridWindow.kt` `AppsSpread`, `AppsGridWholeCellsTest`). Only the list's last row may end short; a grid with room for one column only cannot be spread.
- Peek width 48 by default (adjustable 0–120 in Settings). What the peek shows is Settings › Home screen › Left strip: Game counts (default; each system's count, current in the accent, right-aligned in the row), System codes (the
  system's 3-character code in the count's place and style, same row positions, so the column lines up with the names when it
  opens, where the rows go back to their counts; whole at Largest, `HomeLeftEdgeBoundsTest`) or the letter rail: the letters of the focused list (first letter of each game's sort name, so "The" is skipped; digits under "#"), centred in the peek in equal
  slots over the column's height, the focused game's letter in the accent at weight 600 (`meta` /
  `smallFactCurrent`, 13); at least `railLetterMinHeight` (14 dp, grows with the text size) per
  letter, else a window of letters around the focused one. None for Recent and for a list sorted by
  last played. Names are hidden in the peek, so a long name in a wide font never pokes in; counts show
  again with the names when the system column opens.
- System rows (home column and the peek) are drawn at least 40 high, all equal (34 × the text size above Small). A count from 1,000 is shortened to whole thousands ("1k", "99k") in the row, so it is no wider than a 3-digit count: it stays inside the peek and leaves a long name its room (B1, B3).
- Rows that fit: game list rows are designed 34 high and drawn at least 38 (Settings, option and field rows stay 40; Settings' section rows are 40 too, so the rows share one height; a focused two-line option row keeps `settingsTwoLineRowPadding` (6) above and below its text; the colour presets sit on the panel's text column, 14 in from each side, so 4 a row in the wide 248 panel, 10 apart; the panel's text column stays 14 in from the panel edge like every panel, moving it to the 240-wide grid would leave 0 on the left; at the largest text sizes the colour name and sample rows under the four rows are left out from the end when they do not fit). The 2 dp gap under a game row is part of its tap area, so a row's touch area is at least 40. The count follows the screen height and counts the gaps (8 on the RP5, 7 on the RP4 Pro, 9 on the Thor at Small and at Large, 7 at Largest; unchanged at 576x432, 640x480, 768x432 and 832x468 when the V2 pitch of 40 replaced 36 + 2). Above Small every box around text (rows, system rows, top bar, hint bar, key caps, setup buttons) grows by the size's factor (`TextSize.heightScale`, so a game row is 49 and a Settings row 58 at Largest; 34 × 1.1 = 37.4 at Large, held at the 38 minimum) and fewer rows fit: 7 game rows on the RP5 at Largest. The system column widens by the same factor so a system name keeps its line.
- Top bar, left: where you are. Systems focused "18 systems · 1,284 games"; games "Super
  Nintendo · 248 games"; Settings "Settings › Appearance" (and "› Background" in a panel stop).
  From the Covers bar: the name 20 / 600 in `text`, the count 11 in `muted`, 10.67 apart on one baseline. The name is drawn in 20
  when its whole line fits the room the right-hand items leave, else in 16, and only then ellipsised; the count gives way first
  (`placeNameStyle`, `TopBarFitTest`: "Nintendo Entertainment System" at 576x432 Largest is whole at 16 in Jost, cut at 16 in
  Sans, Serif and Mono).
- Search's top bar: the typed text starts on the results' text line (peek + column gap + row padding, the list's tokens; `SearchTextLineTest`), so the query and the titles under it share one left edge; a label wider than that room (a large text size) pushes it on. The field's well and collar reach past its text (`searchFieldInsetX` + the collar), and the title and the count keep `topBarItemGap` (14) from what is drawn, not from the text box (`SearchTopBarAlignTest`). The results rest on whole rows above the tray and the hint bar, the room left stays empty (`SearchResultsWholeRows*Test`).
- Top bar, right: clock, then the Wi-Fi glyph, then the battery percentage, `topBarItemGap` (14) between
  each. The glyph (three arcs over a dot, `WifiGlyph`) shows only while the device is connected through
  Wi-Fi and is gone otherwise; its ink is as tall as the battery digits' ink (`PlumFont.digitHeightEm`, about 0.75
  of the font size, so it grows with the text size) and centred on them within 1 px, in the text's colour. It follows Show battery: no battery, no glyph.
  Spoken as "Wi-Fi connected".
- Charging bolt (`ChargingGlyph`): a filled bolt `topBarChargingGap` (3) before the battery percentage while the
  device is on its charger (charging, full, or plugged in but not charging), gone on battery or while the state is
  unknown. Its ink is as tall as the digits' ink and centred on them within 1 px, in the text's colour; it follows
  Show battery. Spoken as "Charging". The second screen's in-game card says "Charging · 82%" in words instead.

## Focus and current

One language on every screen, so a glance says where the cursor is:

| State | Look |
|---|---|
| Focused row (the slab) | One slab on every focused row (games list, search, Settings rows, the game menu card, with motion on or off), one glass recipe for every colour set: each layer is a step of perceived lightness (CIE L*) from what lies under it, so a dark page, a light page and a custom colour draw the same object, a rimmed pane of glass over a shadow. A light page must stay below white (the sets' lightest is about L* 93): on pure white the white rim has nothing to show against and only the shadow is left. Tokens are derived from the theme (`theme/FocusSlab.kt`, `SlabLook.of`), read while drawing, never hard-coded. **Fill**: a vertical gradient of the page (on a raised card, of the card: `stopsOnCard`) lit toward white, so it keeps the page's hue, stops at 0, 46 % and 100 %, made opaque. Dark themes: L* +7.5, +5.5 and +4 (`FILL_TOP_L` to `FILL_BOTTOM_L`), up to a third more on black (`DARK_GAIN`: the eye takes a step on a dark page for less). Light themes: L* +3.5, +3 and +2.5 (`LIGHT_TOP_L` to `LIGHT_BOTTOM_L`): the pane stays just above the page, below white, so the white rim shows round it as it does on dark. **Rim** 0.667 dp (`SlabDimens.edge`) inside the box, never an even outline: white on every theme; along the sides L* 12 above the fill on a dark theme (`RIM_SIDE_L`) and white itself on a light one, and at two opposite corners L* 18 above the fill or as far as white goes (`RIM_LIT_L`), fading out over 16 % of the diagonal (`SlabLook.rim`). No inset shadows. **Lift**, drawn before the fill as one cached bitmap (`SoftImages.drops`: exact rounded-rect Gaussians composited once, never stacked strokes or `BlurMaskFilter`): near 0.5 dp down, sigma 0.5 dp, L* 1.33 off the page; far 2.67 dp down, sigma 2.67 dp, L* 2.33 off the page (`LIFT_NEAR_L`, `LIFT_FAR_L`): a shadow on every theme, black on a dark one and brown (#3C2814) on a light one, never a light glow (on a black page it is as deep as black allows). The glow is the accent at the slab's own strength on every theme (`PlumComposeColors.slabGlow`, sigma `SlabDimens.glowSigma`). **Bar**: 4 x 17.33 dp, radius 2, centred, `SlabDimens.barInset` 8.67 in from the slab's outer edge (the 1 px border plus the CSS 12 px), with its own accent glow (sigma 2.4 dp, `barGlowSigma`). It is on every focused row on every theme: one focus language. Corners half the row's height up to `PlumDimens.cornerFocusPill` (13.33). **No ring and no dot**; `strong` text, weight 500 at the row's size, and the label does not move when focus lands (the text starts `gameRowPadding` 23.33 in). The slab is `SlabDimens.height` 46.67 tall (the board's 70 px) and spans the row's width: a 38 or 40 row is overhung by half the difference above and below (`slabOverhang`; layout does not move), so a list leaves that room at its ends (`ListGlide` widens the list by `SlabDimens.maxOverhang` each end and clips there, `GlidingPill`'s clips bleed by it; with animations off `ListGlide.extra` is that room; the list host's `contentPadding` is `glide.extra`; a list without slabs passes `slabRoom = false`). The focused row's year and region are brighter and heavier than a resting row's (`SlabLook.year` / `region`, `FocusSlab.yearInk`). On a light theme the games list (home and search, `rememberGlidingPill(blob = true)`) also draws the board's blue blob under the slab (`SlabDimens` blob values, `drawBlob`; `PlumComposeColors.slabBlob`, the accent at `FocusHalo.LIGHT_SLAB_PEAK` 0.5): a 2D Gaussian measured on the Paper board's 768 list, sigma 0.285 x the slab's width across, 60 dp tall, centred 18.67 dp below the slab's centre; it already holds the glow, so the slab's own glow is left out there. It is drawn from a cached bitmap without dithering (`SoftShadowStepTest`). Checked by `FocusSlabSameObjectTest` (each layer's step on dark, light and mid-tone pages), `SlabBoardValuesTest`, `SlabCssRowTest` (a rendered row), `SlabListHaloTest`, `SlabMotionOffTest`, `FocusSlabReadabilityTest`, `GlassCustomColorsScreenshotTest`. Not followed yet: the kit boards draw the system list's focus as accent text and a dot, and a dialog's as a focused primary button; ours still puts the slab on the system column. System column: the same slab, starting `gameRowPadding` (14) before the label, so it is inset from the screen edge by margin - 14 (10). The accent dot that remains means "new games" (system row), never focus. **Carried** (Settings > Library > Systems, A picks a system up): the row turns solid accent with an `onAccent` ring and `onAccent` text (`Modifier.carriedPill`), so it is told from focused on every preset. Setup's cards keep their own corners (`cornerSetupChoice`, 10) focused or not (`focusPill(shape = ...)`); display-only sample rows draw the look with `focusPillSample` (no press, no touch) |
| Focused cover, swatch or button | A key or swatch is a collar part: focus lights its groove and the halo glows outside it (`PlumSwatch`, `Modifier.collar`), no ring. A button or a switch is a flat part: focus draws the same rim as a cover's, without the scale (Flat parts, `drawGluedRim`). Elsewhere a 2.5 dp accent ring, 3 dp away, inside the **focus halo** (below). **Focus F3 (the grid cover and the Apps tile; one rim, shared with buttons and switches: `FocusLift` holds its width, colour, alpha and glow sigma, `drawRimStroke` draws it)**: the item grows to `FocusLift.SCALE` (1.035) about a pivot that keeps it inside the grid (last column its right edge, first column its left edge, top row its top edge, any other cell its centre; an Apps icon its centre), so at rest the grid stays flush to the right margin and no column or cover size changes. A `FocusLift.rimWidth` (2 dp) rim sits glued on the item's frame (a cover's glass frame, corner 10; an Apps tile's icon, its own mask corner), concentric, `RIM_ALPHA` 0.96, near-white on a dark page and the page's text ink on a light one; under it a tight glow (sigma 2.25 dp) and a small depth shadow (4 dp down, sigma 4.5 dp; an Apps tile has the glow only, its name stands below). No accent colour. Arriving: the lift grows and the rim fades in over `FOCUS_GROW_MS` on `Decelerate`; the item the focus left shrinks and its rim fades out over `FOCUS_LEAVE_MS`: the rims cross-fade, none travels; animations off switch in one frame; nothing animates at rest. The cell scales by a canvas transform in its own draw and the grid draws light and shadow under the cells and the rim over them from one cached image (`SoftImages.drops`) and one stroke: no layer, no blur (`FocusOutline`, `FocusLiftDraw.kt`, `FocusOutlineTest`, `FocusLiftTest`, `FocusDrawCostTest`). A press dips the item about its cell (Apps: icon) centre and the rim dips with it; a finger's tap moves focus first, so a tapped cover gets the same focus. |
| Focus halo (decision 0011) | A soft glow in the accent colour just outside today's ring, fading to transparent: `PlumDimens.focusHaloReach` (10 dp) in `FOCUS_HALO_STEPS` (7) bands, alpha falling as (1 - t)^2 from the peak. **Covers (the focused grid cover): the accent at a 55 % peak (`FocusHalo.COVER_PEAK`) on every theme, Paper included, starting 2 dp outside the ring (`focusRingKeyline`). Rows: the focused slab's own glow, the board's `0 0 22px` accent at 0.3 on every theme, plus on a light theme's games list the blue blob (see the slab row). A focused collar part (button, switch, slider, segment, swatch, choice, field, key) also glows on a light theme**: its glow colour is the accent itself at 24 % (the light board's 30 % read back at the edge: 0.12), so a primary button, whose groove and cap are both the accent, still stands off a resting one (`PlumComposeColors.partGlow`, `FocusHalo.partLook`; on a dark theme it is the halo unchanged). The colour is the accent with its lightness clamped to 0.50–0.66 (a dark accent still shows, a bright one does not glare); the peak alpha is 32 %, falling to 16 % as the clamped colour's luminance rises from 0.45 to 0.80 (smoothstep). Maths and tokens: `theme/FocusHalo.kt`; it arrives as `PlumComposeColors.focusGlow` (transparent on a light theme; a light row's blob is `slabBlob`). A collar part's glow is not the row halo's profile: `drawPartGlow` (`components/Collar.kt`) follows the board's `0 0 22px` shadow, a Gaussian edge of sigma 7.33 dp (`CollarDimens.glowSigma`; the slider's collar and knob 4.67, `knobGlowSigma`: the board's 14 px), half the peak at the edge, in `GLOW_BANDS` (8) bands to 2.2 sigmas: it shows 14 dp out on Graphite (board 14), about 12 on Paper (board 11.3 to 12). Drawn as stroked bands, never a blur (a mask filter does not draw on hardware canvases before Android 9) and only outside the shape, so it is never laid out, never covers the item, and a focus move still costs no measure. One function draws it everywhere (`drawFocusHalo` / `drawHaloOutside` in `components/FocusRing.kt`): the row pill (`focusPill`, the gliding pill of the lists and the game menu's `PanelPill`, `focusPillSample`) starts at the pill's edge, a cover, swatch or button ring starts at its outer edge (`focusRingOutset`, 5.5 dp). A list's clip cuts it at the list's top and bottom edge, like the pill |
| Current, not focused (system while games have focus, section while options have focus, the year of the current game in a peek) | accent text, weight 600, no fill |
| Preview column (games while systems have focus) | `muted` text, no fill; the remembered row one step brighter (`text2`) |

**Right-hand column of a game row**: the year and the region as two right-aligned columns ("1994" `rowYear` Mono 13, "USA" the tag `tag` Mono 11, no spacing, 42.67 and 38 wide times the text scale: the Main board's grid with the region column widened for the larger caps; both grow with the text size) in a system's own list where it has the room (`listShowsPlace`: wide screens below Huge text, and the full-width list while the second screen is up). A row with no year known (no box art, a result still being read, none found) leaves the year's cell empty and keeps its region in its column, never moved. Elsewhere one text: the year alone (`rowYear`) in the list on narrow screens, at Huge and Largest text and at the narrow details stop, the region alone (`tag`) when no year is known; in Recent, Favorites
and Search, which mix systems, the system's short name (`abbr`, at most 5 characters: NES, SNES, GBA, PS1 ...) in the region's caps (`tag`; the Search board's `sys` style, 12 px 600, its 1.4 px spacing drawn as the list's 0.1 em). Resting rows draw them in `muted` (the board's #9A9DA4 / #8C9098 on Graphite; its Paper #6F6B63 / #7A766D fall under the 4.6:1 small-text floor, so `muted` there too); the focused slab draws the year brighter (`SlabLook.year`) and one face heavier, the region brighter (`SlabLook.region`) and as heavy on dark themes, one face heavier on light ones, stepping from the text weight setting's faces; the current year in a peek is accent 600. All on the title's baseline. A long title is cut first; the short name is never cut. At the narrow details stop (list peek 56 dp) the mixed lists show the year or region instead. Checked by `GameRowTrailingTest` (core-ui), `RowTrailingTest`, `RowYearRegionFitTest`.

**Favorite mark**: a star (U+2605, black star) 8 after the title, `smallFact` (13 sp), `meta` colour on every row, focused or
not (accent stays for focus and current). On Y it appears with alpha 0→1 and scale 0.6→1,
180 ms on `Decelerate`, `graphicsLayer` only; no motion when animations are off. A title too long for
the row is cut before the star, never the star.

In the home lists (games and system column) the pill glides from the row it leaves to the row it lands on, like the PS Vita's: the list draws it behind its rows (`rememberGlidingPill`), over `GLIDE_MS` on `Decelerate`, transform and draw only; a move
during a move starts from where the pill is drawn, a held key keeps it with the focus; with animations off it jumps. The game menu, Fix name, Fix box art and Search's results glide the same way (`rememberPanelPill`: their rows differ in height, so the focused row hands the pill its own box). Other screens' pills jump.

Both grids' focus is F3 as described in the row 'Focused cover, swatch or button'; on a scroll the rows glide (see Lists glide) and the lift and rim stay on their item.

Never two filled rows on one screen. The focused item is always on screen (grids scroll so its row shows).
In a list that scrolls the focus never rides the edge: while the list can still scroll that way, the
row past the focused one is whole too, on every frame (from the Thor's hold).
Lists therefore keep three rows past the focus with motion on (the room a 200 ms glide and a quickening hold
need) and one with it off; see Shapes and motion.

**Lists that scroll show it.** Everything that scrolls runs under the bar or into the panel's edge and fades there, one recipe everywhere: `PlumDimens.listFadeBand` 12, a linear ramp to nothing at the edge, the same top and bottom.
- **Home** (`BarFades`, `home/BarRun.kt`). The strip is laid out from the top bar's bottom to the hint bar's gap (`footerGap`, 9, above the hint bar; to the screen's edge with the hint bar off); the systems column, the games list, Search and the grids take that whole run, and one fade lies across the screen's whole width, tight under the top bar and ending at the run's foot, painted in the background colour over every column. So the faded rows end as far above the hint keys as the keys stand above the screen's edge (`BandRuleAndFooterFadeTest`). What does not scroll stays where it was (`inStrip`). The bars stand over the fade.
- **Rest.** A list's first row stands at the strip's top, where it always did. Once scrolled, the room left beside the whole rows is shared evenly, so the same part of a row shows under each bar (`barRows`). The last row at the true end rests whole, clear of the fade. Focus moves as before: the context rows are whole, a tapped part row comes out whole.
- **Less than a third.** At rest a row showing under a third of itself at an edge is left out; the focused row never, and a heading goes with a row left out under it. Never mid-glide, or rows would pop. The systems column, open or as the numbers beside a list or a grid, holds one centred line in each row, so it leaves out a row under half of itself: under that only glyph tops or feet would show. With the hint bar off the strip is taller and the grids rest in it (`BarRoom.taller`, `HintsOffRestTest`).
- **Top fade.** Drawn while a column runs under the top bar, and not while a focused row or cover stands in its reach (the band and `SlabDimens.listEndRoom`), so a focus slab keeps its edge. The bottom fade is always drawn: the rest position keeps a focused row clear of it.
- **Panels and menus** (`BarRunClips(ramps = true)`). The same ramp as an alpha mask at the list's own edges, under the title and into the card's edge, short of the focused row. No chevrons: the fade says more follows. A hard cut across a row is a fault. The ramp stops short of the focused row's slab; a finger that takes that row past the edge gets the whole ramp.
- **Settings' columns, Info's facts, Statistics, Setup** fade at their window's edges with the same band and curve; a row cut at a column's top is hidden only under a third.
- **Settings' rests** (`SettingsScrollEdges.kt`, `restWithPeeks`). Same band, with what a panel of options adds. Where content continues past an edge a part of the next row shows under the band (`EdgePeek`, the band plus the slab's overhang), context rows giving way to it down to one row, going back as well as forward; none where it does not. The focused row is whole inside the window with a margin, its shadow drawn outside the rows' clip and fading out by itself on every side (`ListGlide.keepsGlowRoom`), and the window ends `SlabDimens.listEndRoom` above the panel's foot. A tall row (two lines or more at the larger text sizes) is never left as one line under the top band: the rest shows it whole, or at least two peeks of it, or none of it, with room under the last row where the list needs that. A panel that overruns by a few px gives up that much of its end room rather than scroll. A tall focused card takes room from the header: header parts leave whole (`PanelListColumn`, `PanelListLayout`). A key never leaves a panel's text block half gone: the scroll goes on the way it went, to the block's top or until only its tail lies under the band, keeping the focused stop on screen, so Up always moves; on the first stop the text above comes back as far as the stop stays on screen; a finger rests where it lifts (`PanelBlocks`). The column grows at its foot to reach a rest and shrinks when that room is no longer needed. With less room than a peek needs the kept rows win and the peek may be short. Forms above the keyboard tray keep every px (no `bringRoom`, no `endRoom`).
- **The game menu's ends.** A text in a row cut at an edge shows by half or more, or not at all: at the list's end the list keeps the room to move up, and at a cut bottom row the text goes with what the band hides, taking no tap there (`game/PanelListEnds.kt`).
- Facts joined by " · " that do not fit one line go one a line, with no dot.
Tests `ListsUnderBarsTest`, `PanelRowsFadeTest`, `HomeScrollCueTest`, `SettingsSectionsFadeEndTest`, `AppsGridCutRowTest`, `SettingsEdgesTest`, `RestWithPeeksTest`, `SettingsPanelWholeRowsTest`, `SettingsTopEdgeTest`, `SettingsBoxArtSourceFitTest`. The Apps grid's preview beside the systems shows whole columns (`AppsGridWindow.kt`, `AppsGridWholeCellsTest`). A games list's focused row is brought into view again when rows are added, removed or moved above it (`GamesListRowsAboveTopTest`).

## Mastered mark

A trophy after the title of a game whose achievements are all earned (RetroAchievements): `PlumDimens.masteredMarkSize` 12
square, drawn (no font glyph), colour token `meta`, after the favourite mark with a `favoriteMarkGap` before it, centred on the
title line. No counts. A focused row keeps it where it is and in `meta` (the focus pill only changes the row's background and text,
so the mark stays readable on it). Its spoken name is `achievement_mastered`. Shots `home_row_mastered`.

## Achievements in Info

Under the facts, in their scroll (so Up and Down step by row, and a finger scrolls). First the summary ("Achievements 12 of 45 ·
27%", then "Hardcore 10 of 45" when any are hardcore), then one row per achievement, `achievementRowGap` 8 apart (the same gap
between the summary and the first row). A row: badge `achievementBadgeSize` 36 square on the facts' text line, then title
(`text`), description (`text2`), points and earned date (`muted`). A locked achievement uses the `_lock` badge and `muted`
text, with the badge at `LOCKED_BADGE_ALPHA` 0.6. A badge that fails to load is a neutral `sel` square. A game found by its name (not its file's hash) has one
more quiet line, "Matched by name" (`muted`, `meta`, the Hardcore line's style), under the summary lines. Other states are one quiet
line on the same text line. Numbers asserted by `AchievementsInfoBoundsTest`; shots `game_info_achievements_*`.

## Cards


- **Game panel card insets and pitch (the board's menu card).** The card is 293.33 dp wide (`CardDimens.menuWidth`, the board's 440 px menu card; 360 at Small for Play with and Other versions, `CardDimens.pickerWidth`, grown by the text size factor (432 at Large) up to 60% of the screen, so "Automatic (ask which emulator)" and its "Current" badge, or "RetroArch: mednafen_gba", sit on one line) and on a narrow screen `width - 48`. Everything in it is `panelInset` (2.67 dp, the board's 2.7) from its edge on BOTH sides: a row's slab starts and ends there, so it is centred in the card (the board's slab is 4 px from the left and 24 px from the right, which looks lopsided). Row text, value and chevron, the title, body lines and Info's facts all keep `panelTitleStart` (20.67) from the slab's edge or the inset, 23.33 dp from the card's edge on each side (`OptionRow(sidePadding)`). Rows in the card are `rowHeight` apart with no gap (`panelRowGap` 0: pitch 40, board 40). `GamePanelV2Test.slabInsets*`, `GameCardBoundsTest.menuInsetsAndPitchAreTheBoardsAt768`.
- **Game panel place.** The card sits on the page's outer margin at the right (`stripMarginSide`, 24), from the top bar's gap (10 under the bar) down to the hint bar's gap: no part of it reaches into the bar gaps. Nothing is drawn over the page behind it, no scrim, edge fade or solid column: the list and the grid keep their own look right up to the card's shadow, and the wide details column alone fades out with the card (its alpha is 1 minus the card's own slide progress, read while drawing, so it is not half blank when the card opens nor cut by a short card on the way out; it jumps with animations off) (only the gaps at the card's rounded corners are filled with the page colour, so the home cover's corner does not show there) (a board dialog's scrim covers its whole frame, and the panel's area is only the strip, so a scrim would end in a hard line at the bars). `GamePanelV2Test.cardIsOnThePageMargins*`, `pageBehindTheCardIsDimmedEvenly*`.
- **Game panel list end.** A menu row cut by the card's end shows its text by half or more, or not at all, as a row cut at the top does: a sliver is cut away and a row left with no text goes whole (`hiddenAtEndPx`), never the focused row, and what is hidden takes no tap (`ignoringHiddenEnd`).
- **Game panel foot.** Info's Back sits in the card's foot, at its bottom, with the facts above it taking every dp left (the facts' window always fills down to Back's gap, `FactsFit.viewport` = the room left; what does not fit ends with a cut row and the list's fade; `InfoFactsFillTest`). The footer list ends at the card's own edge, so the slab's shadow is not cut inside the card: one tone under Back. `GamePanelV2Test.backHasOneFooterTone*`, `infoFactsAreWhole*`.
- **Light-theme glows, measured with room** (`plum_button_glow`, `slider_glow` shots; 40 dp of page all round, so no neighbour or edge cuts them): a focused slider's collar on a light theme glows with `sliderLightGlowSigma` (7.8 dp; the knob keeps 4.67) to 13.5 dp on Paper (board 13.3). A button's glow reaches about 14.5 dp on Graphite and 13.5 on Paper (the board's 8.7 to 14.7 and 11.3 to 12.7). A focused cover glows with the kit's accent glow on light themes too (Cover focus, below); `drawCoverLightGlow` (the earlier blue-grey haze) is no longer drawn.

The game panel (menu, Play with, Info, messages and the core question) is a raised card on the page's right margin (width and place: the three paragraphs above), corners 17.33 (`CardDimens.corner`, the board's 26 px), the board's PANEL gradient (`PlumComposeColors.card` `top` to `bottom`: the tone ladder's raised step, L* +7 at the top and +5 at the foot above the page, in the page's own hue (Tone ladder under Colour), so a blue page has a blue card and a cream page a cream one; `fill` is their mean, for what is drawn over the card flat), the CSS 0.67 dp border inside the box (white at 14 % with its top side at 22 % on dark; the board's brown at 6 % with a white top side on light), and the board's two shadows (`0 2px 4px` at 0.4 and `0 30px 60px` at 0.6, brown at 0.45 of that on light; the far one at the depth-cut reach, 2.25 dp down, sigma 2.25, so it stays close to the card) as cached Gaussian images. The gradient is drawn because the board draws one (the spec wins over decision 0011 for cards). A list's edge fade takes the card's colour where the fade sits. `RaisedCardTest`. No 768 screen board draws a card; the width is the Parts sheet's menu card. The title is the game's name. A menu row that opens something ends in a muted chevron, 12 after its value; its value (`meta`) is shown only when real. The core question's No and Yes are `PlumButton`s side by side under the text, right-aligned, 10.67 apart (the board's 16 px), 18.67 under the body (28 px, the gap that gives way at the largest text), Yes (Primary, focused first) rightmost, No (Secondary) left of it. The question's body is `text2` on a 28/19 line, 6.67 under its title (`CardDimens.question*`). Checked by `GameCardBoundsTest` at 768, 576 and 832 and Largest text; shots `game_card_*`. Inside the card: 15 dp above the title (`CardDimens.padTop`, the board's 22 px) and 15 dp below the last row or button (`CardDimens.padBottom`); a list's bottom padding scrolls with the rows, so a focused row always rests fully inside the card, above it. The core question (buttons only) hugs its content from the same top edge: its height is title, body, buttons and the padding, nothing more (when the largest text leaves it short of height, the gap above the buttons gives way, never the 15 below them); a list panel fills the column. The title carries no accent dot (it carried no state). Checked by `GameCardBoundsTest` (hugging, and the focus walked down every menu row at Largest at 576, 768 and 832).

The second screen's tiles (the figures on the game, system and Recent cards) are glass, not the card's flat fill (`Modifier.glassTile`, `PlumComposeColors.glass`): a vertical gradient over the page (dark: white at 17 %, 7 % at 46 %, 4.5 % at the foot; light: white to the background 0.75 toward a warm white `#FFFDFA`), a 0.67 dp edge brighter at the top, a short shade along the foot, and the card's shadow cut out under it. A tile is no control, so decision 0011's no-gradient rule does not reach it. On those cards a meter's fill runs from the accent to `accentLight` (a quarter toward white) with a faint glow at its end, the latest bar runs `accentLight` to the accent, an empty day's stub and the rule above the about text are `PlumComposeColors.line` (the tone ladder's divider), and the page has the accent glow in its top right corner (`backdropGlow`, peak 15 % dark, 11.5 % light).
The second screen's Android app card follows the game card: the name in the game-title style with an "APP" tag at the right, a large centred icon (the initial on a disc when there is no icon), and the package in the mono file-name style. It keeps the game card's margins; a long name or package is cut with an ellipsis.

## Settings

- **List ends**: the options and sections columns end `SlabDimens.listEndRoom` (the slab's overhang plus its glow, 13 dp) above the hint keys' gap, as the home lists do, so a focused last row's shadow fades out instead of ending in a line. The window clip is tightened at draw time (`ColumnFollower.bleedBelow`): a row cut by the bottom edge stays cut there under the fade; with a row wholly below the edge nothing of it shows (its control's glow reaches above its top), so only the room before that is open; with no row below, the whole room is open. Checked by `SettingsListEndsTest` (832x468, 768x432, 640x480, 576x432, both themes) and `SettingsListEndsScreenshotTest`. `GlidingPill` fades its outside shadow, glow and blob to transparent (an alpha mask over the shadow's own layer, across the clip's whole width, never a page-coloured fill that would paint over neighbours) over the last `SlabDimens.tailFade` (12 dp) before each clip edge, so a drop shadow that reaches past the room (Paper's 37 to 42 dp) ends smoothly instead of in a line (the Paper blob was cut at 16/255), beside the pill as well as under it. The home systems column has only 6 dp between its last slab and the hint bar, so no line is measurable there.
- **Panel option lists** (`PanelLazyList`): an option an edge cuts shows under the fade as a list that continues, and nothing of it past the window (`BarRunClips(ramps = true)`). A "Current" badge sits on its option's title line, the detail below keeps the column's width. Checked by `SettingsBoxArtSourceFitTest` (832x468).
- **Slab reach and bar of the left columns**: the systems and Settings sections slab starts 16 dp from the screen edge (`systemPillStart`, 8 into the 24 margin). Systems rows keep their label at 32 (margin + 8) and the accent bar stands in the overhang, 3 dp in from the slab's edge (`systemBarInset`; 19 to 23, about 9 before the text; never touching the rounded ends). Settings sections take the game row's recipe: bar 8.67 in, label 39.33 (`sectionLabelStart`), 10.67 between; the open but unfocused section's dot is centred on that bar's x (`sectionBarCentre`) and every label starts alike. Any bar is at least 8 dp before its text (`SlabStripeGapTest`).
- Sections column: the same alignment as the systems column. It starts at the screen
  edge and is margin + 200 wide (224, not scaled by text size; a label too long for it at the large text sizes takes two lines, never an ellipsis, and its row grows so the text keeps the rhythm of one line: `SectionLabelRow`); every section label starts at the page
  margin (24), the same left line as the top bar text and the first hint key cap, focused or not; the
  focused pill runs from x = 10 to x = 224 (full pill, 2 dp accent ring). The option column starts where it always did (248) and the panel has not moved. Both columns
  use the one `SystemRow` with `bleedStart`. Checked by `SettingsSectionsAlignTest` at four sizes and all
  six text sizes, plus a tap at x = 4 dp. With an option focused the sections slide fully away
  (peek 0); the top bar names the section, and a panel's name after it (in Box art & accounts the panel's name replaces the section's, which leaves no room for both); L1/R1 change section.
- **Three columns (Wide width class, where they fit)**: sections, options, right column (the board `Settings.png`, 768x432:
  `SettingsThreeDimens`, `PlumLayout.isSettingsThreeColumns`). Sections 212.5 wide (the board's 178.67 grown so the longest label of any language, English "Box art & accounts", stays whole with the label starting at 39.33 and 8 dp clear of the divider; `SettingsSectionsFitLabelsTest`), then a hairline divider
  (text colour at alpha 0.08, like Home's list divider) from the first section row to the last; options start at 222.5 and are
  `width − 212.5 − 10 − 18 − 205.33 − 24` wide (298.17 at 768, 362.17 at 832); the right column is 205.33 wide and ends at the
  right margin (24), so at 832 the sections and the right column keep their widths and the options take the rest. The sections
  never slide away: the open section is marked by an accent dot at x 24 with its label at x 36, weight 600 (the Current look)
  while an option or panel has the focus; with the sections focused the row wears the slab as before. A section row has no count
  and no end padding (labels need the room). The top bar reads "Settings" (no section). Row heights stay (40). Keys and touch as
  in two columns; a tap on a visible section opens it (a second tap enters it). The right column holds what the side panel did
  (swatches, panel rows, descriptions, Reset/Clear buttons stacked, the Font sample over two lines). **Whole-width pages**
  (own-colour "+" pages, Licence text, Folder pages, Skip a sub-folder, Remove folder, Fix name, scraper sign-in, Report a
  problem, scraper systems, confirm pages: `SettingsNavigation.DEEP_PAGES`) cover the options and right columns (555 at 768) with the
  sections still on screen; Statistics, which has no right column, does the same (its tiles in the narrow, two-to-a-row layout).
  **Fallback**: `SettingsColumns.fits(textSize, font)` (core-ui; pure) decides, and the two-column layout below is used where
  it is false. A label that would not stay whole in the 142 dp the open section's label gets (the Spanish and Vietnamese names of Home screen,
  the longest) is the first thing to fail: Jost keeps 3 columns up to Large (the same widths as before the factors were raised, one name step up: Jost kept three columns at Large by the 1.2 factor).
  `SettingsColumnsFitTest` (768 and 832, English, Vietnamese, French, Spanish, every font and size) pins the table.
- **Look preview (right column, Appearance)**: the board's card at the top of the right column, 205.33 x 114.67 (x 538.7..744, y 56..170.7 at 768), corner 12, the title 14.67 below it. Inside (dp from the card): list lines 6.67 high at x 12 (y 14.67 and 30.67, 100 and 85.33 wide; y 77.33 and 93.33, 93.33 and 73.33 wide) in the text colour at 16 %; the focused row's mini slab at x 6.67, y 46.67, 117.33 x 20, corner 6.67, drawn at exactly that box in the theme's own slab gradient, border and glow (the shared slab is never as short as 20; its drop shadow and glow are clipped out of the box first) with a text bar 72 x 5.33 at x 13.33 (no accent bar, as the board); a cover tile 61.33 x 46.67 (corner 6.67, 12 from the right, y 14.67) in the board's radial gradient (centre 28 % / 18 %, far-corner radius) from the accent darkened 10, 30 and 50 % at 0, 45 and 100 %, over its drop shadow (y 5.33, sigma 6, black 50 %) and glow (sigma 10, the accent darkened 20 % at 30 %), and a ball 30.67 across (4 from the right, y 9.33) in the radial gradient (centre 34 % / 30 %) accent lightened 45 %, accent, accent darkened 38 % at 0, 42 and 100 %. The card's well is the background pulled 13 % toward black (toward the text on a light theme) with the inset shadow `inset 0 1.33 sigma 2 black 70 %` inside its shape and the board's 0.67 white 8 % line under it, outside the shape (drawn before the clip). Colours are read while drawing and follow the theme, a slider drag included. Options: Colors, Background, Text color, Accent color (the look), Font, Text size, Text weight (the focused row holds a sample line in that type, and grows to hold it); none for any other option, Motion included. **It gives way**: shown only when the panel's own contents and the card fit the column together (on the colour presets pages the swatches, the rest and the title must stay whole beside it; the description under the title leaves before it does, in whole lines, and Font, Text size, Text weight and Fade list edges show the card whenever the rest fits; Font lists four sample rows, so at 768 x 432 they win and the card shows from 832 x 468 at Small text). In three columns the right column's title and description start on the card's left edge (not on the row-text line), title 14.67, description 10.67 on a 16 line, 5.33 apart, measured whatever the layout, never by device; a page that covers the column has none. Checked by `SettingsLookPreviewTest`.
- **Two columns (Wide, where three do not fit)**: options `width − 320` (448 on the RP5), side panel 248 beside them; the
  sections slide away once an option has the focus and the top bar names the section.
- **Narrow**: options `width − 48` (528 on the Nova); a row with a panel opens it as a further
  stop that takes the same width.
- Values (Settings values): a value is drawn in the board's body ink (`body`: Graphite #C6C2B9, Paper #4C4942) on a row at rest and in `text` on the focused row; only the dimmed preview beside a focused section column mutes it (`muted`), never otherwise dimmed. Three kinds, told apart by their chevrons, which are drawn marks (`ValueChevron`, `ValueCheck` in `SettingsValueIcons.kt`: a stroke with round caps, sized from the value text's font size so they keep their look at every text size), dimmed (`muted`), never text glyphs. A value that **cycles** with Left / Right has a left and a right chevron at its sides on the **focused row only**; both keep their room when hidden, so the value does not move when the cursor arrives, and each is `settingsValueGap` (8) from the text, so the two gaps are equal. A value that **opens a panel or page** (every Opens row, the colour-dot rows, a system's emulator) ends in a single right chevron, always shown, the same drawing as the cycler's right one, `settingsValueGap` after the text (or alone when the row has no words). A switch shows its switch. Every chevron is vertically centred on the value's text line and every value ends on the same right line (the chevron's, or the switch's). A value takes at most half the options column and may use two lines; the label keeps the rest. **Status says what it means**: a signed-in account row reads "Signed in as <name>" with an accent check before it ("Signed in" when the name is missing); signed out reads "Sign in", a refused key or password "Sign in again", both with no check. ScreenScraper keeps its short box art word ("340 of 2,054", "Paused", "Battery low", ...) while a run says something; a finished run and no run say who is signed in. Never the bare word "Done". Checked by `SettingsValuesAlign768Test` / `SettingsValuesAlign576Test` (centring ±1 px, equal gaps, one right line, no movement, a long name clear of its label), `SettingsRowWordsTest` (the words), shots `settings_values_*`. An **information-only row** (Version, Privacy, Statistics rows, "Box art: not available in this build") has no chevron, no A hint, A and a tap do nothing and make no sound; its box art status sentence (the row's label) stays in the secondary text colour (`muted`, `meta` when focused). On a narrow screen the hint bar offers "› More" (the Right key) on such a row and on a switch or action row, never an A "More" beside it.
- **Statistics page** (`SettingsStatisticsPage.kt`, sizes `StatisticsDimens`; `SettingsStatisticsLayoutTest`, `SettingsStatisticsFitTest`, shots `settings_statistics`, `_empty`, `_preview`, `_preview_empty`: nothing played, the preview shows the empty-state mark, the line and the box art tile, a grid cell wide and at the page's left, never one bare tile). No side panel: entered, the page is `width − 48` (720 at 768, 528 at 576), from margin to margin. Blocks 12 apart (`settingsPanelGap`): tiles, the lists, the footer. Where the first game cannot stand under the tiles (Huge and Largest text) the page rests on a row of tiles or the lists' top, not through a tile (`restOnBlock`); a scroll within the edge fade's 20 dp stays.
  **Tiles**: `sel2` fill, `cornerRow` (8), padding 14 on every side (the rows' text line); label `meta` (13, `meta` colour, 2 lines), figure `DetailsTitleWide` (20, 600, `text`; one ramp step down, 16, when the whole figure does not fit the tile, never an ellipsis; 2 lines); equal widths and, in a row, equal heights; 12 apart. Every tile ends in something at its foot, pushed to the bottom, so no tile has an empty half: Total play time a line "across 38 games" (`smallFact`, `meta`), Games played and With box art a 4 dp bar (`accent` share on a `bg` track). Four to a row on wide screens, two on narrow ones (a lone last tile keeps the tile width). This week's tile ends in seven bars, 28 high at most, 4 apart, bottom-aligned, each its day's share of the longest day: today `accent`, other days `muted`, a day without play a 4 dp `bg` stub on the sunken look (`drawSunken`); 4 under them each day's narrow name in the locale ("M", "T", `smallFact`, centred, `muted`, today `accent`), from the week's first day as `weekStartOf` counts it. With box art's percentage is rounded down (never 100 % with one cover missing).
  **Lists**: heading `meta` on the rows' text line; rows at least `rowHeight`, padding 23.33 across and `factsRowGap` (4) above and below, `listRowGap` (2) apart. A game row: cover slot 36 (+ 10) while box art is set up (a game without a box front wears its poster cover, landscape; the stand-in only while a picture decodes), title `settingsOption` (one line, …) with the time (`meta`) at its right, the system (`smallFact`, `muted`) under it, then a 4 dp bar 4 below: `muted` share of the first game's time on a `sel` track. The focused game is the focus pill (`strong` text, weight 500). A system row is the same box without cover: name and time, then its share of all play time ("85% of play time", `smallFact`, `muted`, rounded, never 0 % for a system played), then the bar; it never takes focus. Both rows have the same lines, so at every text size the two lists keep one row height and their bars line up across. Wide: the two lists side by side, 24 apart (`columnGap`), each half; narrow: stacked.
  **Footer** "Kept on this handheld only": `smallFact`, `muted`, on the rows' text line. **Empty**: one group centred in the page, across and down: the line (`settingsOption`, `text`, centred), the box-art tile at a tile's width, the footer, 12 apart. **Preview** (sections focused): only the tiles, two to a row, clipped by the preview column. The page scrolls as one column with `ListEdgeFade` at an edge it can scroll past.
- **Side panel content, one left edge** (the game panel's rule): rows, fields
  and buttons span the panel from its left edge; their text, and the panel's own title, body and
  status lines, start on the row-text line 23.33 in (`gameRowPadding`; Settings' panel rows use the
  same 23.33 start; panel text and option rows end `panelTextEnd` 14 in: `onPanelTextLine`). The title's top is the panel's top in every panel, the account panel's too. Buttons keep
  room for their focus ring above and below; at the sides the ring reaches past the panel's edge.
  Swatch grids of the presets pages are left-aligned from the panel's left edge like the row fills, with 3 dp above and below; each swatch is a `PlumSwatch` whose collar falls in the gap around it (`collarBleed`: the swatch keeps its 40 dp and place, so the columns stay), and the colour-set tiles of Colors do the same in a cover collar; the "+" palette grid is a `sel2` container spanning the panel (see "The "+" page's containers"); the Color code and Use rows span the panel like every other row. Checked by `SettingsPanelTextLineTest`, `SettingsAccountAlignTest`, `SettingsBoxArtAlignTest`,
  `SettingsSystemsAlignTest`, `SettingsBoundsTest`.
- **The "+" page's containers**: the swatch grid, the three slider rows and the Color code row are one kind of box: `sel2` fill, `cornerRow`, `settingsPanelRowPadding` at the sides, one `settingsPanelGap` from the next, all spanning the panel so their left and right edges are the same (grid: `settingsSliderRowPad` above and below). The grid's tiles share the width: eight to a row, square, each a `PlumSwatch` in a key collar that takes its lip and groove (7 dp) from its cell, so the cells' gap is `swatchGapWide` (`swatchGapNarrow` on Narrow) less that 7, never below 0 and the caps stand about as far apart as before. **Selected is a check mark, not a ring** (focus lights the collar): the tile whose colour equals the colour in use carries a `settingsSliderThumbRing`-wide check in the text colour that reads on it (`TextMode.bestOn`); a tile can be both focused and selected. Checked by `SettingsColorSliderAlign768Test` / `576Test` (Graphite and Paper, Small and Largest), `PaletteCurationTest`. The selected check (a tick in the readable text colour, never the ring) marks the colour in effect when the page opened and then the one chosen with A, a tap or an applied code; the ring previews the focused tile without moving the check (`SettingsColorPlusSelectionTest`). The grid the cursor walks and the grid drawn are one list, fixed when the page opened, so a store that answers late cannot make them differ.
- **Colour sliders** ("set by sliders first, the code last"): the "+" page of Background, Text color and Accent has three rows between the swatch grid and the Color code row: Hue (0..360 deg), Saturation and Lightness (0..100 %), set from the colour shown. A row is a hex-row-sized fill (`sel2` at rest, the slab when focused, `focusPill`) spanning the panel, one `settingsPanelGap` from the next; inside, on the row-text lines (14 in), the label left and the value right (`meta`), and under them the track: a bar `settingsSliderTrack` (10) high from one text line to the other that draws its own gradient, never the colour's own darkness (a dark colour made the tracks black): Hue is the full spectrum at saturation 100 % and lightness 50 %; Saturation runs from grey to the full colour of the current hue at lightness 50 %; Lightness runs black through the current hue and saturation to white. The thumb shows the current value; which stretches are dimmed or skipped is worked out on the real colour. While the cursor is on the swatch grid the rows show the HSL of the colour being previewed, and a thumb, a dot `settingsSliderThumb` (20) filled with the colour shown and ringed in `strong` (`settingsSliderThumbRing`), its edges on the text lines at both ends. The thumb is a collar knob; the row's slab marks the focused slider. Gamepad: Down from the grid's last row reaches Hue, Saturation, Lightness, then Color code (Up goes back); Left and Right step the focused slider (hue 6 deg, the others 4 %; a held press repeats at the held-direction cadence); Left at a slider's start does nothing (it leaves the page only from the grid's leftmost tile and the Color code row). Touch (the stepped drag felt stuck): a tap on the row sets the value at the finger and focuses the row; a drag moves it only after horizontal touch slop, and a vertical swipe starting on a row scrolls the page. While a finger is down the thumb is drawn under it, continuously, across the whole track (also over dimmed or skipped stretches); the previewed value is the finger's at fine resolution (hue 1 deg, the others 1 %), moved to the nearest passing value over an unreadable stretch; on release or cancel the thumb settles on the value in effect. Nothing is stored while the finger moves (only the screen's colour changes while dragging); the store is written once when it lifts. `ui_move` sounds when the value changes and at most once per D-pad step of travel; the gamepad keeps its steps. Every step previews like a swatch; reaching the Color code row from the sliders keeps the colour tuned (the row shows its code, and a typed code sets the sliders), only B puts back the colour from before the page, and A chooses the shown one and counts as the cursor having moved. **Readable only**: a thumb never stops on a colour that fails `Readability` (Text 7:1 with its derived tokens, Accent 3:1): it steps to the nearest passing value, a tap on an unreadable stretch lands on the nearest passing one, and every track draws its whole length, with the stretches a thumb cannot land on dimmed at `SETTINGS_SLIDER_DIMMED_ALPHA` (no gaps or broken bars; only the drawing changed, the landing is the same). Background has no text or accent test: the tones no text reads on (`Readability.backgroundAllowsText`) are drawn at `SETTINGS_SLIDER_DIMMED_ALPHA` and stepped over. A step sounds `ui_move`; a press at the end of a track is silent. The page scrolls so the focused row stays whole at 576 x 432 and Largest. Checked by `SettingsColorSlidersTest`, `SettingsColorSliderAlignTest`, `SettingsTextColorFit*Test`, shots `settings_color_sliders_*`.
- Switch (decision 0011; the enabled knob is lifted 1 dp so body plus solid edge is centred on the groove, a settled change from the V2 Controls boards, 2026-11-03, `SwitchSliderDimens.switchKnobLift`, `PlumSwitchKnobCentreTest`): a sunken pill track, the accent face (`accentFill`) when on and `chip` when off, with a 1 dp inset shadow on its top edge; the knob is a flat-faced circle (see Controls under Shapes and motion).

## Setup

Four steps, three dots (sign-in shares the box-art dot): folder, box art, sign in, library
ready. Title column 260 on wide screens, stacked on narrow. No storage permission: the folder
step first shows three plain lines (each after a 6 dp `muted` dot) and
"Choose folder" (opens Android's picker at ROMs), then
the chosen folder as a quiet filled card, "+ Add another folder" and Continue. The last step
shows the four biggest systems (hidden on narrow screens), emulators picked ("16 of 18
systems", the missing ones named), the usage statistics switch (on) and Start playing. Every card
there start on one line (no card has a focus dot now).
Buttons are `PlumButton`s in the control collar (the main one in the accent, the others as keys; `SetupButton`): the button's bounds include the collar (cap 38 dp inside it, 52.7 outside; the board's 57 and 79 px), so its outer edge lines up with the column start and keeps at least 10 dp from the part above (Continue and Keep A add `setupButtonSpacer` to the content gap; `SetupButtonCollarTest`). The switch is the flat `PlumSwitch` (`SetupSwitch`). A focused choice or info row is the slab (`focusPill`), never a ring; a chosen folder card and the info rows are raised cards (`raisedCard`, corner `CardDimens.corner`). "Pick a look" tiles sit in a cover collar (`CollarSize.Cover`) and focus lights its groove.

## Depth

The app is thin glass, not moulded plastic. `Depth` in core-ui is the one place to turn it, and it wins over the boards' values quoted in this file: lips, highlights, insets and wells are drawn at `RAISE` (0.6) of the board's, a cap's solid thickness, its press and a cover's hard drop at `THICKNESS` (0.25), the lip of every part at `LIP` (0.7) of its width, soft shadows at `SHADOW` (0.4) and glows at `GLOW` (0.6) of their strength, shadows at `SHADOW_REACH` (0.25) of their blur and drop, glows at `GLOW_REACH` (0.6) of their reach. A part's collar is that much narrower than the board's sizes quoted in this file; a cover keeps its lip, which is part of the grid's packing. The focused slab is one even pane (white 0.085 to 0.07 over the page on dark, white 0.34 to 0.39 of the way to the page on light, which is the old slab's mean: the first 0.5 to 0.58 left it too close to the page for `ColorSetsTest` and `RaisedCardTest`) with no inset shadow and no dark ring, and a thin rim lit at the top left and bottom right corners and nearly gone between them (`drawSlabFace`). A cap's face is nearly flat. The collar lip's bottom band keeps the board's strength (0.03 dark, 0.04 light): a black page has no room under it and a white one none above (`CollarLookTest`). The background is not blurred or bent: Android 6 has no backdrop blur and a real one costs frames on the weak handhelds.

## Empty state (home)

With no system at all the top bar names the app, with no count ("0 systems, 0 games" read as a result, not an invitation). A library with one way forward (Add folder) offers it on the button only; the hint bar names A where there are two buttons, to say which one it presses (`HomeEmptyStateTest`).

Centred in the games area (from the list's start to the right margin, 32 dp kept clear on each side), one under the other, 11 dp apart (`EmptyStateDimens.gap`): the title (`messageTitle`, `text`), the body (`meta`, `text2`, two lines, at most 280 wide) and the buttons (`PlumButton`: primary in the accent, secondary beside it; one under the other when they do not fit; a label is one line when it fits, else it wraps to more lines, never an ellipsis: the buttons may be `EmptyStateDimens.buttonBleed` (12) wider than the text room on each side, so a button ends 20 dp inside the games area, clear of the right margin and the edge fade; at 576 x 432 and Largest English "Files not added" (about 223 dp wide with its cap) does not fit the 164 dp left, so it wraps to two lines; `HomeEmptyButtonsOneLineTest`: no ellipsis, bounds inside the margin, English, French, Vietnamese and Japanese). No mark or picture: the board's disc and square read like a missing image on a device. The first button has the focus at rest; Left and Right move it. When the height is short the body goes; the title and buttons always stay. The Settings Statistics invite is the same title and body. Checked by `HomeEmptyNoMarkTest` (nothing above the title).

## Grid

- Grid area = `width − 96` when games are focused.
- Each system has a cover shape, its box fronts' width over height, and a class from it (the shape comes from data, not guesses):
  - **Landscape**, 1.2 and up (SNES 1.37, N64 1.44).
  - **Square**, 0.85 up to 1.2 (DS 1.12, PlayStation 1.17, Game Boy / Color / Advance and Dreamcast 1.0, PC Engine 0.85).
  - **Tall**, below 0.85 (NES 0.73, Mega Drive and Master System 0.71, PS2 and Wii 0.715, GameCube 0.72, Saturn 0.65, PSP 0.575).
  - **Gaps (every class, nothing grows on focus any more):** between neighbouring collars 18 dp across and down (`PlumDimens.gridGapAcross` / `gridGapDown`; `PlumLayout.gridGap` / `gridRowGap`), the room of the focused cover's growth and ring. The focus halo (10 dp) may overlap a neighbour's gap; the first row's halo stays inside the 22 dp top gap and the last row's inside the 10 dp bar gap. Apps' grid keeps its wider gaps (22 / 20) because its focused tile still grows (`gridGap(CoverClass)`).
- **Grid size** (L1 / R1 and the Size hint, or Settings › Home screen while hints are off; one for every system):
  Small, **Medium (the default**) and Large. Columns = `floor((area + gap) / (cell + gap))` with
  the cell of the class and size below and the **14 (landscape) / 12 (square, tall) gaps** (`gridColumnsGap*`: the counts the table was cut with, so no grid lost a column); the drawn gap is `gridGapAcross`, so a cover is the covers' span
  shared by the columns after their gaps and is wider than its cell (Tall Small on the Thor: cell 79, cover 85.8;
  tile titles still read at the 70 dp minimum). The Thor's columns are (landscape 5 / 4 / 3, square 6 / 5 / 4,
  tall 8 / 6 / 5); the RP5 loses a column at each larger size; every reference width stays at
  least 5 dp of area from a change of count (`PlumLayoutTest`), and the Thor keeps its columns at
  its default Large text (peek 52.8, area 736). The board's landscape grid (RP5-3, Nova-4) is
  Large, its portrait grid (RP5-4, Nova-5) Medium; square and tall Medium and landscape Large are
  the grids from before the sizes. Square Large is 172, not 168, so the Nova at 640 x 480 gets 2 square columns at
  Large against 3 at Medium from Medium text up (168 drew 3 and 3); at Small text there (area 544) both stay 3, since
  more than 173.3 would cost the Thor its 4th square Large column at its default Large text (area 731.4): `GridSizeStepsTest`.

  | Class, size | Cell | RP5 768 | Thor 837 | RP4 Pro 667 | Nova 576 | Cover width (RP5 / Thor / RP4 Pro / Nova) |
  |---|---|---|---|---|---|---|
  | Landscape Small | 118 | 5 | 5 | 4 | 3 | 115.2 / 129.0 / 124.2 / 142.7 |
  | Landscape Medium | 142 | 4 | 4 | 3 | 3 | 149.5 / 166.8 / 173.0 / 142.7 |
  | Landscape Large | 200 | 3 | 3 | 2 | 2 | 206.7 / 229.7 / 270.5 / 225.0 |
  | Square Small | 99 | 6 | 6 | 5 | 4 | 94.0 / 105.5 / 96.6 / 103.0 |
  | Square Medium | 130 | 4 | 5 | 4 | 3 | 151.0 / 130.6 / 125.8 / 144.0 |
  | Square Large | 172 | 3 | 4 | 3 | 2 | 208.0 / 168.2 / 174.3 / 226.0 |
  | Tall Small | 79 | 7 | 8 | 6 | 5 | 77.7 / 74.1 / 77.2 / 78.4 |
  | Tall Medium | 106 | 5 | 6 | 4 | 4 | 116.8 / 105.5 / 125.8 / 103.0 |
  | Tall Large | 128 | 4 | 5 | 4 | 3 | 151.0 / 130.6 / 125.8 / 144.0 |

  The narrowest tile is Small tall on the Thor: 86 dp at Small text (74 with the 22 / 20 gaps; the column counts did not change: `PlumLayout.gridColumns` counts with the old 14 / 12 gaps, `PlumDimens.gridColumnsGap*`). It keeps
  about 50 dp for its title between the 10 dp paddings, so Small tiles set the title in 11 / 600 and the year in
  11 (`smallTileTitle`, `smallTileYear`, their own roles, the ramp's step under 13; Medium and Large keep 13 / 600
  and the year in `smallFact` 13); the ring's
  5.5 dp sits inside every gap. Gaps, corners and paddings go by class, the same at every size.
- **Which saved picture a cell draws**: the grid file (560x400 at most) while its height covers
  the picture's drawn height in pixels, else the large one (720 tall at most for a box front);
  either stands in for the other when only one is saved. PSP at Large on the Thor (238 dp, 546
  px) draws the large file; NES at Small (154 dp, 352 px) the grid one. Decoding stays at the
  drawn size.
- Where a system's shape comes from, first that applies: the median width over height of the
  person's own saved large box fronts once the system has 5 (so a Japanese Super Famicom library
  gets tall covers, a US one wide); else the platform data's `coverRatio`, measured
  from one US box front per system (ScreenScraper box-2D; `tools/platforms/tables.py`
  `COVER_RATIOS`); else the class's default from the data (landscape 1.4, portrait 0.71). The board's
  own two shapes (landscape 1.4, portrait 0.78) stay in the fakes behind the board's shots.
- **Row height of Medium (the default) and Small** comes from the grid's height, not from a shape (`PlumLayout.gridRowHeight`,
  `PlumDimens.gridMediumRows` = 3, `gridSmallRows` = 4, `gridRowsRoom` = 1 dp): `floor((gridStripHeight − (rows − 1) × row gap − 1 dp) / rows)` dp.
  Medium shows exactly three whole rows and Small four, none cut, for landscape, square and tall boxes alike, at every screen and text
  size (Medium at Small text: 103 dp at 768x432 and 576x432, 116 at 832x468; the 9.33 row gap is `gridRowGap`; the halo and groove are drawn
  over the grid into the bar gaps and take no room of their own; the 1 dp stops pixel rounding from pushing the last row past the edge). Boxes
  keep their shapes, so the count across follows (Medium at 768: about 4 landscape boxes to a row; Small about 6). **Large** keeps the
  cell-based height above (the cover width over the shape, but never so tall that a tall box passes its column) and a row may be cut there (`PlumLayoutTest`, `HomeDefaultGridRowsTest`). Boxes are never stretched to fill a row's
  right end: a row that cannot take the next whole box leaves the space empty (the worst measured in the packed shots is 0.84 of an
  average box width, 100 dp at 576).
- Hint bar gap: the board's 18.67 dp (28 px at 1.5 px per dp) between hints where the bar fits, shrinking toward 9 only when it does not (576); pinned by `HintGapTest`. A game row ends 14 dp from its edge, 7 at the Details stop. **Details block (wide details) when it does not fit the column** (`FactsPolicy` in `HomeDetailsBlock.kt`): the genre keeps one line (the whole list when it fits its third, else its first genre, only then it wraps; pinned by `HomeDetailsAllCellsTest.genreIsOneLineAtLargeInJost`); the subtitle reads "Nintendo, 1994" (publisher, comma, year). Cells leave whole, in this order: GENRE, PLAYED (the played count), RATING, EMULATOR; LAST PLAYED and BATTERY go last (never before those four), and the battery only when nothing else is left to drop. A cell is never cut and never shows a dash or an ellipsis; the cells left repack in reading order into rows like the first (a cell that does not fit what is left of a row starts the next, no hole before a cell), and the gaps shrink toward `detailsDividerGapTight` before any cell leaves. Cells are measured with the column's real height, not an unbounded one. Under a title of two lines the cover may go to 0.77 of its 140 dp floor (`SQUEEZED_COVER`, the largest share that keeps the battery cell at 768x432 Large in every font) so the three rows of facts stay. Checked by `HomeDetailsAllCellsTest` (768x432, Small, Large, Largest, every font: the battery value is on screen, the missing cells are a prefix of the order).
- **Packed rows** (every cover grid: one system, Recent, Favorites; not Apps). **The grid fills the content width at every Size step**: the covers span from where the list starts to the right end of the clock and the hint bar (`PlumLayout.gridCoversWidth`), with no leftover gap; the gaps stay 18 dp both ways (`gridGap`, `gridRowGap`). A Size step picks a column count (`PlumLayout.gridColumns`) or, for mixed art, a target row height (`gridRowHeight`: the height of a box of the system's shape one column wide), never a fixed tile width. A box is a collar round a picture of its own shape (a saved box front's, else a poster, an app card or a front still being read the system's); the collar stays the board's thin one and the picture stays whole, only its size changes.
  - **One shape in the system (most)**: `columns` boxes to a row, ONE box width `(W - (columns - 1) * gap) / columns` exactly, one height from the shape, so columns line up on every row, the last too, which may end short (same columns).
  - **Mixed shapes**: justified rows. Every full row is scaled by ONE factor on its pictures so it spans W exactly with board gaps (its height follows: `chromeY + (W - (n - 1) * gap - n * chromeX) / sum of aspects`); each row has its own height. The row breaks are the cheapest by the sum over rows of (ln(height / target))^2 (a dynamic program, `justifiedRows` in `GridPacking.kt`), so no row strays far from the target. There is no cap on a row's scale; for a realistic mix (DS, GBA landscape, PS1 square, poster) every full row stays within 0.8 to 1.25 of the target (`GridPackingTest`). Only the very last row of the list, when its boxes fit across at the target height, stays at the target, left aligned and unscaled, and may end short. A box wider than the whole grid has a row of its own at the grid's width.
  - Each game packs at its stored shape (`game.art_aspect`), so a late result of that shape moves nothing (`GridStoredAspectTest`); a game with none yet packs at its picture's, else its system's. A picture in hand beats a stored value more than 2% away from it (replaced art the pass has not reached). A stored shape written while the grid is on screen waits for the next visit of the system (`GridHeldAspectsTest`): rows on screen do not move for a background pass.
  - A result landing, a text or Size change or a resize packs again (once per width, Size step, system and art list, never per frame); the focused game stays (`GridPacking.kt` packs; `GridRowScroll.kt` keeps `RowSpans`, a px prefix of every row's top and height, which the scroll and the focus-keep rules read, so rows of different heights scroll like equal ones: the focused row is always whole in the window). Left and Right go to the previous or next game (Left at a row's start leaves to the systems, Right at a row's end stays, as in any grid); Up and Down go to the box of the next row whose x centre is nearest the focused box's (a tie: the left one), whatever the rows' heights; the first row's Up and the last row's Down stay. Checked by `GridPackingTest`, `GridEdgesTest`, `GridRightEdgeTest` (every Size step, both themes, 837x471, 853x480, 832x468, 768x432, 640x480, 576x432), `GridMixedRowsTest`, `GridMixedScrollTest`, `HomePackedGridBoundsTest`, `HomeMixedGridBoundsTest`; shots `grid_fill_*` (`GridFillScreenshotTest`). The collar's room for the focus lift is the grid's glide room (`ROW_SCALE_ROOM`), not a clip.
- **Recent and Favorites grids** mix systems: each game's box has its own system's shape (a picture its own), in the
  square class's row height, gap and corners (Small on the RP5: row 101 dp). A game without a picture wears its poster
  cover in its system's shape (Poster cover, below); its collar follows that shape, and its corner is the box's.
- **Apps grid** (its size can't be changed): one fixed size, never the
  Grid size. Icon **72** (`AppsGridDimens.appsGridTileIcon`), centred at the tile's top under 6 of padding, 8 to the
  name, so the focus (the icon's 1.26 dp growth, the 2 dp rim, the glow cut at the name's top) clears it; the name in `meta` (13 at Small, following text size and font), centred, **one line**
  and "…", with hints on or off; 6 under it. Row = 6 + 72 + 8 + one drawn line + 6, rounded up to a whole dp (108 at Small Sans,
  115 at Largest Sans, 121 at Largest Serif), and **2** between rows (`appsGridRowGap`). Columns = `floor((span + 12) / (80 × text scale + 12))` over
  the covers' span (`width − 104`), counted with 12 like the cover grid's column table, but **drawn with the
  cover grid's gap** (20, square covers), so a tile is a little narrower than 80 (77.7 at 768) but never narrower than the icon:
  7 / 5 / 8 at Small on 768 / 576 / 837, 7 on the Thor at its
  default Large, 5 / 3 / 5 at Largest (fewer, wider tiles so a name keeps its words). The grid sits
  `appsGridTopGap` (22) under the top bar (the cover grid keeps `gridTopGap`, 14). **No card** ("don't put apps inside a frame, it's ugly"): an
  icon (its own shape) and its name stand straight on the screen background, with no plate, no rim and no shadow, at rest
  or focused. **Focus as a cover's, lighter**: the cover's thin ring (`drawCoverFocusMark`: 3 gap, 2.5 accent, corner 23 % of the icon's side) on the icon alone, one light sweep on the icon after `FOCUS_SWEEP_REST_MS` of rest. **Nothing grows, lifts or glows**, so every name stays on its row's line at every frame; a press dips the icon and its ring, not the name; the ring travels from the tile left (see Focus and current);
  the focused app's name turns `strong` and one step heavier (400 to 500), the other names stay as they are
  (both names are composed, the one that draws is read while drawing: no recomposition on a move); no other tile is dimmed or
  changed; the tile focused as the grid opens, with animations off or while idle: no sweep, nothing running (`AppsGrid.kt` shares `FocusOutline` with the
  cover grid). An app without an icon: a `sel` round tile with its first letter (`messageTitle`, `text2`).
- **Cover frame**: the collar's lip is glass, the same recipe as the slab: lightness steps above the page, L* +16, +11 and +8 top to bottom (`Depth.COVER_FRAME_TOP_L` to `BOTTOM_L`), a lit crest along its top (white 0.45 at `Depth.RAISE`), a fine edge that parts it from the page (white on a dark or mid-tone page, the theme's brown on a light one), the lip's outer drops as before. Its thickness is the collar's. It reads on Graphite, Paper and a mid-tone custom colour, where the old lip melted into the page; a near-white page leaves little room, so there the edge carries it. `SeatLook.coverGlass`, inside the collar's cached image: no extra layer per cover. `CoverGlassFrameTest`.
- **Cover collar** (the Covers board, drawn at 1.5 px per dp): every grid cover, picture, poster or titled tile, sits in a collar like a button's (`CollarSize.Cover`, `drawCollar`): a lip of 2.67 dp (3.33 at the bottom), a groove of 1.33 dp; corners concentric, a step rounder than the board: picture 6 (`CollarDimens.coverBoxCorner`, the one token for every cover picture), frame 10 = 6 plus the 4 dp the groove and lip add (`CollarDimens.coverFrameCorner`, `CollarSize.outerCorner`; `CoverCollarSpecTest`). The collar hugs the box's own fitted rectangle, not the cell: the box is fitted (never cropped, never stretched) in the cell less the collar's padding (4 dp each side and above, 4.67 below) and stands bottom-centred, so the collar stands on the cell's bottom and a row reads as a shelf. The box has its own small drop shadow (the board's `0 2px 0` at 55 %, a hard copy 1.33 dp down, and `0 3px 4px` at 40 %, 2 dp down with sigma 1.33 as one cached Gaussian image; `coverBoxShadow`), and the collar is the button's own: the kit LIP (radial gradient and six layers) and WELL, lit in the accent when focused (`drawSeatCollar`; the box does not grow or lift). Nothing is drawn inside the picture's edge (no rim, no hairline). Where a box front is transparent the groove shows through.
- **Cover focus**: see Focus F3 in the focus table: the frame grows 3.5 % about its pivot, the rim sits in the 18 dp gaps (`gridTopGap` under the top bar and `gridBottomRoom` over the `barGap` leave it its room at the first and last row).
  The titled tile (a picture or result still on its way, or neither picture nor poster): the drawn box front's field and grain (`BoxFrontLayout.fieldOnly`, the game's system colour with the title's hue turn, light to dark, no letter or code; the neutral grey of the same recipe where no system is known), drawn once per size, the title in white and the year in white at 80 % (4.5:1 and 3:1 on the field's lightest point), corners 6 dp (the box's), the title in `gridFocusTitle` (13 / 600;
  `smallTileTitle` 11 / 600 at the Small grid size) and the year under it in `smallFact` (13;
  `smallTileYear` 11 at Small), 4 apart, at
  the bottom left inside the cover padding (landscape 12, portrait 10), like a box's label; the
  title wraps over the lines the tile holds, and the year gives way when the title needs its line.
- **Apps tiles** have the same focus round the icon (grown about its centre, rim, glow) and keep the cover shadow code (`CoverShadow.kt`).
- **Drawn box front** (a game without a picture: signed out of ScreenScraper, no picture found, or a
  file that cannot be read; replaces the earlier window strip and palette poster). Drawn by Plumemu, never saved and never a
  picture from outside, at the system's own shape and in the same collar and corners as a real cover (`BoxFrontLayout`, app
  `home/`; SPEC section 21). It stands on the shelf like a picture, on every colour set the same: the field is the
  system's own colour (the By-system accent colours, found by the system's short code; the theme accent where it has none),
  its hue turned up to 25 degrees either way by a hash of the title, saturated and mid-dark, lighter at the top left and
  darker at the bottom right (`boxFieldFor`, `BoxFieldLook` in core-ui `theme/PosterPalette.kt`), with a fine monochrome
  grain like a printed box. No band, no system name strip, no dots, no shapes, no small line. A large letter, the title's
  first letter or digit (after a leading "The"), Jost 800 about 0.9 of the shorter side, bleeds off the bottom right in a
  lighter tone of the field at 12 %. Top left: the system's short code ("SNES"; the full name when there is none) in Plum
  Mono 500, white at 80 %, 0.62 of the title's size, left out under 4 sp. Bottom left, 10 px up: the title in Jost 700,
  white, line height 1.05, at most three balanced lines (a last line is never two characters or fewer) and then "..."
  (never a letter cut, never clipped: it is cut by hand), smaller only while a word is wider than the line; 12 px at
  112 x 156 to 19 at 214 x 156, left out under 6 sp. Text insets and sizes are the board's px over 1.5 scaled by the cover's
  height over 156. The card is drawn once into an image per (title, system, size, colours) inside `drawWithCache`; a frame
  only draws that image (`BoxFrontLayoutTest`). The field is chosen so the white title reaches 4.5:1 and the code 3:1 at
  the field's lightest point under the letter and the grain, and the card stands off the page (at least 2.5:1 on a light
  page, 1.5:1 on a dark one): `BoxFrontContrastTest`. The titled tile shown while a picture loads (or with neither picture nor poster) stands on the same field and grain with no letter or code, white title and year, drawn once per size (`TitledTileFieldTest`), so nothing changes colour when the picture fades in. The same
  cover in the grid, the list's cover slot, Recent, Favorites, the statistics rows, the details column and the second
  screen's cards. App games keep their icon cards (a gradient of the icon's colour, a frame and a band: `AppCardLayout`).
- **Art that is not its slot's shape** (portrait front in a wide slot and the like; `HomeCoverRatioTest`, shots `home_art_ratio_*`): in a grid cell it stands centred on the cell's bottom (in its collar), the collar and corners on the picture. In the wide details column it stands on the column's left edge, the one the title and facts use, on the slot's bottom (the title never moves); its corner is the cover corner, 6 whatever the ratio (`pictureCorner`).
- **The details cover keeps its box while its result is read** (`DetailsCoverShapeTest`): the box takes the picture's shape once it is read (a stored value within 2% of it stands, so a large file does not nudge the grid file's shape), the stored shape before that, else the system's, the grid's order, never the whole slot, so it does not shrink when the art lands. A game known to have no picture keeps today's box (poster at the system's shape, or the slot).
- In the Recent and Favorites grids the bar's meta starts with the game's system ("SNES · 1993 · Capcom"); a Favorites cover whose game was taken out with Y and is still shown is dimmed (`UNFAVORITED_COVER_ALPHA`).
- The grid's bar: when the title, its meta and the hints do not all fit, the meta goes first, then
  the hints in the order of **Hint drop order** (the year and publisher are kept over the Switch hint); A stays.

## Type (Jost; Plum Mono for figures and codes)

**One ramp**: the top screen uses **11 · 13 · 16 · 20 · 26** and nothing else; the
second screen **16 · 20 · 26 · 36** (its Compact top line is the top screen's 13). Sizes one step apart
(12/13, 15/16, 16/17) read as inconsistency, not hierarchy. Off the ramp: glyphs drawn as icons inside a
shape (the key cap's letter, 10; the "Aa" text colour sample, 28), the second screen's details-card
facts (15, below), and the second screen's in-game clock, a display face rather than text: its time is 84
on Roomy and 60 on Compact (`InGameDimens` in core-ui's `theme/SecondScreenInGame.kt`, sized in dp so it
does not follow the text size).
**Nothing goes under 11.** The games list's right-hand text was the one exception (the tag at 8 and the year at 10, the board's sizes); it was raised on 2026-11-03 after the Thor build 1299, and 11.33 left the ramp on 2026-11-08: the tag is Mono 11 and the year Mono 13, and both grow with the text size.
`TypeScaleTest.everySizeIsOnTheRamp` fails on any other size; a new style picks a step.

**Mono is for numbers and codes only, never words**. A `TypeRole` with `mono = true` is drawn in Plum Mono at its ramp size times
0.96 (`MONO_FACE_FACTOR`: Mono's digits are 0.75 em high, Jost's 0.72, so they stand as tall as Jost's; a face tuning, not a ramp step), on
the Jost line of the role's size (a row's baselines do not move), no letter spacing, weight 400 or 500 where the role's is 500 or more
(Mono has no 600 face), **never above 500**: a focused or current mono text keeps the 500 face (a 600 would draw the bold 700 face). The top bar's Wi-Fi and charging glyphs follow Mono's digit metrics beside it (`PlumTypography.fontOf`).
`TypeScaleTest.onlyTheNumberAndCodeRolesResolveToMono` pins the set: `TopBar` (clock, battery percentage), `RowYear`,
`RowYearCurrent`, `Tag` (region and system code), `SystemRowCount` (the systems column's counts and codes), `RatingNumber` (the rating's figure alone) (not `KeyCap`: in Mono at 10 or 11 sp, SELECT's cap measures 44.6 or 49.6 against the board's 45.33 and the hint drop table moves, `HomeBoundsTest`, `HintDropOrder*Test`). Everything else, labels included, is Jost. **A phrase that mixes figures and words** ("248 games", "4 h 12 min", "2 days ago"; roles `PlaceCount`, `FactNumber`, `StatisticPhrase`; every Statistics tile figure, "73%" too, since a row of tiles is one group) is wholly Jost with tabular figures (`fontFeatureSettings = "tnum"`, every Jost face has it), never part Mono: one line keeps one face. Statistics, Settings values and the
second screen's figures join later.

| Use | Size | Weight |
|---|---|---|
| Game title in list | 16 | 400, focused 500, current 600 (its menu has the focus) |
| Centred message title (empty list, no results) | 20 (17 until the ramp) | 500 |
| Font sample line (the Font panel is gone; the role stays for the token sheet) | 16 (17) | 400 |
| System name; side panel title (Settings, the game menu; `panelTitle`) | 16 (15) | 400, current 600; the panel title 600, at most 2 lines in Settings |
| Settings option | 16 | 400, focused 500 |
| Details title | 20 (wide was 19; narrow details stop 20) | 600 |
| Top bar place name | 20, stepped to 16 when its line does not fit | 600 |
| Top bar count (`PlaceCount`) | Jost 11, tabular figures (the Covers bar's 11.33), `muted` | 400 |
| Top bar clock, battery percentage (`TopBar`), Wi-Fi glyph beside them | Mono 13 | 400 |
| The hint bar subtitle (`InfoLineMeta`: the Covers board's 17 px, one ramp step) | 11 (11.33 before the mono step) | 400 |
| Details facts: labels (Genre, Rating, Played, Last played, Emulator, Battery; `detailsLabel`) in `muted`, sentence case, no letter spacing | 13 (were tracked capitals, 11 / 600) | 400 |
| Details facts: play time, last played, rating figure, battery value (`FactNumber`); the systems column's counts and codes (`SystemRowCount`) | Mono 13 | 400 |
| The name while typing; meta lines | 13 | 400 |
| Years, counts, small facts; hint label; grid bar's focused title; setup's emulator and statistics rows | 13 (were 12, 12, 14, 14) | 400; grid title 600; setup rows focused 500 |
| Key cap (the hint's letter in its 20-high pill) | 10 in Jost (icon, off the ramp: at 11 "A" made the round cap 21 wide; not Mono, whose word caps such as L1 R1 grow) | 600 |
| Small grid: a titled tile's title and year | 11 (12) | 600 / 400 |
| Setup title | 26 (wide), 20 (narrow; was 22) | 600 |
| Setup's plain lines and buttons | 16 (15) | 400; filled buttons 600 |
| Settings: "Aa" text colour sample | 28 (icon, off the ramp) | 600 |
| Settings: "+" of the colour presets | 20 | 400 |
| Settings › About: app name | 20 (22) | 800 on the board; 600 until plum-fonts ships a heavier face |
| Settings panels: sample years | 11 | 400 |
| Settings panels: folder and emulator rows | 13 | 400, focused 500; "+ Add folder" 600 in the accent |
| Side panel body (Settings' explanations, notices, update notes, errors; `panelBody`) | 13 on a 19.5 line (1.5) | 400, `text2`: the details description's style (was 12 on 1.45 in `muted`) |
| Badge ("Current", "Default") | 11 | 700 |
| System tag on its raised chip (`systemTag`) | 13 | 700 |
| Games list's right-hand text: the tag (region, system code, `tag`, no letter spacing) and the year (`rowYear`) | Mono 11 and 13 (11.33 and 13 before the mono step; 8 and 10 on the board) | 500 (never above) / 400, current year 500 |

Fonts: **Jost is the only text face** (drawn unadjusted: its x-height is 0.46); the Font setting was removed on 2026-11-08
(a settled decision) and a stored value is dropped on the next save. The Plum Sans, Serif and Mono families stay in `PlumFonts`: Sans
draws the check mark and Wi-Fi glyph, Mono draws numbers and codes (above); Serif and Mono were scaled with `font-size-adjust 0.53`.
(Plum Pixel was dropped.) Jost's static 400-800 faces are built from the OFL variable font
(`tools/fonts/build_jost.py`), which adds the Vietnamese horn, hook above and dot below, the arrows, the check mark and the
dong sign in Jost's own strokes.

Weights are real: Jost, Plum Sans and Plum Serif ship 400, 500,
600, 700 and 800, Plum Mono 400, 500, 700 and 800, so a focused row (500) is visibly lighter than the
current item (600). The only weight still borrowed (core-ui `PlumFonts`, pinned by `PlumFontsTest`) is 600
in Mono, drawn with the 700 face. The 500 faces draw Inter 520, Literata 500 and JetBrains Mono 655.

**One weight scale** (the Text weight setting is gone), the same on every screen: the weights in the
table below are the ones drawn. Every role is one face step heavier than the table the app had before the setting existed
(`TypeRole.weight`):

| Role | Weight |
|---|---|
| Rows, body, secondary text (`muted` / `text2` by colour, not by weight) | 500 |
| Focused row | 600 |
| Current item, titles | 700 |
| Badges | 800 |

Mono is never above 500 (600 uses the 700 bold face): its roles draw 500. The weights quoted in the Type table rows above and
in role comments are the board's drawn values, one step lighter than the scale. The second screen's in-game battery part keeps one
step under the scale (burn-in budget). Chosen by shots at 576x432 and 768x432.

**Text size** (Settings › Appearance): six steps that scale every size in the table above.

| Name | Factor |
|---|---|
| Small | 1.0 (the table) |
| Medium | 1.1 |
| Large | 1.2 |
| Larger | 1.3 |
| Huge | 1.4 |
| Largest | 1.5 |

Boxes around text grow by the same factor (never under 1), so Small draws the boxes the table gives. A number in this file
written "at Medium" before the factors were raised is Small's now (factor 1.0); "at Large" numbers are Medium's unless re-measured.

Until the person picks a size the app uses **Large on every screen** (small handhelds such as the
Retroid Pocket Nova and the AYN Thor read better at 1.2; it was Medium below 800 dp). Once picked, the pick stays. Layout holds at every size on every reference size: a test renders the screens at all six sizes (and Serif
and Mono at Large and Largest) and fails on ellipsized, clipped or overlapping text and on a focused item that
is not fully on screen; a list that scrolls may cut a row at its edge. Screenshots at Largest on 837x471 and
576x432.

## Second screen

The picking cards on the second screen's own density. Size class by the shorter side: Roomy from 400 dp,
Compact below. Colours are the theme's three inputs; font and Text size apply. Code:
`core-ui/.../theme/SecondScreenDimens.kt`.

| | Roomy | Compact |
|---|---|---|
| Margins, all round | 40 | 28 |
| Top line (`meta`), after a 6 dp accent dot and 10 | 16 (15 until the ramp) | 13 |
| Title (`text`, 600), 12 under the top line, at most | 36, 3 lines | 26 (27), 2 lines |
| Favourite star (`meta`), 10 after the title, never cut | 20 (24) | 20 |
| Facts line (`text2`, separators `muted`), 16 under the title | 16 (17) | 16 (15) |
| System card's badge (`SystemBadge`), 18 above the top line, at least | 96 x 64 | 72 x 48 |
| System card's facts line ("Nintendo · Home console · 1990–2003", `meta` at the facts size), 16 under the title, the last-played line 4 under it | 16 (17) | 16 (15) |

A system's card has the badge above its top line and the facts line between the title and "Last played …" (the kind goes first when the line does not fit, then the maker; never wrapped or cut); with a badge the title takes one line fewer (2 Roomy, 1 Compact) so the block keeps inside its margins at the largest Text size. Recent, Favorites and Apps have neither; a system with no facts has the badge alone. The system's about text (`meta` at the facts size) sits under the facts line, 4 below it (`systemFactsToAboutGap`), 12 above the last-played line (`systemAboutToLastGap`), at most 3 lines at both sizes (`aboutMaxLines`), whole or not shown, never an ellipsis; when it needs more lines or the block would not fit inside its margins the about text is dropped whole, never a title line. Settings › System info does not touch this card.

One block, left-aligned, resting on the bottom margin; the space above stays empty; a long title
grows upwards. A game has its own card (see the note below). The setup question is a `text2` line at the facts size in the same place (it may wrap to two lines at the largest Text sizes). The
calm screen is only the Plumemu mark (the launcher icon's monochrome shapes cropped to the mark),
52 high, `chip`, centred. **Motion**: it moves like the top screen, with the same tokens and curve as the top
screen, nothing of its own. A new card follows the focus as the top screen's system switch does (it slides
`SWAP_SHIFT_DP` from the side the focus went and fades in, `GLIDE_MS` on `Decelerate`); a change of what the
window is for (Statistics while Settings is open, the in-game card) settles like a screen
(`SCREEN_DEPTH`, `SCREEN_MS`): going in (Settings, a game) from smaller, coming back from larger. The window's
first content, and the first card after the calm mark, show at once: motion is for steps the person took, so a
wake or a card that arrives on its own does not move (Statistics still settles in: opening Settings is a step). Within
one list or grid a card comes from where the focused item went on the top screen (a step right in a grid, from the
right). A card swap runs after the cursor rests, when the top screen has
stopped moving, so the two screens never share a frame of motion. The in-game clock's burn-in shift stays a
jump (it must not move at rest). With motion off everything changes at once.

**Top screen while the window is up** (layout C; retired: the top screen keeps its details column and these numbers no longer apply): the games list alone, `width − 96` (741 on
the Thor at peek 48, 736 at Large text's wider peek), no details column, no narrow details stop
(Right from the list does nothing); the grid unchanged. When the window goes (switch off, display
gone, window refused or dismissed, Plumemu left) the column is back in the next frame, the list
not moved and the cursor on the same game; from the narrow details stop the cursor goes to the
list. Shots `layout_c_thor_top_*` at 1919x1080 (837x471 dp at 367 dpi).

**Replaced:** the game, system, Recent and Favorites cards follow the second-screen design boards (numbers in
`core-ui/.../theme/SecondScreenGameCardDimens` and the entry cards' dimens). The table below is the retired
details card, kept only as history; nothing draws it now.

**Details card** (layout C, retired). Only what the top
screen's details column shows, in its words:

| | Roomy | Compact |
|---|---|---|
| Cover, top-left on the margins: the large box front at its own shape fitted in a square (a portrait box takes its height, a landscape one its width), corner 6, not dimmed | 240 square | 168 square |
| Beside it, 24 after the cover, from its top: top line (dot, system · region, `meta` 16), 12, title (`text` 600, star kept, cut before it), 12, "1994 · Nintendo", 4, "Racing · 8/10" (genre and ScreenScraper's rating out of 10, one decimal only when not whole, 0 counts as none; no players count; a long genre is cut by measuring, the rating stays) (`text2` 15, one line each, cut at the end; 15 is off the ramp: at 16 the genre beside a 240 cover was cut at Small, kept until the person picks a smaller cover or a cut genre) | title 26, 3 lines | title 20 (22), 2 lines |
| Under the cover or the text beside it (the lower), 20, full width: the description (`text2` 15 on a line of 22, cut with "…") | at most 3 lines | 2 |
| Right above the played line, 4 over it and 16 at least under what is above: the file Play launches without its extension ("Silent Hill (USA)", the Info panel's File) (`muted`, one line cut with "…"; none and no gap when unknown). It gives way to the description's last line when both do not fit: at Largest only; on Roomy in Plum Serif, on Compact in every font | 15 | 15 |
| The played line on the bottom margin, 16 at least under the description (`text2`, one line cut with "…"; its place is kept while the play is read): the emulator A starts, in the Info panel's words (the part cut when the line is too long), then the play, always whole: "DuckStation · Played 3 days ago · 1 h 35 min", "DuckStation · Not played yet", "None installed · …" | 16 (17) | 16 (15) |

A part with nothing is left out and the rest close up. At large text sizes the description shows
only the lines that fit above the played line (none if not one fits). No cover (none saved, or the
file cannot be read): the same parts as one block on the bottom margin, full width (top line, title,
fact lines, 16, description, 16, file line, 4, played line). No details at all: the picking card. Code:
(code removed).

**Statistics card** (while Settings is open, any section; code
`app/.../secondscreen/SecondScreenStatisticsCard.kt`). Settings › Statistics' figures and words, without
Version. One block on the bottom margin, left-aligned, full width; the space above stays empty.

| | Roomy | Compact |
|---|---|---|
| Margins, all round | 40 | 28 |
| Top line "Statistics" (dot, `meta`), as the picking cards' | 16 | 13 |
| 12, the total play time (`text` 600, one line, never cut) | 36 | 26 |
| 4, "Total play time" (`text2`); the same line carries the week: "Total play time · 2 h 10 min this week" / "· none this week" / "· less than a minute this week" (one line, never wrapped; when it does not fit the width the week part is dropped, leaving "Total play time"; no week part until a session is recorded); or instead of both, 12 under the top line, "Nothing played yet" (`text2` 600) | 16 | 16 (Nothing played: 26 / 20) |
| Group gap, then the three most-played games, a row gap apart: cover slot (box art set up only; the box front fitted inside, corners 8 / 6, else the `chip` stand-in tile), 16, title (`text`, cut with "…"), 16 at least, time at the right on the title's first baseline (`text2`, never cut) | slot 48, title 2 lines, gap 8 | slot 36, title 1 line, gap 6 |
| Group gap, then "Super Nintendo · Most-played system" (name `text`, separator `muted`, label `text2`), Games played, Games with box art (box art set up only), a row gap apart; words wrap, figures at the right (`text2`, never cut) | 16 | 16 |
| Group gap / row gap | 20 / 8 | 12 / 6 |

Out of height: lines claim the height in this priority, highest first: the
first, second and third most-played game, the most-played system, Games played, Games with box art;
the top line and the total always stay. A line that does not fit what is left is skipped and the
lower lines are still tried, so a one-line Games played shows when a two-line system line did not
fit. Lines that show keep their place on the card. At Small nothing is skipped at either size, with
or without the week part; at Largest (Plum Sans) the box-art line and the system are skipped with
box art set up, Games played alone signed out. Shots `second_screen_statistics_*`.

**In game** (while a game or an app Plumemu started runs; code `core-ui/.../theme/SecondScreenInGame.kt`).
Black: the window stays and draws only black. Clock and battery: always on black whatever the
picked background, one centred block; fixed sizes and font (Plum Sans; the sizes are dp, so neither Android's font scale, Text size nor the
person's font applies, the burn-in numbers were measured on these):

| | Roomy | Compact |
|---|---|---|
| Time, 400, tabular digits, `#646464`, line 88/84 of the size | 84 | 60 |
| Battery line, 8 below: outline glyph (26 x 13, stroke 2, 3 x 5 nub, fill 2 inside by level), 6, then "82%", `#555555` | 20 | 16 (glyph x 0.8) |

Wi-Fi glyph (`WifiGlyph`, as in the top bar) on the battery line, 10 (`wifiGap`) before the battery glyph, in the
line's colour (`#555555`, `#8E3740` when low), its ink as tall as the digits' ink and centred on it within 1 px (measured by
`InGameWifiTest`); only while connected through Wi-Fi and while the line shows (no battery reading, no glyph). The card
follows Settings › Home screen › Show battery like the top bar: off, the card is the time alone, centred, with
neither the battery line nor the glyph, and nothing listens to the battery or Wi-Fi; a change shows at once. It moves with the block.

**Battery part** under the clock card (with Show battery on). Unlike the clock card it follows the person's font and Text size (not Android's font scale), with tabular digits in the head, the chart's labels and the facts alike, so a figure keeps its width as it changes. Plum Sans's digits are tabular with a slashed zero in every weight (its design, as everywhere in the app); Plum Serif's become tabular only through the feature. Durations never pad a zero ("1 h 3 min"):

| | Roomy | Compact |
|---|---|---|
| Frame (the clock card at its top, everything drifting together) | 32 top and bottom, 40 sides | 32, 28 |
| Level ring at the left, after the clock card: track `#333333`, arc from the top clockwise by level `#5C5C5C` (low `#A0404A`), "63%" inside in `#555555` (low `#A0404A`); shown only when the head fits beside it. While it shows, the clock card drops its battery glyph and percent (the Wi-Fi glyph stays); the clock card alone keeps them | 28 under; r 30, stroke 2, 20 to the text | 20 under; r 22, 1.5, 14 |
| Time left beside it: number 600, words 400 ("45 min left", "7 h 50 min left", "1 h 3 min to full"), `#767676`; at 15% and below, not charging, `#A0404A` | 36 + 20 | 26 + 16 |
| "empty at about 11:15" (on five minutes) / "full at 22:50" (Android's exact) / "full at about …" under it, `#767676`; gives way first, unless it costs no height | 16 | 16 |
| Chart under the head: what height is left, at most; runs from the play's first point (to the minute) to empty (charging: full), at least 60 minutes wide; at most 12 h ahead of now (further, the forecast stops on its way down: no end ring, no end time under it or in the head); vertical scale as `BatteryChart.scale` | 16 under; 144 | 10 under; 104 |
| This play's line (`#767676`, low `#A0404A`, round joins) over a fill of its colour at alpha 0.12 at the line fading to none at the floor; ahead `#5C5C5C` fading to 0.3 of its alpha along its length over a fill of 0.04 fading right and down; a ring (`#3A3A3A`, r 5, stroke 2) at its end; usual pace dotted every 7 (`#555555`); now a dot in a radial glow of 2.6 x its radius at alpha 0.25 with a dotted upright guide (`#333333`, 1.5, 6 apart) to the floor (`InGameColors`). No baseline, no grid; nothing touches the frame's edges | 3, 2, 2, r 5 | 2.5, 1.5, 1.5, r 4 |
| Hour ticks under the floor on the clock's whole hours (every n hours when over 12), 6 under, 4 long, 1.5, `#333333`; labels 4 under: "Started 2:40" left, "Empty 11:15" / "Full 22:50" right, `#5C5C5C`; the start gives way when both do not fit | 16 | 16 |
| Facts row centred at the bottom, 10 / 6 under the labels: "Played 0:44 · Used 6% · 8% an hour · as usual" (charging "+40% an hour · since 21:20"; Used left out when the play charged), separators `#555555` with 8 each side; out of width Played goes first, then Used, then usual/since | 16 | 16 |

Out of height (chart under 64 / 48) parts give way in order: "empty at", then the note, then the facts,
then the labels; still under the minimum, no chart. The ring shows only when ring, gap and head fit. The drift note takes the facts'
place while it shows. Notes ("Measuring battery use…", "Moves a little every minute to protect the screen") are 16,
`#767676`, centred at the bottom of the frame, at most two lines. Roles `SecondBattery*` in `TypeRole`.

No seconds; 12 or 24 h as Android is set. Charging: "Charging · 82%". At 15% and below, not
charging, the battery line (glyph and text) is `#8E3740`. The block stays centred and drifts a few dp
against burn-in: a step a minute (2 dp sideways, 4 dp up or down) along a column-major serpentine over 5 x 7 points (x +-4, y +-12 dp), there and back
(68 minutes), mostly up and down; the widest texts stay at least 67 dp (Roomy) and 94 dp (Compact)
inside the window at every point.

## Keyboard

Plumemu's own on-screen keyboard. `PlumKeyboard` in core-ui.

- **Tray**: from the screen's left edge to its right edge, from 8 above the keys down to the bottom of
  what the caller gives it (the caller puts its hint bar on it, 6 under the keys; the hint bar keeps its
  usual left margin). Fill `tray`, a 1 dp
  hairline `trayEdge` along its top, and a faint shadow upwards (the board's `box-shadow: 0 -6px 12px -4px`,
  black at 18 % with light text, 2.8 % with dark text (the board's 45 % and 7 % at `Depth.SHADOW`): the text colour decides, not the background). The tray is the tone ladder's surface step and its word keys the top step (Tone ladder under Colour): lighter than the page on dark and light alike, in the page's own hue.
- **Keys** (after trying Search on the Thor: "smaller and tidy, not full width, not so
  thick"): 10 columns of **44** with 4 between columns and rows, so a 476 block **centred** on the screen;
  the tray stays edge to edge. Under 476 + 2 × 24 = 524 wide the block shrinks to `width − 48` (none of the
  reference sizes). The width does not grow with the text size. Keys **36 high at every text size** (the
  labels still scale and fit: Largest, 23 sp, in 36), corners 8. The keyboard is 8 + 4 rows + 3 gaps high
  (164); the **tray** a screen shows is that plus the 6 gap, the hint bar (22 × the height factor) and the 9
  bottom margin (`PlumLayout.keyboardTrayHeight`, 201 at Small; the figures below were measured with the old 12; the Thor at Large went from
  226.8 to 206.3, a sixth whole result row showing). There is no half-screen limit on the key height any more: every size on 432 and 471 high is under half the screen; the RP4 Pro (375, half 187.5) is above it, as
  before (keys stay 36 for the touch area).
- **Rows** (spans in columns): `q`–`p` / `a`–`l` half a key in / shift 1.5, `z`–`m`, delete 1.5 / `123` 1.5,
  hide 1, Space 4.5, action 3; while the clipboard holds text, a paste key (clipboard icon, 1) takes Space's first
  column on every page: hide 1, paste 1, Space 3.5. Symbols: `1`–`0` / `@ # $ % & - _ + ` / `=\<` 1.5, `., : ; ! ? /`, delete /
  `ABC`, hide, Space, action. Symbols 2 (from `=\<`): `[ ] { } < > = * ^ ~` / `` ` | \ ' " `` in the middle of
  the row / `#+=` (back to Symbols), the same punctuation row, delete / `ABC`, hide, Space, action: every
  printable ASCII character is on some page. A field without shift (Search ignores case) leaves the shift
  key's place empty; the letters keep their places.
- **Look**: letter, digit and symbol keys have no fill, `text`, 16 × text size, 400. Keys with a word or an
  icon (shift, delete, `123`, `ABC`, `=\<`, `#+=`, hide, Space) on a `trayKey` fill, `text2`, 13, 400;
  icons 18 × the height factor. The action key ("Search", "Next", "Sign in", "Done") is the field's primary button:
  accent fill, its word in `onAccent`, 600 (accent text on `trayKey` was under 4.5:1: 3.42 with the default red on
  Graphite); focused, the accent ring goes around it and, since an accent ring on an accent fill does not show, a
  second 2 dp ring in `onAccent` is drawn inside the fill, 3 in from its edge. `onAccent` on the accent reaches 4.5:1 for every preset pair except
  the default red on Midnight (4.45), Plum (4.38) and Forest (4.24), as for the badges. **Focused key, whichever key: the strongest thing on the keyboard**: the accent cap, its label in `onAccentFace` at 600, the groove lit, and the inner `onAccent` ring, which the action key at rest does not have (`KeyboardFocusLookTest`). Shift on: its arrow in the accent;
  once draws the arrow's outline, locked fills it and adds a bar under it (the caps-lock mark).
- **Touch**: a key's touch area is the key and half of each gap around it (half the gap above the top row
  too, and half below the last row, 2 dp past the keyboard's own height): 44 × 40 for a letter at every text
  size and width. One tap focuses the key and presses it.
- **Gamepad** (the state, `KeyboardModel`): Left and Right step one key; at a row's end a fresh press wraps
  to the other end, a held one stops. Up and Down go to the key of the next row whose centre is nearest,
  aiming at the column where the run of Up and Down began, so Up then Down comes back to the same key;
  between two as near, the key the last Up or Down just left wins (so Down then Up, or Up then Down, retrace the same keys even in a chain), then the one nearer the key the move starts on; if still tied **Down takes the right one and Up the left one**, as on a physical keyboard whose rows
  are staggered to the right going down (e→d, r→f, t→g going down; d→e, f→r, s→w going up). Up from the top row and Down from the bottom row leave the keyboard
  (the caller decides what that means); a held press stops there. A page change keeps the row and takes the
  key nearest the same centre. Shift: off, once (one capital letter, then off; a digit or symbol does not
  spend it), locked (pressed again while once), off (pressed again).
- **A field typed with it** (Search): the text in its usual style with a static caret, accent,
  `PlumLayout.caretWidth` (1.5 × the text scale) wide, a line high, never blinking; an empty field shows its
  prompt muted with the caret at its start. A text wider than its room slides left to keep the caret in view.
  The tray slides up and down by `translationY`, 180 ms on `Decelerate` (none with motion off).
- **Cost**: nothing runs while idle (no animation, no caret, no frame callbacks); a focus move
  recomposes the two keys involved; a shift change the 26 letters and the shift key (`PlumKeyboardTest`).
- Checked by `KeyboardModelTest` (rules), `KeyboardFit*Test` (576, 768, 837 and 667 wide: the block 476 and
  centred, keys 36 at all six text sizes, the drawn tray within half the screen except on the 375 high screen,
  40 dp touch areas, no label cut at Largest in Sans, Serif and Mono), `PlumColorsTest` (4.5:1 for the key texts on every preset) and the
  `keyboard_*` shots.

## Colour

Three settings go in; every other token comes out. There is no "theme": dark and light are just two
background presets. **Nothing unreadable**: text and accent that cannot be read on the background are refused when
picked (with a suggestion that passes: Suggesting a colour, below), moved in lightness only (same hue) when a background change
breaks them, and drawn adjusted when a stored combination fails. One check, `Readability` in core-ui: text passes when every derived
text token meets the contrast targets in Derived tokens below (text itself at least 7:1 on the background; secondary text reaches 11:1 where the text allows and is the text itself otherwise), the accent at 3:1 on the background. The ready-made sets pass the same check (text 7:1 on the page) and are held to the other targets below (`ColorSetsTest`).

**Inputs**

- Background: the default is the Plumemu set's deep blue-black ink `#161A24` (an own colour, not a preset; on an OLED handheld setup's default tile is true Black under the same accent, and that page still reads as the Plumemu set); presets Black `#000000` (plain black suits OLED screens), Graphite `#111214`, Midnight `#0B1220`, Plum
  `#1A1020`, Forest `#0E1A14`, Paper `#ECE8DF`, Sand `#EEE6D6`, Mist `#E8ECF0`, then Lemon, Tangerine, Sky,
  Lavender, Cobalt and Wine (the first page: the 8 presets above and these 6, then "+"), or your own
  colour (the "+" page): a hand-picked palette of 8 hue columns (neutral, red, orange, yellow, green, teal, blue,
  purple) curated to at most 3 rows of 8 (`PaletteCuration`: from each hue's six cells its lightest,
  middle and darkest, and no two tiles closer than 12 in CIE76 colour difference; every tile passes the readability check
  of its page), walked
  with the D-pad, then three sliders (Hue, Saturation, Lightness; see Settings › Colour sliders) and
  a hex code typed with the Android keyboard (Y). The "+" grid's eight tiles share the container's inner width
  (about 22 dp at 768 and 576 wide), gap 6 dp on wide screens and 8 in the narrow panel stop; the presets pages keep
  24 dp swatches (gap 6) and 32 dp (gap 8) in the narrow stop.
- Text: any colour. The panel offers 6 to 8 presets that read on the shown background and nothing else:
  Light `#EBE7DD` or Dark `#1C1C1E` first where it passes, then tinted lights (on a dark background) or
  tinted darks (on a light one), each moved only as far as it needs to pass `Readability`; a background
  no text reaches 7:1 on offers none. Changing the background
  keeps the text if it still passes `Readability`; a Light or Dark preset that fails becomes the one with
  the higher contrast against the new background (light text left on a light
  background would hide the Text option itself), any other colour moves in HSL
  lightness only. A refused pick is offered a suggestion (below). Where no text colour can pass (a background of middle lightness, roughly relative
  luminance 0.05 to 0.5) the better of Light and Dark is used. The light/dark **mode** everything else branches on
  (strong text, preset accents, system bar icons) is derived: text lighter than the background is Light.
- Accent: the default is `#3A7CEB` (a custom colour, not a preset); presets Red, Amber, Lilac, Blue, Green, Mono (the text colour), or custom. Two preset
  sets: with light text `#DA4655` `#E8A13A` `#B583E8` `#6FA8FF` `#5FD38D`; with dark text
  `#9E2A3A` `#8F5500` `#7A3FC0` `#1F5FBF` `#1A6B3B`. The default `#3A7CEB` is 4.36:1 on the default page and 4.70:1 on Graphite. A picked accent is kept while it reaches 3:1 on the background; one that no longer does moves in lightness only (same hue). Small
  labels such as "Current" and "Default" are badges (accent fill, `onAccent` text, 11 sp bold),
  so they stay readable on tinted panels with any accent.

**Derived tokens** (`mix(a, b, t)` = straight RGB blend; "reach n:1 on X" = the smallest step from
background towards text that reaches n:1 against X)

Derived-token values below are for the Graphite and Paper presets (the screenshot-test pair);
the default page derives the same way.

| Token | Rule | On Graphite | On Paper |
|---|---|---|---|
| `sel` focused row fill | mix(bg, text, 0.10 light text / 0.09 dark text) | `#272728` | `#D9D6CE` |
| `sel2` small panels | the tone ladder's surface step (+3 L*) | `#17181B` | `#F5F1E7` |
| `text2` secondary text | reach 11:1 on bg where the text allows, else the text itself | `#CCC9C1` | `#2D2C2D` |
| `meta` facts, values on focused row | reach 6:1 on `sel` | `#AAA7A1` | `#4A4948` |
| `muted` small text | reach 4.6:1 on `sel` (so it also holds on the focused row) | `#94928D` | `#5A5958` |
| `chip` key-cap borders, switch ring | reach 3:1 on bg | `#646360` | `#84827E` |
| `strong` focused text | white on light text, `#0F0F10` on dark text | `#FFFFFF` | `#0F0F10` |
| accent | as picked (while it passes); the default is `#3A7CEB`, a preset is the colour of the matching set | `#3A7CEB` (Red `#DA4655`) | `#9E2A3A` (Red) |
| `onAccent` text on accent | background or text, whichever contrasts more with accent; if that is under 4.5:1, pure black or white, whichever contrasts more (pure black or white is the fallback because the dark text alone misses 4.5:1 on some default reds, for example on Midnight, Plum and Forest; the accent itself is not moved) | `#111214` | `#ECE8DF` |
| `tray` the keyboard's tray | the tone ladder's surface step (+3 L*) | `#17181B` | `#F5F1E7` |
| `trayKey` keyboard keys with a word or icon, the focused key; the hint and keyboard key face | the tone ladder's top step (+8 L*) | `#212225` | `#FFFFFE` |
| `line` a card's rules, an empty day's stub | the tone ladder's divider (10 L* toward the text) | `#252629` | `#CFCCC3` |
| `trayEdge` the tray's top hairline | mix(bg, text, 0.14); on a dark theme mix(bg, `surfaceInk`, 0.155) | `#313438` | `#CFCBC4` |
| `trayShadow` the tray's upward shadow | black at 18 % with light text, 2.8 % with dark text (the board's 45 % and 7 % at `Depth.SHADOW`): it follows the text colour, not the background (like `strong`), so dark text on a dark background of one's own gets a near-invisible shadow (accepted) | 18 % | 2.8 % |

Because tokens are mixed from the background or stepped from it, greys pick up its tint (on Plum they are plum
greys).

**Tone ladder** (`ToneLadder` in core-ui). Every tone a screen lays on its page is the page itself moved a fixed step of
perceived lightness (CIE L*), keeping the page's OKLCH hue and chroma (chroma cut only where the screen cannot show it), on
dark and light pages alike, so a blue page has blue cards and a cream page cream ones, never a grey or a white laid over it:

| Step | L* from the page | Used for | On Graphite | On Paper |
|---|---|---|---|---|
| Sunken | −4 | field wells, meter tracks (`fieldWell`, `meterTrack`) | `#050507` | `#E0DDD4` |
| Page | 0 | the background | `#111214` | `#ECE8DF` |
| Surface | +3 | small panels, the keyboard tray (`sel2`, `tray`) | `#17181B` | `#F5F1E7` |
| Raised | +6 (+7 at the top, +5 at the foot) | the raised card (`card`) | `#1F2123` to `#1B1D1F` | `#FFFDF7` to `#FBF7ED` |
| Top | +8 | key faces, word keys (`trayKey`) | `#212225` | `#FFFFFE` |
| Divider | 10 toward the text | rules and stubs (`line`) | `#252629` | `#CFCCC3` |

Checked by `ToneLadderTest` (every step within 0.3 L* of its target on dark, light and coloured pages, hue kept).

**Suggesting a colour** (`Readability.suggest`). When a picked text or accent is refused, the message offers a colour that
passes and looks right, not just the nearest lightness of the same HSL values:

1. It moves OKLCH lightness, keeping the hue the eye sees and as much chroma as the screen can show there.
2. An accent aims at 3.5:1 on the page, the sets' margin, not the 3:1 floor.
3. No mud: an accent that would keep under 85 % of its chroma (yellow, orange or lime going dark) turns its hue up to 40°
   toward the neighbour that holds its colour (yellow to marigold, orange to rust, lime to green).
4. On a coloured page (OKLCH chroma 0.06 or more) an accent within 60° of the page's hue is offered at the opposite hue
   (180°, then 150° and 210°, then 120° and 240°), at least as colourful as the page. On a grey page any hue is fine.
5. Text keeps quiet: a suggested text takes the page's own hue at OKLCH chroma 0.04 at most.

The floors stay the floors, and a stored colour is still drawn adjusted by lightness only (same hue). Checked by
`ReadabilitySuggestTest`. Accent marks the current system, the focused row's pill ring, focus rings, switches and primary
buttons.

The presets keep every text at least 4.5:1 against what is behind it (`PlumColorsTest`). A colour the person picks has to pass `Readability`, which asks 7:1 for the text itself.

**Ready-made sets** (Settings › Appearance › Colors). A set is only
a choice of the three inputs above; Colors shows the set whose background, text mode and accent
colours equal the current ones exactly, else Custom (the person's own colours, kept when a set is
picked over them). In `ColorSet` (core-ui `theme/ColorSets.kt`), cycle order:

| Set | Page | Surface | Text | Muted | Accent | Accent text | A, B, X, Y keys (letter) |
|---|---|---|---|---|---|---|---|
| Plumemu (default) | `#161A24` | `#1C202B` | `#EBE7DD` | `#9A9997` | `#3A7CEB` | `#FFFFFF` | — |
| Daylight (light default) | `#ECEBE5` | `#F5F4ED` | `#1C1C1E` | `#5A5A5A` | `#2A64D8` | `#FFFFFF` | — |
| Ultramarine | `#0F2142` | `#152849` | `#EBE7DD` | `#9CA0A5` | `#F4B740` | `#0F2142` | — |
| Magenta | `#240F1C` | `#2B1522` | `#EBE7DD` | `#9B9190` | `#F0649D` | `#240F1C` | — |
| Peach | `#FDE3D3` | `#FFEDE3` | `#1C1C1E` | `#5F5854` | `#00706B` | `#FFFFFF` | — |
| Matcha | `#E4ECD1` | `#ECF5D9` | `#1C1C1E` | `#585A54` | `#3D6E1C` | `#FFFFFF` | — |
| Mocha | `#F1E6DA` | `#FAEEE2` | `#1C1C1E` | `#5C5956` | `#A4492A` | `#FFFFFF` | — |
| Front loader | `#26272B` | `#2C2E32` | `#EBE7DD` | `#A8A6A0` | `#F2545D` | `#000000` | `#D22A35` (W), `#D22A35` (W), `#3A3B40` (W), `#3A3B40` (W) |
| Spice cube | `#231C50` | `#292357` | `#EBE7DD` | `#A7A2AD` | `#FF9F43` | `#231C50` | `#2EA557` (D), `#D93A3E` (W), `#BDBCC8` (D), `#BDBCC8` (D) |
| Pea green | `#C6D88F` | `#CEE097` | `#1C1C1E` | `#484D3B` | `#2F5F2F` | `#FFFFFF` | — |
| Pocket | `#D2D1CA` | `#DBDAD2` | `#1C1C1E` | `#4B4B4B` | `#8C1D4E` | `#FFFFFF` | `#8C1D4E` (W), `#8C1D4E` (W), `#8C1D4E` (W), `#8C1D4E` (W) |
| Lavender | `#DEDCE6` | `#E7E5EF` | `#1C1C1E` | `#525256` | `#5B3EA1` | `#FFFFFF` | `#4E3591` (W), `#4E3591` (W), `#B2A5DB` (D), `#B2A5DB` (D) |
| Red and gold | `#F3EBD8` | `#FCF3E0` | `#1C1C1E` | `#5C5A56` | `#9A1B2F` | `#FFFFFF` | `#9A1B2F` (W), `#9A1B2F` (W), `#C69A1E` (D), `#C69A1E` (D) |
| Ivory | `#F3EAD0` | `#FCF2D8` | `#1C1C1E` | `#5C5A53` | `#127087` | `#FFFFFF` | `#CF3A3A` (W), `#D9A400` (D), `#2F6FDB` (W), `#2E9A47` (D) |
| Clear grape | `#221838` | `#281F3F` | `#EBE7DD` | `#A39CA2` | `#BE98FF` | `#221838` | — |
| Cold grey | `#272B32` | `#2D3239` | `#EBE7DD` | `#ACABA6` | `#8EC3FF` | `#272B32` | `#3E6FD8` (W), `#2E9A47` (D), `#D9A400` (D), `#D9A400` (D) |
| Banana | `#F2CF43` | `#FBD74E` | `#1C1C1E` | `#584E28` | `#1F45A8` | `#FFFFFF` | `#C8302B` (W), `#0E7C7C` (W), `#926B00` (W), `#926B00` (W) |

Surface is the tone ladder's +3 step; muted is the `muted` token; accent text is what is drawn on an accent cap (`onAccentFace`:
white while it reaches 3:1 on the cap's middle, else the readable `onAccent`). Keys are the A, B, X and Y caps' fills, the
letter on each in brackets (W white, D the dark text `#1C1C1E`); a set without keys draws the usual caps.

Every set keeps the presets' targets: text 4.5:1 and accent 3:1 on
the background, `text2` 11:1, `meta` 6:1 and `muted` 4.6:1 on `sel`, `chip` 3:1, text on the focused
row, the tray and `trayKey` 4.5:1, and `onAccent` 4.5:1 on the accent (`ColorSetsTest`). **A set also keeps a margin for the focused row's look**: the accent at 3.5:1 on the background (the grid ring) and on the pill (the row's
ring), `onAccent` 4.7:1, and `strong`, `text`, `meta`, `muted` at their targets on the pill and the slab; the pill is at least 1.12:1 from the background, and so is the slab on a dark page and the slab's white rim on a light one (which keeps a light page below white).
**A set's earlier values still count** (`ColorSet.previousAccents`, `previousBackgrounds` and `formerLooks`, `ColorSet.settled`, applied in the
app's `toColorsAndLook`): stored settings holding an earlier accent or background of a set, or the whole look of a set that was taken out, with that set's text,
read as that set and draw its current colours; the stored value is not rewritten, and a backup restores the same way. Sets taken out and where they went:
Dot matrix → Pea green, Super → Ivory, Indigo → Spice cube, Charcoal → Cold grey, Cream → Red and gold, and the old
default (Black with `#3E7BE0`, or an own-colour `#DA4655`) → Plumemu; a button set stored under a removed name gives its successor's keys (`ColorSet.named`).
The first colour change made on such a look (page, text or accent) stores the successor's page and accent with it
(`withSettledColors` in the app's `ColorWrite.kt`), so the old page never comes back. The Paper set is not mapped: its look is the Paper
page with the Red accent, two presets anyone can still pick, and that pick stays as chosen.
Any other colour, near one of these or not, stays Custom. Add the old value whenever a set's colour moves. A glyph's
letter is white or the dark text colour, whichever contrasts more with its fill (all 3:1 or more,
`KeyCapLookTest`).

**Glyph colours drawn**. While a set with keys is the current set (derived:
`PlumTheme.buttons`), every `Hint` key cap that reads A, B, X or Y has that letter's
colour as its flat face (Controls under Shapes and motion); there is no ring, and the cap keeps its size and shape (20.67 high, at
least 20 wide), and the letter is the table's white or `#1C1C1E`, still 10 / 600. The colour is the
printed letter's, not the role's: with "Confirm with B" the confirm hint reads B and is B's colour.
START, SELECT, L1 R1 and every cap in the other sets or in Custom colours have the theme's `key` face and a `strong` letter. **A coloured cap needs no
colour contrast with the background**: its edge and shadow under it make it stand off (`HintGlyphColorsTest` checks the pixel under B on Banana), and the letter on it is what is read, as on the
handhelds these sets come from. Every letter is 4.54:1 or more on its cap. Shots `home_glyphs_<size>_<set>[_confirm_b]`.

**Colors panel**. A on Colors opens the side panel (wide: 248 beside the options, as
Background's; narrow: the further stop). Title "Colors", one help line, then a tile per set in cycle
order and Custom while the row has it. A tile is a tiny home screen 16:10 in its own colours,
drawn in fractions of its size: background, a `text2` top-bar line, the `sel` focused row with the
accent dot and a `strong` title, a `muted` meta line, and the confirm and back glyphs (the person's
letters) as small flat keys, in the set's glyph colours or the usual `key` face with a tenth of its width as edge; palette corners 6 with a 1 dp `chip`
edge. Its name is 4 under it, 11 sp (`colorTileName`, `text2`); the current set's 11 / 600 in the
accent (Focus and current). Tiles span the row-text lines (14 in on each side), 12 apart across and
down; a tile is at least 58 × the text size's height factor wide, so 3 per row in the wide panel at
Small to Large and 2 from Larger, at most 5 in the narrow stop. A name too long takes a second line
between words, never cut. The focused tile has the 2.5 dp accent ring; nothing applies while moving.
Checked by `SettingsColorsPanelFit{768,576,Thor}Test` (every font, six sizes); shots
`settings_colors_panel_<size>_<coldgrey|ivory|custom>[_largest]`.

## Shapes and motion

- **Accent face** (V2): every accent surface at rest (switch track on, Primary button, slider fill, choice disc, A key, keyboard action key, empty-state badge) is one flat colour where it is not a seated part (seated parts draw the accent cap gradient, Seated parts below), `PlumComposeColors.accentFill`: the accent 12.5 % toward white, the mean of the board's accent-to-lighter gradient (default blue gives #568BE4); text on it stays `onAccent`. **Dark surfaces** (lips, caps, tray) mix toward the text moved toward blue (`CollarLook.surfaceInk`), as the board's are cooler than its warm ink; the disabled label is `collar.offText` (0.443 / 0.41 of the way to the text), the empty-state disc `collar.well`.
- Row corners 8; setup choices 10; buttons and key caps are pills (fully round).
- Switch 34×20, knob 16.
- **Seated parts** (V2 kit board CSS, dp = px / 1.5; tokens `CollarLook.seat` = `SeatLook` in `core-ui/theme/SeatLook.kt`, drawn by `drawCssCap` / `drawLip` / `drawCssFace` in `components/CssDraw.kt`). Slider, segmented control, swatch, choice, text field, hint keys and keyboard keys are LIP + WELL + CAP, with the board's gradients (buttons and the switch are flat parts, below); this replaces the flat faces of decision 0011 for these parts (the bullet below stays for anything not listed). Every box-shadow is one layer of one cached bitmap per size and recipe (blur B px is a Gaussian of sigma B/2 px, spread grows the shape, an outer shadow is cut out of the box, insets are inside over the fill, the first listed is on top); steps stay under 2 of 255; never `BlurMaskFilter`.
  - (Parts of this list below that name a button or the switch describe the recipe the slider, segments and keys still use for their caps.)
  - LIP: radial gradient (ellipse 120 % x 100 % at the top centre, Paper 90 % high) of lip stops at 0, 45, 100 %; six layers on dark (inset 0 1.33/0.67 white .16, inset 0 -2/2.67 black .40, inset spread 0.67 white .03, 0 -0.67/2 white .03, 0 2/2.67 .45, 0 6.67/14.67 .45), five on Paper (brown shades, white rim). WELL: the well colour with inset 0 0.67/1.33 at .6 (Paper .25). Dark themes derive from the background toward `CollarLook.surfaceInk`, Paper toward warm white and brown, so Navy and a custom purple keep their hue.
  - CAP: gradient cap top to bottom, inset 0 0.67 white .16, a solid 0 2 thickness in `side`, drop 0 2.67/3.33 at .45 (Paper .20); pressed: the down gradient with inset 0 1.33/2 .22, moved 2 dp, cross-faded by the press. ACCENT CAP: accentLight (each channel 30 % toward 255) to accent, inset white .40, thickness accentDark (channel x 0.62), drop .35. OFF: `capOff` with inset 0 0.67/1.33 .18.
  - Hint key: thickness 1.33, drop 0 2/2.67 .40 (Paper .18), press 1.33. Keyboard letter key: no drop, top light .14; word keys 4 % lighter; the action key is the accent cap. Swatch: its colour with inset white .35, thickness black .30, drop .35. Choice: an accent dot with an accentDark thickness, no drop; unselected is the well. Field: the field well with inset 0 2/4 at .55 (Paper .248); in error tinted toward `danger`.
  - Focus: the lip glows with the part glow (blur 14.67 dp), the groove fills with the accent and swaps its shade for the lit inset (0 0.67/1.33 .35) with its own 8 dp glow; the switch adds a 1.33 dp accent ring, the slider knob a 1.33 ring with a 9.33 glow and its fill a 6.67 glow.
  - Labels on an accent cap (primary button, keyboard action key, confirm hint key, a selected choice's check) are white, as on the boards: the one named exception to the 4.5:1 floor (`onAccentFace`; white on the default #3A7CEB is 3.99:1). White holds while it reaches 3:1 on the cap's middle (the accent 15 % toward white); a paler accent falls back to `onAccent`. Every other text keeps the floor. Pinned by `SeatLookTest` (every layer and colour within 2 of the board), `SeatPixelTest` (a rendered seat at 1.5 px per dp) and `OnAccentFaceTest`.
- **Flat parts** (buttons and the switch; tokens `CollarLook.flat` = `FlatPartLook` in `core-ui/theme/FlatPartLook.kt`, drawn by `drawFlatButton` / `drawFlatSwitch` in `components/FlatPartDraw.kt`). A solid fill from the tone ladder; no gradient, no inner highlight, no thickness band, no lip, no groove well, no accent glow. Every colour derives from the page, the text and the accent, so each colour set gets its own.
  - BUTTON: Secondary and Danger cap = `ToneLadder.TOP` (+8 L*) with a hairline (`ToneLadder.line`: 0.5 dp, one pixel at least) inside its edge; Primary = the accent with a hairline of the accent 28 % toward black. On a light page a 1 dp drop shadow under the cap (the board's brown at 22 %), on a dark page none. Pressed: the fill only, nothing moves: a Secondary cap goes `PRESS_DELTA` (6) L* toward the page's contrast side (lighter on a dark page; on a light page 6 L* below the page, which is also at least 6 L* below the rest fill); a Primary cap goes 6.5 L* darker, so the white label keeps its contrast. Off: the ladder's `SURFACE` tone, no edge, no shadow, muted text. The footprint is unchanged (the collar's room stays, so `PartSizesBoardTest` holds).
  - SWITCH: the track is solid accent (hairline: the accent 28 % toward black) when on; when off the ladder's sunken tone (-4 L*) with a 1.5 dp outline drawn inside the track's bounds (size and layout unchanged) in the page's tone stepped from `ToneLadder.LINE` toward the text until it is 3:1 against the page, so an off switch shows on every set (pinned for every colour set and preset by `FlatPartLookTest`). The on thumb is a solid light tone (the page 90 % toward white on dark, 96 % on light) of 18.67 dp; the off thumb is filled with the outline's colour, 3 dp smaller; both have a crisp 1 dp shadow and no glow; a locked switch has the disabled tone. Track 40 x 22.67 dp, as before.
  - FOCUS (button and switch): the same rim as a cover's (one rim: `FocusLift.rimWidth` 2 dp on the part's own edge, no gap, `RIM_ALPHA` 0.96, near-white on a dark page and the text ink on a light one, `drawRimStroke`), but no scale: `drawGluedRim` in `components/FocusRing.kt` adds a tight glow (sigma `FocusLift.glowSigma` 2.25 dp) and a small depth shadow that is the parts' own (`FlatPartDimens.shadowDrop` 2 dp, `shadowBlur` 4 dp, alphas `GLOW_ALPHA_*`, `SHADOW_ALPHA_*`: parts are small and sit close together, so both are shorter and weaker than a cover's). The accent is not used. A panel leaves `focusRimRoom` (12 dp) above and below its buttons (`ringRoomAboveAndBelow`) so nothing is cut by the scroll clip.
  - SWITCH STATE (WCAG 1.4.11): the off state is told by the outlined track (`FlatPartDimens.switchOffOutline` 1.5 dp, inside the track's bounds) and a thumb of the outline's colour (3 dp smaller than the on thumb), the outline being the page's tone stepped toward the text until it is 3:1 against the page; the on state by the accent track at 3:1 and by the knob side (`FlatPartLookTest`).
  - COST: at rest a flat part is plain shapes (a rounded rect, a stroke, on light pages one offset rounded rect): no cached image, no layer, no clip. A focus adds one cached image (the glow and the depth shadow, two layers of one `drawCssShadows`) and one stroke, faded by the focus progress. Pinned by `PlumButtonFlatTest`, `PlumSwitchLookTest`, `FocusPartGlowTest`.
- **Controls (decision 0011, points 2 and 3).** Keys have a flat face that stands off the background: one solid colour (no gradient, dome or texture), a 2 dp edge under it, a 1 dp hairline of light on its top edge and a soft 3 dp shadow, drawn by `drawFlatFace` in one draw block inside the item's bounds except the edge and shadow, which hang below (footprint and text do not move). Shapes are pills and circles. Applies to the hint keys (A B X Y START SELECT L1 R1; an A/B/X/Y cap in a button set keeps its printed colour as its face), dialog buttons (`SetupButton`: the main one in the accent, the others in the `key` face), the switch knob and the colour slider thumb. Face colours are tokens (`FlatFace`): `key` is the background moved 17 % (dark theme) or 11 % (light) toward the text; the edge is 85 % (dark) or 23 % (light) toward black; the knob is a pale face on both themes. Pressed: the hint cap sinks into its collar in its darker pressed tone, the letter kept. Focus stays the ring plus halo. Tracks and fields are sunken (`drawSunken`): the switch track, the slider track, text fields (`sunkenField`, a `sel2` pill reaching 10 / 5 dp past the text's box so the row does not grow) and the progress bar get a 1 dp inset shadow on the top edge (black, 55 % on a dark theme, 28 % on a light one). Checked by `FlatFaceScreenshotTest` (shots `flat_face_*`).
- **Collar (`drawCollar` / `Modifier.collar` in `core-ui/components/Collar.kt`).** Every touchable part sits in a lip (a soft rim of the surface's own colour: a crest (about 1.3 dp, the board's 2 px at 1.5x) of lighter tone along the top edge, the body, a darker band under, a three-layer drop shadow below), then a fine groove, then the part itself; the collar is added outside the part, which keeps its size. Sizes (dp, lip + groove; the bottom is larger): control (button, switch, slider, segment, swatch, field) 5.33 + 1.33, bottom 6 + 2; small key (hint, keyboard) 2.5 + 1, bottom 3 + 1.5; box cover 2.5 + 1.5, bottom 3 + 1.5. The outer corner is the part's radius + groove + lip. Colours derive from the background and text (`CollarLook.of`: lip top/body/bottom and groove as shares of the way to the text, white, black or (Paper's groove and ring) the brown shade, matching the board on Graphite and Paper). Soft shadows are cached Gaussian images of the rounded shape (the slab's in `SoftImages.drops`), so no step shows (`SoftShadowStepTest`). A Paper cap's own shadow is the board's brown at 5.8 % (`FlatFace.LIGHT_CAP_SHADOW`): it tints the groove ring beside the cap to the board's #C4BBAF at rest. `groove` is the sunken track tone (slider track, off switch: Paper #D7D0C3); `ring` is the groove ring between lip and part (Paper #CCC4B8, a step darker; the same as `groove` on a dark theme). A Paper text field's fill is `FieldDimens.well` (#E0DBD0), its ring the collar's. Hint bar labels use `hintLabel` (Graphite #9699A0, Paper = `body` #4C4942). States: focus lights the groove in the accent and draws the focus halo (none on light themes) outside the lip, animated 0..1; pressed sinks the part (`drawFlatFace` press); off is a flat sunken part (`keyOff`, no edge or shadow) with `muted` text, never an opacity; error draws the groove in `danger`. The dark shadow reaches 3 dp at the sides and 6 dp under the lip; the light themes' (`CollarLook.softShadow`) is the board's `0 10px 22px` in brown at 10 %, a Gaussian of sigma 7.33 dp 6.67 dp lower in 16 stacked layers (`CollarDimens.softShadow*`, `drawCollarShadow`): about 9 dp at the sides, 2.7 above, 16 under. The focus glow reaches 14 dp around it, so leave that unclipped and keep collared parts at least 6 dp apart (10 dp where a lit halo must not be covered by the sibling drawn later). Focused and in error: the groove stays the danger colour and an accent ring is drawn just outside the lip.
- **Collar sizes (V2 board, 1.5 px per dp; `CollarSize`, pinned by `PartSizesBoardTest`).** Control: lip 5.33 and groove 1.33 (6 and 2 under). Key (swatch, choice mark): lip 2.67, groove 1.33 (3.33 and 2 under): swatch 34.7 x 36, choice 24 x 25.3. Hint (hint keys): lip 2.67, groove 0.67 (3.33 and 1.33 under): single key 29.3 x 28.7 around a 22.67 x 20.67 cap; a wide key (START, L1 R1) pads its label `HintKeyDimens.capPadding` 8.67 each side (board `0 13px`), a button 20 (board 30 px): our type is bigger (16 sp against 14.7), so they are wider by their text only, never by padding. Segment: lip 4, groove 1.33 (4.67 and 2 under) plus the tray's 2 dp: 45.3 high around a 29.33 pill. Field: lip 4, groove 1.33 (4.67 and 2 under) around a well of 37.3 dp times the text size: 49.3 high. Slider: lip 5.33 above and below a 7.33 track (18 high), the fill and hue band 1 dp inside the track (5.3). The drop shadow reaches 8 dp (4 dp below the part's top edge): 4 dp each side, none above.
- **Panels (decision 0011, point 5).** The game panel (menu, Info, Fix name, Fix box art and the others it opens), the update card ("What's new") and the home-role and pin cards wear one raised card (`Modifier.raisedCard`); the slider's bubble keeps `drawPanelChrome`: a hairline edge (`panelHairline` 0.5 dp, `panelEdge` = the text colour at 16 % on a dark theme, 14 % on a light one) drawn inside the border, corners `panelCorner` (10), and a soft shadow of four stacked rounded rects reaching `panelShadowReach` (6) and sinking `panelShadowDrop` (2), black at `panelShadow` (45 % peak on Graphite, 14 % on Paper, split over the layers); no blur, no layer, read while drawing so a focus move redraws nothing of it. The game panel keeps its size and place: its card ends where the strip does, and its shadow only `panelShadowBottom` (2) further down, so the shadow stays 8 dp or more clear of the hint keys (the strip ends 10 above them; `GamePanelChromeTest`); text and rows keep their place. A small accent dot (`PanelTitleDot`, `panelTitleDot` 6, the hint dot's size) follows the title text `panelTitleDotGap` (8) after its last visible character, centred on that line (the last line, or the last visible line of a title cut with an ellipsis; `PanelTitleWithDot`, a custom layout reading the text's line geometry); room for the dot is held back, so a long title wraps (a card) or is cut with an ellipsis (the game panel, 2 lines) a word earlier and the dot never reaches the panel's edge or hangs at the end of a first line. Test `PanelTitleDotTest`. Lists, rows and text inside are flat as before. Settings' side panels are drawn by Settings itself and are not changed yet. Test `PanelLookTest`, shots `game_menu_*`, `pin_confirm_*`, `whats_new_*`.
- **Press and hover of a key** (decision 0011). Pressed (a finger, the confirm button on a focused button, the held button on its hint key): the face moves down by the edge (2 dp) into it, the edge disappears and the shadow fades away, the content on the face moves with it, and the face takes its darker pressed tone with an inner top shade (hint key and button alike, never a fill in the text colour). Down over `PRESS_IN_MS` (60 ms), up over `KEY_PRESS_OUT_MS` (140 ms; rows and cells keep `PRESS_OUT_MS`, 100 ms), ease-out; a press shorter than 100 ms still shows the whole dip. A focused button, or one under a mouse or stylus (Compose hover; hover never moves the focus), is raised: the face 1 dp higher, the edge 3 dp, a little more shadow, the face a touch lighter (`RAISE_MS`, 100 ms), on top of the ring and halo; the others rest. A tap anywhere on a Settings switch row widens its knob too. A switch's knob slides between its ends and grows a quarter wider while pressed (`on` knob is `onAccent`); the colour slider's thumb goes down while a finger holds or drags it. With animations off (the app setting or the animator scale 0) every change is instant, and a press still shows at once (face down plus colour flash). A cancelled press (finger dragged off, focus lost, the screen closed) returns to rest. One `KeyState` per key (`rememberKeyState`, `keyDrive`, `flatKey` in `components/KeyState.kt`), read only in draw and layer blocks: a press redraws that key and recomposes nothing; layout never changes. Checked by `KeyPressFramesTest` (face offset at rest, pressed, back; cancel; animations off; no recomposition), `HintPressedTest`, `HintPressRecompositionTest`.
- **Fields look the same on either keyboard, and in every row (PartsDark `Fields`, SPEC section 16)**: one core-ui frame, `FieldRowFrame` (`FieldRow` is its Android-keyboard text field; the own-keyboard lines `CaretLine` in sign-in, Fix name, Report a problem fill it). A row is an optional label column (on the row-text line), the board gap `FieldDimens.gap` (16 px = 10.67 dp), then the field: collar (lip + groove) and sunken well drawn INSIDE one box (`Modifier.fieldCollar`, `fieldContentPadding`), at least `FieldDimens.wellHeight` (56 px) plus the collar high (49.33 dp; the well grows a quarter of the text growth, `WELL_GROWTH`). Rows are `FieldDimens.gap` apart. No pill: the row has no focus fill, the cursor on it (or the input focus) lights the groove in the accent with its halo, so focus shows once. A field in error draws its groove in `danger` and tints its well 16 % toward it (`FieldDimens.errorWell`). Search keeps its own board's flat field (`sunkenField`, `searchFieldInset*`, CONFLICT F). Tests `FieldRowsBoundsTest` (box inside row, label gap, row gap, no button strip), `FieldRowFocusOnceTest`, `PartSizesBoardTest`.
- **Slider (`PlumSlider`)**: a sunken track in a lip, the accent fill up to a cap-face knob. The hue slider's track is the board's softened spectrum (coral, amber, green, teal, blue, violet, coral at matched lightness), never a raw HSV rainbow. Locked: the empty sunken track, no fill, the knob an off cap.
- **One curve.** Everything that moves or appears (glides, slides, swaps, screens, fades, the focus growing)
  runs on `PlumMotion.Decelerate` (ease-out cubic: most of the way early, a soft landing), so two things
  moving together never part ways; CSS ease-out is left to presses and raises (a key's own dip). No springs
  and no overshoot: a list must never look as if it ran past the game it stopped on, and every motion ends
  on a known frame. The tokens are time, curve, distance and scale only, never a colour or a shape.
- **Screens arrive with depth** (`SCREEN_MS` 240 ms, `SCREEN_DEPTH` 4 %): the incoming screen settles to its
  size while it fades in, from 4 % smaller going in (Settings opening) and from 4 % larger coming back (the
  home when Settings closes, and when a game or app started from it gives the screen back). Only the
  incoming screen moves; the one it replaces is already gone (the home is not composed under Settings). A
  screen that shows without being asked for (the screen waking, the first start) is there at once.
- **Steps sideways follow their direction** (`SWAP_SHIFT_DP` 16 dp over `GLIDE_MS`): another system's
  games on L2/R2 while the games have the focus slide in from below (next) or above (previous) while they
  fade in; each step of a held key starts again in its direction. A setup step slides in from the right
  going forward and from the left going back.
- **The launch**: on Play (or an app's open) the press shows on the first frame, the home moves into the
  focused cover or row (it grows `LAUNCH_DEPTH` 8 % about the item's centre) and dims to `LAUNCH_DIM`
  over `SCREEN_MS`; the game's window is asked for `LAUNCH_LEAD_MS` (60 ms) in, once ours is visibly under
  way, so Android's own opening animation takes over from ours (the lead is time added to every start), and the home never fades to black (a black frame on launch is a
  defect). Where Android honours it the window also grows from the item (`makeScaleUpAnimation`;
  Android 16 ignores it for another app's task). A window that has not come 2.5 s later gives the home
  back. With motion off: no animation, no lead.
- Strip slide about 180 ms on `Decelerate`; off when animations are off. A move within the column the
  strip is already sliding to lets the slide run on (it does not start over).
- System names follow the strip, not the focus: their alpha goes from 1 at the systems stop to 0
  at the games stop with the strip's actual position, so they fade out as it slides to the peek.
- **Lists glide**: the list scrolls in layout in the same frame as the
  focus moves, then the rows are drawn where they were and move to their place by `translationY`
  over **200 ms on a decelerating curve** (`PlumMotion.GLIDE_MS`, `Decelerate`: most of the way in
  the first quarter, a soft landing), like the PS Vita. A held key never cuts a row: each repeat
  starts from where the rows are drawn now and glides linearly at the repeats' own speed (one row
  per gap), so the list keeps moving at an even speed and never stops between repeats; the last
  glide of a hold is linear too, since nobody knows a repeat is the last. Two extra rows are
  composed past each edge (`ListGlide`'s room, 2 strides) so no gap shows while the rows lag.
  Games list, system column, search results, game panel lists and the grid (by rows). Off with
  animations off: a jump, no extra rows.
  Because the glide draws the rows up to two rows from their place, a list keeps **three rows past
  the focus** towards each edge while it can scroll that way (one with animations off): it starts
  scrolling early, so on every frame of a press or a hold, from its start to a hold's fastest
  repeat, the focused row and the row past it are whole, never under the top bar or the hint bar.
  Measured in `HeldScrollFramesTest` and `ListGlideTest`: with two rows kept, or the room at 1.25
  or 1.5 strides, a hold's quickening repeats cut a fifth of a row or more. Settings' sections and
  options columns, the Systems list and the panel lists keep the same three rows and glide the same way:
  200 ms on `Decelerate`, a held key retargeting at the repeats' speed, the focus pill drawn by the list so it slides from
  row to row and keeps each row's own height and inset (the sections' pill starts 10 from the edge). The licence list and
  texts scroll with the same 200 ms curve; with animations off all of it jumps. Checked by `SettingsGlidingPillFramesTest`
  (768 and 576), `SettingsScrollFramesTest`. A carried system (Library › Systems) draws its own solid pill, and the list's
  pill is hidden meanwhile. The
  grid (games and Apps) glides its rows too, with the same 200 ms and curve, a held key linear
  at the repeat speed, a jump with animations off. It scrolls by the rule it always had: only when the row below the
  focus is not whole, and it opens as it always did (no row kept past the focus, nothing changes at rest). When it does
  scroll, the new row slides in from the grid's edge and the grid's clip cuts it while it glides, so the focused cover may
  be cut during a glide; it stands whole at rest and once the glide ends (`HeldScrollFramesTest.gridHeld`). The ring does not follow the
  covers while they glide: it travels in the same time, so it stays in step (it stays put when its move and the scroll
  are the same row: the cover under it changes).
- **Fade-ins**, 110 ms on `Decelerate`, alpha only: a game panel sub-screen (Play with, Info, Choose an emulator, Fix name),
  search opening and closing. A move in the system column swaps the preview at once (a held key
  would strobe it). A change while a fade runs lets it run on; the first content never fades.
- **Scanning indicator**: while a library scan runs, a small accent dot (8 dp,
  8 before the line) breathes before the scan's status line, in setup and in Settings › Rescan
  library (also under its "Already reading your games" notice). Alpha only, through `graphicsLayer`,
  so nothing recomposes and nothing around the dot is redrawn: 35 % to 100 % and back over 1.6 s, ease-in-out,
  endless. Calm, not a spinner. Solid with animations off. When the scan ends the dot is simply
  gone (the "ready" state says it is done). The dot sits at the first baseline of its text, so a line
  that wraps keeps it by the first line. Only these two status lines have it: not the option row's
  "Reading..." value and not a folder's "Counting games..." detail (minimalism). One component, `ScanPulseLine` (`ScanPulse` is the dot) in core-ui.
- **Pressed**: a real button gives a feel. While a button is held, the
  hint whose key cap stands for it looks pressed, like a real key: the cap fills with the `text`
  colour and its letter takes the `bg` colour, and the cap face (only the cap, not the label) moves down
  into its edge as any key does (Press and hover of a key, above), so nothing around it moves. In 60 ms
  ease-out, out `KEY_PRESS_OUT_MS` (140 ms) ease-out; rows, cells and tiles come back over `PRESS_OUT_MS` (100 ms); a press shorter than 100 ms still shows the whole dip (the way in is
  never cut, the way out waits until 100 ms after the press; with animations off there is no wait: the release is at once, in the same frame). The timings and scales are tokens in `PlumMotion` (`PRESS_IN_MS`, `PRESS_OUT_MS`, `KEY_PRESS_OUT_MS`, `PRESS_MIN_VISIBLE_MS`, `PRESS_SCALE`, `PRESS_FILL_MIX`). A finger down on a hint does the same until
  it lifts. The cap lights for the button printed with its letter (hints print the physical letter: under Confirm with B the confirm hint says "B" and the button printed B lights it; the swap never renames a button). D-pad centre and Enter, which confirm, light the confirm hint's cap, Back and Escape the back hint's; "L1 R1" lights for either; "↑↓" and "→" for the
  D-pad. A hint that appears while its button is still down starts pressed. With animations off the fill
  changes at once and the cap does not shrink. Held keys come from the router (`InputRouter.held`) through
  `LocalHeldKeys` and are read only in a coroutine and in draw and layer blocks: a press recomposes nothing
  (`HintPressRecompositionTest`). Actions still fire on key down. Shots `hint_bar_{released,pressed}_*`.
  **Items press too**: the focused row, tile or button answers the confirm
  button, and any item answers a finger down on it, with the same driver and timing. The item scales to 0.97
  about its centre (`graphicsLayer`, so bounds and text baselines never move) and its focus fill moves 14 %
  toward the `text` colour (lighter on Graphite, darker on Paper; `PlumMotion.PRESS_FILL_MIX`, no palette token).
  With animations off only the fill changes. Only the focused item answers the button (the one the cursor is
  on), and only for a press that began while it was focused: an A that opened the screen shows nothing on the new
  focus. A finger shows it after the tap delay (`ViewConfiguration.getTapTimeout`) and only if it has not moved past touch slop, so a scroll
  never flashes a row; a shorter tap still shows the whole dip. An item that is neither focused nor touched does no work on a key
  event (no collector, no read of the held keys). It follows the confirm button's printed letter (`LocalConfirmCap`, from `ButtonLetters.confirm`), so
  under Confirm with B it is B. One modifier, `Modifier.pressFeel(focused, fill, shape)` in core-ui
  `components/PressFeel.kt`, replaces the item's `background`: `focusFill` (game, option and field rows),
  Settings' option row and `PanelItem`, `SetupButton` and `SetupChoice` use it. The home grid's focused cover
  and its collar dip as one about the cell's centre (`rememberConfirmPressScale`, read in the cell's draw block, the halo dips with it);
  a finger on any cover dips it with its collar (one shared finger dip per
  grid, `rememberTouchPressScale`; the Apps grid's tiles likewise). The system column and Settings' section rows
  (`SystemRow`), Fix box art's result rows and setup's info rows use `pressFeel` (`PressFeelFingerTest`,
  `PressFeelSettingsSectionTest`, `PressFeelFixBoxArtTest`). Settings' colour swatches, "+" tile, By system tile,
  Color code and Use this color rows, systems rows and licence rows use `pressFeel` outside their focus
  ring, so the ring dips with them (`SettingsPressFeelTest`). A field row has no pill, so it never dips (`SignInPressFeelTest`). Shot `home_list_row_pressed_*`; tests `PressFeelTest`, `PressFeelHomeTest`.
- **Held direction**: first repeat 300 ms after the press, then 120 ms, each 20 % quicker down
  to 32 ms, each wait rounded to whole display frames and
  never under 32 ms worth: 5 frames per row at 60 Hz (10 at 120 Hz), the floor 2 frames at 60 Hz
  (4 at 120 Hz). Every move is drawn and the cadence is even at any refresh rate.

## Sound

Three short interface sounds, our own, synthesized by `tools/sound/make_sounds.py` (sine waves and light FM, no
sampled or borrowed audio; in the spirit of a console menu's soft, airy, glassy ticks but not a copy, the design is
in the script's docstring), never the loudest thing on the device. They play as `USAGE_GAME`, so the media volume
sets them and Do Not Disturb or the ringer does not mute them (Do Not Disturb would otherwise make
every sound silent):

| Sound | When | Length, peak |
|---|---|---|
| `ui_move` | the focus actually moved: rows, columns, L2/R2 system or L1/R1 section switches, search results, panel rows | 32 ms, -20 dBFS |
| `ui_confirm` | A / Enter or a tap that opens, plays or toggles something | 120 ms, -14 dBFS |
| `ui_back` | B / Back that goes back or closes a panel | 100 ms, -17 dBFS |

- A press at the end of a list changes nothing and makes no sound. A held D-pad makes at most one
  move sound every 50 ms. Touch scrolling and flings are silent. No sound at app start; launching
  an emulator makes only the confirm of the press.
- Settings › Controls › Button sounds is three steps, ‹ Off › / ‹ Quiet › (35 % of the pool's volume) / ‹ Loud ›
  (full; the default, and what an old "on" reads as); changing it to Quiet or Loud plays one confirm at the new
  volume, Off is silent. Settings › Controls › Vibration (on by default) adds a haptic: a light tick on every
  focus move (at most one per 35 ms, so a held D-pad does not buzz) and a firmer click on a confirm; none for
  back. It is made where the sound is (`UiSounds`) with the best the device has (`Haptics.kt`: primitives, else
  the predefined tick and click, else a short low-amplitude one-shot, else a plain vibrate). The sound pool is made and loaded on a background thread when the app becomes visible
  and released in `onStop`.
- Each screen's model says what a press did where it does it (`Outcome`: moved, activated, went
  back, nothing) and the screen only plays that. A press that does nothing (A while a launch runs,
  SELECT on a list with no grid, X on an empty library, START or Y on an app) is silent.

## Launcher icon

A plum that is also a
round game button. Crimson `#DA4655` circle (the default accent until 2026-11-02), a darker `#A8323F` seam down its
left side, an off-white `#EDEDED` stem and leaf (the light text colour), on black. Flat, no
gradients or shadows. The whole mark fits a 54 dp circle in the 108 dp adaptive canvas, so no mask
clips the leaf. The monochrome layer (themed icons, Android 13+) has the same shapes with the seam
cut out. Every file is generated by `tools/icon/make_icon.py` (vector drawables for Android 8+,
PNGs for Android 6 to 7.1); change the numbers there and run it again, never the outputs.

- **Home details facts grid.** The cells' widths are measured from the text (`home/HomeDetailsShares.kt`): three equal columns when every label and the rating, played and last played values fit one line in a third, else two. A label never wraps and a duration (played, last played, battery) never breaks; an emulator or battery text that does not fit its column has a row of its own (the whole column). Checked by `HomeDetailsGridFitTest` at 853 x 480 (the Thor), 768 and 576.
