package com.plumemu.game

import com.plumemu.coredata.db.AppKind
import com.plumemu.graph.CoreQuestion
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.clockTime

/**
 * The game panel's content as plain data, so what it shows is tested without drawing it: words
 * from strings.xml ([UiText.Res]) or data shown as given ([UiText.Plain]: titles, emulator names).
 */
@Immutable
internal sealed interface UiText {
    data class Res(
        @StringRes val id: Int,
        val args: List<String> = emptyList(),
    ) : UiText

    data class Plain(val text: String) : UiText

    /** A string from strings.xml whose arguments are themselves [UiText] ("%1$s, %2$s"). */
    data class Format(
        @StringRes val id: Int,
        val parts: List<UiText>,
    ) : UiText

    /** A time of day as the device shows it ("until 9:00"), from epoch [millis] (Fix box art's daily limit). */
    data class ClockTime(val millis: Long) : UiText
}

@Composable
internal fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
        is UiText.Plain -> text
        is UiText.Format -> stringResource(id, *parts.map { it.resolve() }.toTypedArray())
        is UiText.ClockTime -> clockTime(millis)
    }

/** What a panel row does when chosen (A, or a tap). */
@Immutable
internal sealed interface PanelCommand {
    data object Play : PanelCommand

    data object OpenPlayWith : PanelCommand

    /** Other versions: the copies of the game the row stands for. */
    data object OpenVersions : PanelCommand

    /** Plays the copy [copyKey] now and from now on for this row (its own key: back to the row's own file). */
    data class PlayVersion(val copyKey: String) : PanelCommand

    data class SetFavorite(val isFavorite: Boolean) : PanelCommand

    data object Hide : PanelCommand

    /** Remove from Recent: the game leaves that list until it is played again. */
    data object RemoveFromRecent : PanelCommand

    data object OpenInfo : PanelCommand

    /** Back one step (the focused "Back" row of Info). */
    data object Back : PanelCommand

    /**
     * Store [emulatorId] for this game (null: back to the system's emulator), then play. With
     * [forSystem] (the first launch's question) it is stored for every game of the system.
     */
    data class PlayWith(val emulatorId: String?, val forSystem: Boolean = false) : PanelCommand

    data class GetEmulator(val packageName: String, val name: String) : PanelCommand

    data object RescanLibrary : PanelCommand

    /** Closes the panel (the "OK" of a message with nothing else to do). */
    data object Close : PanelCommand

    /** Name…: the naming sub-panel. */
    data object OpenNameMenu : PanelCommand

    /** Fix name…: the system's name list, searched. */
    data object OpenFixName : PanelCommand

    data object OpenOwnName : PanelCommand

    /** Not a game: the confirm step first. */
    data object AskNotAGame : PanelCommand

    data object ConfirmNotAGame : PanelCommand

    data object BackToAutomatic : PanelCommand

    /** Stores [choice] as the game's name (the person's fix). */
    data class PickTitle(val choice: TitleChoice) : PanelCommand

    /** Stores [name] (trimmed, never blank) as the game's own name. */
    data class SaveOwnName(val name: String) : PanelCommand

    /** Fix box art…: ScreenScraper searched for the game, listed only with a usable account. */
    data object OpenFixBoxArt : PanelCommand

    /** Searches ScreenScraper for [query] (trimmed, never blank) on the game's system. */
    data class SearchBoxArt(val query: String) : PanelCommand

    /** Stores [hit] as the game's box art (the person's choice). */
    data class PickBoxArt(val hit: BoxArtHit) : PanelCommand

    /** Stores "No box art" as the person's choice. */
    data object StoreNoBoxArt : PanelCommand

    /**
     * The answer to "Did <game> start?" for [question]'s RetroArch core ([started]: Yes); B answers
     * nothing.
     */
    data class AnswerCore(val question: CoreQuestion, val started: Boolean) : PanelCommand

    /** App menu: Android's App info page of the app. */
    data object AppInfo : PanelCommand

    /** App menu: Android's uninstall confirmation for the app. */
    data object AppUninstall : PanelCommand

    /**
     * The person's answer for an installed app: [AppKind.GAME] Show as game, [AppKind.PORT] Show as port,
     * [AppKind.APP] Not a game. From the app menu (the app leaves Apps) and from an app game's menu.
     */
    data class SetAppKind(val kind: AppKind) : PanelCommand

    /** An app game's Not a game: asks first, since the row is deleted with its play time ([appNotAGameConfirmContent]). */
    data object AskAppNotAGame : PanelCommand
}

/**
 * One choosable row: [key] is stable (tests and Compose keys), [value] sits at the right ("Get
 * it"), [isCurrent] adds the "Current" badge, [note] is a line under the row.
 */
