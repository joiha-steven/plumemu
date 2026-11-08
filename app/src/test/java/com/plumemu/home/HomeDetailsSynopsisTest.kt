package com.plumemu.home

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.battery.BATTERY_LINE_SAMPLES
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
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
 * The synopsis of the details column at Medium in Jost, frame by frame: it fills what is left below the facts box (wide) or under the
 * cover (narrow) when the facts are revealed, and nothing above or beside it moves then or later; a game without one leaves that
 * space empty; a game with only two rows of facts has the same box. Prints the cover and the synopsis lines per size for the work log.
 */
abstract class HomeDetailsSynopsisBase(
    private val narrow: Boolean,
    /** The synopsis lines this size must show for a game with a synopsis (a column that has no room for one must show none). */
    private val lines: IntRange,
    private val coverHeight: Float? = null,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Plays(private val play: GamePlay) : HomePlayFactsSource {
        override suspend fun read(gameId: Long) = play
    }

    private class Snap(val boxes: Map<String, Rect>, val lines: Int)

    private fun snap(): Snap {
        val d = compose.density.density
        val boxes = mutableMapOf<String, Rect>()
        for (tag in listOf(HomeTags.DETAILS_COVER, HomeTags.DETAILS_TITLE, HomeTags.DETAILS_SYNOPSIS, HomeTags.DETAILS_FACTS, HomeTags.DETAILS_FACTS_PENDING)) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
                val r = n.boundsInRoot
                boxes[tag] = Rect(r.left / d, r.top / d, r.right / d, r.bottom / d)
            }
        }
        val node = compose.onAllNodesWithTag(HomeTags.DETAILS_SYNOPSIS, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        val lines = node?.let { n -> mutableListOf<TextLayoutResult>().also { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull()?.lineCount } ?: 0
        return Snap(boxes, lines)
    }

    private fun run(
        withSynopsis: Boolean,
        twoRows: Boolean,
    ): List<Snap> {
        val metroid = LibraryLikeContent.SUPER_METROID
        val id = LibraryLikeContent.idAt(metroid).toString()
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = if (twoRows) "" else "Action", players = "1", description = if (withSynopsis) SUPER_METROID_SYNOPSIS else "", rating = if (twoRows) null else SUPER_METROID_RATING)
        val base = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details)
        // The synopsis is the game's own description: the details of a row are read before any scrape result.
        val source = coverArtSource(content = base, ratings = if (twoRows) emptyMap() else mapOf(artKey(id) to SUPER_METROID_RATING))
        if (!twoRows) {
            @Suppress("DEPRECATION")
            RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, BATTERY_LINE_SAMPLES[1].level).putExtra(BatteryManager.EXTRA_SCALE, 100))
        }
        val play = GamePlay(2, 252, EmulatorToStart("RetroArch: Snes9x", isInstalled = true).takeIf { !twoRows }, batteryPace = BATTERY_LINE_SAMPLES[1].pace.takeIf { !twoRows })
        val actions = LibraryLikeContent.gamesFocused(metroid) + if (narrow) listOf(Action.Move(Direction.Right)) else emptyList()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Small)) {
                CompositionLocalProvider(LocalHomePlayFacts provides Plays(play)) { HomeWithArt(source, actions, base) }
            }
        }
        val frames = mutableListOf<Snap>()
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            frames += snap()
        }
        if (!twoRows) {
            // The battery level changes while the game rests: the time in its line changes and nothing moves.
            @Suppress("DEPRECATION")
            RuntimeEnvironment.getApplication().sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 40).putExtra(BatteryManager.EXTRA_SCALE, 100))
            repeat(LEVEL_FRAMES) {
                compose.mainClock.advanceTimeByFrame()
                frames += snap()
            }
        }
        val last = frames.last()
        val d = compose.density.density
        val cover = last.boxes.getValue(HomeTags.DETAILS_COVER)
        println("TABLE ${RuntimeEnvironment.getQualifiers().substringBefore("-normal")} synopsis=$withSynopsis twoRows=$twoRows cover ${cover.width} x ${cover.height} dp; synopsis lines ${last.lines}; d=$d")
        return frames
    }

    private fun assertNothingAboveMoves(frames: List<Snap>) {
        // The synopsis may appear or fill, but the cover, the title and (wide) the facts box stay where the first frame put them.
        val tags = listOf(HomeTags.DETAILS_COVER, HomeTags.DETAILS_TITLE) + if (narrow) emptyList() else listOf(HomeTags.DETAILS_FACTS_PENDING)
        for (tag in tags) {
            val first = frames.first().boxes.getValue(tag)
            frames.forEachIndexed { n, f ->
                val now = f.boxes[tag] ?: f.boxes[HomeTags.DETAILS_FACTS]
                assertTrue("$tag exists in frame $n", now != null)
                assertEquals("$tag top in frame $n", first.top, now!!.top, TOL)
                assertEquals("$tag height in frame $n", first.height, now.height, TOL)
            }
        }
        val synopses = frames.mapNotNull { it.boxes[HomeTags.DETAILS_SYNOPSIS] }
        synopses.forEach { assertEquals("the synopsis does not move once shown", synopses.last().top, it.top, TOL) }
    }

    @Test
    fun aSynopsisFillsTheRoomAtTheRevealAndMovesNothing() {
        val frames = run(withSynopsis = true, twoRows = false)
        assertNothingAboveMoves(frames)
        val shownAt = frames.indexOfFirst { HomeTags.DETAILS_SYNOPSIS in it.boxes }
        val revealedAt = frames.indexOfFirst { HomeTags.DETAILS_FACTS in it.boxes }
        assertTrue("synopsis lines ${frames.last().lines} not in $lines", frames.last().lines in lines)
        if (lines.first > 0) {
            assertTrue("the synopsis is shown", shownAt >= 0)
            assertTrue("the synopsis comes with the facts, not before (synopsis $shownAt, facts $revealedAt)", shownAt >= revealedAt || narrow)
        } else {
            assertEquals("this size shows no synopsis in its column", -1, shownAt)
        }
    }

    @Test
    fun aGameWithoutASynopsisLeavesTheRoomEmpty() {
        val frames = run(withSynopsis = false, twoRows = false)
        assertNothingAboveMoves(frames)
        assertTrue("no synopsis", frames.none { HomeTags.DETAILS_SYNOPSIS in it.boxes })
        // Where a column has room for a synopsis, a game with one shows lines and a game without shows none.
        assertEquals("lines without a synopsis", 0, frames.last().lines)
    }

    @Test
    fun aGameWithTwoRowsOfFactsHasTheSameCover() {
        val two = run(withSynopsis = true, twoRows = true)
        assertNothingAboveMoves(two)
        // The cover is sized from the held box (three rows), never from the rows a game happens to have.
        if (coverHeight != null) assertEquals("the cover of a two-row game", coverHeight, two.last().boxes.getValue(HomeTags.DETAILS_COVER).height, TOL)
    }

    private companion object {
        const val FRAMES = 90
        const val LEVEL_FRAMES = 30
        const val TOL = 0.5f
    }
}

