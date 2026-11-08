package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * Fixed sizes from design/rules.md, in dp. Sizes that depend on the screen width are in
 * [PlumLayout]. Read in composables via `PlumTheme.dimens`.
 */
object PlumDimens {
    // The strip
    val stripMarginSide = 24.dp
    val stripMarginTop = 14.dp
    val stripMarginBottom = 12.dp
    val topBarHeight = 18.dp
    val hintBarHeight = 22.dp

    val footerGap = 9.dp // footer zone: content bottom to the hint bar
    val footerBelow = 9.dp // footer zone: hint bar to the screen bottom

    /** Between the top bar, the content and the hint bar. */
    val barGap = 10.dp
    val systemsColumnWidth = 200.dp
    val gamesListMinWidth = 400.dp
    val detailsColumnWidth = 248.dp
    val columnGap = 24.dp

    /** System peek beside the games; adjustable in Settings from [peekWidthMin] to [peekWidthMax]. */
    val peekWidth = 48.dp
    val peekWidthMin = 0.dp
    val peekWidthMax = 120.dp

    /** How much of the list shows in the narrow details stop. */
    val detailsStopListPeek = 56.dp

    /** The years in that peek, from the screen edge. */
    val detailsStopYearInset = 12.dp
    val detailsStopCoverWidth = 228.dp
    val detailsStopCoverHeight = 164.dp

    /** Between the cover and the title beside it in the narrow details stop. */
    val detailsStopCoverGap = 20.dp

    /** Between the title, its fact lines and the facts table beside the narrow cover. */
    val detailsStopLineGap = 6.dp

    /** Extra space above the facts table beside the narrow cover (the board's 4 dp spacer). */
    val detailsStopFactsSpacer = 4.dp

    /** Between the cover row and the description in the narrow details stop. */
    val detailsStopBlockGap = 14.dp

    /** The wide details column's cover: the column's 248 wide, 178 high. */
    val detailsCoverHeightWide = 178.dp
    /** The least it keeps when the text needs room: the facts give way first. */
    val detailsCoverMinWide = 140.dp

    /** Between the cover, the title block and the facts in the wide details column (the Depth board's column `gap: 24px`). */
    val detailsBlockGapWide = 16.dp

    /** Facts table (Last played, Play time, Emulator): label to value, and between lines. */
    val factsColumnGap = 12.dp
    val factsRowGap = 4.dp
    /** A row with a note line (a result's year), one slab: [noteInset] above the title and under the note, [noteGap] between them, [noteBelowGap] to the next pair. */
    val noteInset = 8.dp
    val noteGap = 2.dp
    val noteBelowGap = 6.dp

    /** Settings rows, option rows, field rows and the game menu's rows. Game rows use [gameRowHeight]. */
    val rowHeight = 40.dp

    /**
     * A game row's design height before the text size scales it. With [gameRowMinHeight] and [listRowGap] rows sit the board's
     * 40 dp apart (its 60 px) at Small (34 is under the minimum); bigger text grows them.
     */
    val gameRowHeight = 34.dp

    /**
     * What a game row is drawn at least, at any text size: with the [listRowGap] under it that is the
     * 40 dp minimum touch area.
     */
    val gameRowMinHeight = 38.dp

    /** Under this height a poster cover is left out of the text-only details column rather than cutting its text (the largest text sizes). */
    val posterMinHeight = 56.dp

    /** Between game rows (the board's list `gap: 2px`) and part of the row above's tap area: rows sit 40 apart at the minimum. */
    val listRowGap = 2.dp

    /** A wide single-system list, right-aligned columns: the year (the board's 64 px), then the region (the board's 48 px, 6 dp wider for the raised tag). */
    val rowYearColumn = 42.67.dp
    val rowRegionColumn = 38.dp

    /**
     * How far the games list reaches left into the gap beside the systems (and is wider by): the board's list starts 64 dp from the
     * edge and is 408 wide, our strip's list box 72 and 400, ending at the same place.
     */
    val gamesListBleed = 8.dp

    /** The hairline between the systems' numbers and the list: its x from the list box's left (the board's 56 against 72), width (1 px = 0.67 dp), and its inset from the strip's top and bottom. */
    val listDividerOffset = 16.dp
    val listDividerWidth = 0.67.dp
    val listDividerInset = 5.33.dp

    /** The details block under the title (GENRE, RATING, PLAYED): label to value, the divider to the block, the title to its line. */
    val detailsLabelGap = 2.67.dp
    val detailsDividerGap = 9.33.dp

