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
 * breaks, and every fact stands on its own line in order, labels at the left, values at one x, at one pitch.
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

    // Requirement changed: one fact to a line (was six cells in three rows). The whole class stays: its point is still that the long values and
    // the long emulator name fit; the share/column measurement is now the line contract (FactLines.kt).
    @Test
    fun everyFactStandsOnItsOwnLineInOrderAtOnePitch() {
        show()
        val box = compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val lines = readFactLines(compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode())
        assertEquals("every fact is on show", FACT_ORDER, lines.map { it.name })
        assertEquals("no problems", emptyList<String>(), factLineProblems(box, lines))
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
        val LABELS = setOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery")
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
