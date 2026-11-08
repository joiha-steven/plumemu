package com.plumemu.coreui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Text size setting (Settings › Appearance), six steps. Small is the table in design/rules.md › Type;
 * the others scale it. Android's own font scale applies on top, since sizes are in sp.
 * The names are what the settings file stores (core-data `TextSizeSetting` has the same names).
 */
enum class TextSize(
    val factor: Float,
    /** The factor on the second screen, whose text is physical-size: one step under the top screen's (see [PlumTypography.secondScreen]). */
    val secondScreenFactor: Float,
) {
    Small(1f, 0.9f),
    Medium(1.1f, 1f),
    Large(1.2f, 1.1f),
    Larger(1.3f, 1.2f),
    Huge(1.4f, 1.3f),
    Largest(1.5f, 1.45f),
    ;

    /**
     * How much taller than at Small a box around text of this size must be: the factor itself, never
     * below 1 (Small draws the table's boxes).
     */
    val heightScale: Float get() = maxOf(1f, factor)

    /** The factor the hint bar's labels and key caps use: [factor] up to [HINT_FACTOR_CAP], so the bar keeps its keys at every size. */
    val hintFactor: Float get() = minOf(factor, HINT_FACTOR_CAP)

    /** [heightScale] for the hint bar and its key caps. */
    val hintHeightScale: Float get() = maxOf(1f, hintFactor)

    companion object {
        /** The size used until the person picks one, on every screen (small handhelds read better at Large). */
        val Default: TextSize = Large

        /** The hint bar's own scale stops here (Medium): five hints with their labels must fit a 576 dp bar at the default size. */
        const val HINT_FACTOR_CAP = 1.1f
    }
}

/**
 * Every text style of the app, from design/rules.md › Type, at Plum Sans and Medium size.
 * [weight] is the weight drawn (one scale for every screen: 500 body, 600 focused, 700 titles, 800 badges); [PlumFonts] says which real
 * face draws it. The 400 / 500 / 600 in the comments below are the board's drawn weights, one face step lighter.
 *

 * Sizes come from one ramp: the top screen 11 · 13 · 16 · 20 · 26, the
 * second screen (`Second*`) 16 · 20 · 26 · 36. Only glyphs drawn as icons inside a shape ([KeyCap], [SettingsInkSample]) stay off it.
 * `TypeScaleTest` fails on a size outside the ramp.
 */