    /** The least the facts block's gaps shrink to when the height left cannot hold all its rows (a large text on a short screen). */
    val detailsDividerGapTight = 3.dp
    val detailsSubtitleGap = 2.67.dp

    /** The rating's five dots (6 px = 4 dp, 3 px apart) and the gap from the number (8 px). */
    val ratingDotSize = 4.dp
    val ratingDotGap = 2.dp
    val ratingNumberGap = 5.33.dp

    /** Between the top bar's place name and its count (the Covers bar's 16 px). */
    val placeCountGap = 10.67.dp

    /** Inside a game row, left and right: the board's text start, 35 px (23.33 dp) in from the slab's left edge. */
    /** A game row's right inset: its year and region end here; only the text start moved to [gameRowPadding] (the slab's bar is inset from the left). */
    val gameRowEndPadding = 14.dp

    /**
     * The game row's right inset at the narrow details stop, where the list is slid out to its last 56 dp and the year is all that shows:
     * 5 keeps a year whole inside the 56 dp that show, up to Huge; at Largest it is 3 (a Jost year at Bold is 52 dp there).
     */
    val gameRowPeekEndPadding = 5.dp
    val gameRowPeekEndPaddingLargest = 3.dp

    val gameRowPadding = 23.33.dp

    /** Right inset of a panel's running text (title, body): it has no slab to clear, so it keeps the 14 dp it had before the text start moved to [gameRowPadding]. */
    val panelTextEnd = 14.dp

    /** System column entries as the board draws them (34 high); drawn at least [systemRowMinHeight]. */
    val systemRowHeight = 34.dp

    /** The least a system row is drawn: the 40 dp touch minimum. Above Medium the scaled height is taller. */
    val systemRowMinHeight = 40.dp

    /**
     * The least room one letter of the peek's letter rail gets at Medium text (a 13 sp capital with a gap
     * above and below); more letters than fit show a window around the focused one.
     */
    val railLetterMinHeight = 14.dp

    /** Inside a system row, left and right. */
    val systemRowPadding = 10.dp

    /** Between Apps and the first system. */
    val systemGroupGap = 4.dp

    /**
     * Between a row's title and its trailing text (year, region, count). Not on the board, whose
     * titles never reach the right column; it keeps a cut title off the region.
     */
    val rowTrailingGap = 12.dp

    /** Between a favorite's title and its star. */
    val favoriteMarkGap = 8.dp

    /** The trophy after a mastered game's title (every achievement unlocked): a square this wide and high, `favoriteMarkGap` after the title or star. */
    val masteredMarkSize = 12.dp

    /** Info's achievement badge (RetroAchievements' own picture, drawn square at this size) and the gap between rows of the list. */
    val achievementBadgeSize = 36.dp
    val achievementRowGap = 8.dp

    /** Between a title and the line under it (the board's details title and its facts). */
    val titleLineGap = 4.dp

    /** Fade over content that continues past the right edge. */
    val edgeFade = 32.dp

    /** The one fade where scrolling content runs under a bar or into a panel's edge (design/rules.md > Lists that scroll). */
    val listFadeBand = 12.dp

    // Bars
    /** Between the clock and the battery on the top bar. */
    val topBarItemGap = 14.dp

    /** Each side of the update pill's text in the top bar. */
    val topBarPillPadding = 8.dp

    /** Between the charging bolt and the battery percentage it stands before. */
    val topBarChargingGap = 3.dp

    /**
     * Between hints on the bottom bar when they are tight: from a label to the next key's collar. The board's wide keys
     * (13 px padding each side) are 4 dp wider than before; 9 keeps A, Y, X, START and SELECT on a 576 bar.
     */
    val hintGap = 9.dp

    /** The Covers board's hint gap (26 px at 1.5 px per dp), used wherever every hint fits with it; the bar shrinks toward [hintGap] only when they do not. */
    val hintGapBoard = (26f / 1.5f).dp

    /** Between a key's collar and its label (the Covers board's 8 px). */
    val hintKeyGap = (16f / 3f).dp

    /** The accent dot after a hint's label (an update found, on START Settings), as the focus dot; [hintKeyGap] before it. */
    val hintDot = 6.dp
    val keyCapMinWidth = 22.67.dp
    val keyCapPadding = 5.dp
    val keyCapBorder = 1.5.dp

