package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.EmulatorToStart
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

/**
 * The details column's play facts (read once the focus rests, with or without box art) and the
 * text-only column without box art: wide screens with no
 * second-screen window only; Apps never; the narrow screen unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = WIDE_768)
class HomeTextDetailsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val source = FakePlayFacts()
    private var windowUp by mutableStateOf(false)
    private lateinit var state: StripState
    private lateinit var layout: PlumLayout

    private fun show(
        actions: List<Action>,
        content: HomeContent = LibraryLikeContent.of(),
        facts: HomePlayFactsSource = source,
    ) {
        compose.setContent {
            PlumTheme(MediumText.Default) {
                layout = PlumTheme.layout
                CompositionLocalProvider(LocalHomePlayFacts provides facts) {
                    HomeAt(content, actions, onState = { state = it }, detailsOnSecondScreen = { windowUp })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(tag: String): Int = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun text(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, DP_TOLERANCE)

    private fun act(action: Action) {
        compose.runOnIdle { state.dispatch(action) }
        compose.waitForIdle()
    }

    // The text-only column (wide, no box art).

    @Test
    fun wideWithoutBoxArtShowsTitleSystemRegionAndFactsBesideTheList() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
        assertEquals("one details column", 1, count(HomeTags.DETAILS))
        assertEquals("Super Metroid", text(HomeTags.DETAILS_TITLE))
        assertEquals("Super Nintendo · USA", text(HomeTags.DETAILS_SYSTEM))
        assertEquals("the facts table", 1, count(HomeTags.DETAILS_FACTS))
        listOf("Last played", "Play time", "Emulator", "2 days ago", "4 h 12 min", "RetroArch: Snes9x").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertExists()
        }
        assertEquals("no cover box", 0, count(HomeTags.DETAILS_COVER))
        assertEquals("no synopsis", 0, count(HomeTags.DETAILS_SYNOPSIS))
        val list = bounds(HomeTags.GAMES)
        val details = bounds(HomeTags.DETAILS)
        assertDp("the list is width − 368", 768.dp - 368.dp, list.right - list.left)
        assertDp("the column 24 after the list", list.right + 24.dp, details.left)
        assertDp("the column 248 wide", 248.dp, details.right - details.left)
        assertDp("its right edge at the 24 dp margin", 768.dp - 24.dp, details.right)
        assertDp("top aligned where the cover box starts with box art (the strip's top)", bounds(HomeTags.STRIP).top, details.top)
        assertDp("the title at the column's top", details.top, bounds(HomeTags.DETAILS_TITLE).top)
    }

    @Test
    fun anEmptyRegionShowsTheSystemNameAlone() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.TETRIS_ATTACK))
        assertEquals("Super Nintendo", text(HomeTags.DETAILS_SYSTEM))
    }

    @Test
    fun recentShowsTheGamesOwnSystemOnceTheFocusRests() {
        show(listOf(Action.TapItem(Column.Systems, 0), Action.Enter))
        assertEquals("Sonic the Hedgehog", text(HomeTags.DETAILS_TITLE))
        assertEquals("Mega Drive · EUR", text(HomeTags.DETAILS_SYSTEM))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the line's place kept while its system is found", 1, count(HomeTags.DETAILS_SYSTEM_PENDING))
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        assertEquals("Super Nintendo · USA", text(HomeTags.DETAILS_SYSTEM))
    }

    @Test
    fun favoritesShowItsGamesSystemToo() {
        show(listOf(Action.TapItem(Column.Systems, 1), Action.Enter))
        assertEquals("Super Nintendo · USA", text(HomeTags.DETAILS_SYSTEM))
        assertEquals(1, count(HomeTags.DETAILS_FACTS))
    }

    /**
     * Y on a game in Favorites gives the screen new content twice (the shown mark, then the library's
     * data); the system line keeps its name through both and never empties.
     */
    @Test
    fun newContentForTheSameGameKeepsTheSystemLine() {
        var content by mutableStateOf(LibraryLikeContent.of())
        compose.setContent {
            PlumTheme(MediumText.Default) {
                CompositionLocalProvider(LocalHomePlayFacts provides source) {
                    HomeAt(content, listOf(Action.TapItem(Column.Systems, 1), Action.Enter), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        assertEquals("Super Nintendo · USA", text(HomeTags.DETAILS_SYSTEM))
        compose.mainClock.autoAdvance = false
        repeat(2) { round ->
            content = LibraryLikeContent.of(favoriteMarked = round % 2 == 1)
            Snapshot.sendApplyNotifications()
            repeat(FRAMES_WATCHED) { frame ->
                compose.mainClock.advanceTimeByFrame()
                assertEquals("content $round, frame $frame: the line's place never empties", 0, count(HomeTags.DETAILS_SYSTEM_PENDING))
                assertEquals("content $round, frame $frame", "Super Nintendo · USA", text(HomeTags.DETAILS_SYSTEM))
            }
        }
    }

    @Test
    fun appsHasNoColumn() {
        show(listOf(Action.TapItem(Column.Systems, 2), Action.Enter))
        assertEquals(0, count(HomeTags.DETAILS))
    }

    @Test
    fun withTheSecondScreensWindowUpThereIsNoColumnAndTheListTakesTheFullWidth() {
        windowUp = true
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
        assertEquals("no details column", 0, count(HomeTags.DETAILS))
        val list = bounds(HomeTags.GAMES)
        assertDp("the full no-details width, width − 96", 768.dp - 96.dp, list.right - list.left)
    }

    // The play facts read.

    @Test
    fun quickMovesReadOnlyTheGameTheFocusRestsOn() {
        show(LibraryLikeContent.gamesFocused(0))
        compose.runOnIdle { source.asked.clear() }
        compose.mainClock.autoAdvance = false
        repeat(4) {
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            // A held key: a row every 48 ms, under the 150 ms rest.
            compose.mainClock.advanceTimeBy(HELD_KEY_MS)
        }
        assertEquals("nothing read while moving", emptyList<Long>(), source.asked)
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        assertEquals("read once, for the fifth game only", listOf(LibraryLikeContent.idAt(4)), source.asked)
        compose.mainClock.advanceTimeBy(IDLE_MS)
        assertEquals("nothing more while idle", listOf(LibraryLikeContent.idAt(4)), source.asked)
    }

    @Test
    fun theFactsKeepTheirRoomWhileTheyAreRead() {
        show(LibraryLikeContent.gamesFocused(0))
        val facts = bounds(HomeTags.DETAILS_FACTS)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the table's room kept", 1, count(HomeTags.DETAILS_FACTS_PENDING))
        val pending = bounds(HomeTags.DETAILS_FACTS_PENDING)
        assertDp("at the same top", facts.top, pending.top)
        assertDp("the same height", facts.bottom - facts.top, pending.bottom - pending.top)
        compose.mainClock.advanceTimeBy(SETTLED_MS)
        assertEquals("then the table", 1, count(HomeTags.DETAILS_FACTS))
    }

    @Test
    fun aReadThatThrowsShowsTheColumnWithoutFactsAndIsLogged() {
        ShadowLog.clear()
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), facts = FakePlayFacts(fails = true))
        assertEquals("the column", 1, count(HomeTags.DETAILS))
        assertEquals("Super Metroid", text(HomeTags.DETAILS_TITLE))
        assertEquals("no table", 0, count(HomeTags.DETAILS_FACTS))
        assertEquals("no room kept for it", 0, count(HomeTags.DETAILS_FACTS_PENDING))
        val logged = ShadowLog.getLogsForTag(TAG).filter { "could not be read" in it.msg }
        assertEquals("the cause logged once: ${ShadowLog.getLogsForTag(TAG).map { it.msg }}", 1, logged.size)
        assertTrue("with the exception's class", "IllegalStateException" in logged.single().msg)
    }

    /** A closed Room database throws a CancellationException of its own: logged and shown like any failure, not taken for a move away. */
    @Test
    fun aDatabaseCancelThatIsNotOursIsLoggedToo() {
        ShadowLog.clear()
        val closed =
            object : HomePlayFactsSource {
                override suspend fun read(gameId: Long): GamePlay = throw CancellationException("database closed")
            }
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), facts = closed)
        assertEquals("no table", 0, count(HomeTags.DETAILS_FACTS))
        assertEquals("no room kept for it", 0, count(HomeTags.DETAILS_FACTS_PENDING))
        assertEquals("logged", 1, ShadowLog.getLogsForTag(TAG).count { "could not be read" in it.msg })
    }

    @Test
    fun theNoneSourceReadsNothingAndKeepsNoRoom() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), facts = HomePlayFactsSource.None)
        assertEquals(0, count(HomeTags.DETAILS_FACTS))
        assertEquals(0, count(HomeTags.DETAILS_FACTS_PENDING))
    }

    // With box art: the same read fills the box-art column's table.

    @Test
    fun theBoxArtColumnShowsTheReadFacts() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), LibraryLikeContent.of(hasBoxArt = true))
        assertEquals(1, count(HomeTags.DETAILS_COVER))
        compose.onNodeWithText("4 h 12 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("RetroArch: Snes9x", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aRowsOwnPlayFactsWinAndNothingIsRead() {
        val own = PlayFacts(lastPlayedDaysAgo = 0, playTimeMinutes = 30, emulator = "Bsnes")
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = "Action", players = "1", description = "", play = own)
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details))
        compose.onNodeWithText("Bsnes", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Today", useUnmergedTree = true).assertExists()
        assertEquals("nothing read for it", emptyList<Long>(), source.asked.filter { it == LibraryLikeContent.idAt(LibraryLikeContent.SUPER_METROID) })
    }

    @Test
    @Config(qualifiers = NARROW_576)
    fun theNarrowDetailsStopWithBoxArtShowsTheReadFacts() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID), LibraryLikeContent.of(hasBoxArt = true))
        act(Action.Move(Direction.Right))
        assertEquals(Column.Details, state.focus.column)
        compose.onNodeWithText("2 days ago", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("RetroArch: Snes9x", useUnmergedTree = true).assertExists()
    }

    // An empty library: without box art its Apps list keeps the full width (as before the text-only
    // column), with box art it stays width − 368 as on HEAD.

    @Test
    fun anEmptyLibraryWithoutBoxArtKeepsItsAppsListFullWidth() = assertEmptyLibraryAppsWidth(hasBoxArt = false, 768.dp - 96.dp)

    @Test
    fun anEmptyLibraryWithBoxArtKeepsItsAppsListBesideTheDetails() = assertEmptyLibraryAppsWidth(hasBoxArt = true, 768.dp - 368.dp)

    @Test
    @Config(qualifiers = NARROW_576)
    fun anEmptyLibraryWithoutBoxArtOnANarrowScreen() = assertEmptyLibraryAppsWidth(hasBoxArt = false, 576.dp - 96.dp)

    @Test
    @Config(qualifiers = NARROW_576)
    fun anEmptyLibraryWithBoxArtOnANarrowScreen() = assertEmptyLibraryAppsWidth(hasBoxArt = true, 400.dp)

    private fun assertEmptyLibraryAppsWidth(
        hasBoxArt: Boolean,
        expected: Dp,
    ) {
        val empty = HomeContent.of(emptyList(), emptyList(), List(APPS) { GameRowModel("app-$it", "App $it", "") }, emptyList(), hasBoxArt = hasBoxArt)
        show(listOf(Action.TapItem(Column.Systems, 2), Action.Enter), empty)
        val list = bounds(HomeTags.GAMES)
        assertDp("the Apps list's width (box art: $hasBoxArt)", expected, list.right - list.left)
        assertEquals("no details column", 0, count(HomeTags.DETAILS))
    }

    // Narrow without box art: unchanged.

    @Test
    @Config(qualifiers = NARROW_576)
    fun narrowWithoutBoxArtHasNoColumnAndRightDoesNothing() {
        show(LibraryLikeContent.gamesFocused(LibraryLikeContent.SUPER_METROID))
        assertEquals("no details column", 0, count(HomeTags.DETAILS))
        val list = bounds(HomeTags.GAMES)
        assertDp("the list unchanged, width − 96", 576.dp - 96.dp, list.right - list.left)
        val before = state.focus
        act(Action.Move(Direction.Right))
        assertEquals("Right does nothing new: no details stop", before, state.focus)
        assertEquals(0, count(HomeTags.DETAILS))
        assertEquals("no read without a column", emptyList<Long>(), source.asked)
    }
}