@Immutable
internal data class PanelRow(
    val key: String,
    val label: UiText,
    val command: PanelCommand,
    val isEnabled: Boolean = true,
    val value: UiText? = null,
    val isCurrent: Boolean = false,
    val note: UiText? = null,
)

@Immutable
internal sealed interface PanelItem {
    data class Row(val row: PanelRow) : PanelItem

    /** A small heading that divides a list ("Not installed"); never focused. */
    data class Heading(val text: UiText) : PanelItem
}

/** A panel: [title] (16 / 600), [body] lines (13, muted), then [items] (Info shows its facts before them). */
@Immutable
internal data class PanelContent(
    val title: UiText,
    val body: List<UiText> = emptyList(),
    val items: List<PanelItem> = emptyList(),
) {
    /** The rows the cursor can stand on, in order; the panel's focus is an index into this. */
    val focusableRows: List<PanelRow> = items.mapNotNull { (it as? PanelItem.Row)?.row?.takeIf(PanelRow::isEnabled) }
}

/** Which panel is open. */
@Immutable
internal sealed interface PanelScreen {
    data class Menu(val facts: GameFacts) : PanelScreen

    /**
     * The Apps entry's menu (START or a long press on an app): App info, Show as game and Show as port when
     * [canChooseKind], and Uninstall when [canUninstall].
     */
    data class AppMenu(val canUninstall: Boolean, val canChooseKind: Boolean = false) : PanelScreen

    data class PlayWith(val listing: EmulatorListing) : PanelScreen

    /** Other versions: one row per copy of the game, in the menu's place. */
    data class Versions(val listing: VersionListing) : PanelScreen

    /**
     * Read-only facts. [scroll]: their Up and Down, kept with the screen (an answer read later keeps it,
     * `copy(facts = …)`), never in the panel, so a press never replaces the panel a background read answers.
     */
    data class Info(
        val facts: GameFacts,
        val scroll: InfoScroll = InfoScroll(),
    ) : PanelScreen

    /** Name…: the naming entries, in the menu's place. */
    data class NameMenu(val facts: GameFacts) : PanelScreen

    /** A launch error, or another failure the person must hear about. */
    data class Message(val content: PanelContent) : PanelScreen

    /** Fix name…: a field and the ranked titles of the system's name list. */
    data class FixName(override val typing: FixNameState) : PanelScreen, TypingScreen

    /** Use my own name: a field and Save. */
    data class OwnName(override val typing: OwnNameState) : PanelScreen, TypingScreen

    /** Fix box art…: a field, Search, ScreenScraper's games, No box art. */
    data class FixBoxArt(override val typing: FixBoxArtState) : PanelScreen, TypingScreen
}

/** A panel with a text field: its state takes the actions first ([TypingState.onAction]). */
internal interface TypingScreen {
    val typing: TypingState
}

/** The game a panel is about: its row id and the title shown. */
@Immutable
internal data class GameRef(
    val key: String,
    val title: String,
)

/**
 * An open panel: which [screen] for which [game], the [focus] (index into the content's focusable
 * rows), and the panel Back returns to ([parent], null: Back closes).
 */
@Immutable
internal data class OpenPanel(
    val game: GameRef,
    val screen: PanelScreen,
    val focus: Int = 0,
    val parent: OpenPanel? = null,
) {
    val content: PanelContent = contentOf(game, screen)
}

internal fun contentOf(
    game: GameRef,
    screen: PanelScreen,
): PanelContent =
    when (screen) {
        is PanelScreen.Menu -> gameMenuContent(game, screen.facts)
        is PanelScreen.AppMenu -> appMenuContent(game, screen.canUninstall, screen.canChooseKind)
        is PanelScreen.NameMenu -> nameMenuContent(game, screen.facts)
        is PanelScreen.PlayWith -> playWithContent(screen.listing)
        is PanelScreen.Versions -> versionsContent(screen.listing)
        // Read-only facts, then one "Back" row so something is always focused.
        is PanelScreen.Info -> PanelContent(UiText.Plain(game.title), items = listOf(PanelItem.Row(PanelRow(INFO_BACK_KEY, UiText.Res(R.string.game_action_back), PanelCommand.Back))))
        is PanelScreen.Message -> screen.content
        // The field and the rows come from the screen's state (FixNamePanel.kt).
        is PanelScreen.FixName -> PanelContent(UiText.Res(R.string.game_fix_name_title))
        is PanelScreen.OwnName -> PanelContent(UiText.Res(R.string.game_own_name_title))
        is PanelScreen.FixBoxArt -> PanelContent(UiText.Res(R.string.game_fix_box_art_title))
    }

internal const val INFO_BACK_KEY = "back"
