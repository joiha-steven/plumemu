package com.plumemu.home

import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** B is not printed on the games list's bar, but it still goes back to the systems column. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeBackOnGamesListTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun backOnTheGamesListReturnsToTheSystemsColumnWithoutAHintForIt() {
        var state: StripState? = null
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onState = { state = it }) } }
        compose.waitForIdle()
        assertEquals(Column.Games, compose.runOnIdle { state!!.focus.column })
        assertEquals("no B Back hint on the games list", 0, compose.onAllNodesWithTag(HomeTags.hint(Action.Back), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { state!!.dispatch(Action.Back) }
        compose.waitForIdle()
        assertEquals(Column.Systems, compose.runOnIdle { state!!.focus.column })
    }
}
