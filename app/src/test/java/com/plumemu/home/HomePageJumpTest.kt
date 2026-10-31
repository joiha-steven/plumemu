package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L1 / R1 jump a letter only in a list in name order (the same lists that have a letter
 * rail, [HomeEntry.hasLetterRail]). In Recent, and in a system's list sorted by last played or year, they jump
 * one page: as many rows as the list shows ([com.plumemu.coreui.theme.PlumLayout.listRowsThatFit]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePageJumpTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private var page = 0
    private var snesIndex = 0

    // Titles in no letter order, as a list by last played is: a letter jump here would land anywhere.
    private val titles = List(60) { i -> listOf("Zelda", "Asterix", "Metroid", "Bomberman", "Castlevania", "Wario")[i % 6] + " $i" }

    private fun rows(
        prefix: String,
        titles: List<String>,
    ) = titles.mapIndexed { i, title -> GameRowModel("$prefix-$i", title, "USA", systemAbbr = "SNES", systemId = "snes") }

    private fun show(
        sortedByName: Boolean,
        systemTitles: List<String> = titles,
    ) {
        val content = HomeContent.of(rows("recent", titles), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows("snes", systemTitles))), sortedByName = sortedByName)
        snesIndex = content.entries.indexOfFirst { it.id == "snes" }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, onDebug = {}) }
            page = PlumTheme.layout.listRowsThatFit
            HomeScreen(content, router, HomeCallbacks(games = games))
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun focusedRow(): Int {
        games.launched.clear()
        send(Action.Play)
        return games.launched.single().substringAfterLast('-').toInt()
    }

    /** Recent is the first entry ([snesIndex] is the system's). */
    private fun openEntry(index: Int) = send(*List(index) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    @Test
    fun inRecentL1R1JumpAPage() {
        show(sortedByName = true)
        openEntry(0)
        assertEquals(0, focusedRow())
        send(Action.NextSystem) // R1
        assertEquals("R1 jumps one page", page, focusedRow())
        send(Action.NextSystem)
        assertEquals("and another", 2 * page, focusedRow())
        send(Action.PreviousSystem) // L1
        assertEquals("L1 jumps a page back", page, focusedRow())
        send(Action.PreviousSystem, Action.PreviousSystem)
        assertEquals("never before the first row", 0, focusedRow())
    }

    @Test
    fun inAListByLastPlayedL1R1JumpAPageAndStopAtTheEnd() {
        show(sortedByName = false)
        openEntry(snesIndex)
        send(Action.NextSystem)
        assertEquals("R1 jumps one page", page, focusedRow())
        repeat(titles.size / page + 1) { send(Action.NextSystem) }
        assertEquals("never past the last row", titles.lastIndex, focusedRow())
    }

    @Test
    fun inAListByNameL1R1StillJumpALetter() {
        val byName = titles.sorted()
        show(sortedByName = true, systemTitles = byName)
        openEntry(snesIndex)
        assertEquals('A', byName[focusedRow()].first())
        send(Action.NextSystem)
        val jumped = focusedRow()
        assertEquals("R1 lands on the first game of the next letter", byName.indexOfFirst { it.startsWith("B") }, jumped)
    }
}
