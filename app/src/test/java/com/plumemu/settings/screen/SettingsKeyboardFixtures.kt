package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInTypingShots
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.toThemeSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FixNameState
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelScreen
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleFinder
import com.plumemu.game.TitleList
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK

/*
 * Settings with a field typing on Plumemu's keyboard, for shots and fit tests:
 * the model is driven by its own actions, as in use, with the settings' keyboard left on Plumemu
 * (the default).
 */

/** The accent's custom palette with [typed] in the hex field and the tray up; [tried]: the action key pressed on it. */
@Composable
internal fun HexTypingSettings(
    typed: String = "1a2b",
    tried: Boolean = false,
) {
    val settings = settingsForShot()
    val geometry = settingsGeometry(PlumTheme.layout)
    val model =
        remember {
            SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentCustom, PaletteCuration.curated(CustomPalette.accentSwatchesOn(settings.toThemeSettings().backgroundColor)).size + ColorSlider.entries.size)).also {
                it.onAction(Action.Enter, settings, geometry)
                if (typed.isNotEmpty()) it.onAction(Action.Typed(TypedKey.Text(typed)), settings, geometry)
                if (tried) it.onAction(Action.Menu, settings, geometry)
            }
        }
    ShownSettings(settings, model)
}

/** The unidentified games Settings' Fix name… fixtures list: the second one's system has no list (Use my own name). */
internal val FixtureUnidentified =
    listOf(
        SettingsGame(31, "mtrd fusn", "Game Boy Advance"),
        SettingsGame(32, "My Homebrew Demo 2", "Test System"),
    )

private val fixtureTitles =
    listOf(
        TitleChoice("Metroid Fusion", "USA, Europe", 2002, "Test DAT"),
        TitleChoice("Metroid Fusion (Japan)", "Japan", 2003, "Test DAT"),
        TitleChoice("Mother 3", "Japan", 2006, "Test DAT"),
    )

/** Answers at once: game 32's system has no list. */
private object FixtureNames : SettingsNameActions by SettingsNameActions.None {
    override fun titleList(
        gameId: Long,
        onDone: (TitleList?) -> Unit,
    ) = onDone(if (gameId == 32L) TitleList.NoList("Test System") else TitleList.Ready(TitleFinder(fixtureTitles)))
}

private object FixtureActions : SettingsActions by NoActions {
    override val names: SettingsNameActions get() = FixtureNames
}

/**
 * Library › Unidentified games › Fix name… for "mtrd fusn" with its results ([own]: Use my own name
 * for the game without a list, with the line saying why; [refused]: after a blank name was refused),
 * typing with the tray up (not with [android]).
 */
@Composable
internal fun NameFixTypingSettings(
    own: Boolean = false,
    refused: Boolean = false,
    /** Controls › Keyboard on Android: the page without typing. */
    android: Boolean = false,
) {
    val settings = settingsForShot().let { if (android) it.copy(controls = it.controls.copy(keyboard = KeyboardSetting.Android)) else it }
    val geometry = settingsGeometry(PlumTheme.layout)
    val content = SettingsContent(unidentifiedGames = FixtureUnidentified)
    val model =
        remember {
            val focus =
                SettingsFocus(
                    column = SettingsColumn.Panel,
                    section = SettingsSection.Library,
                    rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.UnidentifiedGames)),
                    page = PanelPage.UnidentifiedGames,
                    panelIndex = if (own) 1 else 0,
                )
            SettingsModel(FixtureActions, focus).also { model ->
                model.content = content
                model.onAction(Action.Enter, settings, geometry)
                when (val screen = model.nameFix.open?.screen) {
                    is PanelScreen.FixName -> screen.typing.let { state: FixNameState -> state.showResults(state.query, state.finder?.find(state.query).orEmpty()) }
                    is PanelScreen.OwnName ->
                        if (refused) {
                            val state: OwnNameState = screen.typing
                            state.onTextChange("  ")
                            model.onAction(Action.Move(Direction.Down), settings, geometry)
                            model.onAction(Action.Enter, settings, geometry)
                        }
                    else -> error("Fix name… did not open")
                }
                if (!android) model.onAction(Action.Enter, settings, geometry)
            }
        }
    ShownSettings(settings, model)
}

@Composable
private fun ShownSettings(
    settings: PlumSettings,
    model: SettingsModel,
) {
    CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }
}

/** Library › Sign in to ScreenScraper with [form] open as it is: the tray up or down, as the form's own actions leave it. */
@Composable
internal fun SignInSettingsWith(form: SignInForm) {
    val content = AccountShotContent.of(AccountStatus.SignedOut)
    val focus = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperSignIn)
    val model =
        remember(form) {
            SettingsModel(NoActions, focus).also {
                it.content = content
                it.signIn.open(form)
            }
        }
    ShownSettings(settingsForShot(), model)
}

/**
 * Library › Sign in to ScreenScraper with the sign-in form typing with Plumemu's keyboard
 *, in [state] (account/SignInTypingShots.kt): the tray up over the panel.
 */
@Composable
internal fun SignInTypingSettings(state: SignInTypingShots.State) {
    val content = AccountShotContent.of(AccountStatus.SignedOut)
    val focus = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperSignIn)
    val model =
        remember(state) {
            SettingsModel(NoActions, focus).also {
                it.content = content
                it.signIn.open(SignInTypingShots.form(state, stacked = true))
            }
        }
    ShownSettings(settingsForShot(), model)
}