enum class TypeRole(
    val sizeSp: Float,
    val weight: Int,
    /**
     * A number or a code, drawn in Plum Mono ([PlumFonts.Mono]) at [sizeSp] x [MONO_FACE_FACTOR], on the Jost line of the same size so a
     * row's baselines do not move; weight 500, never above (Mono has no 600 face and its 700 is bold); no letter spacing.
     */
    val mono: Boolean = false,
    /**
     * A phrase that mixes figures and words ("248 games", "4 h 12 min"): Jost with tabular figures (`tnum`: every digit one width, so a
     * changing count does not jiggle the line), never Mono, so one line keeps one face.
     */
    val tabular: Boolean = false,
) {
    GameTitle(16f, 500),
    GameTitleFocused(16f, 600),

    /** The current game, not focused (its menu has the focus): accent, 600 (design/rules.md › Focus and current). */
    GameTitleCurrent(16f, 700),

    /** A centred message's title (empty list, no results): one ramp step above the rows' 16 (it was 17). */
    MessageTitle(20f, 600),

    /** The Settings › Font panel's sample line (panel removed; kept for the token sheet's golden): the game rows' 16. */
    FontSample(16f, 500),
    SystemName(16f, 500),
    SystemNameCurrent(16f, 700),

    /** A focused system row: the focus language's weight 500 (the board's systems screen). */
    SystemNameFocused(16f, 600),
    SettingsOption(16f, 500),
    SettingsOptionFocused(16f, 600),
    DetailsTitleWide(20f, 700),

    /** In the narrow details stop. */
    DetailsTitleNarrow(20f, 700),
    /** The top bar's clock and battery percentage: numbers, Mono. */
    TopBar(13f, 500, mono = true),
    Meta(13f, 500),

    /** Years, counts, small facts (12–13 on the board; 13 like [Meta]). */
    SmallFact(13f, 500),

    /** The current game's year in the narrow details stop's list peek: accent, 600. */
    SmallFactCurrent(13f, 700),

    /**
     * A game row's year (the board's list columns and the year alone) and the same current in accent 600. The board draws 10 dp; the Thor's
     * list read too small at that and at 11.33, so it takes the ramp's next step, 13 (design/rules.md › Type).
     */
    RowYear(13f, 500, mono = true),
    RowYearCurrent(13f, 500, mono = true),

    /** The tag: a region or a system's short code beside a game's title. Mono 11, no tracking (a mono face is already even). */
    Tag(11f, 500, mono = true),

    /** A system row's count, or its code in the System codes peek (the systems column): Mono 13 like [Meta], which it replaces there. */
    SystemRowCount(13f, 500, mono = true),

    /** A details fact's value or a count phrase beside words (play time, last played, the battery time, "N games"): Jost 13, tabular figures; the labels above stay Jost ([DetailsLabel]). */
    FactNumber(13f, 500, tabular = true),

    /** The rating's figure ("8.5"), alone in its cell beside the dots: Mono 13. */
    RatingNumber(13f, 500, mono = true),

    /**
     * A Statistics tile's figure, a number alone ("73%") or with words ("3 h 40 min", "38 of 412"): Jost 20, tabular figures. A row
     * of tiles is one group, so one face for all of its figures.
     */
    StatisticPhrase(20f, 700, tabular = true),

    /**
     * The top bar's place name and its count, from the Covers bar (30 px = 20 dp, 600; 17 px = 11.33 dp, the ramp's 11); the clock
     * and status use [InfoLineMeta]. A name whose line does not fit steps down to 16 ([PlaceText]).
     */
    PlaceName(20f, 700),
    /** The top bar's count ("248 games"): Jost 11, tabular figures. */
    PlaceCount(11f, 500, tabular = true),

    /** The top bar's rest when it is words, not a count (Settings' breadcrumb "› Rules"): [PlaceCount]'s size, no digits. */
    PlaceWords(11f, 500),

    /** The details block's labels (Genre, Rating, Played, Last played …): sentence case, 13, drawn `muted` by the caller; no letter spacing. */
    DetailsLabel(13f, 500),

    /** The line under the details title ("Nintendo, 1994"): 15 board px = 10 dp, the ramp's 11. */
    DetailsSubtitle(11f, 500),

    /** A titled grid tile's title (the board's 14 / 600; 13 on the ramp). */
    GridFocusTitle(13f, 700),

    /** The focused game's title on the grid's info line (the Covers board: 24 px = 16 dp, 600) and the part after it (17 px = 11.33 dp; also the top bar clock and status). */
    InfoLineTitle(16f, 700),
    InfoLineMeta(11f, 500),

    /**
     * A titled tile's title in the Small grid: 11 / 600, the ramp's step under 13 (12 on the older ramp;
     * at 13 the landscape tiles cut titles from Huge, from Large in Serif). The narrowest tiles (tall, about
     * 81 wide) leave 61 for the text.
     */
    SmallTileTitle(11f, 700),

    /** The year under it: 11 like the title, so the year never reads larger than the name above it. */
    SmallTileYear(11f, 500),
    HintLabel(13f, 500),
    /**
     * A hint's key letter in its 20-high pill: 10 in Jost, an icon off the ramp. At 11 "A" is 8 wide and with the
     * pill's 5 + 1.5 each side the round cap grew to 21 x 20 (type ramp). Not Mono: its word caps ("L1 R1") grow and a hint could drop.
     */
    KeyCap(10f, 700),
    SetupTitleWide(26f, 700),
    SetupTitleNarrow(20f, 700),

    /** Setup's plain lines before a folder is chosen (the board's 15 in `text2`; 16 on the ramp). */
    SetupPoint(16f, 500),

    /** Setup's filled buttons: Choose folder, Continue, Start playing. */
    SetupButton(16f, 700),

    /** Setup's outlined buttons ("Not now"). */
    SetupButtonQuiet(16f, 500),

    /** A setup choice or folder card; focused 500, like a settings option. */
    SetupChoice(16f, 500),
    SetupChoiceFocused(16f, 600),
    SetupChoiceCurrent(16f, 700),

    /** The first line of setup's emulator and usage statistics rows. */
    SetupRowTitle(13f, 500),

    /** That title on a focused row: the focus language's weight 500. */
    SetupRowTitleFocused(13f, 600),

    /** "1,284 games" on setup's last steps. */
    SetupCount(16f, 700),

    /** "Current", "Default": accent fill, onAccent text, 11 bold (design/rules.md › Colour). */
    Badge(11f, 800),

    /** A system's tag on its raised chip (the band at the top of a games list): the ramp's 13, heavier than the board's 8.67 for a 34 dp chip. */
    SystemTag(13f, 800),

    /** Settings › Text color: the "Aa" of each card (the board's 28 / 600). */
    SettingsInkSample(28f, 700),

    /** Settings: the "+" of the colour presets (the board's 20 / 400). */
    SettingsSwatchGlyph(20f, 500),

    /**
     * Settings › About: the app's name. The board draws 22 / 800; 20 on the ramp. The Plum fonts
     * ship no face heavier than 600 (700 in Mono), so it is 600 until plum-fonts has one.
     */
    SettingsAboutTitle(20f, 700),

    /** Settings panels: the years of the sample rows and the font names (the board's 11). */
    SettingsPanelMeta(11f, 500),

    /** Settings panels: a focused folder or emulator row (the board's 13, at the focus weight). */
    SettingsPanelItemFocused(13f, 600),

    /** Settings › ROM folders: "+ Add folder" (the board's 13 / 600, in the accent). */
    SettingsPanelAction(13f, 700),

    /** Settings › Colors: a set's name under its preview tile (the panel meta's 11). */
    ColorTileName(11f, 500),

    /** The current set's name there: accent, 600, the "current, not focused" look (design/rules.md › Focus and current). */
    ColorTileNameCurrent(11f, 700),

    /** Second screen, picking (design/rules.md › Second screen): the top line, title, star and facts line, Roomy (the board's 15, 36, 24, 17). */
    SecondTopRoomy(16f, 500),
    SecondTitleRoomy(36f, 700),

    SecondStarRoomy(20f, 500),
    SecondFactsRoomy(16f, 500),

    /** The same, Compact (its top line is [Meta], 13; the board's title 27, star 20, facts 15). */
    SecondTitleCompact(26f, 700),
    SecondStarCompact(20f, 500),
    SecondFactsCompact(16f, 500),

    /**
     * Second screen, the App and Apps cards: the title, Roomy and Compact. The top line is
     * [SecondTopRoomy] at both sizes.
     */
    SecondDetailsTitleRoomy(26f, 700),
    SecondDetailsTitleCompact(20f, 700),

    /**
     * Second screen in game, the battery part under the clock card: the time left's number ("45", 600) and its words ("min left",
     * 400), Roomy then Compact (one ramp step down), and the part's other lines, both sizes: "empty at
     * 22:35", the hour labels, the key and the notes (the design's 17 and 15, 16 on the ramp).
     */
    SecondBatteryLeftRoomy(36f, 700),
    SecondBatteryUnitRoomy(20f, 500),
    SecondBatteryLeftCompact(26f, 700),
    SecondBatteryUnitCompact(16f, 500),
    SecondBatteryLine(16f, 500),

    /** On-screen keyboard (design/rules.md › Keyboard): a letter, digit or symbol key, 16 / 400; focused 600. */
    KeyboardKey(16f, 500),
    KeyboardKeyFocused(16f, 700),

    /** A key with a word on it (123, ABC, =\<, Space, the action): the board's 13 / 400. */
    KeyboardWord(13f, 500),

    /** The same focused, and the action key (accent) always: 600. */
    KeyboardWordStrong(13f, 700),
    ;

    /**
     * Size in sp after the font's x-height factor and the text size setting. A [mono] role ignores the font's factor: its face is
     * Plum Mono whatever the font in use, tuned by [MONO_FACE_FACTOR].
     */
    fun scaledSizeSp(
        font: PlumFont,
        textSize: TextSize,
        secondScreen: Boolean = false,
    ): Float = sizeSp * (if (mono) MONO_FACE_FACTOR else font.sizeFactor) * textFactor(textSize, secondScreen)

    /** Line height in sp: the font's normal line at the role's size in that font, also for a [mono] role (its baseline stays where Jost's is). */
    fun lineHeightSp(
        font: PlumFont,
        textSize: TextSize,
        secondScreen: Boolean = false,
    ): Float = sizeSp * font.sizeFactor * textFactor(textSize, secondScreen) * font.lineHeightEm

    private fun textFactor(
        textSize: TextSize,
        secondScreen: Boolean,
    ): Float = if (secondScreen) textSize.secondScreenFactor else if (this == HintLabel || this == KeyCap) textSize.hintFactor else textSize.factor

    /** The face family that draws this role: Plum Mono for a [mono] role, else the font's own. */
    fun familyFor(font: PlumFont): FontFamily = if (mono) PlumFonts.Mono else PlumFonts.familyOf(font)

    /**
     * [weight] plus the font's dark-background adjustment when the text is light. A [mono] role never goes above 500: Mono has no 600
     * face and its 700 is the bold one, too far above the 500 beside it.
     */
    fun weightFor(
        font: PlumFont,
        textMode: TextMode,
    ): Int {
        val asked = if (textMode == TextMode.Light) weight + font.darkBackgroundWeightAdjust else weight
        return if (mono) minOf(asked, MONO_MAX_WEIGHT) else asked
    }
}

/**
 * A mono role is drawn at this share of its size: Mono's digits stand 0.75 em high, Jost's 0.72, so 0.96 makes them as tall as the
 * Jost digits beside them. A face tuning like [PlumFont.sizeFactor], not a step of the ramp.
 */
const val MONO_FACE_FACTOR = 0.96f

private const val MONO_MAX_WEIGHT = 500

/** The top bar's place name one ramp step down ([TypeRole.PlaceName] when its line does not fit); the bar's height holds this step's line. */
const val PLACE_NAME_STEP_SP = 16f