    /** A flat face (keys, buttons, knobs): the edge under it, the hairline of light on top, and how far its shadow reaches below. */
    val flatEdge = 2.dp
    val flatRim = 1.dp
    val flatShadow = 3.dp * Depth.SHADOW_REACH

    /** A focused or hovered key sits this much higher: its edge grows by it and the face rises by it. */
    val flatRaise = 1.dp

    /** A pressed switch's knob grows by this share of its width. */
    const val SWITCH_SQUEEZE = 0.25f

    /** The inset shadow of a sunken track or field. */
    val insetShadow = 1.dp

    /** A panel (game panel, dialog card, update card): the hairline edge, how far its soft shadow reaches and sinks, its corners. */
    val panelHairline = 0.5.dp
    val panelShadowReach = 6.dp
    val panelShadowDrop = 2.dp
    val panelCorner = 10.dp

    /**
     * The game panel's card sits on the page's outer margin at the right, from the top bar's gap down to the hint bar's gap (its shadow
     * [panelShadowBottom] below that: the strip ends 10 dp above the hint keys, so the bottom stays tight, at least 8 dp clear).
     * Everything inside it, a row's slab included, is [panelInset] from its edge on both sides (the board's 2.7 dp on the left).
     * Rows in the card are 40 apart, not [listRowGap] more (the board's 40 dp pitch).
     */
    val panelInset = 2.67.dp

    /**
     * The inner padding of a card's title: its text, and the text, value and chevron of every row and fact under it, start and end this far
     * inside the slab (or the inset), so one line is 23.33 dp from the card's edge on each side.
     */
    val panelTitleStart = 20.67.dp
    val panelRowGap = 0.dp
    val panelShadowBottom = 2.dp

    /** The accent dot by a panel's title: the hint dot's size, sitting at the title's end inside the panel's padding. */
    val panelTitleDot = 6.dp

    /** A card title's dot sits this far after its last character (a title may wrap: the dot stays by the first line's end). */
    val panelTitleDotGap = 8.dp

    /** How far a sunken text field's pill reaches past the text's own box, sideways and up and down. */
    val fieldInsetX = 10.dp
    val fieldInsetY = 5.dp

    /** The same for Search's field in the 18 dp top bar, which has less room. */
    val searchFieldInsetX = 6.dp
    val searchFieldInsetY = 0.dp

    // Settings and setup
    val settingsSectionsWidth = 200.dp
    val settingsPanelWidth = 248.dp

    /** In a side panel (Settings, the game menu): between the title block and what follows (the board's 12). */
    val panelBlockGap = 12.dp

    /** "Current" and "Default" badges (accent fill, 11 sp bold): padding, corners, gap after a label. */
    val badgePaddingHorizontal = 6.dp
    val badgePaddingVertical = 1.dp
    val cornerBadge = 6.dp
    val badgeGap = 8.dp
    /** An option's value: the colour dot (16, `chip` ring 1.5) and 8 to its name. */
    val settingsValueDot = 16.dp
    val settingsValueGap = 8.dp

    /** Between the parts of the side panel (title, text, swatches, preview): 12; lists 10. */
    /** Settings › Statistics: the space above a group's small heading ("Most played"). */
    val settingsGroupHeadingGap = 12.dp

    val settingsPanelGap = 12.dp
    val settingsPanelListGap = 10.dp

    /** An update note's bullet: the accent dot's size and the width of the column it sits in (the text hangs after it). */
    val settingsNoteDot = 5.dp
    val settingsNoteIndent = 14.dp

    /** The fade at the bottom of the update notes while they can scroll further. */
    val settingsNoteFade = 24.dp

    /** Background and accent presets in the panel: 40, 10 apart, background corners 10. */
    val settingsPresetSwatch = 40.dp
    val settingsPresetSwatchGap = 10.dp
    val settingsPresetSwatchCorner = 10.dp

    /** The 3 dp above and below a swatch board, so a focus ring is not cut there; at the sides the board starts at the panel's edge and the ring passes it (rules.md, Settings panels). */
    val settingsSwatchInset = 3.dp

    /** A colour of the custom palette: corners 6, 1 dp edge. */
    val settingsPaletteCorner = 6.dp
    val settingsSwatchEdge = 1.dp

    /**
     * Settings › Colors' preview tiles: a tiny home screen 16:10 wide over high, the
     * palette's corners and edge, 12 apart (two focus rings and 1 between them), its name 4 under
     * it. A tile is at least 58 wide times the text size's height factor, so its name fits: 3 per
     * row in the wide panel at Medium and Large, 2 from Larger; at most 5 in the narrow stop.
     */
    val settingsColorTileMinWidth = 58.dp
    val settingsColorTileGap = 12.dp
    val settingsColorTileNameGap = 4.dp
    const val SETTINGS_COLOR_TILE_ASPECT = 1.6f

