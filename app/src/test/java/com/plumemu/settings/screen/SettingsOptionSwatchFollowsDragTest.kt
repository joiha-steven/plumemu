package com.plumemu.settings.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The small colour dots on the Background, Text color and Accent option rows follow a finger on a colour
 * slider, read while drawing, so the move recomposes no row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsOptionSwatchFollowsDragTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val preview = ColorPreview()
    private var stored: PlumSettings = PlumSettings()

    private fun open() {
        compose.setContent { SettingsTheme(stored, null, preview) { SettingsAt(optionsAt(SettingsOption.Background), stored) } }
        compose.waitForIdle()
    }

    /** The colour at the middle of the dot that starts [option]'s value. */
    private fun dotOf(option: SettingsOption): Color {
        compose.waitForIdle()
        val image = compose.onNodeWithTag(SettingsTags.value(option), useUnmergedTree = true).also { compose.onRoot().captureToImage() }.captureToImage()
        val density = 2f
        val x = (PlumDimens.settingsValueDot.value * density / 2).toInt()
        return Color(image.asAndroidBitmap().getPixel(x, image.height / 2))
    }

    @Test
    fun theDotsOfBackgroundTextAndAccentFollowTheFingerBeforeTheLift() {
        open()
        val before = mapOf(SettingsOption.Background to dotOf(SettingsOption.Background), SettingsOption.TextColor to dotOf(SettingsOption.TextColor), SettingsOption.Accent to dotOf(SettingsOption.Accent))
        val dragged = stored.appearance.copy(background = ColorSetting.Custom(0x203050), textColor = TextColorSetting.Custom(0xE8D8C8), accent = ColorSetting.Custom(0xF0A060))
        compose.runOnIdle { preview.show(dragged, fingerDown = true) }
        val shown = preview.over(stored).toThemeSettings()
        assertEquals(shown.backgroundColor.toComposeColor().toArgb(), dotOf(SettingsOption.Background).toArgb())
        assertEquals(shown.textColor.toComposeColor().toArgb(), dotOf(SettingsOption.TextColor).toArgb())
        assertEquals(shown.accentColor.toComposeColor().toArgb(), dotOf(SettingsOption.Accent).toArgb())
        before.forEach { (option, colour) -> assertNotEquals("$option moved", colour.toArgb(), dotOf(option).toArgb()) }
    }

    @Test
    fun aDraggedAccentReplacesTheBySystemDiscAndPuttingItBackBringsTheDiscBack() {
        stored = PlumSettings().let { it.withAppearance { a -> a.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))) } }
        open()
        val disc = compose.onAllNodesWithTag(SettingsTags.VALUE_BY_SYSTEM, useUnmergedTree = true)
        disc.assertCountEquals(1)
        compose.runOnIdle { preview.show(stored.appearance.copy(accent = ColorSetting.Custom(0xF0A060)), fingerDown = true) }
        compose.waitForIdle()
        disc.assertCountEquals(0)
        val shown = preview.over(stored).toThemeSettings()
        assertEquals(shown.accentColor.toComposeColor().toArgb(), dotOf(SettingsOption.Accent).toArgb())
        compose.runOnIdle { preview.clear() }
        compose.waitForIdle()
        disc.assertCountEquals(1)
    }

    @Test
    fun aDraggedBackgroundKeepsTheAccentsBySystemDisc() {
        stored = PlumSettings().let { it.withAppearance { a -> a.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))) } }
        open()
        val disc = compose.onAllNodesWithTag(SettingsTags.VALUE_BY_SYSTEM, useUnmergedTree = true)
        compose.runOnIdle { preview.show(stored.appearance.copy(background = ColorSetting.Custom(0x203050)), fingerDown = true) }
        compose.waitForIdle()
        disc.assertCountEquals(1)
    }
}
