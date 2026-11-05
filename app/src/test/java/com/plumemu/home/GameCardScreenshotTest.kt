package com.plumemu.home

import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.Beetle
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.sampleFacts
import com.plumemu.graph.AchievementCount
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game panel as the V2 board's raised card: the menu with its real values and chevrons, and the core question with No and Yes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameCardScreenshotTest {
    private val game = GameRef("snes-6", "Super Metroid")
    private val facts = sampleFacts(title = "Super Metroid", isFavorite = true, canFixBoxArt = true, canSort = true, achievementCount = AchievementCount(12, 40))
        .copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true), sort = SortGamesSetting.Name)
    private val added = AddedSession(sessionId = 9, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
    private val question = CoreQuestion(1, "snes-6", "Super Metroid", Beetle.id, "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), added)

    private fun shot(name: String, panel: OpenPanel, textSize: TextSize = TextSize.Small) =
        ReferenceShots.capture(name, textSize = textSize, sizes = ReferenceShots.Size.WithPocket6) {
            HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel)
        }

    /** Sort by… focused, as the board draws it. */
    private fun menuPanel() = OpenPanel(game, PanelScreen.Menu(facts), focus = 3)

    private fun ask() = OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question)))

    @Test
    fun menu() = shot("game_card_menu", menuPanel())

    @Test
    fun menuAtLargest() = shot("game_card_menu", menuPanel(), TextSize.Largest)

    /** The cursor on Hide game, the last row, and on Info before it: the slab's margin to the card's bottom edge. */
    @Test
    fun menuOnLast() = shot("game_card_menu_last", menuPanel().copy(focus = 8))

    @Test
    fun menuOnSecondToLast() = shot("game_card_menu_second", menuPanel().copy(focus = 7))

    @Test
    fun question() = shot("game_card_question", ask())

    /** The cursor on No (Left from the first focus). */
    @Test
    fun questionOnNo() = shot("game_card_question_no", ask().copy(focus = 1))

    @Test
    fun questionAtLargest() = shot("game_card_question", ask(), TextSize.Largest)
}
