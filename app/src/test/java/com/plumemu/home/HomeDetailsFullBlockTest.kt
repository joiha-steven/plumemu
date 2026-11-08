package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorToStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The settled facts box of the wide details column at Medium in Jost: a game with every fact shows every cell, placed inside the box
 * (coordinates measured, not the existence of a node); a cell with nothing to say takes no place (no hole); and an emulator's name is
 * never cut. Prints the cover's size (dp) for the work log.
 */
private const val LONG_TITLE = "The Legend of Zelda: A Link to the Past Four Swords Anniversary Edition"

abstract class HomeDetailsFullBlockBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Plays(private val play: GamePlay) : HomePlayFactsSource {
        override suspend fun read(gameId: Long) = play
    }

    private class Cell(val label: String, val value: String, val labelBox: Rect, val valueBox: Rect, val ellipsized: Boolean)

    private class Shown(val box: Rect, val cover: Rect, val cells: Map<String, Cell>, val titleLines: Int)

    private fun battery(level: Int) {
        @Suppress("DEPRECATION")
        RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, 100))
    }

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun textOf(n: SemanticsNode) = n.config[SemanticsProperties.Text].joinToString()

    private fun ellipsized(n: SemanticsNode): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val l = layouts.firstOrNull() ?: return false
        return (0 until l.lineCount).any { l.isLineEllipsized(it) }
    }

    /** The settled box of a game with [genre] and [play]: each label with the value under it, by label. */
    private fun settle(
        play: GamePlay,
        genre: String = "Action",
        rating: Boolean = true,
        pace: Boolean = true,
        font: PlumFont = PlumFont.Jost,
        title: String = "Super Metroid",
    ): Shown {
        val metroid = LibraryLikeContent.SUPER_METROID
        val id = LibraryLikeContent.idAt(metroid).toString()
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = genre, players = "1", description = "", rating = if (rating) SUPER_METROID_RATING else null)
        val base = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details, metroidTitle = title)
        val source = coverArtSource(content = base, synopses = mapOf(artKey(id) to SUPER_METROID_SYNOPSIS), ratings = if (rating) mapOf(artKey(id) to SUPER_METROID_RATING) else emptyMap())
        if (pace) battery(BATTERY_LINE_SAMPLES[1].level)
        val withPace = if (pace) play.copy(batteryPace = BATTERY_LINE_SAMPLES[1].pace) else play
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                CompositionLocalProvider(LocalHomePlayFacts provides Plays(withPace)) { HomeWithArt(source, LibraryLikeContent.gamesFocused(metroid), base) }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        fun node(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
        val facts = node(HomeTags.DETAILS_FACTS)
        val all = texts(facts).filter { it.boundsInRoot.width > 0f }
        val labels = setOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery")
        val cells = mutableMapOf<String, Cell>()
        println("FACTS box ${facts.boundsInRoot} " + all.joinToString { "'${textOf(it)}' ${it.boundsInRoot}" })
        // The rating cell is spoken as one "Rating, 8.5/10" (its texts are hidden from the tree), so it is found by that description.
        fun rating(n: SemanticsNode): SemanticsNode? = n.takeIf { SemanticsProperties.ContentDescription in it.config && it.config[SemanticsProperties.ContentDescription].joinToString().startsWith("Rating") } ?: n.children.firstNotNullOfOrNull(::rating)
        rating(facts)?.let { cells["Rating"] = Cell("Rating", it.config[SemanticsProperties.ContentDescription].joinToString(), it.boundsInRoot, it.boundsInRoot, false) }
        all.forEach { label ->
            val l = textOf(label)
            if (l in labels) {
                // The value is the text right under the label, in its column.
                val value = all.filter { textOf(it) !in labels && it.boundsInRoot.top >= label.boundsInRoot.bottom - 1f && it.boundsInRoot.left >= label.boundsInRoot.left - 1f && it.boundsInRoot.top - label.boundsInRoot.bottom < 20f }.minByOrNull { it.boundsInRoot.top * 10000f + (it.boundsInRoot.left - label.boundsInRoot.left) }
                cells[l] = Cell(l, value?.let(::textOf).orEmpty(), label.boundsInRoot, value?.boundsInRoot ?: Rect.Zero, value?.let(::ellipsized) ?: false)
            }
        }
        val d = compose.density.density
        val cover = node(HomeTags.DETAILS_COVER).boundsInRoot
        val synopsis = compose.onAllNodesWithTag(HomeTags.DETAILS_SYNOPSIS, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        val synopsisLines = synopsis?.let { n -> mutableListOf<TextLayoutResult>().also { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull()?.lineCount } ?: 0
        val title = node(HomeTags.DETAILS_TITLE).boundsInRoot
        println("TABLE ${RuntimeEnvironment.getQualifiers()} $font cover ${cover.width / d} x ${cover.height / d}; title top ${title.top / d}; facts box ${facts.boundsInRoot.height / d}; synopsis lines $synopsisLines; column ${node(HomeTags.DETAILS).boundsInRoot.let { "${it.top / d}..${it.bottom / d}" }}; title bottom ${title.bottom / d}; facts ${facts.boundsInRoot.top / d}..${facts.boundsInRoot.bottom / d}; synopsis ${synopsis?.boundsInRoot}")
        val titleLines = node(HomeTags.DETAILS_TITLE).let { n -> mutableListOf<TextLayoutResult>().also { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull()?.lineCount ?: 0 }
        println("COVER ${RuntimeEnvironment.getQualifiers()} title lines $titleLines cover ${cover.width / d} x ${cover.height / d} dp")
        return Shown(facts.boundsInRoot, cover, cells, titleLines)
    }

    private fun assertInside(shown: Shown) {
        shown.cells.values.forEach {
            assertTrue("${it.label} is placed: label ${it.labelBox} value '${it.value}' ${it.valueBox}", it.labelBox.width > 0f && it.valueBox.width > 0f && it.value.isNotEmpty())
            assertTrue("${it.label} and its value '${it.value}' lie inside the box ${shown.box}: ${it.labelBox} ${it.valueBox}", it.labelBox.top >= shown.box.top && it.valueBox.bottom <= shown.box.bottom + 1f && it.valueBox.right <= shown.box.right + 1f && it.labelBox.left >= shown.box.left)
        }
    }

    @Test
    fun everyFactShowsInsideTheBox() {
        val shown = settle(GamePlay(2, 252, EmulatorToStart("RetroArch: Snes9x", isInstalled = true)))
        assertEquals(setOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery"), shown.cells.keys)
        assertInside(shown)
        assertTrue("a cover remains: ${shown.cover}", shown.cover.height > 0f)
    }

    private fun emulatorCase(name: String) {
        val shown = settle(GamePlay(2, 252, EmulatorToStart(name, isInstalled = true)))
        val cell = shown.cells.getValue("Emulator")
        assertTrue("'$name' is not ellipsised: '${cell.value}'", !cell.ellipsized)
        assertTrue("the battery cell is there too: ${shown.cells.keys}", "Battery" in shown.cells)
        assertInside(shown)
    }

    @Test
    fun genesisPlusGxIsNotCut() = emulatorCase("RetroArch: Genesis Plus GX")

    @Test
    fun mupen64PlusNextIsNotCut() = emulatorCase("RetroArch: Mupen64Plus-Next")

    @Test
    fun askedOnPlayIsNotCut() = emulatorCase("Asked on Play")

    private fun twoLineTitle(emulator: String, genre: String = "Action") {
        val shown = settle(GamePlay(2, 252, EmulatorToStart(emulator, isInstalled = true)), genre = genre, title = LONG_TITLE)
        assertEquals("the title takes its two lines", 2, shown.titleLines)
        assertEquals(setOf("Genre", "Rating", "Played", "Last played", "Emulator", "Battery"), shown.cells.keys)
        assertInside(shown)
        assertTrue("a cover remains: ${shown.cover}", shown.cover.height > 0f)
    }

    // The title is known in the first frame; under two lines the cover gives up part of its floor so no row of the box is dropped.
    @Test
    fun aTwoLineTitleKeepsEveryCellWithPicodrive() = twoLineTitle("Picodrive")

    @Test
    fun aTwoLineTitleKeepsEveryCellWithALongEmulatorAndGenre() = twoLineTitle("RetroArch: Beetle PSX HW (not installed)", "Role-Playing Game / Action RPG")

    @Test
    fun cellsWithNothingToSayLeaveNoHole() {
        val shown = settle(GamePlay(2, 252, null), genre = "", rating = false, pace = false)
        // The emulator always has a value ("Asked on Play" with none installed); it goes under the two, on the second row.
        assertEquals(setOf("Played", "Last played", "Emulator"), shown.cells.keys)
        val played = shown.cells.getValue("Played")
        val last = shown.cells.getValue("Last played")
        assertEquals("PLAYED starts the first row", shown.box.left, played.labelBox.left, 1f)
        assertTrue("PLAYED is on the first row: ${played.labelBox} in ${shown.box}", played.labelBox.top - shown.box.top < played.labelBox.height * 2)
        assertEquals("LAST PLAYED shares the first row", played.labelBox.top, last.labelBox.top, 1f)
        assertTrue("LAST PLAYED follows PLAYED", last.labelBox.left > played.labelBox.left)
        // Every row starts at the box's left edge: no cell stands alone further right with nothing before it.
        shown.cells.values.groupBy { it.labelBox.top }.forEach { (top, row) -> assertEquals("the row at $top starts at the left edge", shown.box.left, row.minOf { it.labelBox.left }, 1f) }
        assertInside(shown)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsFullBlock768Test : HomeDetailsFullBlockBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsFullBlock832Test : HomeDetailsFullBlockBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsFullBlockThorTest : HomeDetailsFullBlockBase()
