package com.plumemu.coreui.components.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The keyboard alone on a screen, its tray running to the bottom as Search's will (the board's
 * `ban-phim-rieng` mockups 1, 3, 4, 6 and 7 without the screen around it), for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class KeyboardScreenshotTest {
    /** Search's keyboard (no shift) with the focus on o (mockup 1). */
    @Test
    fun letters() =
        ReferenceShots.capture("keyboard_letters") {
            Keyboard(KeyboardSpec("Search", hasShift = false)) { it.at("o") }
        }

    /** The digits and symbols with the focus on 2 (mockup 3). */
    @Test
    fun symbols() =
        ReferenceShots.capture("keyboard_symbols") {
            Keyboard(KeyboardSpec("Search", hasShift = false)) { it.toPage(KeyboardPage.Symbols).at("2") }
        }

    /** Fix name's keyboard: shift on and focused, the letters in upper case, "Done" (mockup 4). */
    @Test
    fun shiftOn() =
        ReferenceShots.capture("keyboard_shift") {
            Keyboard(KeyboardSpec("Done", hasShift = true)) { it.toggleShift().at("SHIFT") }
        }

    /** Search's action key focused: the accent ring outside it and the onAccent ring inside its accent fill. */
    @Test
    fun actionFocused() =
        ReferenceShots.capture("keyboard_action_focused") {
            Keyboard(KeyboardSpec("Search", hasShift = false)) { it.at("ACTION") }
        }

    /** The largest text, focus on c (mockup 7): keys held at 36 on a 432 high screen (the tray near half of it). */
    @Test
    fun largestText() =
        ReferenceShots.capture("keyboard", textSize = TextSize.Largest) {
            Keyboard(KeyboardSpec("Search", hasShift = false)) { it.at("c") }
        }

    @Composable
    private fun Keyboard(
        spec: KeyboardSpec,
        setUp: (KeyboardModel) -> KeyboardModel,
    ) {
        val state = remember { KeyboardState(setUp(spec.startModel())) }
        KeyboardOnScreen(state, spec)
    }

    private fun KeyboardModel.at(label: String): KeyboardModel = focusOn(idOf(label))
}
