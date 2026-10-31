package com.plumemu.home

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixNameState
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import org.junit.Assert.assertEquals
import org.junit.Test

/** What A says in the hint bar while the cursor is on a text field: "Type" (A opens the keyboard), not "Select". */
class KeyboardHintsTest {
    private val game = GameRef("7", "Zork")
    private val finder = TitleFinder(listOf(TitleChoice("Zork Quest", "USA", 1994, null)))

    private fun labels(state: FixNameState) = panelHints(OpenPanel(game, PanelScreen.FixName(state))).map { it.label }

    @Test
    fun fixNameOnTheFieldSaysTypeWithOrWithoutResultsAndWhileLoading() {
        val withResults = FixNameState("zork", finder).also { it.showResults("zork", finder.find("zork")) }
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), labels(withResults))
        val noMatch = FixNameState("qwxv", finder).also { it.showResults("qwxv", finder.find("qwxv")) }
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), labels(noMatch))
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), labels(FixNameState("zork", finder = null)))
    }

    @Test
    fun fixNameOnAResultSaysSelect() {
        val state = FixNameState("zork", finder).also { it.showResults("zork", finder.find("zork")) }
        state.onAction(Action.Move(Direction.Down))
        assertEquals(listOf(R.string.home_hint_select, R.string.home_hint_back), labels(state))
    }

    @Test
    fun ownNameSaysTypeOnTheFieldAndSelectOnSave() {
        val state = OwnNameState("Zork")
        val panel = OpenPanel(game, PanelScreen.OwnName(state))
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), panelHints(panel).map { it.label })
        state.onAction(Action.Move(Direction.Down))
        assertEquals(listOf(R.string.home_hint_select, R.string.home_hint_back), panelHints(panel).map { it.label })
    }

    @Test
    fun searchSaysTypeOnTheFieldAndPlayOnAResult() {
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), searchHints(inField = true, focusedIsFavorite = false).map { it.label })
        assertEquals(R.string.home_hint_play, searchHints(inField = false, focusedIsFavorite = false).first().label)
    }

    private fun boxArt(): Pair<FixBoxArtState, OpenPanel> {
        val state = FixBoxArtState("zork", "PlayStation")
        return state to OpenPanel(game, PanelScreen.FixBoxArt(state))
    }

    private fun boxArtLabels(panel: OpenPanel) = panelHints(panel).map { it.label }

    @Test
    fun fixBoxArtSaysTypeOnTheFieldSearchOnSearchAndSelectOnAGameAndNoBoxArt() {
        val (state, panel) = boxArt()
        assertEquals(listOf(R.string.account_hint_type, R.string.home_hint_back), boxArtLabels(panel))
        state.onAction(Action.Move(Direction.Down))
        assertEquals("Search row", listOf(R.string.home_hint_search, R.string.home_hint_back), boxArtLabels(panel))
        state.searching("zork")
        state.showSearch("zork", BoxArtSearchOutcome.Found(listOf(BoxArtHit("1", "Zork", 1980, null, "PC", null))))
        state.onAction(Action.Move(Direction.Down))
        assertEquals("a game", listOf(R.string.home_hint_select, R.string.home_hint_back), boxArtLabels(panel))
        state.onAction(Action.Move(Direction.Down))
        assertEquals("No box art", listOf(R.string.home_hint_select, R.string.home_hint_back), boxArtLabels(panel))
    }

    @Test
    fun fixBoxArtSaysOnlyBackWhileAPickIsStored() {
        val (state, panel) = boxArt()
        state.onAction(Action.Move(Direction.Down))
        state.storing()
        assertEquals(listOf(R.string.home_hint_back), boxArtLabels(panel))
    }
}
