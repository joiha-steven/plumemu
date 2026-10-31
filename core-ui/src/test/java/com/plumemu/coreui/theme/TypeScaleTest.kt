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
                // The focused title on the grid's bar (RP5-3-grid-snes: 14, weight 600).
                TypeRole.GridFocusTitle to (13f to 600),
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
                TypeRole.SetupRowTitle to (13f to 400), // 14
                TypeRole.SetupRowTitleFocused to (13f to 500), // 14
                TypeRole.SetupCount to (16f to 600),
                TypeRole.Badge to (11f to 700),
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
                // Second screen, the details card (layout C).
                TypeRole.SecondDetailsTitleRoomy to (26f to 600),
                TypeRole.SecondDetailsTitleCompact to (20f to 600), // 22
                // Kept at 15: at 16 the Roomy card cut the genre beside the cover at Medium.
                TypeRole.SecondDetailsFact to (15f to 400),
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
        val topRamp = setOf(11f, 13f, 16f, 20f, 26f)
        val secondRamp = setOf(16f, 20f, 26f, 36f)
        // Glyphs drawn as icons inside a shape, not read as text: the only sizes allowed off the ramp.
        // KeyCap: at 11 a letter no longer fits the round 20 cap (21 wide).
        val icons = mapOf(TypeRole.KeyCap to 10f, TypeRole.SettingsInkSample to 28f)
        // Off the ramp with a reason (TypeRole's comment); a new entry here needs a reason.
        val exceptions = mapOf(TypeRole.SecondDetailsFact to 15f)
        val second = TypeRole.entries.filter { it.name.startsWith("Second") } - exceptions.keys
        val top = TypeRole.entries - second.toSet() - icons.keys - exceptions.keys

        for (role in second) assertTrue("$role ${role.sizeSp} is not on the second screen's ramp $secondRamp", role.sizeSp in secondRamp)
        for (role in top) assertTrue("$role ${role.sizeSp} is not on the ramp $topRamp", role.sizeSp in topRamp)
        for ((role, size) in icons + exceptions) assertEquals("$role", size, role.sizeSp)

        val topSizes = (top + icons.keys).map { it.sizeSp }.toSet()
        // The five steps and the two icons, nothing else.
        assertEquals("top screen sizes", topRamp + icons.values, topSizes)
        // The second screen's Compact top line is the top screen's Meta (13), the only role it shares; one class shows at a time.
        val roomy = SecondScreenDimens.Roomy.roles().map { it.sizeSp }.toSet()
        val compact = SecondScreenDimens.Compact.roles().map { it.sizeSp }.toSet()
        // At most four besides the exceptions' sizes.
        val excepted = exceptions.values.toSet()
        assertTrue("Roomy second screen sizes $roomy", (roomy - excepted).size <= 4)
        assertTrue("Compact second screen sizes $compact", (compact - excepted).size <= 4)
        // The in-game battery part: its sizes are on the second screen's ramp at each size class.
        for (dimens in InGameDimens.entries) {
            val sizes = listOf(dimens.leftRole, dimens.unitRole, InGameDimens.lineRole).map { it.sizeSp }.toSet()
            assertTrue("$dimens battery part sizes $sizes", sizes.all { it in secondRamp })
        }
    }

    private fun SecondScreenDimens.roles(): List<TypeRole> =
        listOf(topRole, titleRole, starRole, factsRole, detailsTitleRole, SecondScreenDimens.detailsTopRole, SecondScreenDimens.detailsFactRole)

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
    fun mediumIsTheTableAndOtherSizesScaleAroundIt() {
        assertEquals(16f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Medium))
        assertEquals(16f * 0.9f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Small), 1e-5f)
        assertEquals(16f * 1.1f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Large), 1e-5f)
        assertEquals(16f * 1.0352f * 1.1f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Serif, TextSize.Large), 1e-3f)
    }

    @Test
    fun sixSizesInOrderKeepTheThreeOlderFactors() {
        assertEquals(listOf("Small", "Medium", "Large", "Larger", "Huge", "Largest"), TextSize.entries.map { it.name })
        assertEquals(listOf(0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.45f), TextSize.entries.map { it.factor })
        assertEquals(TextSize.entries.sortedBy { it.factor }, TextSize.entries)
        assertEquals(16f * 1.45f, TypeRole.GameTitle.scaledSizeSp(PlumFont.Sans, TextSize.Largest), 1e-4f)
    }

    @Test
    fun boxesAroundTextGrowAboveMediumAndNeverShrink() {
        assertEquals(1f, TextSize.Small.heightScale)
        assertEquals(1f, TextSize.Medium.heightScale)
        assertEquals(1.45f, TextSize.Largest.heightScale)
    }

    @Test
    fun theDefaultIsLargeOnEveryScreen() {
        assertEquals(TextSize.Large, TextSize.Default)
    }

    @Test
    fun lineHeightIsTheFontsNormalLine() {
        assertEquals(16f * 1.21f, TypeRole.GameTitle.lineHeightSp(PlumFont.Sans, TextSize.Medium), 1e-2f)
        assertEquals(16f * (0.53f / 0.512f) * 1.485f, TypeRole.GameTitle.lineHeightSp(PlumFont.Serif, TextSize.Medium), 1e-3f)
        assertEquals(16f * (0.53f / 0.55f) * 1.32f, TypeRole.GameTitle.lineHeightSp(PlumFont.Mono, TextSize.Medium), 1e-3f)
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
        val thin = PlumTypography(PlumFont.Sans, TextSize.Medium, TextMode.Light, TextWeight.Thin)
        val bold = PlumTypography(PlumFont.Sans, TextSize.Medium, TextMode.Light, TextWeight.Bold)
        assertEquals(androidx.compose.ui.text.font.FontWeight(400), thin.gameTitle.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(700), bold.gameTitle.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(800), bold.gameTitleCurrent.fontWeight)
        assertTrue(thin != bold)
    }
}
