package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Apps grid's bar at 576 x 432: what stays when it is too narrow (X Search and START Menu go before B Back; A and SELECT stay). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class AppsGridHintBarTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun isPlaced(action: Action): Boolean =
        compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    private fun everySetting(check: (TextSize, List<Action>) -> Unit) {
        var size by mutableStateOf(TextSize.Default)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { AppsGridBoard(focused = 8) } }
        for (s in TextSize.entries) {
            size = s
            compose.waitForIdle()
            check(s, listOf(Action.Play, Action.Back, Action.Search, Action.Menu, Action.JumpLetterNext, Action.ToggleView).filter(::isPlaced))
        }
    }

    @Test
    fun backAndSelectStayAtEveryTextSize() {
        everySetting { size, kept ->
            assertEquals("$size: $kept", listOf(Action.Play, Action.Back, Action.ToggleView), kept.filter { it in setOf(Action.Play, Action.Back, Action.ToggleView) })
        }
    }

    @Test
    fun searchAndMenuGoBeforeBack() {
        everySetting { size, kept ->
            if (Action.Search in kept) assertEquals("$size: $kept", true, Action.Menu in kept)
            if (Action.Menu in kept) assertEquals("$size: $kept", true, Action.Back in kept)
        }
    }
}
