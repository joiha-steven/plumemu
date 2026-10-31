package com.plumemu.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.settings.screen.SettingsPanelTags
import com.plumemu.settings.screen.ShotContent
import com.plumemu.settings.screen.optionsAt
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The breathing dot of the scan step: it draws more than one alpha while the scan
 * runs, recomposes nothing while it does, is solid with motion off, and is gone once the scan ends.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class ScanPulseTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    @Before
    fun traceCompositions() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun show(
        state: SetupUiState,
        animationsOn: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = animationsOn)) { SetupAt(state, pulsePhase = null) } }
        compose.mainClock.advanceTimeByFrame()
    }

    /** The dot's centre pixel, which is the accent at the dot's current alpha over the background. */
    private fun dotPixel(): Int {
        val image: ImageBitmap = compose.onNodeWithTag(SetupTags.SCAN_PULSE).captureToImage()
        return image.asAndroidBitmap().getPixel(image.width / 2, image.height / 2)
    }

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF

    @Test
    fun theDotBreathesBetweenAboutAThirdAndFullBrightnessWithoutRecomposing() {
        show(SetupFakes.scanning)
        compose.onNodeWithTag(SetupTags.SCAN_PULSE).assertExists()
        calls.clear()
        val seen = mutableListOf<Int>()
        repeat(32) {
            compose.mainClock.advanceTimeBy(50)
            seen += red(dotPixel())
        }
        assertTrue("the dot never changed: $seen", seen.max() - seen.min() > 40)
        // The background's red channel is dark and the accent's bright, so the dot's alpha shows in red.
        assertEquals("nothing recomposed while the dot breathed: $calls", emptyList<String>(), calls)
    }

    @Test
    fun withAnimationsOffTheDotIsSolidAndNothingMoves() {
        show(SetupFakes.scanning, animationsOn = false)
        val first = dotPixel()
        calls.clear()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(first, dotPixel())
        assertEquals("solid: the accent itself", PlumThemeSettings.Graphite.accentColor.red, red(first))
        assertEquals(emptyList<String>(), calls)
    }

    /**
     * The rescan panel is composed only while it is on screen (`SettingsStrip`: the panel column, or the
     * options column at a width where the panel sits beside it). At the narrow width with the options column
     * focused the panel is slid away, so its dot is not composed and cannot animate.
     */
    @Test
    @Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theRescanPanelDotIsNotComposedWhileThePanelIsOffStage() {
        val scanning = ShotContent.library.copy(isScanning = true, scan = ScanReport(phase = ScanPhase.Naming, gamesNamed = 1, gamesToName = 2))
        compose.mainClock.autoAdvance = false
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { SettingsAt(optionsAt(SettingsOption.Rescan), content = scanning) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(SettingsPanelTags.SCAN_PULSE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun noDotOnceTheScanHasEnded() {
        show(SetupFakes.ready)
        compose.onNodeWithTag(SetupTags.SCAN_PULSE).assertDoesNotExist()
    }

    /** The dot's centre must lie within the first line of its text (top..bottom of line 0), not the middle of a wrapped block. */
    private fun assertDotBesideFirstLine(
        dotTag: String,
        textTag: String,
        label: String,
    ): Int {
        val dot = compose.onNodeWithTag(dotTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val textNode = compose.onNodeWithTag(textTag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        textNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val top = textNode.boundsInRoot.top + layout.getLineTop(0)
        val baseline = textNode.boundsInRoot.top + layout.getLineBaseline(0)
        val centre = (dot.top + dot.bottom) / 2
        // Top of the first line down to its baseline: a dot centred on a wrapped block lands on the line's bottom, below the baseline.
        assertTrue("$label: dot centre $centre not in the first line's upper part $top..$baseline", centre in top..baseline)
        return layout.lineCount
    }

    /** Runs [check] at every text size and in Mono; returns the most lines the text took at any of them. */
    private fun everySizeAndFont(check: (String) -> Int): Int {
        var mostLines = 1
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { content() }
        }
        val all = TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Mono to TextSize.Large, PlumFont.Mono to TextSize.Largest)
        for ((f, z) in all) {
            font = f
            size = z
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            mostLines = maxOf(mostLines, check("${z.name} ${f.name}"))
        }
        return mostLines
    }

    private lateinit var content: @Composable () -> Unit

    @Test
    fun theSetupDotStaysByTheFirstLineAtEveryTextSizeAndInMono() {
        content = { SetupAt(SetupFakes.scanning) }
        val mostLines = everySizeAndFont { assertDotBesideFirstLine(SetupTags.SCAN_PULSE, SetupTags.SCAN_LINE, "setup $it") }
        assertTrue("the setup line never wrapped (most lines: $mostLines)", mostLines > 1)
    }

    @Test
    fun theRescanPanelDotStaysByTheFirstLineEvenWhenTheLineWraps() {
        // The longest progress line: big numbers in the naming phase, so the larger sizes and fonts wrap it.
        val longest = ScanReport(phase = ScanPhase.Naming, gamesNamed = 123_456, gamesToName = 234_567)
        content = { SettingsAt(optionsAt(SettingsOption.Rescan), content = ShotContent.library.copy(isScanning = true, scan = longest)) }
        val mostLines = everySizeAndFont { assertDotBesideFirstLine(SettingsPanelTags.SCAN_PULSE, SettingsPanelTags.STATUS, "rescan panel $it") }
        assertTrue("the panel line never wrapped, so this proves nothing about a wrapped block", mostLines > 1)
    }
}
