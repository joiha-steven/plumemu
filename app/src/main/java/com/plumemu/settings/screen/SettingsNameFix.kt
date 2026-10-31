package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.game.FixNameState
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.game.OwnNameState
import com.plumemu.game.PanelCommand
import com.plumemu.game.PanelScreen
import com.plumemu.game.RenameBoxArtNotice
import com.plumemu.game.TitleChoice
import com.plumemu.game.TitleList
import com.plumemu.game.TypingOutcome
import com.plumemu.game.TypingPanelBody
import com.plumemu.game.TypingPanelLayout
import com.plumemu.game.TypingState
import com.plumemu.game.resolve
import com.plumemu.game.noTitleListContent

/**
 * What the graph does for Settings › Library › Unidentified games. Each call returns at
 * once; the answer comes back through its callback on the main thread.
 */
internal interface SettingsNameActions {
    /** The game's system list for Fix name…; null when the game is gone or could not be read. */
    fun titleList(
        gameId: Long,
        onDone: (TitleList?) -> Unit,
    )

    /**
     * [onDone] gets whether the name was stored and, if so, why box art could not be looked up by
     * it (null: looked up, or nothing to say; [RenameBoxArtNotice]).
     */
    fun setChosenTitle(
        gameId: Long,
        choice: TitleChoice,
        onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
    )

    /** [onDone] as [setChosenTitle]'s. */
    fun setOwnName(
        gameId: Long,
        name: String,
        onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
    )

    object None : SettingsNameActions {
        override fun titleList(
            gameId: Long,
            onDone: (TitleList?) -> Unit,
        ) = onDone(null)

        override fun setChosenTitle(
            gameId: Long,
            choice: TitleChoice,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) = onDone(false, null)

        override fun setOwnName(
            gameId: Long,
            name: String,
            onDone: (stored: Boolean, boxArt: RenameBoxArtNotice?) -> Unit,
        ) = onDone(false, null)
    }
}

/**
 * The Fix name… page open over Unidentified games: the game (by id: the list changes while the
 * page is open) and its field. A system without a usable list opens Use my own name directly,
 * with [noList] saying why.
 */
@Immutable
internal data class OpenNameFix(
    val gameId: Long,
    /** The game's place in the list, to come back to. */
    val listIndex: Int,
    val screen: PanelScreen,
    val noList: TitleList? = null,
    /** Set when the system's list could not be read at all: the page says so by this system's name. */
    val unreadableSystem: String? = null,
) {
    val typing: TypingState? get() = (screen as? PanelScreen.FixName)?.typing ?: (screen as? PanelScreen.OwnName)?.typing
}

/**
 * Settings' side of Fix name…, the same field and rows as the game menu's
 * ([FixNameState], [OwnNameState]), typed with the keyboard Settings › Controls › Keyboard named
 * when the page opened (Plumemu's tray over Settings). [open] is the open page; the model routes every action to
 * [onAction] while the focus is on [PanelPage.FixName]. Main thread only.
 */
@Stable
internal class SettingsNameFix {
    var open: OpenNameFix? by mutableStateOf(null)
        private set

    /** The keyboard the open page's fields type with (Settings › Controls › Keyboard when it opened). */
    private var keyboard = KeyboardChoice.Plumemu

    /** Fix name… for [game] at once, its list still being read, typed with [keyboard]. */
    fun showLoading(
        game: SettingsGame,
        listIndex: Int,
        keyboard: KeyboardChoice,
    ): OpenNameFix {
        this.keyboard = keyboard
        return OpenNameFix(game.id, listIndex, PanelScreen.FixName(FixNameState(game.title, finder = null, keyboard))).also { open = it }
    }

