package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.TextSize
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
 * A focus move recomposes only the rows whose look changed: the row that loses the
 * focus and the row that gains it. Counted with Compose's composition tracer, which sees every
 * composable body that actually runs (skipped ones do not report).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<String>()

    /** Each call with the test clock's time when it ran: the frame it ran in. */
    private val callTimes = mutableListOf<Pair<String, Long>>()

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
                    callTimes += info to compose.mainClock.currentTime
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun count(function: String): Int = calls.count { it.startsWith("com.plumemu.coreui.components.$function ") }

    /** The games list's item body ([GamesListItem]): it reads the row's look, so it runs when that changes. */
    private fun listItems(): Int = calls.count { it.startsWith("com.plumemu.home.GamesListItem ") }

    /** The letter rail recomposes at most the two letters whose look changed, never the whole rail. */
    private fun assertRailLettersAtMostTwo() = assertTrue("rail letters recomposed: ${calls.filter { "RailLetter" in it }}", railLetterRuns() <= 2)

    private fun railLetterRuns(): Int = calls.count { it.startsWith("com.plumemu.home.RailLetter ") }

    /**
     * Screen composables (`com.plumemu.home`) and shared components (TopBar, Hint, …) that ran,
     * except the rows, the games list's items that
     * draw them and the pages' look followers (counted on their own). Theme getters called by a recomposed row are not
     * counted. Nor are the two scroll keepers, which emit nothing: they decide
     * the scroll while composing the move, so it lands in the same frame (a coroutine scrolled
     * one frame late). Nor is the details column ([detailsParts]): it shows the focused game, so a
     * move re-runs it; the tests that move beside it check it on its own.
     */
    private fun othersThatRan(): List<String> {
        assertRailLettersAtMostTwo()
        val rowParts = listOf("GameRow", "SystemRow").map { "com.plumemu.coreui.components.$it " }
        // RailLetter: a move to another letter changes the look of the two letters involved (`assertRailLettersAtMostTwo`).
        val ours = listOf("KeepFocusedVisible", "KeepFocusedCellVisible", "GamesListItem", "FollowRowForms", "RailLetter").map { "com.plumemu.home.$it " }
        // A row's press look (`pressFeel` and its driver) runs with the row and for nobody else: the two game
        // rows of a move may each run it once, any more is an "other".
        val pressAllowance = mutableMapOf<String, Int>().withDefault { 2 }
        return calls
            .filter { it.startsWith("com.plumemu.home.") || it.startsWith("com.plumemu.battery.") || it.startsWith("com.plumemu.coreui.components.") }
            .filterNot { info ->
                val isPress = info.startsWith("com.plumemu.coreui.components.pressFeel.") || info.startsWith("com.plumemu.coreui.components.pressFeelWith.")
                isPress && pressAllowance.getValue(info) > 0 && true.also { pressAllowance[info] = pressAllowance.getValue(info) - 1 }
            }
            .filterNot { info -> rowParts.any { info.startsWith(it) } }
            .filterNot { info -> ours.any { info.startsWith(it) } }
            .filterNot { info -> DETAILS_FILES.any { "($it:" in info } }
    }

    /**
     * The details column's composables that ran (with or without box art), by name. The
     * 768 no-art board has the text-only column beside the list, as box art has.
     */
    private fun detailsParts(): List<String> =
        calls.filter { info -> info.isDetailsPart() }.map { it.substringBefore(' ') }

    /**
     * A move beside a details column re-runs it in one pass for the new game: every one of its functions
     * at most as often as it is called in one pass (once, or [CALLS_PER_PASS]), all in one frame (not
     * again a frame later).
     */
    private fun assertDetailsRanOnce(what: String) {
        val parts = detailsParts()
        assertEquals("$what: the column ran once for the new game: $parts", 1, parts.count { it == "com.plumemu.home.DetailsColumn" })
        val tooOften = parts.groupingBy { it }.eachCount().filter { (name, times) -> times > (CALLS_PER_PASS[name] ?: 1) }
        assertEquals("$what: functions run more often than one pass calls them: $parts", emptyMap<String, Int>(), tooOften)
        val frames = callTimes.filter { (info, _) -> info.isDetailsPart() }.map { it.second }.distinct()
        assertEquals("$what: all in one frame (frame times $frames)", 1, frames.size)
    }

    private fun showAt(
        actions: List<Action>,
        // Letters: the rail is the part of the peek whose recomposition these tests bound (Left edge, default Counts, has none).
        content: HomeContent = FakeHomeContent.board().withLeftEdge(LeftEdgeSetting.Letters),
        settings: PlumThemeSettings = PlumThemeSettings.Default,
    ): StripState {
        lateinit var state: StripState
        compose.setContent { PlumTheme(settings) { HomeAt(content, actions, onState = { state = it }) } }
        compose.waitForIdle()
        return state
    }

    /** The grid cells composed now, by their tag. */
    private fun coverCellsShown(): Set<String> =
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a cover cell") { node -> node.config.any { (key, value) -> key.name == "TestTag" && (value as? String)?.matches(Regex("home_cover_\\d+")) == true } }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { node -> node.config.first { it.key.name == "TestTag" }.value as String }.toSet()

    private fun moveDownAndCount(state: StripState) {
        compose.runOnIdle {
            calls.clear()
            callTimes.clear()
        }
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.waitForIdle()
    }

    private fun assertNoRailWhenAGameMoves(edge: LeftEdgeSetting) {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), FakeHomeContent.board().withLeftEdge(edge))
        moveDownAndCount(state)
        assertEquals("$edge: no rail letters: ${calls.filter { "RailLetter" in it }}", 0, railLetterRuns())
        assertEquals("$edge: system rows are not recomposed by a game move", 0, count("SystemRow"))
    }

    @Test
    fun withCountsAGameMoveComposesNoRailAtAll() = assertNoRailWhenAGameMoves(LeftEdgeSetting.Counts)

    @Test
    fun withCodesAGameMoveComposesNoRailAtAll() = assertNoRailWhenAGameMoves(LeftEdgeSetting.Codes)

    @Test
    fun gameMoveRecomposesOnlyTheTwoGameRows() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter))
        assertEquals("all visible rows composed at first", true, calls.isNotEmpty())
        moveDownAndCount(state)
        assertEquals("list items run: only the two whose look changed", 2, listItems())
        assertEquals("look followers run: the shown page's", 1, calls.count { it.startsWith("com.plumemu.home.FollowRowForms ") })
        assertEquals("game rows recomposed: ${calls.filter { "GameRow" in it }}", 2, count("GameRow"))
        assertEquals("system rows recomposed", 0, count("SystemRow"))
        assertEquals("nothing else of ours (bars, columns, hints) recomposes", emptyList<String>(), othersThatRan())
        // The move (Super Metroid to Yoshi's Island) crosses from the letter S to Y: exactly those two letters change look.
        assertEquals("rail letters recomposed: ${calls.filter { "RailLetter" in it }}", 2, railLetterRuns())
        assertDetailsRanOnce("no art, 768")
        assertTrue("the text-only column draws no cover: ${detailsParts()}", detailsParts().none { "Cover" in it })
        assertEquals(1, state.focus.row)
    }

    /** With the system info band as the list's first item the move recomposes the same two rows and nothing of the band. */
    @Test
    fun gameMoveWithTheSystemInfoBandRecomposesOnlyTheTwoGameRows() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), FakeHomeContent.board().withSystemInfo())
        moveDownAndCount(state)
        assertEquals("list items run: only the two whose look changed", 2, listItems())
        assertEquals("game rows recomposed: ${calls.filter { "GameRow" in it }}", 2, count("GameRow"))
        assertEquals("system rows recomposed", 0, count("SystemRow"))
        assertEquals("nothing else of ours (the band, bars, columns, hints) recomposes", emptyList<String>(), othersThatRan())
    }

    /** The box-art column beside the list at 768: the same rule as the text-only one above. */
    @Test
    fun gameMoveBesideTheBoxArtColumnRecomposesTheTwoRowsAndTheColumn() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), FakeHomeContent.withArtwork())
        moveDownAndCount(state)
        assertEquals("list items run: only the two whose look changed", 2, listItems())
        assertEquals("game rows recomposed: ${calls.filter { "GameRow" in it }}", 2, count("GameRow"))
        assertEquals("nothing else of ours recomposes", emptyList<String>(), othersThatRan())
        assertDetailsRanOnce("box art, 768")
    }

    /**
     * Largest in Serif, where the wide cover gives up height to the title block (`WideDetailsLayout`): a move
     * from a one-line to a two-line title and back re-runs the column once, all in one frame, as at Medium.
     * The cover's height is decided while laying out, not kept in state, so no second pass follows. Real
     * text measuring (native graphics) for these two only: the title's lines are what is tested.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aMoveBetweenOneAndTwoTitleLinesAtLargestSerifRunsTheColumnOnceInOneFrame() = movesBetweenTitleLines("768")

    private fun movesBetweenTitleLines(where: String) {
        val largestSerif = PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = PlumFont.Serif)
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), snesTitled(ONE_LINE_TITLE, TWO_LINE_TITLE), largestSerif)
        val oneLine = titleHeight()
        moveDownAndCount(state)
        assertTrue("$where: the second title takes more lines (${titleHeight()} vs $oneLine px)", titleHeight() > oneLine * 1.5f)
        assertDetailsRanOnce("$where, Largest Serif, one title line to two")
        compose.runOnIdle {
            calls.clear()
            callTimes.clear()
        }
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Up)) }
        compose.waitForIdle()
        assertDetailsRanOnce("$where, Largest Serif, two title lines to one")
    }

    private fun titleHeight(): Float = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).fetchSemanticsNode().size.height.toFloat()

    /** The board with box art, its first Super Nintendo games titled [titles]. */
    private fun snesTitled(vararg titles: String): HomeContent {
        val content = FakeHomeContent.withArtwork()
        val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]
        val renamed = snes.copy(rows = snes.rows.mapIndexed { i, row -> titles.getOrNull(i)?.let { row.copy(title = it) } ?: row })
        return content.withEntries(content.entries.map { if (it.id == snes.id) renamed else it })
    }

    /**
     * With play facts read (the library's case), after the focus rested and the facts arrived nothing
     * recomposes while idle: no ticker, no second read.
     */
    @Test
    fun nothingRecomposesWhileIdleBesideTheTextColumnWithItsFacts() {
        val reads = mutableListOf<Long>()
        val source =
            object : HomePlayFactsSource {
                override suspend fun read(gameId: Long): GamePlay {
                    reads += gameId
                    return GamePlay(2, 252, com.plumemu.game.EmulatorToStart("Snes9x", isInstalled = true))
                }
            }
        val content = numberedSnes()
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                CompositionLocalProvider(LocalHomePlayFacts provides source) {
                    HomeAt(content, listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM), Action.Enter), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.waitForIdle()
        compose.onNodeWithTag(HomeTags.DETAILS_FACTS, useUnmergedTree = true).assertExists()
        assertEquals("one read per rested game", listOf(1L, 2L), reads)
        compose.runOnIdle { calls.clear() }
        compose.mainClock.advanceTimeBy(IDLE_MS)
        compose.waitForIdle()
        assertEquals("nothing ran while idle", emptyList<String>(), calls.filter { it.startsWith("com.plumemu.") })
        assertEquals("no read while idle", listOf(1L, 2L), reads)
    }

    /**
     * A game measured before (a usual pace) with the battery at a fixed level: on a move the battery line
     * runs once, in the frame the played facts arrive in (not a frame later, which would
     * move the column twice), never more often than the facts table; while idle nothing runs. The battery
     * is read once for the screen, not by each line.
     */
    @Test
    fun theBatteryLineArrivesWithTheFactsAndStaysStill() {
        sendBattery(level = 60)
        val source =
            object : HomePlayFactsSource {
                override suspend fun read(gameId: Long): GamePlay =
                    GamePlay(2, 252, com.plumemu.game.EmulatorToStart("Snes9x", isInstalled = true), batteryPace = com.plumemu.battery.BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0))
            }
        val content = numberedSnes()
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                CompositionLocalProvider(LocalHomePlayFacts provides source) {
                    HomeAt(content, listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM), Action.Enter), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(com.plumemu.battery.PickingBatteryTags.LINE, useUnmergedTree = true).assertExists()
        moveDownAndCount(state)
        compose.onNodeWithTag(com.plumemu.battery.PickingBatteryTags.LINE, useUnmergedTree = true).assertExists()
        val line = callTimes.filter { (info, _) -> info.startsWith("com.plumemu.battery.PickingBatteryLine ") }
        val table = callTimes.filter { (info, _) -> info.startsWith("com.plumemu.home.Facts ") }
        assertEquals("the line ran once for the new game: ${detailsParts()}", 1, line.size)
        assertEquals("the table ran once for the new game: ${detailsParts()}", 1, table.size)
        assertEquals("the line in the frame the facts arrived in", table.single().second, line.single().second)
        assertEquals("nothing else of ours recomposes", emptyList<String>(), othersThatRan())
        compose.runOnIdle { calls.clear() }
        compose.mainClock.advanceTimeBy(IDLE_MS)
        compose.waitForIdle()
        assertEquals("nothing ran while idle", emptyList<String>(), calls.filter { it.startsWith("com.plumemu.") })
    }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun sendBattery(level: Int) {
        org.robolectric.RuntimeEnvironment.getApplication().sendStickyBroadcast(
            android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                .putExtra(android.os.BatteryManager.EXTRA_LEVEL, level)
                .putExtra(android.os.BatteryManager.EXTRA_SCALE, 100),
        )
    }

    /** Super Nintendo with library-like ids (numbers), so the play facts are read. */
    private fun numberedSnes(): HomeContent =
        HomeContent.of(
            recent = emptyList(),
            favorites = emptyList(),
            apps = emptyList(),
            systems = listOf(SystemGames("snes", "Super Nintendo", List(ROWS) { i -> GameRowModel("${i + 1}", "Game ${i + 1}", "USA") })),
        )

    /** Recent mixes systems: its rows also read whether the details stop is focused, and that must not recompose them on a move. */
    @Test
    fun aMoveInRecentRecomposesOnlyTheTwoGameRows() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, 0), Action.Enter), FakeHomeContent.mixedSystems())
        moveDownAndCount(state)
        assertEquals("list items run: only the two whose look changed", 2, listItems())
        assertEquals("game rows recomposed: ${calls.filter { "GameRow" in it }}", 2, count("GameRow"))
        assertEquals(1, state.focus.row)
    }

    /**
     * The rows' composed looks are written while composing the frame of a move (RowForms, by
     * FollowRowForms): the two rows recompose in that same frame, at the same frame time, not one
     * frame later.
     */
    @Test
    fun aMovesTwoRowsRecomposeInTheFrameOfTheMove() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter))
        moveDownAndCount(state)
        val follower = callTimes.filter { (info, _) -> info.startsWith("com.plumemu.home.FollowRowForms ") }.map { it.second }
        val items = callTimes.filter { (info, _) -> info.startsWith("com.plumemu.home.GamesListItem ") }.map { it.second }
        assertEquals("the shown page's follower ran once", 1, follower.size)
        assertEquals("both rows in the follower's frame (frame times, ms)", listOf(follower.single(), follower.single()), items)
    }

    /**
     * Enter and Back change every row's look on the shown page and on the pages kept beside it
     * (Preview to Normal), but those looks differ only in the title's colour, which the row reads
     * while drawing: only each page's remembered row (Focused, its dot and fill) recomposes.
     * With the looks composed, Enter recomposed every visible row of every kept page.
     */
    @Test
    fun enterAndBackRecomposeOnlyTheRememberedRows() {
        // The system before SNES first, then down: SNES shown with the page it left kept beside it.
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX - 1)))
        moveDownAndCount(state)
        assertEquals(FakeHomeContent.BOARD_SNES_INDEX, state.focus.systemIndex)
        val pages = compose.onAllNodesWithTag(HomeTags.GAMES_PAGE, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals("the shown page and the one kept", 2, pages)
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Enter) }
        compose.waitForIdle()
        assertEquals(Column.Games, state.focus.column)
        assertEquals("game rows recomposed on Enter (one per built page, $pages): ${calls.filter { "GameRow" in it }}", pages, count("GameRow"))
        assertEquals("list items run on Enter", pages, listItems())
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Back) }
        compose.waitForIdle()
        assertEquals(Column.Systems, state.focus.column)
        assertEquals("game rows recomposed on Back: ${calls.filter { "GameRow" in it }}", pages, count("GameRow"))
    }

    /**
     * Narrow, with box art, a single system's list: Right to the details stop and Back change only the
     * remembered row's look. No row reads the stop for its right-hand text (year · region is never shown
     * beside the details on a narrow screen), so the others, on every built page, stay untouched.
     */
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theNarrowDetailsStopRecomposesNoRowButTheRememberedOne() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), FakeHomeContent.withArtwork())
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        assertEquals(Column.Details, state.focus.column)
        assertEquals("list items run at the details stop: ${calls.filter { "GamesListItem" in it }}", 1, listItems())
        assertEquals("game rows recomposed at the details stop", 1, count("GameRow"))
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Back) }
        compose.waitForIdle()
        assertEquals(Column.Games, state.focus.column)
        // Back from the stop also changes the look of the row the list was left on (Current to Focused): list items 2, game rows 1, the same as before year · region.
        assertEquals("list items run on Back from the details stop", 2, listItems())
        assertEquals("game rows recomposed on Back from the details stop", 1, count("GameRow"))
    }

    @Test
    fun systemMoveRecomposesOnlyTheTwoSystemRows() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)))
        moveDownAndCount(state)
        assertEquals("system rows recomposed: ${calls.filter { "SystemRow" in it }}", 2, count("SystemRow"))
        assertEquals(FakeHomeContent.BOARD_SNES_INDEX + 1, state.focus.systemIndex)
    }

    @Test
    fun systemMoveWithTheSystemInfoBandRecomposesOnlyTheTwoSystemRows() {
        val state = showAt(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), FakeHomeContent.board().withSystemInfo())
        moveDownAndCount(state)
        assertEquals("system rows recomposed: ${calls.filter { "SystemRow" in it }}", 2, count("SystemRow"))
        assertEquals(FakeHomeContent.BOARD_SNES_INDEX + 1, state.focus.systemIndex)
        // The page just shown was built by this move (Robolectric has no idle time to build it ahead); going back to the
        // page that is already built composes no band and no row again.
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Up)) }
        compose.waitForIdle()
        assertEquals("the built page's band is not composed again: ${calls.filter { "SystemInfoBand" in it }}", 0, calls.count { "SystemInfoBand" in it })
        assertEquals("nor its rows", 0, listItems())
        assertEquals(2, count("SystemRow"))
    }

    @Test
    fun gridMoveRecomposesNoCoverOnlyTheBarTitle() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                // The board's landscape grid, three columns: the Large grid size.
                ProvideGridSize(GridSize.Large) { HomeAt(FakeHomeContent.withArtwork(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), onState = { state = it }, gridIds = setOf("snes")) }
            }
        }
        compose.waitForIdle()
        moveDownAndCount(state)
        // The ring is drawn over the grid from the focus, so a move redraws it and no cover recomposes.
        assertEquals("covers recomposed: ${calls.filter { "CoverTile" in it }}", 0, count("CoverTile"))
        assertEquals("no rows", 0, count("GameRow") + count("SystemRow"))
        // The bar's title shows the focused game, so it changes on a move; nothing else does.
        val barTitle = listOf("GridHintBar", "TitledHintBar", "FocusTitle", "yearAndPublisher")
        val others =
            othersThatRan()
                .filterNot { info -> listOf("CoverTile", "GridCover").any { info.substringBefore(' ').endsWith(".$it") } }
                .filterNot { info -> "<anonymous>" in info && "HomeGrid.kt" in info }
                .filterNot { info -> barTitle.any { info.substringBefore(' ').endsWith(".$it") } }
                // The title's slot of the bar (a subcomposition : the meta and hints give way to it).
                .filterNot { info -> info.substringBefore(' ').startsWith("com.plumemu.coreui.components.TitledHintBar.<anonymous>") }
        assertEquals("nothing else of ours recomposes", emptyList<String>(), others)
        assertEquals("grid items: ${calls.filter { "HomeGrid.kt" in it && "<anonymous>" in it }}", 0, calls.count { "HomeGrid.kt" in it && "<anonymous>" in it })
        assertEquals("down one row of three", 3, state.focus.row)
    }

    /**
     * The same move with ScreenScraper results and decoded pictures (a live [HomeArt], not
     * [HomeArtSource.None]): the pictures, their titled tiles and the slots read nothing of the
     * focus, so no cover recomposes; only the bar's title and facts follow the focus.
     */
    @Test
    fun gridMoveWithLiveArtRecomposesNoCoverOnlyTheBarTitle() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                ProvideGridSize(GridSize.Large) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) }
            }
        }
        compose.waitForIdle()
        moveDownAndCount(state)
        val covers = listOf("CoverArt", "CoverPicture", "TitledTile", "GridCover").map { "com.plumemu.home.$it " } + "com.plumemu.coreui.components.CoverTile "
        assertEquals("covers recomposed: ${calls.filter { c -> covers.any { c.startsWith(it) } }}", 0, calls.count { c -> covers.any { c.startsWith(it) } })
        val barTitle = listOf("GridHintBar", "TitledHintBar", "FocusTitle", "yearAndPublisher")
        val others =
            othersThatRan()
                .filterNot { info -> barTitle.any { info.substringBefore(' ').endsWith(".$it") } }
                .filterNot { info -> info.substringBefore(' ').startsWith("com.plumemu.coreui.components.TitledHintBar.<anonymous>") }
        assertEquals("nothing else of ours recomposes", emptyList<String>(), others)
        assertEquals("down one row of three", 3, state.focus.row)
    }

    /** The same move in Recent's grid, where each cover keeps its system's shape: the bounds are the system grid's. */
    @Test
    fun mixedGridMoveWithLiveArtRecomposesNoCoverOnlyTheBarTitle() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                ProvideGridSize(GridSize.Large) {
                    HomeWithArt(rememberSource { mixedGridSource() }, gridActions(0, 1), MixedGridLibrary, gridIds = setOf(HomeContent.RECENT_ID), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        val before = state.focus.row
        val shownBefore = coverCellsShown()
        moveDownAndCount(state)
        val covers = listOf("CoverArt", "CoverPicture", "TitledTile", "GridCover").map { "com.plumemu.home.$it " } + "com.plumemu.coreui.components.CoverTile "
        // The grid scrolls by whole rows (the row below the shown ones starts in the room left under them), so the
        // covers of a row that comes into view compose once (GridCover, CoverArt, CoverPicture each); no other cover runs.
        val enteredView = (coverCellsShown() - shownBefore).size
        assertEquals("covers recomposed: ${calls.filter { c -> covers.any { c.startsWith(it) } }}", 3 * enteredView, calls.count { c -> covers.any { c.startsWith(it) } })
        val barTitle = listOf("GridHintBar", "TitledHintBar", "FocusTitle", "yearAndPublisher")
        val others =
            othersThatRan()
                .filterNot { info -> covers.any { info.startsWith(it) } } // counted above, against the covers that entered
                .filterNot { info -> barTitle.any { info.substringBefore(' ').endsWith(".$it") } }
                .filterNot { info -> info.substringBefore(' ').startsWith("com.plumemu.coreui.components.TitledHintBar.<anonymous>") }
        // The cells' own composables (GridCover.Cover) run for the covers that came into view and for no other.
        val coverBodies = others.filter { it.startsWith("com.plumemu.home.GridCover.Cover ") }
        assertTrue("GridCover.Cover ran for covers already on screen: $coverBodies", coverBodies.size <= enteredView)
        assertEquals("nothing else of ours recomposes", emptyList<String>(), others - coverBodies.toSet())
        assertTrue("the focus moved down a row", state.focus.row > before)
    }

    /**
     * Apps' grid of icons, signed out: a move recomposes no tile and loads no icon; only
     * the bar's title (the focused app's name) runs, as in a game grid.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun appsGridMoveRecomposesNoTileAndLoadsNoIcon() {
        lateinit var state: StripState
        val icons = FakeAppIcons()
        compose.setContent { PlumTheme { AppsGridBoard(focused = 0, icons = icons, onState = { state = it }) } }
        compose.waitForIdle()
        val loaded = icons.loads.toList()
        assertTrue("the shown tiles loaded their icons", loaded.isNotEmpty())
        moveDownAndCount(state)
        val tiles = listOf("AppTile", "AppIconStandIn").map { "com.plumemu.home.$it " }
        assertEquals("tiles recomposed: ${calls.filter { c -> tiles.any { c.startsWith(it) } }}", 0, calls.count { c -> tiles.any { c.startsWith(it) } })
        val barTitle = listOf("GridHintBar", "TitledHintBar", "FocusTitle", "yearAndPublisher")
        val others =
            othersThatRan()
                .filterNot { info -> barTitle.any { info.substringBefore(' ').endsWith(".$it") } }
                .filterNot { info -> info.substringBefore(' ').startsWith("com.plumemu.coreui.components.TitledHintBar.<anonymous>") }
        assertEquals("nothing else of ours recomposes", emptyList<String>(), others)
        assertEquals("a move loads no icon", loaded, icons.loads.toList())
        assertTrue("the focus moved down a row", state.focus.rowOf(HomeContent.APPS_ID) > 0)
    }

    @Test
    fun aClockTickRecomposesOnlyTheClockText() {
        val clock = mutableStateOf("21:40")
        compose.setContent { PlumTheme { HomeAt(FakeHomeContent.board(), BoardGamesFocused, clock = { clock.value }) } }
        compose.waitForIdle()
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { clock.value = "21:41" }
        compose.waitForIdle()
        val ours = calls.filter { it.startsWith("com.plumemu.home.") || it.startsWith("com.plumemu.coreui.components.") }
        assertEquals("ran: $ours", listOf("StatusText"), ours.map { it.substringBefore(" ").substringAfterLast('.') })
    }
}

/**
 * The files that hold the details column (box art, text only, the play facts, the battery line under
 * them) and the number format its facts call (`CountFormat.kt`: the rating, days and minutes; the top
 * bar and the system column, its other callers, would show up as themselves, which the tests forbid).
 */
internal val DETAILS_FILES = listOf("HomeDetails.kt", "WideDetailsLayout.kt", "HomeDetailsText.kt", "HomePlayFacts.kt", "CountFormat.kt", "PickingBatteryLine.kt")

/** A traced call of the details column: in one of [DETAILS_FILES], of the home screen or the battery line. */
internal fun String.isDetailsPart(): Boolean = (startsWith("com.plumemu.home.") || startsWith("com.plumemu.battery.")) && DETAILS_FILES.any { "($it:" in this }

/**
 * Details functions one pass calls more than once: the box-art column's two fact lines ("1994 ·
 * Nintendo", "Action · 7.5/10") and the locale read by the rating and the count format; and
 * WideDetails' three slot lambdas (cover, title block, the rest of [WideDetailsLayout]), which share one name.
 */
internal val CALLS_PER_PASS = mapOf("com.plumemu.home.FactLine" to 2, "com.plumemu.home.currentLocale" to 2, "com.plumemu.home.WideDetails.<anonymous>" to 3)

/** One line at Largest in Serif in the 248 details column. */
internal const val ONE_LINE_TITLE = "Contra III"

/** More than one line at Largest in Serif in the 248 details column. */
internal const val TWO_LINE_TITLE = "Super Mario World 2: Yoshi's Island"

/** Where Super Nintendo sits in [numberedSnes]'s column: after Recent and Favorites (no Apps given, the entry stays). */
private const val FIRST_SYSTEM = 3
private const val ROWS = 12
private const val IDLE_MS = 10_000L