/** Counts its reads; [fails]: every read throws, as a closed database does. */
internal class FakePlayFacts(
    private val fails: Boolean = false,
    private val pace: com.plumemu.battery.BatteryPace? = null,
) : HomePlayFactsSource {
    val asked = mutableListOf<Long>()

    override suspend fun read(gameId: Long): GamePlay {
        asked += gameId
        if (fails) throw IllegalStateException("closed")
        return GamePlay(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = EmulatorToStart("RetroArch: Snes9x", isInstalled = true), batteryPace = pace)
    }
}

/** Content shaped like the library's: numeric ids, Recent and Favorites repeating the systems' games. */
internal object LibraryLikeContent {
    const val SUPER_METROID = 1
    const val TETRIS_ATTACK = 2
    const val SNES_INDEX = 3

    private val snesTitles =
        listOf(
            "Chrono Trigger" to "USA", "Super Metroid" to "USA", "Tetris Attack" to "", "EarthBound" to "USA",
            "Mega Man X" to "USA", "Yoshi's Island" to "USA", "Super Mario World" to "EUR", "Final Fantasy VI" to "JPN",
        )

    fun idAt(row: Int): Long = 101L + row

    /** [favoriteMarked]: the Favorites row as the shown mark makes it after Y (a new content, the same game). */
    fun of(
        hasBoxArt: Boolean = false,
        metroidDetails: GameDetails? = null,
        favoriteMarked: Boolean = true,
    ): HomeContent {
        val snes =
            snesTitles.mapIndexed { i, (title, region) ->
                GameRowModel(idAt(i).toString(), title, region, systemAbbr = "SNES", details = if (i == SUPER_METROID) metroidDetails else null)
            }
        val sonic = GameRowModel("201", "Sonic the Hedgehog", "EUR", systemAbbr = "MD")
        return HomeContent.of(
            recent = listOf(sonic, snes[SUPER_METROID]),
            favorites = listOf(snes[SUPER_METROID].copy(isFavorite = favoriteMarked)),
            apps = listOf(GameRowModel("app-0", "Browser", "")),
            systems = listOf(SystemGames("snes", "Super Nintendo", snes), SystemGames("md", "Mega Drive", listOf(sonic))),
            hasBoxArt = hasBoxArt,
        )
    }

    /** Super Nintendo's games with [row] focused. */
    fun gamesFocused(row: Int): List<Action> = listOf(Action.TapItem(Column.Systems, SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, row))
}

private const val WIDE_768 = "w768dp-h432dp-land-xhdpi"
private const val NARROW_576 = "w576dp-h432dp-land-xhdpi"
private const val HELD_KEY_MS = 48L
private const val SETTLED_MS = 400L
private const val IDLE_MS = 10_000L

/** Frames watched after new content: past the 150 ms settle at 60 Hz. */
private const val FRAMES_WATCHED = 15
private const val APPS = 3
private const val DP_TOLERANCE = 0.5f
