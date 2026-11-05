package com.plumemu.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide details column holds its places: once a game is focused nothing in it moves or resizes when its late data
 * arrives (art details, rating and synopsis, play facts); values only appear in room already held.
 */
abstract class HomeDetailsHoldBase(private val narrow: Boolean = false) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** What the column shows at one moment, in dp. */
    private class Snap(val cover: Rect?, val title: Rect?, val subtitle: Rect?, val facts: Rect?, val synopsis: Rect?, val factTexts: Map<String, Rect>)

    private fun bounds(tag: String): Rect? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { dp(it.boundsInRoot) }

    private fun dp(r: androidx.compose.ui.geometry.Rect): Rect {
        val d = compose.density.density
        return Rect(r.left / d, r.top / d, r.right / d, r.bottom / d)
    }

    private fun textsUnder(node: SemanticsNode): List<Pair<String, Rect>> {
        val own = if (SemanticsProperties.Text in node.config) listOf(node.config[SemanticsProperties.Text].joinToString() to dp(node.boundsInRoot)) else emptyList()
        return own + node.children.flatMap { textsUnder(it) }
    }

    private fun snap(): Snap {
        val factsNode =
            (compose.onAllNodesWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).fetchSemanticsNodes() + compose.onAllNodesWithTag(HomeTags.DETAILS_FACTS_PENDING, useUnmergedTree = true).fetchSemanticsNodes()).firstOrNull()
        val texts = factsNode?.let { n -> textsUnder(n).filter { it.first.isNotEmpty() }.toMap() }.orEmpty()
        return Snap(bounds(HomeTags.DETAILS_COVER), bounds(HomeTags.DETAILS_TITLE), bounds(HomeTags.DETAILS_SUBTITLE), factsNode?.let { dp(it.boundsInRoot) }, bounds(HomeTags.DETAILS_SYNOPSIS), texts)
    }

    private fun same(
        what: String,
        a: Rect?,
        b: Rect?,
    ) {
        assertNotNull("$what exists in the first state", a)
        assertNotNull("$what exists in the second state", b)
        assertEquals("$what left", a!!.left, b!!.left, 0.5f)
        assertEquals("$what top", a.top, b.top, 0.5f)
        assertEquals("$what width", a.width, b.width, 0.5f)
        assertEquals("$what height", a.height, b.height, 0.5f)
    }

    /** Where the block starts and how high it is: the narrow stop's table is as wide as its words, and an empty one has no width. */
    private fun sameRows(
        what: String,
        a: Rect?,
        b: Rect?,
    ) {
        assertNotNull("$what exists in the first state", a)
        assertNotNull("$what exists in the second state", b)
        assertEquals("$what top", a!!.top, b!!.top, 0.5f)
        assertEquals("$what height", a.height, b.height, 0.5f)
    }

    @Test
    fun nothingMovesWhenLateDataArrivesOrIsMissing() {
        val base = FakeHomeContent.withArtwork()
        val numbered = base.withEntries(base.entries.mapIndexed { e, entry -> entry.copy(rows = entry.rows.mapIndexed { r, row -> row.copy(id = "${e * 1000 + r + 1}") }) })
        val rows = numbered.entries[FakeHomeContent.BOARD_SNES_INDEX].rows
        val (full, noFacts, absent) = Triple(rows[FakeHomeContent.BOARD_REMEMBERED_ROW], rows[FakeHomeContent.BOARD_REMEMBERED_ROW + 1], rows[FakeHomeContent.BOARD_REMEMBERED_ROW - 1])
        val synopsis = SUPER_METROID_SYNOPSIS
        val source =
            coverArtSource(
                absent = setOf(absent.title),
                synopses = listOf(full, noFacts).associate { artKey(it.id) to synopsis },
                ratings = listOf(full, noFacts).associate { artKey(it.id) to SUPER_METROID_RATING },
                content = numbered,
            )
        val plays = MapPlayFacts(mapOf(full.id.toLong() to GamePlay(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = com.plumemu.game.EmulatorToStart("RetroArch: Snes9x", isInstalled = true))))
        lateinit var state: StripState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme {
                CompositionLocalProvider(LocalHomePlayFacts provides plays) {
                    HomeWithArt(source, if (narrow) BoardGamesFocused + Action.Move(Direction.Right) else BoardGamesFocused, numbered.asLibrary(), onState = { state = it })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        // Frame by frame from the first one of the full game to well after its reads: every fact text stays where it first stood.
        val frames = mutableListOf(snap())
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            frames += snap()
        }
        val first = frames.first()
        val settled = frames.last()
        assertTrue("the facts arrived", settled.factTexts.values.size >= 3)
        frames.forEach { f ->
            f.factTexts.forEach { (text, box) -> settled.factTexts[text]?.let { same("fact '$text' while it arrived", box, it) } }
        }
        val firstWithValues = frames.first { it.factTexts.size > 1 }
        assertTrue("the values come all at once, not the genre alone: ${firstWithValues.factTexts.keys}", firstWithValues.factTexts.keys.containsAll(settled.factTexts.keys.filter { it != "RATING" && !it.startsWith("8") }))
        same("cover", first.cover, settled.cover)
        same("title", first.title, settled.title)
        if (!narrow) sameRows("subtitle line", first.subtitle, settled.subtitle)
        (if (narrow) ::sameRows else ::same)("facts block", first.facts, settled.facts)
        // A game whose facts read comes back empty, and one with no result at all, keep the same places.
        fun settle(to: Direction) {
            compose.runOnIdle { state.dispatch(Action.Move(to)) }
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.waitForIdle()
        }
        settle(Direction.Down)
        val empty = snap()
        settle(Direction.Up)
        settle(Direction.Up)
        val none = snap()
        for ((name, s) in listOf("empty facts" to empty, "no result" to none)) {
            if (!narrow) sameRows("subtitle line ($name)", settled.subtitle, s.subtitle)
            same("cover ($name)", settled.cover, s.cover)
            // Another game's title is as wide as its own words: only where it starts and how high it is hold.
            assertEquals("title top ($name)", settled.title!!.top, s.title!!.top, 0.5f)
            assertEquals("title height ($name)", settled.title.height, s.title.height, 0.5f)
            (if (narrow) ::sameRows else ::same)("facts block ($name)", settled.facts, s.facts)
        }
        // The synopsis stands under the held block: where there is room for it (not at every size) it starts at the same place with and without facts.
        if (settled.synopsis != null && empty.synopsis != null) same("synopsis with and without facts", settled.synopsis, empty.synopsis)
    }

    private companion object {
        const val FRAMES = 40
        const val SETTLE_MS = 800L
    }
}

/** Plays by game id; a missing id reads nothing (the game is unknown to the library). */
internal class MapPlayFacts(private val byId: Map<Long, GamePlay?>) : HomePlayFactsSource {
    override suspend fun read(gameId: Long): GamePlay? = byId[gameId]
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsHold768Test : HomeDetailsHoldBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeDetailsHold832Test : HomeDetailsHoldBase()

/** The narrow details stop (third strip stop) at 640 x 480: the same rule, with its fact lines and play facts held. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeDetailsHoldNarrowTest : HomeDetailsHoldBase(narrow = true)
