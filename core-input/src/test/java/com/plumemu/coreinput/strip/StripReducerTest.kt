package com.plumemu.coreinput.strip

import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripReducerTest {
    // The system column as the product defines: Recent, Favorites, Apps, then systems with games.
    private val content =
        StripContent(
            systems =
                listOf(
                    SystemEntry("recent", itemCount = 0),
                    SystemEntry("favorites", itemCount = 3),
                    SystemEntry("apps", itemCount = 12),
                    SystemEntry("snes", itemCount = 248),
                    SystemEntry("gba", itemCount = 5, gridColumns = 3),
                ),
            hasBoxArt = true,
        )
    private val noArt = content.copy(hasBoxArt = false)

    private fun Reduction.then(
        action: Action,
        on: StripContent = content,
        narrow: Boolean = false,
    ) = StripReducer.reduce(focus, action, on, narrow)

    private fun start(
        action: Action,
        on: StripContent = content,
        narrow: Boolean = false,
    ) = StripReducer.reduce(StripFocus(), action, on, narrow)

    private fun Reduction.repeat(
        times: Int,
        action: Action,
        narrow: Boolean = false,
    ): Reduction = (1..times).fold(this) { r, _ -> r.then(action, narrow = narrow) }

    private val up = Action.Move(Direction.Up)
    private val down = Action.Move(Direction.Down)
    private val left = Action.Move(Direction.Left)
    private val right = Action.Move(Direction.Right)
    private val heldUp = Action.Move(Direction.Up, isRepeat = true)
    private val heldDown = Action.Move(Direction.Down, isRepeat = true)

    /** The focus on snes's games (system index 3). */
    private fun onSnesGames(narrow: Boolean = false) = Reduction(StripFocus()).repeat(3, down).then(Action.Enter, narrow = narrow)

    @Test
    fun theSystemColumnWrapsOnAFreshPressAndStopsWhileHeld() {
        assertEquals(4, start(up).focus.systemIndex)
        assertEquals(0, start(heldUp).focus.systemIndex)
        val last = start(up)
        assertEquals(0, last.then(down).focus.systemIndex)
        assertEquals(4, last.then(heldDown).focus.systemIndex)
    }

    @Test
    fun recentFavoritesAndAppsAreEntriesAtTheTopAndAnEmptyOneCannotBeEntered() {
        val recent = start(Action.Enter)
        assertEquals(Column.Systems, recent.focus.column)
        assertEquals("recent", recent.focus.systemId)
        assertEquals(Column.Systems, start(right).focus.column)
        val favorites = start(down).then(right)
        assertEquals(Column.Games to "favorites", favorites.focus.column to favorites.focus.systemId)
        val apps = start(down).then(down).then(Action.Enter)
        assertEquals(Column.Games to "apps", apps.focus.column to apps.focus.systemId)
    }

    @Test
    fun returningToASystemRestoresItsRow() {
        val snes = onSnesGames().repeat(3, down)
        assertEquals(3, snes.focus.row)
        val gba = snes.then(Action.Back).then(down).then(Action.Enter).then(right)
        assertEquals("gba" to 1, gba.focus.systemId to gba.focus.row)
        val back = gba.then(Action.Back).then(up).then(Action.Enter)
        assertEquals("snes" to 3, back.focus.systemId to back.focus.row)
        assertEquals("L1/R1 restore rows too", 1, back.then(Action.NextSystem).focus.row)
    }

    @Test
    fun theGamesListWrapsOnAFreshPressAndStopsWhileHeld() {
        assertEquals(247, onSnesGames().then(up).focus.row)
        assertEquals(0, onSnesGames().then(heldUp).focus.row)
        val end = onSnesGames().then(up)
        assertEquals(0, end.then(down).focus.row)
        assertEquals(247, end.then(heldDown).focus.row)
    }

    @Test
    fun aSingleSystemWrapsOntoItselfAndChangingSystemStays() {
        val one = StripContent(listOf(SystemEntry("nes", 1)), hasBoxArt = true)
        val r = start(down, on = one)
        assertEquals(0, r.focus.systemIndex)
        val games = r.then(Action.Enter, on = one)
        assertEquals(0, games.then(down, on = one).focus.row)
        assertEquals(games.focus, games.then(Action.NextSystem, on = one).focus)
        assertEquals(games.focus, games.then(Action.PreviousSystem, on = one).focus)
    }

    @Test
    fun noSystemsAtAllIsHarmless() {
        val empty = StripContent(emptyList(), hasBoxArt = false)
        for (action in listOf(up, down, right, Action.Enter, Action.Back, Action.NextSystem, Action.Menu, Action.TapItem(Column.Games, 0))) {
            val r = start(action, on = empty)
            assertEquals(Column.Systems, r.focus.column)
            assertTrue(r.intents.isEmpty())
        }
    }

    @Test
    fun changingSystemWrapsAndSkipsEmptyEntriesOnlyWhileInGames() {
        assertEquals("gba", start(Action.PreviousSystem).focus.systemId)
        assertEquals("favorites", start(Action.NextSystem).focus.systemId)
        // From gba's games, R1 wraps past the empty Recent to Favorites and stays in games.
        val gba = start(Action.PreviousSystem).then(Action.Enter).then(Action.NextSystem)
        assertEquals(Column.Games to "favorites", gba.focus.column to gba.focus.systemId)
        assertEquals("gba", gba.then(Action.PreviousSystem).focus.systemId)
    }

    @Test
    fun enterAndPlayOnAGamePlayIt() {
        val snes = onSnesGames().repeat(2, down)
        assertEquals(listOf(StripIntent.Play("snes", 2)), snes.then(Action.Enter).intents)
        assertEquals(listOf(StripIntent.Play("snes", 2)), snes.then(Action.Play).intents)
        assertEquals("Play on the system column opens it", Column.Games, start(down).then(Action.Play).focus.column)
    }

    @Test
    fun backStepsLeftAndStopsAtTheSystems() {
        val details = onSnesGames(narrow = true).then(right, narrow = true)
        assertEquals(Column.Details, details.focus.column)
        val games = details.then(Action.Back, narrow = true)
        assertEquals(Column.Games, games.focus.column)
        val systems = games.then(Action.Back, narrow = true)
        assertEquals(Column.Systems, systems.focus.column)
        assertEquals(systems, systems.then(Action.Back, narrow = true))
    }

    @Test
    fun detailsAreAStopOnlyOnNarrowScreensWithBoxArt() {
        assertEquals(Column.Games, onSnesGames().then(right).focus.column)
        assertEquals(Column.Details, onSnesGames(narrow = true).then(right, narrow = true).focus.column)
        val withoutArt = StripReducer.reduce(onSnesGames().focus, right, noArt, isNarrow = true)
        assertEquals(Column.Games, withoutArt.focus.column)
    }

    @Test
    fun upAndDownInDetailsChangeTheGameAndLeftGoesBack() {
        val details = onSnesGames(narrow = true).then(right, narrow = true).then(down, narrow = true)
        assertEquals(Column.Details to 1, details.focus.column to details.focus.row)
        assertEquals(Column.Games, details.then(left, narrow = true).focus.column)
    }

    @Test
    fun startOpensSettingsOnTheSystemsAndTheGameMenuOnAGame() {
        assertEquals(listOf(StripIntent.OpenSettings), start(Action.Menu).intents)
        assertEquals(listOf(StripIntent.OpenGameMenu("snes", 1)), onSnesGames().then(down).then(Action.Menu).intents)
        assertEquals(listOf(StripIntent.OpenSettings), onSnesGames().then(Action.OpenSettings).intents)
    }

    @Test
    fun selectTogglesTheViewOnlyWithBoxArt() {
        assertEquals(listOf(StripIntent.ToggleView("snes")), onSnesGames().then(Action.ToggleView).intents)
        assertTrue(StripReducer.reduce(onSnesGames().focus, Action.ToggleView, noArt, false).intents.isEmpty())
    }

    /** Apps' grid of icons needs no box art: SELECT reaches the screen there, and still nowhere else. */
    @Test
    fun selectTogglesAnEntryWithAGridWithoutArtAlsoWithoutBoxArt() {
        val appsWithout = noArt.copy(systems = noArt.systems.map { if (it.id == "apps") it.copy(hasGridWithoutArt = true) else it })
        val onApps = StripReducer.reduce(StripFocus(systemIndex = 2), Action.Enter, appsWithout, false)
        assertEquals(listOf(StripIntent.ToggleView("apps")), onApps.then(Action.ToggleView, on = appsWithout).intents)
        assertEquals(listOf(StripIntent.ToggleView("apps")), StripReducer.reduce(StripFocus(systemIndex = 2), Action.ToggleView, appsWithout, false).intents)
        assertTrue(StripReducer.reduce(onSnesGames().focus, Action.ToggleView, appsWithout, false).intents.isEmpty())
    }

    @Test
    fun favoriteAsksTheScreenOnlyWithAnItemFocusedAndKeepsTheFocus() {
        val onSecond = onSnesGames().then(down)
        val toggled = onSecond.then(Action.ToggleFavorite)
        assertEquals(listOf(StripIntent.ToggleFavorite("snes", 1)), toggled.intents)
        assertEquals(onSecond.focus, toggled.focus)
        assertTrue("nothing to mark on the system column", start(Action.ToggleFavorite).intents.isEmpty())
        val details = onSnesGames().then(right, narrow = true).then(Action.ToggleFavorite, narrow = true)
        assertEquals(listOf(StripIntent.ToggleFavorite("snes", 0)), details.intents)
    }

    @Test
    fun searchOpensFromAnyColumnAndKeepsTheFocus() {
        assertEquals(listOf(StripIntent.OpenSearch), start(Action.Search).intents)
        val onSecond = onSnesGames().then(down)
        val opened = onSecond.then(Action.Search)
        assertEquals(listOf(StripIntent.OpenSearch), opened.intents)
        assertEquals(onSecond.focus, opened.focus)
    }

    @Test
    fun letterJumpsAskTheScreenAndFocusRowApplies() {
        val snes = onSnesGames().repeat(4, down)
        assertEquals(listOf(StripIntent.JumpLetter("snes", 4, forward = true)), snes.then(Action.JumpLetterNext).intents)
        assertEquals(listOf(StripIntent.JumpLetter("snes", 4, forward = false)), snes.then(Action.JumpLetterPrevious).intents)
        assertTrue(start(Action.JumpLetterNext).intents.isEmpty())
        assertEquals(40, StripReducer.focusRow(snes.focus, 40, content, false).row)
        assertEquals("clamped to the list", 247, StripReducer.focusRow(snes.focus, 9_999, content, false).row)
    }

    @Test
    fun gridMovesByCellAndRowAndLeavesAtItsEdges() {
        // gba: 5 covers in 3 columns -> rows [0 1 2] [3 4].
        val grid = start(Action.PreviousSystem).then(Action.Enter)
        assertEquals(1, grid.then(right).focus.row)
        assertEquals(3, grid.then(down).focus.row)
        assertEquals("partial last row: land on its last cover", 4, grid.repeat(2, right).then(down).focus.row)
        assertEquals("no wrap at the top", 0, grid.then(up).focus.row)
        assertEquals("no row below", 3, grid.then(down).then(down).focus.row)
        assertEquals(Column.Systems, grid.then(left).focus.column)
        assertEquals("end of a row on a wide screen stays", 2, grid.repeat(3, right).focus.row)
        assertEquals(Column.Details, StripReducer.reduce(grid.repeat(2, right).focus, right, content, isNarrow = true).focus.column)
    }

    @Test
    fun tapsFocusFirstAndActOnTheSecond() {
        val tapSnes = start(Action.TapItem(Column.Systems, 3))
        assertEquals(Column.Systems to "snes", tapSnes.focus.column to tapSnes.focus.systemId)
        val opened = tapSnes.then(Action.TapItem(Column.Systems, 3))
        assertEquals(Column.Games, opened.focus.column)
        val game = opened.then(Action.TapItem(Column.Games, 7))
        assertEquals(7, game.focus.row)
        assertTrue(game.intents.isEmpty())
        assertEquals(listOf(StripIntent.Play("snes", 7)), game.then(Action.TapItem(Column.Games, 7)).intents)
        assertEquals("out of range is ignored", game, game.then(Action.TapItem(Column.Games, 248)))
    }

    @Test
    fun tappingTheSystemEdgeChangesSystemOrReturnsToTheColumn() {
        val snes = onSnesGames()
        val gba = snes.then(Action.TapItem(Column.Systems, 4))
        assertEquals(Column.Games to "gba", gba.focus.column to gba.focus.systemId)
        val back = gba.then(Action.TapItem(Column.Systems, 4))
        assertEquals(Column.Systems to "gba", back.focus.column to back.focus.systemId)
        val toEmpty = snes.then(Action.TapItem(Column.Systems, 0))
        assertEquals("an empty entry is focused on the system column", Column.Systems to "recent", toEmpty.focus.column to toEmpty.focus.systemId)
    }

    @Test
    fun aLongPressOnAGameFocusesItAndOpensItsMenu() {
        val r = onSnesGames().then(Action.LongPressItem(Column.Games, 9))
        assertEquals(9, r.focus.row)
        assertEquals(listOf(StripIntent.OpenGameMenu("snes", 9)), r.intents)
    }

    @Test
    fun aLongPressOnTheSystemColumnOpensSettingsFromAnyColumn() {
        // The touch path to Settings that works with the hint bar hidden (SPEC › Controls).
        val fromSystems = start(Action.LongPressItem(Column.Systems, 2))
        assertEquals(listOf(StripIntent.OpenSettings), fromSystems.intents)
        assertEquals("the focus stays", 0 to Column.Systems, fromSystems.focus.systemIndex to fromSystems.focus.column)
        val fromGames = onSnesGames().then(Action.LongPressItem(Column.Systems, 0))
        assertEquals(listOf(StripIntent.OpenSettings), fromGames.intents)
        assertEquals(onSnesGames().focus, fromGames.focus)
    }

    @Test
    fun tappingDetailsOpensTheStopOnNarrowAndPlaysFromIt() {
        val details = onSnesGames(narrow = true).then(Action.TapItem(Column.Details, 0), narrow = true)
        assertEquals(Column.Details, details.focus.column)
        assertEquals(listOf(StripIntent.Play("snes", 0)), details.then(Action.TapItem(Column.Details, 0), narrow = true).intents)
        assertEquals(Column.Games, onSnesGames().then(Action.TapItem(Column.Details, 0)).focus.column)
    }

    @Test
    fun aRescanThatRemovesTheFocusedSystemKeepsAValidFocus() {
        val gba = start(Action.PreviousSystem).then(Action.Enter).then(down)
        val withoutGba = content.copy(systems = content.systems.dropLast(1))
        val r = StripReducer.reduce(gba.focus, Action.Move(Direction.Down), withoutGba, false)
        assertEquals("snes", r.focus.systemId)
        val shrunk = content.copy(systems = content.systems.map { if (it.id == "gba") it.copy(itemCount = 1) else it })
        assertEquals(0, gba.focus.reconciled(shrunk, false).row)
    }

    // Apps never has details (apps have no art), even with box art.
    private val appsWithoutDetails =
        content.copy(systems = content.systems.map { if (it.id == "apps") it.copy(hasDetails = false) else it })

    private fun inAppsGames(narrow: Boolean) =
        start(Action.TapItem(Column.Systems, 2), appsWithoutDetails, narrow).then(Action.Enter, appsWithoutDetails, narrow)

    @Test
    fun appsHasNoDetailsStopOnANarrowScreen() {
        val apps = inAppsGames(narrow = true)
        assertEquals(Column.Games, apps.focus.column)
        assertEquals("Right stays in the list", Column.Games, apps.then(Action.Move(Direction.Right), appsWithoutDetails, narrow = true).focus.column)
        assertEquals("a tap on the details area does nothing", Column.Games, apps.then(Action.TapItem(Column.Details, 0), appsWithoutDetails, narrow = true).focus.column)
        val geometryStops = StripGeometry(true, 0.dp, 200.dp, 400.dp, 24.dp, 48.dp, 56.dp).stops(apps.focus, appsWithoutDetails)
        assertEquals(listOf(Column.Systems, Column.Games), geometryStops)
        assertEquals("a restored details focus falls back to the list", Column.Games, apps.focus.copy(column = Column.Details).reconciled(appsWithoutDetails, hasDetailsStop = true).column)
    }

    @Test
    fun otherSystemsKeepTheirDetailsStop() {
        val snes = start(Action.TapItem(Column.Systems, 3), appsWithoutDetails, narrow = true).then(Action.Enter, appsWithoutDetails, narrow = true)
        assertEquals(Column.Details, snes.then(Action.Move(Direction.Right), appsWithoutDetails, narrow = true).focus.column)
    }

    private fun withGbaIds(ids: List<String>) = content.copy(systems = content.systems.map { if (it.id == "gba") it.copy(itemCount = ids.size, itemIds = ids) else it })

    @Test
    fun aReSortUnderTheFocusKeepsTheSameGameFocused() {
        val sorted = withGbaIds(listOf("g0", "g1", "g2", "g3", "g4"))
        val onG2 = start(Action.TapItem(Column.Systems, 4), sorted).then(Action.Enter, sorted).then(Action.TapItem(Column.Games, 2), sorted)
        assertEquals("g2", onG2.focus.rowIds["gba"])
        val resorted = withGbaIds(listOf("g4", "g2", "g0", "g1", "g3"))
        assertEquals(1, onG2.focus.reconciled(resorted, false).row)
        assertEquals(listOf(StripIntent.Play("gba", 1)), onG2.then(Action.Enter, resorted).intents)
        assertEquals("a move starts from the game's new place", 2, onG2.then(right, resorted).focus.row)
    }

    @Test
    fun whenTheFocusedGameIsGoneTheNearestRowIsFocusedAndFollowedFromThen() {
        val sorted = withGbaIds(listOf("g0", "g1", "g2", "g3", "g4"))
        val onG2 = start(Action.TapItem(Column.Systems, 4), sorted).then(Action.Enter, sorted).then(Action.TapItem(Column.Games, 2), sorted)
        val gone = withGbaIds(listOf("g0", "g1", "g3", "g4"))
        val after = onG2.focus.reconciled(gone, false)
        assertEquals(2, after.row)
        assertEquals("g3", after.rowIds["gba"])
        assertEquals(3, after.reconciled(withGbaIds(listOf("g9", "g0", "g1", "g3", "g4")), false).row)
    }

    @Test
    fun aRememberedGameIsFoundAgainWhenItsSystemIsFocusedAgain() {
        val sorted = withGbaIds(listOf("g0", "g1", "g2"))
        val onG1 = start(Action.TapItem(Column.Systems, 4), sorted).then(Action.Enter, sorted).then(Action.TapItem(Column.Games, 1), sorted)
        val elsewhere = onG1.then(Action.PreviousSystem, sorted)
        assertEquals("snes", elsewhere.focus.systemId)
        val resorted = withGbaIds(listOf("g1", "g2", "g0"))
        val back = elsewhere.then(Action.NextSystem, resorted)
        assertEquals("gba" to 0, back.focus.systemId to back.focus.row)
    }

    @Test
    fun withoutIdsRowsAreKeptByIndexAsBefore() {
        val onRow3 = start(Action.TapItem(Column.Systems, 3)).then(Action.Enter).then(Action.TapItem(Column.Games, 3))
        assertEquals(emptyMap<String, String>(), onRow3.focus.rowIds)
        assertEquals(3, onRow3.focus.reconciled(content, false).row)
    }

    @Test
    fun landingOnASystemFocusesItsFirstGameInTheGamesColumn() {
        val deep = start(Action.TapItem(Column.Systems, 3)).then(Action.Enter).repeat(30, down)
        assertEquals(30, deep.focus.row)
        val landed = StripReducer.landOn(deep.focus, "favorites", content, isNarrow = false)
        assertEquals(Triple("favorites", Column.Games, 0), Triple(landed.systemId, landed.column, landed.row))
        assertEquals("the other systems keep their rows", 30, landed.rowOf("snes"))
    }

    @Test
    fun landingOnASystemWithNoGamesStaysOnTheSystemsColumn() {
        val landed = StripReducer.landOn(StripFocus(), "recent", content, isNarrow = false)
        assertEquals(Pair("recent", Column.Systems), landed.systemId to landed.column)
    }

    @Test
    fun landingOnAnUnknownSystemChangesNothing() {
        val before = start(Action.TapItem(Column.Systems, 3)).focus
        assertEquals(before, StripReducer.landOn(before, "gone", content, isNarrow = false))
    }

    // Settings › Controls › Push right to play: the first Right arms, the second plays.
    private fun pushed(
        focus: StripFocus,
        action: Action,
        on: StripContent = content,
        narrow: Boolean = false,
        enabled: Boolean = true,
        armed: Boolean = false,
    ) = StripReducer.reduce(focus, action, on, narrow, enabled, armed)

    private val heldRight = Action.Move(Direction.Right, isRepeat = true)

    @Test
    fun withPushRightOffRightOnAListRowNeitherArmsNorPlays() {
        val focus = onSnesGames().focus
        val r = pushed(focus, right, enabled = false, armed = true)
        assertEquals(emptyList<StripIntent>(), r.intents)
        assertEquals(false, r.armsRight)
        assertEquals(focus, r.focus)
    }

    @Test
    fun theFirstRightOnAListRowArmsAndDoesNotPlay() {
        val focus = onSnesGames().focus
        val r = pushed(focus, right)
        assertEquals(emptyList<StripIntent>(), r.intents)
        assertEquals(true, r.armsRight)
        assertEquals(focus, r.focus)
    }

    @Test
    fun theSecondRightOnTheArmedRowPlaysTheFocusedGameLikeA() {
        val focus = onSnesGames().then(down).focus
        val byRight = pushed(focus, right, armed = true)
        val byA = StripReducer.reduce(focus, Action.Enter, content, false)
        assertEquals(listOf(StripIntent.Play("snes", 1)), byRight.intents)
        assertEquals(byA.intents, byRight.intents)
        assertEquals(focus, byRight.focus)
    }

    @Test
    fun theRightThatEntersTheListFromTheSystemColumnArmsNothing() {
        val onSnes = Reduction(StripFocus()).repeat(3, down).focus
        val entered = pushed(onSnes, right)
        assertEquals(emptyList<StripIntent>(), entered.intents)
        assertEquals(false, entered.armsRight)
        assertEquals(Column.Games, entered.focus.column)
        // Held on from there: the repeats do nothing.
        assertEquals(emptyList<StripIntent>(), pushed(entered.focus, heldRight).intents)
        assertEquals(false, pushed(entered.focus, heldRight).armsRight)
    }

    @Test
    fun aHeldRightNeverArmsOrPlaysEvenWhenArmed() {
        val focus = onSnesGames().focus
        val r = pushed(focus, heldRight, armed = true)
        assertEquals(emptyList<StripIntent>(), r.intents)
        assertEquals(false, r.armsRight)
        assertEquals(focus, r.focus)
    }

    @Test
    fun aGridKeepsRightMovingBetweenCovers() {
        val grid = Reduction(StripFocus()).repeat(4, down).then(Action.Enter).focus
        assertEquals(Column.Games, grid.column)
        val r = pushed(grid, right, armed = true)
        assertEquals(emptyList<StripIntent>(), r.intents)
        assertEquals(false, r.armsRight)
        assertEquals(1, r.focus.row)
    }

    @Test
    fun theSystemColumnAndAnEmptyListAreUnchanged() {
        val r = pushed(StripFocus(), right)
        assertEquals(emptyList<StripIntent>(), r.intents)
        assertEquals(false, r.armsRight)
        assertEquals(Column.Systems, r.focus.column)
    }

    @Test
    fun onANarrowScreenWithDetailsRightFromTheListGoesToTheDetailsStopAndArmsNothing() {
        val focus = onSnesGames(narrow = true).focus
        val r = pushed(focus, right, narrow = true)
        assertEquals(Column.Details, r.focus.column)
        assertEquals(false, r.armsRight)
        assertEquals(emptyList<StripIntent>(), r.intents)
        // Even a stale arming does not play from the list.
        assertEquals(emptyList<StripIntent>(), pushed(focus, right, narrow = true, armed = true).intents)
        assertEquals("off: the same", Column.Details, pushed(focus, right, narrow = true, enabled = false).focus.column)
    }

    @Test
    fun onANarrowScreenAnEntryWithoutDetailsStillArmsAndPlaysInItsList() {
        val noDetails = content.copy(systems = content.systems.map { if (it.id == "apps") it.copy(hasDetails = false) else it })
        val atApps = Reduction(StripFocus()).repeat(2, down, narrow = true).focus
        val appsGames = pushed(atApps, Action.Enter, on = noDetails, narrow = true).focus
        assertEquals(Column.Games, appsGames.column)
        val first = pushed(appsGames, right, on = noDetails, narrow = true)
        assertEquals("Apps has no details stop: Right arms", true, first.armsRight)
        assertEquals(Column.Games, first.focus.column)
        assertEquals(listOf(StripIntent.Play("apps", 0)), pushed(appsGames, right, on = noDetails, narrow = true, armed = true).intents)
    }

    @Test
    fun atTheDetailsStopTheFirstRightArmsAndTheSecondPlays() {
        val details = pushed(onSnesGames(narrow = true).focus, right, narrow = true).focus
        val first = pushed(details, right, narrow = true)
        assertEquals(true, first.armsRight)
        assertEquals(Column.Details, first.focus.column)
        assertEquals(emptyList<StripIntent>(), first.intents)
        assertEquals(listOf(StripIntent.Play("snes", 0)), pushed(details, right, narrow = true, armed = true).intents)
        assertEquals("held never arms", false, pushed(details, heldRight, narrow = true, armed = true).armsRight)
        assertEquals("held never plays", emptyList<StripIntent>(), pushed(details, heldRight, narrow = true, armed = true).intents)
        assertEquals("Left goes back", Column.Games, pushed(details, Action.Move(Direction.Left), narrow = true).focus.column)
        assertEquals("off: Right at details does nothing", emptyList<StripIntent>(), pushed(details, right, narrow = true, enabled = false).intents)
    }
}