    /** Setup's look tiles under the title (Narrow): lower than Settings' so two rows and the title fit a 4:3 screen. */
    const val SETUP_LOOK_TILE_ASPECT_NARROW = 1.85f
    const val SETTINGS_COLOR_TILE_MAX_COLUMNS = 5

    /** The hex code row of the custom palette: 36 high. */
    val settingsHexRowHeight = 36.dp

    /**
     * The Hue, Saturation and Lightness rows under the palette: the row fill
     * and ring of the hex row; inside, a label line and the track, [settingsSliderRowPad] above and
     * below. The track is a bar [settingsSliderTrack] high running from one row-text line to the
     * other; the thumb is a dot [settingsSliderThumb] across with a [settingsSliderThumbRing]
     * `strong` ring, its edges on the row-text lines at both ends. The part of a background's
     * track that no text reads on is drawn at [SETTINGS_SLIDER_DIMMED_ALPHA].
     */
    val settingsSliderRowPad = 6.dp
    val settingsSliderTrack = 10.dp
    val settingsSliderThumb = 20.dp
    val settingsSliderThumbRing = 2.dp
    const val SETTINGS_SLIDER_DIMMED_ALPHA = 0.3f

    /**
     * Inside a side panel's rows, left and right: the row-text line, the same as [gameRowPadding] so a
     * panel's title and text start where its rows' text does (it was 12).
     */
    val settingsPanelRowPadding = gameRowPadding

    /** Room above and below the two lines of an option row (Confirm with B / Circle…), so its text keeps clear of the focus ring (audit B5). */
    val settingsTwoLineRowPadding = 6.dp

    /** Settings › ROM folders: a folder card's top and bottom padding (the board's 10 / 12 card). */
    val settingsFolderCardPadding = 10.dp

    /**
     * Settings › Library › Systems: each row's drag handle, a touch column at the row's right end
     * (as wide as a list row is high) holding three bars 12 long, 1.5 thick, 4 apart.
     */
    val settingsHandleWidth = 40.dp
    val settingsHandleBar = 12.dp
    val settingsHandleBarThickness = 1.5.dp
    val settingsHandleBarGap = 4.dp

    /** A system row's top and bottom padding in that panel (name over its game count). */
    val settingsSystemRowPadding = 6.dp

    /** The sample game rows under the background swatches: 32 high, 2 apart, 4 above. */
    val settingsPreviewRowHeight = 32.dp
    val settingsPreviewTopPadding = 4.dp

    /** A text colour card ("Aa"): 12 inside, 6 between its lines, 10 apart. */
    val settingsInkCardPadding = 12.dp
    val settingsInkCardGap = 6.dp
    val setupTitleColumnWidth = 260.dp

    /** Setup (the board's RP5-O1 and Nova-7): title column to content, wide and narrow. */
    val setupMainGapWide = 48.dp
    val setupMainGapNarrow = 14.dp

    /** Above the title, under the top bar's gap. */
    val setupTopPaddingWide = 34.dp
    val setupTopPaddingNarrow = 8.dp

    /** Between the title and its description. */
    val setupTitleGapWide = 12.dp
    val setupTitleGapNarrow = 6.dp

    /** Between the items of the content column. */
    val setupContentGap = 8.dp

    /** "Setup" to the step dots on the top bar, and between the dots. */
    val setupDotsGap = 10.dp
    val setupDotGap = 5.dp
    val setupDot = 6.dp
    val setupDotCurrentWidth = 16.dp

    /** The three plain lines before a folder is chosen: 32 high, dot 12 before the text. */
    val setupPointHeight = 32.dp
    val setupPointGap = 12.dp

    /** Setup's buttons: 38 high (the board's 57 px), 20 inside left and right, 10 apart. */
    val setupButtonHeight = 38.dp
    val setupButtonPadding = 20.dp
    val setupButtonGap = 10.dp

    /** Above "Choose folder", and above Continue or Start playing. */
    val setupChooseButtonSpacer = 12.dp
    val setupButtonSpacer = 4.dp

    /** The line under the content ("You can add or remove folders later"). */
    val setupNoteTopPadding = 4.dp

