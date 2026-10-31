package com.plumemu.settings.screen

import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Settings › Appearance › Colors' panel, the cursor on Ivory's tile, drawn in the set it
 * is on: Charcoal (dark, coloured glyphs) and Super (light, coloured glyphs), at both reference
 * sizes and at Medium and Largest; and on own colours (Graphite), where Custom has its tile:
 * `settings_colors_panel_<size>_<set>[_largest].png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsColorsPanelScreenshotTest {
    @Test
    fun panelInADarkAndALightSet() {
        val mismatches = mutableListOf<String>()
        val graphite = AppearanceSettings(background = ColorSetting.Preset("Graphite"))
        val looks =
            listOf(
                "charcoal" to graphite.withColors(ColorSet.Charcoal).copy(ownColors = null),
                "super" to graphite.withColors(ColorSet.Super).copy(ownColors = null),
                "custom" to graphite,
            )
        for (size in ReferenceShots.Size.Original) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
            for ((label, appearance) in looks) {
                for (largest in listOf(false, true)) {
                    if (largest && label == "custom") continue
                    val stored =
                        PlumSettings(
                            appearance = if (largest) appearance.copy(textSize = TextSizeSetting.Largest) else appearance.copy(textSize = TextSizeSetting.Medium),
                            flags = SetupFlags(setupCompleted = true),
                        )
                    val name = "settings_colors_panel_${size.label}_$label${if (largest) "_largest" else ""}.png"
                    try {
                        captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/$name") {
                            PlumTheme(stored.toThemeSettings()) {
                                SettingsAt(panelAt(SettingsOption.Colors, PanelPage.ColorSets, ColorSet.Ivory.ordinal), stored)
                            }
                        }
                    } catch (e: AssertionError) {
                        // Collected, not swallowed: rethrown below together with the others.
                        mismatches += "$name: ${e.message}"
                    }
                }
            }
        }
        if (mismatches.isNotEmpty()) throw AssertionError("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}
