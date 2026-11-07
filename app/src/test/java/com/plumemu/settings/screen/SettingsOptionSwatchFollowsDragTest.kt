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

/** What a finger on a colour slider does to the option rows' colour dots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsOptionSwatchFollowsDragTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val preview = ColorPreview()
    private val dragged get() = stored.appearance.copy(background = ColorSetting.Custom(0x203050), textColor = TextColorSetting.Custom(0xE8D8C8), accent = ColorSetting.Custom(0xF0A060))
    private val still = PlumSettings().let { it.copy(advanced = it.advanced.copy(animations = false)) }
    private var stored: PlumSettings = still

    private fun open() {
        compose.setContent { SettingsTheme(stored, null, preview) { SettingsAt(optionsAt(SettingsOption.TextColor), stored) } }
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

    /** A finger on a slider means the "+" page is up, and that page covers the options column: no dot is on screen to follow it. */
    private fun assertNoRowDotOn(
        option: SettingsOption,
        page: PanelPage,
    ) {
        compose.setContent { SettingsTheme(stored, null, preview) { SettingsAt(panelAt(option, page, 0), stored) } }
        compose.waitForIdle()
        compose.runOnIdle { preview.show(dragged, fingerDown = true) }
        compose.waitForIdle()
        for (row in listOf(SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent)) {
            compose.onNodeWithTag(SettingsTags.value(row), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun aFingerOnTheBackgroundsPlusPageHasNoRowDotToMove() = assertNoRowDotOn(SettingsOption.Background, PanelPage.BackgroundCustom)

    @Test
    fun aFingerOnTheTextColorsPlusPageHasNoRowDotToMove() = assertNoRowDotOn(SettingsOption.TextColor, PanelPage.TextCustom)

    @Test
    fun aFingerOnTheAccentsPlusPageHasNoRowDotToMove() = assertNoRowDotOn(SettingsOption.Accent, PanelPage.AccentCustom)

    /** Should a preview reach the list, the Accent row reads it while drawing: exactly its colour, with no recomposition of the row. */
    @Test
    fun theAccentDotReadsThePreviewWhileDrawing() {
        open()
        val before = dotOf(SettingsOption.Accent).toArgb()
        compose.runOnIdle { preview.show(dragged, fingerDown = true) }
        val shown = preview.over(stored).toThemeSettings()
        assertEquals(shown.accentColor.toComposeColor().toArgb(), dotOf(SettingsOption.Accent).toArgb())
        assertNotEquals(before, dotOf(SettingsOption.Accent).toArgb())
    }

    @Test
    fun aDraggedAccentReplacesTheBySystemDiscAndPuttingItBackBringsTheDiscBack() {
        stored = still.let { it.withAppearance { a -> a.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))) } }
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
        stored = still.let { it.withAppearance { a -> a.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))) } }
        open()
        val disc = compose.onAllNodesWithTag(SettingsTags.VALUE_BY_SYSTEM, useUnmergedTree = true)
        compose.runOnIdle { preview.show(stored.appearance.copy(background = ColorSetting.Custom(0x203050)), fingerDown = true) }
        compose.waitForIdle()
        disc.assertCountEquals(1)
    }
}
