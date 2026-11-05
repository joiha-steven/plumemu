package com.plumemu.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The games list fades its last row whole: no clear share at the bottom edge, so a row cut there is never left at half its height. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GamesPageFadesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theGamesListHasNoClearShareAndFadesAWholeRow() {
        var clear = -1f
        compose.setContent {
            PlumTheme {
                val fades = gamesListFades(rememberLazyListState(), rememberListGlide(stride = 40.dp), 80f) { 0 }
                clear = fades.clearShare
            }
        }
        compose.waitForIdle()
        assertEquals("clear share", 0f, clear, 0f)
    }

    /**
     * Both forms build the same fades, so only the call site can be told apart: the games list in `GamesPages.kt` must go through
     * [gamesListFades] (this test and the function's contract above), not build its own.
     */
    @Test
    fun theGamesListBuildsItsFadesThroughGamesListFades() {
        val source = java.io.File("src/main/java/com/plumemu/home/GamesPages.kt").readText()
        assertTrue("GamesPages.kt calls gamesListFades", "gamesListFades(" in source)
        assertTrue("GamesPages.kt does not build ListEdgeFades itself", "ListEdgeFades(" !in source)
    }
}
