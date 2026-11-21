package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A finger dragging along the Color strip: after touch slop it moves the stop under it, previewed (the store stays untouched),
 * and the colour is stored once when the finger lifts, the gesture is cancelled or the row leaves; a vertical swipe scrolls the page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorStripDragTouchTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val preview = ColorPreview()
    private var writesAtStart = 0
    private var writes = 0
    private val actions =
        object : SettingsActions by NoActions {
            override val colorPreview: ColorPreview get() = preview

            override fun update(transform: (PlumSettings) -> PlumSettings) {
                writes++
            }
        }
    private lateinit var model: SettingsModel
    private var colorRow = 0
    private val showing = mutableStateOf(true)

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        model =
            remember {
                SettingsModel(actions, optionsAt(SettingsOption.Background)).also { m ->
                    m.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (m.colourPart != ColorPart.Color && guard++ < 40) m.onAction(Action.Move(Direction.Down), settings, geometry)
                    colorRow = m.focus.panelIndex
                }
            }
        // Remembered, as the screen's is: a new touch object restarts the gesture mid-drag.
        val touch =
            remember {
            SettingsTouch(
                {}, {}, {}, {}, {}, {}, {},
                onStop = { model.onTouchStop(it, settings, geometry) },
                onStopDrag = { model.onTouchStop(it, settings, geometry, drag = true) },
                onStopEnd = model::onTouchStopEnd,
            )
            }
        SettingsLayout(settings, model, touch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun start() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { if (showing.value) Page() } }
        compose.waitForIdle()
        writesAtStart = writes // reaching the strip from the presets wrote already
    }

    private val last = ColorShades.stopCount(ShadeRole.Background) - 1

    private fun androidx.compose.ui.test.TouchInjectionScope.barY() = height - 16.dp.toPx()

    private fun androidx.compose.ui.test.TouchInjectionScope.barX(fraction: Float): Float {
        val side = PlumDimens.settingsPanelRowPadding.toPx()
        return side + (width - 2 * side) * fraction
    }

    private fun dragFromTo(
        from: Float,
        to: Float,
        end: androidx.compose.ui.test.TouchInjectionScope.() -> Unit,
    ) {
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput {
            down(Offset(barX(from), barY()))
            moveBy(Offset((barX(to) - barX(from)), 0f))
            end()
        }
        compose.waitForIdle()
    }

    @Test
    fun aDragPreviewsTheStopUnderTheFingerAndStoresNothingUntilItLifts() {
        start()
        dragFromTo(0.1f, 0.6f) { }
        assertNotNull("the theme is previewed while the finger is down", preview.appearance)
        assertTrue("the knob follows the finger (stop ${model.colorStop})", kotlin.math.abs(model.colorStop - last * 6 / 10) <= 1)
        assertEquals("nothing stored while the finger is down", 0, writes - writesAtStart)
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("stored once on lift", 1, writes - writesAtStart)
    }

    @Test
    fun aCancelledDragStoresTheColourOnceToo() {
        start()
        dragFromTo(0.1f, 0.6f) { }
        assertEquals(0, writes - writesAtStart)
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(1, writes - writesAtStart)
    }

    @Test
    fun aVerticalSwipeStartingOnTheStripScrollsThePageAndMovesNoStop() {
        start()
        val before = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top
        val stop = model.colorStop
        compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).performTouchInput {
            down(Offset(width * 0.5f, barY()))
            moveBy(Offset(0f, -300f))
            up()
        }
        compose.waitForIdle()
        assertEquals(stop, model.colorStop)
        assertEquals(0, writes - writesAtStart)
        assertNotEquals("the page scrolled", before, compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun theRowLeavingMidDragLetsGoOfTheFingerAndStoresOnce() {
        start()
        dragFromTo(0.1f, 0.6f) { }
        assertTrue(preview.isActive)
        assertEquals("the finger is still down", 0, writes - writesAtStart)
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        assertEquals("the row went: the drag was let go of", 1, writes - writesAtStart)
    }
}
