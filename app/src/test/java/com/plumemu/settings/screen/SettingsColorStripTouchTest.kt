package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ShadeRole
import com.plumemu.coreui.theme.TextSize
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

/**
 * Touch on the Color strip: a tap on the bar gives the stop under the finger (the ends give the first
 * and the last), and a vertical swipe that starts on the row scrolls the page and sets nothing.
 * Finger dragging along the strip is not wired yet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorStripTouchTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val stops = mutableListOf<Int>()
    private var colorRow = 0

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (model.colourPart != ColorPart.Color && guard++ < 40) model.onAction(Action.Move(Direction.Down), settings, geometry)
                    colorRow = model.focus.panelIndex
                }
            }
        val recording = SettingsTouch({}, {}, {}, {}, {}, {}, {}, onStop = { stops += it })
        SettingsLayout(settings, model, recording, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun start() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { Page() } }
        compose.waitForIdle()
    }

    private val last = ColorShades.stopCount(ShadeRole.Background) - 1

    /** A tap at [fraction] of the bar's width (the row less its side padding), on the bar (a knob's half width in from the row's bottom padding). */
    private fun tapAt(fraction: Float) {
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput {
            val side = PlumDimens.settingsPanelRowPadding.toPx()
            click(Offset(side + (width - 2 * side) * fraction, height - 16.dp.toPx()))
        }
        compose.waitForIdle()
    }

    @Test
    fun aTapOnTheBarGivesTheStopUnderTheFingerAndTheEndsGiveTheFirstAndTheLast() {
        start()
        tapAt(0.01f)
        tapAt(0.99f)
        tapAt(0.5f)
        assertEquals(listOf(0, last, last / 2), stops)
    }

    @Test
    fun aVerticalSwipeStartingOnTheRowScrollsThePageAndSetsNothing() {
        start()
        val before = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput {
            down(Offset(width * 0.5f, height - 16.dp.toPx()))
            moveBy(Offset(0f, -300f))
            up()
        }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), stops)
        val after = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top
        assertNotEquals("the page scrolled", before, after)
    }
}
