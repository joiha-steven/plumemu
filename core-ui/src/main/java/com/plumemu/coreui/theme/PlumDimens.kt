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

    /** Between the cover, the title block and the facts in the wide details column. */
    val detailsBlockGapWide = 12.dp

    /** Facts table (Last played, Play time, Emulator): label to value, and between lines. */
    val factsColumnGap = 12.dp
    val factsRowGap = 4.dp
    /** Settings rows, option rows, field rows and the game menu's rows. Game rows use [gameRowHeight]. */
    val rowHeight = 40.dp

    /** A game row's design height before the text size scales it (denser than the 40 it had). */
    val gameRowHeight = 36.dp

    /**
     * What a game row is drawn at least, at any text size: with the [listRowGap] under it that is the
     * 40 dp minimum touch area.
     */
    val gameRowMinHeight = 38.dp

    /** Between game rows (the board's list `gap: 2px`) and part of the row above's tap area: rows sit 40 apart at the minimum. */
    val listRowGap = 2.dp

    /** Inside a game row, left and right. */
    val gameRowPadding = 14.dp

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

    /** Between a title and the line under it (the board's details title and its facts). */
    val titleLineGap = 4.dp

    /** Fade over content that continues past the right edge. */
    val edgeFade = 32.dp

    /** Fade at the bottom (or top) edge of a list that scrolls, over rows that continue past it (design/rules.md > Lists that scroll). */
    val listEdgeFade = 20.dp

    // Bars
    /** Between the clock and the battery on the top bar. */
    val topBarItemGap = 14.dp

    /** Between the charging bolt and the battery percentage it stands before. */
    val topBarChargingGap = 3.dp

    /** Between hints on the bottom bar. */
    val hintGap = 20.dp

    /** Between a key cap and its label. */
    val hintKeyGap = 6.dp

    /** The accent dot after a hint's label (an update found, on START Settings), as the focus dot; [hintKeyGap] before it. */
    val hintDot = 6.dp
    val keyCapMinWidth = 20.dp
    val keyCapPadding = 5.dp
    val keyCapBorder = 1.5.dp

    // Settings and setup
    val settingsSectionsWidth = 200.dp
    val settingsPanelWidth = 248.dp

    /**
     * Settings › Statistics' side panel: the focused game's cover is fitted inside the panel's width by at most this
     * height (less when the text below needs the room). Shorter than the details column's 178 so the title,
     * system, three facts and the play line fit the 432 dp screen's panel at Medium.
     */
    val settingsStatisticsCoverHeight = 130.dp

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

    /** Setup's buttons: 40 high, 20 inside left and right, 10 apart. */
    val setupButtonHeight = 40.dp
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
    val gridGapLandscape = 22.dp
    val gridGapPortrait = 20.dp

    /** The gap between rows of tall covers (PSP: 263 dp rows, the lifted cover's ring reaches 21.2 dp above its row, so it must not meet the cover above; `PlumLayoutTest`). */
    val gridGapTall = 22.dp

    /** The gaps the column table of; `PlumLayout.gridColumns` counts with them so the wider drawn gap keeps every column count. */
    val gridColumnsGapLandscape = 14.dp
    val gridColumnsGapPortrait = 12.dp

    /** The board's padding around the grid (covers span the grid area minus 4 on each side). */
    val gridPadding = 4.dp

    /** Between hints, and between the focused title and the hints, on the grid's bottom bar. */
    val gridHintGap = 16.dp

    // Apps grid (one fixed size; Settings › Grid size and L1 / R1 never change it)
    /**
     * An app's icon in the Apps grid: 56, the size Android's own launchers draw on a phone, small enough
     * that 7 tiles fit across the RP5's 664 dp of grid at Medium text ([appsGridTileMinWidth]).
     */
    val appsGridIcon = 56.dp

    /**
     * The narrowest Apps tile at Medium text: the icon and 12 on each side, wide enough for a 9-letter name
     * ("Calculator") on one line. It grows with the text size (`PlumLayout.textScale`), so a larger text
     * gets fewer, wider tiles: 7 / 5 / 8 columns at 768 / 576 / 837 dp at Medium (7 on the Thor at its
     * default Large), 5 / 3 / 5 at Largest.
     */
    val appsGridTileMinWidth = 80.dp

    /** Inside an Apps tile, above the icon and below the name: the focus ring stands this far clear of both. */
    val appsGridTilePadding = 6.dp

    /** Between an app's icon and its name. */
    val appsGridNameGap = 4.dp

    /** Inside a stand-in cover, around its title: landscape 12, portrait 10, details 14. */
    val coverPaddingLandscape = 12.dp
    val coverPaddingPortrait = 10.dp
    val coverPaddingDetails = 14.dp

    // Shapes
    /** Rows and buttons. */
    val cornerRow = 8.dp
    val cornerSetupChoice = 10.dp
    val cornerCoverLandscape = 8.dp
    val cornerCoverPortrait = 6.dp

    /** Key caps are fully round: corner = height / 2. */
    val keyCapHeight = 20.dp
    val switchWidth = 34.dp
    val switchHeight = 20.dp
    val switchKnob = 16.dp

    /** The `chip` ring of a switch that is off. */
    val switchOffRing = 1.5.dp

    /**
     * A system's text badge at least 72 x 48, growing with its
     * text; `chip` ring, corners [cornerRow], 5 dp accent dot 6 in from the top left corner.
     */
    val systemBadgeMinWidth = 72.dp
    val systemBadgeMinHeight = 48.dp
    val systemBadgeRing = 1.5.dp
    val systemBadgePaddingHorizontal = 14.dp
    val systemBadgeDot = 5.dp
    val systemBadgeDotInset = 6.dp

    /** The band over a games list: the facts line 14 right of the badge, a hairline 8 under it. */
    val systemBandBadgeGap = 14.dp
    val systemBandRuleGap = 8.dp
    val systemBandRuleWidth = 1.dp

    // Focus
    val focusDot = 6.dp

    /** Between the focus dot and the row's text. */
    val focusDotGap = 10.dp
    val focusRingWidth = 2.5.dp

    /** The focused row's pill ( > Focus and current): ring width, drawn inside its edge; corner radius cap (half the row height up to this, so rows up to 40 dp are full pills). */
    val focusPillRing = 2.dp
    val cornerFocusPill = 20.dp

    /** Gap between the focused item and its ring. */
    val focusRingGap = 3.dp

    /**
     * How much larger the focused cover is drawn, about its centre
     * and lifted [focusedLift] up, drawn by the grid over its clip, never by layout. The bound is the top row: the cover
     * grows upward by 5 % of a row and is lifted, which with the ring's 5.5 dp outset has to stay inside the gap to the
     * top bar: [gridTopGap] (`PlumLayoutTest`, `GridFocusBoundsTest`).
     */
    const val FOCUSED_COVER_SCALE = 1.10f

    /** The same for a focused Apps tile (icon and name). */
    const val FOCUSED_ICON_SCALE = 1.13f

    /** How far a focused grid item rises, at full growth. */
    val focusedLift = 2.5.dp

    /**
     * The gap between the top bar and the cover grid (the other screens keep [barGap]): the ring's 5.5 dp
     * outset, the 2.5 dp rise and the upward half of the 10 % growth of the tallest row (263 dp, PSP at Large on 768: 13.2)
     * is 21.2; 22 leaves room (was 20 with the 5 % lift).
     */
    val gridTopGap = 22.dp

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
    val coverGlowBlur = 18.dp
    val coverGlowOffsetY = 4.dp
    const val COVER_GLOW_ALPHA_DARK = 0.5f
    const val COVER_GLOW_ALPHA_LIGHT = 0.2f

    /** A cover's average colour is lifted until its brightest channel (0..1) reaches this, so a dark cover still glows. */
    const val COVER_GLOW_MIN_VALUE = 0.6f

    /**
     * The one light band that sweeps across the focused cover (design/rules.md › Focus and current):
     * white at this peak opacity, as wide as this share of the cover's span along its direction, the
     * direction this many degrees below horizontal.
     */
    const val FOCUS_SWEEP_ALPHA = 0.28f

    /** The light band's colour: white on both themes (a light, not a tint). */
    val focusSweepColor = androidx.compose.ui.graphics.Color.White
    const val FOCUS_SWEEP_WIDTH = 0.45f
    const val FOCUS_SWEEP_ANGLE_DEGREES = 25f

    /**
     * A cover in the Favorites grid whose game was just unfavorited (Y) and is still shown until the person leaves
     * the list: the Favorites list drops the row's star, the grid has none, so the cover is dimmed instead. The app
     * had no dim alpha before this (it dims text through the `muted` colour); the focused cover is dimmed too.
     */
    const val UNFAVORITED_COVER_ALPHA = 0.5f
    val focusGlowBlur = 12.dp
    val focusGlowSpread = 1.dp

    /** How far a focus ring reaches past its item (gap + width): space a grid keeps clear around covers. */
    val focusRingOutset = focusRingGap + focusRingWidth

    // On-screen keyboard (design/rules.md › Keyboard): 10 columns of 44, centred, 4 rows of 36.
    /** A key's height, the same at every text size (labels grow, keys do not); [PlumLayout.keyboardKeyHeight] holds the half-screen cap. */
    val keyboardKeyHeight = 36.dp

    /** A key's width on a screen wide enough; narrower screens shrink the block to `width − 2 × 24`. */
    val keyboardColumnWidth = 44.dp

    /** Between keys, across and down; half of it on each side belongs to a key's touch area. */
    val keyboardKeyGap = 4.dp

    /** The tray above the first row of keys. */
    val keyboardTopPadding = 8.dp

    /** Between the last row of keys and the hint bar the caller draws on the tray. */
    val keyboardHintGap = 6.dp

    /** The accent ring drawn just outside the focused key (no gap, no glow: the 4 dp gaps have no room for more). */
    val keyboardFocusRing = 2.dp

    /** The focused action key's second ring (`onAccent`, as wide as the focus ring): this far inside its accent fill. */
    val keyboardActionRingInset = 3.dp

    /** The tray's top hairline. */
    val keyboardEdge = 1.dp

    /** The grid cover's hairline, drawn just inside its edge (`PlumColors.coverEdge`): no layout change. */
    val coverEdge = 1.dp

    /** The tray's upward shadow, the board's `box-shadow: 0 -6px 12px -4px`: offset, blur, spread (inwards). */
    val keyboardShadowOffset = 6.dp
    val keyboardShadowBlur = 12.dp
    val keyboardShadowSpread = 4.dp

    /** Shift, delete and hide icons at Medium ([PlumLayout.keyboardIconSize] grows them with the text). */
    val keyboardIconSize = 18.dp

    /** Locked shift: the bar under the arrow (thickness; the arrow's stroke is the same). */
    val keyboardIconStroke = 1.8.dp

    /** The static caret of a field typed with the keyboard, at Medium ([PlumLayout.caretWidth] grows it with the text). */
    val caretWidth = 1.5.dp

    const val KEYBOARD_COLUMNS = 10
    const val KEYBOARD_ROWS = 4

    // Colour swatches (background palette)
    val swatchWide = 24.dp
    val swatchGapWide = 6.dp

    /** In the narrow panel stop. */
    val swatchNarrow = 32.dp
    val swatchGapNarrow = 8.dp
}
