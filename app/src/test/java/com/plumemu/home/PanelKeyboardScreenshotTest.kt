package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.FixNameState
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game panel's fields typing with Plumemu's keyboard (the tray of Search and the panel's parts): Fix name… with shift on for one capital, Use my own
 * name, Fix box art… before its search (no game covers), and Fix name… at the largest text size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PanelKeyboardScreenshotTest {
    private val game = GameRef("snes-6", "Super Metroid")
    private val finder =
        TitleFinder(
            listOf(
                TitleChoice("Super Metroid", "Japan, USA, Europe", 1994, "MAME software lists"),
                TitleChoice("Super Metroid (Beta)", "Japan", 1994, "MAME software lists"),
                TitleChoice("Super Mario World", "USA", 1990, "MAME software lists"),
            ),
        )

    private fun shot(
        name: String,
        textSize: TextSize = TextSize.Medium,
        panel: () -> OpenPanel,
    ) = ReferenceShots.capture(name, textSize = textSize) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel()) }

    /** Typing "Super M" in Fix name…, shift pressed once (SELECT) for the next capital. */
    private fun fixNameShift(): OpenPanel {
        val state = FixNameState("Super M", finder)
        state.showResults(state.query, finder.find(state.query))
        state.onAction(Action.Enter)
        state.onAction(Action.ToggleView)
        return OpenPanel(game, PanelScreen.FixName(state))
    }

    @Test
    fun fixNameTypingWithShift() = shot("game_fix_name_typing") { fixNameShift() }

    @Test
    fun fixNameTypingLargest() = shot("game_fix_name_typing", TextSize.Largest) { fixNameShift() }

    @Test
    fun ownNameTyping() =
        shot("game_own_name_typing") {
            OpenPanel(game, PanelScreen.OwnName(OwnNameState("Super Metroid").apply { onAction(Action.Enter) }))
        }

    @Test
    fun fixBoxArtTyping() =
        shot("game_fix_box_art_typing") {
            OpenPanel(game, PanelScreen.FixBoxArt(FixBoxArtState("Super Metroid", "Super Nintendo").apply { onAction(Action.Enter) }))
        }
}