/** On the narrow details screen (the games stop) the synopsis shows under the cover. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeDetailsSynopsis576Test : HomeDetailsSynopsisBase(narrow = true, lines = 1..Int.MAX_VALUE)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsSynopsis640Test : HomeDetailsSynopsisBase(narrow = true, lines = 1..Int.MAX_VALUE)

/** The wide column of 768, 832 and the Thor has no room under the box for a synopsis line: it shows none (the cover keeps its height). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsSynopsis768Test : HomeDetailsSynopsisBase(narrow = false, lines = 0..0, coverHeight = 140f) // 144 before the facts' labels grew from 11 to 13: the cover is at its 140 floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsSynopsis832Test : HomeDetailsSynopsisBase(narrow = false, lines = 0..0, coverHeight = 171f) // 178 before the facts' labels grew from 11 to 13 (a taller block, re-measured)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsSynopsisThorTest : HomeDetailsSynopsisBase(narrow = false, lines = 0..0, coverHeight = 171.34f) // 177.87 before the facts' labels grew from 11 to 13 (re-measured)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w960dp-h540dp-land-xhdpi")
class HomeDetailsSynopsis960Test : HomeDetailsSynopsisBase(narrow = false, lines = 2..Int.MAX_VALUE)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1280dp-h720dp-land-xhdpi")
class HomeDetailsSynopsis1280Test : HomeDetailsSynopsisBase(narrow = false, lines = 5..Int.MAX_VALUE)
