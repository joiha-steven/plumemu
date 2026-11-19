package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ToneLadder
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Coming back to Colors searches no tone again: every set's preview derives its tones from the remembered ladder. A ladder too small
 * for the eleven previews forgot them between draws, and each search costs about a millisecond on a slow handheld (a 54 ms frame).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorsToneSearchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var model: SettingsModel
    private val settings = PlumSettings()
    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)

    private fun moveAndDraw(direction: Direction) {
        compose.runOnIdle { model.onAction(Action.Move(direction), settings, geometry) }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
    }

    @Test
    fun returningToColorsSearchesNoToneAgain() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.Colors)) }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { null }, battery = { null })
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        moveAndDraw(Direction.Up)
        moveAndDraw(Direction.Down)
        moveAndDraw(Direction.Up)
        val before = ToneLadder.searches
        moveAndDraw(Direction.Down)
        assertEquals("tones searched again on coming back to Colors", 0, ToneLadder.searches - before)
    }
}
