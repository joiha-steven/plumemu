package com.plumemu.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A row the game menu's end has hidden takes no tap; the rows above it still do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PanelHiddenEndTapTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun tapsAt(y: Float): Int {
        var taps = 0
        compose.setContent {
            Box(Modifier.size(200.dp).testTag(BOX).ignoringHiddenEnd { HIDDEN_PX }) {
                Box(Modifier.fillMaxWidth().size(200.dp).clickable { taps++ })
            }
        }
        compose.onNodeWithTag(BOX).performTouchInput { click(Offset(centerX, height - y)) }
        compose.waitForIdle()
        return taps
    }

    @Test
    fun aTapOnTheHiddenEndReachesNothing() = assertEquals(0, tapsAt(HIDDEN_PX / 2))

    @Test
    fun aTapAboveItReachesTheRow() = assertEquals(1, tapsAt(HIDDEN_PX * 2))

    private companion object {
        const val BOX = "box"
        const val HIDDEN_PX = 40f
    }
}
