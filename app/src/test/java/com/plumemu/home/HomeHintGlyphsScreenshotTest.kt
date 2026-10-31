package com.plumemu.home

import com.plumemu.coreui.theme.TextSize
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.LocalButtonLetters
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The main screen's hint bar in the five sets with coloured A/B/X/Y glyphs: Super,
 * Charcoal, Ivory, Banana and Pocket, at both reference sizes, games focused; and Super with "Confirm with B", where
 * the confirm hint reads B in B's colour: `home_glyphs_<size>_<set>[_confirm_b].png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeHintGlyphsScreenshotTest {
    @Test
    fun hintBarInTheSetsWithGlyphColours() {
        val mismatches = mutableListOf<String>()
        val shots =
            listOf(ColorSet.Super, ColorSet.Charcoal, ColorSet.Ivory, ColorSet.Banana, ColorSet.Pocket).map { it to ConfirmButtonSetting.Right } + (ColorSet.Super to ConfirmButtonSetting.Bottom)
        for (size in ReferenceShots.Size.Original) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
            for ((set, confirm) in shots) {
                val suffix = if (confirm == ConfirmButtonSetting.Bottom) "_confirm_b" else ""
                val name = "home_glyphs_${size.label}_${set.name.lowercase()}$suffix.png"
                try {
                    captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/$name") {
                        PlumTheme(PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent, buttonSet = set, textSize = TextSize.Medium)) {
                            CompositionLocalProvider(LocalButtonLetters provides ButtonLetters.of(confirm)) {
                                HomeAt(FakeHomeContent.board(), BoardGamesFocused)
                            }
                        }
                    }
                } catch (e: AssertionError) {
                    // Collected, not swallowed: rethrown below together with the others.
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) throw AssertionError("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}
