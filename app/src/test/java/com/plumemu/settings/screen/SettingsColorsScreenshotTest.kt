package com.plumemu.settings.screen

import com.plumemu.coreui.theme.TextSize
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.toSetting
import com.plumemu.settings.toThemeSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Settings › Appearance with the Colors row focused, drawn in the set it shows: Plumemu
 * (the default, dark) and Super (a light set whose background and accent are stored as hex codes,
 * so the Background and Accent rows show "Super", not "Custom"). The colours are the set's, not
 * the Graphite/Paper pair, so the shots are named by set: `settings_colors_<size>_<set>.png`, at
 * the two reference sizes like every other shot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsColorsScreenshotTest {
    @Test
    fun colorsRowInPlumemuAndSuper() {
        val mismatches = mutableListOf<String>()
        for (size in ReferenceShots.Size.Original) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
            // Ivory's buttons on a background changed by hand: Colors reads Custom, the buttons stay.
            val shots = listOf(ColorSet.Plumemu to settingsIn(ColorSet.Plumemu), ColorSet.Super to settingsIn(ColorSet.Super), ColorSet.Ivory to ivoryOnGraphite())
            for ((set, settings) in shots) {
                val name = "settings_colors_${size.label}_${set.name.lowercase()}${if (settings.appearance.buttonSet != null && settings.appearance.background != set.background.toSetting()) "_tuned" else ""}.png"
                try {
                    captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/$name") {
                        PlumTheme(settings.toThemeSettings().copy(textSize = TextSize.Medium)) { SettingsAt(optionsAt(SettingsOption.Colors), settings) }
                    }
                } catch (e: AssertionError) {
                    // Collected, not swallowed: rethrown below together with the others.
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) throw AssertionError("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }

    private fun ivoryOnGraphite(): PlumSettings {
        val ivory = settingsIn(ColorSet.Ivory)
        return ivory.copy(appearance = ivory.appearance.copy(background = ColorSetting.Preset("Graphite"), textColor = TextColorSetting.Light, buttonSet = "Ivory"))
    }

    private fun settingsIn(set: ColorSet): PlumSettings =
        PlumSettings(
            appearance = AppearanceSettings(background = set.background.toSetting(), textColor = set.textMode.toSetting(), accent = set.accent.toSetting()),
            flags = SetupFlags(setupCompleted = true),
        )
}
