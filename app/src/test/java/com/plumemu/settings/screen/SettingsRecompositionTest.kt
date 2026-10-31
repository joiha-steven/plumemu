package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A move of the focus redraws only the rows whose look changes, not the whole strip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsRecompositionTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var model: SettingsModel
    private val settings = PlumSettings()
    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)

    private fun show(focus: SettingsFocus) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                model = remember { SettingsModel(NoActions, focus).also { it.content = ShotContent.library } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { null }, battery = { null })
            }
        }
    }

    private fun move(direction: Direction) {
        compose.runOnIdle { model.onAction(Action.Move(direction), settings, geometry) }
        compose.waitForIdle()
    }

    @Test
    fun movingBetweenOptionsRecomposesOnlyTheTwoRowsThatChange() {
        show(optionsAt(SettingsOption.Background))
        SettingsRecomposeProbe.reset()
        repeat(3) { move(Direction.Down) }
        assertEquals("the strip is not redrawn by moves inside the options", 0, SettingsRecomposeProbe.strips)
        assertEquals("no section row changes", 0, SettingsRecomposeProbe.sectionRows)
        // Each move: the row losing the focus and the row gaining it.
        assertTrue("option rows redrawn: ${SettingsRecomposeProbe.optionRows}", SettingsRecomposeProbe.optionRows <= 6)
    }

    @Test
    fun movingAcrossSectionsOnlyRedrawsTheSectionRowsThatChange() {
        show(SettingsFocus())
        SettingsRecomposeProbe.reset()
        move(Direction.Down)
        assertEquals(0, SettingsRecomposeProbe.strips)
        assertTrue("section rows redrawn: ${SettingsRecomposeProbe.sectionRows}", SettingsRecomposeProbe.sectionRows <= 2)
    }
}
