package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScaleTest {
    @Test
    fun sizesAndWeightsMatchTheDesignTable() {
        // The type ramp (docs/design/rules.md › Type); the old size is in the comment where it changed.
        val expected =
            mapOf(
                TypeRole.GameTitle to (16f to 400),
                TypeRole.GameTitleFocused to (16f to 500),
                TypeRole.GameTitleCurrent to (16f to 600),
                // One step above the rows (17 before the ramp).
                TypeRole.MessageTitle to (20f to 500),
                TypeRole.FontSample to (16f to 400), // 17
                TypeRole.SystemName to (16f to 400), // 15
                TypeRole.SystemNameCurrent to (16f to 600), // 15
                TypeRole.SystemNameFocused to (16f to 500), // 15
                TypeRole.SettingsOption to (16f to 400),
                TypeRole.SettingsOptionFocused to (16f to 500),
                TypeRole.DetailsTitleWide to (20f to 600), // 19
                TypeRole.DetailsTitleNarrow to (20f to 600),
                TypeRole.TopBar to (13f to 400),
                TypeRole.Meta to (13f to 400),
                TypeRole.SmallFact to (13f to 400), // 12
                // The current year in the narrow details stop (Nova-3-details: 12, weight 600).
                TypeRole.SmallFactCurrent to (13f to 600),
                // The games list's right-hand text: the board's 10 and 8 raised, the year to 13 and the tag to the info line's step (Thor build 1299).
                TypeRole.RowYear to (13f to 400),
                TypeRole.RowYearCurrent to (13f to 600),
                TypeRole.Tag to (11.33f to 600), // RowRegion
                // The top bar place and details block (board px / 1.5, then the ramp's nearest step; 11 where the board's is smaller).
                TypeRole.PlaceName to (20f to 600),
                TypeRole.PlaceCount to (11f to 400),
                TypeRole.DetailsLabel to (11f to 600),
                TypeRole.DetailsSubtitle to (11f to 400),
                // The focused title on the grid's bar (RP5-3-grid-snes: 14, weight 600).
                TypeRole.GridFocusTitle to (13f to 600),
                // The grid's info line from the Covers board (24 px and 17 px over 1.5).
                TypeRole.InfoLineTitle to (16f to 600),
                TypeRole.InfoLineMeta to (11.33f to 400),
                // 12 before the ramp; 13 cut the Small grid's landscape titles from Huge (from Large in Serif).
                TypeRole.SmallTileTitle to (11f to 600),
                TypeRole.SmallTileYear to (11f to 400), // smallFact (12) before the ramp
                TypeRole.HintLabel to (13f to 400), // 12
                TypeRole.KeyCap to (10f to 600),
                TypeRole.SetupTitleWide to (26f to 600),
                TypeRole.SetupTitleNarrow to (20f to 600), // 22
                TypeRole.SetupPoint to (16f to 400), // 15
                TypeRole.SetupButton to (16f to 600), // 15
                TypeRole.SetupButtonQuiet to (16f to 400), // 15
                TypeRole.SetupChoice to (16f to 400),
                TypeRole.SetupChoiceFocused to (16f to 500),
                TypeRole.SetupChoiceCurrent to (16f to 600),
                TypeRole.SetupRowTitle to (13f to 400), // 14
                TypeRole.SetupRowTitleFocused to (13f to 500), // 14
                TypeRole.SetupCount to (16f to 600),
                TypeRole.Badge to (11f to 700),
                TypeRole.SystemTag to (13f to 700),
                TypeRole.SettingsInkSample to (28f to 600),
                TypeRole.SettingsSwatchGlyph to (20f to 400),
                // The board's 22 / 800: no such face in plum-fonts yet.
                TypeRole.SettingsAboutTitle to (20f to 600),
                TypeRole.SettingsPanelMeta to (11f to 400),
                TypeRole.SettingsPanelItemFocused to (13f to 500),
                TypeRole.SettingsPanelAction to (13f to 600),
                TypeRole.ColorTileName to (11f to 400),
                TypeRole.ColorTileNameCurrent to (11f to 600),
                // Second screen, picking: Roomy then Compact (docs/design/rules.md › Second screen).
                TypeRole.SecondTopRoomy to (16f to 400), // 15
                TypeRole.SecondTitleRoomy to (36f to 600),
                TypeRole.SecondStarRoomy to (20f to 400), // 24
                TypeRole.SecondFactsRoomy to (16f to 400), // 17
                TypeRole.SecondTitleCompact to (26f to 600), // 27
                TypeRole.SecondStarCompact to (20f to 400),
                TypeRole.SecondFactsCompact to (16f to 400), // 15
                // Second screen, the App and Apps cards' title.
                TypeRole.SecondDetailsTitleRoomy to (26f to 600),
                TypeRole.SecondDetailsTitleCompact to (20f to 600), // 22
                // Second screen in game, the battery part: the design's 17 and 15 are 16 on the ramp.
                TypeRole.SecondBatteryLeftRoomy to (36f to 600),
                TypeRole.SecondBatteryUnitRoomy to (20f to 400),
                TypeRole.SecondBatteryLeftCompact to (26f to 600),
                TypeRole.SecondBatteryUnitCompact to (16f to 400),
                TypeRole.SecondBatteryLine to (16f to 400),
                // The on-screen keyboard (design/rules.md › Keyboard; the board's 16 and 13).
                TypeRole.KeyboardKey to (16f to 400),
                TypeRole.KeyboardKeyFocused to (16f to 600),
                TypeRole.KeyboardWord to (13f to 400),
                TypeRole.KeyboardWordStrong to (13f to 600),
            )
        assertEquals(expected, TypeRole.entries.associateWith { it.sizeSp to it.weight })
    }

    @Test
    fun everySizeIsOnTheRamp() {
        // One ramp instead of 13 sizes a step apart. A new role picks a step; it does not add a size.
        val topRamp = setOf(11f, 11.33f, 13f, 16f, 20f, 26f)
        val secondRamp = setOf(16f, 20f, 26f, 36f)
        // Glyphs drawn as icons inside a shape, not read as text: the only sizes allowed off the ramp.
        // KeyCap: at 11 a letter no longer fits the round 20 cap (21 wide).
        val icons = mapOf(TypeRole.KeyCap to 10f, TypeRole.SettingsInkSample to 28f)
        // Off the ramp with a reason (TypeRole's comment); a new entry here needs a reason.
        val exceptions = emptyMap<TypeRole, Float>()
        val second = TypeRole.entries.filter { it.name.startsWith("Second") } - exceptions.keys
        val top = TypeRole.entries - second.toSet() - icons.keys - exceptions.keys
        // Everything else keeps the ramp's minimum of 11.
        for (role in top) assertTrue("$role ${role.sizeSp} is under the ramp's 11", role.sizeSp >= topRamp.min())

        for (role in second) assertTrue("$role ${role.sizeSp} is not on the second screen's ramp $secondRamp", role.sizeSp in secondRamp)
        for (role in top) assertTrue("$role ${role.sizeSp} is not on the ramp $topRamp", role.sizeSp in topRamp)
        for ((role, size) in icons + exceptions) assertEquals("$role", size, role.sizeSp)

        val topSizes = (top + icons.keys).map { it.sizeSp }.toSet()
        // The six steps and the two icons, nothing else.
        assertEquals("top screen sizes", topRamp + icons.values, topSizes)
        // The second screen's Compact top line is the top screen's Meta (13), the only role it shares; one class shows at a time.
        val roomy = SecondScreenDimens.Roomy.roles().map { it.sizeSp }.toSet()
        val compact = SecondScreenDimens.Compact.roles().map { it.sizeSp }.toSet()
        // At most four.
        assertTrue("Roomy second screen sizes $roomy", roomy.size <= 4)
        assertTrue("Compact second screen sizes $compact", compact.size <= 4)
        // The in-game battery part: its sizes are on the second screen's ramp at each size class.
        for (dimens in InGameDimens.entries) {
            val sizes = listOf(dimens.leftRole, dimens.unitRole, InGameDimens.lineRole).map { it.sizeSp }.toSet()
            assertTrue("$dimens battery part sizes $sizes", sizes.all { it in secondRamp })
        }
    }

    private fun SecondScreenDimens.roles(): List<TypeRole> =
        listOf(topRole, titleRole, starRole, factsRole, detailsTitleRole, SecondScreenDimens.detailsTopRole)

    @Test
    fun theListTagsAreAtTheFloorAndGrowWithTheTextSize() {
        // The region and system code and the year beside it were 8 and 10 dp (the board's) and read too small on the Thor: the 11 dp floor or more.
        val floor = 11f
        for (role in listOf(TypeRole.Tag, TypeRole.RowYear, TypeRole.RowYearCurrent)) {
            assertTrue("$role", role.scaledSizeSp(PlumFont.Jost, TextSize.Small) >= floor)
        }
        // Weight 600 (Thin draws the table's weight; Medium and Bold move it up like every role).
        assertEquals(600, TypeRole.Tag.weightFor(PlumFont.Jost, TextMode.Light, TextWeight.Thin))
        assertEquals(11.33f * 1.5f, TypeRole.Tag.scaledSizeSp(PlumFont.Jost, TextSize.Largest), 1e-3f)
        assertEquals(13f, TypeRole.RowYear.scaledSizeSp(PlumFont.Jost, TextSize.Small), 1e-6f)
    }

    @Test
    fun serifAndMonoAreScaledToTheBoardsXHeight() {
        // x-height after scaling = factor × sxHeight / unitsPerEm = 0.53 for both (font-size-adjust 0.53).
        assertEquals(0.53f, PlumFont.Serif.sizeFactor * 0.512f, 1e-6f)
        assertEquals(0.53f, PlumFont.Mono.sizeFactor * 0.550f, 1e-6f)
        assertEquals(1.0352f, PlumFont.Serif.sizeFactor, 1e-4f)
        assertEquals(0.9636f, PlumFont.Mono.sizeFactor, 1e-4f)
        assertEquals(1f, PlumFont.Sans.sizeFactor)
    }

    @Test
    fun smallIsTheTableAndOtherSizesScaleItUp() {
        assertEquals(16f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Small))
        assertEquals(16f * 1.1f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Medium), 1e-5f)
        assertEquals(16f * 1.2f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Large), 1e-5f)
        assertEquals(16f * 1.0352f * 1.2f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Serif, TextSize.Large), 1e-3f)
    }

    @Test
    fun sixSizesInOrderFromTheTableUpToOnePointFive() {
        assertEquals(listOf("Small", "Medium", "Large", "Larger", "Huge", "Largest"), TextSize.entries.map { it.name })
        assertEquals(listOf(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f), TextSize.entries.map { it.factor })
        assertEquals(TextSize.entries.sortedBy { it.factor }, TextSize.entries)
        assertEquals(16f * 1.5f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Largest), 1e-4f)
    }

    @Test
    fun boxesAroundTextGrowAboveSmallAndNeverShrink() {
        assertEquals(1f, TextSize.Small.heightScale)
        assertEquals(1.1f, TextSize.Medium.heightScale)
        assertEquals(1.5f, TextSize.Largest.heightScale)
    }

    @Test
    fun theDefaultIsLargeOnEveryScreen() {
        assertEquals(TextSize.Large, TextSize.Default)
    }

    @Test
    fun lineHeightIsTheFontsNormalLine() {
        assertEquals(16f * 1.21f, TypeRole.GameTitle.lineHeightSp(PlumFont.Sans, TextSize.Small), 1e-2f)
        assertEquals(16f * (0.53f / 0.512f) * 1.485f, TypeRole.GameTitle.lineHeightSp(PlumFont.Serif, TextSize.Small), 1e-3f)
        assertEquals(16f * (0.53f / 0.55f) * 1.32f, TypeRole.GameTitle.lineHeightSp(PlumFont.Mono, TextSize.Small), 1e-3f)
    }

    @Test
    fun noWeightChangeOnDarkBackgrounds() {
        for (font in PlumFont.entries) {
            assertEquals(500, TypeRole.GameTitleFocused.weightFor(font, TextMode.Light, TextWeight.Thin))
            assertEquals(500, TypeRole.GameTitleFocused.weightFor(font, TextMode.Dark, TextWeight.Thin))
        }
    }

    @Test
    fun thinIsTodaysTableAndMediumAndBoldMoveEveryRoleUp() {
        // Thin 400/500/600 (badge keeps its 600 face), Medium 500/600/700 (badge 800), Bold 700/800/800 (badge 800; Bold moves 3 steps along 400-800, capped).
        for (role in TypeRole.entries) {
            val thinExpected = if (role.weight == 700) 600 else role.weight
            assertEquals("$role Thin", thinExpected, role.weightFor(PlumFont.Sans, TextMode.Light, TextWeight.Thin))
            assertEquals("$role default is Medium", minOf(role.weight + 100, 800), role.weightFor(PlumFont.Sans, TextMode.Dark))
            assertEquals("$role Medium", minOf(role.weight + 100, 800), role.weightFor(PlumFont.Sans, TextMode.Dark, TextWeight.Medium))
            assertEquals("$role Bold", minOf(role.weight + 300, 800), role.weightFor(PlumFont.Sans, TextMode.Dark, TextWeight.Bold))
        }
        assertEquals(800, TypeRole.Badge.weightFor(PlumFont.Sans, TextMode.Light, TextWeight.Medium))
        assertEquals(800, TypeRole.Badge.weightFor(PlumFont.Sans, TextMode.Light, TextWeight.Bold))
        assertEquals(700, TypeRole.GameTitle.weightFor(PlumFont.Mono, TextMode.Light, TextWeight.Bold))
    }

    @Test
    fun typographyCarriesTheTextWeight() {
        val thin = PlumTypography(PlumFont.Sans, TextSize.Small, TextMode.Light, TextWeight.Thin)
        val bold = PlumTypography(PlumFont.Sans, TextSize.Small, TextMode.Light, TextWeight.Bold)
        assertEquals(androidx.compose.ui.text.font.FontWeight(400), thin.gameTitle.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(700), bold.gameTitle.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(800), bold.gameTitleCurrent.fontWeight)
        assertTrue(thin != bold)
    }

    @Test
    fun theHintBarStopsGrowingAtMedium() {
        val sans = PlumFont.Sans
        // Labels and key caps: 13 and 10 sp times the font factor, at 1.1 from Medium up.
        for (size in listOf(TextSize.Medium, TextSize.Large, TextSize.Largest)) {
            assertEquals("hint label at ${size.name}", 13f * sans.sizeFactor * 1.1f, TypeRole.HintLabel.scaledSizeSp(sans, size), 0.001f)
            assertEquals("key cap at ${size.name}", 10f * sans.sizeFactor * 1.1f, TypeRole.KeyCap.scaledSizeSp(sans, size), 0.001f)
        }
        assertEquals(13f * sans.sizeFactor, TypeRole.HintLabel.scaledSizeSp(sans, TextSize.Small), 0.001f)
        // Other text keeps growing, and the bar's height follows its labels.
        assertEquals(13f * sans.sizeFactor * 1.5f, TypeRole.Meta.scaledSizeSp(sans, TextSize.Largest), 0.001f)
        val largest = PlumLayout(768.dp, 432.dp, textScale = TextSize.Largest.heightScale, hintScale = TextSize.Largest.hintHeightScale)
        assertEquals(22.dp * 1.1f, largest.hintBarHeight)
        assertEquals(PlumDimens.keyCapHeight * 1.1f, largest.keyCapHeight)
    }
}
