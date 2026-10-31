package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.FixNameState
import com.plumemu.game.GameRef
import com.plumemu.game.NameCertainty
import com.plumemu.game.NameFacts
import com.plumemu.game.NameOrigin
import com.plumemu.game.OpenPanel
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.notAGameConfirmContent
import com.plumemu.game.sampleFacts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/**
 * The naming panels over the board's games screen (the game
 * panel's parts and tokens): Fix name… with results and with none, Use my own name, Not a game's
 * confirm, and Info with "Name from".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class NameFixScreenshotTest {
    private val game = GameRef("snes-6", "Super Metroid")
    private val finder =
        TitleFinder(
            listOf(
                TitleChoice("Super Metroid", "Japan, USA, Europe", 1994, "MAME software lists"),
                TitleChoice("Super Metroid (Beta)", "Japan", 1994, "MAME software lists"),
                TitleChoice("Metroid Fusion", "USA, Europe", 2002, "MAME software lists"),
                TitleChoice("Super Mario World", "USA", 1990, "MAME software lists"),
                TitleChoice("Super Mario Kart", "", 1992, "MAME software lists"),
            ),
        )

    private fun shot(name: String, panel: OpenPanel) =
        ReferenceShots.capture(name) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    private fun fixName(query: String, onResults: Boolean): OpenPanel {
        val state = FixNameState(query, finder)
        state.showResults(query, finder.find(query))
        if (onResults) state.onAction(com.plumemu.coreinput.Action.Move(com.plumemu.coreinput.Direction.Down))
        return OpenPanel(game, PanelScreen.FixName(state))
    }

    @Test
    fun fixNameWithResults() = shot("game_fix_name", fixName("super metroid", onResults = true))

    @Test
    fun fixNameNoMatch() = shot("game_fix_name_no_match", fixName("qwxv plomb", onResults = false))

    @Test
    fun fixNameLoading() = shot("game_fix_name_loading", OpenPanel(game, PanelScreen.FixName(FixNameState("Super Metroid", finder = null))))

    /** Name…: the naming entries in the menu's place, Back to automatic offered (a fix is stored). */
    @Test
    fun nameMenu() {
        val facts = sampleFacts(title = "Super Metroid", name = NameFacts(NameOrigin.OwnName, null, null, hasFix = true))
        shot("game_name_menu", OpenPanel(game, PanelScreen.NameMenu(facts), focus = 0, parent = OpenPanel(game, PanelScreen.Menu(facts), focus = 4)))
    }

    @Test
    fun ownName() = shot("game_own_name", OpenPanel(game, PanelScreen.OwnName(OwnNameState("Super Metroid"))))

    @Test
    fun notAGameConfirm() = shot("game_not_a_game", OpenPanel(game, PanelScreen.Message(notAGameConfirmContent(game))))

    @Test
    fun infoNameFrom() {
        val facts =
            sampleFacts(title = "Super Metroid", lastPlayedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)).copy(
                systemName = "Super Nintendo",
                regions = "USA,EUR",
                fileName = "Super Metroid (Japan, USA) (En) (Rev 1) [!].sfc",
                folderName = "ROMs/snes",
                sizeBytes = 3L * 1024 * 1024,
                emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
                name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "MAME software lists", hasFix = false),
            )
        shot("game_info_name_from", OpenPanel(game, PanelScreen.Info(facts)))
    }
}
