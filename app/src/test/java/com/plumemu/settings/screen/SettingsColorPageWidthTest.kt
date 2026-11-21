package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The colour page reaches the right margin at every width class and text size: with the cursor on Color, the strip's
 * right edge is the content's right text line (the screen less the strip margin and a row's padding), whether Settings
 * has two columns or three.
 */
abstract class SettingsColorPageWidthBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Accent)).also { m ->
                    m.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (m.colourPart != ColorPart.Color && guard++ < 40) m.onAction(Action.Move(Direction.Up), settings, geometry)
                }
            }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    @Test
    fun theStripReachesTheRightMarginAtEveryTextSize() {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { Page() } }
        val failures = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            val strip = compose.onNodeWithTag(STRIP_BAR_TAG).getUnclippedBoundsInRoot()
            val line = root.right - PlumDimens.stripMarginSide - PlumDimens.settingsPanelRowPadding
            println("COLOUR PAGE $each: root ${root.right} strip ${strip.left}..${strip.right} right text line $line")
            if (abs(strip.right.value - line.value) > 0.5f) failures += "$each: strip right ${strip.right}, not on the right text line $line"
            // Two columns used to draw the page in the panel's 248 dp only: the strip must be as long as in three columns (it starts at 246 there, further left in two).
            if (strip.left > THREE_COLUMN_STRIP_LEFT) failures += "$each: strip starts at ${strip.left}, so the page does not take the options' room (three columns start it at $THREE_COLUMN_STRIP_LEFT)"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        val THREE_COLUMN_STRIP_LEFT = 246.5.dp
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsColorPageWidth832Test : SettingsColorPageWidthBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPageWidth768Test : SettingsColorPageWidthBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorPageWidth576Test : SettingsColorPageWidthBase()
