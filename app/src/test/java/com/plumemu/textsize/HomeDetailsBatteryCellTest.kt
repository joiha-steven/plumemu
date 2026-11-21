package com.plumemu.textsize

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FACT_ORDER
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.factLineProblems
import com.plumemu.home.readFactLines
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide details block's lower facts: last played, emulator and battery lines, at 768 x 432 and 832 x 468: the battery cell shows the time on a
 * charge at all six text sizes, whole (never left out, never cut); with no estimate the cell is left out and the others keep their order and pitch; the old line under
 * the block is gone.
 */
abstract class HomeDetailsBatteryCellBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    // A cell with nothing to say takes no room, so a genre and a rating keep the first row whole and the second row in its own columns.
    private val FULL_FIRST_ROW = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", rating = 9, players = "1", description = "")

    private var size by mutableStateOf(TextSize.Small)

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun show(pace: BatteryPace?) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 83).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts(pace = pace)) {
                    HomeAt(LibraryLikeContent.of(hasBoxArt = true, metroidDetails = FULL_FIRST_ROW), LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun details(): SemanticsNode = findTagged(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode(), HomeTags.DETAILS)!!

    private fun texts(node: SemanticsNode): List<Pair<String, SemanticsNode>> =
        (if (SemanticsProperties.Text in node.config) listOf(node.config[SemanticsProperties.Text].joinToString("") to node) else emptyList()) +
            node.children.flatMap(::texts)

    @Test
    fun theTimeOnAChargeIsOnScreenAtEverySizeInEveryFont() {
        show(BATTERY_LINE_SAMPLES[1].pace)
        val problems = mutableListOf<String>()
        for (eachFont in listOf(PlumFont.Jost)) {
            for (each in TextSize.entries) {
                size = each
                compose.waitForIdle()
                val tag = "${each.name} ${eachFont.name}"
                val cell = findTagged(details(), HomeTags.DETAILS_BATTERY)
                if (cell == null) {
                    problems += "$tag: no battery cell"
                    continue
                }
                val all = texts(cell).map { it.first }
                // The cell says "About 2 h 45 min"; where the three rows leave no room for the word it says "2 h 45 min" (never in Jost at Medium).
                val time = all.map { it.replace("\u00A0", " ") }
                val mustSayAbout = eachFont == PlumFont.Jost && each == TextSize.Small
                if (!(all.contains("Battery") && ("About 2 h 45 min" in time || (!mustSayAbout && "2 h 45 min" in time)))) problems += "$tag: $all"
                if (all.any { it.contains("\u2026") }) problems += "$tag: cut with an ellipsis $all"
                if (findTagged(details(), "picking_battery_line") != null) problems += "$tag: the old line is still there"
                notFullyShown(cell, compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())?.let { problems += "$tag: the cell $it" }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun withNoEstimateTheLineIsLeftOutAndTheOthersKeepTheirOrder() {
        show(null)
        val block = findTagged(details(), HomeTags.DETAILS_FACTS)!!
        assertNull("a cell with no estimate", findTagged(details(), HomeTags.DETAILS_BATTERY))
        val all = texts(block)
        assertTrue("no BATTERY label", all.none { it.first == "Battery" })
        assertTrue("five labels", all.count { it.first in LABELS } == 5)
        assertTrue("no dash", all.none { it.first.trim() in setOf("-", "\u2013", "\u2014") })
        // Five facts, one to a line in order, at one pitch: the missing one leaves no hole and the others keep their order.
        assertFactLines(block, FACT_ORDER - "Battery")
    }

    @Test
    fun withAnEstimateAllSixFactsStandOnTheirOwnLines() {
        show(BATTERY_LINE_SAMPLES[1].pace)
        val block = findTagged(details(), HomeTags.DETAILS_FACTS)!!
        val all = texts(block)
        // Requirement changed: one fact to a line (was six cells in three rows, with the battery a third cell of the emulator's row).
        val labels = all.filter { it.first in LABELS }
        assertEquals("all six facts", LABELS, labels.map { it.first }.toSet())
        assertFactLines(block, FACT_ORDER)
    }

    private fun assertFactLines(block: SemanticsNode, expected: List<String>) {
        val lines = readFactLines(block)
        assertEquals("the facts on show, in order", expected, lines.map { it.name })
        assertEquals("the contract", emptyList<String>(), factLineProblems(block.boundsInRoot, lines))
    }

    private companion object {
        val LABELS = setOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsBatteryCellTest : HomeDetailsBatteryCellBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsBatteryCell832Test : HomeDetailsBatteryCellBase()
