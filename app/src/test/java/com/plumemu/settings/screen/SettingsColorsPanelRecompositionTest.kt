package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A move in Colors' panel recomposes only the tile it leaves and the one it reaches, not all eleven. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorsPanelRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var model: SettingsModel
    private val settings = PlumSettings()
    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)

    private fun move(direction: Direction) {
        compose.runOnIdle { model.onAction(Action.Move(direction), settings, geometry) }
        compose.waitForIdle()
    }

    @Test
    fun aMoveRecomposesAtMostTheTwoTilesInvolved() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                model = remember { SettingsModel(NoActions, panelAt(SettingsOption.Colors, PanelPage.ColorSets, 0)) }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { null }, battery = { null })
            }
        }
        compose.waitForIdle()
        assertTrue("every tile composed: ${SettingsRecomposeProbe.colorTiles}", SettingsRecomposeProbe.colorTiles >= ColorSet.entries.size)
        for (direction in listOf(Direction.Right, Direction.Down, Direction.Left, Direction.Up)) {
            SettingsRecomposeProbe.reset()
            move(direction)
            assertTrue("$direction recomposed ${SettingsRecomposeProbe.colorTiles} tiles", SettingsRecomposeProbe.colorTiles in 1..2)
        }
    }
}
