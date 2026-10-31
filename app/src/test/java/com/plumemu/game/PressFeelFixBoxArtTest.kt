package com.plumemu.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fix box art's result rows press like option rows: the focused one dips while the confirm button is held, the others do not. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelFixBoxArtTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())

    private fun pixels(key: String): List<Int> {
        val map = compose.onNodeWithTag(FixBoxArtTags.fill(key), useUnmergedTree = true).captureToImage().toPixelMap()
        return buildList { for (y in 0 until map.height step 1) for (x in 0 until map.width step 1) add(map[x, y].hashCode()) }
    }

    @Test
    fun theConfirmButtonDipsTheFocusedResultOnly() {
        val state = BoxArtPanelFixtures.results().also { repeat(2) { _ -> it.onAction(Action.Move(Direction.Down)) } }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides held) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val focused = boxArtHitKey(state.hitAt(state.cursor)!!)
        val other = boxArtHitKey(state.hitAt(state.cursor + 1)!!)
        val restingFocused = pixels(focused)
        val restingOther = pixels(other)
        held.value = setOf("A")
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        assertNotEquals("the focused result dips", restingFocused, pixels(focused))
        assertEquals("another result does not", restingOther, pixels(other))
    }
}
