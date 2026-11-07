package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A finger has scrolled a games list away from the focused row: rows a background scan adds meanwhile do not throw it back to the focus. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GamesListDragKeptTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState
    private var content by mutableStateOf(FakeHomeContent.board())

    private fun scrolled(): Float =
        compose.onNode(hasTestTag(HomeTags.GAMES_PAGE), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f

    @Test
    fun rowsAddedWhileDraggedLeaveTheListWhereTheFingerPutIt() {
        val id = "recent"
        val systemIndex = content.entries.indexOfFirst { it.id == id }
        val actions = listOf(Action.TapItem(Column.Systems, systemIndex), Action.Enter, Action.TapItem(Column.Games, 0))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(content, actions, onAction = { state.dispatch(it) }, onState = { state = it }) } }
        compose.waitForIdle()
        compose.onNode(hasTestTag(HomeTags.GAMES_PAGE), useUnmergedTree = true).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.1f, durationMillis = 3000) }
        compose.waitForIdle()
        val before = scrolled()
        assertTrue("the drag scrolled the list", before > 0f)
        // A scan adds a row at the end.
        compose.runOnIdle {
            content = content.withEntries(content.entries.map { if (it.id == id) it.copy(rows = it.rows + it.rows.last().copy(id = "found-by-scan", title = "Found by scan")) else it })
        }
        compose.waitForIdle()
        assertEquals("the list stayed where the finger left it", before, scrolled(), 0.5f)
    }
}
