package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
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

/** The Nova's slid-out list strip at the details stop shows years only: no region code (USA...), while the games stop still shows them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class NarrowDetailsStopYearsOnlyTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val toGames = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)

    private fun regionTexts(): Int =
        listOf("USA", "EUR", "JPN").sumOf { code -> compose.onAllNodes(hasText(code), useUnmergedTree = true).fetchSemanticsNodes().count { SemanticsProperties.InvisibleToUser !in it.config } }

    private lateinit var state: com.plumemu.coreinput.strip.StripState

    /** The artwork board with the SNES years taken out, so every row falls back to its region (a library whose rows show regions). */
    private fun noYears(): HomeContent {
        val content = FakeHomeContent.withArtwork()
        return content.withEntries(content.entries.map { e -> e.copy(rows = e.rows.map { r -> r.copy(details = r.details?.copy(year = null)) }) })
    }

    private fun show(actions: List<Action>) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(noYears(), actions, onState = { state = it }) } }
        compose.waitForIdle()
    }

    @Test
    fun theGamesStopStillShowsRegionCodes() {
        show(toGames)
        assertTrue("region codes are in the rows", regionTexts() > 0)
    }

    @Test
    fun theDetailsStopShowsNoRegionCode() {
        show(toGames)
        for (move in listOf(Direction.Down, Direction.Right)) {
            compose.runOnIdle { state.dispatch(Action.Move(move)) }
            compose.waitForIdle()
        }
        assertTrue("focus is at the details stop: ${state.focus.column}", state.focus.column == Column.Details)
        assertTrue("no region code at the details stop, found ${regionTexts()}", regionTexts() == 0)
    }
}