    /** A choice or folder card: at least 56 high, 8 / 16 inside, 1 dp ring when not filled. */
    val setupChoiceMinHeight = 56.dp
    val setupChoicePaddingVertical = 8.dp
    val setupChoicePaddingHorizontal = 16.dp

    /** The text start of every row that wears the focus slab: after its accent bar and the gap a game row has ([gameRowPadding]). */
    val slabTextStart = gameRowPadding
    val setupChoiceDotGap = 12.dp
    val setupChoiceLineGap = 3.dp
    val setupChoiceRing = 1.dp

    /** The scan indicator (a breathing accent dot) and its gap to the line it stands before. */
    val scanPulseSize = 8.dp
    val scanPulseGap = 8.dp

    /** The scan's progress bar. */
    val setupProgressHeight = 6.dp

    /** The four biggest systems on the last step: two columns 20 apart, rows 24 high, 2 apart. */
    val setupFoundColumnGap = 20.dp
    val setupFoundRowHeight = 24.dp
    val setupFoundRowGap = 2.dp
    val setupFoundTopPadding = 6.dp

    /** The emulators and usage statistics rows: 8 / 14 inside, 16 to the switch, 2 between lines. */
    val setupRowPaddingVertical = 8.dp
    val setupRowPaddingHorizontal = 14.dp
    val setupRowGap = 16.dp
    val setupRowLineGap = 2.dp
    val setupRowTopMargin = 4.dp

    /** The home screen question: panel width and the space inside it. */
    val homeRolePromptWidth = 420.dp
    val homeRolePromptPadding = 24.dp

    // Grid
    /** Between collars of a file game's grid, the same both ways: wider than the boards' so the frames read apart. */
    val gridGapAcross = 18.dp
    val gridGapDown = 18.dp

    /** Apps' grid (its focused tile still grows and rises) keeps its wider gaps. */
    val gridGapLandscape = 22.dp
    val gridGapPortrait = 20.dp

    /** The gaps the column table of; `PlumLayout.gridColumns` counts with them so the wider drawn gap keeps every column count. */
    val gridColumnsGapLandscape = 14.dp
    val gridColumnsGapPortrait = 12.dp

    /** Between hints, and between the focused title and the hints, on the grid's bottom bar: 16 to a key cap, less the collar's 7 dp of side room. */
    val gridHintGap = 9.dp
    val hintTitleMin = 96.dp // the least room the grid bar's focused title keeps (about 12 characters at 13): a long title is cut to it before a key leaves

    /** Between the focused title and the text after it on the grid's info line (the Covers board's 14 px flex gap; no separator dot). */
    val infoLineGap = 9.33.dp

    /** Inside a stand-in cover, around its title: landscape 12, portrait 10, details 14. */
    val coverPaddingLandscape = 12.dp
    val coverPaddingPortrait = 10.dp
    val coverPaddingDetails = 14.dp

    // Shapes
    /** Rows and buttons. */
    val cornerRow = 8.dp
    val cornerSetupChoice = 10.dp
    val cornerCoverLandscape = CollarDimens.coverBoxCorner
    val cornerCoverPortrait = CollarDimens.coverBoxCorner

    /** Key caps are fully round: corner = height / 2. */
    val keyCapHeight = 20.67.dp
    val switchWidth = 34.dp
    val switchHeight = 20.dp
    val switchKnob = 16.dp

    /** The `chip` ring of a switch that is off. */
    val switchOffRing = 1.5.dp

    /**
     * A system's tag, the RAISED chip: [systemBadgeHeight] tall (times the text size's height scale), 12 padding each side, corners 8,
     * its name tracked [SYSTEM_BADGE_TRACKING] em; wider with the text.
     */
    val systemBadgeHeight = 34.dp
    val systemBadgeCorner = 8.dp
    val systemBadgePaddingHorizontal = 12.dp
    const val SYSTEM_BADGE_TRACKING = 0.09f

    /** The band over a games list: the facts line 14 right of the badge, a hairline 8 under it. */
    val systemBandBadgeGap = 14.dp
    val systemBandRuleGap = 8.dp
    val systemBandRuleWidth = 1.dp

    // Focus
    val focusDot = 6.dp

    /** Between the focus dot and the row's text. */
    val focusDotGap = 10.dp
    val focusRingWidth = 2.5.dp
    /** The carried row's ring, drawn inside its edge (a focused row has none). */
    val focusPillRing = 2.dp

    /** The focused row's slab corner (the board's 20 px / 1.5): half the row's height up to this. */
    val cornerFocusPill = 13.33.dp