    /** The game's list arrived: its titles in Fix name…, or Use my own name when its system has no list. */
    fun show(
        game: SettingsGame,
        listIndex: Int,
        list: TitleList,
    ) {
        val page = open
        val loading = (page?.screen as? PanelScreen.FixName)?.typing
        open =
            when {
                list is TitleList.Ready && page != null && loading != null -> page.also { loading.listReady(list.finder) }
                list is TitleList.Ready -> OpenNameFix(game.id, listIndex, PanelScreen.FixName(FixNameState(game.title, list.finder, keyboard)))
                else -> OpenNameFix(game.id, listIndex, PanelScreen.OwnName(OwnNameState(game.title, keyboard)), noList = list)
            }
    }

    /** The list could not be read (the read failed): the page stays, says so, and offers Use my own name. */
    fun showUnreadable(
        game: SettingsGame,
        listIndex: Int,
    ) {
        open = OpenNameFix(game.id, listIndex, PanelScreen.OwnName(OwnNameState(game.title, keyboard)), TitleList.Unavailable, game.systemName)
    }

    fun close() {
        open = null
    }

    /**
     * One action on the open page: what the field makes of it, or the command it ran into. Back,
     * START and Left on the rows leave the page ([Step.Leave]); a pick or a saved name stores it
     * and leaves too.
     */
    fun onAction(action: Action): Step = onTyping { it.onAction(action) }

    /** What the open field made of [outcomeOf] (an action, or a tap on a key of Plumemu's tray), as a [Step]. */
    inline fun onTyping(outcomeOf: (TypingState) -> TypingOutcome): Step {
        val page = open ?: return Step.Leave(null)
        val typing = page.typing ?: return Step.Leave(null)
        return when (val outcome = outcomeOf(typing)) {
            TypingOutcome.Handled -> Step.Stay
            TypingOutcome.NotHandled -> Step.Leave(null)
            is TypingOutcome.Run ->
                when (val command = outcome.command) {
                    is PanelCommand.PickTitle, is PanelCommand.SaveOwnName -> Step.Leave(command)
                    else -> Step.Stay
                }
        }
    }

    sealed interface Step {
        data object Stay : Step

        /** Back to the list; [command] to store first (a pick or a typed name), null for none. */
        data class Leave(val command: PanelCommand?) : Step
    }
}

/**
 * The Fix name… page in the Settings panel: its title, why there is no list (if so), then the field
 * and rows. With Plumemu's keyboard the page is laid out as the game panel is ([TypingPanelLayout]:
 * while the tray is up the title gives way from the top and what is under the field scrolls); with
 * Android's it is the panel's plain column, as before.
 */
@Composable
internal fun SettingsFixNamePage(
    page: OpenNameFix,
    onAction: (Action) -> Unit,
) {
    val typing = page.typing ?: return
    val gap = PlumDimens.settingsPanelListGap
    if (typing.keyboardKind != KeyboardChoice.Plumemu) {
        // Android's keyboard: the panel's plain column (nothing covers it).
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            PanelTitle(stringResource(if (page.noList == null) R.string.game_fix_name_title else R.string.game_own_name_title))
            NameFixWhy(page)
            TypingPanelBody(page.screen, onAction)
        }
        return
    }
    TypingPanelLayout(
        typing,
        keyboardCovers = settingsKeyboardCovers(),
        modifier = Modifier.fillMaxHeight(),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                PanelTitle(stringResource(if (page.noList == null) R.string.game_fix_name_title else R.string.game_own_name_title))
                NameFixWhy(page)
            }
            Spacer(Modifier.height(gap))
        },
    ) {
        TypingPanelBody(page.screen, onAction)
    }
}

/** Why there is no list on the page: the system has none, or reading it failed (then by the system's name). */
@Composable
private fun NameFixWhy(page: OpenNameFix) {
    val system = page.unreadableSystem
    if (system != null) {
        PanelBody(stringResource(R.string.settings_titles_unreadable, system))
    } else {
        page.noList?.let { list -> noTitleListContent(list).body.forEach { PanelBody(it.resolve()) } }
    }
}
