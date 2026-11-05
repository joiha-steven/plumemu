package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Compose text styles for every [TypeRole], read in composables via `PlumTheme.type`. */
@Immutable
class PlumTypography(
    val font: PlumFont,
    val textSize: TextSize,
    val textMode: TextMode,
    val textWeight: TextWeight = TextWeight.Default,
    /** Every size times this, for a window whose panel is denser than the main one ([SecondScreenPhysical]); 1 everywhere else. */
    val physicalScale: Float = 1f,
    /** The second screen's type: sizes at [TextSize.secondScreenFactor], not the top screen's [TextSize.factor]. */
    val secondScreen: Boolean = false,
) {
    /** The Text size factor in use: the second screen's own on the second screen. */
    val sizeFactor: Float get() = if (secondScreen) textSize.secondScreenFactor else textSize.factor

    private val styles: Map<TypeRole, TextStyle> = TypeRole.entries.associateWith(::buildStyle)

    operator fun get(role: TypeRole): TextStyle = styles.getValue(role)

    val gameTitle: TextStyle get() = get(TypeRole.GameTitle)
    val gameTitleFocused: TextStyle get() = get(TypeRole.GameTitleFocused)
    val gameTitleCurrent: TextStyle get() = get(TypeRole.GameTitleCurrent)
    val messageTitle: TextStyle get() = get(TypeRole.MessageTitle)
    val fontSample: TextStyle get() = get(TypeRole.FontSample)
    val systemName: TextStyle get() = get(TypeRole.SystemName)
    val systemNameCurrent: TextStyle get() = get(TypeRole.SystemNameCurrent)
    val systemNameFocused: TextStyle get() = get(TypeRole.SystemNameFocused)
    val settingsOption: TextStyle get() = get(TypeRole.SettingsOption)
    val settingsOptionFocused: TextStyle get() = get(TypeRole.SettingsOptionFocused)
    val topBar: TextStyle get() = get(TypeRole.TopBar)
    val meta: TextStyle get() = get(TypeRole.Meta)
    val smallFact: TextStyle get() = get(TypeRole.SmallFact)
    val smallFactCurrent: TextStyle get() = get(TypeRole.SmallFactCurrent)
    val rowYear: TextStyle get() = get(TypeRole.RowYear)
    val rowYearCurrent: TextStyle get() = get(TypeRole.RowYearCurrent)
    val placeName: TextStyle get() = get(TypeRole.PlaceName)
    val placeCount: TextStyle get() = get(TypeRole.PlaceCount)
    val detailsSubtitle: TextStyle get() = get(TypeRole.DetailsSubtitle)

    /** A region or system code ([TypeRole.Tag]): 11.33 / 600, spaced 0.1 em; the caller writes it in capitals. */
    val tag: TextStyle by lazy { get(TypeRole.Tag).copy(letterSpacing = 0.1.em) }

    /** GENRE / RATING / PLAYED: 600, spaced 0.145 em (the board's 1.6 px on 11); the caller writes the words in capitals. */
    val detailsLabel: TextStyle by lazy { get(TypeRole.DetailsLabel).copy(letterSpacing = 0.145.em) }
    val gridFocusTitle: TextStyle get() = get(TypeRole.GridFocusTitle)
    val infoLineTitle: TextStyle get() = get(TypeRole.InfoLineTitle)
    val infoLineMeta: TextStyle get() = get(TypeRole.InfoLineMeta)
    val smallTileTitle: TextStyle get() = get(TypeRole.SmallTileTitle)
    val smallTileYear: TextStyle get() = get(TypeRole.SmallTileYear)

    /**
     * A titled grid tile's title at [gridSize]: [gridFocusTitle] (13 / 600), or [smallTileTitle]
     * (11 / 600) at Small, whose narrowest tiles (tall, about 81 wide) leave 61 for the text.
     */
    fun coverTitle(gridSize: GridSize): TextStyle = if (gridSize == GridSize.Small) smallTileTitle else gridFocusTitle

    /** The year under that title: [smallFact] (13), or [smallTileYear] (11) at Small, never larger than the title. */
    fun coverSubtitle(gridSize: GridSize): TextStyle = if (gridSize == GridSize.Small) smallTileYear else smallFact

    /** A game's description in the details: [meta] (13) on the board's 1.5 line height. */
    val description: TextStyle by lazy {
        val base = get(TypeRole.Meta)
        base.copy(lineHeight = base.fontSize * DESCRIPTION_LINE_HEIGHT)
    }

    /** A side panel's title (Settings, the game menu): 16 / 600, the current system's role (the board's; 15 before the ramp). */
    val panelTitle: TextStyle get() = systemNameCurrent

    /**
     * A side panel's text (Settings' explanations, notices, update notes): the details' [description],
     * 13 on a 1.5 line, drawn in `text2` like it (12 `muted` read too small).
     */
    val panelBody: TextStyle get() = description
    val hintLabel: TextStyle get() = get(TypeRole.HintLabel)
    val keyCap: TextStyle get() = get(TypeRole.KeyCap)
    val badge: TextStyle get() = get(TypeRole.Badge)
    val systemTag: TextStyle get() = get(TypeRole.SystemTag)
    val settingsInkSample: TextStyle get() = get(TypeRole.SettingsInkSample)
    val settingsSwatchGlyph: TextStyle get() = get(TypeRole.SettingsSwatchGlyph)
    val settingsAboutTitle: TextStyle get() = get(TypeRole.SettingsAboutTitle)
    val settingsPanelMeta: TextStyle get() = get(TypeRole.SettingsPanelMeta)
    val settingsPanelItemFocused: TextStyle get() = get(TypeRole.SettingsPanelItemFocused)
    val settingsPanelAction: TextStyle get() = get(TypeRole.SettingsPanelAction)
    val colorTileName: TextStyle get() = get(TypeRole.ColorTileName)
    val colorTileNameCurrent: TextStyle get() = get(TypeRole.ColorTileNameCurrent)
    val setupPoint: TextStyle get() = get(TypeRole.SetupPoint)
    val setupButton: TextStyle get() = get(TypeRole.SetupButton)
    val setupButtonQuiet: TextStyle get() = get(TypeRole.SetupButtonQuiet)
    val setupChoice: TextStyle get() = get(TypeRole.SetupChoice)
    val setupChoiceFocused: TextStyle get() = get(TypeRole.SetupChoiceFocused)
    val setupChoiceCurrent: TextStyle get() = get(TypeRole.SetupChoiceCurrent)
    val setupRowTitle: TextStyle get() = get(TypeRole.SetupRowTitle)
    val setupRowTitleFocused: TextStyle get() = get(TypeRole.SetupRowTitleFocused)
    val setupCount: TextStyle get() = get(TypeRole.SetupCount)

    /** Setup's small lines (13, `muted` in the screen): the board's 1.45 line height. */
    val setupNote: TextStyle by lazy {
        val base = get(TypeRole.SmallFact)
        base.copy(lineHeight = base.fontSize * SETUP_NOTE_LINE_HEIGHT)
    }

    /** 20 on wide screens and in the narrow details stop (19 and 20 before the ramp). */
    fun detailsTitle(widthClass: WidthClass): TextStyle =
        get(if (widthClass == WidthClass.Wide) TypeRole.DetailsTitleWide else TypeRole.DetailsTitleNarrow)

    /** 26 on wide screens, 20 on narrow ones. Also the app's title on the home placeholder. */
    fun setupTitle(widthClass: WidthClass): TextStyle =
        get(if (widthClass == WidthClass.Wide) TypeRole.SetupTitleWide else TypeRole.SetupTitleNarrow)

    private fun buildStyle(role: TypeRole): TextStyle =
        TextStyle(
            fontFamily = PlumFonts.familyOf(font),
            fontSize = (role.scaledSizeSp(font, textSize, secondScreen) * physicalScale).sp,
            fontWeight = FontWeight(role.weightFor(font, textMode, textWeight)),
            lineHeight = (role.lineHeightSp(font, textSize, secondScreen) * physicalScale).sp,
            // Centre the glyphs in the line and keep the full line box, like CSS line-height.
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )

    private companion object {
        const val DESCRIPTION_LINE_HEIGHT = 1.5f
        const val SETUP_NOTE_LINE_HEIGHT = 1.45f
    }

    override fun equals(other: Any?): Boolean =
        other is PlumTypography && other.font == font && other.textSize == textSize && other.textMode == textMode &&
            other.textWeight == textWeight && other.physicalScale == physicalScale && other.secondScreen == secondScreen

    override fun hashCode(): Int = ((((font.hashCode() * 31 + textSize.hashCode()) * 31 + textMode.hashCode()) * 31 + textWeight.hashCode()) * 31 + physicalScale.hashCode()) * 31 + secondScreen.hashCode()
}
