package com.plumemu.home

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.LocalButtonLetters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With Confirm with B the hints print the physical letters: the Play hint says "B" and the Back hint says "A".
 * The button printed B must light the Play cap, and the button printed A the Back cap (the router's A/B swap
 * must not rename the buttons the person sees).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintPressedConfirmWithBTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val router = InputRouter(CoroutineScope(Job()), InputSettings(swapAB = true), onDebug = {}) { }

    private fun capPixel(index: Int): Color {
        val image = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true)[index].captureToImage()
        return image.toPixelMap()[(image.width * 0.16f).toInt(), image.height / 2]
    }

    private fun press(keyCode: Int) {
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
    }

    private fun release(keyCode: Int) {
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun theButtonPrintedBLightsPlayAndTheButtonPrintedALightsBack() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                CompositionLocalProvider(LocalHeldKeys provides router.held, LocalButtonLetters provides ButtonLetters.of(ConfirmButtonSetting.Bottom)) {
                    HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter))
                }
            }
        }
        compose.waitForIdle()
        val playAtRest = capPixel(0)
        val backAtRest = capPixel(1)

        press(KeyEvent.KEYCODE_BUTTON_B)
        assertNotEquals("B lights the Play hint (its cap says B)", playAtRest, capPixel(0))
        assertEquals("and not the Back hint", backAtRest, capPixel(1))
        release(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(playAtRest, capPixel(0))

        press(KeyEvent.KEYCODE_BUTTON_A)
        assertNotEquals("A lights the Back hint (its cap says A)", backAtRest, capPixel(1))
        assertEquals("and not the Play hint", playAtRest, capPixel(0))
        release(KeyEvent.KEYCODE_BUTTON_A)
    }
}
