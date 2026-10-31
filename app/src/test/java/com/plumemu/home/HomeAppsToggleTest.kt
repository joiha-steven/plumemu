package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Show Android apps switched while a system is focused: the focus stays on that system (core-input reconciles by id). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeAppsToggleTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theFocusedSystemStaysFocusedWhenTheAppsEntryLeavesAndComesBack() {
        val board = FakeHomeContent.board()
        val gba = FIRST_SYSTEM_INDEX + 1
        val content = mutableStateOf(board)
        lateinit var state: StripState
        compose.setContent { PlumTheme { HomeAt(content.value, listOf(Action.TapItem(Column.Systems, gba)), onState = { state = it }) } }
        compose.runOnIdle { assertEquals("gba", state.focus.systemId) }

        content.value = withApps(board, show = false)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("gba", state.focus.systemId)
            assertEquals("one row up without Apps", gba - 1, state.focus.systemIndex)
        }
        compose.onNodeWithTag(HomeTags.system(gba - 1), useUnmergedTree = true).assertIsDisplayed()

        content.value = withApps(board, show = true)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("gba" to gba, state.focus.systemId to state.focus.systemIndex) }
    }

    private fun withApps(
        board: HomeContent,
        show: Boolean,
    ): HomeContent =
        HomeContent.of(
            recent = board.entries[0].rows,
            favorites = board.entries[1].rows,
            apps = board.entries[2].rows,
            systems = board.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), it.rows, it.coverShape) },
            hasBoxArt = board.hasBoxArt,
            showApps = show,
        )
}
