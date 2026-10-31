package com.plumemu.game

import com.plumemu.coredata.db.AppKind
import kotlinx.coroutines.Job

/**
 * The person's answer for an installed app, from the app menu (the row id of the Apps entry) or from an
 * app game's menu (the row id of the game). [PanelCommand.AskAppNotAGame] only opens the confirmation (it deletes
 * the row with its play time) and returns null. [PanelCommand.SetAppKind] closes the panel and the row leaves its
 * list; a failed write says so over the menu ([GameMenuController.write]) and the row stays; an answer stored but not
 * yet followed by the library says so. Kept like a naming write: once asked it is finished even if the person leaves
 * the panel. Kept out of [GameMenuController], which is at its length limit.
 */
internal fun GameMenuController.answerAppKind(
    open: OpenPanel,
    command: PanelCommand,
    actions: GameActions,
    apps: AppActions,
): Job? {
    val game = open.game
    if (command !is PanelCommand.SetAppKind) {
        panel = OpenPanel(game, PanelScreen.Message(appNotAGameConfirmContent(game)), parent = open)
        return null
    }
    val fromApps = open.screen is PanelScreen.AppMenu
    return keptWrite {
        var followed = true
        val stored = write("app kind", game, open) { followed = if (fromApps) apps.setKind(game.key, command.kind) else actions.setAppKind(game.key, command.kind) }
        // Show as port / Show as game keep the row (and its star) in the library; only Not a game deletes it.
        if (stored && command.kind == AppKind.APP) favoriteListener?.hidden(game)
        if (stored && !followed && panel == null) panel = OpenPanel(game, PanelScreen.Message(appKindLaterMessage()))
    }
}
