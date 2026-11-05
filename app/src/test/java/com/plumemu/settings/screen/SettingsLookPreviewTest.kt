package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SettingsPreviewDimens
import com.plumemu.coreui.theme.SettingsPreviewLook
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The look preview of the right column (docs/design/rules.md › Settings): where it is, which options have it, when it gives way, that it follows the theme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLookPreviewTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        option: SettingsOption,
        size: TextSize = TextSize.Small,
    ) = compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(optionsAt(option)) } }

    /** Placed: a part of a panel's own layout stays composed when it gives way, but is not placed. */
    private fun hasPreview() = compose.onAllNodesWithTag(SettingsTags.LOOK_PREVIEW).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    @Test
    fun theOptionsThatChangeTheLookGetTheTileAndNoOtherDoes() {
        val with = listOf(SettingsOption.Colors, SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent, SettingsOption.Font, SettingsOption.TextSize, SettingsOption.TextWeight, SettingsOption.EdgeFade)
        assertEquals(with.toSet(), SettingsOption.entries.filter { previewKindOf(it) != null }.toSet())
        show(SettingsOption.TextSize)
        assertTrue(hasPreview())
    }

    @Test
    fun anOptionOutsideTheLookHasTheTextPanelOnly() {
        for (option in listOf(SettingsOption.Language, SettingsOption.ScanOnStartup, SettingsOption.Animations)) assertEquals(null, previewKindOf(option))
        show(SettingsOption.ScanOnStartup)
        assertFalse(hasPreview())
    }

    private fun shownAtMedium(option: SettingsOption): Boolean {
        show(option)
        println("PREVIEW $option shown=${hasPreview()}")
        return hasPreview()
    }

    /** Font lists four sample rows of its own, so at 768 x 432 the card does not fit above them and the rows win. */
    @Test fun fontGivesWayToItsSampleRowsAtMedium() = assertFalse(shownAtMedium(SettingsOption.Font))

    /** Where the four rows and the card fit together (832 x 468, Small text) the card shows, the description giving way. */
    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun fontShowsTheTileWhereItFitsWithItsRows() {
        show(SettingsOption.Font, TextSize.Small)
        assertTrue(hasPreview())
    }

    @Test fun textWeightTile() = assertTrue(shownAtMedium(SettingsOption.TextWeight))

    @Test fun fadeTile() = assertTrue(shownAtMedium(SettingsOption.EdgeFade))

    @Test fun textColorGivesWayToItsSwatchesAtMedium() = assertFalse(shownAtMedium(SettingsOption.TextColor))

    @Test fun accentGivesWayToItsSwatches() = assertFalse(shownAtMedium(SettingsOption.Accent))

    @Test fun colorsGiveWayToTheirTiles() = assertFalse(shownAtMedium(SettingsOption.Colors))

    @Test fun backgroundGivesWayToItsSwatchesAtMedium() = assertFalse(shownAtMedium(SettingsOption.Background))

    /** The board's card: x 538.7..744, y 56..170.7 (808..1116 x 84..256 px at 1.5). */
    @Test
    fun theTileIsWhereTheBoardPutsIt() {
        show(SettingsOption.TextSize)
        val b = compose.onNodeWithTag(SettingsTags.LOOK_PREVIEW).getBoundsInRoot()
        assertEquals(538.67f, b.left.value, 1f)
        assertEquals(744f, b.right.value, 1f)
        assertEquals(56f, b.top.value, 1f)
        assertEquals(170.67f, b.bottom.value, 1f)
        assertEquals(205.33f, (b.right - b.left).value, 1f)
        assertEquals(114.67f, (b.bottom - b.top).value, 1f)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun at832TheTileKeepsItsSizeAtTheRightMargin() {
        show(SettingsOption.TextSize)
        val b = compose.onNodeWithTag(SettingsTags.LOOK_PREVIEW).getBoundsInRoot()
        assertEquals(832f - 24f, b.right.value, 1f)
        assertEquals(205.33f, (b.right - b.left).value, 1f)
        assertEquals(114.67f, (b.bottom - b.top).value, 1f)
    }

    /** The title sits under the card by the board's gap (22 px = 14.67 dp). */
    @Test
    fun theTitleStartsOneGapUnderTheCard() {
        assertEquals(14.67f, SettingsPreviewDimens.gapBelow.value, 0.01f)
        assertEquals(205.33f, SettingsPreviewDimens.width.value, 0.01f)
        assertEquals(114.67f, SettingsPreviewDimens.height.value, 0.01f)
        assertEquals(12f, SettingsPreviewDimens.corner.value, 0.01f)
    }

    /** The rule itself: the card is placed only when the panel and the card fit the column together. */
    @Test
    fun theCardShowsOnlyWhereItFitsWholeWithThePanel() {
        val card = SettingsPreviewDimens.top + SettingsPreviewDimens.height + SettingsPreviewDimens.gapBelow
        var panel by mutableStateOf(100.dp)
        compose.setContent {
            Box(Modifier.width(240.dp).height(300.dp)) {
                PlumTheme(PlumThemeSettings.Graphite) { PanelWithPreview(PreviewKind.Look, fadeEdges = true, allowed = true) { Box(Modifier.width(240.dp).height(panel)) } }
            }
        }
        for ((height, shown) in listOf(100.dp to true, (300.dp - card) to true, (300.dp - card + 1.dp) to false, 300.dp to false, 100.dp to true)) {
            compose.runOnIdle { panel = height }
            compose.waitForIdle()
            assertEquals("panel $height", shown, hasPreview())
        }
    }

    @Test
    fun aCoveringPageHasNoCard() {
        compose.setContent {
            Box(Modifier.width(240.dp).height(400.dp)) {
                PlumTheme(PlumThemeSettings.Graphite) { PanelWithPreview(PreviewKind.Look, fadeEdges = true, allowed = false) { Box(Modifier.width(240.dp).height(40.dp)) } }
            }
        }
        assertFalse(hasPreview())
    }

    /** The yielding layout of the colour pages: swatches first, then the title, the text give way to the preview. */
    private fun yields(columnHeight: Int): Triple<Boolean, Boolean, Boolean> {
        compose.setContent {
            Box(Modifier.width(240.dp).height(columnHeight.dp)) {
                PanelHeaderYields(
                    Modifier.fillMaxHeight(),
                    gap = 10.dp,
                    header = listOf({ Box(Modifier.height(20.dp).fillMaxWidth().testTag("title")) }, { Box(Modifier.height(40.dp).fillMaxWidth().testTag("body")) }),
                    main = { Box(Modifier.height(100.dp).fillMaxWidth().testTag("main")) },
                    rest = {},
                    leading = { Box(Modifier.height(100.dp).fillMaxWidth().testTag("lead")) },
                )
            }
        }
        fun placed(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
        return Triple(placed("lead"), placed("title"), placed("body"))
    }

    /** Room for everything: lead 100 + title 20 + body 40 + main 100 + three gaps of 10 = 290. */
    @Test
    fun colourPageWithRoomForAllShowsThePreviewAboveTheTitle() = assertEquals(Triple(true, true, true), yields(290))

    /** Without room for the text the preview still shows while the title and the swatches fit with it: lead 100 + 10 + title 20 + 10 + main 100 = 240. */
    @Test
    fun colourPageGivesTheTextUpBeforeThePreview() = assertEquals(Triple(true, true, false), yields(240))

    /** Without room for preview, title and swatches together, the preview goes; the title, the text and the swatches stay. */
    @Test
    fun colourPageDropsThePreviewWhenTheSwatchesAndTheTitleNeedTheRoom() = assertEquals(Triple(false, true, true), yields(239))

    /** [shown]: measured on this size: the swatches and the card fit together or they do not. */
    private fun backgroundAt(
        size: TextSize,
        shown: Boolean,
    ) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { SettingsAt(optionsAt(SettingsOption.Background)) } }
        val panel = compose.onNodeWithTag(SettingsTags.PANEL).getBoundsInRoot()
        assertEquals("$size at ${panel.right}", shown, hasPreview())
        if (hasPreview()) {
            val card = compose.onNodeWithTag(SettingsTags.LOOK_PREVIEW).getBoundsInRoot()
            assertTrue("$size: the card is inside the column", card.bottom <= panel.bottom)
            // Every swatch whole, under the card and above the column's end.
            for (i in 0..8) {
                val swatch = compose.onNodeWithTag(SettingsTags.panelItem(i)).getBoundsInRoot()
                assertTrue("$size: swatch $i under the card", swatch.top >= card.bottom)
                assertTrue("$size: swatch $i whole", swatch.bottom <= panel.bottom)
            }
        }
    }

    @Test fun backgroundSmall() = backgroundAt(TextSize.Small, shown = false)

    @Test fun backgroundMedium() = backgroundAt(TextSize.Medium, shown = false)

    @Test fun backgroundLarge() = backgroundAt(TextSize.Large, shown = false)

    @Test fun backgroundLargest() = backgroundAt(TextSize.Largest, shown = false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun background832Small() = backgroundAt(TextSize.Small, shown = false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun background832Medium() = backgroundAt(TextSize.Medium, shown = false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun background832Large() = backgroundAt(TextSize.Large, shown = false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun background832Largest() = backgroundAt(TextSize.Largest, shown = false)

    private fun pixel(
        tag: String,
        x: Float,
        y: Float,
    ): Color {
        val at = compose.onNodeWithTag(tag).getBoundsInRoot()
        val image = compose.onRoot().captureToImage().toPixelMap()
        val density = 2f
        return image[((at.left.value + x) * density).toInt(), ((at.top.value + y) * density).toInt()]
    }

    private fun near(
        expected: Color,
        actual: Color,
        what: String,
    ) {
        for ((e, a) in listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)) {
            assertTrue("$what: expected $expected got $actual", kotlin.math.abs(e - a) * 255f <= 3f)
        }
    }

    private fun lum(c: Color) = c.red + c.green + c.blue

    /** The board's cover tile (`radial-gradient(circle at 28% 18%, #3470D8, #1F4FA8 45%, #143677)` on the blue accent): its stops stay within 14 per channel of the board's, and it is drawn as the gradient: lightest at its centre, darker toward the far corner. */
    @Test
    fun theCoverTileIsTheBoardsRadialGradient() {
        val d = SettingsPreviewDimens
        assertEquals(61.33f, d.tileWidth.value, 0.01f)
        assertEquals(46.67f, d.tileHeight.value, 0.01f)
        assertEquals(6.67f, d.tileCorner.value, 0.01f)
        assertEquals(12f, d.tileRight.value, 0.01f)
        assertEquals(14.67f, d.tileTop.value, 0.01f)
        assertEquals(30.67f, d.ballDiameter.value, 0.01f)
        val accent = Color(0xFF3E7BE0)
        val board = listOf(Color(0xFF3470D8), Color(0xFF1F4FA8), Color(0xFF143677))
        SettingsPreviewLook.tileStops(accent).zip(board).forEachIndexed { i, (ours, theirs) ->
            for ((o, b) in listOf(ours.red to theirs.red, ours.green to theirs.green, ours.blue to theirs.blue)) assertTrue("stop $i: $ours against $theirs", kotlin.math.abs(o - b) * 255f <= 14f)
        }
        assertEquals(listOf(0f, 0.45f, 1f), SettingsPreviewLook.TILE_STOPS)
        assertEquals(listOf(0f, 0.42f, 1f), SettingsPreviewLook.BALL_STOPS)
        show(SettingsOption.TextWeight)
        val centre = pixel(SettingsTags.LOOK_PREVIEW, 149f, 23f)
        val middle = pixel(SettingsTags.LOOK_PREVIEW, 162f, 40f)
        val corner = pixel(SettingsTags.LOOK_PREVIEW, 189f, 58f)
        assertTrue("lightest at the gradient's centre: $centre $middle $corner", lum(centre) > lum(middle) && lum(middle) > lum(corner))
    }

    /** The tile's drop shadow (`0 8px 18px` black .5) darkens the well under its bottom edge; far from it the well is plain. */
    @Test
    fun theCoverTileCastsItsDropShadow() {
        show(SettingsOption.TextWeight)
        val under = pixel(SettingsTags.LOOK_PREVIEW, 163f, 65f)
        val away = pixel(SettingsTags.LOOK_PREVIEW, 163f, 105f)
        assertTrue("shadow under the tile: $under against $away", lum(under) < lum(away) - 0.02f || lum(under) > lum(away) + 0.02f)
    }

    /** The card's `0 1px 0` white line sits just outside its bottom edge (outside the clip), and the card's inset shadow darkens its top edge inside. */
    @Test
    fun theCardHasItsBottomLineOutsideAndItsInsetShadowInside() {
        show(SettingsOption.TextWeight)
        val line = pixel(SettingsTags.LOOK_PREVIEW, 100f, 114.9f)
        val page = pixel(SettingsTags.LOOK_PREVIEW, 100f, 118f)
        assertTrue("the white line is lighter than the page under it: $line against $page", lum(line) > lum(page) + 0.04f)
        val top = pixel(SettingsTags.LOOK_PREVIEW, 100f, 0.5f)
        val inside = pixel(SettingsTags.LOOK_PREVIEW, 100f, 6f)
        assertTrue("the inset shadow is darkest at the top edge: $top against $inside", lum(top) < lum(inside) - 0.01f)
        assertEquals(0.67f, SettingsPreviewDimens.frameLineDrop.value, 0.01f)
        assertEquals(1.33f, SettingsPreviewDimens.frameInsetDrop.value, 0.01f)
    }

    /** A slider drag shows through ColorPreview: the tile and the well follow the previewed colours while the store is untouched. */
    @Test
    fun theTileFollowsALiveColourPreview() {
        val preview = ColorPreview()
        val stored = PlumSettings(flags = SetupFlags(setupCompleted = true))
        compose.setContent { SettingsTheme(stored, null, preview) { SettingsAt(optionsAt(SettingsOption.TextWeight), stored) } }
        compose.waitForIdle()
        val before = pixel(SettingsTags.LOOK_PREVIEW, 149f, 23f)
        compose.runOnIdle { preview.show(stored.appearance.copy(accent = ColorSetting.Custom(0x2E9E5B), background = ColorSetting.Custom(0x203040)), fingerDown = true) }
        compose.waitForIdle()
        val after = pixel(SettingsTags.LOOK_PREVIEW, 149f, 23f)
        near(SettingsPreviewLook.tileStops(Color(0xFF2E9E5B))[0], after, "tile in the previewed accent")
        assertTrue("the tile changed colour", before != after)
        near(SettingsPreviewLook.well(Color(0xFF203040), Color(0xFFEDE8DD)).let { it }, pixel(SettingsTags.LOOK_PREVIEW, 100f, 108f), "well on the previewed background")
    }

    /** A stored change (a ‹ › step, a swatch picked) reaches the tile the same way. */
    @Test
    fun theTileFollowsAStoredChange() {
        var stored by mutableStateOf(PlumSettings(flags = SetupFlags(setupCompleted = true)))
        compose.setContent { SettingsTheme(stored) { SettingsAt(optionsAt(SettingsOption.TextWeight), stored) } }
        compose.waitForIdle()
        compose.runOnIdle { stored = stored.copy(appearance = stored.appearance.copy(accent = ColorSetting.Custom(0xD0502A))) }
        compose.waitForIdle()
        near(SettingsPreviewLook.tileStops(Color(0xFFD0502A))[0], pixel(SettingsTags.LOOK_PREVIEW, 149f, 23f), "tile after a stored accent")
    }

    private fun bottomLine(on: Boolean): Color {
        val settings = PlumSettings(appearance = AppearanceSettings(background = ColorSetting.Preset("Graphite"), edgeFade = on), flags = SetupFlags(setupCompleted = true))
        compose.setContent { SettingsTheme(settings) { SettingsAt(optionsAt(SettingsOption.EdgeFade), settings) } }
        compose.waitForIdle()
        return pixel(SettingsTags.LOOK_PREVIEW, 40f, 96f)
    }

    private val plainLine: Color
        get() {
            val theme = PlumThemeSettings.Graphite
            val colors = com.plumemu.coreui.theme.PlumColors.derive(theme.backgroundColor, theme.textMode, theme.accentColor)
            val well = SettingsPreviewLook.well(colors.bg.toComposeColor(), colors.text.toComposeColor())
            return SettingsPreviewLook.line(colors.text.toComposeColor()).compositeOver(well)
        }

    /** Fade list edges on: the last list line is half gone into the well at its middle; off: it is the plain line. */
    @Test
    fun fadeShowsWhileFadeListEdgesIsOn() {
        val line = bottomLine(true)
        assertTrue("faded: $line against the plain ${plainLine}", line.red < plainLine.red - 0.05f)
    }

    @Test
    fun noFadeWhileFadeListEdgesIsOff() = near(plainLine, bottomLine(false), "the plain last line")

    /** Whether the chevron node at [tag] has anything drawn in it, against the screen's background. */
    private fun chevronDrawn(tag: String): Boolean {
        val at = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bg = image[2, 2]
        for (x in (at.left.value * 2).toInt() until (at.right.value * 2).toInt()) {
            for (y in (at.top.value * 2).toInt() until (at.bottom.value * 2).toInt()) {
                val c = image[x, y]
                if (kotlin.math.abs(c.red - bg.red) + kotlin.math.abs(c.green - bg.green) + kotlin.math.abs(c.blue - bg.blue) > 0.15f) return true
            }
        }
        return false
    }

    /** Colors' A opens its tiles, so at rest it ends in the one right chevron like every row that opens a page; Font, which only steps, shows none. */
    @Test
    fun colorsEndsInARightChevronAtRestAndAStepOnlyRowDoesNot() {
        show(SettingsOption.TextSize)
        val right = { option: SettingsOption -> SettingsTags.chevronRight(SettingsTags.value(option)) }
        val left = { option: SettingsOption -> SettingsTags.chevronLeft(SettingsTags.value(option)) }
        assertTrue("Colors: right chevron at rest", chevronDrawn(right(SettingsOption.Colors)))
        assertFalse("Colors: no left chevron at rest", chevronDrawn(left(SettingsOption.Colors)))
        assertTrue("Language opens a page: right chevron", chevronDrawn(right(SettingsOption.Language)))
        assertFalse("Font only steps: no chevron at rest", chevronDrawn(right(SettingsOption.Font)))
    }
}
