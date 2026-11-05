package com.plumemu.home

import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.SortChoices
import com.plumemu.game.sampleFacts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sort by… (the game menu's entry, its choices, and the "R3 Sort" hint on the games list) at both sizes, Graphite and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameSortScreenshotTest {
    private val game = GameRef("snes-6", "Super Metroid")
    private val facts =
        sampleFacts(title = "Super Metroid").copy(
            systemName = "Super Nintendo",
            emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true),
            canSort = true,
        )

    private fun shot(name: String, panel: OpenPanel?) = ReferenceShots.capture(name) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    /** The games list's hint bar: "R3 Sort" beside X Search and START Menu. */
    @Test
    fun hintBar() = shot("games_sort_hint", null)

    /** START on a game of a system list: Sort by… after Hide, focused. */
    @Test
    fun menuWithSortBy() = shot("game_menu_sort_by", OpenPanel(game, PanelScreen.Menu(facts), focus = 4))

    /** Sort by… with the sort in use (Most played) marked Current and focused; no game has a year or a rating, so Year and Rating are not listed. */
    @Test
    fun choices() =
        shot(
            "game_sort_choices",
            OpenPanel(game, PanelScreen.SortMenu(SortChoices(SortGamesSetting.MostPlayed, hasYear = false, hasRating = false)), focus = 2),
        )

    /** Year is listed once a game has one. */
    @Test
    fun choicesWithYear() =
        shot("game_sort_choices_year", OpenPanel(game, PanelScreen.SortMenu(SortChoices(SortGamesSetting.Name, hasYear = true, hasRating = false)), focus = 0))

    /** Rating is listed last once a game has one; here it is the sort in use, marked and focused. */
    @Test
    fun choicesWithRating() =
        shot("game_sort_choices_rating", OpenPanel(game, PanelScreen.SortMenu(SortChoices(SortGamesSetting.Rating, hasYear = true, hasRating = true)), focus = 5))
}
