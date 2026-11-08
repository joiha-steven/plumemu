package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A panel's buttons keep [FlatPartDimens.focusRimRoom] above and below them, so a focused bottom button's rim, glow and shadow (which reach 10 dp
 * past its cap; `FlatPartLookTest` pins that against the room) are never cut by the panel's scroll clip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelButtonRimRoomTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theButtonsRowKeepsTheRimRoomAboveAndBelow() {
        compose.setContent { Box(Modifier.testTag("room")) { Box(Modifier.ringRoomAboveAndBelow()) { Box(Modifier.size(10.dp).testTag("part")) } } }
        compose.waitForIdle()
        val d = compose.density.density
        val room = compose.onNodeWithTag("room").fetchSemanticsNode().boundsInRoot
        val part = compose.onNodeWithTag("part").fetchSemanticsNode().boundsInRoot
        assertEquals("room above", FlatPartDimens.focusRimRoom.value * d, part.top - room.top, 0.5f)
        assertEquals("room below", FlatPartDimens.focusRimRoom.value * d, room.bottom - part.bottom, 0.5f)
        assertTrue("the room is at least the glued rim's 10 dp reach", FlatPartDimens.focusRimRoom >= 10.dp)
    }
}
