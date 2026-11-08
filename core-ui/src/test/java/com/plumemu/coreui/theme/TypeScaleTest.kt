package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScaleTest {
    @Test
    fun sizesAndWeightsMatchTheDesignTable() {
        // The type ramp and the one weight scale (docs/design/rules.md › Type); the old size is in the comment where it changed.
        val expected =
            mapOf(
                TypeRole.GameTitle to (16f to 500),
                TypeRole.GameTitleFocused to (16f to 600),
                TypeRole.GameTitleCurrent to (16f to 700),
                // One step above the rows (17 before the ramp).
                TypeRole.MessageTitle to (20f to 600),
                TypeRole.FontSample to (16f to 500), // 17
                TypeRole.SystemName to (16f to 500), // 15
                TypeRole.SystemNameCurrent to (16f to 700), // 15
                TypeRole.SystemNameFocused to (16f to 600), // 15
                TypeRole.SettingsOption to (16f to 500),
                TypeRole.SettingsOptionFocused to (16f to 600),
                TypeRole.DetailsTitleWide to (20f to 700), // 19
                TypeRole.DetailsTitleNarrow to (20f to 700),
                TypeRole.TopBar to (13f to 500), // Mono
                TypeRole.Meta to (13f to 500),
                TypeRole.SmallFact to (13f to 500), // 12
                // The current year in the narrow details stop (Nova-3-details: 12, weight 600).
                TypeRole.SmallFactCurrent to (13f to 700),
                // The games list's right-hand text: the board's 10 and 8 raised, the year to 13 and the tag to the info line's step (Thor build 1299).
                TypeRole.RowYear to (13f to 500),
                TypeRole.RowYearCurrent to (13f to 500),
                TypeRole.Tag to (11f to 500), // RowRegion; Mono 11, 11.33 before the mono step
                TypeRole.SystemRowCount to (13f to 500), // Mono
                TypeRole.FactNumber to (13f to 500), // Jost, tabular figures
                TypeRole.RatingNumber to (13f to 500), // Mono, a figure alone
                TypeRole.StatisticPhrase to (20f to 700), // Jost, tabular figures
                // The top bar place and details block (board px / 1.5, then the ramp's nearest step; 11 where the board's is smaller).
                TypeRole.PlaceName to (20f to 700),
                TypeRole.PlaceCount to (11f to 500), // Jost, tabular figures
                TypeRole.PlaceWords to (11f to 500),
                TypeRole.DetailsLabel to (13f to 500), // sentence case, 11 / 600 tracked caps before the mono step
                TypeRole.DetailsSubtitle to (11f to 500),
                // The focused title on the grid's bar (RP5-3-grid-snes: 14, weight 600).
                TypeRole.GridFocusTitle to (13f to 700),
                // The grid's info line from the Covers board (24 px and 17 px over 1.5).
                TypeRole.InfoLineTitle to (16f to 700),
                TypeRole.InfoLineMeta to (11f to 500), // 11.33 before the mono step (the nearest ramp step)
                // 12 before the ramp; 13 cut the Small grid's landscape titles from Huge (from Large in Serif).
                TypeRole.SmallTileTitle to (11f to 700),
                TypeRole.SmallTileYear to (11f to 500), // smallFact (12) before the ramp
                TypeRole.HintLabel to (13f to 500), // 12
                TypeRole.KeyCap to (10f to 700),
                TypeRole.SetupTitleWide to (26f to 700),
                TypeRole.SetupTitleNarrow to (20f to 700), // 22
                TypeRole.SetupPoint to (16f to 500), // 15
                TypeRole.SetupButton to (16f to 700), // 15
                TypeRole.SetupButtonQuiet to (16f to 500), // 15
                TypeRole.SetupChoice to (16f to 500),
                TypeRole.SetupChoiceFocused to (16f to 600),
                TypeRole.SetupChoiceCurrent to (16f to 700),
                TypeRole.SetupRowTitle to (13f to 500), // 14
                TypeRole.SetupRowTitleFocused to (13f to 600), // 14
                TypeRole.SetupCount to (16f to 700),
                TypeRole.Badge to (11f to 800),
                TypeRole.SystemTag to (13f to 800),
                TypeRole.SettingsInkSample to (28f to 700),
                TypeRole.SettingsSwatchGlyph to (20f to 500),
                // The board's 22 / 800: no such face in plum-fonts yet.
                TypeRole.SettingsAboutTitle to (20f to 700),
                TypeRole.SettingsPanelMeta to (11f to 500),
                TypeRole.SettingsPanelItemFocused to (13f to 600),
                TypeRole.SettingsPanelAction to (13f to 700),
                TypeRole.ColorTileName to (11f to 500),
                TypeRole.ColorTileNameCurrent to (11f to 700),
                // Second screen, picking: Roomy then Compact (docs/design/rules.md › Second screen).
                TypeRole.SecondTopRoomy to (16f to 500), // 15
                TypeRole.SecondTitleRoomy to (36f to 700),
                TypeRole.SecondStarRoomy to (20f to 500), // 24
                TypeRole.SecondFactsRoomy to (16f to 500), // 17
                TypeRole.SecondTitleCompact to (26f to 700), // 27
                TypeRole.SecondStarCompact to (20f to 500),
                TypeRole.SecondFactsCompact to (16f to 500), // 15
                // Second screen, the App and Apps cards' title.
                TypeRole.SecondDetailsTitleRoomy to (26f to 700),
                TypeRole.SecondDetailsTitleCompact to (20f to 700), // 22
                // Second screen in game, the battery part: the design's 17 and 15 are 16 on the ramp.
                TypeRole.SecondBatteryLeftRoomy to (36f to 700),
                TypeRole.SecondBatteryUnitRoomy to (20f to 500),
                TypeRole.SecondBatteryLeftCompact to (26f to 700),
                TypeRole.SecondBatteryUnitCompact to (16f to 500),
                TypeRole.SecondBatteryLine to (16f to 500),
                // The on-screen keyboard (design/rules.md › Keyboard; the board's 16 and 13).
                TypeRole.KeyboardKey to (16f to 500),
                TypeRole.KeyboardKeyFocused to (16f to 700),
                TypeRole.KeyboardWord to (13f to 500),
                TypeRole.KeyboardWordStrong to (13f to 700),
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
        // The role's size is the ramp step; Mono draws it at 0.96 of that, which is a face tuning (digits as tall as Jost's).
        val floor = 11f
        for (role in listOf(TypeRole.Tag, TypeRole.RowYear, TypeRole.RowYearCurrent)) {
            assertTrue("$role", role.sizeSp >= floor)
        }
        // Weight 500 in Mono, which has no 600.
        assertEquals(500, TypeRole.Tag.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(11f * MONO_FACE_FACTOR * 1.5f, TypeRole.Tag.scaledSizeSp(PlumFont.Jost, TextSize.Largest), 1e-3f)
        assertEquals(13f * MONO_FACE_FACTOR, TypeRole.RowYear.scaledSizeSp(PlumFont.Jost, TextSize.Small), 1e-6f)
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
            assertEquals(600, TypeRole.GameTitleFocused.weightFor(font, TextMode.Light))
            assertEquals(600, TypeRole.GameTitleFocused.weightFor(font, TextMode.Dark))
        }
    }

    @Test
    fun oneWeightScaleBodyFocusedTitleBadge() {
        // The scale of every screen (the Text weight setting is gone): body 500, focused 600, titles 700, badges 800; Mono never above 500.
        for (role in TypeRole.entries) {
            assertTrue("$role is on a face", role.weight in listOf(500, 600, 700, 800))
            assertEquals("$role", if (role.mono) minOf(role.weight, 500) else role.weight, role.weightFor(PlumFont.Jost, TextMode.Dark))
        }
        assertEquals(500, TypeRole.GameTitle.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(600, TypeRole.GameTitleFocused.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(700, TypeRole.GameTitleCurrent.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(700, TypeRole.DetailsTitleWide.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(800, TypeRole.Badge.weightFor(PlumFont.Jost, TextMode.Light))
        assertEquals(500, TypeRole.RowYearCurrent.weightFor(PlumFont.Jost, TextMode.Light))
    }

    @Test
    fun typographyDrawsTheScale() {
        val type = PlumTypography(PlumFont.Jost, TextSize.Small, TextMode.Light)
        assertEquals(androidx.compose.ui.text.font.FontWeight(500), type.gameTitle.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight(700), type.gameTitleCurrent.fontWeight)
    }

    @Test
    fun theHintBarStopsGrowingAtMedium() {
        val sans = PlumFont.Sans
        // Labels and key caps: 13 sp times the font factor and 10 sp times the font factor, at 1.1 from Medium up.
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

    /** Numbers and codes: the roles drawn wholly in Plum Mono (docs/design/rules.md › Type). Every other role is Jost; a phrase of figures and words is in [tabularRoles]. */
    private val monoRoles =
        setOf(
            TypeRole.TopBar,
            TypeRole.RowYear,
            TypeRole.RowYearCurrent,
            TypeRole.Tag,
            TypeRole.SystemRowCount,
            TypeRole.RatingNumber,
        )

    /** Phrases that mix figures and words: Jost with tabular figures, never Mono. */
    private val tabularRoles = setOf(TypeRole.PlaceCount, TypeRole.FactNumber, TypeRole.StatisticPhrase)

    @Test
    fun onlyTheNumberAndCodeRolesResolveToMono() {
        val type = PlumTypography(PlumFont.Jost, TextSize.Small, TextMode.Light)
        assertEquals(monoRoles, TypeRole.entries.filter { it.mono }.toSet())
        for (role in TypeRole.entries) {
            val expected = if (role in monoRoles) PlumFonts.Mono else PlumFonts.Jost
            assertEquals("$role", expected, type[role].fontFamily)
            // No tracking on a mono role: it is already even.
            if (role in monoRoles) assertTrue("$role has letter spacing", type[role].letterSpacing.isUnspecified)
        }
        // Labels over the facts are words: Jost, 13, plain.
        assertEquals(13f, type.detailsLabel.fontSize.value, 1e-6f)
        assertTrue(type.detailsLabel.letterSpacing.isUnspecified)
    }

    @Test
    fun phraseRolesAreJostWithTabularFiguresAndNothingElseIs() {
        val type = PlumTypography(PlumFont.Jost, TextSize.Small, TextMode.Light)
        assertEquals(tabularRoles, TypeRole.entries.filter { it.tabular }.toSet())
        for (role in TypeRole.entries) {
            assertEquals("$role", if (role in tabularRoles) "tnum" else null, type[role].fontFeatureSettings)
            if (role in tabularRoles) assertEquals("$role", PlumFonts.Jost, type[role].fontFamily)
        }
        // A row of Statistics tiles is one group: every figure, "73%" too, is the Jost phrase.
        assertEquals(type[TypeRole.StatisticPhrase], type.statisticFigure)
    }

    @Test
    fun everyJostFaceHasTheTabularFiguresFeature() {
        val faces = java.io.File("src/main/res/font").listFiles { f -> f.name.startsWith("jost_") }.orEmpty()
        assertEquals(5, faces.size)
        for (face in faces) assertTrue("${face.name} has no tnum", String(face.readBytes(), Charsets.ISO_8859_1).contains("tnum"))
    }

    @Test
    fun aMonoRoleKeepsItsJostLineAndTakesWeight500() {
        val jost = PlumFont.Jost
        for (size in TextSize.entries) {
            for (role in monoRoles) {
                val factor = size.factor
                val asJost = role.sizeSp * factor * jost.lineHeightEm
                assertEquals("$role line at ${size.name}", asJost, role.lineHeightSp(jost, size), 1e-3f)
                assertEquals("$role size at ${size.name}", role.sizeSp * MONO_FACE_FACTOR * factor, role.scaledSizeSp(jost, size), 1e-3f)
            }
        }
        for (role in monoRoles) {
            assertEquals("$role", 500, role.weightFor(jost, TextMode.Light))
        }
    }

    @Test
    fun wifiAndChargingGlyphsFollowMonoBesideMonoDigits() {
        val type = PlumTypography(PlumFont.Jost, TextSize.Small, TextMode.Light)
        assertEquals(PlumFont.Mono, type.fontOf(type.topBar))
        assertEquals(PlumFont.Jost, type.fontOf(type.meta))
    }
}
