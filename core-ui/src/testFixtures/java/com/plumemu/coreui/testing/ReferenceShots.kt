package com.plumemu.coreui.testing

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot harness: one call gives a screen its four reference shots (docs/conventions/testing.md),
 * 768×432 and 576×432 dp (and, when a screen asks with `sizes`, the 4:3 640×480 and 640×432), each in Graphite (dark) and Paper (light), stored as
 * `<screen>_<768x432|576x432>_<graphite|paper>.png` in the calling module's screenshot folder.
 *
 * Use it from a test class run by Robolectric with native graphics:
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
 * class MyScreenScreenshotTest {
 *     @Test
 *     fun myScreen() = ReferenceShots.capture("my_screen") { MyScreen() }
 * }
 * ```
 * `./gradlew recordRoborazziDebug` writes the PNGs, `verifyRoborazziDebug` compares with them.
 */
object ReferenceShots {
    /**
     * Android level the screenshots are rendered with. Robolectric 4.17 needs Java 21 for 35
     * and 36, and the build runs on Java 17; rendering of text and shapes does not depend on it.
     */
    const val ROBOLECTRIC_SDK: Int = 34

    /** Where the reference images live, relative to the module (unit tests run there). */
    const val OUTPUT_DIR: String = "src/test/screenshots"

    /** A screen size in dp. A class, not an enum, so [Pocket6] can stay out of [entries] (the shots that cover every size). */
    class Size private constructor(
        val label: String,
        val widthDp: Int,
        val heightDp: Int,
    ) {
        override fun toString(): String = label

        companion object {
            /** Retroid Pocket 5, the reference device. */
            val Wide = Size("768x432", 768, 432)

            /** Retroid Pocket Nova. */
            val Narrow = Size("576x432", 576, 432)

            /** A 4:3 screen of 640 dp at full height: 1280x960 px at 320 dpi, without the system bar. */
            val FourThree = Size("640x480", 640, 480)

            /** The same 4:3 screen with Android's 48 dp navigation bar taking the bottom: the Retroid Pocket Nova as it runs. */
            val FourThreeWithBar = Size("640x432", 640, 432)

            /** Retroid Pocket 6, about 832x468 dp. Not in [entries]: a screen asks for it by name. */
            val Pocket6 = Size("832x468", 832, 468)

            /** The Pocket 6 at its real 1920x1080 px, 853x480 dp at xhdpi-ish scale; not in [entries]. */
            val Pocket6Full = Size("853x480", 853, 480)

            /** The sizes screens that say "every size" cover. */
            val entries: List<Size> = listOf(Wide, Narrow, FourThree, FourThreeWithBar)

            /** The two original reference sizes: what [capture] takes without a `sizes` argument, so existing shots stay as they were. */
            val Original: List<Size> = listOf(Wide, Narrow)

            /** The 4:3 sizes (640 wide), each screen that covers them says so with `sizes = Size.FourThreeSizes`. */
            val FourThreeSizes: List<Size> = listOf(FourThree, FourThreeWithBar)

            /** The original sizes and the Pocket 6's, for screens whose layout depends on the width. */
            val WithPocket6: List<Size> = listOf(Wide, Narrow, Pocket6)
        }
    }

    enum class Preset(
        val label: String,
        val settings: PlumThemeSettings,
    ) {
        Graphite("graphite", PlumThemeSettings.Graphite),
        Paper("paper", PlumThemeSettings.Paper),
    }

    /**
     * `<screen>_<size>_<preset>.png`; a text size other than Small adds its name last
     * (`home_768x432_paper_largest.png`).
     */
    fun fileName(
        screen: String,
        size: Size,
        preset: Preset,
        textSize: TextSize = TextSize.Small,
    ): String {
        val sizeSuffix = if (textSize == TextSize.Small) "" else "_${textSize.name.lowercase()}"
        return "${screen}_${size.label}_${preset.label}$sizeSuffix.png"
    }

    /**
     * Captures [content] inside [PlumTheme] at every [Size] and [Preset]
     * at [textSize] (Small by default).
     * [content] sees the screen size through `PlumTheme.layout`, as on a device.
     *
     * Every shot is taken and compared even when an earlier one differs; the test then fails
     * once, listing every mismatch.
     */
    fun capture(
        screen: String,
        textSize: TextSize = TextSize.Small,
        sizes: List<Size> = Size.Original,
        content: @Composable () -> Unit,
    ) {
        require(screen.matches(Regex("[a-z0-9_]+"))) { "screen name must be snake_case, got '$screen'" }
        val mismatches = mutableListOf<String>()
        for (size in sizes) {
            // xhdpi: twice the dp, sharp enough to judge the fonts in the PNG.
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
            for (preset in Preset.entries) {
                val name = fileName(screen, size, preset, textSize)
                try {
                    captureRoboImage(filePath = "$OUTPUT_DIR/$name") {
                        PlumTheme(preset.settings.copy(textSize = textSize)) { content() }
                    }
                } catch (e: AssertionError) {
                    // Collected, not swallowed: rethrown below together with the others.
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            throw AssertionError("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
        }
    }
}
