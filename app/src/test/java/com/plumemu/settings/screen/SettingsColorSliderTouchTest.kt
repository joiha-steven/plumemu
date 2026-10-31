package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Touch on a slider row: a tap on the track sets the value, a horizontal drag
 * moves it after touch slop, and a vertical swipe that starts on a slider row scrolls the page
 * instead of touching the slider.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorSliderTouchTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val calls = mutableListOf<Pair<Int, Float>>()
    private var ends = 0
    private val showing = mutableStateOf(true)

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    val plus = ColorPages.presetCount(PanelPage.BackgroundPresets, settings.toThemeSettings().backgroundColor) - 1
                    model.onTapPanelItem(plus, settings, geometry)
                    if (model.focus.page == PanelPage.BackgroundPresets) model.onTapPanelItem(plus, settings, geometry)
                    model.onAction(Action.Move(Direction.Down), settings, geometry)
                }
            }
        val recording = SettingsTouch({}, {}, {}, {}, {}, {}, {}, onSlider = { i, f -> calls += i to f }, onSliderEnd = { ends++ })
        SettingsLayout(settings, model, recording, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private val hue get() = backgroundGrid.size

    private fun start() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { if (showing.value) Page() } }
        compose.waitForIdle()
    }

    @Test
    fun aTapOnTheTrackSetsTheValue() {
        start()
        compose.onNodeWithTag(SettingsTags.panelItem(hue)).performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(1, calls.size)
        assertEquals(hue, calls[0].first)
        assertTrue(calls[0].second in 0.4f..0.6f)
    }

    @Test
    fun aHorizontalDragMovesTheValueAfterSlop() {
        start()
        compose.onNodeWithTag(SettingsTags.panelItem(hue)).performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveBy(Offset(width * 0.3f, 0f))
            up()
        }
        compose.waitForIdle()
        assertTrue(calls.isNotEmpty())
        assertTrue("ends near the finger", calls.last().second in 0.4f..0.6f)
    }

    @Test
    fun theRowLeavingTheCompositionMidDragLetsGoOfTheFinger() {
        start()
        compose.onNodeWithTag(SettingsTags.panelItem(hue)).performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveBy(Offset(width * 0.3f, 0f))
        }
        compose.waitForIdle()
        assertTrue("the drag moved", calls.isNotEmpty())
        assertEquals("the finger is still down", 0, ends)
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        assertTrue("the row went: the drag was let go of", ends >= 1)
    }

    @Test
    fun aVerticalSwipeStartingOnASliderRowScrollsThePageAndSetsNothing() {
        start()
        val before = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top
        compose.onNodeWithTag(SettingsTags.panelItem(hue)).performTouchInput {
            down(Offset(width * 0.5f, height / 2f))
            moveBy(Offset(0f, -300f))
            up()
        }
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Int, Float>>(), calls)
        val after = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top
        assertNotEquals("the page scrolled", before, after)
    }
}
