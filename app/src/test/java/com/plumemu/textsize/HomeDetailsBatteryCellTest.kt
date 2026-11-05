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
import com.plumemu.home.FakePlayFacts
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeAt
import com.plumemu.home.HomeTags
import com.plumemu.home.LibraryLikeContent
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.home.appComposeRule
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
 * The wide details block's second row, LAST PLAYED | EMULATOR | BATTERY, at 768 x 432 and 832 x 468: the battery cell shows the time on a
 * charge at all six text sizes in every font, whole (never left out, never cut); with no estimate the cell is left out and the other two keep their columns; the old line under
 * the block is gone.
 */
abstract class HomeDetailsBatteryCellBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    // A cell with nothing to say takes no room, so a genre and a rating keep the first row whole and the second row in its own columns.
    private val FULL_FIRST_ROW = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", rating = 9, players = "1", description = "")

    private var size by mutableStateOf(TextSize.Small)
    private var font by mutableStateOf(PlumFont.Sans)

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun show(pace: BatteryPace?) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 83).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
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
        for (eachFont in PlumFont.entries) {
            for (each in TextSize.entries) {
                font = eachFont
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
                if (!(all.contains("BATTERY") && ("About 2 h 45 min" in time || (!mustSayAbout && "2 h 45 min" in time)))) problems += "$tag: $all"
                if (all.any { it.contains("\u2026") }) problems += "$tag: cut with an ellipsis $all"
                if (findTagged(details(), "picking_battery_line") != null) problems += "$tag: the old line is still there"
                notFullyShown(cell, compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())?.let { problems += "$tag: the cell $it" }
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun withNoEstimateTheCellIsLeftOutAndTheOthersKeepTheirColumns() {
        show(null)
        val block = findTagged(details(), HomeTags.DETAILS_FACTS)!!
        assertNull("a cell with no estimate", findTagged(details(), HomeTags.DETAILS_BATTERY))
        val all = texts(block)
        assertTrue("no BATTERY label", all.none { it.first == "BATTERY" })
        assertTrue("five labels", all.count { it.first in LABELS } == 5)
        assertTrue("no dash", all.none { it.first.trim() in setOf("-", "\u2013", "\u2014") })
        // Five cells in the rows the arrangement gives them (HomeDetailsShares.kt): every row starts at the box's left edge.
        assertRowsStartAtTheLeft(block, all.filter { it.first in LABELS })
    }

    @Test
    fun withAnEstimateTheThreeCellsStandInThreeColumns() {
        show(BATTERY_LINE_SAMPLES[1].pace)
        val block = findTagged(details(), HomeTags.DETAILS_FACTS)!!
        val all = texts(block)
        // Requirement changed: six cells in three rows (genre + rating, played + last played, emulator + battery; a long emulator name on a row of
        // its own and the battery a third cell of another row).
        val labels = all.filter { it.first in LABELS }
        assertEquals("all six cells", LABELS, labels.map { it.first }.toSet())
        assertRowsStartAtTheLeft(block, labels)
    }

    private fun assertRowsStartAtTheLeft(block: SemanticsNode, labels: List<Pair<String, SemanticsNode>>) {
        val rows = labels.groupBy { Math.round(it.second.boundsInRoot.top) }
        assertTrue("at most three rows: ${rows.mapValues { e -> e.value.map { it.first } }}", rows.size <= 3)
        rows.forEach { (top, row) -> assertEquals("the row at $top starts at the left edge", block.boundsInRoot.left, row.minOf { it.second.boundsInRoot.left }, 1f) }
    }

    private companion object {
        val LABELS = setOf("GENRE", "RATING", "PLAYED", "LAST PLAYED", "EMULATOR", "BATTERY")
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
