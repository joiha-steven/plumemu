package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.MutableStateFlow
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.game.Beetle
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreMissingMessage
import com.plumemu.game.sampleListing
import com.plumemu.platforms.SystemKind
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens each UI item is judged on, at the sizes of the 640x480 Nova and the 832x468 Pocket 6 ([UiEvidenceBase]). */
abstract class UiEvidenceScenes(
    size: String,
) : UiEvidenceBase(size) {
    private val games = listOf(Action.TapItem(Column.Systems, 2), Action.Enter, Action.TapItem(Column.Games, 0))

    private fun detailsContent() =
        HomeContent.of(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                SystemGames(
                    "ps",
                    "PlayStation",
                    listOf(
                        GameRowModel("1", "Ape Escape", "USA", systemAbbr = "PS1", details = GameDetails(1998, "Sony Computer Entertainment", "Platform", "1", "")),
                        GameRowModel("2", "Gran Turismo", "USA", systemAbbr = "PS1"),
                    ),
                ),
            ),
            hasBoxArt = true,
            showApps = false,
        )

    @Test
    fun item1GamesStop() =
        shoot("1-details-margin") {
            CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(detailsContent(), games) }
        }

    @Test
    fun item1DetailsStop() =
        shoot("1-details-margin-third-stop") {
            CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(detailsContent(), games + Action.Move(Direction.Right)) }
        }

    @Test
    fun item2ListPeek() =
        shoot("2-list-peek") {
            CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(FakeHomeContent.withArtwork().withSystemInfo(), BoardGamesFocused + Action.Move(Direction.Right)) }
        }

    private fun empty(why: EmptyLibrary): @Composable () -> Unit = {
        val notices = HomeNotices(emptyLibrary = MutableStateFlow(why))
        CompositionLocalProvider(LocalHomeNotices provides notices) { HomeAt(FakeHomeContent.noGames()) }
    }

    @Test
    fun item3EmptyNoFolder() = shoot("3-empty-state", scene = empty(EmptyLibrary.NoFolder))

    @Test
    fun item3EmptyNoGames() = shoot("3-empty-state-no-games", scene = empty(EmptyLibrary.NoGames))

    private fun arcade(enter: Boolean): @Composable () -> Unit = {
        val index = FakeHomeContent.board().entries.indexOfFirst { it.id == BOARD_ARCADE_ID }
        HomeAt(FakeHomeContent.board().withArcadeAbout(), listOfNotNull(Action.TapItem(Column.Systems, index), Action.Enter.takeIf { enter }))
    }

    @Test
    fun item4ArcadeSystemsOpen() = shoot("4-arcade-fact", scene = arcade(enter = false))

    @Test
    fun item4ArcadeGamesStop() = shoot("4-arcade-fact-games-stop", scene = arcade(enter = true))

    @Test
    fun item4ArcadeLarger() = shoot("4-arcade-fact-larger", TextSize.Larger, arcade(enter = true))

    @Test
    fun item4ArcadeLargerSystemsOpen() = shoot("4-arcade-fact-larger-systems-open", TextSize.Larger, arcade(enter = false))

    @Test
    fun item5aAppsGrid() = shoot("5a-apps-grid") { AppsGridBoard(focused = 0) }

    @Test
    fun item5aAppsGridDeep() = shoot("5a-apps-grid-deep") { AppsGridBoard(focused = 22) }

    private fun psx(): HomeContent {
        val board = FakeHomeContent.board()
        return board.withEntries(board.entries.map { if (it.id == "psx") it.copy(info = SystemInfo("PS1", "Sony", SystemKind.Console, 1994, 2006)) else it })
    }

    @Test
    fun item5bPs1Band() = shoot("5b-ps1-band") { HomeAt(psx(), BoardGamesFocused + listOf(Action.TapItem(Column.Systems, psx().entries.indexOfFirst { it.id == "psx" }), Action.Enter)) }

    @Test
    fun item5cSystemsDeep() = shoot("5c-systems-column") { HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.board().entries.lastIndex))) }

    @Test
    fun item5dCoreMissingScrolled() =
        shoot("5d-core-missing-scrolled") {
            val game = GameRef("snes-6", "Super Metroid")
            val message = coreMissingMessage(sampleListing().copy(systemName = "Super Nintendo"), Beetle.id, "RetroArch: Snes9x")
            HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(game, PanelScreen.Message(message), focus = 2))
        }

    @Test
    fun item5dCoreMissing() =
        shoot("5d-core-missing") {
            val game = GameRef("snes-6", "Super Metroid")
            val message = coreMissingMessage(sampleListing().copy(systemName = "Super Nintendo"), Beetle.id, "RetroArch: Snes9x")
            HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = OpenPanel(game, PanelScreen.Message(message)))
        }

    private fun apps(extra: List<Action>): @Composable () -> Unit = {
        CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons()) {
            HomeAt(appsGridLibrary(hasBoxArt = true), listOf(Action.TapItem(Column.Systems, APPS_ENTRY), Action.Enter) + extra)
        }
    }

    @Test
    fun item6AppsDetails() = shoot("6-apps-details", scene = apps(emptyList()))

    @Test
    fun item6AppsDetailsThirdStop() = shoot("6-apps-details-third-stop", scene = apps(listOf(Action.Move(Direction.Right))))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class UiEvidence640x480Test : UiEvidenceScenes("640x480")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class UiEvidence832x468Test : UiEvidenceScenes("832x468")
