package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details column's facts for a game with 26 h 31 min played, a battery estimate and a long emulator name: no label wraps, no duration
 * breaks, and every cell stands in an equal column (the grid is measured from the text, HomeDetailsShares.kt).
 */
abstract class HomeDetailsGridFitBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Panel() {
        val details = GameDetails(year = 2013, publisher = "Nintendo", genre = "Adventure", players = "1", description = "", rating = 9)
        val play = PlayFacts(0, 26 * 60 + 31, "RetroArch (Mupen64Plus-Next GLES3 with a long name)", batteryMinutesOnCharge = 405.0, batteryPercentPerHour = 14.8)
        Column(Modifier.width(PlumDimens.detailsColumnWidth).testTag(PANEL)) { DetailsBlock(details, ShownPlay.Fixed(play)) }
    }

    private fun show() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                // The battery cell needs a reading (83 %, not charging).
                CompositionLocalProvider(LocalBatteryReading provides remember { mutableStateOf<BatteryReading?>(BatteryReading(83, false)) }) { Panel() }
            }
        }
        compose.waitForIdle()
    }

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun text(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("")

    private fun lines(node: SemanticsNode): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().lineCount
    }

    @Test
    fun noLabelWrapsAndNoDurationBreaks() {
        show()
        val all = texts(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode())
        val labels = all.filter { text(it) in LABELS }
        assertEquals("the labels: ${all.map(::text)}", LABELS.size, labels.size)
        labels.forEach { assertEquals("${text(it)} wraps", 1, lines(it)) }
        val durations = all.filter { Regex("(About[\\s\u00A0])?\\d+[\\s\u00A0]h[\\s\u00A0]\\d+[\\s\u00A0]min").matches(text(it)) || text(it) == "Today" }
        assertTrue("the durations: ${all.map(::text)}", durations.size >= 3)
        durations.forEach { assertEquals("${text(it)} breaks", 1, lines(it)) }
    }

    // Requirement changed: six cells in three rows (HomeDetailsShares.kt); a row of cells that fit equal parts has them, else each has what its text needs.
    @Test
    fun everyRowStartsAtTheLeftInOrderWithoutOverlap() {
        show()
        val panel = compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val labels = texts(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode()).filter { text(it) in LABELS }
        val rows = labels.groupBy { Math.round(it.boundsInRoot.top) }.toSortedMap().values.map { row -> row.sortedBy { it.boundsInRoot.left } }
        assertEquals("the labels: ${labels.map(::text)}", LABELS.size, labels.size)
        assertTrue("at most three rows: ${rows.map { r -> r.map(::text) }}", rows.size <= 3)
        rows.forEach { row ->
            assertEquals("${row.map(::text)} starts at the left edge", panel.left, row.first().boundsInRoot.left, 1f)
            row.zipWithNext().forEach { (a, b) -> assertTrue("${text(a)} ends before ${text(b)}", a.boundsInRoot.right <= b.boundsInRoot.left) }
        }
    }

    /** The details column as a golden (`home_details_grid_<size>_graphite.png`). */
    abstract val golden: String

    @Test
    fun theColumnGolden() {
        show()
        compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$golden.png")
    }

    private companion object {
        const val PANEL = "details_panel"
        val LABELS = setOf("GENRE", "RATING", "PLAYED", "LAST PLAYED", "EMULATOR", "BATTERY")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = POCKET6)
class HomeDetailsGridFitThorTest : HomeDetailsGridFitBase() {
    override val golden = "home_details_grid_853x480_graphite"
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsGridFit768Test : HomeDetailsGridFitBase() {
    override val golden = "home_details_grid_768x432_graphite"
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeDetailsGridFit576Test : HomeDetailsGridFitBase() {
    override val golden = "home_details_grid_576x432_graphite"
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsGridFit640Test : HomeDetailsGridFitBase() {
    override val golden = "home_details_grid_640x480_graphite"
}
