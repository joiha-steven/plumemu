package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.game.FixNameState
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.TitleList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Settings › Library › Unidentified games and its Fix name…, by gamepad and by touch. */
internal class SettingsUnidentifiedTest : SettingsModelHarness() {
    /** Fix name… here types with Android's keyboard; Plumemu's: SettingsNameFixOwnKeyboardTest. */
    @Before
    fun typeWithAndroidsKeyboard() = useAndroidKeyboard()

    private val unidentified =
        listOf(
            SettingsGame(21, "zork quest 2", "Game Boy Advance"),
            SettingsGame(22, "galfn island", "Super Nintendo"),
            SettingsGame(23, "dravon", "Test System"),
        )
    private val titles =
        listOf(
            TitleChoice("Zork Quest II", "USA", 1993, "Test DAT"),
            TitleChoice("Galfen Island", "", 1995, "Test DAT"),
        )

    private class FakeNames(var list: (Long) -> TitleList?) : SettingsNameActions {
        val asked = mutableListOf<Long>()
        val chosen = mutableListOf<Pair<Long, TitleChoice>>()
        val own = mutableListOf<Pair<Long, String>>()
        var stores = true

        /** Why box art could not be looked up by the new name, answered with a stored name. */
        var boxArt: RenameBoxArtNotice? = null

        /** Set: the list is answered only when the test calls it (a first, slow build). */
        var held: (() -> Unit)? = null
        var hold = false

        override fun titleList(
            gameId: Long,
            onDone: (TitleList?) -> Unit,
        ) {
            asked += gameId
            if (hold) held = { onDone(list(gameId)) } else onDone(list(gameId))
        }

        override fun setChosenTitle(
            gameId: Long,
            choice: TitleChoice,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) {
            chosen += gameId to choice
            onDone(stores, boxArt.takeIf { stores })
        }

        override fun setOwnName(
            gameId: Long,
            name: String,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) {
            own += gameId to name
            onDone(stores, boxArt.takeIf { stores })
        }
    }

    private val names = FakeNames { id -> if (id == 23L) TitleList.NoList("Test System") else TitleList.Ready(TitleFinder(titles)) }

    private fun fixName(): FixNameState = (model.nameFix.open!!.screen as PanelScreen.FixName).typing

