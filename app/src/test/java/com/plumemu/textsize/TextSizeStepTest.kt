package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.screen.NoActions
import com.plumemu.settings.screen.NoTouch
import com.plumemu.settings.screen.SettingsActions
import com.plumemu.settings.screen.SettingsColumn
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsGeometry
import com.plumemu.settings.screen.SettingsLayout
import com.plumemu.settings.screen.SettingsModel
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsSection
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.TEST_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Changing the text size in Settings: the focused row stays on screen as the rows grow, and not picked is Large on every screen. */
abstract class TextSizeStepBase(
    private val defaultSize: TextSizeSetting,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumSettings())
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private val actions =
        object : SettingsActions by NoActions {
            override fun update(transform: (PlumSettings) -> PlumSettings) {
                settings = transform(settings)
            }
        }

    private fun show() {
        val focus = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Appearance, rememberedOption = mapOf(SettingsSection.Appearance to SettingsSection.Appearance.options.indexOf(SettingsOption.TextSize)))
        compose.setContent {
            SettingsTheme(settings) {
                model = remember { SettingsModel(actions, focus) }
                geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = defaultSize)
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { null }, battery = { null })
            }
        }
    }

    protected fun assertFocusedRowStaysInViewWhileSteppingToLargest() {
        show()
        compose.waitForIdle()
        repeat(TextSizeSetting.entries.size) {
            compose.runOnIdle { model.onAction(Action.Move(Direction.Right), settings, geometry) }
            compose.waitForIdle()
            // Unclipped: where the row is laid out, not what the list lets through.
            val row = compose.onNodeWithTag(SettingsTags.option(SettingsOption.TextSize)).getUnclippedBoundsInRoot()
            val list = compose.onNodeWithTag(SettingsTags.OPTION_LIST).getBoundsInRoot()
            val size = settings.appearance.textSize
            assertTrue("$size: row ${row.top}..${row.bottom} is outside the list ${list.top}..${list.bottom}", row.top >= list.top - TOLERANCE && row.bottom <= list.bottom + TOLERANCE)
        }
        assertEquals(TextSizeSetting.Largest, settings.appearance.textSize)
    }

    protected fun assertRowShowsTheDefaultUntilPicked(expected: String) {
        show()
        compose.waitForIdle()
        assertEquals(null, settings.appearance.textSize)
        compose.onNodeWithTag(SettingsTags.value(SettingsOption.TextSize), useUnmergedTree = true).onChild().assertTextEquals(expected)
    }

    private companion object {
        val TOLERANCE = androidx.compose.ui.unit.Dp(0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class TextSizeStep667Test : TextSizeStepBase(TextSizeSetting.Default) {
    @Test
    fun theFocusedRowStaysInViewFromLargeToLargest() = assertFocusedRowStaysInViewWhileSteppingToLargest()

    @Test
    fun theRowShowsLargeUntilPicked() = assertRowShowsTheDefaultUntilPicked("‹ Large ›")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class TextSizeStep837Test : TextSizeStepBase(TextSizeSetting.Large) {
    /** The Thor: nothing stored, so the theme and the row both say Large. */
    @Test
    fun theRowShowsLargeUntilPicked() = assertRowShowsTheDefaultUntilPicked("‹ Large ›")

    @Test
    fun theFocusedRowStaysInViewWhileSteppingUp() = assertFocusedRowStaysInViewWhileSteppingToLargest()
}
