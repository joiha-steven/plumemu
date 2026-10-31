package com.plumemu.home

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.appNotAGameConfirmContent
import com.plumemu.game.sampleFacts
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The visible part: the Apps menu with Show as game and Show as port, the Android games list with two
 * installed apps (no box art, so plain rows), and an app game's menu with Show as port and Not a game.
 * 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppKindScreenshotTest {
    private val celeste = GameRowModel(id = "901", title = "Celeste", region = "", systemId = "android", sortName = "celeste")
    private val stardew = GameRowModel(id = "902", title = "Stardew Valley", region = "", systemId = "android", sortName = "stardew valley")
    private val androidGames = HomeContent.of(
        recent = emptyList(),
        favorites = emptyList(),
        apps = emptyList(),
        systems = listOf(SystemGames("android", "Android games", listOf(celeste, stardew), code = "AND")),
    )
    private val opened = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)

    @Test
    fun appsMenuWithShowAsGameAndShowAsPort() {
        val app = GameRef(AppsGridApps[0].id, AppsGridApps[0].title)
        val panel = OpenPanel(app, PanelScreen.AppMenu(canUninstall = true, canChooseKind = true))
        ReferenceShots.capture("apps_menu_kinds") { HomeAt(appsGridLibrary(), gridActions(APPS_ENTRY, 0), panel = panel) }
    }

    @Test
    fun androidGamesWithTwoInstalledApps() = ReferenceShots.capture("android_games_list") { HomeAt(androidGames, opened) }

    @Test
    fun anAppGamesMenu() {
        val facts = sampleFacts(title = "Celeste").copy(systemName = "Android games", isAppGame = true, emulator = null)
        val panel = OpenPanel(GameRef(celeste.id, celeste.title), PanelScreen.Menu(facts), focus = 3)
        ReferenceShots.capture("game_menu_app_game") { HomeAt(androidGames, opened, panel = panel) }
    }

    /** Not a game on an app game asks first (the row is deleted with its play time): Cancel focused, at Large text. */
    @Test
    fun notAGameQuestionForAnAppGame() {
        val game = GameRef(celeste.id, celeste.title)
        val menu = OpenPanel(game, PanelScreen.Menu(sampleFacts(title = "Celeste").copy(systemName = "Android games", isAppGame = true, emulator = null)), focus = 4)
        val ask = OpenPanel(game, PanelScreen.Message(appNotAGameConfirmContent(game)), parent = menu)
        ReferenceShots.capture("game_app_not_a_game", textSize = TextSize.Large) { HomeAt(androidGames, opened, panel = ask) }
    }
}
