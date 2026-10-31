package com.plumemu.coreui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Text size setting (Settings › Appearance), six steps. Medium is the table in design/rules.md ›
 * Type; the others scale it. Android's own font scale applies on top, since sizes are in sp.
 * Small, Medium and Large keep the factors they always had; the names are what the settings file
 * stores (core-data `TextSizeSetting` has the same names).
 */
enum class TextSize(
    val factor: Float,
) {
    Small(0.9f),
    Medium(1f),
    Large(1.1f),
    Larger(1.2f),
    Huge(1.3f),
    Largest(1.45f),
    ;

    /**
     * How much taller than at Medium a box around text of this size must be: 1 for the sizes
     * below Medium (their text fits the Medium box), the factor above it.
     */
    val heightScale: Float get() = maxOf(1f, factor)

    companion object {
        /** The size used until the person picks one, on every screen (small handhelds read better at Large). */
        val Default: TextSize = Large
    }
}

/**
 * Text weight setting (Settings › Appearance), three settings (Thin, Medium, Bold): how many steps along the
 * faces 400, 500, 600, 700, 800 every role's design weight moves up (Medium 1, Bold 3, capped at 800; Bold at
 * 2 steps read too light). Thin is the table in
 * design/rules.md › Type exactly as it was drawn before the setting existed; the names are what the
 * settings file stores (core-data `TextWeightSetting` has the same names).
 */
enum class TextWeight(
    val steps: Int,
) {
    Thin(0),
    Medium(1),
    Bold(3),
    ;

    /**
     * The weight asked for a role whose design weight is [designWeight]. Thin keeps the table, except that a
     * 700 (the badge) is asked as 600: Sans and Serif have no 700 face of their own and that role draws the
     * 600 face, which Thin must keep drawing. Medium and Bold move along [FACES], then stop at 800.
     */
    fun apply(designWeight: Int): Int {
        if (this == Thin) return if (designWeight == THIN_BADGE_DESIGN_WEIGHT) THIN_BADGE_ASKED_WEIGHT else designWeight
        val at = FACES.indexOfFirst { it >= designWeight }.let { if (it < 0) FACES.lastIndex else it }
        return FACES[minOf(at + steps, FACES.lastIndex)]
    }

    companion object {
        /** What a person who has not picked gets; Thin is the old look, still one step away. */
        val Default: TextWeight = Medium

        private val FACES = listOf(400, 500, 600, 700, 800)
        private const val THIN_BADGE_DESIGN_WEIGHT = 700
        private const val THIN_BADGE_ASKED_WEIGHT = 600
    }
}

/**
 * Every text style of the app, from design/rules.md › Type, at Plum Sans and Medium size.
 * [weight] is the design weight; [PlumFonts] says which real face draws each weight.
 *
 * Sizes come from one ramp: the top screen 11 · 13 · 16 · 20 · 26, the
 * second screen (`Second*`) 16 · 20 · 26 · 36. Only glyphs drawn as icons inside a shape stay off
 * it ([KeyCap], [SettingsInkSample]). `TypeScaleTest` fails on a size outside the ramp.
 */