    /** Gap between the focused item and its ring. */
    val focusRingGap = 3.dp

    /**
     * The gap between the top bar and the cover grid (the other screens keep [barGap]): the focused tallest row's upward half of
     * the 3.5 % growth (263 dp, PSP at Large on 768: 4.6) plus [FocusLift.reach] (6.5) is 11.1, so the lift, rim and glow clear the top bar.
     */
    val gridTopGap = 14.dp

    /** What the cover grid leaves above the [barGap] over the hint bar: the lifted ring of a bottom row stays clear of the hint keys. Taken from the grid's height. */
    val gridBottomRoom = 8.dp

    /** The Apps grid's gap under the top bar: its tiles grow 13 % and rise, so the ring needs more room than a cover's; it keeps no extra room at the bottom. */
    val appsGridTopGap = 22.dp

    /**
     * The soft shadow under every grid cover:
     * blur radius and downward offset at rest, and the deeper, further one of the focused cover.
     * Both fit in the gap between rows (20 dp at least).
     */
    val coverShadowBlur = 8.dp
    val coverShadowOffsetY = 3.dp
    val coverShadowLiftedBlur = 12.dp
    val coverShadowLiftedOffsetY = 7.dp

    /** Opacity of the shadow's black on a dark and on a light background (lighter and softer on light ones), at rest and lifted. */
    const val COVER_SHADOW_ALPHA_DARK = 0.55f
    const val COVER_SHADOW_ALPHA_LIGHT = 0.22f
    const val COVER_SHADOW_LIFTED_ALPHA_DARK = 0.55f
    const val COVER_SHADOW_LIFTED_ALPHA_LIGHT = 0.24f

    /** A background brighter than this (relative luminance) counts as light for the cover shadow. */
    const val COVER_SHADOW_LIGHT_BACKGROUND = 0.5f

    /**
     * The rim light (: a black shadow cannot show on
     * Graphite): a [coverEdge]-wide white line just inside every cover's edge, at this opacity on a dark background. On a
     * light background (the shadow carries the depth there) a picture keeps its `coverEdge` hairline and a titled tile has no rim.
     */
    val coverRimColor = androidx.compose.ui.graphics.Color.White
    const val COVER_RIM_ALPHA_DARK = 0.14f

    /**
     * The focused cover's glow (same rule), drawn under it in the cover's own colour after the lifted shadow: blur, offset
     * down and opacity at full growth on a dark and on a light background.
     */
    val coverGlowBlur = 18.dp * Depth.GLOW_REACH
    val coverGlowOffsetY = 4.dp * Depth.SHADOW_REACH
    const val COVER_GLOW_ALPHA_DARK = 0.5f * Depth.GLOW
    const val COVER_GLOW_ALPHA_LIGHT = 0.2f * Depth.GLOW

    /** A cover's average colour is lifted until its brightest channel (0..1) reaches this, so a dark cover still glows. */
    const val COVER_GLOW_MIN_VALUE = 0.6f

    /**
     * A cover in the Favorites grid whose game was just unfavorited (Y) and is still shown until the person leaves
     * the list: the Favorites list drops the row's star, the grid has none, so the cover is dimmed instead. The app
     * had no dim alpha before this (it dims text through the `muted` colour); the focused cover is dimmed too.
     */
    const val UNFAVORITED_COVER_ALPHA = 0.5f

    /** The badge of an achievement not yet unlocked (its `_lock` picture, dimmed further so the earned ones read first). */
    const val LOCKED_BADGE_ALPHA = 0.6f

    /** How far the focus halo reaches past the ring (rules.md > Focus and current); drawn as [FOCUS_HALO_STEPS] bands, never blurred. Strength and colour: `FocusHalo`. */
    val focusHaloReach = 10.dp * Depth.GLOW_REACH
    const val FOCUS_HALO_STEPS = 7
    /** Focus ring reach past its item (gap + width): space a grid keeps clear around covers. */
    val focusRingOutset = focusRingGap + focusRingWidth
    /** A band of the screen colour round a ring, for the ring's contrast (Settings' colour grid). */
    val focusRingKeyline = 2.dp

    /** The grid cover's hairline, drawn just inside its edge (`PlumColors.coverEdge`): no layout change. */
    val coverEdge = 1.dp
    /** The static caret of a field typed with the keyboard, at Medium ([PlumLayout.caretWidth] grows it with the text). */
    val caretWidth = 1.5.dp
}
