package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Color code" row on the "+" page shows the colour a finger is dragging, not the stored one:
 * the theme's colours follow the preview as States, so the row reads the preview itself.
 * Checked on the Background, Text color and Accent pages, and after the lift once the store has the colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorHexFollowsDragTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val preview = ColorPreview()
    private lateinit var stored: PlumSettings

    /** The store's background; a test sets it to the dragged colour to stand for the write landing. */
    private val storedBackground = mutableStateOf<ColorSetting?>(null)

    private var option = SettingsOption.Background
    private var presets = PanelPage.BackgroundPresets

    private val actions =
        object : SettingsActions by NoActions {
            override val colorPreview: ColorPreview = preview
        }

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android), appearance = base.appearance.copy(background = storedBackground.value ?: base.appearance.background))
        stored = settings
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(actions, optionsAt(option)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    val theme = settings.toThemeSettings()
                    val current =
                        when (presets) {
                            PanelPage.TextPresets -> theme.textColor
                            PanelPage.AccentPresets -> theme.accentColor
                            else -> theme.backgroundColor
                        }
                    val plus = ColorPages.presetCount(presets, current) - 1
                    model.onTapPanelItem(plus, settings, geometry)
                    if (model.focus.page == presets) model.onTapPanelItem(plus, settings, geometry)
                    model.onAction(Action.Move(Direction.Down), settings, geometry)
                }
            }
        SettingsLayout(settings, model, SettingsTouch({}, {}, {}, {}, {}, {}, {}, onSlider = { _, _ -> }, onSliderEnd = {}), TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun open() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { Page() } }
        compose.waitForIdle()
    }

    private fun drag(shown: AppearanceSettings.() -> AppearanceSettings) {
        compose.runOnIdle { preview.show(stored.appearance.shown(), fingerDown = true) }
        compose.waitForIdle()
    }

    private fun assertHexShown(hex: String) {
        compose.onNodeWithText(hex, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theHexTextFollowsTheFingerDuringADrag() {
        open()
        drag { copy(background = ColorSetting.Custom(0x204060)) }
        assertHexShown("#204060")
        drag { copy(background = ColorSetting.Custom(0x305070)) }
        assertHexShown("#305070")
    }

    @Test
    fun afterTheLiftTheHexShowsTheFinalColourOnceTheStoreHasIt() {
        open()
        drag { copy(background = ColorSetting.Custom(0x204060)) }
        compose.runOnIdle { preview.release() }
        compose.waitForIdle()
        assertHexShown("#204060")
        // The write lands: the store holds the colour, so the overlay has done its job.
        compose.runOnIdle { storedBackground.value = ColorSetting.Custom(0x204060) }
        compose.waitForIdle()
        compose.runOnIdle { preview.settleAgainst(stored.appearance) }
        compose.waitForIdle()
        assertFalse("the overlay went", preview.isActive)
        assertHexShown("#204060")
    }

    @Test
    fun theTextColorPageFollowsTheFingerToo() {
        option = SettingsOption.TextColor
        presets = PanelPage.TextPresets
        open()
        drag { copy(textColor = TextColorSetting.Custom(0xE0D0C0)) }
        assertHexShown("#E0D0C0")
    }

    @Test
    fun theAccentPageFollowsTheFingerToo() {
        option = SettingsOption.Accent
        presets = PanelPage.AccentPresets
        open()
        drag { copy(accent = ColorSetting.Custom(0xC04080)) }
        assertHexShown("#C04080")
    }
}
