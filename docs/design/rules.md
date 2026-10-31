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

- Margins 24 left and right, 14 top, 12 bottom. Top bar 18 high, bottom hint bar 22 high, 10 between bars and content.
- Hint bar: each hint at its natural width, 20 apart. When they do not all fit (the Nova at 576
  with A, B, Y, X and START Menu, a wide font, a long language), hints give way in this order:
  "L2 R2 Switch system" (L2 / R2 switch the entry on every list and grid) first, then "L1 R1 Size" (a game grid only; L1 / R1 change the grid size there), then "B Back", then SELECT; START and Y are not in the order. A hint is never cut and hints never
  overlap. SELECT gives way before B because by touch SELECT is the only way between a list and its grid, while B has the system's back gesture. At 576 x 432 a games list still has no room for SELECT at Largest (A, Y, X, START and SELECT need 614 to 670 dp of 528) nor in Serif at the default size (542).
- Columns: systems 200, games list at least 400, details 248, 24 between columns.
- **Systems column alignment**: every system label starts at the page margin (24), the
  same left line as the top bar text and the first hint key cap, focused or not, so the label never moves when
  focus does. The column itself starts at the screen edge and is margin + systems column width wide (200 × the text size factor: 224 at Medium, 314 at Largest). The focus
  pill (the focused row is the same pill everywhere) starts 14 before the label, so 10 from the screen edge,
  and runs to the column's right edge at the row height; the label does not move with focus. Settings' sections column follows the same rule (below).
  Checked (pill inset 10, label 14 inside it, centred on the row) by `HomeSystemsAlignTest` at four sizes and all six text sizes.
- **System info band**: the games list of a system starts with a
  band at the list's left edge, as the list's **first item** (it scrolls away with the rows; in both column focuses; Enter and
  Back change nothing): `SystemBadge` (the short name in `systemNameCurrent` 16 / 600 in `strong`, in the person's chosen
  font, `chip` ring 1.5, corners 8, a 5 dp `accent` dot 6 in from the top left corner; at least 72 x 48, wider with the text,
  never cut), 14 to its right the facts line (`meta` 13, `text2`, centred on the badge, one line: it drops the kind, then the
  maker, to fit between the list's left edge and the screen's right margin), 8 under the badge a 1 dp hairline in `sel`, then
  the rows (one row fewer on screen while the band is in view; focus on the first game scrolls the list fully to its top, a deep remembered row opens with the band out of view). Nothing for Recent, Favorites, Apps or a system the platform
  data does not know; a system with no facts shows the badge alone. About text: under the facts line, `meta` 13 `text2`, 4 below it (`factsRowGap`), up to 3 lines, whole or not shown (never an ellipsis), in a column as wide as the facts line's room; the band grows to hold it (the hairline stays 8 below the taller of badge and text). Settings › Home screen › System info off: no band. Checked by
  `SystemInfoBandTest` (numbers at 576, 768 and 837 wide, Medium and Largest; a tap keeps its row under the finger; no drift
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
- **Narrow** (screen < 768): the list stays 400; the details peek in from the right: the same content
  as the third stop (cover, title, year and publisher, genre and rating, play facts, description) in the same layout, simply
  clipped by the screen's edge so the person sees there is more to the right ("show everything, but cut
  by the screen, so people know to go right to see it all"; this replaces the earlier rule of cover only, no text in the
  peek, `HomeDetails` `textHidden`: with the systems focused the column is off screen, so its text is not composed) and details become a third stop. In the details stop
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
    where `systemsOpen` is the margin plus the systems column (224 at Medium, scaled by the text size): 368 at 640 at
    Medium, 395 at 667, 400 from 672; a text size that would leave it under 320 (Huge and Largest at 640) keeps 400
    with the fade, as at 576. It covers the games stop (list at 72, details peek from 24 after the list, **the full
    details cut by the screen's edge**) and the details stop (`width − 104`, ends at the right margin).
  - Without box art, or with the details on the Thor's second screen, the list is still `width − 96` (544 at 640, 571
    at 667) once the games have the focus; only **while the systems are open** its width is the same `narrowListWidth`
    (`HomeStrip`, `previewFits`), so Recent, Favorites and every system show their year, region or code whole. The width
    changes at the moment the focus changes column; no stop depends on it (no details).
  - Settings with a section focused: the options column is `width − 272` (368 at 640, sections are 200 + margin +
    gap) so labels and values both fit (a long label wraps or is cut by its own row, the value stays); it widens to
    `width − 48` when an option is focused (`OptionsColumn`, `isPreview`). Checked in Vietnamese at Large and Largest.
  - Grids keep `width − 96`: their preview with the systems open still runs under the fade at 640 (left as it was).
  Checked by `HomeFourThree480BoundsTest` / `HomeFourThree432BoundsTest` (every home state, Recent with and without
  art), `SettingsSectionPreviewVi*Test` and `FourThreeScreens*BoundsTest` (Settings, setup, search).
- Systems focused: the details column (wide) shows only its cover, or nothing without box art: its text would be cut mid-word at the screen edge (as on narrow screens). The strip rests at its start; content past the right edge runs under a 32 dp
  fade. No fade where nothing continues (grids).
