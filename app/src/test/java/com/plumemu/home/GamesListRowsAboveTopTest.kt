package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import com.plumemu.coreui.components.EdgeClearsKey
import com.plumemu.coreui.theme.PlumDimens
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A games list never draws a row above its own top edge at rest (under the top bar's title), whatever happens to its rows:
 * one added at the top (the game just played moves to the top of Recent), removed, or moved.
 */
abstract class GamesListRowsAboveTopBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState
    private var content by mutableStateOf(FakeHomeContent.board())

    protected fun show(
        entryId: String,
        focusRow: Int,
        edgeFadeOn: Boolean = true,
    ) {
        entry = entryId
        val systemIndex = content.entries.indexOfFirst { it.id == entryId }
        val actions = listOf(Action.TapItem(Column.Systems, systemIndex), Action.Enter, Action.TapItem(Column.Games, focusRow))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(edgeFadeOn = edgeFadeOn)) { HomeAt(content, actions, onAction = { state.dispatch(it) }, onState = { state = it }) } }
        compose.waitForIdle()
    }

    protected fun change(
        entryId: String,
        rows: (List<GameRowModel>) -> List<GameRowModel>,
    ) {
        compose.runOnIdle {
            content = content.withEntries(content.entries.map { if (it.id == entryId) it.copy(rows = rows(it.rows)) else it })
        }
        compose.waitForIdle()
    }

    /**
     * A row above the list's top edge may be composed (the glide's room) but never drawn: the list's mask (kept with Fade list edges off too) must clear the room there
     * (`EdgeFades.top` or `EdgeFades.clearTop` above 0). And the focused row is whole inside the list, whatever happened to the rows.
     */
    protected fun assertNoRowDrawnAboveTheTop(where: String) {
        val focused = compose.runOnIdle { state.focus.rowOf(entry) }
        val inList = hasTestTag(HomeTags.GAMES_PAGE) and hasAnyDescendant(hasTestTag(HomeTags.game(focused)))
        val list = compose.onNode(inList, useUnmergedTree = true)
        val bounds = list.getUnclippedBoundsInRoot()
        val fades = compose.onNode(SemanticsMatcher.keyIsDefined(EdgeClearsKey) and hasAnyDescendant(hasTestTag(HomeTags.game(focused))), useUnmergedTree = true).fetchSemanticsNode().config[EdgeClearsKey]
        val heightPx = with(compose.density) { bounds.height.toPx() }
        val fadePx = with(compose.density) { PlumDimens.listFadeBand.toPx() }
        val roomCleared = fades.top(fadePx, heightPx) > 0f || fades.clearTop(heightPx) > 0f
        val rows = (0 until 40).filter { compose.onAllNodesWithTag(HomeTags.game(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val tops = rows.associateWith { compose.onNodeWithTag(HomeTags.game(it), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value }
        val above = tops.filter { it.value < bounds.top.value - 0.5f }
        assertTrue("$where: rows $above start above the list's top ${bounds.top} and the room there is not cleared", above.isEmpty() || roomCleared)
        val row = compose.onNodeWithTag(HomeTags.game(focused), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$where: focused row $focused ${row.top}..${row.bottom} is not whole in the list ${bounds.top}..${bounds.bottom}", row.top.value >= bounds.top.value - 0.5f && row.bottom.value <= bounds.bottom.value + 0.5f)
    }

    private var entry = ""

    protected fun added(rows: List<GameRowModel>) = listOf(rows.first().copy(id = "just-played", title = "Just played")) + rows

    protected fun withoutFirst(rows: List<GameRowModel>) = rows.drop(1)

    protected fun lastToFirst(rows: List<GameRowModel>) = listOf(rows.last()) + rows.dropLast(1)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GamesListRowsAboveTop768Test : GamesListRowsAboveTopBase() {
    @Test fun recentAddedAtTopFromTop() = run("recent", 0, ::added)

    @Test fun recentAddedAtTopFromDeep() = run("recent", 8, ::added)

    @Test fun recentFirstRemoved() = run("recent", 0, ::withoutFirst)

    @Test fun recentLastMovedToTop() = run("recent", 0, ::lastToFirst)

    @Test fun favoritesAddedAtTop() = run("favorites", 0, ::added)

    @Test fun systemAddedAtTop() = run("snes", 0, ::added)

    // Settings' "Fade list edges" off: no ramp, but a row never draws behind the title.
    @Test fun recentAddedAtTopFromDeepFadesOff() = run("recent", 8, ::added, edgeFadeOn = false)

    @Test fun recentFirstRemovedFadesOff() = run("recent", 0, ::withoutFirst, edgeFadeOn = false)

    private fun run(
        entry: String,
        focus: Int,
        edit: (List<GameRowModel>) -> List<GameRowModel>,
        edgeFadeOn: Boolean = true,
    ) {
        show(entry, focus, edgeFadeOn)
        assertNoRowDrawnAboveTheTop("$entry before")
        change(entry, edit)
        assertNoRowDrawnAboveTheTop("$entry after")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GamesListRowsAboveTop576Test : GamesListRowsAboveTopBase() {
    @Test fun recentAddedAtTopFromTop() {
        show("recent", 0)
        change("recent", ::added)
        assertNoRowDrawnAboveTheTop("576 recent")
    }
}
