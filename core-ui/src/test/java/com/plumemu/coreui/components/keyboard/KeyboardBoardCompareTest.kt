package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.remember
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The keyboard at the board's scale (1.5 px per dp: 768x432 dp is the board's 1152x648 px), for a side-by-side with
 * the rendered board (`build/v2prev/Search*.html`). Written to `build/v2compare/` only with V2COMPARE=1
 * (a verify run would otherwise look for these files as goldens, and build/ is not in git); it asserts nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class KeyboardBoardCompareTest {
    @Test
    fun atBoardScale() {
        assumeTrue(System.getenv("V2COMPARE") != null)
        for (size in listOf(ReferenceShots.Size.Wide, ReferenceShots.Size.Narrow)) {
            RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-hdpi")
            for (preset in ReferenceShots.Preset.entries) {
                captureRoboImage(filePath = "../build/v2compare/keyboard_${size.label}_${preset.label}_ours.png") {
                    PlumTheme(preset.settings) {
                        val spec = KeyboardSpec("Search", hasShift = false)
                        val state = remember { KeyboardState(spec.startModel().let { it.focusOn(it.idOf("r")) }) }
                        KeyboardOnScreen(state, spec)
                    }
                }
            }
        }
    }
}