    private fun openList() {
        actions.nameActions = names
        model.content = SettingsContent(unidentifiedGames = unidentified)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnidentifiedGames)
        press(Action.Enter)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
    }

    @Test
    fun unidentifiedGamesIsListedOnlyWhileThereAreSomeBeforeHiddenGames() {
        assertFalse(SettingsOption.UnidentifiedGames in SettingsSection.Library.rowsIn(SettingsContent()))
        val rows = SettingsSection.Library.rowsIn(SettingsContent(unidentifiedGames = unidentified, hiddenGames = unidentified.take(1)))
        assertEquals(listOf(SettingsOption.UnidentifiedGames, SettingsOption.HiddenGames), rows.dropLast(2).takeLast(2)) // the two Recent rows come after
    }

    @Test
    fun aOpensFixNameWithTheShownNameAndAPickReturnsToTheListInPlace() {
        openList()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(22L), names.asked)
        assertEquals(PanelPage.FixName, model.focus.page)
        val state = fixName()
        assertEquals("galfn island", state.query)
        state.showResults(state.query, state.finder!!.find(state.query))
        assertEquals("Galfen Island", state.results.first().name)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(22L to titles[1]), names.chosen)
        assertNull(model.nameFix.open)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
        assertEquals("the next game moves up into the place", 1, model.focus.panelIndex)
    }

    @Test
    fun aPickSaysWhyBoxArtWasNotLookedUpAndNothingWhenItWas() {
        openList()
        press(Action.Enter)
        val state = fixName()
        state.showResults(state.query, state.finder!!.find(state.query))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals("looked up: nothing to say", null, model.work.notice)

        names.boxArt = RenameBoxArtNotice.NoWifi
        press(Action.Enter)
        val next = fixName()
        next.showResults(next.query, next.finder!!.find(next.query))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(2, names.chosen.size)
        assertEquals(SettingsOption.UnidentifiedGames to SettingsNotice.RenameNoWifi, model.work.notice)
    }

    @Test
    fun everyReasonAfterARenameHasItsLine() {
        val cases =
            listOf(
                RenameBoxArtNotice.NoWifi to R.string.settings_rename_box_art_no_wifi,
                RenameBoxArtNotice.SignInNeeded to R.string.settings_rename_box_art_sign_in,
                RenameBoxArtNotice.Paused to R.string.settings_rename_box_art_paused,
                RenameBoxArtNotice.DailyLimit(5_000) to R.string.settings_rename_box_art_daily_limit,
                RenameBoxArtNotice.ServerClosed(6_000) to R.string.settings_rename_box_art_server_closed,
            )
        for ((notice, text) in cases) assertEquals("$notice", text, SettingsNotice.afterRename(notice).text)
    }

    @Test
    fun theHintBarSaysATypeOnTheFixNameFieldAndChooseOnAResult() {
        openList()
        move(Direction.Down)
        press(Action.Enter)
        assertEquals("on the field A opens the keyboard", R.string.account_hint_type, confirmHint(model))
        val state = fixName()
        state.showResults(state.query, state.finder!!.find(state.query))
        assertEquals("with results too", R.string.account_hint_type, confirmHint(model))
        move(Direction.Down)
        assertEquals("on a result A chooses", R.string.settings_hint_choose, confirmHint(model))
    }

    @Test
    fun theHintBarSaysATypeOnTheOwnNameFieldAndChooseOnSave() {
        openList()
        move(Direction.Down)
        move(Direction.Down)
        press(Action.Enter)
        assertTrue(model.nameFix.open!!.screen is PanelScreen.OwnName)
        assertEquals(R.string.account_hint_type, confirmHint(model))
        move(Direction.Down)
        assertEquals(R.string.settings_hint_choose, confirmHint(model))
    }

    @Test
    fun fixNameOpensAtOnceReadingTheNamesThenShowsThem() {
        names.hold = true
        openList()
        press(Action.Enter)
        assertEquals("open before the list is read", PanelPage.FixName, model.focus.page)
        val state = fixName()
        assertTrue(state.isLoading)
        names.held!!.invoke()
        assertFalse(state.isLoading)
        assertEquals(TitleFinder::class, state.finder!!::class)
    }

    @Test
    fun aTapOnAResultPicksItAndBackLeavesWithoutStoring() {
        openList()
        // Touch: a tap focuses the game, a second tap opens Fix name….
        model.onTapPanelItem(0, actions.shown, wide)
        model.onTapPanelItem(0, actions.shown, wide)
        val state = fixName()
        state.showResults(state.query, state.finder!!.find(state.query))
        press(Action.Back)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
        assertEquals(emptyList<Pair<Long, TitleChoice>>(), names.chosen)
        press(Action.Enter)
        val again = fixName()
        again.showResults(again.query, again.finder!!.find(again.query))
        press(Action.TapItem(Column.Details, 0))
        assertEquals(listOf(21L to titles[0]), names.chosen)
    }

    @Test
    fun aSystemWithoutAListOpensOwnNameThatRefusesBlank() {
        openList()
        move(Direction.Down)
        move(Direction.Down)
        press(Action.Enter)
        val page = model.nameFix.open!!
        assertEquals(TitleList.NoList("Test System"), page.noList)
        val own = (page.screen as PanelScreen.OwnName).typing
        own.onTextChange(" ")
        press(Action.Enter)
        assertFalse("A on the field opens the keyboard; it does not save", own.isRefused)
        assertTrue(own.keyboardUp)
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertTrue(own.isRefused)
        assertEquals(emptyList<Pair<Long, String>>(), names.own)
        own.onTextChange("Dravon Deluxe")
        press(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
        assertEquals(listOf(23L to "Dravon Deluxe"), names.own)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
    }

    @Test
    fun failuresAreSaidInThePanel() {
        openList()
        names.stores = false
        press(Action.Enter)
        val state = fixName()
        state.showResults(state.query, state.finder!!.find(state.query))
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(SettingsOption.UnidentifiedGames to SettingsNotice.NameFixFailed, model.work.notice)

    }

    @Test
    fun anUnreadableNameListIsSaidOnTheFixNamePageNotOnTheListRow() {
        openList()
        names.list = { null }
        press(Action.Enter)
        assertEquals("the page stays open where the person looks", PanelPage.FixName, model.focus.page)
        val page = model.nameFix.open!!
        assertEquals("the page names the system whose list failed", unidentified[0].systemName, page.unreadableSystem)
        assertTrue(page.screen is PanelScreen.OwnName)
        assertNull("no notice on the list row", model.work.notice)
        // A person can still type a name and save it, by key.
        (page.screen as PanelScreen.OwnName).typing.onTextChange("Galf Island")
        press(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
        assertEquals(listOf(unidentified[0].id to "Galf Island"), names.own)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
    }

    @Test
    fun anUnreadableNameListStillSavesATypedNameByKey() {
        openList()
        names.list = { null }
        press(Action.Enter)
        val own = (model.nameFix.open!!.screen as PanelScreen.OwnName).typing
        own.onTextChange("Galf Island")
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(listOf(unidentified[0].id to "Galf Island"), names.own)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
    }

    @Test
    fun aGameThatLeftTheLibraryClosesFixNameWithItsOwnMessage() {
        openList()
        names.list = { TitleList.GameGone }
        press(Action.Enter)
        assertEquals("back on the list", PanelPage.UnidentifiedGames, model.focus.page)
        assertNull(model.nameFix.open)
        assertEquals(SettingsOption.UnidentifiedGames to SettingsNotice.GameGone, model.work.notice)
    }

    @Test
    fun aGoneGameOnTheLastRowLeavesTheListOnTheRemainingRowAndTheLastOneClosesThePanel() {
        actions.nameActions = names
        model.content = SettingsContent(unidentifiedGames = unidentified.take(2))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnidentifiedGames)
        press(Action.Enter)
        move(Direction.Down)
        names.list = { TitleList.GameGone }
        press(Action.Enter)
        assertEquals(PanelPage.UnidentifiedGames, model.focus.page)
        assertEquals("the focus is on a row that exists", 0, model.focus.panelIndex)
        assertEquals(SettingsOption.UnidentifiedGames to SettingsNotice.GameGone, model.work.notice)
        names.list = { TitleList.Unavailable }
        press(Action.Enter)
        assertEquals("A still opens the remaining row", PanelPage.FixName, model.focus.page)
        assertEquals(unidentified[0].id, model.nameFix.open!!.gameId)
        press(Action.Back)
        // The only game gone: nothing is left to list, the panel closes.
        model.content = SettingsContent(unidentifiedGames = unidentified.take(1))
        names.list = { TitleList.GameGone }
        press(Action.Enter)
        assertNull(model.focus.page)
        assertNull(model.nameFix.open)
    }

    @Test
    fun theLastFixedGameClosesThePanel() {
        actions.nameActions = names
        model.content = SettingsContent(unidentifiedGames = unidentified.take(1))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnidentifiedGames)
        press(Action.Enter)
        press(Action.Enter)
        val state = fixName()
        state.showResults(state.query, state.finder!!.find(state.query))
        move(Direction.Down)
        press(Action.Enter)
        assertNull(model.focus.page)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }
}