enum class TypeRole(
    val sizeSp: Float,
    val weight: Int,
) {
    GameTitle(16f, 400),
    GameTitleFocused(16f, 500),

    /** The current game, not focused (its menu has the focus): accent, 600 (design/rules.md › Focus and current). */
    GameTitleCurrent(16f, 600),

    /** A centred message's title (empty list, no results): one ramp step above the rows' 16 (it was 17). */
    MessageTitle(20f, 500),

    /** The Settings › Font panel's sample line: the game rows' 16 (17 on the older ramp). */
    FontSample(16f, 400),
    SystemName(16f, 400),
    SystemNameCurrent(16f, 600),

    /** A focused system row: the focus language's weight 500 (the board's systems screen). */
    SystemNameFocused(16f, 500),
    SettingsOption(16f, 400),
    SettingsOptionFocused(16f, 500),
    DetailsTitleWide(20f, 600),

    /** In the narrow details stop. */
    DetailsTitleNarrow(20f, 600),
    TopBar(13f, 400),
    Meta(13f, 400),

    /** Years, counts, small facts (12–13 on the board; 13 like [Meta]). */
    SmallFact(13f, 400),

    /** The current game's year in the narrow details stop's list peek: accent, 600. */
    SmallFactCurrent(13f, 600),

    /** The focused game's title on the grid's bottom bar (the board's 14 / 600; 13 on the ramp). */
    GridFocusTitle(13f, 600),

    /**
     * A titled tile's title in the Small grid: 11 / 600, the ramp's step under 13 (12 on the older ramp;
     * at 13 the landscape tiles cut titles from Huge, from Large in Serif). The narrowest tiles (tall, about
     * 81 wide) leave 61 for the text.
     */
    SmallTileTitle(11f, 600),

    /** The year under it: 11 like the title, so the year never reads larger than the name above it. */
    SmallTileYear(11f, 400),
    HintLabel(13f, 400),
    /**
     * A hint's key letter in its 20-high pill: 10, an icon off the ramp. At 11 "A" is 8 wide and with the
     * pill's 5 + 1.5 each side the round cap grew to 21 x 20 (type ramp).
     */
    KeyCap(10f, 600),
    SetupTitleWide(26f, 600),
    SetupTitleNarrow(20f, 600),

    /** Setup's plain lines before a folder is chosen (the board's 15 in `text2`; 16 on the ramp). */
    SetupPoint(16f, 400),

    /** Setup's filled buttons: Choose folder, Continue, Start playing. */
    SetupButton(16f, 600),

    /** Setup's outlined buttons ("Not now"). */
    SetupButtonQuiet(16f, 400),

    /** A setup choice or folder card; focused 500, like a settings option. */
    SetupChoice(16f, 400),
    SetupChoiceFocused(16f, 500),

    /** The first line of setup's emulator and usage statistics rows. */
    SetupRowTitle(13f, 400),

    /** That title on a focused row: the focus language's weight 500. */
    SetupRowTitleFocused(13f, 500),

    /** "1,284 games" on setup's last steps. */
    SetupCount(16f, 600),

    /** "Current", "Default": accent fill, onAccent text, 11 bold (design/rules.md › Colour). */
    Badge(11f, 700),

    /** Settings › Text color: the "Aa" of each card (the board's 28 / 600). */
    SettingsInkSample(28f, 600),

    /** Settings: the "+" of the colour presets (the board's 20 / 400). */
    SettingsSwatchGlyph(20f, 400),

    /**
     * Settings › About: the app's name. The board draws 22 / 800; 20 on the ramp. The Plum fonts
     * ship no face heavier than 600 (700 in Mono), so it is 600 until plum-fonts has one.
     */
    SettingsAboutTitle(20f, 600),

    /** Settings panels: the years of the sample rows and the font names (the board's 11). */
    SettingsPanelMeta(11f, 400),

    /** Settings panels: a focused folder or emulator row (the board's 13, at the focus weight). */
    SettingsPanelItemFocused(13f, 500),

    /** Settings › ROM folders: "+ Add folder" (the board's 13 / 600, in the accent). */
    SettingsPanelAction(13f, 600),

    /** Settings › Colors: a set's name under its preview tile (the panel meta's 11). */
    ColorTileName(11f, 400),

    /** The current set's name there: accent, 600, the "current, not focused" look (design/rules.md › Focus and current). */
    ColorTileNameCurrent(11f, 600),

    /** Second screen, picking (design/rules.md › Second screen): the top line, title, star and facts line, Roomy (the board's 15, 36, 24, 17). */
    SecondTopRoomy(16f, 400),
    SecondTitleRoomy(36f, 600),

    SecondStarRoomy(20f, 400),
    SecondFactsRoomy(16f, 400),

    /** The same, Compact (its top line is [Meta], 13; the board's title 27, star 20, facts 15). */
    SecondTitleCompact(26f, 600),
    SecondStarCompact(20f, 400),
    SecondFactsCompact(16f, 400),

    /**
     * Second screen, the details card (a game with a ScreenScraper result, layout C): the title,
     * Roomy and Compact; the "1994 · Nintendo" and "Action · 1 player" lines and the description
     * (its line height is [PlumTypography.secondDescription]'s). The top line is [SecondTopRoomy]
     * at both sizes, the played line the picking card's facts line.
     */
    SecondDetailsTitleRoomy(26f, 600),
    SecondDetailsTitleCompact(20f, 600),
    /**
     * The details card's fact lines, file line and description: 15, off the second screen's ramp (the ramp
     * asks 16). At 16 the Roomy card cut "Action, Adventure · 8.5/10" beside the cover at Medium
     * (194 dp beside a 240 cover); kept unless a smaller cover or a cut genre is chosen.
     */
    SecondDetailsFact(15f, 400),

    /**
     * Second screen in game, the battery part under the clock card: the time left's number ("45", 600) and its words ("min left",
     * 400), Roomy then Compact (one ramp step down), and the part's other lines, both sizes: "empty at
     * 22:35", the hour labels, the key and the notes (the design's 17 and 15, 16 on the ramp).
     */
    SecondBatteryLeftRoomy(36f, 600),
    SecondBatteryUnitRoomy(20f, 400),
    SecondBatteryLeftCompact(26f, 600),
    SecondBatteryUnitCompact(16f, 400),
    SecondBatteryLine(16f, 400),

    /** On-screen keyboard (design/rules.md › Keyboard): a letter, digit or symbol key, 16 / 400; focused 600. */
    KeyboardKey(16f, 400),
    KeyboardKeyFocused(16f, 600),

    /** A key with a word on it (123, ABC, =\<, Space, the action): the board's 13 / 400. */
    KeyboardWord(13f, 400),

    /** The same focused, and the action key (accent) always: 600. */
    KeyboardWordStrong(13f, 600),
    ;

    /** Size in sp after the font's x-height factor and the text size setting. */
    fun scaledSizeSp(
        font: PlumFont,
        textSize: TextSize,
    ): Float = sizeSp * font.sizeFactor * textSize.factor

    /** Line height in sp: the font's normal line at the scaled size. */
    fun lineHeightSp(
        font: PlumFont,
        textSize: TextSize,
    ): Float = scaledSizeSp(font, textSize) * font.lineHeightEm

    /** Design weight plus the font's dark-background adjustment when the text is light, then moved up by [textWeight]. */
    fun weightFor(
        font: PlumFont,
        textMode: TextMode,
        textWeight: TextWeight = TextWeight.Default,
    ): Int = textWeight.apply(if (textMode == TextMode.Light) weight + font.darkBackgroundWeightAdjust else weight)
}
