package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.ArcadeSetFacts
import com.plumemu.game.Beetle
import com.plumemu.game.DuckStation
import com.plumemu.game.EmulatorItem
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.Epsxe
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreMissingMessage
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.launchMessage
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import com.plumemu.game.sampleFacts
import com.plumemu.game.sampleListing
import com.plumemu.game.sampleVersions
import com.plumemu.game.versionsPanel
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.choice.ChoiceSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/**
 * The game panel (the Settings side panel's parts and tokens) over the
 * board's games screen, Super Metroid focused: the menu, Play with, Info and two launch errors.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GamePanelScreenshotTest {
    private val game = GameRef("snes-6", "Super Metroid")
    private val facts =
        sampleFacts(title = "Super Metroid", lastPlayedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)).copy(
            systemName = "Super Nintendo",
            regions = "USA,EUR",
            fileName = "Super Metroid (Japan, USA) (En) (Rev 1) [!].sfc",
            folderName = "ROMs/snes",
            sizeBytes = 3L * 1024 * 1024,
            emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
        )

    private fun shot(name: String, panel: OpenPanel) =
        ReferenceShots.capture(name) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    @Test
    fun menu() = shot("game_menu", OpenPanel(game, PanelScreen.Menu(facts), focus = 1))

    /** Opened from Recent: Remove from Recent follows Hide; focus on it. */
    @Test
    fun menuFromRecent() = shot("game_menu_remove_from_recent", OpenPanel(game, PanelScreen.Menu(facts.copy(canRemoveFromRecent = true)), focus = 4))

    @Test
    fun playWith() {
        val risky = EmulatorItem("risky", "Risky Emu", "org.example.risky", isLegalRisk = true)
        val listing = sampleListing(current = Beetle.id, installed = listOf(DuckStation, Beetle), notInstalled = listOf(Epsxe, risky))
            .copy(systemName = "Super Nintendo")
        // Focus on the current emulator: the badge must sit inside the highlighted row.
        shot("game_play_with", OpenPanel(game, PanelScreen.PlayWith(listing), focus = 2))
    }

    /** A row of two regional copies: Other versions… after Play with, its value the version A plays now. */
    @Test
    fun menuWithOtherVersions() =
        shot("game_menu_versions", OpenPanel(game, PanelScreen.Menu(facts.copy(versions = sampleVersions(currentIndex = 1))), focus = 2))

    /**
     * Other versions of Super Metroid on the SNES board: one row per regional copy by its region, the
     * one A plays with "Current", focused. (Arcade labels are checked by OtherVersionsFitTest.)
     */
    @Test
    fun otherVersions() {
        val menu = OpenPanel(game, PanelScreen.Menu(facts.copy(versions = sampleVersions(currentIndex = 1))), focus = 2)
        shot("game_versions", versionsPanel(game, sampleVersions(currentIndex = 1), menu))
    }

    /** Box art (hidden in the alpha): the grid's cover drops its ring while the menu has the focus. */
    @Test
    fun menuOverGrid() =
        ReferenceShots.capture("game_menu_grid") {
            // The Large grid size, the board's landscape grid, as drawn before the grid sizes.
            ProvideGridSize(GridSize.Large) {
                HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused, gridIds = setOf("snes"), panel = OpenPanel(game, PanelScreen.Menu(facts)))
            }
        }

    @Test
    fun info() = shot("game_info", OpenPanel(game, PanelScreen.Info(facts)))

    /**
     * Info too tall for the panel (Largest in Serif): the facts scroll above Back, and the row after
     * the last whole one is cut at the scroll's bottom edge, part of it in view, so it is plain there is more.
     */
    @Test
    fun infoThatScrolls() =
        ReferenceShots.capture("game_info_scrolls", fonts = listOf(PlumFont.Serif), textSize = TextSize.Largest) {
            HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(game, PanelScreen.Info(facts)))
        }

    /** An arcade set: its regions from the description and the set line, which tells versions apart. */
    @Test
    fun infoArcade() {
        val arcade =
            facts.copy(
                title = "Cadillacs and Dinosaurs",
                systemName = "Arcade",
                regions = "USA",
                fileName = "dinou.zip",
                folderName = "ROMs/arcade",
                sizeBytes = 2L * 1024 * 1024,
                emulator = EmulatorToStart("RetroArch (FinalBurn Neo)", isInstalled = true),
                isArcade = true,
                arcadeSet = ArcadeSetFacts("dinou", listOf("USA 930201")),
            )
        shot("game_info_arcade", OpenPanel(GameRef("arcade-1", "Cadillacs and Dinosaurs"), PanelScreen.Info(arcade)))
    }

    @Test
    fun emulatorMissing() {
        val outcome = LaunchOutcome.EmulatorMissing(6, EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation"), ChoiceSource.PersonForGame)
        shot("game_error_emulator_missing", OpenPanel(game, PanelScreen.Message(launchMessage(outcome, null)!!)))
    }

    /** Back from a launch with a RetroArch core nobody has confirmed: Yes focused. */
    @Test
    fun coreQuestion() {
        val question = CoreQuestion(1, "snes-6", "Super Metroid", "retroarch-snes9x", "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), null)
        shot("game_core_question", OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question))))
    }

    /** After No: the core may be missing, the system's other emulators (the failed one left out) and those to get. */
    @Test
    fun coreMissing() {
        val message = coreMissingMessage(sampleListing().copy(systemName = "Super Nintendo"), Beetle.id, "RetroArch: Snes9x")
        shot("game_core_missing", OpenPanel(game, PanelScreen.Message(message)))
    }

    /** First game of a system with nothing chosen: installed ones only, the recommended one first and focused. */
    @Test
    fun chooseEmulatorFirstLaunch() {
        val offers = listOf(
            EmulatorOffer("snes9x", "Snes9x EX+", "com.explusalpha.Snes9xPlus"),
            EmulatorOffer("retroarch-snes9x", "RetroArch: Snes9x", "com.retroarch.aarch64"),
            EmulatorOffer("retroarch-bsnes-mercury-accuracy", "RetroArch: bsnes-mercury Accuracy", "com.retroarch.aarch64"),
        )
        val message = launchMessage(LaunchOutcome.EmulatorNotChosen(6, "snes", "Super Nintendo", offers, "snes9x"), sampleListing().copy(systemName = "Super Nintendo"))!!
        shot("game_choose_emulator_first", OpenPanel(game, PanelScreen.Message(message)))
    }

    /** Switch: every emulator has legal risk, none installed; nothing to offer, one OK. */
    @Test
    fun noEmulatorSwitch() {
        val risky = listOf("Eden", "Citron", "Sudachi").map { EmulatorItem(it.lowercase(), it, "org.example.${it.lowercase()}", isLegalRisk = true) }
        val listing = sampleListing(installed = emptyList(), automatic = null, notInstalled = risky).copy(systemName = "Nintendo Switch")
        val message = launchMessage(LaunchOutcome.NoEmulatorForSystem(6, "switch", emptyList()), listing)!!
        shot("game_error_no_emulator_switch", OpenPanel(GameRef("switch-1", "The Legend of Zelda: Tears of the Kingdom"), PanelScreen.Message(message)))
    }

    @Test
    fun fileMissing() {
        val outcome = LaunchOutcome.FileMissing(6, "Super Metroid (Japan, USA) (En) (Rev 1) [!].sfc", FileProblem.Gone)
        shot("game_error_file_missing", OpenPanel(game, PanelScreen.Message(launchMessage(outcome, null)!!)))
    }
}