- Peek width 48 by default (adjustable 0–120 in Settings). What the peek shows is Settings › Appearance › Left edge: Game counts (default; each system's count, current in the accent, right-aligned in the row), System codes (the
  system's 3-character code in the count's place and style, same row positions, so the column lines up with the names when it
  opens, where the rows go back to their counts; whole at Largest, `HomeLeftEdgeBoundsTest`) or the letter rail: the letters of the focused list (first letter of each game's sort name, so "The" is skipped; digits under "#"), centred in the peek in equal
  slots over the column's height, the focused game's letter in the accent at weight 600 (`meta` /
  `smallFactCurrent`, 13); at least `railLetterMinHeight` (14 dp, grows with the text size) per
  letter, else a window of letters around the focused one. None for Recent and for a list sorted by
  last played. Names are hidden in the peek, so a long name in a wide font never pokes in; counts show
  again with the names when the system column opens.
- System rows (home column and the peek) are drawn at least 40 high, all equal (34 × the text size above Medium). A count from 1,000 is shortened to whole thousands ("1k", "99k") in the row, so it is no wider than a 3-digit count: it stays inside the peek and leaves a long name its room (B1, B3).
- Rows that fit: game list rows are designed 36 high and drawn at least 38 (Settings, option and field rows stay 40; Settings' section rows are 40 too, so the rows share one height; a focused two-line option row keeps `settingsTwoLineRowPadding` (6) above and below its text; the colour presets sit on the panel's text column, 14 in from each side, so 4 a row in the wide 248 panel, 10 apart; the panel's text column stays 14 in from the panel edge like every panel, moving it to the 240-wide grid would leave 0 on the left; at the largest text sizes the colour name and sample rows under the four rows are left out from the end when they do not fit). The 2 dp gap under a game row is part of its tap area, so a row's touch area is at least 40. The count follows the screen height and counts the gaps (8 on the RP5, 7 on the RP4 Pro, 9 on the Thor at Medium and at Large, 6 at Largest). Above Medium every box around text (rows, system rows, top bar, hint bar, key caps, setup buttons) grows by the size's factor (`TextSize.heightScale`, so a game row is 52 and a Settings row 58 at Largest; 36 × 1.1 = 39.6 at Large) and fewer rows fit: 6 game rows on the RP5 at Largest. The system column widens by the same factor so a system name keeps its line.
- Top bar, left: where you are. Systems focused "18 systems · 1,284 games"; games "Super
  Nintendo · 248 games"; Settings "Settings › Appearance" (and "› Background" in a panel stop).
- Search's top bar: the typed text starts on the results' text line (peek + column gap + row padding, the list's tokens; `SearchTextLineTest`), so the query and the titles under it share one left edge; a label wider than that room (a large text size) pushes it on.
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
| Focused row ("style 3") | A pill: `PlumColors.pill` fill (the background moved 16 % toward the accent, eased back toward the background until `meta` 6:1 and `muted` 4.6:1 still read on it, so any accent is safe) with a 2 dp accent ring drawn inside its edge (`PlumDimens.focusPillRing`); corners half the row's height up to `PlumDimens.cornerFocusPill` (20), so a one-line row is a full pill; `strong` text, weight 500; **no dot**, and the text does not move when focus lands. The pill spans the row's whole box (`Modifier.focusPill`, one modifier for every row; it is `pressFeel` too). System column: the same pill, starting `gameRowPadding` (14) before the label, so it is inset from the screen edge by margin − 14 (10) and the label stays on the page margin. The accent dot that remains means "new games" (system row), never focus. **Carried** (Settings > Library > Systems, A picks a system up): the pill turns solid accent with an `onAccent` ring and `onAccent` text (`Modifier.carriedPill`), so it is told from focused on every preset. Setup's cards keep their own corners (`cornerSetupChoice`, 10) focused or not (`focusPill(shape = …)`); display-only sample rows draw the look with `focusPillSample` (no press, no touch) |
| Focused cover, swatch or button | 2.5 dp accent ring, 3 dp away, with a very soft glow: shadow 0 0 12 dp, spread 1 dp, accent at 25%. **A grid item (cover or Apps tile) differs**: the ring hugs the item's own shape (its corner radius plus the gap), a 3 dp band of the screen background colour first, then the 2.5 dp accent ring, and no accent glow; under the item the glow is its own colour (see Grid). It also grows and rises (see Grid) and, once focus has rested on it 120 ms, one soft light band sweeps across its face: white at 28 % peak, 45 % of the cover's span wide with soft edges, travelling 25° below horizontal in 400 ms, clipped to the cover's rounded shape, then gone; it plays once per arrival, is cancelled if focus leaves, and does not play when animations are off (`FocusSweep`) |
| Current, not focused (system while games have focus, section while options have focus, the year of the current game in a peek) | accent text, weight 600, no fill |
| Preview column (games while systems have focus) | `muted` text, no fill; the remembered row one step brighter (`text2`) |

**Right-hand column of a game row**: `year · region` ("1994 · USA"; one of them when only one is known, the region alone without box art) in a system's own list, the year alone at the narrow details stop, in the 400 dp list beside the details on narrow screens and at Largest text; in Recent, Favorites
and Search, which mix systems, the system's short name (`abbr`, at most 5 characters: NES, SNES, GBA, PS1 ...) in the
same slot, `smallFact` style and colour, on the title's baseline. A long title is cut first; the short name is never cut. At the narrow details stop (list peek 56 dp) the mixed lists show the year or region instead.

**Favorite mark**: a star (U+2605, black star) 8 after the title, `smallFact` (13 sp), `meta` colour on every row, focused or
not (accent stays for focus and current). On Y it appears with alpha 0→1 and scale 0.6→1,
180 ms ease-out, `graphicsLayer` only; no motion when animations are off. A title too long for
the row is cut before the star, never the star.

In the home lists (games and system column) the pill glides from the row it leaves to the row it lands on, like the PS Vita's: the list draws it behind its rows (`rememberGlidingPill`), over `GLIDE_MS` on `Decelerate`, transform and draw only; a move
during a move starts from where the pill is drawn, a held key keeps it with the focus; with animations off it jumps. The game menu, Fix name, Fix box art and Search's results glide the same way (`rememberPanelPill`: their rows differ in height, so the focused row hands the pill its own box). Other screens' pills jump.

The grids' focus travels the same way: in the games grid and the Apps grid the focus ring glides from the cell it leaves to the cell it lands on, in X within a row and in Y between rows, over `GLIDE_MS` on `Decelerate` (`FocusTravel` in core-ui; the grid's ring pass reads it while drawing, so nothing recomposes). The cover or tile itself still grows and lifts in place (150 ms). A move during a move starts from where the ring is drawn; a held key (left, right or down) glides linearly at the repeats' speed; Right at a row's end does not wrap, so nothing travels; with animations off the ring jumps. On a scroll the grid's rows glide (see Lists glide) and the ring, travelling in the same time, stays where it is while the covers change under it.

Never two filled rows on one screen. The focused item is always on screen (grids scroll so its row shows).
In a list that scrolls the focus never rides the edge: while the list can still scroll that way, the
row past the focused one is whole too, on every frame (from the Thor's hold).
Lists therefore keep three rows past the focus with motion on (the room a 200 ms glide and a quickening hold
need) and one with it off; see Shapes and motion.

**Lists that scroll show it**: a game-panel list that does not fit
(the game menu at Huge and Largest and from Recent, Info's facts with a long title, Settings' sections column) fades out the row
cut at its bottom edge, and at its top edge once scrolled (`ListEdgeFade`, `PlumDimens.listEdgeFade` 20, from clear to the
background, drawn only while rows continue past that edge, touches pass through). The row is still cut, as above;
the fade makes it plain the list scrolls instead of a half text line ending in a hard edge. Test `GamePanelScrollCueTest`.

## Settings

- Sections column: the same alignment as the systems column. It starts at the screen
  edge and is margin + 200 wide (224, not scaled by text size); every section label starts at the page
  margin (24), the same left line as the top bar text and the first hint key cap, focused or not; the
  focused pill runs from x = 10 to x = 224 (full pill, 2 dp accent ring). The option column starts where it always did (248) and the panel has not moved. Both columns
  use the one `SystemRow` with `bleedStart`. Checked by `SettingsSectionsAlignTest` at four sizes and all
  six text sizes, plus a tap at x = 4 dp. With an option focused the sections slide fully away
  (peek 0); the top bar names the section; L1/R1 change section.
- **Wide**: options `width − 320` (448 on the RP5), side panel 248 beside them.
- **Narrow**: options `width − 48` (528 on the Nova); a row with a panel opens it as a further
  stop that takes the same width.
- Values: three kinds, told apart by the mark. A value that **cycles** with Left / Right is "‹ X ›". A value that **opens a panel or page** (every Opens row, the colour-dot rows, a system's emulator) is "X »": the double angle quote `»` (U+00BB, in every font's Latin-1 set, so no font falls back to another face), after a no-break space so the mark never wraps alone. It is a different shape from the single `›` of the cycling pair and of the Right key cap, and reads at arm's length at 576x432 and 768x432 at every text size because it is the value's own text (same `meta` size and colour). The mark lives in `settings_value_opens` / `settings_value_chevron`; nothing else draws a mark. A switch shows its switch. An **information-only row** (Version, Privacy, Statistics rows, "Box art: not available in this build") has no mark, no A hint, A and a tap do nothing and make no sound, and its value (or, for the box art status, its sentence) is in the secondary text colour (`muted`, `meta` when focused). On a narrow screen the hint bar offers "› More" (the Right key) on such a row and on a switch or action row, never an A "More" beside it.
- **Statistics page, left column** (`SettingsStatisticsLayoutTest`, `SettingsStatisticsFitTest`). Same order as the second-screen card: the total play time is the page's big figure (`SecondTitleCompact`, 26, 600; its label "Total play time" under it in `smallFact`), "This week" a normal row under it, then a small "Most played" heading (`meta`, not a row: the cursor skips it) over the three games, then the system and the figures. A game row: title, then only the system under it, time at the right; the system row: "Most-played system", the system's name under it, time at the right. **One text line**: every row's text, and the heading, starts at row padding 14 + the cover slot (36 + 10) when box art is set up, empty in rows without a cover. Every row keeps `factsRowGap` (4) above and below its text, so a title of three lines has what a one-line row has. A game without art shows a landscape tile (aspect 1.4, as real covers here) in `chip` with a 1 dp `muted` hairline, visible on Graphite and Paper. The second-screen card keeps its portrait stand-in.
  **Side panel** (part 2; `settings_statistics_game_panel*`): on a game row, wide screens show that game in the 248 panel: cover fitted in 248 x (at most `settingsStatisticsCoverHeight` 130, less when the text needs the room; the landscape stand-in without art), then the title (`panelTitle`, 2 lines) and the system (`panelBody`, `muted`) 4 apart, one line per fact (label `muted` left, value `text2` right, `factsRowGap` apart: Play time, Times played, Last played; a figure not stored is left out) and "A to play" (`muted`), all on the row-text line with `settingsPanelListGap` (10) between groups. Any other row keeps the privacy explanation. Narrow: no panel, A plays.
- **Side panel content, one left edge** (the game panel's rule): rows, fields
  and buttons span the panel from its left edge; their text, and the panel's own title, body and
  status lines, start on the row-text line 14 in (`gameRowPadding`; Settings' panel rows use the
  same 14). The title's top is the panel's top in every panel, the account panel's too. Buttons keep
  room for their focus ring above and below; at the sides the ring reaches past the panel's edge.
  Swatch grids of the presets pages are left-aligned from the panel's left edge like the row fills, with 3 dp above and below for the ring; the "+" palette grid is a `sel2` container spanning the panel (see "The "+" page's containers"); the Color code and Use rows span the panel like every other row. Checked by `SettingsPanelTextLineTest`, `SettingsAccountAlignTest`, `SettingsBoxArtAlignTest`,
  `SettingsSystemsAlignTest`, `SettingsBoundsTest`.
- **The "+" page's containers**: the swatch grid, the three slider rows and the Color code row are one kind of box: `sel2` fill, `cornerRow`, `settingsPanelRowPadding` at the sides, one `settingsPanelGap` from the next, all spanning the panel so their left and right edges are the same (grid: `settingsSliderRowPad` above and below, the ring's room). The grid's tiles share the width: eight to a row, `swatchGapWide` (`swatchGapNarrow` on Narrow) apart, square. **Selected is a check mark, not a ring** (the focus ring stays the focus language): the tile whose colour equals the colour in use carries a `settingsSliderThumbRing`-wide check in the text colour that reads on it (`TextMode.bestOn`); a tile can be both focused and selected. Checked by `SettingsColorSliderAlign768Test` / `576Test` (Graphite and Paper, Medium and Largest), `PaletteCurationTest`. The selected check (a tick in the readable text colour, never the ring) marks the colour in effect when the page opened and then the one chosen with A, a tap or an applied code; the ring previews the focused tile without moving the check (`SettingsColorPlusSelectionTest`). The grid the cursor walks and the grid drawn are one list, fixed when the page opened, so a store that answers late cannot make them differ.
- **Colour sliders** ("set by sliders first, the code last"): the "+" page of Background, Text color and Accent has three rows between the swatch grid and the Color code row: Hue (0..360 deg), Saturation and Lightness (0..100 %), set from the colour shown. A row is a hex-row-sized fill (`sel2`, `cornerRow`, focus ring as every row) spanning the panel, one `settingsPanelGap` from the next; inside, on the row-text lines (14 in), the label left and the value right (`meta`), and under them the track: a bar `settingsSliderTrack` (10) high from one text line to the other that draws its own gradient, never the colour's own darkness (a dark colour made the tracks black): Hue is the full spectrum at saturation 100 % and lightness 50 %; Saturation runs from grey to the full colour of the current hue at lightness 50 %; Lightness runs black through the current hue and saturation to white. The thumb shows the current value; which stretches are dimmed or skipped is worked out on the real colour. While the cursor is on the swatch grid the rows show the HSL of the colour being previewed, and a thumb, a dot `settingsSliderThumb` (20) filled with the colour shown and ringed in `strong` (`settingsSliderThumbRing`), its edges on the text lines at both ends. The thumb is the focus dot: the row's ring marks the focused slider. Gamepad: Down from the grid's last row reaches Hue, Saturation, Lightness, then Color code (Up goes back); Left and Right step the focused slider (hue 6 deg, the others 4 %; a held press repeats at the held-direction cadence); Left at a slider's start does nothing (it leaves the page only from the grid's leftmost tile and the Color code row). Touch (the stepped drag felt stuck): a tap on the row sets the value at the finger and focuses the row; a drag moves it only after horizontal touch slop, and a vertical swipe starting on a row scrolls the page. While a finger is down the thumb is drawn under it, continuously, across the whole track (also over dimmed or skipped stretches); the previewed value is the finger's at fine resolution (hue 1 deg, the others 1 %), moved to the nearest passing value over an unreadable stretch; on release or cancel the thumb settles on the value in effect. Nothing is stored while the finger moves (only the screen's colour changes while dragging); the store is written once when it lifts. `ui_move` sounds when the value changes and at most once per D-pad step of travel; the gamepad keeps its steps. Every step previews like a swatch; reaching the Color code row from the sliders keeps the colour tuned (the row shows its code, and a typed code sets the sliders), only B puts back the colour from before the page, and A chooses the shown one and counts as the cursor having moved. **Readable only**: a thumb never stops on a colour that fails `Readability` (Text 7:1 with its derived tokens, Accent 3:1): it steps to the nearest passing value, a tap on an unreadable stretch lands on the nearest passing one, and every track draws its whole length, with the stretches a thumb cannot land on dimmed at `SETTINGS_SLIDER_DIMMED_ALPHA` (no gaps or broken bars; only the drawing changed, the landing is the same). Background has no text or accent test: the tones no text reads on (`Readability.backgroundAllowsText`) are drawn at `SETTINGS_SLIDER_DIMMED_ALPHA` and stepped over. A step sounds `ui_move`; a press at the end of a track is silent. The page scrolls so the focused row stays whole at 576 x 432 and Largest. Checked by `SettingsColorSlidersTest`, `SettingsColorSliderAlignTest`, `SettingsTextColorFit*Test`, shots `settings_color_sliders_*`.
- Switch on: accent track, knob `onAccent`. Off: `sel` track with a 1.5 dp `chip` ring, `muted` knob.

## Setup

Four steps, three dots (sign-in shares the box-art dot): folder, box art, sign in, library
ready. Title column 260 on wide screens, stacked on narrow. No storage permission: the folder
step first shows three plain lines (each after a 6 dp `muted` dot) and
"Choose folder" (opens Android's picker at ROMs), then
the chosen folder as a quiet filled card, "+ Add another folder" and Continue. The last step
shows the four biggest systems (hidden on narrow screens), emulators picked ("16 of 18
systems", the missing ones named), the usage statistics switch (on) and Start playing. Every card
there start on one line (no card has a focus dot now).
A focused button gets the 2.5 dp accent ring and soft glow, like a focused cover.

## Grid

- Grid area = `width − 96` when games are focused.
- Each system has a cover shape, its box fronts' width over height, and a class from it (the shape comes from data, not guesses):
  - **Landscape**, 1.2 and up (SNES 1.37, N64 1.44): gap 22 (was 14: the grid read as cramped).
  - **Square**, 0.85 up to 1.2 (DS 1.12, PlayStation 1.17, Game Boy / Color / Advance and Dreamcast 1.0, PC Engine 0.85): gap 20 (was 12). Not on the board.
  - **Tall**, below 0.85 (NES 0.73, Mega Drive and Master System 0.71, PS2 and Wii 0.715, GameCube 0.72, Saturn 0.65, PSP 0.575): between rows **22**, between columns 20 (was 12; focus ring: the lifted cover's ring reaches 21.2 dp above a 263 dp row and must clear the cover above, `PlumDimens.gridGapTall`).
- **Grid size** (Settings › Appearance › Grid size, one for every system):
  Small, **Medium (the default**) and Large. Columns = `floor((area + gap) / (cell + gap))` with
  the cell of the class and size below and the **14 (landscape) / 12 (square, tall) gaps** (`gridColumnsGap*`: counted as before the
  gaps grew to 22 / 20, so no grid lost a column); the drawn gap is 22 / 20, so a cover is the covers' span
  shared by the columns after their gaps and can be narrower than its cell (Tall Small on the Thor: cell 79, cover 74.1;
  tile titles still read at the 70 dp minimum). The Thor's columns are (landscape 5 / 4 / 3, square 6 / 5 / 4,
  tall 8 / 6 / 5); the RP5 loses a column at each larger size; every reference width stays at
  least 5 dp of area from a change of count (`PlumLayoutTest`), and the Thor keeps its columns at
  its default Large text (peek 52.8, area 736). The board's landscape grid (RP5-3, Nova-4) is
  Large, its portrait grid (RP5-4, Nova-5) Medium; square and tall Medium and landscape Large are
  the grids from before the sizes.

  | Class, size | Cell | RP5 768 | Thor 837 | RP4 Pro 667 | Nova 576 | Cover width (RP5 / Thor / RP4 Pro / Nova) |
  |---|---|---|---|---|---|---|
  | Landscape Small | 118 | 5 | 5 | 4 | 3 | 115.2 / 129.0 / 124.2 / 142.7 |
  | Landscape Medium | 142 | 4 | 4 | 3 | 3 | 149.5 / 166.8 / 173.0 / 142.7 |
  | Landscape Large | 200 | 3 | 3 | 2 | 2 | 206.7 / 229.7 / 270.5 / 225.0 |
  | Square Small | 99 | 6 | 6 | 5 | 4 | 94.0 / 105.5 / 96.6 / 103.0 |
  | Square Medium | 130 | 4 | 5 | 4 | 3 | 151.0 / 130.6 / 125.8 / 144.0 |
  | Square Large | 168 | 3 | 4 | 3 | 2 | 208.0 / 168.2 / 174.3 / 226.0 |
  | Tall Small | 79 | 7 | 8 | 6 | 5 | 77.7 / 74.1 / 77.2 / 78.4 |
  | Tall Medium | 106 | 5 | 6 | 4 | 4 | 116.8 / 105.5 / 125.8 / 103.0 |
  | Tall Large | 128 | 4 | 5 | 4 | 3 | 151.0 / 130.6 / 125.8 / 144.0 |

  The narrowest tile is Small tall on the Thor: 74 dp at Medium text (81 before the gaps went to 22 / 20; the column counts did not change: `PlumLayout.gridColumns` counts with the old 14 / 12 gaps, `PlumDimens.gridColumnsGap*`). It keeps
  about 50 dp for its title between the 10 dp paddings, so Small tiles set the title in 11 / 600 and the year in
  11 (`smallTileTitle`, `smallTileYear`, their own roles, the ramp's step under 13; Medium and Large keep 13 / 600
  and the year in `smallFact` 13); the ring's
  5.5 dp sits inside every gap. Gaps, corners and paddings go by class, the same at every size.
- **Which saved picture a cell draws**: the grid file (560x400 at most) while its height covers
  the picture's drawn height in pixels, else the large one (720 tall at most for a box front);
  either stands in for the other when only one is saved. PSP at Large on the Thor (238 dp, 546
  px) draws the large file; NES at Medium (154 dp, 352 px) the grid one. Decoding stays at the
  drawn size.
- Where a system's shape comes from, first that applies: the median width over height of the
  person's own saved large box fronts once the system has 5 (so a Japanese Super Famicom library
  gets tall covers, a US one wide); else the platform data's `coverRatio`, measured
  from one US box front per system (ScreenScraper box-2D; `tools/platforms/tables.py`
  `COVER_RATIOS`); else the class's default from the data (landscape 1.4, portrait 0.71). The board's
  own two shapes (landscape 1.4, portrait 0.78) stay in the fakes behind the board's shots.
- Row height keeps the system's cover shape: the cover's width over its ratio. Tiles (the titled
  placeholder and the box front's slot) have the system's ratio; a picture keeps its own inside it.
- **Recent and Favorites grids** mix systems: their cells are the
  square class's at the grid size (columns, gap 12, corners and row height of a 1.0 shape; Small
  on the RP5 6 columns of 100.7 x 101), and each game's cover keeps its own system's shape inside
  its cell, fitted and standing on the cell's bottom like a picture. A game without a picture
  stands as the titled tile in its system's shape, fitted the same way; the ring and the focused
  cover's 10 % growth follow that shape, and its corners are its own class's.
- **Apps grid** (its size can't be changed): one fixed size, never the
  Grid size. Icon **56** (`PlumDimens.appsGridIcon`), centred at the tile's top under 6 of padding, 4 to the
  name; the name in `meta` (13 at Medium, following text size and font), centred, two lines at most and
  "…"; 6 under it. Row = 6 + 56 + 4 + two drawn lines + 6, rounded up to a whole dp (104 at Medium Sans,
  118 at Largest Sans, 130 at Largest Serif). Columns = `floor((span + 12) / (80 × text scale + 12))` over
  the covers' span (`width − 104`), counted with 12 like the cover grid's column table, but **drawn with the
  cover grid's gap** (20, square covers), so a tile is a little narrower than 80 (77.7 at 768):
  7 / 5 / 8 at Medium on 768 / 576 / 837, 7 on the Thor at its
  default Large, 5 / 3 / 5 at Largest (fewer, wider tiles so a name keeps its words). The grid sits
  `gridTopGap` (22) under the top bar like the cover grid. **No card** ("don't put apps inside a frame, it's ugly"): an
  icon (its own shape) and its name stand straight on the screen background, with no plate, no rim and no shadow, at rest
  or focused. **Focus language as the cover grid**: the focused tile grows 13 % (`FOCUSED_ICON_SCALE`) about its centre
  and rises 2.5 dp (`focusedLift`) in 150 ms, with the glow in its icon's average colour, one light sweep after 120 ms of rest
  clipped to the tile, and the grid ring (a 3 dp background gap, then 2.5 dp accent) hugging the whole icon-and-name area
  (corner 8); the focused app's name turns `strong` and one step heavier (400 to 500), the other names stay as they are
  (both names are composed, the one that draws is read while drawing: no recomposition on a move); no other tile is dimmed or
  changed; the lifted copy is drawn over a plate of the background colour
  (invisible against it) so the in-place copy never shows through; the tile focused as the grid opens, with animations off or while idle:
  no grow, no sweep, nothing running (`AppsGrid.kt` shares `CoverLift`, `FocusSweep`, `CoverShadow`'s glow with the
  cover grid). An app without an icon: a `sel` round tile with its first letter (`messageTitle`, `text2`).
- Focus ring on a grid cover or tile: a 3 dp gap in the background colour, then 2.5 dp in the accent colour, both following the item's corner (no accent glow, the glow is the cover's own colour; see Focus and current). Cover corners 8 (landscape) or 6 (square and tall); the tile's title padding 12 or 10 the same way.
- **Real covers** (the bar is "simple but beautiful"): each box front keeps its own
  shape, fitted in its cell (never cropped, never stretched) and standing on the cell's bottom, so
  a row reads as a shelf; the ring follows the picture, not the cell. The focused cover is drawn
  10 % larger about its centre, raised 2.5 dp (`PlumDimens.FOCUSED_COVER_SCALE`, `focusedLift`, drawn by the grid over
  its clip, no layout change), growing to it in 150 ms ease-out on arrival and shrinking back in
  the same time when focus leaves (`CoverLift`, draw phase only; at once with animations off; the cover focused as the grid opens, on a system switch or a new grid size is lifted in the first frame, with no grow and no sweep). Growth is about the cover's centre plus the 2.5 dp rise, so on the top row cover and ring (5.5 dp
  out) stay inside the gap to the top bar, which for the cover grid only is **22 dp** (`PlumDimens.gridTopGap`; the bar gap
  everywhere else stays 10): 5.5 + 2.5 + 5 % of the tallest row (263 dp, PSP at Large on 768) is 21.2. The grid gives that
  10 dp up from its height (`PlumLayout.gridStripHeight`, which the rows that fit follow); nothing of it is cut at any edge of the grid,
  while every other cover ends at the grid's edges. Every tile names its game: until a picture is
  decoded (and for good when its file cannot be read) its place is the titled tile in the
  picture's shape, and the picture fades in over it in 110 ms; a picture already in memory is
  drawn at once, with no title under it. A picture has a 1 dp hairline just inside its edge (drawn inside the picture, it moves nothing),
  `coverEdge` (the background 10 % towards the text: a few percent lighter on dark backgrounds,
  darker on light ones; `PlumDimens.coverEdge`), so a box front's black or white border does not
  melt into the background (simple and minimal; no glow, plate, blur or
  gradient on the cover's own edge; the background-coloured plate `HomeGrid` draws under the focused cover is never seen).
  **Loosened later** (a black shadow cannot show on Graphite: background (14,15,17), darkest shadow
  (10,11,12)): the resting edge now has the **rim light** below, and the focused cover has a **glow** (Cover rim and
  glow). Still no plate, blur or gradient on the cover's own edge. Also allowed that day, outside the edge: the **cover
  shadow** and the one-time focus sweep (Focus and current).
- **Cover shadow** (the grid was flat): every cover, picture or titled tile, sits on a soft black shadow
  pushed down, like a box on a table, drawn behind the cover and outside its clip, never part of the
  focused copy. At rest: blur 8 dp, 3 dp down, black at 55 % on a dark background (Graphite) and 22 % on a light one
  (Paper). The focused cover adds a deeper, further one under its larger copy: blur 12 dp, 7 dp down, 55 % / 24 %, faded in with the growth. A dimmed Favorites cover dims its shadow
  too. Tokens `PlumDimens.coverShadow*` and `COVER_SHADOW_*`; code `core-ui/.../components/CoverShadow.kt`. It is cheap by construction: the blur is
  computed once, in software, into a small ALPHA_8 bitmap of a rounded corner (a mask filter does not draw on a hardware canvas
  before Android 9) and drawn as nine stretched slices at any cover size, so nothing is computed per frame and nothing
  animates while the grid is idle. Both shadows stay inside the gaps (22 dp at least) and the grid's 4 dp padding is only a start: a
  shadow at the grid's own edge is cut by its clip, as the covers are. The titled tile (a game without
  art, or waiting): flat `sel`, corners 8 / 6, the title in `gridFocusTitle` (13 / 600, `text2`;
  `smallTileTitle` 11 / 600 at the Small grid size) and the year under it in `smallFact` (13, `muted`;
  `smallTileYear` 11 at Small), 4 apart, at
  the bottom left inside the cover padding (landscape 12, portrait 10), like a box's label; the
  title wraps over the lines the tile holds, and the year gives way when the title needs its line.
  The details column's tile for a game without art has no title and no year (the details title
  and facts are right under it): only the system-styled tile.
- **Cover rim and glow** (no depth on the dark theme). **Rim:** every grid cover, picture or titled tile
  gets a 1 dp white line just inside its rounded edge at 14 % (`PlumDimens.COVER_RIM_ALPHA_DARK`,
  `coverRimColor`), drawn over the cover so the focused copy carries it; nothing moves. On a light background (Paper;
  same test as the shadow) a picture keeps its `coverEdge` hairline and a titled tile has none: the shadow carries
  the depth there. Drawn by `drawCoverRim` (`CoverShadow.kt`), which replaces the old picture hairline drawing.
  **Glow:** only the focused cover has a soft glow in its own colour under its grown copy, after the lifted shadow:
  blur 18 dp, 4 dp down, 50 % of the colour at full growth on Graphite and 20 % on Paper (`coverGlow*`, `COVER_GLOW_*`),
  faded in and out with the grow (`FOCUS_GROW_MS`; at once with animations off) and drawn in the grid's ring pass beside
  the lifted shadow, so nothing runs while idle. The colour is the average of the cover's image, found once when the
  image is first seen by shrinking it to 16 x 16 (`CoverGlowColor`, kept per picture / app by `CoverGlowColors`, no
  library), pixels under 6 % opacity ignored, and lifted until its brightest channel is 60 % so a dark cover still glows
  (`COVER_GLOW_MIN_VALUE`); a titled tile glows in its `sel` colour lifted the same way; an Apps tile (which has no rim, shadow or card) in its icon's
  average. The glow's blurred shape is made wide enough to reach full strength in its middle (`Look.fullStrength`);
  with only a corner's width it came out at 58 %. Measured on Graphite at 768 x 432: 6 dp under the focused cover is 25
  levels brighter than the background in the cover's blue (12 on the other channels). The glow still shows 8 dp past a
  first-row cover and a last-column one (`HomeCoverLiftTest`).
- **Art that is not its slot's shape** (portrait front in a wide slot and the like; `HomeCoverRatioTest`, shots `home_art_ratio_*`): in a grid cell it stands centred on the cell's bottom, ring and corners on the picture. In the wide details column it stands on the column's left edge, the one the title and facts use, on the slot's bottom (the title never moves); its corner is its own class's, 8 from ratio 1.2, else 6 (`pictureCorner`).
- In the Recent and Favorites grids the bar's meta starts with the game's system ("SNES · 1993 · Capcom"); a Favorites cover whose game was taken out with Y and is still shown is dimmed (`UNFAVORITED_COVER_ALPHA`).
- The grid's bar: when the title, its meta and the hints do not all fit, the meta goes first, then
  the hints in the strip's order ("L2 R2 Switch system" first, then "L1 R1 Size", then SELECT, then B Back; the year and publisher are kept over the Switch hint); A stays.

## Type (Plum Sans; other fonts scaled to the same x-height)

**One ramp**: the top screen uses **11 · 13 · 16 · 20 · 26** and nothing else; the
second screen **16 · 20 · 26 · 36** (its Compact top line is the top screen's 13). Sizes one step apart
(12/13, 15/16, 16/17) read as inconsistency, not hierarchy. Off the ramp: glyphs drawn as icons inside a
shape (the key cap's letter, 10; the "Aa" text colour sample, 28), the second screen's details-card
facts (15, below), and the second screen's in-game clock, a display face rather than text: its time is 84
on Roomy and 60 on Compact (`InGameDimens` in core-ui's `theme/SecondScreenInGame.kt`, sized in dp so it
does not follow the text size).
`TypeScaleTest.everySizeIsOnTheRamp` fails on any other size; a new style picks a step.

| Use | Size | Weight |
|---|---|---|
| Game title in list | 16 | 400, focused 500, current 600 (its menu has the focus) |
| Centred message title (empty list, no results) | 20 (17 until the ramp) | 500 |
| Settings › Font panel sample line | 16 (17) | 400 |
| System name; side panel title (Settings, the game menu; `panelTitle`) | 16 (15) | 400, current 600; the panel title 600, at most 2 lines in Settings |
| Settings option | 16 | 400, focused 500 |
| Details title | 20 (wide was 19; narrow details stop 20) | 600 |
| Top bar, meta lines | 13 | 400 |
| Years, counts, small facts; hint label; grid bar's focused title; setup's emulator and statistics rows | 13 (were 12, 12, 14, 14) | 400; grid title 600; setup rows focused 500 |
| Key cap (the hint's letter in its 20-high pill) | 10 (icon, off the ramp: at 11 "A" made the round cap 21 wide) | 600 |
| Small grid: a titled tile's title and year | 11 (12) | 600 / 400 |
| Setup title | 26 (wide), 20 (narrow; was 22) | 600 |
| Setup's plain lines and buttons | 16 (15) | 400; filled buttons 600 |
| Settings: "Aa" text colour sample | 28 (icon, off the ramp) | 600 |
| Settings: "+" of the colour presets | 20 | 400 |
| Settings › About: app name | 20 (22) | 800 on the board; 600 until plum-fonts ships a heavier face |
| Settings panels: sample years, font names | 11 | 400 |
| Settings panels: folder and emulator rows | 13 | 400, focused 500; "+ Add folder" 600 in the accent |
| Side panel body (Settings' explanations, notices, update notes, errors; `panelBody`) | 13 on a 19.5 line (1.5) | 400, `text2`: the details description's style (was 12 on 1.45 in `muted`) |
| Badge ("Current", "Default") | 11 | 700 |

Fonts: Plum Sans (default), Plum Serif and Plum Mono, the last two scaled with
`font-size-adjust 0.53`. (Plum Pixel was dropped.)

Weights are real: Plum Sans and Plum Serif ship 400, 500,
600, 700 and 800, Plum Mono 400, 500, 700 and 800, so a focused row (500) is visibly lighter than the
current item (600). The only weight still borrowed (core-ui `PlumFonts`, pinned by `PlumFontsTest`) is 600
in Mono, drawn with the 700 face. The 500 faces draw Inter 520, Literata 500 and JetBrains Mono 655.

**Text weight** (Settings › Appearance; stored as `appearance.text_weight`, core-ui
`TextWeight`): how many steps every role's weight moves up along 400, 500, 600, 700, 800 (capped at 800): Thin 0, Medium 1, Bold 3
(Bold at 2 read too light).
The table's weights are Thin's. The Settings row is ‹ Thin › ‹ Medium › ‹ Bold ›; **the default is Medium**
(a person who never picked gets Medium, and Thin stays one step away).

| Role | Thin | Medium | Bold |
|---|---|---|---|
| Rows | 400 | 500 | 700 |
| Focused row | 500 | 600 | 800 |
| Current item, titles | 600 | 700 | 800 |
| Badges | 700 asked, drawn with the 600 face | 800 | 800 |

Thin draws exactly what the app drew before the setting existed, so its badge asks 600 (the 600 face).
Mono follows its own faces (600 uses the 700 face).

**Text size** (Settings › Appearance): six steps that scale every size in the table above.

| Name | Factor |
|---|---|
| Small | 0.9 |
| Medium | 1.0 (the table) |
| Large | 1.1 |
| Larger | 1.2 |
| Huge | 1.3 |
| Largest | 1.45 |

Until the person picks a size the app uses **Large on every screen** (small handhelds such as the
Retroid Pocket Nova and the AYN Thor read better at 1.1; it was Medium below 800 dp). Once picked, the pick stays. Layout holds at every size on every reference size: a test renders the screens at all six sizes (and Serif
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
grows upwards. A game with details (a ScreenScraper result: a saved box front or any of year,
publisher, genre, rating, description; signed in) gets the details card instead. The setup question is a `text2` line at the facts size in the same place (it may wrap to two lines at the largest Text sizes). The
calm screen is only the Plumemu mark (the launcher icon's monochrome shapes cropped to the mark),
52 high, `chip`, centred. A change of content replaces the block at once: no fade on the second screen (its
changes run on the top screen's main thread; the card follows only after the cursor rests 250 ms).

**Top screen while the window is up** (layout C): the games list alone, `width − 96` (741 on
the Thor at peek 48, 736 at Large text's wider peek), no details column, no narrow details stop
(Right from the list does nothing); the grid unchanged. When the window goes (switch off, display
gone, window refused or dismissed, Plumemu left) the column is back in the next frame, the list
not moved and the cursor on the same game; from the narrow details stop the cursor goes to the
list. Shots `layout_c_thor_top_*` at 1919x1080 (837x471 dp at 367 dpi), `layout_c_thor_bottom_*`
at 1240x1079.

**Details card** (layout C). Only what the top
screen's details column shows, in its words:

| | Roomy | Compact |
|---|---|---|
| Cover, top-left on the margins: the large box front at its own shape fitted in a square (a portrait box takes its height, a landscape one its width), corners 8 (6 portrait), not dimmed | 240 square | 168 square |
| Beside it, 24 after the cover, from its top: top line (dot, system · region, `meta` 16), 12, title (`text` 600, star kept, cut before it), 12, "1994 · Nintendo", 4, "Racing · 8/10" (genre and ScreenScraper's rating out of 10, one decimal only when not whole, 0 counts as none; no players count; a long genre is cut by measuring, the rating stays) (`text2` 15, one line each, cut at the end; 15 is off the ramp: at 16 the genre beside a 240 cover was cut at Medium, kept until the person picks a smaller cover or a cut genre) | title 26, 3 lines | title 20 (22), 2 lines |
| Under the cover or the text beside it (the lower), 20, full width: the description (`text2` 15 on a line of 22, cut with "…") | at most 3 lines | 2 |
| Right above the played line, 4 over it and 16 at least under what is above: the file Play launches without its extension ("Silent Hill (USA)", the Info panel's File) (`muted`, one line cut with "…"; none and no gap when unknown). It gives way to the description's last line when both do not fit: at Largest only; on Roomy in Plum Serif, on Compact in every font | 15 | 15 |
| The played line on the bottom margin, 16 at least under the description (`text2`, one line cut with "…"; its place is kept while the play is read): the emulator A starts, in the Info panel's words (the part cut when the line is too long), then the play, always whole: "DuckStation · Played 3 days ago · 1 h 35 min", "DuckStation · Not played yet", "None installed · …" | 16 (17) | 16 (15) |

A part with nothing is left out and the rest close up. At large text sizes the description shows
only the lines that fit above the played line (none if not one fits). No cover (none saved, or the
file cannot be read): the same parts as one block on the bottom margin, full width (top line, title,
fact lines, 16, description, 16, file line, 4, played line). No details at all: the picking card. Code:
`app/.../secondscreen/SecondScreenDetailsCard.kt`.

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
fit. Lines that show keep their place on the card. At Medium nothing is skipped at either size, with
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

**Battery part** under the clock card (with Show battery on). Unlike the clock card it follows the person's font and Text size (not Android's font scale):

| | Roomy | Compact |
|---|---|---|
| Frame (the clock card at its top, everything drifting together) | 32 top and bottom, 40 sides | 32, 28 |
| Time left, after the clock card: number 600, words 400 ("45 min left", "About 4 h 15 min left", "1 h 03 to full"), `#767676`; at 15% and below, not charging, `#A0404A` | 28 under; 36 + 20 | 20 under; 26 + 16 |
| "empty at 22:35" / "full at 22:50" on its baseline at the right, under it when both do not fit (`#767676`) | 16 | 16 |
| Chart under it: what height is left, at most | 14 under; 144 | 10 under; 104 |
| Lines: this play (`#767676`, low `#A0404A`), ahead dashed 8 / 7 (`#5C5C5C`), usual pace dotted every 7 (`#555555`), now dot | 3.5, 3, 2.5, r 6 | 3, 2.5, 2, r 5 |
| Hour ticks at the bottom only (no grid), 5 long, 1.5, `#333333`; labels 4 under (first at the left edge, last at the right, the others centred; 12-hour ones closer than 8 keep only the ends) | 16 | 16 |
| Key at the bottom, at least 12 / 8 under the labels: swatch 22 + 10 + "48% an hour", 28 / 20, then swatch + "usually 40%" or "as usual" (charging "+40% an hour", "since 21:20") | 16 | 16 |

Out of height (chart under 64 / 48): "empty at" on a line of its own gives way, then the key, then the
labels. Notes ("Measuring battery use…", "Moves a little every minute to protect the screen") are 16,
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
  black at 45 % with light text, 7 % with dark text: the text colour decides, not the background). Mixing towards the text makes the tray lighter on a dark
  background and darker on a light one; on a coloured background it keeps the hue (Midnight).
- **Keys** (after trying Search on the Thor: "smaller and tidy, not full width, not so
  thick"): 10 columns of **44** with 4 between columns and rows, so a 476 block **centred** on the screen;
  the tray stays edge to edge. Under 476 + 2 × 24 = 524 wide the block shrinks to `width − 48` (none of the
  reference sizes). The width does not grow with the text size. Keys **36 high at every text size** (the
  labels still scale and fit: Largest, 23 sp, in 36), corners 8. The keyboard is 8 + 4 rows + 3 gaps high
  (164); the **tray** a screen shows is that plus the 6 gap, the hint bar (22 × the height factor) and the 12
  bottom margin (`PlumLayout.keyboardTrayHeight`, 204 at Medium, 206 at Large, 214 at Largest; the Thor at Large went from
  226.8 to 206.3, a sixth whole result row showing). There is no half-screen limit on the key height any more: every size on 432 and 471 high is under half the screen; the RP4 Pro (375, half 187.5) is above it, as
  before (keys stay 36 for the touch area).
- **Rows** (spans in columns): `q`–`p` / `a`–`l` half a key in / shift 1.5, `z`–`m`, delete 1.5 / `123` 1.5,
  hide 1, Space 4.5, action 3. Symbols: `1`–`0` / `@ # $ % & - _ + ` / `=\<` 1.5, `., : ; ! ? /`, delete /
  `ABC`, hide, Space, action. Symbols 2 (from `=\<`): `[ ] { } < > = * ^ ~` / `` ` | \ ' " `` in the middle of
  the row / `#+=` (back to Symbols), the same punctuation row, delete / `ABC`, hide, Space, action: every
  printable ASCII character is on some page. A field without shift (Search ignores case) leaves the shift
  key's place empty; the letters keep their places.
- **Look**: letter, digit and symbol keys have no fill, `text`, 16 × text size, 400. Keys with a word or an
  icon (shift, delete, `123`, `ABC`, `=\<`, `#+=`, hide, Space) on a `trayKey` fill, `text2`, 13, 400;
  icons 18 × the height factor. The action key ("Search", "Next", "Sign in", "Done") is the field's primary button:
  accent fill, its word in `onAccent`, 600 (accent text on `trayKey` was under 4.5:1: 3.42 with the default red on
  Graphite); focused, the accent ring goes around it and, since an accent ring on an accent fill does not show, a
  second 2 dp ring in `onAccent` is drawn inside the fill, 3 in from its edge (`KeyboardFocusLookTest`). `onAccent` on the accent reaches 4.5:1 for every preset pair except
  the default red on Midnight (4.45), Plum (4.38) and Forest (4.24), as for the badges. Focused key: `trayKey` fill, `strong` text, 600, and a 2 dp accent ring just outside the key (no gap,
  no glow: the 4 dp gaps have no room for the 5.5 dp ring of covers). Shift on: its arrow in the accent;
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
  The tray slides up and down by `translationY`, 180 ms ease-out (none with motion off).
- **Cost**: nothing runs while idle (no animation, no caret, no frame callbacks); a focus move
  recomposes the two keys involved; a shift change the 26 letters and the shift key (`PlumKeyboardTest`).
- Checked by `KeyboardModelTest` (rules), `KeyboardFit*Test` (576, 768, 837 and 667 wide: the block 476 and
  centred, keys 36 at all six text sizes, the drawn tray within half the screen except on the 375 high screen,
  40 dp touch areas, no label cut at Largest in Sans, Serif and Mono), `PlumColorsTest` (4.5:1 for the key texts on every preset) and the
  `keyboard_*` shots.

## Colour

Three settings go in; every other token comes out. There is no "theme": dark and light are just two
background presets. **Nothing unreadable**: text and accent that cannot be read on the background are refused when
picked, moved in lightness only (same hue) when a background change breaks them, and drawn adjusted
when a stored combination fails. One check, `Readability` in core-ui: text passes when every derived
text token meets the contrast targets in Derived tokens below (text itself at least 7:1 on the background; secondary text reaches 11:1 where the text allows and is the text itself otherwise), the accent at 3:1 on the background. The ready-made sets are held to the other targets, with text itself at 4.5:1 (`ColorSetsTest`).

**Inputs**

- Background: Black `#000000` (default; plain black suits OLED screens), Graphite `#0E0F11`, Midnight `#0B1220`, Plum
  `#1A1020`, Forest `#0E1A14`, Paper `#F4F3EF`, Sand `#EEE6D6`, Mist `#E8ECF0`, then Lemon, Tangerine, Sky,
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
  Light `#EDEDED` or Dark `#17181A` first where it passes, then tinted lights (on a dark background) or
  tinted darks (on a light one), each moved only as far as it needs to pass `Readability`; a background
  no text reaches 7:1 on offers none. Changing the background
  keeps the text if it still passes `Readability`; a Light or Dark preset that fails becomes the one with
  the higher contrast against the new background (light text left on a light
  background would hide the Text option itself), any other colour moves in HSL
  lightness only. Where no text colour can pass (a background of middle lightness, roughly relative
  luminance 0.05 to 0.5) the better of Light and Dark is used. The light/dark **mode** everything else branches on
  (strong text, preset accents, system bar icons) is derived: text lighter than the background is Light.
- Accent: Red (default), Amber, Lilac, Blue, Green, Mono (the text colour), or custom. Two preset
  sets: with light text `#DA4655` `#E8A13A` `#B583E8` `#6FA8FF` `#5FD38D`; with dark text
  `#9E2A3A` `#8F5500` `#7A3FC0` `#1F5FBF` `#1A6B3B`. The default red is a slightly deep crimson, 4.56:1 on Graphite and 4.99:1 on the default Black. A picked accent is kept while it reaches 3:1 on the background; one that no longer does moves in lightness only (same hue). Small
  labels such as "Current" and "Default" are badges (accent fill, `onAccent` text, 11 sp bold),
  so they stay readable on tinted panels with any accent.

**Derived tokens** (`mix(a, b, t)` = straight RGB blend; "reach n:1 on X" = the smallest step from
background towards text that reaches n:1 against X)

Derived-token values below are for the Graphite and Paper presets (the screenshot-test pair);
the default Black derives the same way.

| Token | Rule | On Graphite | On Paper |
|---|---|---|---|
| `sel` focused row fill | mix(bg, text, 0.10 light text / 0.09 dark text) | `#242527` | `#E0DFDC` |
| `sel2` small panels | mix(bg, text, 0.05) | `#191A1C` | `#E9E8E4` |
| `text2` secondary text | reach 11:1 on bg where the text allows, else the text itself | `#C5C5C5` | `#323234` |
| `meta` facts, values on focused row | reach 6:1 on `sel` | `#A6A6A7` | `#4C4D4D` |
| `muted` small text | reach 4.6:1 on `sel` (so it also holds on the focused row) | `#8F9091` | `#5E5E5E` |
| `chip` key-cap borders, switch ring | reach 3:1 on bg | `#636365` | `#8A8A89` |
| `strong` focused text | white on light text, black on dark text | `#FFFFFF` | `#000000` |
| accent | as picked (while it passes); default Red of the matching set | `#DA4655` | `#9E2A3A` |
| `onAccent` text on accent | background or text, whichever contrasts more with accent; if that is under 4.5:1, pure black or white, whichever contrasts more (pure black or white is the fallback because the dark text alone misses 4.5:1 on some default reds, for example on Midnight, Plum and Forest; the accent itself is not moved) | `#0E0F11` | `#F4F3EF` |
| `tray` the keyboard's tray | mix(bg, text, 0.06) | `#1B1C1E` | `#E7E6E2` |
| `trayKey` keyboard keys with a word or icon, the focused key | mix(bg, text, 0.12) | `#292A2B` | `#D9D9D5` |
| `trayEdge` the tray's top hairline | mix(bg, text, 0.14) | `#2D2E30` | `#D5D4D1` |
| `trayShadow` the tray's upward shadow | black at 45 % with light text, 7 % with dark text: it follows the text colour, not the background (like `strong`), so dark text on a dark background of one's own gets a near-invisible shadow (accepted) | 45 % | 7 % |

Because tokens are mixed from the background, greys pick up its tint (on Plum they are plum
greys). Accent marks the current system, the focused row's pill ring, focus rings, switches and primary
buttons.

The presets keep every text at least 4.5:1 against what is behind it (`PlumColorsTest`). A colour the person picks has to pass `Readability`, which asks 7:1 for the text itself.

**Ready-made sets** (Settings › Appearance › Colors). A set is only
a choice of the three inputs above; Colors shows the set whose background, text mode and accent
colours equal the current ones exactly, else Custom (the person's own colours, kept when a set is
picked over them). In `ColorSet` (core-ui `theme/ColorSets.kt`), cycle order:

| Set | Background | Text | Accent | A, B, X, Y glyphs (letter colour in brackets) |
|---|---|---|---|---|
| Plumemu | Black `#000000` | Light | Red `#DA4655` | — |
| Paper | Paper `#F4F3EF` | Dark | Red `#9E2A3A` | — |
| Pocket | `#CDCCC4` | Dark | `#8C1D4E` | `#8C1D4E` (white) on all four |
| Dot matrix | `#B4DB11` | Dark | `#2E5E2E` | — |
| Super | `#D9D8D3` | Dark | `#AA2924` | `#C8302B` (white), `#D9A400` (dark), `#2B54B5` (white), `#2E8B4A` (white) |
| Indigo | `#262158` | Light | `#A79CF5` | — |
| Charcoal | `#2A2B2E` | Light | Green `#5FD38D` | `#3E6FD8` (white), `#2E9A47` (dark), `#D9A400` (dark), `#D9A400` (dark) |
| Ivory | `#F3EAD0` | Dark | `#127087` | `#CF3A3A` (white), `#D9A400` (dark), `#2F6FDB` (white), `#2E9A47` (dark) |
| Banana | `#F2CD3C` | Dark | `#1F45A8` | `#C8302B` (white), `#0E7C7C` (white), `#926B00` (white), `#926B00` (white) |
| Cream | `#E9DFC8` | Dark | `#9A1F2B` | — |

Every set keeps the presets' targets: text 4.5:1 and accent 3:1 on
the background, `text2` 11:1, `meta` 6:1 and `muted` 4.6:1 on `sel`, `chip` 3:1, text on the focused
row, the tray and `trayKey` 4.5:1, and `onAccent` 4.5:1 on the accent (`ColorSetsTest`). **A set also keeps a margin for the focused row's look**: the accent at 3.5:1 on the background (the grid ring) and on the pill (the row's
ring), `onAccent` 4.7:1, and `strong`, `text`, `meta`, `muted` at their targets on the pill; the pill is at least 1.12:1 from the background.
Three accents moved by lightness only (same hue and saturation): Super `#B22B26` → `#AA2924` (ring on pill 3.78 → 4.04, badge text
4.50 → 4.82), Dot matrix `#306130` → `#2E5E2E` (3.82 → 3.98, 4.54 → 4.75), Ivory `#1792B0` → `#127087` (ring on pill 2.56 → 3.88, on the
background 3.03 → 4.74; the bright teal was too faint as a ring on the tinted pill). The other seven sets already passed. **A set's previous accents still count** (`ColorSet.previousAccents`, `ColorSet.settled`, applied in the
app's `toColorsAndLook`): stored settings whose accent is an old value of a set (Super `#B22B26`, Dot matrix `#306130`,
Ivory `#1792B0`) with that set's background and text read as that set and draw its current accent; the stored value is not
rewritten. Any other accent, near it or not, stays Custom. Add the old value to the list whenever a set's accent moves. Five values of four sets did not reach the targets, and only their HSL lightness moved: Pocket background `#C8C6BE` →
`#CDCCC4` (`text2` 10.39 → 11), Dot matrix background `#9BBC0F` → `#B4DB11` (`text2` 8.12 → 11) and
accent `#306230` → `#306130` (badge text 3.29 → 4.54), Super accent `#C8302B` → `#B22B26` (badge
text 3.76 → 4.50), Ivory accent `#1795B3` → `#1792B0` (on the background 2.92 → 3.02; 3.03 on the warmer `#F3EAD0`; the keys are as on the AYN Thor: A red, B yellow, X blue, Y green). A glyph's
letter is white or the dark text colour, whichever contrasts more with its fill (all 3:1 or more,
`KeyCapLookTest`).

**Glyph colours drawn**. While Super, Charcoal, Ivory, Banana or Pocket is the current set (derived:
`PlumTheme.buttons`), every `Hint` key cap that reads A, B, X or Y is filled with that letter's
colour; its 1.5 dp ring takes the fill's colour, so the cap keeps its size and shape (20 high, at
least 20 wide), and the letter is the table's white or `#17181A`, still 10 / 600. The colour is the
printed letter's, not the role's: with "Confirm with B" the confirm hint reads B and is B's colour.
START, SELECT, L1 R1 and every cap in the other sets or in Custom colours keep the `chip` ring and
`text` letter. **No outline around a coloured cap**:
a fill need not stand out from the background, because the letter on it is what is read, as on the
handhelds these sets come from. Measured, letter on fill / fill on background: Super A 5.37 / 3.76,
B 7.83 / 1.59, X 6.91 / 4.84, Y 4.28 / 3.00; Charcoal A 4.70 / 3.01, B 4.93 / 3.93, X and Y 7.83 /
6.24; Ivory A 4.86 / 4.05, B 7.83 / 1.89, X 4.75 / 3.96, Y 4.93 / 3.00; Banana (red, teal and a darker yellow on the yellow `#F2CD3C`) A 5.37 / 3.47, B 5.01 / 3.24, X and Y 4.86 / 3.14; Pocket (all four the accent magenta, close to the grey DMG's A/B) 8.75 / 5.43. Every letter is 4.28:1 or
more on its cap. Shots `home_glyphs_<size>_<set>[_confirm_b]`.

**Colors panel**. A on Colors opens the side panel (wide: 248 beside the options, as
Background's; narrow: the further stop). Title "Colors", one help line, then a tile per set in cycle
order and Custom while the row has it. A tile is a tiny home screen 16:10 in its own colours,
drawn in fractions of its size: background, a `text2` top-bar line, the `sel` focused row with the
accent dot and a `strong` title, a `muted` meta line, and the confirm and back glyphs (the person's
letters) in the set's glyph colours or the usual `chip` ring; palette corners 6 with a 1 dp `chip`
edge. Its name is 4 under it, 11 sp (`colorTileName`, `text2`); the current set's 11 / 600 in the
accent (Focus and current). Tiles span the row-text lines (14 in on each side), 12 apart across and
down; a tile is at least 58 × the text size's height factor wide, so 3 per row in the wide panel at
Small to Large and 2 from Larger, at most 5 in the narrow stop. A name too long takes a second line
between words, never cut. The focused tile has the 2.5 dp accent ring; nothing applies while moving.
Checked by `SettingsColorsPanelFit{768,576,Thor}Test` (every font, six sizes); shots
`settings_colors_panel_<size>_<charcoal|super|custom>[_largest]`.

## Shapes and motion

- Row and button corners 8; setup choices 10; key caps fully round (20 high).
- Switch 34×20, knob 16.
- Strip slide about 180 ms, ease-out; off when animations are off. A move within the column the
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
- **Fade-ins**, 110 ms ease-out, alpha only: another system's list or grid on L2/R2 while the
  games have the focus, a game panel sub-screen (Play with, Info, Choose an emulator, Fix name),
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
  colour, its ring and letter follow (the letter takes the `bg` colour), and the cap (only the cap, not the
  label) scales to 0.92 about its centre, by `graphicsLayer`, so nothing around it moves. In 60 ms
  ease-out, out 100 ms ease-out; a press shorter than 100 ms still shows the whole dip (the way in is
  never cut, the way out waits until 100 ms after the press; with animations off there is no wait: the release is at once, in the same frame). The timings and scales are tokens in `PlumMotion` (`PRESS_IN_MS`, `PRESS_OUT_MS`, `PRESS_MIN_VISIBLE_MS`, `HINT_PRESS_SCALE`, `PRESS_SCALE`, `PRESS_FILL_MIX`). A finger down on a hint does the same until
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
  and its ring dip as one about the cover's centre (`rememberConfirmPressScale`, a background plate hides the
  cell's own copy); a finger on any cover dips it, with its ring when it is focused (one shared finger dip per
  grid, `rememberTouchPressScale`; the Apps grid's tiles likewise). The system column and Settings' section rows
  (`SystemRow`), Fix box art's result rows and setup's info rows use `pressFeel` (`PressFeelFingerTest`,
  `PressFeelSettingsSectionTest`, `PressFeelFixBoxArtTest`). Settings' colour swatches, "+" tile, By system tile,
  Color code and Use this color rows, systems rows and licence rows use `pressFeel` outside their focus
  ring, so the ring dips with them (`SettingsPressFeelTest`). **A field row typed with Plumemu's own keyboard does not answer the confirm button while the tray is up** (A types a key there, so the key dips, not the row): it keeps its fill, a finger still presses it, and once the tray closes it answers again (`pressFeel(answersButton = false)`; `SettingsPressFeelTest`, `SignInPressFeelTest`). Shot `home_list_row_pressed_*`; tests `PressFeelTest`, `PressFeelHomeTest`.
- **Held direction**: first repeat 300 ms after the press, then 80 ms (or, with "Hold to scroll
  faster", 120 ms each 20 % quicker down to 32 ms), each wait rounded to whole display frames and
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
- Settings › Advanced › Sounds is three steps, ‹ Off › / ‹ Quiet › (35 % of the pool's volume) / ‹ Loud ›
  (full; the default, and what an old "on" reads as); changing it to Quiet or Loud plays one confirm at the new
  volume, Off is silent. Settings › Advanced › Vibration (on by default) adds a haptic: a light tick on every
  focus move (at most one per 35 ms, so a held D-pad does not buzz) and a firmer click on a confirm; none for
  back. It is made where the sound is (`UiSounds`) with the best the device has (`Haptics.kt`: primitives, else
  the predefined tick and click, else a short low-amplitude one-shot, else a plain vibrate). The sound pool is made and loaded on a background thread when the app becomes visible
  and released in `onStop`.
- Each screen's model says what a press did where it does it (`Outcome`: moved, activated, went
  back, nothing) and the screen only plays that. A press that does nothing (A while a launch runs,
  SELECT on a list with no grid, X on an empty library, START or Y on an app) is silent.

## Launcher icon

A plum that is also a
round game button. Crimson `#DA4655` circle (the default accent), a darker `#A8323F` seam down its
left side, an off-white `#EDEDED` stem and leaf (the light text colour), on black. Flat, no
gradients or shadows. The whole mark fits a 54 dp circle in the 108 dp adaptive canvas, so no mask
clips the leaf. The monochrome layer (themed icons, Android 13+) has the same shapes with the seam
cut out. Every file is generated by `tools/icon/make_icon.py` (vector drawables for Android 8+,
PNGs for Android 6 to 7.1); change the numbers there and run it again, never the outputs.
